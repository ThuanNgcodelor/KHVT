package com.booking.system.hr;

import com.booking.system.entity.User;
import com.booking.system.hr.api.dto.HrPayrollDtos;
import com.booking.system.hr.entity.*;
import com.booking.system.hr.enums.HrPayrollDeliveryStatus;
import com.booking.system.hr.enums.HrPayrollRowStatus;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.*;
import com.booking.system.hr.service.HrPayrollTestRecipientService;
import com.booking.system.hr.service.TelegramBotClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrPayrollTestRecipientServiceTest {
    private static final HrImportActor ACTOR = new HrImportActor("USER:admin", "Admin", "ADMIN");
    @Mock HrPayrollTestRecipientRepository recipients;
    @Mock HrPayrollTestDeliveryRepository testDeliveries;
    @Mock HrPayrollImportRowRepository rows;
    @Mock HrSystemSettingRepository settings;
    @Mock HrAuditEventRepository audits;
    @Mock TelegramBotClient bot;
    HrPayrollTestRecipientService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new HrPayrollTestRecipientService(recipients, testDeliveries, rows, settings, audits,
                new HrImportJsonCodec(), bot);
        user = new User(); user.setId("user-1"); user.setEmail("admin@example.test"); user.setFullName("Admin");
    }

    @Test
    void createsOneTimeBotLinkWithoutPersistingPlainToken() {
        HrSystemSetting username = new HrSystemSetting(); username.setSettingValue("@cfc_payroll_bot");
        when(settings.findBySettingKey("telegram.bot.username")).thenReturn(Optional.of(username));
        when(recipients.findByUserId("user-1")).thenReturn(Optional.empty());
        when(recipients.save(any())).thenAnswer(invocation -> {
            HrPayrollTestRecipient value = invocation.getArgument(0); value.setId("recipient-1"); return value;
        });

        HrPayrollDtos.TestRecipientResponse response = service.createLink(user, ACTOR);

        assertThat(response.linkUrl()).startsWith("https://t.me/cfc_payroll_bot?start=paytest_");
        ArgumentCaptor<HrPayrollTestRecipient> saved = ArgumentCaptor.forClass(HrPayrollTestRecipient.class);
        verify(recipients).save(saved.capture());
        String plainToken = response.linkUrl().substring(response.linkUrl().indexOf("paytest_") + 8);
        assertThat(saved.getValue().getLinkTokenHash()).hasSize(64).doesNotContain(plainToken);
        assertThat(saved.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void sendsSkippedEmployeePayloadOnlyToActiveTestRecipient() {
        HrPayrollTestRecipient recipient = new HrPayrollTestRecipient();
        recipient.setId("recipient-1"); recipient.setStatus("ACTIVE"); recipient.setTelegramChatId(999L);
        when(recipients.findByUserId("user-1")).thenReturn(Optional.of(recipient));
        HrPayrollImport payrollImport = new HrPayrollImport(); payrollImport.setId("import-1"); payrollImport.setPayrollMonth("2026-07");
        HrPayrollImportRow row = new HrPayrollImportRow();
        row.setId("row-1"); row.setPayrollImport(payrollImport); row.setEmployeeCode("D042"); row.setEmployeeName("Võ Nghĩa Hòa");
        row.setStatus(HrPayrollRowStatus.SKIPPED);
        row.setPayloadJson("{\"cong\":26,\"tienLuong\":14797000,\"tongThu\":1070000,\"nganHangChuyen\":13727000}");
        when(rows.findByIdAndPayrollImportId("row-1", "import-1")).thenReturn(Optional.of(row));
        when(testDeliveries.save(any())).thenAnswer(invocation -> {
            HrPayrollTestDelivery value = invocation.getArgument(0); value.setId("test-1"); return value;
        });
        when(bot.sendPayrollPdf(eq(999L), any(), any(), any())).thenReturn(new TelegramBotClient.PayrollSendResult(true, null));

        var result = service.sendTest("import-1", "row-1", user, ACTOR);

        assertThat(result.status()).isEqualTo(HrPayrollDeliveryStatus.SENT);
        assertThat(row.getStatus()).isEqualTo(HrPayrollRowStatus.SKIPPED);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> document = ArgumentCaptor.forClass(byte[].class);
        verify(bot).sendPayrollPdf(eq(999L), document.capture(), any(), message.capture());
        assertThat(document.getValue()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        assertThat(message.getValue())
                .startsWith("⚠️ BẢN GỬI THỬ - KHÔNG PHẢI PHIẾU LƯƠNG CHÍNH THỨC")
                .contains("Dữ liệu nguồn: D042 - Võ Nghĩa Hòa")
                .contains("KHOẢN THU TRONG LƯƠNG", "13.727.000 đ");
    }
}
