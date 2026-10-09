package com.booking.system.hr;

import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class HrDomainIsolationTest {

    @Test
    void employeeDomainHasNoLegacyBookingEntityReferenceOrUserJoinColumn() {
        assertThat(Arrays.stream(HrEmployee.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getPackageName))
                .doesNotContain("com.booking.system.entity");

        assertThat(Arrays.stream(HrEmployee.class.getDeclaredFields())
                .map(field -> field.getAnnotation(JoinColumn.class))
                .filter(annotation -> annotation != null)
                .map(JoinColumn::name))
                .doesNotContain("user_id");
    }

    @Test
    void derivedExcelValuesAreNotPersistedAsEmployeeFields() {
        assertThat(Arrays.stream(HrEmployee.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("age", "yearsOfService", "totalIncome");
        assertThat(Arrays.stream(HrEmployeeEmployment.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("age", "yearsOfService", "totalIncome");
    }
}
