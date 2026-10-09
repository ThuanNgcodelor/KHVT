package com.booking.system.hr;

import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrRosterInclusionReason;
import com.booking.system.hr.service.HrExcelExportService;
import com.booking.system.hr.service.HrRosterProjectionService;
import com.booking.system.hr.service.HrSalaryRaiseService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;

class HrExcelExportServiceTest {

    private final HrEmployeeRepository employeeRepository = mock(HrEmployeeRepository.class);
    private final HrEmployeeMovementRepository movementRepository = mock(HrEmployeeMovementRepository.class);
    private final HrRosterProjectionService rosterProjectionService = mock(HrRosterProjectionService.class);
    private final HrSalaryRaiseService salaryRaiseService = mock(HrSalaryRaiseService.class);
    private final HrExcelExportService service = new HrExcelExportService(
            employeeRepository,
            movementRepository,
            rosterProjectionService,
            salaryRaiseService
    );

    @Test
    void exportMonthCreatesThreeSheets() throws Exception {
        when(movementRepository.findConfirmedForExport(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 1)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 6, 30))
        )).thenReturn(List.of());
        when(rosterProjectionService.projectedItems(LocalDate.of(2026, 6, 1))).thenReturn(List.of());

        HrExcelExportService.ExportFile file = service.exportMonth(2026, 6);

        assertThat(file.fileName()).isEqualTo("hr-T6-26.xlsx");
        assertThat(sheetNames(file.content())).containsExactly("TĂNG", "GIẢM", "T6-26");
        assertThat(zipEntry(file.content(), "xl/styles.xml")).isNotBlank();
    }

    @Test
    void monthlyRosterUsesOldJuneSalaryAndRaisedJulySalary() throws Exception {
        HrEmployee employee = new HrEmployee();
        employee.setId("salary-export-employee");
        employee.setEmployeeCode("A339");
        employee.setFullName("Lê Minh Toàn");
        HrEmployeeEmployment employment = new HrEmployeeEmployment();
        employment.setEmployee(employee);
        employment.setBaseSalary(new BigDecimal("6647000"));
        employment.setAllowance(new BigDecimal("1892000"));
        employee.setEmployment(employment);
        var item = new HrRosterProjectionService.ProjectedRosterItem(
                "item-salary", employee, 1, 1, "A339", "Lê Minh Toàn",
                "HC", "Hành chính", "NV", "Nhân viên", "BT", "Bình thường",
                HrEmploymentStatus.ACTIVE, LocalDate.of(2013, 4, 15), null, BigDecimal.valueOf(12),
                HrRosterInclusionReason.BASELINE, null, null, null);
        when(movementRepository.findConfirmedForExport(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(rosterProjectionService.projectedItems(LocalDate.of(2026, 6, 1))).thenReturn(List.of(item));
        when(rosterProjectionService.projectedItems(LocalDate.of(2026, 7, 1))).thenReturn(List.of(item));
        when(salaryRaiseService.compensationAt(anyCollection(), eq(LocalDate.of(2026, 6, 30))))
                .thenReturn(Map.of(employee.getId(), new HrSalaryRaiseService.Compensation(
                        new BigDecimal("6647000"), new BigDecimal("1892000"), "2/6", "B3.1",
                        null, null, null)));
        when(salaryRaiseService.compensationAt(anyCollection(), eq(LocalDate.of(2026, 7, 31))))
                .thenReturn(Map.of(employee.getId(), new HrSalaryRaiseService.Compensation(
                        new BigDecimal("7046000"), new BigDecimal("1892000"), "3/6", "B3.1",
                        48, LocalDate.of(2026, 7, 1), LocalDate.of(2030, 6, 10))));

        try (XSSFWorkbook june = new XSSFWorkbook(new ByteArrayInputStream(service.exportMonth(2026, 6).content()));
             XSSFWorkbook july = new XSSFWorkbook(new ByteArrayInputStream(service.exportMonth(2026, 7).content()))) {
            assertThat(june.getSheet("T6-26").getRow(4).getCell(6).getNumericCellValue()).isEqualTo(6_647_000);
            assertThat(june.getSheet("T6-26").getRow(4).getCell(8).getNumericCellValue()).isEqualTo(8_539_000);
            assertThat(july.getSheet("T7-26").getRow(4).getCell(6).getNumericCellValue()).isEqualTo(7_046_000);
            assertThat(july.getSheet("T7-26").getRow(4).getCell(8).getNumericCellValue()).isEqualTo(8_938_000);
        }
    }

    @Test
    void exportYearCreatesIncreaseDecreaseAndTwelveMonthSheets() throws Exception {
        when(movementRepository.findConfirmedForExport(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 1)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 12, 31))
        )).thenReturn(List.of());
        for (int month = 1; month <= 12; month++) {
            when(rosterProjectionService.projectedItems(LocalDate.of(2026, month, 1))).thenReturn(List.of());
        }

        HrExcelExportService.ExportFile file = service.exportYear(2026);

        assertThat(file.fileName()).isEqualTo("hr-nam-2026.xlsx");
        assertThat(sheetNames(file.content())).containsExactly(
                "TĂNG", "GIẢM",
                "T1-26", "T2-26", "T3-26", "T4-26", "T5-26", "T6-26",
                "T7-26", "T8-26", "T9-26", "T10-26", "T11-26", "T12-26"
        );
        assertThat(zipEntry(file.content(), "xl/worksheets/sheet14.xml")).isNotBlank();
    }

    @Test
    void exportLaborBookKeepsLongFieldsReadable() throws Exception {
        LocalDate period = LocalDate.of(2026, 8, 1);
        HrRosterProjectionService.ProjectedRosterItem item = new HrRosterProjectionService.ProjectedRosterItem(
                "item-1",
                null,
                1,
                1,
                "A268",
                "Nguyễn Công Huân",
                "HC",
                "Hành chính",
                "NV",
                "Nhân viên",
                "BT",
                "Bình thường",
                HrEmploymentStatus.ACTIVE,
                LocalDate.of(2013, 4, 15),
                null,
                BigDecimal.valueOf(12),
                HrRosterInclusionReason.BASELINE,
                null,
                null,
                null
        );
        when(rosterProjectionService.projectedItems(period)).thenReturn(List.of(item));

        HrExcelExportService.ExportFile file = service.exportLaborBookMonth(2026, 8);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            var sheet = workbook.getSheet("SO_QUAN_LY_LAO_DONG");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getColumnWidth(0)).isBetween(4 * 256, 6 * 256);   // A: STT
            assertThat(sheet.getColumnWidth(2)).isGreaterThanOrEqualTo(20 * 256); // C: Họ và tên
            assertThat(sheet.getColumnWidth(6)).isGreaterThanOrEqualTo(32 * 256);  // G: Nơi cư trú
            assertThat(sheet.getColumnWidth(14)).isGreaterThanOrEqualTo(12 * 256); // O: BHYT
            assertThat(sheet.getColumnWidth(15)).isGreaterThanOrEqualTo(12 * 256); // P: BHTN
            assertThat(sheet.getColumnWidth(25)).isGreaterThanOrEqualTo(30 * 256); // Z: Chấm dứt HĐLĐ
            assertThat(sheet.getRow(6).getHeightInPoints()).isGreaterThanOrEqualTo(50f);
            assertThat(sheet.getRow(4).getHeightInPoints()).isGreaterThanOrEqualTo(30f);
            assertThat(sheet.getRow(5).getHeightInPoints()).isGreaterThanOrEqualTo(80f);
            assertThat(sheet.getRow(6).getCell(6).getCellStyle().getWrapText()).isTrue();
            assertThat(sheet.getRow(6).getCell(21).getCellStyle().getWrapText()).isTrue();
            assertThat(sheet.getRow(6).getCell(25).getCellStyle().getWrapText()).isTrue();
        }
    }

    private static List<String> sheetNames(byte[] workbook) throws Exception {
        String workbookXml = zipEntry(workbook, "xl/workbook.xml");
        assertThat(workbookXml).isNotBlank();
        return java.util.regex.Pattern.compile("<sheet name=\"([^\"]+)\"")
                .matcher(workbookXml)
                .results()
                .map(match -> match.group(1))
                .toList();
    }

    private static String zipEntry(byte[] workbook, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
