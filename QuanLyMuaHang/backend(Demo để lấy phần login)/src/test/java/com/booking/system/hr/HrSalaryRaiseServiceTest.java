package com.booking.system.hr;

import com.booking.system.config.LegacySchemaFilterProvider;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.entity.HrAuditable;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrImportBatchStatus;
import com.booking.system.hr.enums.HrImportRowStatus;
import com.booking.system.hr.enums.HrSalaryChangeStatus;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.importer.HrImportJsonCodec;
import com.booking.system.hr.importer.HrSalaryRaiseWorkbookParser;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeEmploymentRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrEmployeeSalaryChangeRepository;
import com.booking.system.hr.repository.HrExcelImportBatchRepository;
import com.booking.system.hr.repository.HrExcelImportRowRepository;
import com.booking.system.hr.service.HrSalaryRaiseService;
import jakarta.persistence.EntityManager;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = HrSalaryRaiseServiceTest.Config.class)
class HrSalaryRaiseServiceTest {
    private static final HrImportActor ADMIN = new HrImportActor("TEST:ADMIN", "Test admin", "ADMIN");
    private static final HrImportActor MANAGER = new HrImportActor("TEST:MANAGER", "Test manager", "MANAGER");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:hr_salary_raise;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.schema_filter_provider",
                () -> LegacySchemaFilterProvider.class.getName());
    }

    @jakarta.annotation.Resource HrExcelImportBatchRepository batches;
    @jakarta.annotation.Resource HrExcelImportRowRepository rows;
    @jakarta.annotation.Resource HrEmployeeRepository employees;
    @jakarta.annotation.Resource HrEmployeeEmploymentRepository employments;
    @jakarta.annotation.Resource HrEmployeeSalaryChangeRepository salaryChanges;
    @jakarta.annotation.Resource HrAuditEventRepository auditEvents;
    @jakarta.annotation.Resource EntityManager entityManager;

    @Test
    void importKeepsJuneSalaryAndUsesRaisedSalaryFromJulyThenCanRollback() throws Exception {
        HrEmployee employee = employee("A339", "Lê Minh Toàn", "6647000", "1892000");
        HrSalaryRaiseService service = service();

        var uploaded = service.upload("Tang luong thang 7-2026.xlsx",
                workbook("A339", "Lê Minh Toàn", "6647000"), ADMIN);
        var validated = service.validate(uploaded.id(), ADMIN);

        assertThat(validated.status()).isEqualTo(HrImportBatchStatus.VALIDATED);
        assertThat(validated.invalidRows()).isZero();
        assertThat(validated.warningRows()).isEqualTo(1);
        assertThat(service.preview(uploaded.id(), 0, 20).rows().getFirst().status())
                .isEqualTo(HrImportRowStatus.WARNING);

        var confirmed = service.confirm(uploaded.id(), "salary-july-2026", true, ADMIN);
        assertThat(confirmed.status()).isEqualTo(HrImportBatchStatus.CONFIRMED);
        assertThat(confirmed.importedRows()).isEqualTo(1);

        var june = service.compensationAt(List.of(employee), LocalDate.of(2026, 6, 30)).get(employee.getId());
        var july = service.compensationAt(List.of(employee), LocalDate.of(2026, 7, 1)).get(employee.getId());
        var september = service.compensationAt(List.of(employee), LocalDate.of(2026, 9, 30)).get(employee.getId());
        assertThat(june.total()).isEqualByComparingTo("8539000");
        assertThat(july.total()).isEqualByComparingTo("8938000");
        assertThat(september.total()).isEqualByComparingTo("8938000");
        assertThat(july.grade()).isEqualTo("3/6");
        assertThat(july.reviewCycleMonths()).isEqualTo(48);
        assertThat(july.nextReviewDate()).isEqualTo(LocalDate.of(2030, 6, 10));

        var history = service.history(employee.getId(), 0, 20).content();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().status()).isEqualTo(HrSalaryChangeStatus.APPLIED);

        service.rollback(uploaded.id(), "Kiểm thử hoàn tác batch", ADMIN);
        assertThat(service.history(employee.getId(), 0, 20).content().getFirst().status())
                .isEqualTo(HrSalaryChangeStatus.ROLLED_BACK);
        assertThat(service.compensationAt(List.of(employee), LocalDate.of(2026, 9, 30))
                .get(employee.getId()).total()).isEqualByComparingTo("8539000");
    }

    @Test
    void salaryMismatchMarksBatchInvalidAndBlocksTheWholeConfirmation() throws Exception {
        employee("A340", "Nhân viên lệch lương", "6000000", "1892000");
        HrSalaryRaiseService service = service();

        var uploaded = service.upload("salary-mismatch.xlsx",
                workbook("A340", "Nhân viên lệch lương", "6647000"), ADMIN);
        var validated = service.validate(uploaded.id(), ADMIN);

        assertThat(validated.invalidRows()).isEqualTo(1);
        assertThat(service.preview(uploaded.id(), 0, 20).rows().getFirst().status())
                .isEqualTo(HrImportRowStatus.INVALID);
        assertThatThrownBy(() -> service.confirm(uploaded.id(), "must-not-confirm", true, ADMIN))
                .isInstanceOf(HrApiException.class)
                .extracting(exception -> ((HrApiException) exception).code())
                .isEqualTo("SALARY_RAISE_INVALID_ROWS");
        assertThat(salaryChanges.count()).isZero();
    }

    @Test
    void managerCanConfirmRollbackAndDeleteWhileSalaryHistoryIsPreserved() throws Exception {
        HrEmployee employee = employee("A341", "Nhân viên tương lai", "6647000", "1892000");
        HrSalaryRaiseService service = service();
        LocalDate effectiveDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusYears(5).withMonth(7).withDayOfMonth(1);
        byte[] source = workbook("A341", "Nhân viên tương lai", "6647000",
                effectiveDate, effectiveDate.plusMonths(48));

        var firstUpload = service.upload("salary-future.xlsx", source, MANAGER);
        var repeatedUpload = service.upload("salary-future.xlsx", source, MANAGER);
        assertThat(repeatedUpload.id()).isEqualTo(firstUpload.id());
        var validated = service.validate(firstUpload.id(), MANAGER);
        assertThat(validated.validRows()).isEqualTo(1);

        service.confirm(firstUpload.id(), "future-manager", false, MANAGER);
        assertThat(service.history(employee.getId(), 0, 20).content().getFirst().status())
                .isEqualTo(HrSalaryChangeStatus.SCHEDULED);
        assertThat(employee.getEmployment().getBaseSalary()).isEqualByComparingTo("6647000");
        assertThat(service.compensationAt(List.of(employee), effectiveDate.minusDays(1))
                .get(employee.getId()).total()).isEqualByComparingTo("8539000");
        assertThat(service.compensationAt(List.of(employee), effectiveDate)
                .get(employee.getId()).total()).isEqualByComparingTo("8938000");

        assertThat(service.applyDueScheduled(effectiveDate.minusDays(1), HrImportActor.systemSalaryActor())).isZero();
        assertThat(service.applyDueScheduled(effectiveDate, HrImportActor.systemSalaryActor())).isEqualTo(1);
        assertThat(service.history(employee.getId(), 0, 20).content().getFirst().status())
                .isEqualTo(HrSalaryChangeStatus.APPLIED);
        assertThat(employee.getEmployment().getBaseSalary()).isEqualByComparingTo("7046000");

        assertThatThrownBy(() -> service.deleteImport(firstUpload.id(), MANAGER))
                .isInstanceOf(HrApiException.class)
                .extracting(exception -> ((HrApiException) exception).code())
                .isEqualTo("SALARY_RAISE_DELETE_REQUIRES_ROLLBACK");

        String historyId = service.history(employee.getId(), 0, 20).content().getFirst().id();
        service.rollback(firstUpload.id(), "Manager hoàn tác để kiểm thử", MANAGER);
        service.deleteImport(firstUpload.id(), MANAGER);
        entityManager.clear();

        assertThat(batches.findById(firstUpload.id())).isEmpty();
        assertThat(rows.findAllByBatch_IdOrderByRowNumber(firstUpload.id())).isEmpty();
        assertThat(salaryChanges.findById(historyId)).get().satisfies(change -> {
            assertThat(change.getStatus()).isEqualTo(HrSalaryChangeStatus.ROLLED_BACK);
            assertThat(change.getImportBatch()).isNull();
            assertThat(change.getRollbackReason()).isEqualTo("Manager hoàn tác để kiểm thử");
        });
        assertThat(service.compensationAt(List.of(employees.findById(employee.getId()).orElseThrow()),
                effectiveDate).get(employee.getId()).total()).isEqualByComparingTo("8539000");
    }

    @Test
    void managerCanDeleteValidatedFileBecauseItHasNotChangedSalary() throws Exception {
        employee("A342", "Nhân viên xóa file", "6647000", "1892000");
        HrSalaryRaiseService service = service();
        var uploaded = service.upload("salary-unused.xlsx",
                workbook("A342", "Nhân viên xóa file", "6647000"), MANAGER);
        service.validate(uploaded.id(), MANAGER);

        service.deleteImport(uploaded.id(), MANAGER);

        assertThat(batches.findById(uploaded.id())).isEmpty();
        assertThat(rows.findAllByBatch_IdOrderByRowNumber(uploaded.id())).isEmpty();
        assertThat(salaryChanges.count()).isZero();
    }

    private HrSalaryRaiseService service() {
        return new HrSalaryRaiseService(new HrSalaryRaiseWorkbookParser(), batches, rows, employees, employments,
                salaryChanges, auditEvents, new HrImportJsonCodec(), entityManager);
    }

    private HrEmployee employee(String code, String fullName, String salary, String allowance) {
        HrEmployee employee = new HrEmployee();
        employee.setEmployeeCode(code);
        employee.setFullName(fullName);
        employee.setEmploymentStatus(HrEmploymentStatus.ACTIVE);
        audit(employee);
        employee = employees.save(employee);

        HrEmployeeEmployment employment = new HrEmployeeEmployment();
        employment.setEmployee(employee);
        employment.setBaseSalary(new BigDecimal(salary));
        employment.setAllowance(new BigDecimal(allowance));
        employment.setSalaryGrade("2/6");
        employment.setSalaryScaleCode("B3.1");
        audit(employment);
        employments.save(employment);
        employee.setEmployment(employment);
        return employee;
    }

    private byte[] workbook(String employeeCode, String fullName, String currentSalary) throws Exception {
        return workbook(employeeCode, fullName, currentSalary,
                LocalDate.of(2026, 7, 1), LocalDate.of(2030, 6, 10));
    }

    private byte[] workbook(String employeeCode, String fullName, String currentSalary,
                            LocalDate effectiveDate, LocalDate nextReviewDate) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("1-7-2026");
            var header = sheet.createRow(1);
            String[] headers = {"MS", "HỌ VÀ TÊN", "Lương 2026", "PHỤ CẤP 2026", "TỔNG LƯƠNG 2026",
                    "Bậc 2026", "Mã số 2026", "Bậc sau khi nâng", "Lương sau nâng bậc",
                    "Phụ cấp Sau nâng", "TỔNG LƯƠNG SAU NÂNG", "HẠN NÂNG BẬC",
                    "NGÀY NÂNG BẬC GẦN NHẤT", "NGÀY TỚI HẠN"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue(employeeCode);
            row.createCell(1).setCellValue(fullName);
            row.createCell(2).setCellValue(new BigDecimal(currentSalary).doubleValue());
            row.createCell(3).setCellValue(1_892_000);
            row.createCell(4).setCellValue(new BigDecimal(currentSalary).add(new BigDecimal("1892000")).doubleValue());
            row.createCell(5).setCellValue("2/6");
            row.createCell(6).setCellValue("B3.1");
            row.createCell(7).setCellValue("3/6");
            row.createCell(8).setCellValue(7_046_000);
            row.createCell(9).setCellValue(1_892_000);
            row.createCell(10).setCellValue(8_938_000);
            row.createCell(11).setCellValue(48);
            row.createCell(12).setCellValue(date(effectiveDate));
            row.createCell(13).setCellValue(date(nextReviewDate));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static Date date(int year, int month, int day) {
        return date(LocalDate.of(year, month, day));
    }

    private static Date date(LocalDate value) {
        return Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
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
