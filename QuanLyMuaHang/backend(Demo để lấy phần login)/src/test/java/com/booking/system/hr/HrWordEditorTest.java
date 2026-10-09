package com.booking.system.hr;

import com.booking.system.hr.service.*;
import com.booking.system.hr.importer.HrImportActor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrWordEditorTest {
    HrWordEditorSettings settings=new HrWordEditorSettings(true,"https://docs.test.invalid","https://hr.test.invalid","test-only-secret-at-least-32-bytes-long");
    HrWordEditorTokens tokens=new HrWordEditorTokens(settings);
    HrWordEditorDownload downloader=mock(HrWordEditorDownload.class);
    HrImportActor actor=new HrImportActor("owner","Owner","ADMIN");
    HrWordEditorService service;
    JdbcTemplate jdbc;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:word_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
        service=new HrWordEditorService(jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),settings,tokens,downloader,
                new HrDocumentTemplateService(jdbc),mock(HrEmploymentContractDocumentService.class));
    }
    @Test void authenticatedFinalCallbackCreatesDraftThenPublishesExactlyOnce() throws Exception {
        var open=service.open(new HrWordEditorService.OpenRequest("TEMPLATE","OFFICE","active",null),actor);
        String id=open.id();
        byte[] bytes=HrDocumentTemplateService.builtIn("OFFICE").bytes();
        when(downloader.fetch("https://docs.test.invalid/cache/files/a/output.docx")).thenReturn(bytes);
        assertThat(tokens.verify((String)open.config().get("token"))).containsKey("document");
        Map<?,?> permissions=(Map<?,?>)((Map<?,?>)open.config().get("document")).get("permissions");
        assertThat(permissions.get("edit")).isEqualTo(true);
        assertThat(permissions.get("download")).isEqualTo(false);
        assertThat(permissions.get("print")).isEqualTo(false);
        assertThatThrownBy(()->service.publish(id,"note",actor)).hasMessageContaining("Chưa nhận");
        String jwt=tokens.sign(Map.of("key",id,"status",2,"url","https://docs.test.invalid/cache/files/a/output.docx"));
        service.callback(id,jwt); service.callback(id,jwt);
        verify(downloader,times(1)).fetch(anyString());
        assertThat(service.owned(id,actor).status()).isEqualTo("READY");
        String result=service.publish(id,"Edited online",actor);
        assertThat(service.publish(id,"duplicate",actor)).isEqualTo(result);
        assertThat(service.owned(id,actor).status()).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_document_template_revisions",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT active_version_id FROM hr_document_template_families WHERE template_kind='OFFICE'",String.class)).isEqualTo(result);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_audit_events WHERE action='HR_DOCUMENT_TEMPLATE_ACTIVATED'",Integer.class)).isEqualTo(1);
        assertThatThrownBy(()->service.owned(id,new HrImportActor("other","Other","ADMIN"))).hasMessageContaining("Không tìm thấy");
    }
    @Test void rejectsBadSignaturesWrongSessionAndContentPurpose() throws Exception {
        var open=service.open(new HrWordEditorService.OpenRequest("TEMPLATE","PROBATION","builtin",null),actor);
        assertThatThrownBy(()->service.callback(open.id(),"invalid")).isInstanceOf(Exception.class);
        assertThatThrownBy(()->service.callback(open.id(),tokens.sign(Map.of("key","wrong","status",2)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.source(open.id(),tokens.sign(Map.of("session",open.id(),"purpose","other")))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.source(open.id(),tokens.sign(Map.of("session",open.id(),"purpose","word-content")))).isNotEmpty();
        verifyNoInteractions(downloader);
    }
    @Test void noChangeAndHeaderPayloadCallbacksWorkAndDraftCanBeReopened() throws Exception {
        var open=service.open(new HrWordEditorService.OpenRequest("TEMPLATE","OFFICE","active",null),actor);
        service.callback(open.id(),tokens.sign(Map.of("payload",Map.of("key",open.id(),"status",4))));
        assertThat(service.owned(open.id(),actor).status()).isEqualTo("UNCHANGED");
        var next=service.open(new HrWordEditorService.OpenRequest(null,null,null,open.id()),actor);
        assertThat(next.id()).isNotEqualTo(open.id());
        assertThat(service.draft(next.id(),actor)).containsExactly(service.draft(open.id(),actor));
    }
    @Test void callbackDownloadFailureDoesNotClaimSaved() throws Exception {
        var open=service.open(new HrWordEditorService.OpenRequest("TEMPLATE","OFFICE","active",null),actor);
        when(downloader.fetch(anyString())).thenThrow(new java.io.IOException("test"));
        assertThatThrownBy(()->service.callback(open.id(),tokens.sign(Map.of("key",open.id(),"status",2,"url","https://docs.test.invalid/cache/files/a/file.docx"))))
                .isInstanceOf(java.io.IOException.class);
        assertThat(service.owned(open.id(),actor).status()).isEqualTo("OPEN");
    }
    @Test void downloadUrlMustStayOnConfiguredOriginAndCachePath() {
        assertThat(settings.trustedDownload("https://docs.test.invalid/cache/files/a/output.docx?token=x")).isNotNull();
        for(String bad:List.of("http://docs.test.invalid/cache/files/a", "https://evil.test/cache/files/a", "https://docs.test.invalid@evil.test/cache/files/a",
                "https://docs.test.invalid:444/cache/files/a", "https://docs.test.invalid/private", "https://docs.test.invalid/cache/files/%2e%2e/private", "file:///etc/passwd"))
            assertThatThrownBy(()->settings.trustedDownload(bad)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new HrWordEditorSettings(false,"","","").requireConfigured()).hasMessageContaining("Chưa cấu hình");
    }

    @Test void expiredSessionCannotExposeContentOrAcceptCallback() {
        var open=service.open(new HrWordEditorService.OpenRequest("TEMPLATE","OFFICE","active",null),actor);
        jdbc.update("UPDATE hr_word_editor_sessions SET expires_at=? WHERE id=?",java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1),open.id());
        assertThatThrownBy(()->service.resume(open.id(),actor)).hasMessageContaining("hết hạn");
        assertThatThrownBy(()->service.source(open.id(),tokens.sign(Map.of("session",open.id(),"purpose","word-content")))).hasMessageContaining("hết hạn");
        assertThatThrownBy(()->service.callback(open.id(),tokens.sign(Map.of("key",open.id(),"status",2)))).hasMessageContaining("hết hạn");
        verifyNoInteractions(downloader);
    }

    @Test void cancelledSessionCannotPublishOrBeOverwrittenByCallback() throws Exception {
        var open=service.open(new HrWordEditorService.OpenRequest("TEMPLATE","OFFICE","active",null),actor);
        service.cancel(open.id(),actor);
        assertThat(service.owned(open.id(),actor).status()).isEqualTo("CANCELLED");
        service.callback(open.id(),tokens.sign(Map.of("key",open.id(),"status",2)));
        assertThat(service.owned(open.id(),actor).status()).isEqualTo("CANCELLED");
        assertThatThrownBy(()->service.publish(open.id(),"Cancelled",actor)).hasMessageContaining("Chưa nhận");
        verifyNoInteractions(downloader);
    }

    @Test void changingTemplateVariablesKeepsDraftAndDoesNotPublish() throws Exception {
        var open=service.open(new HrWordEditorService.OpenRequest("TEMPLATE","OFFICE","active",null),actor);
        when(downloader.fetch(anyString())).thenReturn(HrDocumentTemplateService.builtIn("PROBATION").bytes());
        service.callback(open.id(),tokens.sign(Map.of("key",open.id(),"status",2,"url","https://docs.test.invalid/cache/files/a/x.docx")));
        assertThatThrownBy(()->service.publish(open.id(),"Removed fields",actor)).hasMessageContaining("giữ đúng các biến");
        assertThat(service.owned(open.id(),actor).status()).isEqualTo("READY");
        assertThat(service.draft(open.id(),actor)).containsExactly(HrDocumentTemplateService.builtIn("PROBATION").bytes());
    }
}
