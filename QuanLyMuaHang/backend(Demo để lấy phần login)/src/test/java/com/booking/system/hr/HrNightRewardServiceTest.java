package com.booking.system.hr;

import com.booking.system.hr.api.dto.HrNightRewardDtos;
import com.booking.system.hr.entity.HrAttendanceShiftPolicy;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrNightRewardMonthlyQualification;
import com.booking.system.hr.entity.HrNightRewardProgram;
import com.booking.system.hr.entity.HrProductionAttendanceImport;
import com.booking.system.hr.entity.HrProductionAttendanceShift;
import com.booking.system.hr.enums.HrAttendanceImportStatus;
import com.booking.system.hr.enums.HrAttendancePolicyGroup;
import com.booking.system.hr.enums.HrAttendanceResolutionType;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrNightRewardEntitlementStatus;
import com.booking.system.hr.enums.HrNightRewardMonthlyStatus;
import com.booking.system.hr.enums.HrProductionAttendanceShiftStatus;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.repository.*;
import com.booking.system.hr.service.HrNightRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrNightRewardServiceTest {
    private static final String MONTH = "2026-08";
    private static final HrImportActor ADMIN = new HrImportActor("admin@test.local", "Admin", "ADMIN");

    @Mock private HrNightRewardProgramRepository programRepository;
    @Mock private HrNightRewardMonthlyQualificationRepository qualificationRepository;
    @Mock private HrNightRewardQualificationShiftRepository qualificationShiftRepository;
    @Mock private HrNightRewardExceptionRepository exceptionRepository;
    @Mock private HrNightRewardLedgerEntryRepository ledgerRepository;
    @Mock private HrNightRewardEntitlementRepository entitlementRepository;
    @Mock private HrProductionAttendanceImportRepository attendanceImportRepository;
    @Mock private HrProductionAttendanceShiftRepository attendanceShiftRepository;
    @Mock private HrAttendanceShiftPolicyRepository shiftPolicyRepository;
    @Mock private HrEmployeeRepository employeeRepository;
    @Mock private HrAuditEventRepository auditRepository;
    @InjectMocks private HrNightRewardService service;

    private final AtomicReference<HrNightRewardMonthlyQualification> finalized = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        HrNightRewardProgram program = new HrNightRewardProgram();
        program.setId("program");
        program.setCode("NIGHT_REWARD");
        program.setName("Thưởng ca đêm");
        program.setEffectiveFrom(LocalDate.of(2026, 8, 1));
        program.setQualifyingNightThreshold(10);
        program.setEligiblePolicyGroup(HrAttendancePolicyGroup.PRODUCTION_WORKER);
        when(programRepository.findEffective(LocalDate.of(2026, 8, 1))).thenReturn(Optional.of(program));

        HrProductionAttendanceImport confirmed = new HrProductionAttendanceImport();
        confirmed.setId("confirmed");
        confirmed.setSourceFileName("Bổ sung XNHC T8.2026.xlsx");
        HrProductionAttendanceImport pending = new HrProductionAttendanceImport();
        pending.setId("pending");
        pending.setSourceFileName("XNPBHC T8.2026.xlsx");
        when(attendanceImportRepository.findByAttendanceMonthAndStatusOrderByCreatedAtAsc(
                MONTH, HrAttendanceImportStatus.CONFIRMED)).thenReturn(List.of(confirmed));
        when(attendanceImportRepository.findByAttendanceMonthAndStatusOrderByCreatedAtAsc(
                MONTH, HrAttendanceImportStatus.PREVIEWED)).thenReturn(List.of(pending));

        HrEmployee employee = new HrEmployee();
        employee.setId("employee");
        employee.setEmployeeCode("C595");
        employee.setFullName("Huỳnh Văn Linh");
        employee.setEmploymentStatus(HrEmploymentStatus.ACTIVE);
        when(employeeRepository.findAllByEmployeeCodeIn(anyCollection())).thenReturn(List.of(employee));

        HrAttendanceShiftPolicy policy = new HrAttendanceShiftPolicy();
        policy.setId("night-policy");
        policy.setCountsTowardNightReward(true);
        when(shiftPolicyRepository.findAllByOrderByPolicyGroupAscPriorityDescCodeAsc()).thenReturn(List.of(policy));

        List<HrProductionAttendanceShift> shifts = java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(day -> {
            HrProductionAttendanceShift shift = new HrProductionAttendanceShift();
            shift.setId("shift-" + day);
            shift.setImportId("confirmed");
            shift.setEmployeeCode("C595");
            shift.setWorkDate(LocalDate.of(2026, 8, day));
            shift.setPolicyGroup(HrAttendancePolicyGroup.PRODUCTION_WORKER);
            shift.setShiftPolicyId("night-policy");
            shift.setShiftCodeSnapshot("CN_18_5");
            shift.setStatus(HrProductionAttendanceShiftStatus.CONFIRMED);
            shift.setResolutionType(HrAttendanceResolutionType.NORMAL);
            shift.setWorkValue(BigDecimal.ONE);
            return shift;
        }).toList();
        when(attendanceShiftRepository.findActiveByAttendanceMonthAndImportStatus(
                MONTH, HrAttendanceImportStatus.CONFIRMED)).thenReturn(shifts);
        when(attendanceShiftRepository.findActiveImportIdsByPolicyGroup(
                List.of("pending"), HrAttendancePolicyGroup.PRODUCTION_WORKER)).thenReturn(List.of("pending"));
        when(exceptionRepository.findByProgramIdAndEmployeeIdInAndAttendanceMonthOrderByCreatedAtDesc(
                eq("program"), any(), eq(MONTH))).thenReturn(List.of());
        when(qualificationRepository.findCurrentForEmployees(eq("program"), any(), eq(HrNightRewardMonthlyStatus.STALE)))
                .thenAnswer(invocation -> finalized.get() == null ? List.of() : List.of(finalized.get()));
        when(qualificationRepository.findFirstByProgramIdAndEmployeeIdAndAttendanceMonthOrderByRevisionDesc(
                "program", "employee", MONTH))
                .thenAnswer(invocation -> Optional.ofNullable(finalized.get()));
        when(entitlementRepository.findByProgramIdAndEmployeeIdAndStatus(
                "program", "employee", HrNightRewardEntitlementStatus.DRAFT)).thenReturn(List.of());
    }

    @Test
    void finalizesConfirmedEmployeesWhenPendingFileHasDifferentEmployees() {
        when(attendanceShiftRepository.findActiveEmployeeCodesByImportIdsAndPolicyGroup(
                List.of("pending"), HrAttendancePolicyGroup.PRODUCTION_WORKER)).thenReturn(List.of("00186"));
        when(qualificationRepository.save(any())).thenAnswer(invocation -> {
            HrNightRewardMonthlyQualification value = invocation.getArgument(0);
            value.setId("qualification");
            finalized.set(value);
            return value;
        });
        when(qualificationRepository.findByProgramIdAndEmployeeIdOrderByAttendanceMonthAscRevisionAsc(
                "program", "employee")).thenAnswer(invocation -> List.of(finalized.get()));
        var preview = service.preview(MONTH);
        assertThat(preview.readyToFinalize()).isTrue();
        assertThat(preview.notices()).anyMatch(value -> value.contains("XNPBHC T8.2026.xlsx"));
        assertThat(preview.employees()).singleElement().satisfies(value -> {
            assertThat(value.actualNightShiftCount()).isEqualTo(12);
            assertThat(value.progress().periodicQualifiedMonths()).isZero();
        });

        var result = service.finalizeMonth(MONTH, new HrNightRewardDtos.FinalizeMonthRequest("Đối soát tháng 8"), ADMIN);
        assertThat(result.createdQualifications()).isEqualTo(1);
        assertThat(service.preview(MONTH).employees()).singleElement().satisfies(value -> {
            assertThat(value.finalizedStatus()).isEqualTo(HrNightRewardMonthlyStatus.QUALIFIED);
            assertThat(value.progress().periodicQualifiedMonths()).isEqualTo(1);
            assertThat(value.progress().loyaltyQualifiedMonths()).isEqualTo(1);
        });

        var repeated = service.finalizeMonth(MONTH, new HrNightRewardDtos.FinalizeMonthRequest("Đối soát lại"), ADMIN);
        assertThat(repeated.createdQualifications()).isZero();
        assertThat(repeated.unchangedQualifications()).isEqualTo(1);
        assertThat(service.preview(MONTH).employees()).singleElement().satisfies(value -> {
            assertThat(value.progress().periodicQualifiedMonths()).isEqualTo(1);
            assertThat(value.progress().loyaltyQualifiedMonths()).isEqualTo(1);
        });
    }

    @Test
    void blocksWhenPendingFileContainsTheSameEmployee() {
        when(attendanceShiftRepository.findActiveEmployeeCodesByImportIdsAndPolicyGroup(
                List.of("pending"), HrAttendancePolicyGroup.PRODUCTION_WORKER)).thenReturn(List.of("C595"));
        var preview = service.preview(MONTH);
        assertThat(preview.readyToFinalize()).isFalse();
        assertThat(preview.blockers()).anyMatch(value -> value.contains("C595"));
        assertThat(preview.notices()).isEmpty();
    }
}
