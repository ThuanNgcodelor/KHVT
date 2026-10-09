package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrSalaryRaiseDtos;
import com.booking.system.hr.entity.HrAuditable;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrSalaryReviewBucket;
import com.booking.system.hr.enums.HrSalaryReviewStatus;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeEmploymentRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.service.HrSalaryReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = HrSalaryReviewServiceTest.Config.class)
class HrSalaryReviewServiceTest {
    private static final HrImportActor ADMIN = new HrImportActor("TEST:ADMIN", "Test admin", "ADMIN");
    private static final HrImportActor USER = new HrImportActor("TEST:USER", "Test user", "USER");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:hr_salary_review;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider",
                () -> LegacySchemaFilterProvider.class.getName());
    }

    @jakarta.annotation.Resource HrEmployeeRepository employees;
    @jakarta.annotation.Resource HrEmployeeEmploymentRepository employments;
    @jakarta.annotation.Resource HrAuditEventRepository auditEvents;

    @Test
    void dashboardTracksOverdueAndDeferredReviewWithoutChangingSalary() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        HrEmployeeEmployment employment = employee("A350", today.minusDays(5));
        HrSalaryReviewService service = service();

        var overdue = service.dashboard(HrSalaryReviewBucket.OVERDUE, null, null, 0, 20);
        assertThat(overdue.stats().overdue()).isEqualTo(1);
        assertThat(overdue.employees().content()).singleElement().satisfies(item -> {
            assertThat(item.employeeCode()).isEqualTo("A350");
            assertThat(item.reviewStatus()).isEqualTo(HrSalaryReviewStatus.PENDING);
            assertThat(item.daysUntilDue()).isNegative();
        });

        var deferred = service.update(employment.getEmployeeId(), new HrSalaryRaiseDtos.ReviewUpdateRequest(
                HrSalaryReviewStatus.DEFERRED, today.plusDays(20), "Chờ kết quả đánh giá", employment.getRowVersion()
        ), ADMIN);
        assertThat(deferred.reviewStatus()).isEqualTo(HrSalaryReviewStatus.DEFERRED);
        assertThat(deferred.effectiveReviewDate()).isEqualTo(today.plusDays(20));
        assertThat(deferred.baseSalary()).isEqualByComparingTo("6000000");

        var refreshed = service.dashboard(HrSalaryReviewBucket.DUE_30, null, null, 0, 20);
        assertThat(refreshed.stats().overdue()).isZero();
        assertThat(refreshed.stats().due30()).isEqualTo(1);
        assertThat(refreshed.employees().content()).hasSize(1);
        assertThat(auditEvents.count()).isEqualTo(1);
    }

    @Test
    void reviewUpdateRequiresApproverAndReasonForDeferral() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        HrEmployeeEmployment employment = employee("A351", today.plusDays(10));
        HrSalaryReviewService service = service();

        assertThatThrownBy(() -> service.update(employment.getEmployeeId(),
                new HrSalaryRaiseDtos.ReviewUpdateRequest(
                        HrSalaryReviewStatus.IN_REVIEW, null, null, employment.getRowVersion()), USER))
                .isInstanceOf(HrApiException.class)
                .extracting(exception -> ((HrApiException) exception).code())
                .isEqualTo("SALARY_REVIEW_APPROVER_REQUIRED");

        assertThatThrownBy(() -> service.update(employment.getEmployeeId(),
                new HrSalaryRaiseDtos.ReviewUpdateRequest(
                        HrSalaryReviewStatus.DEFERRED, today.plusDays(15), null, employment.getRowVersion()), ADMIN))
                .isInstanceOf(HrApiException.class)
                .extracting(exception -> ((HrApiException) exception).code())
                .isEqualTo("SALARY_REVIEW_NOTE_REQUIRED");
    }

    private HrSalaryReviewService service() {
        return new HrSalaryReviewService(employments, auditEvents, new HrImportJsonCodec());
    }

    private HrEmployeeEmployment employee(String code, LocalDate dueDate) {
        HrEmployee employee = new HrEmployee();
        employee.setEmployeeCode(code);
        employee.setFullName("Nhân viên " + code);
        employee.setEmploymentStatus(HrEmploymentStatus.ACTIVE);
        audit(employee);
        employee = employees.save(employee);

        HrEmployeeEmployment employment = new HrEmployeeEmployment();
        employment.setEmployee(employee);
        employment.setBaseSalary(new BigDecimal("6000000"));
        employment.setAllowance(new BigDecimal("1000000"));
        employment.setLastSalaryRaiseDate(dueDate.minusMonths(48));
        employment.setNextSalaryReviewDate(dueDate);
        employment.setSalaryReviewCycleMonths(48);
        audit(employment);
        employment = employments.save(employment);
        employee.setEmployment(employment);
        return employment;
    }

    private static void audit(HrAuditable value) {
        value.setCreatedByActor(ADMIN.subject());
        value.setUpdatedByActor(ADMIN.subject());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "com.booking.system.hr.entity")
    @EnableJpaRepositories(basePackages = "com.booking.system.hr.repository")
    static class Config { }
}
