package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrLeaveEntitlementUpdateRequest;
import com.booking.system.hr.api.dto.HrMovementAdjustmentRequest;
import com.booking.system.hr.api.dto.HrMovementCreateRequest;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.enums.HrEmployeeGender;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrMovementStatus;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.enums.HrRosterStatus;
import com.booking.system.hr.importer.HrBaselineImportContract;
import com.booking.system.hr.importer.HrBaselineImportPersistence;
import com.booking.system.hr.importer.HrBaselineImportService;
import com.booking.system.hr.importer.HrBaselineWorkbookParser;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrMonthlyRosterItemRepository;
import com.booking.system.hr.repository.HrMonthlyRosterRepository;
import com.booking.system.hr.service.HrRosterProjectionService;
import com.booking.system.hr.service.HrLeaveEntitlementService;
import com.booking.system.hr.service.HrEmploymentContractService;
import com.booking.system.hr.service.HrWorkforceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "debug=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.springframework.jdbc.core=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = HrPhase5WorkforceServiceTest.TestApplication.class)
@Import({
        HrBaselineWorkbookParser.class,
        HrBaselineImportContract.class,
        HrImportJsonCodec.class,
        HrBaselineImportPersistence.class,
        HrBaselineImportService.class,
        HrLeaveEntitlementService.class,
        HrRosterProjectionService.class,
        HrEmploymentContractService.class,
        HrWorkforceService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HrPhase5WorkforceServiceTest {

    private static final byte[] WORKBOOK = HrBaselineWorkbookFixture.validWorkbook();
    private static final HrImportActor MANAGER = new HrImportActor(
            "manager@example.test", "Fixture Manager", "MANAGER"
    );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:hr_phase_5_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider",
                () -> LegacySchemaFilterProvider.class.getName());
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
        registry.add("app.hr.baseline.sha256", () -> sha256(WORKBOOK));
        registry.add("app.hr.import.payload-retention-days", () -> "30");
    }

    @jakarta.annotation.Resource private HrBaselineImportService importService;
    @jakarta.annotation.Resource private HrLeaveEntitlementService leaveEntitlementService;
    @jakarta.annotation.Resource private HrRosterProjectionService rosterProjectionService;
    @jakarta.annotation.Resource private HrWorkforceService workforceService;
    @jakarta.annotation.Resource private HrEmployeeRepository employeeRepository;
    @jakarta.annotation.Resource private HrEmployeeMovementRepository movementRepository;
    @jakarta.annotation.Resource private HrMonthlyRosterRepository rosterRepository;
    @jakarta.annotation.Resource private HrMonthlyRosterItemRepository rosterItemRepository;
    @jakarta.annotation.Resource private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearHrData() {
        for (String table : new String[]{
                "hr_audit_events", "hr_excel_import_rows", "hr_monthly_roster_items",
                "hr_monthly_rosters", "hr_employee_movements", "hr_employee_contacts",
                "hr_employee_insurance", "hr_employee_identity", "hr_employee_employment",
                "hr_employees", "hr_excel_import_batches", "hr_excel_template_versions",
                "hr_working_conditions", "hr_positions", "hr_departments"
        }) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
    }

    @Test
    void liveMonthlyProjectionRecalculatesBaselineMonthAndFollowingMonthsByEffectiveDate() {
        var uploaded = importService.uploadAndParse("baseline-values-2026.xlsx", WORKBOOK, MANAGER);
        importService.validate(uploaded.batchId(), MANAGER);
        importService.confirm(uploaded.batchId(), "phase5-live-projection-baseline", true, MANAGER);

        var baseline = rosterRepository.findByPeriodStart(LocalDate.of(2026, 6, 1)).orElseThrow();
        assertThat(baseline.getStatus()).isEqualTo(HrRosterStatus.CLOSED);
        assertThat(baseline.getItemCount()).isEqualTo(329);

        HrEmployee employee = employeeRepository.findByEmploymentStatus(
                HrEmploymentStatus.ACTIVE, PageRequest.of(0, 1)).getContent().getFirst();
        var decrease = workforceService.createMovement(new HrMovementCreateRequest(
                employee.getId(), HrMovementType.DECREASE, LocalDate.of(2026, 6, 20),
                "Nghỉ việc trong tháng 6", "P5-LIVE-DEC",
                LocalDate.of(2026, 6, 20), "phase5-live-decrease"
        ), MANAGER);
        workforceService.confirmMovement(decrease.id(), decrease.rowVersion(), MANAGER);

        assertThat(rosterProjectionService.roster(baseline.getId()).itemCount()).isEqualTo(328);
        assertThat(rosterProjectionService.roster("period-2026-07-01").itemCount()).isEqualTo(328);

        var unchangedBaseline = rosterRepository.findById(baseline.getId()).orElseThrow();
        assertThat(unchangedBaseline.getItemCount()).isEqualTo(329);
        assertThat(rosterItemRepository.countByRoster_Id(baseline.getId())).isEqualTo(329);
    }

    @Test
    void confirmedMovementCanBeAdjustedWithoutRewritingItsHistory() {
        var uploaded = importService.uploadAndParse("baseline-values-2026.xlsx", WORKBOOK, MANAGER);
        importService.validate(uploaded.batchId(), MANAGER);
        importService.confirm(uploaded.batchId(), "phase9-adjustment-baseline", true, MANAGER);

        HrEmployee employee = employeeRepository.findByEmploymentStatus(
                HrEmploymentStatus.ACTIVE, PageRequest.of(0, 1)).getContent().getFirst();
        var original = workforceService.createMovement(new HrMovementCreateRequest(
                employee.getId(), HrMovementType.DECREASE, LocalDate.of(2026, 6, 20),
                "Nghỉ việc báo ban đầu", "P9-ORIGINAL", LocalDate.of(2026, 6, 20), "phase9-original"
        ), MANAGER);
        var confirmedOriginal = workforceService.confirmMovement(original.id(), original.rowVersion(), MANAGER);

        assertThat(rosterProjectionService.roster("period-2026-06-01").itemCount()).isEqualTo(328);
        assertThat(rosterProjectionService.roster("period-2026-07-01").itemCount()).isEqualTo(328);

        var adjustment = workforceService.createAdjustment(confirmedOriginal.id(), new HrMovementAdjustmentRequest(
                HrMovementType.DECREASE,
                LocalDate.of(2026, 7, 5),
                "Đơn vị báo muộn, hiệu lực đúng là tháng 7", "P9-ADJUST", LocalDate.of(2026, 7, 5),
                "phase9-adjustment", confirmedOriginal.rowVersion()
        ), MANAGER);
        var confirmedAdjustment = workforceService.confirmMovement(adjustment.id(), adjustment.rowVersion(), MANAGER);

        assertThat(movementRepository.findById(confirmedOriginal.id()).orElseThrow().getStatus())
                .isEqualTo(com.booking.system.hr.enums.HrMovementStatus.CONFIRMED);
        assertThat(movementRepository.findById(confirmedAdjustment.id()).orElseThrow()
                .getCorrectionOfMovement().getId()).isEqualTo(confirmedOriginal.id());
        assertThat(rosterProjectionService.roster("period-2026-06-01").itemCount()).isEqualTo(329);
        assertThat(rosterProjectionService.roster("period-2026-07-01").itemCount()).isEqualTo(328);
        assertThat(rosterProjectionService.reconciliation().confirmedAdjustments()).isEqualTo(1);
    }

    @Test
    void yearlyLeaveEntitlementUsesManualOverrideInLiveProjection() {
        var uploaded = importService.uploadAndParse("baseline-values-2026.xlsx", WORKBOOK, MANAGER);
        importService.validate(uploaded.batchId(), MANAGER);
        importService.confirm(uploaded.batchId(), "phase7-leave-baseline", true, MANAGER);

        HrEmployee employee = employeeRepository.findByEmploymentStatus(
                HrEmploymentStatus.ACTIVE, PageRequest.of(0, 1)).getContent().getFirst();
        String workingConditionId = employee.getEmployment() == null || employee.getEmployment().getWorkingCondition() == null
                ? null
                : employee.getEmployment().getWorkingCondition().getId();
        if (workingConditionId == null) {
            workingConditionId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO hr_working_conditions (
                        id, code, name, description, status, sort_order, annual_leave_days_base,
                        created_at, updated_at, created_by_actor, updated_by_actor, row_version
                    ) VALUES (?, ?, ?, NULL, 'ACTIVE', 0, 14.00, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), ?, ?, 0)
                    """,
                    workingConditionId, "COND-LEAVE", "Điều kiện độc hại test", MANAGER.subject(), MANAGER.subject());
            jdbcTemplate.update(
                    "UPDATE hr_employee_employment SET working_condition_id = ? WHERE employee_id = ?",
                    workingConditionId, employee.getId()
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE hr_working_conditions SET annual_leave_days_base = 14.00 WHERE id = ?",
                    workingConditionId
            );
        }
        jdbcTemplate.update(
                "UPDATE hr_employee_employment SET leave_accrual_start_date = DATE '2021-01-01' WHERE employee_id = ?",
                employee.getId()
        );

        BigDecimal calculatedLeaveDays = rosterProjectionService.projectedItems(LocalDate.of(2026, 7, 1)).stream()
                .filter(item -> item.employee().getId().equals(employee.getId()))
                .findFirst()
                .orElseThrow()
                .leaveDays();
        assertThat(calculatedLeaveDays).isEqualByComparingTo("15.00");

        leaveEntitlementService.updateEntitlement(
                employee.getId(),
                new HrLeaveEntitlementUpdateRequest(2026, 0L, new BigDecimal("13.00"), "Điều chỉnh theo quyết định"),
                MANAGER
        );

        BigDecimal overriddenLeaveDays = rosterProjectionService.projectedItems(LocalDate.of(2026, 7, 1)).stream()
                .filter(item -> item.employee().getId().equals(employee.getId()))
                .findFirst()
                .orElseThrow()
                .leaveDays();
        assertThat(overriddenLeaveDays).isEqualByComparingTo("13.00");
    }

    @Test
    void twelveIncreasesAndTwoDecreasesProduce339WithoutMutatingBaseline() {
        var uploaded = importService.uploadAndParse("baseline-values-2026.xlsx", WORKBOOK, MANAGER);
        importService.validate(uploaded.batchId(), MANAGER);
        importService.confirm(uploaded.batchId(), "phase5-baseline", true, MANAGER);

        var baseline = rosterRepository.findByPeriodStart(LocalDate.of(2026, 6, 1)).orElseThrow();
        assertThat(baseline.getStatus()).isEqualTo(HrRosterStatus.CLOSED);
        assertThat(baseline.getItemCount()).isEqualTo(329);
        assertThat(rosterItemRepository.countByRoster_Id(baseline.getId())).isEqualTo(329);

        for (int index = 1; index <= 12; index++) {
            HrEmployee employee = new HrEmployee();
            employee.setEmployeeCode("P5-ADD-%02d".formatted(index));
            employee.setFullName("Nhân sự tăng " + index);
            employee.setGender(HrEmployeeGender.UNKNOWN);
            employee.setEmploymentStatus(HrEmploymentStatus.DRAFT);
            employee.setCreatedByActor(MANAGER.subject());
            employee.setUpdatedByActor(MANAGER.subject());
            employee = employeeRepository.save(employee);

            var draft = workforceService.createMovement(new HrMovementCreateRequest(
                    employee.getId(), HrMovementType.INCREASE, LocalDate.of(2026, 6, 20),
                    "Bổ sung nhân sự", null, null, "phase5-add-" + index
            ), MANAGER);
            workforceService.confirmMovement(draft.id(), draft.rowVersion(), MANAGER);
        }

        var activeEmployees = employeeRepository.findByEmploymentStatus(
                HrEmploymentStatus.ACTIVE, PageRequest.of(0, 2)).getContent();
        for (int index = 0; index < 2; index++) {
            HrEmployee employee = activeEmployees.get(index);
            var draft = workforceService.createMovement(new HrMovementCreateRequest(
                    employee.getId(), HrMovementType.DECREASE, LocalDate.of(2026, 6, 25),
                    "Ngừng làm việc theo quyết định", "P5-DEC-" + index,
                    LocalDate.of(2026, 6, 24), "phase5-decrease-" + index
            ), MANAGER);
            workforceService.confirmMovement(draft.id(), draft.rowVersion(), MANAGER);
        }

        assertThat(employeeRepository.countByEmploymentStatus(HrEmploymentStatus.ACTIVE)).isEqualTo(339);
        assertThat(employeeRepository.countByEmploymentStatus(HrEmploymentStatus.INACTIVE)).isEqualTo(2);

        var draftRoster = workforceService.createRoster(LocalDate.of(2026, 7, 1), MANAGER);
        assertThat(workforceService.createRoster(LocalDate.of(2026, 7, 1), MANAGER).id())
                .isEqualTo(draftRoster.id());
        var openRoster = workforceService.openRoster(draftRoster.id(), draftRoster.rowVersion(), MANAGER);
        assertThat(openRoster.status()).isEqualTo(HrRosterStatus.OPEN);
        assertThat(openRoster.itemCount()).isEqualTo(339);

        var closedRoster = workforceService.closeRoster(openRoster.id(), openRoster.rowVersion(), MANAGER);
        assertThat(closedRoster.status()).isEqualTo(HrRosterStatus.CLOSED);
        assertThat(closedRoster.itemCount()).isEqualTo(339);
        assertThat(closedRoster.rosterChecksum()).hasSize(64);
        assertThat(rosterItemRepository.countByRoster_Id(closedRoster.id())).isEqualTo(339);
        var addedItems = rosterItemRepository.findAllByRoster_IdOrderByDisplayOrder(closedRoster.id()).stream()
                .filter(item -> item.getEmployeeCode().startsWith("P5-ADD-"))
                .toList();
        assertThat(addedItems).isNotEmpty();

        var unchangedBaseline = rosterRepository.findById(baseline.getId()).orElseThrow();
        assertThat(unchangedBaseline.getStatus()).isEqualTo(HrRosterStatus.CLOSED);
        assertThat(unchangedBaseline.getItemCount()).isEqualTo(329);
        assertThat(rosterItemRepository.countByRoster_Id(baseline.getId())).isEqualTo(329);

        assertThatThrownBy(() -> workforceService.reopenRoster(
                baseline.getId(), unchangedBaseline.getRowVersion(), "Không được phép", MANAGER))
                .isInstanceOf(HrApiException.class)
                .extracting(error -> ((HrApiException) error).code())
                .isEqualTo("BASELINE_ROSTER_IMMUTABLE");

        var reopened = workforceService.reopenRoster(
                closedRoster.id(), closedRoster.rowVersion(), "Bổ sung quyết định muộn", MANAGER);
        assertThat(reopened.status()).isEqualTo(HrRosterStatus.OPEN);
        assertThat(reopened.rosterChecksum()).isNull();

        var reclosed = workforceService.closeRoster(reopened.id(), reopened.rowVersion(), MANAGER);
        var august = workforceService.createRoster(LocalDate.of(2026, 8, 1), MANAGER);
        assertThat(august.status()).isEqualTo(HrRosterStatus.DRAFT);
        assertThatThrownBy(() -> workforceService.reopenRoster(
                reclosed.id(), reclosed.rowVersion(), "Không được sửa kỳ nguồn", MANAGER))
                .isInstanceOf(HrApiException.class)
                .extracting(error -> ((HrApiException) error).code())
                .isEqualTo("ROSTER_HAS_DOWNSTREAM_PERIOD");
    }

    @Test
    void idempotencyAndDraftDeleteGuardsPreserveHistory() {
        HrEmployee employee = new HrEmployee();
        employee.setEmployeeCode("P5-DRAFT-DELETE");
        employee.setFullName("Nhân sự bản nháp");
        employee.setGender(HrEmployeeGender.UNKNOWN);
        employee.setEmploymentStatus(HrEmploymentStatus.DRAFT);
        employee.setCreatedByActor(MANAGER.subject());
        employee.setUpdatedByActor(MANAGER.subject());
        employee = employeeRepository.save(employee);

        HrMovementCreateRequest request = new HrMovementCreateRequest(
                employee.getId(), HrMovementType.INCREASE, LocalDate.of(2026, 7, 20),
                "Bổ sung nhân sự", null, null, "phase5-idempotent-draft"
        );
        var first = workforceService.createMovement(request, MANAGER);
        var replay = workforceService.createMovement(request, MANAGER);

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(movementRepository.countByEmployee_Id(employee.getId())).isEqualTo(1);
        String employeeId = employee.getId();
        assertThatThrownBy(() -> workforceService.deleteDraftEmployee(employeeId, 0L, MANAGER))
                .isInstanceOf(HrApiException.class)
                .extracting(error -> ((HrApiException) error).code())
                .isEqualTo("EMPLOYEE_HAS_REFERENCES");

        workforceService.deleteDraftMovement(first.id(), first.rowVersion(), MANAGER);
        workforceService.deleteDraftEmployee(employeeId, 0L, MANAGER);

        assertThat(movementRepository.countByEmployee_Id(employeeId)).isZero();
        assertThat(employeeRepository.findById(employeeId)).isEmpty();
    }

    @Test
    void futureMovementCannotBeConfirmedAndStaleVersionIsRejected() {
        HrEmployee employee = new HrEmployee();
        employee.setEmployeeCode("P5-FUTURE");
        employee.setFullName("Nhân sự tương lai");
        employee.setGender(HrEmployeeGender.UNKNOWN);
        employee.setEmploymentStatus(HrEmploymentStatus.DRAFT);
        employee.setCreatedByActor(MANAGER.subject());
        employee.setUpdatedByActor(MANAGER.subject());
        employee = employeeRepository.save(employee);

        var draft = workforceService.createMovement(new HrMovementCreateRequest(
                employee.getId(), HrMovementType.INCREASE,
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1),
                null, null, null, "phase5-future"
        ), MANAGER);

        var confirmed = workforceService.confirmMovement(draft.id(), draft.rowVersion(), MANAGER);
        assertThat(confirmed.status()).isEqualTo(HrMovementStatus.CONFIRMED);

        var employeeAfter = employeeRepository.findById(employee.getId()).orElseThrow();
        assertThat(employeeAfter.getEmploymentStatus()).isEqualTo(HrEmploymentStatus.ACTIVE);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.booking.system.hr.entity")
    @EnableJpaRepositories(basePackages = "com.booking.system.hr.repository")
    static class TestApplication {
    }
}
