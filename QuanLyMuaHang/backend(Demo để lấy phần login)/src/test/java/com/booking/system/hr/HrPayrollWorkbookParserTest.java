package com.booking.system.hr;

import com.booking.system.hr.service.HrPayrollWorkbookParser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HrPayrollWorkbookParserTest {

    private final HrPayrollWorkbookParser parser = new HrPayrollWorkbookParser();

    @Test
    void keepsEmployeeRowsWithBlankSequenceAndIgnoresNonEmployeeCodes() throws Exception {
        byte[] workbook = workbook(new Object[][]{
                {1, "D001", "Nhân viên Một", "001", 22, 10_000_000, 1_000_000, 9_000_000},
                {null, "D002", "Nhân viên Hai", null, 21, 9_000_000, 900_000, 8_100_000},
                {null, "CĐ", "Mã đơn vị Một", null, null, 5_000_000, 0, 5_000_000},
                {null, "CĐ", "Mã đơn vị Hai", null, null, 5_000_000, 0, 5_000_000},
        });

        var parsed = parser.parse(workbook);

        assertThat(parsed.payrollMonth()).isEqualTo("07/2026");
        assertThat(parsed.rows()).extracting(HrPayrollWorkbookParser.PayrollRow::employeeCode)
                .containsExactly("D001", "D002");
        assertThat(parsed.rows().get(1).rowNumber()).isEqualTo(4);
    }

    @Test
    void stillRejectsDuplicateEmployeeCodesThatCanBeSent() throws Exception {
        byte[] workbook = workbook(new Object[][]{
                {1, "D001", "Nhân viên Một", "001", 22, 10_000_000, 1_000_000, 9_000_000},
                {null, "D001", "Nhân viên Một", "001", 22, 10_000_000, 1_000_000, 9_000_000},
        });

        assertThatThrownBy(() -> parser.parse(workbook))
                .hasMessageContaining("D001")
                .hasMessageContaining("xuất hiện nhiều lần");
    }

    private static byte[] workbook(Object[][] data) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("Tháng 7/2026");
            var header = sheet.createRow(1);
            String[] headers = {"STT", "Mã số", "Họ và Tên", "Số tài khoản", "Công", "Tiền lương", "Tổng thu", "NH chuyển"};
            for (int column = 0; column < headers.length; column++) header.createCell(column).setCellValue(headers[column]);
            for (int index = 0; index < data.length; index++) {
                var row = sheet.createRow(index + 2);
                for (int column = 0; column < data[index].length; column++) {
                    Object value = data[index][column];
                    if (value instanceof Number number) row.createCell(column).setCellValue(number.doubleValue());
                    else if (value != null) row.createCell(column).setCellValue(value.toString());
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
