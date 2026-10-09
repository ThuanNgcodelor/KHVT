package com.booking.system.hr.api;

import com.booking.system.hr.entity.HrAuditEvent;
import com.booking.system.hr.api.dto.HrRosterResponse;
import com.booking.system.hr.enums.HrRosterStatus;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrExcelImportBatchRepository;
import com.booking.system.hr.service.HrLeaveEntitlementService;
import com.booking.system.hr.service.HrRosterProjectionService;
import com.booking.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrActivityQueryServiceTest {

    @Mock
    private HrEmployeeMovementRepository movementRepository;
    @Mock
    private HrAuditEventRepository auditRepository;
    @Mock
    private HrExcelImportBatchRepository importBatchRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HrRosterProjectionService rosterProjectionService;
    @Mock
    private HrLeaveEntitlementService leaveEntitlementService;

    private HrActivityQueryService service;

    @BeforeEach
    void setUp() {
        service = new HrActivityQueryService(
                movementRepository,
                auditRepository,
                importBatchRepository,
                userRepository,
                rosterProjectionService,
                leaveEntitlementService
        );
    }

    @Test
    void listEndpointsClampPageAndSizeAndReturnStablePageDto() {
        HrAuditEvent event = new HrAuditEvent();
        event.setActorSubject("USER:manager-id");
        event.setActorRole("MANAGER");
        event.setAction("BASELINE_IMPORT_CONFIRMED");
        event.setEntityType("HR_IMPORT_BATCH");
        event.setChangedFields("[\"status\",\"importedRows\"]");
        event.setOccurredAt(LocalDateTime.of(2026, 7, 22, 8, 0));
        when(auditRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(event), invocation.getArgument(0), 1));

        var response = service.auditEvents(-4, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().action()).isEqualTo("BASELINE_IMPORT_CONFIRMED");
        assertThat(response.content().getFirst().changedFields())
                .isEqualTo("[\"status\",\"importedRows\"]");
    }

    @Test
    void rosterDetailReturnsStableDtoAndMissingRosterReturns404() {
        var roster = new HrRosterResponse(
                "roster-2026-06",
                LocalDate.of(2026, 6, 1),
                HrRosterStatus.OPEN,
                329,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0
        );
        when(rosterProjectionService.roster(roster.id())).thenReturn(roster);

        var response = service.roster(roster.id());

        assertThat(response.id()).isEqualTo("roster-2026-06");
        assertThat(response.periodStart()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(response.status()).isEqualTo(HrRosterStatus.OPEN);
        assertThat(response.itemCount()).isEqualTo(329);

        when(rosterProjectionService.roster("missing")).thenThrow(HrApiException.notFound(
                "HR_ROSTER_NOT_FOUND", "Không tìm thấy danh sách nhân sự tháng."));
        assertThatThrownBy(() -> service.roster("missing"))
                .isInstanceOf(HrApiException.class)
                .satisfies(exception -> {
                    HrApiException apiError = (HrApiException) exception;
                    assertThat(apiError.status().value()).isEqualTo(404);
                    assertThat(apiError.code()).isEqualTo("HR_ROSTER_NOT_FOUND");
                });
    }

    @Test
    void rosterItemsReturn404ContractWhenRosterDoesNotExist() {
        when(rosterProjectionService.rosterItems("missing", 0, 20)).thenThrow(HrApiException.notFound(
                "HR_ROSTER_NOT_FOUND", "Không tìm thấy danh sách nhân sự tháng."));

        assertThatThrownBy(() -> service.rosterItems("missing", 0, 20))
                .isInstanceOf(HrApiException.class)
                .satisfies(exception -> assertThat(((HrApiException) exception).status().value()).isEqualTo(404));
    }
}
