package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrExcelImportBatchRepository;
import com.booking.system.hr.repository.HrExcelImportRowRepository;
import com.booking.system.hr.repository.HrMonthlyRosterItemRepository;
import com.booking.system.hr.repository.HrMonthlyRosterRepository;
import com.booking.system.hr.repository.HrEmployeeSalaryChangeRepository;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HrSchemaOwnershipTest {

    private final LegacySchemaFilterProvider provider = new LegacySchemaFilterProvider();

    @Test
    void hibernateMigrationCannotCreateUpdateOrDropFlywayOwnedHrTables() {
        Table hrTable = new Table("orm", "hr_employees");
        Table legacyTable = new Table("orm", "users");

        assertThat(provider.getCreateFilter().includeTable(hrTable)).isFalse();
        assertThat(provider.getMigrateFilter().includeTable(hrTable)).isFalse();
        assertThat(provider.getDropFilter().includeTable(hrTable)).isFalse();
        assertThat(provider.getTruncatorFilter().includeTable(hrTable)).isFalse();

        assertThat(provider.getCreateFilter().includeTable(legacyTable)).isTrue();
        assertThat(provider.getMigrateFilter().includeTable(legacyTable)).isTrue();
    }

    @Test
    void explicitSchemaValidationStillIncludesHrTables() {
        assertThat(provider.getValidateFilter().includeTable(new Table("orm", "hr_employees"))).isTrue();
    }

    @Test
    void historyBearingRepositoriesDoNotExposeGenericDeleteOperations() {
        List<Class<?>> repositories = List.of(
                HrAuditEventRepository.class,
                HrEmployeeMovementRepository.class,
                HrExcelImportBatchRepository.class,
                HrExcelImportRowRepository.class,
                HrEmployeeSalaryChangeRepository.class,
                HrMonthlyRosterRepository.class,
                HrMonthlyRosterItemRepository.class
        );

        assertThat(repositories.stream()
                .flatMap(repository -> Arrays.stream(repository.getMethods()))
                .map(Method::getName)
                .filter(name -> name.startsWith("delete") || name.startsWith("remove")))
                .isEmpty();
    }
}
