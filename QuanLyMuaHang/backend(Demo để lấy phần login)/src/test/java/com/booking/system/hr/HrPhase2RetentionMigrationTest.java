package com.booking.system.hr;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrPhase2RetentionMigrationTest {

    private static final String MIGRATION = "db/migration/V2__add_hr_import_payload_retention.sql";

    @Test
    void retentionMigrationChangesOnlyHrImportStagingTables() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains(
                "ALTER TABLE hr_excel_import_batches",
                "payload_retention_until DATETIME(6) NULL",
                "payload_purged_at DATETIME(6) NULL",
                "payload_purged_by_actor VARCHAR(320) NULL",
                "ck_hr_import_payload_purge_audit",
                "idx_hr_import_payload_retention",
                "ALTER TABLE hr_excel_import_rows",
                "MODIFY COLUMN raw_payload JSON NULL"
        );
        assertThat(sql).doesNotContain(" users", " bookings", " rooms", " vehicles");
    }

    @Test
    void migrationPreservesExistingPayloadAndSupportsAuditedPurge() throws SQLException {
        String jdbcUrl = "jdbc:h2:mem:hr_phase_2_retention;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            MigrateResult phaseOne = flyway(connection, MigrationVersion.fromVersion("1")).migrate();
            assertThat(phaseOne.migrationsExecuted).isEqualTo(1);

            statement.executeUpdate(importBatchSql());
            statement.executeUpdate("""
                    INSERT INTO hr_excel_import_rows (
                        id, batch_id, sheet_name, source_row_number, raw_payload, payload_sha256
                    ) VALUES (
                        'retention-row-1', 'retention-batch-1', 'T6-26', 5,
                        JSON_OBJECT('employeeCode', 'NV0001'),
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                    )
                    """);

            Flyway latestFlyway = flyway(connection, null);
            MigrateResult phaseTwo = latestFlyway.migrate();
            MigrateResult noOp = latestFlyway.migrate();

            assertThat(phaseTwo.migrationsExecuted).isEqualTo(27);
            assertThat(noOp.migrationsExecuted).isZero();
            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM hr_excel_import_rows
                    WHERE id = 'retention-row-1' AND raw_payload IS NOT NULL
                    """)).isEqualTo(1);

            assertThat(statement.executeUpdate("""
                    UPDATE hr_excel_import_rows
                    SET raw_payload = NULL
                    WHERE id = 'retention-row-1'
                    """)).isEqualTo(1);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    UPDATE hr_excel_import_batches
                    SET payload_purged_at = CURRENT_TIMESTAMP
                    WHERE id = 'retention-batch-1'
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    UPDATE hr_excel_import_batches
                    SET payload_purged_by_actor = 'manager@example.test'
                    WHERE id = 'retention-batch-1'
                    """))
                    .isInstanceOf(SQLException.class);

            assertThat(statement.executeUpdate("""
                    UPDATE hr_excel_import_batches
                    SET payload_retention_until = CURRENT_TIMESTAMP,
                        payload_purged_at = CURRENT_TIMESTAMP,
                        payload_purged_by_actor = 'manager@example.test'
                    WHERE id = 'retention-batch-1'
                    """)).isEqualTo(1);

            assertThat(singleInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.indexes
                    WHERE table_schema = 'public'
                      AND table_name = 'hr_excel_import_batches'
                      AND index_name = 'idx_hr_import_payload_retention'
                    """)).isEqualTo(1);
        }
    }

    private static Flyway flyway(Connection connection, MigrationVersion target) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static int singleInt(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String importBatchSql() {
        return """
                INSERT INTO hr_excel_import_batches (
                    id, import_type, source_file_name, file_sha256, file_size,
                    source_sheet_name, attempt_number, created_by_actor, updated_by_actor
                ) VALUES (
                    'retention-batch-1', 'BASELINE', 'fixture.xlsx',
                    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                    1, 'T6-26', 1, 'SYSTEM', 'SYSTEM'
                )
                """;
    }

    private static String migrationSql() throws IOException {
        ClassLoader classLoader = HrPhase2RetentionMigrationTest.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(MIGRATION)) {
            if (input == null) {
                throw new IOException("Missing migration resource: " + MIGRATION);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
