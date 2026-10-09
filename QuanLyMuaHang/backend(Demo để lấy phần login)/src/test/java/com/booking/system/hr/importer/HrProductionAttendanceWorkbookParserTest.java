package com.booking.system.hr.importer;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HrProductionAttendanceWorkbookParserTest {
    private final HrProductionAttendanceWorkbookParser parser = new HrProductionAttendanceWorkbookParser();

    @Test
    void retainsBlankDaysEveryPunchAndCleansMachineNameMarker() throws Exception {
        byte[] workbook;
        try (var excel = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = excel.createSheet("T8");
            sheet.createRow(0).createCell(0).setCellValue("GIỜ CHẤM CÔNG - 08/2026");
            var header = sheet.createRow(1);
            header.createCell(1).setCellValue("Mã nhân viên");
            header.createCell(2).setCellValue("Tên nhân viên");
            header.createCell(4).setCellValue("Ngày");
            for (int column = 6; column <= 9; column++) header.createCell(column).setCellValue("Chấm lần " + (column - 5));

            var first = sheet.createRow(2);
            first.createCell(1).setCellValue("B124");
            first.createCell(2).setCellValue("Đỗ_x0000_ Đình Cường");
            first.createCell(4).setCellValue("01-Aug-26");
            first.createCell(6).setCellValue("06:00");
            first.createCell(7).setCellValue("12:00");
            first.createCell(8).setCellValue("13:00");
            first.createCell(9).setCellValue("17:45");

            var blankDay = sheet.createRow(3);
            blankDay.createCell(1).setCellValue("B124");
            blankDay.createCell(2).setCellValue("Đỗ Đình Cường");
            blankDay.createCell(4).setCellValue(LocalDate.of(2026, 8, 2));
            excel.write(output);
            workbook = output.toByteArray();
        }

        var parsed = parser.parse(workbook, "2026-08");

        assertThat(parsed.totalRows()).isEqualTo(2);
        assertThat(parsed.totalPunches()).isEqualTo(4);
        assertThat(parsed.days().get(0).employeeName()).isEqualTo("Đỗ Đình Cường");
        assertThat(parsed.days().get(0).punches()).extracting(value -> value.punchedAt().toLocalTime())
                .containsExactly(LocalTime.of(6, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 45));
        assertThat(parsed.days().get(1).punches()).isEmpty();
    }

    @Test
    void parsesAllActualDepartmentWorkbooksWithoutDroppingRowsOrPunches() throws Exception {
        List<String> files = List.of(
                "PTC T8.2026.xlsx", "XNK T8.2026.xlsx", "KHVT T8.2026.xlsx",
                "KTCD T8.2026.xlsx", "VP KHO T8.2026.xlsx", "VPXNHC T8.2026.xlsx",
                "KCS T8.2026.xlsx", "KTTC T8.2026.xlsx", "KD T8.2026.xlsx");
        Path fixtureDirectory = Path.of("..", "FileChamCong");
        assumeTrue(files.stream().allMatch(file -> Files.isRegularFile(fixtureDirectory.resolve(file))),
                "Bỏ qua đối soát nếu bộ Excel phòng ban cục bộ không có trong checkout.");

        var parsed = files.stream().map(file -> {
            try {
                return parser.parse(Files.readAllBytes(fixtureDirectory.resolve(file)), "2026-08");
            } catch (Exception exception) {
                throw new AssertionError("Không đọc được file thực tế " + file, exception);
            }
        }).toList();

        assertThat(parsed).allMatch(value -> value.attendanceMonth().toString().equals("2026-08"));
        assertThat(parsed.stream().mapToInt(HrProductionAttendanceWorkbookParser.ParsedWorkbook::totalRows).sum())
                .isEqualTo(2759);
        assertThat(parsed.stream().mapToInt(HrProductionAttendanceWorkbookParser.ParsedWorkbook::totalPunches).sum())
                .isEqualTo(3386);
        assertThat(parsed.stream().flatMap(value -> value.days().stream())
                .map(HrProductionAttendanceWorkbookParser.ParsedDay::employeeCode).distinct())
                .hasSize(89);

        var kcs = parsed.get(6).days().stream().filter(value -> value.employeeCode().equals("A057")).toList();
        assertThat(kcs).isNotEmpty();
        assertThat(kcs.stream().flatMap(value -> value.punches().stream())
                .map(value -> value.punchedAt().toLocalTime()))
                .anyMatch(value -> !value.isBefore(LocalTime.of(12, 30)) && !value.isAfter(LocalTime.of(13, 30)))
                .anyMatch(value -> !value.isBefore(LocalTime.of(21, 30)) && !value.isAfter(LocalTime.of(22, 30)));
    }
}
