package com.booking.system.hr;

import com.booking.system.hr.importer.HrImportIssueSeverity;
import com.booking.system.hr.importer.HrSalaryRaiseWorkbookParser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HrSalaryRaiseWorkbookParserTest {
    private final HrSalaryRaiseWorkbookParser parser = new HrSalaryRaiseWorkbookParser();

    @Test
    void parsesSampleContractAndKeepsSourceNextReviewDateAsWarning() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("1-7-2026");
            var header = sheet.createRow(1);
            String[] headers = {"MS", "HỌ VÀ TÊN", "Lương 2026", "PHỤ CẤP 2026", "TỔNG LƯƠNG 2026",
                    "Bậc 2026", "Mã số 2026", "Bậc sau khi nâng", "Lương sau nâng bậc",
                    "Phụ cấp Sau nâng", "TỔNG LƯƠNG SAU NÂNG", "HẠN NÂNG BẬC",
                    "NGÀY NÂNG BẬC GẦN NHẤT", "NGÀY TỚI HẠN"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(2);
            row.createCell(0).setCellValue("A339");
            row.createCell(1).setCellValue("Lê Minh Toàn");
            row.createCell(2).setCellValue(6_647_000);
            row.createCell(3).setCellValue(1_892_000);
            row.createCell(4).setCellValue(8_539_000);
            row.createCell(5).setCellValue("2/6");
            row.createCell(6).setCellValue("B3.1");
            row.createCell(7).setCellValue("3/6");
            row.createCell(8).setCellValue(7_046_000);
            row.createCell(9).setCellValue(1_892_000);
            row.createCell(10).setCellValue(8_938_000);
            row.createCell(11).setCellValue(48);
            row.createCell(12).setCellValue(date(2026, 7, 1));
            row.createCell(13).setCellValue(date(2030, 6, 10));
            workbook.write(output);
            bytes = output.toByteArray();
        }

        var parsed = parser.parse(bytes);
        var result = parsed.rows().getFirst();

        assertThat(parsed.sheetName()).isEqualTo("1-7-2026");
        assertThat(result.data().employeeCode()).isEqualTo("A339");
        assertThat(result.data().currentTotal()).isEqualByComparingTo("8539000");
        assertThat(result.data().newTotal()).isEqualByComparingTo("8938000");
        assertThat(result.data().effectiveDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.data().nextReviewDate()).isEqualTo(LocalDate.of(2030, 6, 10));
        assertThat(result.issues()).anyMatch(issue -> issue.severity() == HrImportIssueSeverity.WARNING
                && issue.message().contains("giữ đúng ngày trong file"));
        assertThat(result.issues()).noneMatch(issue -> issue.severity() == HrImportIssueSeverity.ERROR);
    }

    @Test
    void parsesTheProvidedJulyWorkbookWhenAvailable() throws Exception {
        Path sample = Path.of("../Tang luong thang 7-2026.xlsx");
        assumeTrue(Files.isRegularFile(sample), "File nghiệp vụ chỉ có ở workspace local");

        var parsed = parser.parse(Files.readAllBytes(sample));
        assertThat(parsed.rows()).hasSize(78);
        assertThat(parsed.rows().stream().map(row -> row.data().employeeCode()).distinct().count())
                .isEqualTo(parsed.rows().size());
        assertThat(parsed.rows()).allSatisfy(row -> assertThat(row.issues())
                .noneMatch(issue -> issue.severity() == HrImportIssueSeverity.ERROR));
        var a339 = parsed.rows().stream()
                .filter(row -> "A339".equals(row.data().employeeCode()))
                .findFirst().orElseThrow();

        assertThat(a339.data().fullName()).isEqualTo("Lê Minh Toàn");
        assertThat(a339.data().currentTotal()).isEqualByComparingTo("8539000");
        assertThat(a339.data().newTotal()).isEqualByComparingTo("8938000");
        assertThat(a339.data().reviewCycleMonths()).isEqualTo(48);
        assertThat(a339.data().effectiveDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(a339.data().nextReviewDate()).isEqualTo(LocalDate.of(2030, 6, 10));
        assertThat(a339.issues()).noneMatch(issue -> issue.severity() == HrImportIssueSeverity.ERROR);

        var terminal = parsed.rows().stream()
                .filter(row -> "A112".equals(row.data().employeeCode()))
                .findFirst().orElseThrow();
        assertThat(terminal.data().reviewCycleMonths()).isNull();
        assertThat(terminal.data().nextReviewDate()).isNull();
    }

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
