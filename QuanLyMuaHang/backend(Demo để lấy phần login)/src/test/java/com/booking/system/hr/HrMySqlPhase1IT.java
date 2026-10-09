package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.entity.HrAuditEvent;
import com.booking.system.hr.entity.HrDepartment;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeContact;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.entity.HrEmployeeIdentity;
import com.booking.system.hr.entity.HrEmployeeInsurance;
import com.booking.system.hr.entity.HrEmployeeMovement;
import com.booking.system.hr.entity.HrEmployeeSalaryChange;
import com.booking.system.hr.entity.HrExcelImportBatch;
import com.booking.system.hr.entity.HrExcelImportRow;
import com.booking.system.hr.entity.HrExcelTemplateVersion;
import com.booking.system.hr.entity.HrMonthlyRoster;
import com.booking.system.hr.entity.HrMonthlyRosterItem;
import com.booking.system.hr.entity.HrPosition;
import com.booking.system.hr.entity.HrWorkingCondition;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.SchemaToolingSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "HR_MYSQL_JDBC_URL", matches = ".+")
class HrMySqlPhase1IT {

    private static final String JDBC_URL = System.getenv("HR_MYSQL_JDBC_URL");
    private static final String JDBC_USER = System.getenv().getOrDefault("HR_MYSQL_JDBC_USER", "root");
    private static final String JDBC_PASSWORD = System.getenv().getOrDefault("HR_MYSQL_JDBC_PASSWORD", "");

    @Test
    void migratesAndValidatesAgainstIsolatedMySql8() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_probe (id INT PRIMARY KEY, marker VARCHAR(32) NOT NULL)");
            statement.executeUpdate("INSERT INTO legacy_probe (id, marker) VALUES (1, 'preserve-me')");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, JDBC_USER, JDBC_PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();

        MigrateResult firstRun = flyway.migrate();
        MigrateResult secondRun = flyway.migrate();

        assertThat(firstRun.migrationsExecuted).isEqualTo(28);
        assertThat(secondRun.migrationsExecuted).isZero();

        verifyMySqlConstraintsAndLegacyData();
        verifyHibernateUpdateCannotCreateHrObjects();
        validateHibernateMappings();
    }

    private void verifyMySqlConstraintsAndLegacyData() throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "SELECT COUNT(*) FROM legacy_probe WHERE id = 1 AND marker = 'preserve-me'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'hr\\_%'"))
                    .isEqualTo(55);
            assertThat(singleInt(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'hr_employee_salary_changes'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND table_name LIKE 'hr\\_%' AND referenced_table_name = 'users'"))
                    .isZero();

            statement.executeUpdate("""
                    INSERT INTO hr_employees (
                        id, employee_code, full_name, employment_status, created_by_actor, updated_by_actor
                    ) VALUES (
                        'mysql-employee-1', 'MYSQL0001', 'MySQL Fixture', 'ACTIVE', 'external@example.test', 'external@example.test'
                    )
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employees (
                        id, employee_code, full_name, employment_status, created_by_actor, updated_by_actor
                    ) VALUES (
                        'mysql-employee-2', 'MYSQL0001', 'Duplicate Fixture', 'ACTIVE', 'SYSTEM', 'SYSTEM'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employees (
                        id, employee_code, full_name, employment_status, created_by_actor, updated_by_actor
                    ) VALUES (
                        'mysql-employee-3', 'MYSQL0003', 'Invalid Status', 'INVALID', 'SYSTEM', 'SYSTEM'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO hr_employee_movements (
                        id, employee_id, movement_type, status, effective_date,
                        from_employee_status, to_employee_status, source_kind,
                        created_by_actor, updated_by_actor
                    ) VALUES (
                        'mysql-movement-1', 'mysql-employee-1', 'DECREASE', 'DRAFT', CURRENT_DATE,
                        'ACTIVE', 'ACTIVE', 'MANUAL', 'SYSTEM', 'SYSTEM'
                    )
                    """))
                    .isInstanceOf(SQLException.class);

            statement.executeUpdate(importBatchSql("mysql-batch-1", 1));
            statement.executeUpdate(importBatchSql("mysql-batch-2", 2));
            statement.executeUpdate("""
                    INSERT INTO hr_excel_import_rows (
                        id, batch_id, sheet_name, source_row_number, raw_payload, payload_sha256
                    ) VALUES (
                        'mysql-row-1', 'mysql-batch-1', 'T6-26', 5, JSON_OBJECT('employeeCode', 'MYSQL0001'),
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                    )
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM hr_excel_import_batches WHERE id = 'mysql-batch-1'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void validateHibernateMappings() {
        StandardServiceRegistry registry = registry("validate");
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> entity : hrEntities()) {
                sources.addAnnotatedClass(entity);
            }
            try (SessionFactory ignored = sources.buildMetadata().buildSessionFactory()) {
                // Building the SessionFactory executes Hibernate schema validation.
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void verifyHibernateUpdateCannotCreateHrObjects() throws SQLException {
        StandardServiceRegistry registry = registry("update");
        try {
            MetadataSources sources = new MetadataSources(registry);
            sources.addAnnotatedClass(HrFilterProbe.class);
            try (SessionFactory ignored = sources.buildMetadata().buildSessionFactory()) {
                // The configured migrate filter must exclude this hr_* test table.
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }

        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'hr_filter_probe'"))
                    .isZero();
        }
    }

    private static StandardServiceRegistry registry(String schemaAction) {
        Map<String, Object> settings = Map.of(
                AvailableSettings.JAKARTA_JDBC_URL, JDBC_URL,
                AvailableSettings.JAKARTA_JDBC_USER, JDBC_USER,
                AvailableSettings.JAKARTA_JDBC_PASSWORD, JDBC_PASSWORD,
                AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect",
                SchemaToolingSettings.HBM2DDL_AUTO, schemaAction,
                SchemaToolingSettings.HBM2DDL_FILTER_PROVIDER, LegacySchemaFilterProvider.class.getName(),
                AvailableSettings.SHOW_SQL, false
        );
        return new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
    }

    private static Class<?>[] hrEntities() {
        return new Class<?>[]{
                HrAuditEvent.class,
                HrDepartment.class,
                HrEmployee.class,
                HrEmployeeContact.class,
                HrEmployeeEmployment.class,
                HrEmployeeIdentity.class,
                HrEmployeeInsurance.class,
                HrEmployeeMovement.class,
                HrEmployeeSalaryChange.class,
                HrExcelImportBatch.class,
                HrExcelImportRow.class,
                HrExcelTemplateVersion.class,
                HrMonthlyRoster.class,
                HrMonthlyRosterItem.class,
                HrPosition.class,
                HrWorkingCondition.class
        };
    }

    private static String importBatchSql(String id, int attemptNumber) {
        return """
                INSERT INTO hr_excel_import_batches (
                    id, import_type, source_file_name, file_sha256, file_size,
                    source_sheet_name, attempt_number, created_by_actor, updated_by_actor
                ) VALUES (
                    '%s', 'BASELINE', 'fixture.xlsx',
                    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                    1, 'T6-26', %d, 'SYSTEM', 'SYSTEM'
                )
                """.formatted(id, attemptNumber);
    }

    private static int singleInt(Statement statement, String sql) throws SQLException {
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }

    @Entity
    @Table(name = "hr_filter_probe")
    public static class HrFilterProbe {
        @Id
        @Column(length = 36)
        private String id;
    }
}
