package com.booking.system.hr;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrTelegramDtos;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeTelegramBinding;
import com.booking.system.hr.entity.HrSystemSetting;
import com.booking.system.hr.entity.HrTelegramRegistration;
import com.booking.system.hr.enums.HrTelegramBindingStatus;
import com.booking.system.hr.enums.HrTelegramRegistrationStatus;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrEmployeeTelegramBindingRepository;
import com.booking.system.hr.repository.HrSystemSettingRepository;
import com.booking.system.hr.repository.HrTelegramRegistrationRepository;
import com.booking.system.hr.service.HrTelegramService;
import com.booking.system.hr.service.TelegramBotClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrTelegramServiceTest {

    private static final HrImportActor ACTOR = new HrImportActor("USER:admin", "Admin", "ADMIN");

    @Mock private HrTelegramRegistrationRepository registrationRepository;
    @Mock private HrEmployeeTelegramBindingRepository bindingRepository;
    @Mock private HrEmployeeRepository employeeRepository;
    @Mock private HrSystemSettingRepository settingRepository;
    @Mock private HrAuditEventRepository auditRepository;
    @Mock private TelegramBotClient botClient;
    @Mock private com.booking.system.hr.service.HrPayrollTestRecipientService payrollTestRecipientService;

    private HrTelegramService service;

    @BeforeEach
    void setUp() {
        service = new HrTelegramService(registrationRepository, bindingRepository, employeeRepository,
                settingRepository, auditRepository, new HrImportJsonCodec(), botClient,
                payrollTestRecipientService);
        ReflectionTestUtils.setField(service, "webhookSecret", "webhook-secret");
    }

    @Test
    void revokeRequiresActiveBindingAndPersistsReason() {
        HrEmployee employee = employee("employee-1", "D042", "Võ Nghĩa Hòa");
        HrEmployeeTelegramBinding binding = new HrEmployeeTelegramBinding();
        binding.setId("binding-1");
        binding.setEmployee(employee);
        binding.setStatus(HrTelegramBindingStatus.ACTIVE);
        when(bindingRepository.findByEmployeeIdForUpdate("employee-1")).thenReturn(Optional.of(binding));

        service.revoke("employee-1", new HrTelegramDtos.RevokeRequest("  Đổi tài khoản Telegram  "), ACTOR);

        assertThat(binding.getStatus()).isEqualTo(HrTelegramBindingStatus.REVOKED);
        assertThat(binding.getRevokedReason()).isEqualTo("Đổi tài khoản Telegram");
        assertThat(binding.getRevokedAt()).isNotNull();
        verify(bindingRepository).save(binding);
        verify(auditRepository).save(any());

        assertThatThrownBy(() -> service.revoke("employee-1",
                new HrTelegramDtos.RevokeRequest("Thu hồi lần nữa"), ACTOR))
                .isInstanceOf(HrApiException.class)
                .extracting(error -> ((HrApiException) error).code())
                .isEqualTo("TELEGRAM_BINDING_ALREADY_REVOKED");
    }

    @Test
    void revokeRejectsBlankReasonBeforeChangingBinding() {
        assertThatThrownBy(() -> service.revoke("employee-1", new HrTelegramDtos.RevokeRequest("  "), ACTOR))
                .isInstanceOf(HrApiException.class)
                .extracting(error -> ((HrApiException) error).code())
                .isEqualTo("TELEGRAM_REVOKE_REASON_REQUIRED");
        verify(bindingRepository, never()).findByEmployeeIdForUpdate(any());
    }

    @Test
    void startResumesPhoneReceivedRegistrationWithoutResettingIt() {
        enableRegistration();
        HrTelegramRegistration registration = new HrTelegramRegistration();
        registration.setStatus(HrTelegramRegistrationStatus.PHONE_RECEIVED);
        registration.setPhoneNumber("084388509046");
        registration.setAttemptCount(2);
        when(bindingRepository.findActiveByTelegramUserId(2043568560L, HrTelegramBindingStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(registrationRepository.findTopByTelegramUserIdOrderByCreatedAtDesc(2043568560L))
                .thenReturn(Optional.of(registration));
        when(registrationRepository.findTopByTelegramUserIdAndStatusInOrderByCreatedAtDesc(
                eq(2043568560L), any())).thenReturn(Optional.of(registration));

        service.handleWebhook("webhook-secret", startUpdate(2043568560L, 2043568560L));

        assertThat(registration.getStatus()).isEqualTo(HrTelegramRegistrationStatus.PHONE_RECEIVED);
        assertThat(registration.getPhoneNumber()).isEqualTo("084388509046");
        assertThat(registration.getAttemptCount()).isEqualTo(2);
        verify(registrationRepository).save(registration);
        verify(botClient).sendText(eq(2043568560L), org.mockito.ArgumentMatchers.contains("nhập Mã nhân viên"));
        verify(botClient, never()).sendContactRequest(any());
    }

    @Test
    void payTestStartTokenIsHandledWithoutCreatingEmployeeRegistration() {
        Map<String, Object> update = Map.of("message", Map.of(
                "from", Map.of("id", 2043568560L, "username", "admin_test"),
                "chat", Map.of("id", 2043568560L, "type", "private"),
                "text", "/start paytest_one-time-token"));

        service.handleWebhook("webhook-secret", update);

        verify(payrollTestRecipientService).handleStartToken(
                "one-time-token", 2043568560L, 2043568560L, "admin_test");
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void employeeStatusUsesRevokedBindingInsteadOfHistoricalVerifiedRegistration() {
        HrEmployee employee = employee("employee-1", "D042", "Võ Nghĩa Hòa");
        HrEmployeeTelegramBinding binding = new HrEmployeeTelegramBinding();
        binding.setEmployee(employee);
        binding.setStatus(HrTelegramBindingStatus.REVOKED);
        binding.setTelegramUserId(2043568560L);
        binding.setRevokedAt(LocalDateTime.of(2026, 9, 14, 9, 30));
        binding.setRevokedReason("Đổi tài khoản Telegram");
        HrTelegramRegistration historicalRegistration = new HrTelegramRegistration();
        historicalRegistration.setEmployee(employee);
        historicalRegistration.setStatus(HrTelegramRegistrationStatus.VERIFIED);

        when(employeeRepository.findAllByEmploymentStatusOrderByEmployeeCode(any()))
                .thenReturn(List.of(employee));
        when(bindingRepository.findAllByEmployeeIdIn(List.of("employee-1"))).thenReturn(List.of(binding));
        when(registrationRepository.findAllByEmployeeIdInOrderByCreatedAtDesc(List.of("employee-1")))
                .thenReturn(List.of(historicalRegistration));

        var page = service.employeeStatuses(null, "REVOKED", PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().status()).isEqualTo("REVOKED");
        assertThat(page.getContent().getFirst().revokedReason()).isEqualTo("Đổi tài khoản Telegram");
        assertThat(page.getContent().getFirst().revokedAt()).isEqualTo(LocalDateTime.of(2026, 9, 14, 9, 30));
    }

    private void enableRegistration() {
        HrSystemSetting enabled = new HrSystemSetting();
        enabled.setSettingValue("true");
        when(settingRepository.findBySettingKey("telegram.bot.username")).thenReturn(Optional.empty());
        when(settingRepository.findBySettingKey("telegram.enabled")).thenReturn(Optional.of(enabled));
    }

    private static Map<String, Object> startUpdate(long userId, long chatId) {
        return Map.of("message", Map.of(
                "from", Map.of("id", userId, "username", "telegram_user"),
                "chat", Map.of("id", chatId, "type", "private"),
                "text", "/start register"));
    }

    private static HrEmployee employee(String id, String code, String name) {
        HrEmployee employee = new HrEmployee();
        employee.setId(id);
        employee.setEmployeeCode(code);
        employee.setFullName(name);
        return employee;
    }
}
