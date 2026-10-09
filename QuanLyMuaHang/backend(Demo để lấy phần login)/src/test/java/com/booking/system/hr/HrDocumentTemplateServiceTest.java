package com.booking.system.hr;

import com.booking.system.hr.service.HrDocumentTemplateService;
import com.booking.system.hr.importer.HrImportActor;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class HrDocumentTemplateServiceTest {
    @Test void uploadDoesNotActivateAndVersionsAreDeduplicatedAndScoped() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:word_templates;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        var service = new HrDocumentTemplateService(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var actor = new HrImportActor("hr@test.invalid", "HR Test", "ADMIN");
        var original = HrDocumentTemplateService.builtIn("OFFICE");
        String id = tx.execute(status -> service.upload("OFFICE", "new-layout.docx", original.bytes(), "Test upload", actor));
        assertThat(service.active("OFFICE").fileName()).isEqualTo(original.fileName());
        assertThat(service.list("OFFICE",0)).singleElement().satisfies(r -> {
            assertThat(r.active()).isFalse(); assertThat(r.actor()).isEqualTo(actor.subject());
        });
        String duplicateId = tx.execute(status -> service.upload("OFFICE", "duplicate.docx", original.bytes(), "Same bytes", actor));
        assertThat(duplicateId).isEqualTo(id);
        assertThat(service.list("OFFICE",1)).isEmpty();
        assertThatThrownBy(() -> service.download("PROBATION", id)).hasMessageContaining("Không tìm thấy");
        tx.executeWithoutResult(status -> service.activate("OFFICE",id,actor));
        assertThat(service.active("OFFICE").fileName()).isEqualTo("new-layout.docx");
        tx.executeWithoutResult(status -> service.activate("OFFICE","builtin",actor));
        assertThat(service.active("OFFICE").fileName()).isEqualTo(original.fileName());
        assertThat(service.download("OFFICE",id).bytes()).containsExactly(original.bytes());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_audit_events WHERE action='HR_DOCUMENT_TEMPLATE_ACTIVATED'", Integer.class)).isEqualTo(2);
        assertThatThrownBy(() -> service.upload("OFFICE","bad.docx",HrDocumentTemplateService.builtIn("PROBATION").bytes(),"Wrong fields",actor))
                .hasMessageContaining("giữ đúng các biến");
    }
}
