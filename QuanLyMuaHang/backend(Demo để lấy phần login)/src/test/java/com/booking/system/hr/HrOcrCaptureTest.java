package com.booking.system.hr;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrOcrProfileResult;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrOcrCaptureTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    HrOcrService ocr;
    SimpMessagingTemplate messaging;
    HrOcrCaptureService service;
    HrImportActor owner = new HrImportActor("USER:owner", "Owner", "MANAGER");
    HrImportActor other = new HrImportActor("USER:other", "Other", "ADMIN");
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:capture_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V20__hr_general_labor_ocr_capture.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        ocr = mock(HrOcrService.class); messaging = mock(SimpMessagingTemplate.class);
        service = new HrOcrCaptureService(jdbc, tx, ocr, messaging, 30);
    }
    @AfterEach void close() { service.shutdown(); }
    String open() { return service.open(UUID.randomUUID().toString(), owner).id(); }
    MockMultipartFile photo(int color) throws Exception {
        var img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB); img.setRGB(0, 0, color);
        var out = new ByteArrayOutputStream(); ImageIO.write(img, "png", out);
        return new MockMultipartFile("file", "photo.png", "image/png", out.toByteArray());
    }
    HrOcrProfileResult result() throws Exception {
        return new ObjectMapper().readValue("{\"fullName\":\"TEST PERSON\",\"rawOcrText\":\"private raw text\"}", HrOcrProfileResult.class);
    }
    void awaitStatus(String id, String status) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!service.get(id, owner).status().equals(status) && System.nanoTime() < end) Thread.sleep(10);
        assertThat(service.get(id, owner).status()).isEqualTo(status);
    }
    @Test void onlySameAccountCanUseEverySessionOperationEvenIfQrIsKnown() throws Exception {
        String id = open();
        var image = service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(1), owner).images().getFirst();
        assertThat(service.pair(id, new HrImportActor("USER:owner", "Same account on phone", "MANAGER")).paired()).isTrue();
        for (org.assertj.core.api.ThrowableAssert.ThrowingCallable operation : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                () -> service.get(id, other), () -> service.open(id, other), () -> service.pair(id, other),
                () -> service.image(id, image.id(), other), () -> service.remove(id, image.id(), other),
                () -> service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(2), other),
                () -> service.scan(id, 1, other), () -> service.close(id, false, other))) {
            assertThatThrownBy(operation).isInstanceOf(HrApiException.class).hasMessageContaining("cùng tài khoản");
        }
        verifyNoInteractions(ocr);
    }
    @Test void retryIsIdempotentAndRetakingOneSideReplacesOnlyThatSide() throws Exception {
        String id = open(), upload = UUID.randomUUID().toString();
        var first = service.upload(id, upload, "FRONT", photo(1), owner);
        var retry = service.upload(id, upload, "FRONT", photo(1), owner);
        assertThat(retry.images()).isEqualTo(first.images()); assertThat(retry.revision()).isEqualTo(first.revision());
        service.upload(id, UUID.randomUUID().toString(), "BACK", photo(2), owner);
        var replaced = service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(3), owner);
        assertThat(replaced.images()).hasSize(2).doesNotContain(first.images().getFirst());
        assertThatThrownBy(() -> service.image(id, first.images().getFirst().id(), owner)).hasMessageContaining("thay thế");
        assertThatThrownBy(() -> service.upload(id, upload, "FRONT", photo(1), owner)).hasMessageContaining("đã bị bỏ");
        assertThat(service.get(id, owner).images()).isEqualTo(replaced.images());
    }

    @Test void removingAnImageCannotBeUndoneByADelayedUploadRetry() throws Exception {
        String id = open(), uploadId = UUID.randomUUID().toString();
        var first = service.upload(id, uploadId, "OTHER", photo(1), owner);
        service.remove(id, first.images().getFirst().id(), owner);
        assertThatThrownBy(() -> service.upload(id, uploadId, "OTHER", photo(1), owner)).hasMessageContaining("đã bị bỏ");
        assertThat(service.get(id, owner).images()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_ocr_capture_images WHERE content IS NOT NULL", Integer.class)).isZero();
    }

    @Test void rejectsMoreThanSixPhotosAndQuotaRestoresAfterClosingSession() throws Exception {
        String id = open();
        for (int n = 0; n < 6; n++) service.upload(id, UUID.randomUUID().toString(), "OTHER", photo(n), owner);
        assertThatThrownBy(() -> service.upload(id, UUID.randomUUID().toString(), "OTHER", photo(7), owner)).hasMessageContaining("6 ảnh");
        assertThat(service.get(id, owner).images()).hasSize(6);
        for (int n = 0; n < 4; n++) open();
        assertThatThrownBy(this::open).hasMessageContaining("5 phiên");
        service.close(id, false, owner);
        assertThat(open()).isNotBlank();
    }

    @Test void concurrentRetriesCreateOnlyOneImage() throws Exception {
        String id = open(), clientId = UUID.randomUUID().toString();
        var photo = photo(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> service.upload(id, clientId, "FRONT", photo, owner));
            var b = executor.submit(() -> service.upload(id, clientId, "FRONT", photo, owner));
            assertThat(a.get(3, TimeUnit.SECONDS).images()).isEqualTo(b.get(3, TimeUnit.SECONDS).images());
        }
        assertThat(service.get(id, owner).images()).hasSize(1);
    }

    @Test void rollbackDoesNotDeliverAnOcrEvent() throws Exception {
        String id = open(); var photo = photo(1);
        tx.executeWithoutResult(status -> {
            try { service.upload(id, UUID.randomUUID().toString(), "FRONT", photo, owner); }
            catch (Exception e) { throw new RuntimeException(e); }
            status.setRollbackOnly();
        });
        assertThat(service.get(id, owner).images()).isEmpty();
        verifyNoInteractions(messaging);
    }

    @Test void controllerRoundTripSerializesSnapshotsAndProtectsImagesWithNoStore() throws Exception {
        var user = new com.booking.system.entity.User();
        user.setId("owner"); user.setStatus(com.booking.system.enums.UserStatus.ACTIVE);
        user.setRole(com.booking.system.enums.RoleEnum.MANAGER); user.setFullName("Owner");
        var principal = new java.util.concurrent.atomic.AtomicReference<>(user);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.booking.system.hr.api.HrOcrCaptureController(service, new com.booking.system.hr.api.HrActorResolver()))
                .setControllerAdvice(new com.booking.system.hr.api.HrApiExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(org.springframework.core.MethodParameter p) { return p.getParameterType() == com.booking.system.entity.User.class; }
                    public Object resolveArgument(org.springframework.core.MethodParameter p, org.springframework.web.method.support.ModelAndViewContainer m,
                                                  org.springframework.web.context.request.NativeWebRequest r, org.springframework.web.bind.support.WebDataBinderFactory b) { return principal.get(); }
                }).build();
        String id = UUID.randomUUID().toString(), root = "/api/v1/hr/general-labor/ocr-captures";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(root)
                .contentType("application/json").content("{\"id\":\"" + id + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.status").value("OPEN"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(root + "/" + id + "/images")
                .file(photo(1)).param("clientId", UUID.randomUUID().toString()).param("kind", "FRONT"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.images.length()").value(1));
        var image = service.get(id, owner).images().getFirst();
        String path = root + "/" + id + "/images/" + image.id();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store, private"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(photo(1).getBytes()));
        user.setId("other");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void differentFormsRemainIsolatedAndSnapshotSurvivesServiceRestart() throws Exception {
        String a = open(), b = open();
        service.upload(a, UUID.randomUUID().toString(), "FRONT", photo(1), owner);
        assertThat(service.get(b, owner).images()).isEmpty();
        var restarted = new HrOcrCaptureService(jdbc, tx, ocr, messaging, 30);
        try { assertThat(restarted.get(a, owner).images()).hasSize(1); } finally { restarted.shutdown(); }
    }
    @Test void scanningIsDeduplicatedAndReturnsResultWithoutRawProviderText() throws Exception {
        var response = result(); when(ocr.extractProfile(anyList(), eq(owner))).thenReturn(response);
        String id = open(); var uploaded = service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(1), owner);
        service.scan(id, uploaded.revision(), owner); awaitStatus(id, "READY");
        var ready = service.get(id, owner);
        assertThat(ready.result()).containsEntry("fullName", "TEST PERSON").doesNotContainKey("rawOcrText");
        service.scan(id, ready.revision(), owner);
        verify(ocr, times(1)).extractProfile(anyList(), eq(owner));
    }
    @Test void imageChangeRejectsLateResultAndAStaleScanRequest() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var response = result();
        when(ocr.extractProfile(anyList(), eq(owner))).thenAnswer(inv -> { entered.countDown(); release.await(3, TimeUnit.SECONDS); return response; });
        String id = open(); var first = service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(1), owner);
        service.scan(id, first.revision(), owner); assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        var replacement = service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(2), owner);
        release.countDown(); service.shutdown();
        assertThat(service.get(id, owner).status()).isEqualTo("OPEN");
        assertThat(service.get(id, owner).result()).isNull();
        assertThatThrownBy(() -> service.scan(id, first.revision(), owner)).hasMessageContaining("vừa thay đổi");
        assertThat(replacement.revision()).isGreaterThan(first.revision());
    }
    @Test void closePurgesPhotosAndResultAndBlocksLateWrites() throws Exception {
        String id = open(); service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(1), owner);
        var closed = service.close(id, true, owner);
        assertThat(closed.status()).isEqualTo("COMPLETED"); assertThat(closed.images()).isEmpty(); assertThat(closed.result()).isNull();
        assertThat(service.close(id, false, owner).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(1), owner)).hasMessageContaining("kết thúc");
        assertThatThrownBy(() -> service.scan(id, closed.revision(), owner)).hasMessageContaining("kết thúc");
    }
    @Test void expiryDeniesAccessBeforeCleanupAndCascadePurgesContent() throws Exception {
        String id = open(); service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(1), owner);
        jdbc.update("UPDATE hr_ocr_capture_sessions SET expires_at=? WHERE id=?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), id);
        assertThatThrownBy(() -> service.get(id, owner)).hasMessageContaining("hết hạn");
        service.cleanup();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_ocr_capture_images", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hr_ocr_capture_sessions", Integer.class)).isZero();
    }
    @Test void interruptedWorkerCanBeRetriedAndInvalidImagesAreRejected() throws Exception {
        String id = open();
        jdbc.update("UPDATE hr_ocr_capture_sessions SET status='SCANNING',run_started_at=? WHERE id=?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(4), id);
        service.cleanup(); assertThat(service.get(id, owner).status()).isEqualTo("FAILED");
        assertThatThrownBy(() -> service.upload(id, UUID.randomUUID().toString(), "FRONT",
                new MockMultipartFile("file", "bad.png", "image/png", "not an image".getBytes()), owner)).hasMessageContaining("JPEG");
        assertThatThrownBy(() -> service.scan(id, service.get(id, owner).revision(), owner)).hasMessageContaining("ít nhất");
    }
    @Test void websocketEventsArePrivateInvalidationsOnlyAndDeliveryFailureDoesNotRollback() throws Exception {
        String id = open();
        doThrow(new RuntimeException("offline")).when(messaging).convertAndSendToUser(eq("owner"), eq("/queue/ocr-capture"), any(Object.class));
        service.upload(id, UUID.randomUUID().toString(), "FRONT", photo(1), owner);
        assertThat(service.get(id, owner).images()).hasSize(1);
        verify(messaging).convertAndSendToUser("owner", "/queue/ocr-capture", Map.of("sessionId", id));
        assertThat(HrOcrSocketPolicy.allowed(StompCommand.SUBSCRIBE, "/user/queue/ocr-capture")).isTrue();
        assertThat(HrOcrSocketPolicy.allowed(StompCommand.SUBSCRIBE, "/user/queue/notifications")).isTrue();
        for (String destination : List.of("/queue/ocr-capture-user123", "/user/other/queue/ocr-capture", "/queue/**", "/**"))
            assertThat(HrOcrSocketPolicy.allowed(StompCommand.SUBSCRIBE, destination)).isFalse();
        assertThat(HrOcrSocketPolicy.allowed(StompCommand.SEND, "/user/queue/ocr-capture")).isFalse();
    }
}
