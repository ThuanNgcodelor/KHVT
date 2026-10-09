package com.booking.system.hr;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrPhase1MigrationTest {

    private static final String MIGRATION = "db/migration/V1__create_hr_phase_1_schema.sql";
    private static final String PAYROLL_ENHANCEMENT_MIGRATION = "db/migration/V24__enhance_hr_payroll_delivery.sql";
    private static final Pattern HR_TABLE_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(hr_[a-z0-9_]+)"
    );

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "hr_departments",
            "hr_positions",
            "hr_working_conditions",
            "hr_employees",
            "hr_employee_employment",
            "hr_employee_identity",
            "hr_employee_insurance",
            "hr_employee_contacts",
            "hr_employee_movements",
            "hr_monthly_rosters",
            "hr_monthly_roster_items",
            "hr_excel_template_versions",
            "hr_excel_import_batches",
            "hr_excel_import_rows",
            "hr_audit_events"
    );

    @Test
    void migrationCreatesOnlyTheLockedHrTableContract() throws IOException {
        String sql = migrationSql();
        Matcher matcher = HR_TABLE_PATTERN.matcher(sql);
        Set<String> actualTables = new java.util.HashSet<>();
        while (matcher.find()) {
            actualTables.add(matcher.group(1).toLowerCase());
        }

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
        assertThat(sql).doesNotContain("REFERENCES users", "user_id", "ENUM(");
        assertThat(sql).contains(
                "raw_payload JSON NOT NULL",
                "leave_accrual_start_date DATE NULL",
                "attempt_number INT NOT NULL DEFAULT 1",
                "uk_hr_employee_code",
                "idx_hr_movement_status_date_type",
                "idx_hr_audit_entity"
        );
        assertThat(sql).contains(
                "fk_hr_roster_item_roster FOREIGN KEY (roster_id) REFERENCES hr_monthly_rosters (id) ON DELETE RESTRICT",
                "fk_hr_import_row_batch FOREIGN KEY (batch_id) REFERENCES hr_excel_import_batches (id) ON DELETE RESTRICT"
        );

        String executableSql = sql.replaceAll("(?m)^--.*$", "");
        assertThat(executableSql).doesNotContainPattern("(?i)\\b(ALTER|DROP|DELETE|TRUNCATE)\\s+(TABLE|FROM)?\\s*(users|bookings|rooms|vehicles)");
    }

    @Test
    void trackedFlywayDefaultsRequireExplicitLegacyBaseline() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = HrPhase1MigrationTest.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new IOException("Missing application.properties");
            }
            properties.load(input);
        }

        assertThat(properties.getProperty("spring.flyway.baseline-on-migrate"))
                .isEqualTo("${FLYWAY_BASELINE_ON_MIGRATE:false}");
        assertThat(properties.getProperty("spring.flyway.baseline-version")).isEqualTo("0");
        assertThat(properties.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider"))
                .isEqualTo("com.booking.system.config.LegacySchemaFilterProvider");
    }

    @Test
    void payrollEnhancementCreatesReplacementForeignKeyIndexBeforeDroppingUniqueIndex() throws IOException {
        String sql = resourceSql(PAYROLL_ENHANCEMENT_MIGRATION);
        int replacementIndex = sql.indexOf("CREATE INDEX idx_hr_payroll_campaign_import_created");
        int uniqueIndexDrop = sql.indexOf("DROP INDEX uk_hr_payroll_campaign_import");

        assertThat(replacementIndex).isGreaterThanOrEqualTo(0);
        assertThat(uniqueIndexDrop).isGreaterThan(replacementIndex);
    }

    @Test
    void migrationRunsOnceAndEnforcesCoreConstraints() throws SQLException {
        String jdbcUrl = "jdbc:h2:mem:hr_phase_1_clean;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection anchorConnection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = anchorConnection.createStatement()) {
            Flyway flyway = flyway(anchorConnection, false);
            MigrateResult firstRun = flyway.migrate();
            MigrateResult secondRun = flyway.migrate();

            assertThat(firstRun.migrationsExecuted).isEqualTo(28);
            assertThat(secondRun.migrationsExecuted).isZero();

            for (String table : EXPECTED_TABLES) {
                assertThat(tableExists(statement, table)).as(table).isTrue();
            }
            assertThat(tableExists(statement, "hr_employment_contracts")).isTrue();
            assertThat(tableExists(statement, "hr_employee_documents")).isTrue();
            assertThat(tableExists(statement, "hr_system_settings")).isTrue();
            assertThat(tableExists(statement, "hr_telegram_registrations")).isTrue();
            assertThat(tableExists(statement, "hr_employee_telegram_bindings")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_imports")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_records")).isTrue();
            assertThat(tableExists(statement, "hr_ocr_capture_sessions")).isTrue();
            assertThat(tableExists(statement, "hr_ocr_capture_images")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_shift_policies")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_source_days")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_punches")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_shifts")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_incidents")).isTrue();
            assertThat(tableExists(statement, "hr_attendance_exemptions")).isTrue();
            assertThat(tableExists(statement, "hr_employee_salary_changes")).isTrue();

            statement.executeUpdate("""
                    INSERT INTO hr_employees (
                        id, employee_code, full_name, created_by_actor, updated_by_actor
                    ) VALUES (
                        'employee-1', 'NV0001', 'Fixture Employee', 'external-actor@example.test', 'external-actor@example.test'
                    )
                    """);

            try (var rows = statement.executeQuery("""
                    SELECT workforce_group, onboarding_source, onboarding_policy_version
                    FROM hr_employees
                    WHERE id = 'employee-1'
                    """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("workforce_group")).isEqualTo("LEGACY_UNKNOWN");
                assertThat(rows.getString("onboarding_source")).isEqualTo("LEGACY");
                assertThat(rows.getInt("onboarding_policy_version")).isEqualTo(1);
            }

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employees (
                        id, employee_code, full_name, created_by_actor, updated_by_actor
                    ) VALUES (
                        'employee-2', 'NV0001', 'Duplicate Employee', 'SYSTEM', 'SYSTEM'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employees (
                        id, employee_code, full_name, employment_status, created_by_actor, updated_by_actor
                    ) VALUES (
                        'employee-3', 'NV0003', 'Invalid Status', 'UNKNOWN_STATUS', 'SYSTEM', 'SYSTEM'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            statement.executeUpdate("""
                    INSERT INTO hr_employment_contracts (
                        id, employee_id, contract_type, contract_number, sign_date,
                        effective_from, effective_until, status, idempotency_key,
                        created_by_actor, updated_by_actor
                    ) VALUES (
                        'contract-1', 'employee-1', 'FIXED_TERM_12_MONTHS', '001/HDLD/2026',
                        DATE '2026-08-10', DATE '2026-08-15', DATE '2027-08-15', 'READY',
                        'onboarding-1', 'SYSTEM', 'SYSTEM'
                    )
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employment_contracts (
                        id, employee_id, contract_type, contract_number, sign_date,
                        effective_from, effective_until, status, idempotency_key,
                        created_by_actor, updated_by_actor
                    ) VALUES (
                        'contract-2', 'employee-1', 'INDEFINITE', '002/HDLD/2026',
                        DATE '2026-08-10', DATE '2026-08-15', DATE '2027-08-15', 'READY',
                        'onboarding-2', 'SYSTEM', 'SYSTEM'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            statement.executeUpdate("DELETE FROM hr_employees WHERE id = 'employee-1'");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM hr_employment_contracts")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_departments (
                        id, code, name, parent_id, created_by_actor, updated_by_actor
                    ) VALUES (
                        'dept-1', 'D1', 'Dept 1', 'dept-1', 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employee_movements (
                        id, employee_id, movement_type, status, effective_date, source_kind, created_by_actor, updated_by_actor
                    ) VALUES (
                        'mov-1', 'phase-1-emp-1', 'INVALID', 'DRAFT', '2026-06-01', 'MANUAL', 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employee_employment (
                        employee_id, base_salary, created_by_actor, updated_by_actor
                    ) VALUES (
                        'phase-1-emp-1', -1, 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employee_insurance (
                        employee_id, valid_from, valid_until, created_by_actor, updated_by_actor
                    ) VALUES (
                        'phase-1-emp-1', '2026-06-30', '2026-06-01', 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_monthly_rosters (
                        id, period_start, total_active_employees, created_by_actor, updated_by_actor
                    ) VALUES (
                        'roster-1', '2026-06-15', 10, 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employee_movements (
                        id, employee_id, movement_type, status, effective_date, source_kind,
                        confirmed_at, confirmed_by_actor, created_by_actor, updated_by_actor
                    ) VALUES (
                        'mov-lifecycle-1', 'phase-1-emp-1', 'TRANSFER', 'DRAFT', '2026-06-01', 'MANUAL',
                        NOW(6), 'actor', 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employee_movements (
                        id, employee_id, movement_type, status, effective_date, source_kind,
                        to_employee_status, created_by_actor, updated_by_actor
                    ) VALUES (
                        'mov-activation-1', 'phase-1-emp-1', 'INCREASE', 'DRAFT', '2026-06-01', 'MANUAL',
                        'INACTIVE', 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employee_movements (
                        id, employee_id, movement_type, status, effective_date, source_kind,
                        from_employee_status, to_employee_status, created_by_actor, updated_by_actor
                    ) VALUES (
                        'mov-decrease-1', 'phase-1-emp-1', 'DECREASE', 'DRAFT', '2026-06-01', 'MANUAL',
                        'INACTIVE', 'ACTIVE', 'test', 'test'
                    )
                    """))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void baselineOnMigratePreservesExistingBookingBaseData() throws SQLException {
        String jdbcUrl = "jdbc:h2:mem:hr_phase_1_existing;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id VARCHAR(36) PRIMARY KEY, email VARCHAR(320) NOT NULL)");
            statement.executeUpdate("INSERT INTO users (id, email) VALUES ('legacy-user', 'legacy@example.test')");

            MigrateResult result = flyway(connection, true).migrate();
            assertThat(result.migrationsExecuted).isEqualTo(28);

            try (var rows = statement.executeQuery("SELECT id, email FROM users")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("id")).isEqualTo("legacy-user");
                assertThat(rows.getString("email")).isEqualTo("legacy@example.test");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static Flyway flyway(Connection connection, boolean baselineOnMigrate) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
    }

    private static boolean tableExists(Statement statement, String tableName) throws SQLException {
        try (var rows = statement.executeQuery("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = '%s'
                """.formatted(tableName))) {
            rows.next();
            return rows.getInt(1) == 1;
        }
    }

    private static String migrationSql() throws IOException {
        return resourceSql(MIGRATION);
    }

    private static String resourceSql(String resource) throws IOException {
        ClassLoader classLoader = HrPhase1MigrationTest.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing migration resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
