package com.booking.system.hr.api;

import com.booking.system.hr.entity.HrAttendanceImport;
import com.booking.system.hr.entity.HrAttendanceRecord;
import com.booking.system.hr.entity.HrDepartment;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeEmployment;
import com.booking.system.hr.enums.HrAttendanceImportStatus;
import com.booking.system.hr.enums.HrAttendanceRecordStatus;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.repository.HrAttendanceImportRepository;
import com.booking.system.hr.repository.HrAttendanceRecordRepository;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrSystemSettingRepository;
import com.booking.system.hr.service.HrAttendanceService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrAttendanceServiceTest {

    @Mock private HrAttendanceImportRepository importRepository;
    @Mock private HrAttendanceRecordRepository recordRepository;
    @Mock private HrEmployeeRepository employeeRepository;
    @Mock private HrSystemSettingRepository settingRepository;
    @Mock private HrAuditEventRepository auditRepository;

    private HrAttendanceService service;

    @BeforeEach
    void setUp() {
        service = new HrAttendanceService(importRepository, recordRepository, employeeRepository, settingRepository, auditRepository);
    }

    @Test
    void recognizesSupportedDatesKeepsNoPunchAndAutoFillsOnlyMissingSide() throws Exception {
        HrEmployee employee = employee("A395", "Ngô Thanh Vy");
        when(employeeRepository.findByEmployeeCode(anyString())).thenReturn(Optional.of(employee));
        when(importRepository.findByFileSha256(anyString())).thenReturn(Optional.empty());
        when(importRepository.save(any(HrAttendanceImport.class))).thenAnswer(invocation -> {
            HrAttendanceImport saved = invocation.getArgument(0);
            saved.setId("attendance-1");
            saved.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            return saved;
        });
        List<HrAttendanceRecord> savedRecords = new ArrayList<>();
        when(recordRepository.save(any(HrAttendanceRecord.class))).thenAnswer(invocation -> {
            HrAttendanceRecord saved = invocation.getArgument(0);
            saved.setId("record-" + savedRecords.size());
            savedRecords.add(saved);
            return saved;
        });

        var result = service.upload("XNK T7.2026.xlsx", workbookFixture(), actor(), "07/2026");

        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.validRows()).isEqualTo(4);
        assertThat(result.autoFilledRows()).isEqualTo(1);
        assertThat(result.noPunchRows()).isEqualTo(1);
        assertThat(result.errorRows()).isZero();
        assertThat(savedRecords).extracting(HrAttendanceRecord::getWorkDate).containsExactly(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 4));
        assertThat(savedRecords.get(1).getStatus()).isEqualTo(HrAttendanceRecordStatus.AUTO_FILLED);
        assertThat(savedRecords.get(1).getCheckIn()).isEqualTo(LocalTime.of(7, 30));
        assertThat(savedRecords.get(1).getCheckOut()).isEqualTo(LocalTime.of(16, 30));
        assertThat(savedRecords.get(2).getStatus()).isEqualTo(HrAttendanceRecordStatus.NO_PUNCH);
        assertThat(savedRecords.get(2).getCheckIn()).isNull();
        assertThat(savedRecords.get(2).getCheckOut()).isNull();
    }

    @Test
    void monthlySummaryUsesConfirmedImportsAndDeduplicatesEmployeeDate() {
        HrAttendanceImport batch = confirmedImport("attendance-1", "07/2026");
        when(importRepository.findAllByAttendanceMonthAndStatusOrderByCreatedAtAsc("07/2026", HrAttendanceImportStatus.CONFIRMED))
                .thenReturn(List.of(batch));
        HrAttendanceRecord first = record("attendance-1", "A395", LocalDate.of(2026, 7, 1), HrAttendanceRecordStatus.AUTO_FILLED, 5, 0);
        HrAttendanceRecord duplicateBetter = record("attendance-1", "A395", LocalDate.of(2026, 7, 1), HrAttendanceRecordStatus.VALID, 0, 0);
        HrAttendanceRecord noPunch = record("attendance-1", "A395", LocalDate.of(2026, 7, 2), HrAttendanceRecordStatus.NO_PUNCH, 0, 0);
        when(recordRepository.findByImportIdIn(List.of("attendance-1"))).thenReturn(List.of(first, duplicateBetter, noPunch));
        when(employeeRepository.findAttendanceEmployeesByCodes(any())).thenReturn(List.of(employee("A395", "Ngô Thanh Vy")));

        var summary = service.monthlySummary("07/2026", null, null);

        assertThat(summary.confirmedImports()).isEqualTo(1);
        assertThat(summary.totalEmployees()).isEqualTo(1);
        assertThat(summary.workDays()).isEqualTo(1);
        assertThat(summary.noPunchRows()).isEqualTo(1);
        assertThat(summary.autoFilledRows()).isZero();
        assertThat(summary.onTimeRate()).isEqualTo(100.0);
    }

    @Test
    void congExportMatchesApprovedTwoRowCalendarLayout() throws Exception {
        HrAttendanceImport batch = confirmedImport("attendance-1", "09/2026");
        batch.setSourceFileName("KHVT T9.2026.xlsx");
        when(importRepository.findById("attendance-1")).thenReturn(Optional.of(batch));

        HrAttendanceRecord firstDay = record("attendance-1", "A339", LocalDate.of(2026, 9, 1),
                HrAttendanceRecordStatus.VALID, 0, 0);
        HrAttendanceRecord thirdDay = record("attendance-1", "A339", LocalDate.of(2026, 9, 3),
                HrAttendanceRecordStatus.MISSING_CHECK_IN, 0, 0);
        when(recordRepository.findByImportIdOrderBySourceRowNumber("attendance-1"))
                .thenReturn(List.of(firstDay, thirdDay));
        when(employeeRepository.findAttendanceEmployeesByCodes(any())).thenReturn(List.of(employeeWithDepartment(
                "A339", "Lê Minh Toàn", "Phòng Kế hoạch Vật tư")));

        HrAttendanceService.ExportFile exported = service.exportCong("attendance-1");

        assertThat(exported.fileName()).isEqualTo("CONG_KHVT T9.2026.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.content()))) {
            var sheet = workbook.getSheet("Công");
            assertThat(sheet.getMergedRegions()).extracting(CellRangeAddress::formatAsString).containsExactlyInAnyOrder(
                    "A3:AH3", "A4:A5", "B4:B5", "C4:C5", "AH4:AH5");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("(KẾ HOẠCH VẬT TƯ)");
            assertThat(sheet.getRow(3).getCell(3).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(3).getCell(32).getNumericCellValue()).isEqualTo(30);
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue()).isEqualTo("T.3");
            assertThat(sheet.getRow(3).getCell(33).getStringCellValue()).isEqualTo("Ngày\ncông");
            assertThat(sheet.getRow(5).getCell(3).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(5).getCell(4).getNumericCellValue()).isZero();
            assertThat(sheet.getRow(5).getCell(5).getNumericCellValue()).isEqualTo(0.5);
            assertThat(sheet.getRow(5).getCell(33).getNumericCellValue()).isEqualTo(1.5);
            assertThat(sheet.getColumnWidth(2)).isEqualTo((int) (25.71 * 256));
            assertThat(sheet.getColumnWidth(3)).isEqualTo((int) (4.71 * 256));
            assertThat(sheet.getRow(5).getCell(3).getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.THIN);
            assertThat(sheet.getRow(5).getCell(3).getCellStyle().getAlignment()).isEqualTo(HorizontalAlignment.CENTER);
            assertThat(sheet.getRow(5).getCell(3).getCellStyle().getFontIndexAsInt()).isGreaterThan(0);
            assertThat(workbook.getFontAt(sheet.getRow(5).getCell(3).getCellStyle().getFontIndexAsInt()).getFontName())
                    .isEqualTo("Arial");
        }
    }

    private static byte[] workbookFixture() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("XNK");
            sheet.createRow(0).createCell(0).setCellValue("GIỜ CHẤM CÔNG");
            sheet.createRow(1).createCell(0).setCellValue("STT");
            addTextRow(sheet.createRow(2), "01-Jul-26", "07:36", "17:28");

            Row numericDateRow = sheet.createRow(3);
            numericDateRow.createCell(1).setCellValue("A395");
            numericDateRow.createCell(2).setCellValue("Ngô Thanh Vy");
            numericDateRow.createCell(4).setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 7, 2)));
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd-mmm-yy"));
            numericDateRow.getCell(4).setCellStyle(dateStyle);
            numericDateRow.createCell(6).setCellValue("07:30");

            addTextRow(sheet.createRow(4), "2026-07-03", null, null);
            addTextRow(sheet.createRow(5), "4/7/2026", "07:32", "16:45");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void addTextRow(Row row, String date, String checkIn, String checkOut) {
        row.createCell(1).setCellValue("A395");
        row.createCell(2).setCellValue("Ngô Thanh Vy");
        row.createCell(4).setCellValue(date);
        if (checkIn != null) row.createCell(6).setCellValue(checkIn);
        if (checkOut != null) row.createCell(8).setCellValue(checkOut);
    }

    private static HrAttendanceRecord record(String importId, String code, LocalDate date,
                                               HrAttendanceRecordStatus status, int late, int early) {
        HrAttendanceRecord record = new HrAttendanceRecord();
        record.setImportId(importId);
        record.setEmployeeCode(code);
        record.setEmployeeName("Ngô Thanh Vy");
        record.setWorkDate(date);
        record.setStatus(status);
        record.setWorkValue(status == HrAttendanceRecordStatus.NO_PUNCH ? BigDecimal.ZERO : BigDecimal.ONE);
        record.setLateMinutes(late);
        record.setEarlyMinutes(early);
        return record;
    }

    private static HrAttendanceImport confirmedImport(String id, String month) {
        HrAttendanceImport batch = new HrAttendanceImport();
        batch.setId(id);
        batch.setAttendanceMonth(month);
        batch.setStatus(HrAttendanceImportStatus.CONFIRMED);
        return batch;
    }

    private static HrEmployee employee(String code, String name) {
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode(code);
        employee.setFullName(name);
        return employee;
    }

    private static HrEmployee employeeWithDepartment(String code, String name, String departmentName) {
        HrEmployee employee = employee(code, name);
        HrDepartment department = new HrDepartment();
        department.setName(departmentName);
        HrEmployeeEmployment employment = new HrEmployeeEmployment();
        employment.setEmployee(employee);
        employment.setEmployeeId(employee.getId());
        employment.setDepartment(department);
        employee.setEmployment(employment);
        return employee;
    }

    private static HrImportActor actor() {
        return new HrImportActor("USER:manager-1", "HR Manager", "MANAGER");
    }
}
