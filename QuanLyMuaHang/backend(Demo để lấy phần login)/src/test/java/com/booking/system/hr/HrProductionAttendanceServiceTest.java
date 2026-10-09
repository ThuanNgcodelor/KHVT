package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrProductionAttendanceWorkbookParser;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.repository.HrAttendancePunchRepository;
import com.booking.system.hr.repository.HrAttendanceShiftAdjustmentRepository;
import com.booking.system.hr.repository.HrAttendanceSourceDayRepository;
import com.booking.system.hr.repository.HrProductionAttendanceShiftRepository;
import com.booking.system.hr.repository.HrProductionAttendanceImportRepository;
import com.booking.system.hr.repository.HrAttendanceShiftPolicyRepository;
import com.booking.system.hr.repository.HrAttendanceWorkCreditRuleRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.api.dto.HrProductionAttendanceDtos;
import com.booking.system.hr.enums.HrAttendanceIncidentScopeType;
import com.booking.system.hr.enums.HrAttendanceResolutionType;
import com.booking.system.hr.enums.HrProductionAttendanceShiftStatus;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.service.HrProductionAttendanceService;
import com.booking.system.hr.service.HrProductionAttendanceReportService;
import com.booking.system.hr.service.HrNightRewardService;
import com.booking.system.hr.service.HrProductionShiftMatcher;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"debug=false", "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = HrProductionAttendanceServiceTest.TestApplication.class)
@Import({HrProductionAttendanceWorkbookParser.class, HrProductionShiftMatcher.class,
        HrProductionAttendanceService.class, HrProductionAttendanceReportService.class, HrNightRewardService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HrProductionAttendanceServiceTest {
    private static final HrImportActor ACTOR = new HrImportActor("manager@example.test", "Manager", "MANAGER");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:hr_production_attendance;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider", () -> LegacySchemaFilterProvider.class.getName());
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    }

    @jakarta.annotation.Resource private HrProductionAttendanceService service;
    @jakarta.annotation.Resource private HrProductionAttendanceReportService reportService;
    @jakarta.annotation.Resource private HrAttendanceSourceDayRepository sourceDayRepository;
    @jakarta.annotation.Resource private HrAttendancePunchRepository punchRepository;
    @jakarta.annotation.Resource private HrProductionAttendanceShiftRepository shiftRepository;
    @jakarta.annotation.Resource private HrProductionAttendanceImportRepository productionImportRepository;
    @jakarta.annotation.Resource private HrAttendanceShiftPolicyRepository policyRepository;
    @jakarta.annotation.Resource private HrAttendanceWorkCreditRuleRepository creditRepository;
    @jakarta.annotation.Resource private HrAttendanceShiftAdjustmentRepository adjustmentRepository;
    @jakarta.annotation.Resource private HrEmployeeRepository employeeRepository;

    @Test
    void manualNightDecisionDoesNotStealPreviousNightCheckoutOrCascadeReviewBackward() throws Exception {
        HrEmployee employee = new HrEmployee();
        employee.setEmployeeCode("B901");
        employee.setFullName("Nguyễn Văn Chuỗi Ca");
        employee.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);
        employee.setCreatedByActor(ACTOR.subject());
        employee.setUpdatedByActor(ACTOR.subject());
        employee = employeeRepository.save(employee);

        var batch = service.upload("B901-consecutive-night.xlsx", consecutiveNightWorkbook(), "2026-08", ACTOR);
        try {
            var initial = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id());
            var day9 = initial.stream().filter(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 9)))
                    .findFirst().orElseThrow();
            var day10 = initial.stream().filter(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 10)))
                    .findFirst().orElseThrow();
            assertThat(day9.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.AUTO_MATCHED);
            assertThat(day10.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.AUTO_MATCHED);

            var punches = punchRepository.findByImportIdOrderByEmployeeCodeAscPunchedAtAsc(batch.id());
            var previousNightCheckout = punches.stream()
                    .filter(value -> value.getPunchedAt().equals(LocalDateTime.of(2026, 8, 10, 5, 0)))
                    .findFirst().orElseThrow();
            var day10CheckIn = punches.stream()
                    .filter(value -> value.getPunchedAt().equals(LocalDateTime.of(2026, 8, 10, 18, 0)))
                    .findFirst().orElseThrow();
            var day10CheckOut = punches.stream()
                    .filter(value -> value.getPunchedAt().equals(LocalDateTime.of(2026, 8, 11, 5, 0)))
                    .findFirst().orElseThrow();

            assertThatThrownBy(() -> service.decideShift(day10.getId(),
                    new HrProductionAttendanceDtos.ShiftDecisionRequest(
                            HrProductionAttendanceDtos.DecisionAction.CONFIRM, "CN_18_5",
                            previousNightCheckout.getId(), day10CheckOut.getId(), new BigDecimal("1.5"),
                            new BigDecimal("50000"), "Không được lấy lượt ra của ngày 9", day10.getRowVersion()),
                    ACTOR)).isInstanceOfSatisfying(HrApiException.class,
                            exception -> assertThat(exception.code())
                                    .isEqualTo("ATTENDANCE_NIGHT_PUNCH_DIRECTION_INVALID"));

            var refreshedDay10 = shiftRepository.findById(day10.getId()).orElseThrow();
            service.decideShift(refreshedDay10.getId(), new HrProductionAttendanceDtos.ShiftDecisionRequest(
                    HrProductionAttendanceDtos.DecisionAction.CONFIRM, "CN_18_5",
                    day10CheckIn.getId(), day10CheckOut.getId(), new BigDecimal("1.5"),
                    new BigDecimal("50000"), "Xác nhận đúng chuỗi ca đêm", refreshedDay10.getRowVersion()), ACTOR);

            var recalculated = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id());
            var recalculatedDay9 = recalculated.stream()
                    .filter(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 9)))
                    .findFirst().orElseThrow();
            assertThat(recalculatedDay9.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.AUTO_MATCHED);
            assertThat(recalculatedDay9.getCheckOutAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 5, 0));
        } finally {
            service.deleteImport(batch.id(), ACTOR);
        }
    }

    @Test
    void manualNightShiftDecisionAcceptsCheckoutOutsideAutomaticWindowOnExpectedDate() throws Exception {
        employeeRepository.findByEmployeeCode("B900").orElseGet(() -> {
            HrEmployee employee = new HrEmployee();
            employee.setEmployeeCode("B900");
            employee.setFullName("Nguyễn Văn Ca Đêm");
            employee.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);
            employee.setCreatedByActor(ACTOR.subject());
            employee.setUpdatedByActor(ACTOR.subject());
            return employeeRepository.save(employee);
        });

        var batch = service.upload("B900-night-manual.xlsx", nightManualOverrideWorkbook(), "2026-08", ACTOR);
        try {
            var shift = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id()).stream()
                    .filter(value -> value.getEmployeeCode().equals("B900"))
                    .filter(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 10)))
                    .findFirst().orElseThrow();
            assertThat(shift.getShiftCodeSnapshot()).isEqualTo("CN_18_5");
            assertThat(shift.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.NEEDS_REVIEW);
            assertThat(shift.getCheckInAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 18, 26));
            assertThat(shift.getCheckOutAt()).isNull();

            var punches = service.shiftPunches(shift.getId());
            var checkIn = punches.stream()
                    .filter(value -> value.punchedAt().equals(LocalDateTime.of(2026, 8, 10, 18, 26)))
                    .findFirst().orElseThrow();
            var checkOut = punches.stream()
                    .filter(value -> value.punchedAt().equals(LocalDateTime.of(2026, 8, 11, 6, 6)))
                    .findFirst().orElseThrow();

            var saved = service.decideShift(shift.getId(), new HrProductionAttendanceDtos.ShiftDecisionRequest(
                    HrProductionAttendanceDtos.DecisionAction.CONFIRM, "CN_18_5",
                    checkIn.id(), checkOut.id(), new BigDecimal("1.5"),
                    new BigDecimal("50000"), "Xác nhận ca đêm ra sau cửa tự động", shift.getRowVersion()), ACTOR);

            assertThat(saved.checkInAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 18, 26));
            assertThat(saved.checkOutAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 6, 6));
            assertThat(saved.workValue()).isEqualByComparingTo("1.5");
            assertThat(saved.nightAllowanceAmount()).isEqualByComparingTo("50000");
            assertThat(saved.resolutionType()).isEqualTo(HrAttendanceResolutionType.MANUAL_OVERRIDE);
        } finally {
            service.deleteImport(batch.id(), ACTOR);
        }
    }

    @Test
    void importsRealWorkbookPersistsRawRowsAndCalculatesB124WithoutDroppingDays() throws Exception {
        HrEmployee employee = new HrEmployee();
        employee.setEmployeeCode("B124");
        employee.setFullName("Đỗ Đình Cường");
        employee.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);
        employee.setCreatedByActor(ACTOR.subject());
        employee.setUpdatedByActor(ACTOR.subject());
        employeeRepository.save(employee);

        HrEmployee vanNhut = new HrEmployee();
        vanNhut.setEmployeeCode("B128");
        vanNhut.setFullName("Phan Văn Nhựt");
        vanNhut.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);
        vanNhut.setCreatedByActor(ACTOR.subject());
        vanNhut.setUpdatedByActor(ACTOR.subject());
        employeeRepository.save(vanNhut);
        service.createEmployeePolicy(new HrProductionAttendanceDtos.CreateEmployeePolicyRequest(
                "B128", com.booking.system.hr.enums.HrAttendancePolicyGroup.PRODUCTION_WORKER,
                new BigDecimal("1.5"), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                "Ca ngày cố định 1,5 công, không tính theo tổng số phút"), ACTOR);

        byte[] workbook = Files.readAllBytes(Path.of("..", "CongXn.xlsx"));
        assertThat(policyRepository.findAllByOrderByPolicyGroupAscPriorityDescCodeAsc())
                .extracting(value -> value.getCode() + ":" + value.getPolicyGroup() + ":" + value.isActive())
                .contains("CN_DAY:PRODUCTION_WORKER:true", "CN_18_5:PRODUCTION_WORKER:true");
        assertThat(policyRepository.findAllByOrderByPolicyGroupAscPriorityDescCodeAsc())
                .filteredOn(value -> "CN_18_5".equals(value.getCode()))
                .singleElement()
                .satisfies(value -> assertThat(value.isCountsTowardNightReward()).isTrue());
        assertThat(policyRepository.findAllByOrderByPolicyGroupAscPriorityDescCodeAsc())
                .filteredOn(value -> value.getPolicyGroup().name().equals("PRODUCTION_WORKER"))
                .extracting(value -> value.getCode() + ":" + value.getCheckInFrom() + "-" + value.getCheckInUntil()
                        + ":" + value.getCheckOutFrom() + "-" + value.getCheckOutUntil())
                .containsExactlyInAnyOrder(
                        "CN_DAY:04:00-09:30:12:00-23:59",
                        "CN_18_5:16:45-18:59:04:00-05:59");
        assertThat(creditRepository.findAllByOrderByPriorityDesc())
                .allMatch(value -> value.isActive())
                .hasSize(7);
        var batch = service.upload("CongXn.xlsx", workbook, "2026-08", ACTOR);

        assertThat(batch.totalRows()).isEqualTo(279);
        assertThat(batch.totalPunches()).isEqualTo(491);
        assertThat(sourceDayRepository.count()).isEqualTo(279);
        assertThat(punchRepository.count()).isEqualTo(491);
        assertThat(shiftRepository.count()).isEqualTo(279);

        var b124 = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id()).stream()
                .filter(value -> value.getEmployeeCode().equals("B124")).toList();
        var b128 = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id()).stream()
                .filter(value -> value.getEmployeeCode().equals("B128")).toList();
        assertThat(b128).filteredOn(value -> "CN_DAY".equals(value.getShiftCodeSnapshot())
                        && value.getCheckInAt() != null && value.getCheckOutAt() != null)
                .allSatisfy(value -> {
                    assertThat(value.getWorkValue()).isEqualByComparingTo("1.5");
                    assertThat(value.getExplanation()).contains("mức ca ngày cố định 1.5 công");
                });
        assertThat(b124).hasSize(31);
        List<String> untouchedB128ShiftIds = b128.stream().map(value -> value.getId()).sorted().toList();
        var august18 = b124.stream().filter(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 18)))
                .findFirst().orElseThrow();
        assertThat(august18.getShiftCodeSnapshot()).isEqualTo("CN_18_5");
        assertThat(august18.getCheckInAt()).isEqualTo(LocalDateTime.of(2026, 8, 18, 17, 24));
        assertThat(august18.getCheckOutAt()).isEqualTo(LocalDateTime.of(2026, 8, 19, 5, 49));

        var august18Checkout = punchRepository.findByImportIdAndEmployeeCodeAndWorkDateOrderByPunchedAtAsc(
                        batch.id(), "B124", LocalDate.of(2026, 8, 18)).stream()
                .filter(value -> value.getPunchedAt().equals(LocalDateTime.of(2026, 8, 18, 17, 24)))
                .findFirst().orElseThrow();
        service.decideShift(august18.getId(), new HrProductionAttendanceDtos.ShiftDecisionRequest(
                HrProductionAttendanceDtos.DecisionAction.CONFIRM, "CN_DAY", null, august18Checkout.getId(),
                new BigDecimal("1.5"), BigDecimal.ZERO,
                "Ca ngày thiếu lượt vào do máy chấm công mất điện", august18.getRowVersion()), ACTOR);

        assertThat(shiftRepository.findByImportIdAndActiveTrueAndEmployeeCodeOrderByWorkDateAsc(batch.id(), "B128"))
                .extracting(value -> value.getId())
                .containsExactlyInAnyOrderElementsOf(untouchedB128ShiftIds);

        b124 = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id()).stream()
                .filter(value -> value.getEmployeeCode().equals("B124")).toList();
        assertThat(b124).hasSize(31);
        assertThat(b124.stream().map(value -> value.getWorkValue()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .as(b124.stream().map(value -> value.getWorkDate() + ":" + value.getShiftCodeSnapshot() + ":" + value.getStatus() + ":" + value.getExplanation()).toList().toString())
                .isEqualByComparingTo("45");
        assertThat(b124).filteredOn(value -> "CN_18_5".equals(value.getShiftCodeSnapshot())).hasSize(5);
        assertThat(b124.stream().map(value -> value.getNightAllowanceAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("250000");
        assertThat(b124).filteredOn(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 18)))
                .singleElement().satisfies(value -> {
                    assertThat(value.getShiftCodeSnapshot()).isEqualTo("CN_DAY");
                    assertThat(value.getCheckInAt()).isNull();
                    assertThat(value.getCheckOutAt()).isEqualTo(LocalDateTime.of(2026, 8, 18, 17, 24));
                    assertThat(value.getNightAllowanceAmount()).isEqualByComparingTo("0");
                    assertThat(value.getResolutionType()).isEqualTo(HrAttendanceResolutionType.MANUAL_OVERRIDE);
                });
        assertThat(b124).filteredOn(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 19)))
                .singleElement().satisfies(value -> {
                    assertThat(value.getShiftCodeSnapshot()).isEqualTo("CN_DAY");
                    assertThat(value.getCheckInAt()).isEqualTo(LocalDateTime.of(2026, 8, 19, 5, 49));
                    assertThat(value.getCheckOutAt()).isEqualTo(LocalDateTime.of(2026, 8, 19, 17, 23));
                });

        var employeeOverview = service.employeeReviewSummaries(batch.id(), null, 0, 5);
        assertThat(employeeOverview.totalElements()).isEqualTo(9);
        assertThat(employeeOverview.content()).hasSize(5);
        assertThat(employeeOverview.content()).filteredOn(value -> value.employeeCode().equals("B124"))
                .singleElement()
                .satisfies(value -> {
                    assertThat(value.totalDays()).isEqualTo(31);
                    assertThat(value.proposedWorkValue()).isEqualByComparingTo("45");
                    assertThat(value.nightShifts()).isEqualTo(5);
                    assertThat(value.nightAndOvertimeShifts())
                            .isEqualTo(value.nightShifts() + value.overtimeShifts());
                    assertThat(value.nightAllowanceAmount()).isEqualByComparingTo("250000");
                });

        var september = service.upload("B124-2026-09.xlsx", septemberBoundaryWorkbook(), "2026-09", ACTOR);
        assertThat(september.totalRows()).isEqualTo(3);
        assertThat(september.totalPunches()).isEqualTo(4);
        assertThat(sourceDayRepository.count()).isEqualTo(282);
        assertThat(punchRepository.count()).isEqualTo(495);
        assertThat(shiftRepository.count()).isEqualTo(590);
        var allShiftRevisions = shiftRepository.findAll(PageRequest.of(0, 1000)).getContent();
        assertThat(allShiftRevisions).filteredOn(value -> value.isActive()).hasSize(282);
        assertThat(allShiftRevisions).filteredOn(value -> !value.isActive()).hasSize(308);

        var activeB124 = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id()).stream()
                .filter(value -> value.getEmployeeCode().equals("B124")).toList();
        assertThat(activeB124.stream().map(value -> value.getWorkValue()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("45");
        var monthBoundaryShift = activeB124.stream()
                .filter(value -> value.getWorkDate().equals(LocalDate.of(2026, 8, 31)))
                .findFirst().orElseThrow();
        assertThat(monthBoundaryShift.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.AUTO_MATCHED);
        assertThat(monthBoundaryShift.getCalculationVersion()).isEqualTo(3);
        assertThat(monthBoundaryShift.getCheckInAt()).isNotNull();
        assertThat(monthBoundaryShift.getCheckOutAt()).isEqualTo(LocalDateTime.of(2026, 8, 31, 17, 8));
        assertThat(monthBoundaryShift.getResolutionType()).isEqualTo(HrAttendanceResolutionType.NORMAL);
        assertThat(monthBoundaryShift.getWorkValue()).isEqualByComparingTo("1.5");

        var septemberShifts = shiftRepository.findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(september.id());
        assertThat(septemberShifts).hasSize(3);
        assertThat(septemberShifts.get(0).getWorkDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(septemberShifts.get(0).getShiftCodeSnapshot()).isEqualTo("CN_DAY");
        assertThat(septemberShifts.get(0).getCheckInAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 5, 30));
        assertThat(septemberShifts.get(0).getCheckOutAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 17, 30));
        assertThat(septemberShifts.get(0).getWorkValue()).isEqualByComparingTo("1.5");
        assertThat(septemberShifts.get(1).getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.NEEDS_REVIEW);
        assertThat(septemberShifts.get(1).getCheckInAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 5, 0));
        assertThat(septemberShifts.get(1).getCheckOutAt()).isNull();
        assertThat(septemberShifts.get(2).getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.NEEDS_REVIEW);
        assertThat(septemberShifts.get(2).getCheckInAt()).isEqualTo(LocalDateTime.of(2026, 9, 3, 17, 30));
        assertThat(septemberShifts.get(2).getCheckOutAt()).isNull();

        List<String> usedPunchIds = allShiftRevisions.stream().filter(value -> value.isActive())
                .flatMap(value -> java.util.stream.Stream.of(value.getCheckInPunchId(), value.getCheckOutPunchId()))
                .filter(Objects::nonNull).toList();
        assertThat(usedPunchIds).doesNotHaveDuplicates();

        var missingCheckout = septemberShifts.get(2);
        assertThat(missingCheckout.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.NEEDS_REVIEW);
        assertThat(missingCheckout.getCheckInAt()).isNotNull();
        assertThat(missingCheckout.getCheckOutAt()).isNull();

        var incident = service.createIncident(new HrProductionAttendanceDtos.CreateIncidentRequest(
                LocalDateTime.of(2026, 9, 4, 4, 0), LocalDateTime.of(2026, 9, 4, 6, 0),
                HrAttendanceIncidentScopeType.ALL, List.of(), "Mô phỏng mất lượt ra máy chấm công"), ACTOR);
        var candidates = service.analyzeIncident(incident.id(), september.id());
        assertThat(candidates).extracting(HrProductionAttendanceDtos.IncidentCandidate::shiftId)
                .contains(missingCheckout.getId());
        service.confirmIncident(incident.id(), september.id(), new HrProductionAttendanceDtos.ConfirmIncidentRequest(
                List.of(new HrProductionAttendanceDtos.IncidentSelection(
                        missingCheckout.getId(), new BigDecimal("1.5"), new BigDecimal("50000"))),
                "Xác nhận thiếu lượt ra do sự cố máy"), ACTOR);

        var resolved = shiftRepository.findById(missingCheckout.getId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.CONFIRMED);
        assertThat(resolved.getResolutionType()).isEqualTo(HrAttendanceResolutionType.DEVICE_OUTAGE);
        assertThat(resolved.getCheckOutAt()).as("Không được sinh giờ chấm giả khi xử lý sự cố").isNull();
        assertThat(resolved.getIncidentId()).isEqualTo(incident.id());
        assertThat(adjustmentRepository.findByShiftIdOrderByCreatedAtDesc(resolved.getId())).hasSize(1);
        assertThat(sourceDayRepository.count()).isEqualTo(282);
        assertThat(punchRepository.count()).isEqualTo(495);

        var overlappingAugust = service.upload("B124-overlap-2026-08.xlsx",
                duplicateAugustBoundaryWorkbook(), "2026-08", ACTOR);
        var overlappingShift = shiftRepository
                .findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(overlappingAugust.id()).get(0);
        var overlappingCheckIn = punchRepository.findByImportIdOrderByEmployeeCodeAscPunchedAtAsc(overlappingAugust.id()).get(0);
        var reservedBoundaryCheckout = punchRepository.findByImportIdOrderByEmployeeCodeAscPunchedAtAsc(september.id()).stream()
                .filter(value -> value.getPunchedAt().equals(LocalDateTime.of(2026, 9, 1, 5, 30)))
                .findFirst().orElseThrow();
        assertThat(overlappingShift.getStatus()).isEqualTo(HrProductionAttendanceShiftStatus.NEEDS_REVIEW);
        assertThatThrownBy(() -> service.decideShift(overlappingShift.getId(),
                new HrProductionAttendanceDtos.ShiftDecisionRequest(
                        HrProductionAttendanceDtos.DecisionAction.CONFIRM, "CN_18_5",
                        overlappingCheckIn.getId(), reservedBoundaryCheckout.getId(), new BigDecimal("1.5"),
                        new BigDecimal("50000"), "Thử dùng lại lượt ra đã thuộc ca khác", overlappingShift.getRowVersion()),
                ACTOR)).isInstanceOfSatisfying(HrApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ATTENDANCE_PUNCH_ALREADY_USED"));

        var exemption = service.createExemption(new HrProductionAttendanceDtos.CreateExemptionRequest(
                "B124", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), "Đi thị trường"), ACTOR);
        var cancelled = service.cancelExemption(exemption.id(),
                new HrProductionAttendanceDtos.CancelExemptionRequest("Lập nhầm thời gian", exemption.rowVersion()), ACTOR);
        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancellationReason()).isEqualTo("Lập nhầm thời gian");

        var augustShiftsForReport = shiftRepository
                .findByImportIdAndActiveTrueOrderByEmployeeCodeAscWorkDateAsc(batch.id());
        augustShiftsForReport.stream()
                .filter(value -> value.getStatus() == HrProductionAttendanceShiftStatus.NEEDS_REVIEW)
                .forEach(value -> {
                    value.setStatus(HrProductionAttendanceShiftStatus.REJECTED);
                    value.setWorkValue(BigDecimal.ZERO);
                    value.setNightAllowanceAmount(BigDecimal.ZERO);
                });
        shiftRepository.saveAll(augustShiftsForReport);
        var confirmedAugust = service.confirmImport(batch.id(), ACTOR);
        var summary = reportService.monthlySummary("2026-08");
        assertThat(summary.confirmedImports()).isEqualTo(1);
        assertThat(summary.totalEmployees()).isEqualTo(9);
        assertThat(summary.totalWorkValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(summary.employees()).filteredOn(value -> value.employeeCode().equals("B124"))
                .singleElement().satisfies(value -> {
                    assertThat(value.days()).hasSize(31);
                    assertThat(value.totalWorkValue()).isEqualByComparingTo("45");
                    assertThat(value.nightShifts()).isEqualTo(5);
                    assertThat(value.nightAllowanceAmount()).isEqualByComparingTo("250000");
                });
        var exported = reportService.exportMonthlySummary("2026-08");
        assertThat(exported.fileName()).isEqualTo("BANG_CONG_CA_SAN_XUAT_2026-08.xlsx");
        try (XSSFWorkbook exportedWorkbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(exported.content()))) {
            assertThat(exportedWorkbook.getSheet("Bảng công")).isNotNull();
            assertThat(exportedWorkbook.getSheet("Đối soát")).isNotNull();
            assertThat(exportedWorkbook.getSheet("Bảng công").getRow(3).getCell(36).getNumericCellValue())
                    .isEqualTo(45d);
        }
        org.springframework.test.util.ReflectionTestUtils.setField(reportService, "shadowMode", true);
        var shadowExport = reportService.exportMonthlySummary("2026-08");
        assertThat(shadowExport.fileName()).isEqualTo("SHADOW_BANG_CONG_CA_SAN_XUAT_2026-08.xlsx");
        try (XSSFWorkbook shadowWorkbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(shadowExport.content()))) {
            assertThat(shadowWorkbook.getSheet("Bảng công").getRow(0).getCell(0).getStringCellValue())
                    .startsWith("BẢN SHADOW - KHÔNG DÙNG TRẢ LƯƠNG");
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(reportService, "shadowMode", false);
        }
        assertThatThrownBy(() -> service.reopenImport(batch.id(),
                new HrProductionAttendanceDtos.ReopenImportRequest("Manager không được mở khóa", confirmedAugust.rowVersion()), ACTOR))
                .isInstanceOfSatisfying(HrApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PRODUCTION_ATTENDANCE_REOPEN_FORBIDDEN"));
        assertThatThrownBy(() -> service.deleteImport(batch.id(), ACTOR))
                .isInstanceOfSatisfying(HrApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PRODUCTION_ATTENDANCE_DELETE_CONFIRMED"));
        var reopened = service.reopenImport(batch.id(),
                new HrProductionAttendanceDtos.ReopenImportRequest("Điều chỉnh sau đối soát", confirmedAugust.rowVersion()),
                new HrImportActor("admin@example.test", "Admin", "ADMIN"));
        assertThat(reopened.status().name()).isEqualTo("PREVIEWED");

        service.deleteImport(overlappingAugust.id(), ACTOR);
        assertThat(productionImportRepository.findById(overlappingAugust.id())).isEmpty();
    }

    private byte[] septemberBoundaryWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Thang 09");
            var header = sheet.createRow(0);
            header.createCell(1).setCellValue("Mã nhân viên");
            header.createCell(2).setCellValue("Tên nhân viên");
            header.createCell(4).setCellValue("Ngày");
            header.createCell(6).setCellValue("Chấm lần 1");
            header.createCell(7).setCellValue("Chấm lần 2");

            var first = sheet.createRow(1);
            first.createCell(1).setCellValue("B124");
            first.createCell(2).setCellValue("Đỗ Đình Cường");
            first.createCell(4).setCellValue("01-Sep-26");
            first.createCell(6).setCellValue("05:30");
            first.createCell(7).setCellValue("17:30");

            var second = sheet.createRow(2);
            second.createCell(1).setCellValue("B124");
            second.createCell(2).setCellValue("Đỗ Đình Cường");
            second.createCell(4).setCellValue("02-Sep-26");
            second.createCell(6).setCellValue("05:00");

            var third = sheet.createRow(3);
            third.createCell(1).setCellValue("B124");
            third.createCell(2).setCellValue("Đỗ Đình Cường");
            third.createCell(4).setCellValue("03-Sep-26");
            third.createCell(6).setCellValue("17:30");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] duplicateAugustBoundaryWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Thang 08 trung bien");
            var header = sheet.createRow(0);
            header.createCell(1).setCellValue("Mã nhân viên");
            header.createCell(2).setCellValue("Tên nhân viên");
            header.createCell(4).setCellValue("Ngày");
            header.createCell(6).setCellValue("Chấm lần 1");

            var row = sheet.createRow(1);
            row.createCell(1).setCellValue("B124");
            row.createCell(2).setCellValue("Đỗ Đình Cường");
            row.createCell(4).setCellValue("31-Aug-26");
            row.createCell(6).setCellValue("17:40");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] nightManualOverrideWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Thang 08 ca dem");
            var header = sheet.createRow(0);
            header.createCell(1).setCellValue("Mã nhân viên");
            header.createCell(2).setCellValue("Tên nhân viên");
            header.createCell(4).setCellValue("Ngày");
            header.createCell(6).setCellValue("Chấm lần 1");
            header.createCell(7).setCellValue("Chấm lần 2");

            var nightStart = sheet.createRow(1);
            nightStart.createCell(1).setCellValue("B900");
            nightStart.createCell(2).setCellValue("Nguyễn Văn Ca Đêm");
            nightStart.createCell(4).setCellValue("10-Aug-26");
            nightStart.createCell(7).setCellValue("18:26");

            var nightEnd = sheet.createRow(2);
            nightEnd.createCell(1).setCellValue("B900");
            nightEnd.createCell(2).setCellValue("Nguyễn Văn Ca Đêm");
            nightEnd.createCell(4).setCellValue("11-Aug-26");
            nightEnd.createCell(6).setCellValue("06:06");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] consecutiveNightWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Thang 08 ca dem lien tiep");
            var header = sheet.createRow(0);
            header.createCell(1).setCellValue("Mã nhân viên");
            header.createCell(2).setCellValue("Tên nhân viên");
            header.createCell(4).setCellValue("Ngày");
            header.createCell(6).setCellValue("Chấm lần 1");
            header.createCell(7).setCellValue("Chấm lần 2");

            var day9 = sheet.createRow(1);
            day9.createCell(1).setCellValue("B901");
            day9.createCell(2).setCellValue("Nguyễn Văn Chuỗi Ca");
            day9.createCell(4).setCellValue("09-Aug-26");
            day9.createCell(7).setCellValue("18:00");

            var day10 = sheet.createRow(2);
            day10.createCell(1).setCellValue("B901");
            day10.createCell(2).setCellValue("Nguyễn Văn Chuỗi Ca");
            day10.createCell(4).setCellValue("10-Aug-26");
            day10.createCell(6).setCellValue("05:00");
            day10.createCell(7).setCellValue("18:00");

            var day11 = sheet.createRow(3);
            day11.createCell(1).setCellValue("B901");
            day11.createCell(2).setCellValue("Nguyễn Văn Chuỗi Ca");
            day11.createCell(4).setCellValue("11-Aug-26");
            day11.createCell(6).setCellValue("05:00");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.booking.system.hr.entity")
    @EnableJpaRepositories(basePackages = "com.booking.system.hr.repository")
    static class TestApplication { }
}
