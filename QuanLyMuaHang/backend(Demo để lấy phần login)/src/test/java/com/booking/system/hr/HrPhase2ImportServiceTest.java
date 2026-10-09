package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.enums.HrEmployeeGender;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrImportBatchStatus;
import com.booking.system.hr.enums.HrImportType;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.importer.HrBaselineImportContract;
import com.booking.system.hr.importer.HrBaselineImportException;
import com.booking.system.hr.importer.HrBaselineImportPersistence;
import com.booking.system.hr.importer.HrBaselineImportService;
import com.booking.system.hr.importer.HrBaselineWorkbookParser;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.importer.HrWorkforceSnapshotContract;
import com.booking.system.hr.importer.HrWorkforceSnapshotImportService;
import com.booking.system.hr.importer.HrWorkforceSnapshotPersistence;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrDepartmentRepository;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrExcelImportBatchRepository;
import com.booking.system.hr.repository.HrExcelImportRowRepository;
import com.booking.system.hr.repository.HrMonthlyRosterItemRepository;
import com.booking.system.hr.repository.HrMonthlyRosterRepository;
import com.booking.system.hr.repository.HrPositionRepository;
import com.booking.system.hr.repository.HrWorkingConditionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

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
@ContextConfiguration(classes = HrPhase2ImportServiceTest.TestApplication.class)
@Import({
        HrBaselineWorkbookParser.class,
        HrBaselineImportContract.class,
        HrImportJsonCodec.class,
        HrBaselineImportPersistence.class,
        HrBaselineImportService.class,
        HrWorkforceSnapshotContract.class,
        HrWorkforceSnapshotPersistence.class,
        HrWorkforceSnapshotImportService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HrPhase2ImportServiceTest {

    private static final byte[] WORKBOOK = HrBaselineWorkbookFixture.validWorkbook();
    private static final byte[] WORKFORCE_SNAPSHOT =
            HrBaselineWorkbookFixture.workforceSnapshotWorkbook();
    private static final HrImportActor MANAGER = new HrImportActor(
            "manager@example.test", "Fixture Manager", "MANAGER"
    );

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String mysqlUrl = System.getenv("HR_MYSQL_JDBC_URL");
        boolean useMySql = mysqlUrl != null && !mysqlUrl.isBlank();
        registry.add("spring.datasource.url", () -> useMySql
                ? mysqlUrl
                : "jdbc:h2:mem:hr_phase_2_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> useMySql
                ? System.getenv().getOrDefault("HR_MYSQL_JDBC_USER", "root")
                : "sa");
        registry.add("spring.datasource.password", () -> useMySql
                ? System.getenv().getOrDefault("HR_MYSQL_JDBC_PASSWORD", "")
                : "");
        registry.add("spring.datasource.driver-class-name", () -> useMySql
                ? "com.mysql.cj.jdbc.Driver"
                : "org.h2.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider",
                () -> LegacySchemaFilterProvider.class.getName());
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("logging.level.org.hibernate.SQL", () -> "OFF");
        registry.add("logging.level.org.springframework.jdbc.core", () -> "OFF");
        registry.add("debug", () -> "false");
        registry.add("app.hr.baseline.sha256", () -> sha256(WORKBOOK));
        registry.add("app.hr.workforce-snapshot.sha256", () -> sha256(WORKFORCE_SNAPSHOT));
        registry.add("app.hr.import.payload-retention-days", () -> "30");
    }

    @jakarta.annotation.Resource
    private HrBaselineImportService importService;

    @jakarta.annotation.Resource
    private HrBaselineWorkbookParser workbookParser;

    @jakarta.annotation.Resource
    private HrBaselineImportPersistence importPersistence;

    @jakarta.annotation.Resource
    private HrWorkforceSnapshotImportService snapshotImportService;

    @jakarta.annotation.Resource
    private HrExcelImportBatchRepository batchRepository;

    @jakarta.annotation.Resource
    private HrExcelImportRowRepository rowRepository;

    @jakarta.annotation.Resource
    private HrEmployeeRepository employeeRepository;

    @jakarta.annotation.Resource
    private HrEmployeeMovementRepository movementRepository;

    @jakarta.annotation.Resource
    private HrMonthlyRosterRepository rosterRepository;

    @jakarta.annotation.Resource
    private HrMonthlyRosterItemRepository rosterItemRepository;

    @jakarta.annotation.Resource
    private HrDepartmentRepository departmentRepository;

    @jakarta.annotation.Resource
    private HrPositionRepository positionRepository;

    @jakarta.annotation.Resource
    private HrWorkingConditionRepository conditionRepository;

    @jakarta.annotation.Resource
    private HrAuditEventRepository auditRepository;

    @jakarta.annotation.Resource
    private JdbcTemplate jdbcTemplate;

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
    void uploadValidateConfirmIdempotentRollbackAndPurgeAreAuditable() {
        var parsed = importService.uploadAndParse("baseline-values-2026.xlsx", WORKBOOK, MANAGER);
        assertThat(parsed.status()).isEqualTo(HrImportBatchStatus.PARSED);
        assertThat(parsed.totalRows()).isEqualTo(329);
        assertThat(employeeRepository.count()).isZero();
        assertThat(movementRepository.count()).isZero();
        var uploadRetry = importService.uploadAndParse("renamed-copy.xlsx", WORKBOOK, MANAGER);
        assertThat(uploadRetry.batchId()).isEqualTo(parsed.batchId());
        assertThat(rowRepository.count()).isEqualTo(329);

        var preValidationPreview = importService.preview(parsed.batchId(), 0, 10);
        assertThat(preValidationPreview.totalElements()).isEqualTo(329);
        assertThat(preValidationPreview.rows()).hasSize(10);
        assertThat(preValidationPreview.rows().getFirst().data()).isNotNull();

        var validated = importService.validate(parsed.batchId(), MANAGER);
        assertThat(validated.status()).isEqualTo(HrImportBatchStatus.VALIDATED);
        assertThat(validated.validRows()).isEqualTo(300);
        assertThat(validated.warningRows()).isEqualTo(29);
        assertThat(validated.invalidRows()).isZero();
        if (usingMySql()) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT JSON_TYPE(raw_payload) FROM hr_excel_import_rows WHERE batch_id = ? LIMIT 1",
                    String.class,
                    parsed.batchId()
            )).isEqualTo("OBJECT");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT JSON_TYPE(normalized_payload) FROM hr_excel_import_rows WHERE batch_id = ? LIMIT 1",
                    String.class,
                    parsed.batchId()
            )).isEqualTo("OBJECT");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT JSON_TYPE(issue_codes) FROM hr_excel_import_rows WHERE batch_id = ? LIMIT 1",
                    String.class,
                    parsed.batchId()
            )).isEqualTo("ARRAY");
        }

        assertThatThrownBy(() -> importService.confirm(
                parsed.batchId(), "fixture-confirmation-1", false, MANAGER
        ))
                .isInstanceOf(HrBaselineImportException.class)
                .extracting(error -> ((HrBaselineImportException) error).getCode())
                .isEqualTo("WARNINGS_REQUIRE_ACKNOWLEDGEMENT");
        assertThat(employeeRepository.count()).isZero();

        var confirmed = importService.confirm(parsed.batchId(), "fixture-confirmation-1", true, MANAGER);
        assertThat(confirmed.status()).isEqualTo(HrImportBatchStatus.CONFIRMED);
        assertThat(confirmed.importedRows()).isEqualTo(329);
        assertThat(employeeRepository.count()).isEqualTo(329);
        assertThat(movementRepository.count()).isEqualTo(329);
        assertThat(rosterRepository.count()).isOne();
        assertThat(rosterItemRepository.count()).isEqualTo(329);
        String rosterSnapshot = rosterItemRepository.findAllByRoster_IdOrderByDisplayOrder(
                rosterRepository.findByPeriodStart(LocalDate.of(2026, 6, 1)).orElseThrow().getId()
        ).getFirst().getSnapshotPayload();
        assertThat(rosterSnapshot)
                .contains("employeeCode", "departmentName")
                .doesNotContain(
                        "socialInsuranceNumber", "healthInsuranceNumber", "legacyIdentityNumber",
                        "citizenIdentityNumber", "permanentAddress", "currentAddress", "phone",
                        "baseSalary", "allowance"
                );
        if (usingMySql()) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT JSON_TYPE(snapshot_payload) FROM hr_monthly_roster_items LIMIT 1",
                    String.class
            )).isEqualTo("OBJECT");
        }

        var retry = importService.confirm(parsed.batchId(), "fixture-confirmation-1", true, MANAGER);
        assertThat(retry.confirmedAt()).isEqualTo(confirmed.confirmedAt());
        assertThat(employeeRepository.count()).isEqualTo(329);

        var rolledBack = importService.rollback(parsed.batchId(), MANAGER);
        assertThat(rolledBack.status()).isEqualTo(HrImportBatchStatus.ROLLED_BACK);
        assertThat(rolledBack.importedRows()).isZero();
        assertThat(employeeRepository.count()).isZero();
        assertThat(movementRepository.count()).isZero();
        assertThat(rosterRepository.count()).isZero();
        assertThat(rosterItemRepository.count()).isZero();
        assertThat(rowRepository.count()).isEqualTo(329);
        assertThat(departmentRepository.count()).isPositive();
        assertThat(positionRepository.count()).isPositive();
        assertThat(conditionRepository.count()).isPositive();

        HrExcelImportBatchRepository batches = batchRepository;
        var batch = batches.findById(parsed.batchId()).orElseThrow();
        batch.setPayloadRetentionUntil(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        batch.setUpdatedByActor(MANAGER.subject());
        batches.save(batch);

        assertThat(importService.purgeExpiredPayloads(HrImportActor.systemRetentionActor())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hr_excel_import_rows WHERE raw_payload IS NOT NULL OR normalized_payload IS NOT NULL OR employee_code_hint IS NOT NULL",
                Integer.class
        )).isZero();
        assertThat(batchRepository.findById(parsed.batchId()).orElseThrow().getPayloadPurgedAt()).isNotNull();
        assertThat(auditRepository.count()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void conflictInsertedAfterValidationBlocksConfirmWithoutPartialData() {
        var parsed = importService.uploadAndParse("baseline-values-2026.xlsx", WORKBOOK, MANAGER);
        var validated = importService.validate(parsed.batchId(), MANAGER);
        assertThat(validated.invalidRows()).isZero();

        HrEmployee conflicting = new HrEmployee();
        conflicting.setEmployeeCode("T001");
        conflicting.setFullName("Fixture Conflict");
        conflicting.setGender(HrEmployeeGender.UNKNOWN);
        conflicting.setEmploymentStatus(HrEmploymentStatus.ACTIVE);
        conflicting.setStatusEffectiveDate(LocalDate.of(2026, 6, 1));
        conflicting.setCreatedByActor(MANAGER.subject());
        conflicting.setUpdatedByActor(MANAGER.subject());
        employeeRepository.save(conflicting);

        assertThatThrownBy(() -> importService.confirm(
                parsed.batchId(), "fixture-confirmation-conflict", true, MANAGER
        ))
                .isInstanceOf(HrBaselineImportException.class)
                .extracting(error -> ((HrBaselineImportException) error).getCode())
                .isEqualTo("EMPLOYEE_CODE_ALREADY_EXISTS");

        assertThat(employeeRepository.count()).isOne();
        assertThat(movementRepository.count()).isZero();
        assertThat(rosterRepository.count()).isZero();
        assertThat(batchRepository.findById(parsed.batchId()).orElseThrow().getStatus())
                .isEqualTo(HrImportBatchStatus.VALIDATED);
    }

    @Test
    void abandonedValidatedBatchExpiresWithoutKeepingPersonnelPayload() {
        var parsed = importService.uploadAndParse("baseline-values-2026.xlsx", WORKBOOK, MANAGER);
        importService.validate(parsed.batchId(), MANAGER);

        var batch = batchRepository.findById(parsed.batchId()).orElseThrow();
        batch.setPayloadRetentionUntil(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        batch.setUpdatedByActor(MANAGER.subject());
        batchRepository.save(batch);

        assertThat(importService.purgeExpiredPayloads(HrImportActor.systemRetentionActor())).isEqualTo(1);
        var expired = batchRepository.findById(parsed.batchId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(HrImportBatchStatus.FAILED);
        assertThat(expired.getPayloadPurgedAt()).isNotNull();
        assertThat(rowRepository.findAllByBatch_IdOrderByRowNumber(parsed.batchId()))
                .allMatch(row -> row.getRowStatus() == com.booking.system.hr.enums.HrImportRowStatus.SKIPPED)
                .allMatch(row -> row.getRawPayload() == null && row.getNormalizedPayload() == null)
                .allMatch(row -> row.getEmployeeCodeHint() == null);
        assertThat(employeeRepository.count()).isZero();
    }

    @Test
    void lockedWorkforceSnapshotCreatesTheCorrectedJuneBaselineAtomically() {
        var preview = snapshotImportService.preview(
                "workforce-baseline-339-2026.xlsx",
                WORKFORCE_SNAPSHOT
        );
        assertThat(preview.applicable()).isTrue();
        assertThat(preview.bootstrap()).isTrue();
        assertThat(preview.currentTotalEmployees()).isZero();
        assertThat(preview.currentActiveEmployees()).isZero();
        assertThat(preview.targetActiveEmployees()).isEqualTo(339);
        assertThat(preview.increaseCount()).isZero();
        assertThat(preview.decreaseCount()).isZero();

        var result = snapshotImportService.confirm(
                "workforce-baseline-339-2026.xlsx",
                WORKFORCE_SNAPSHOT,
                "corrected-june-baseline",
                339,
                MANAGER
        );
        assertThat(result.bootstrapped()).isTrue();
        assertThat(result.replayed()).isFalse();
        assertThat(result.increasedEmployees()).isZero();
        assertThat(result.decreasedEmployees()).isZero();
        assertThat(result.activeEmployees()).isEqualTo(339);
        assertThat(result.totalEmployees()).isEqualTo(339);
        assertThat(employeeRepository.countByEmploymentStatus(HrEmploymentStatus.INACTIVE)).isZero();
        assertThat(movementRepository.count()).isEqualTo(339);
        assertThat(rosterRepository.count()).isEqualTo(1);

        var june = rosterRepository.findByPeriodStart(LocalDate.of(2026, 6, 1)).orElseThrow();
        var juneItems = rosterItemRepository.findAllByRoster_IdOrderByDisplayOrder(june.getId());
        assertThat(juneItems).hasSize(339);
        assertThat(juneItems.getFirst().getEmployeeCode()).isEqualTo("T003");
        assertThat(juneItems.getLast().getEmployeeCode()).isEqualTo("U012");
        assertThat(juneItems)
                .extracting(item -> item.getDisplayOrder())
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 339).boxed().toList()
                );

        var snapshotBatch = batchRepository.findById(result.batchId()).orElseThrow();
        assertThat(snapshotBatch.getImportType()).isEqualTo(HrImportType.BASELINE);
        assertThat(snapshotBatch.getImportedRows()).isEqualTo(339);
        assertThat(rowRepository.findAllByBatch_IdOrderByRowNumber(snapshotBatch.getId())).hasSize(339);
        assertThat(movementRepository.findAllByImportBatch_Id(snapshotBatch.getId()))
                .allMatch(movement -> movement.getMovementType() == HrMovementType.INITIAL_LOAD);

        var replay = snapshotImportService.confirm(
                "renamed-copy.xlsx",
                WORKFORCE_SNAPSHOT,
                "corrected-june-baseline",
                339,
                MANAGER
        );
        assertThat(replay.replayed()).isTrue();
        assertThat(employeeRepository.count()).isEqualTo(339);
        assertThat(movementRepository.count()).isEqualTo(339);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "HR_BASELINE_XLSX", matches = ".+")
    void lockedRealBaselineCompletesTransactionalImportAndRollback() throws IOException {
        byte[] realWorkbook = Files.readAllBytes(Path.of(System.getenv("HR_BASELINE_XLSX")));
        var parsedWorkbook = workbookParser.parse(realWorkbook);
        new HrBaselineImportContract(HrBaselineImportContract.LOCKED_SHA256).verify(parsedWorkbook);

        var staged = importPersistence.stage("baseline-values-2026.xlsx", parsedWorkbook, MANAGER);
        assertThat(staged.totalRows()).isEqualTo(329);
        assertThat(employeeRepository.count()).isZero();

        var validated = importService.validate(staged.batchId(), MANAGER);
        assertThat(validated.invalidRows()).isZero();
        assertThat(validated.validRows() + validated.warningRows()).isEqualTo(329);
        assertThat(validated.warningRows()).isPositive();

        var confirmed = importService.confirm(
                staged.batchId(), "real-baseline-confirmation", true, MANAGER
        );
        assertThat(confirmed.importedRows()).isEqualTo(329);
        assertThat(employeeRepository.count()).isEqualTo(329);
        assertThat(movementRepository.count()).isEqualTo(329);
        assertThat(rosterItemRepository.count()).isEqualTo(329);

        var rolledBack = importService.rollback(staged.batchId(), MANAGER);
        assertThat(rolledBack.status()).isEqualTo(HrImportBatchStatus.ROLLED_BACK);
        assertThat(employeeRepository.count()).isZero();
        assertThat(movementRepository.count()).isZero();
        assertThat(rosterRepository.count()).isZero();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean usingMySql() {
        String value = System.getenv("HR_MYSQL_JDBC_URL");
        return value != null && !value.isBlank();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.booking.system.hr.entity")
    @EnableJpaRepositories(basePackages = "com.booking.system.hr.repository")
    static class TestApplication {
    }
}
