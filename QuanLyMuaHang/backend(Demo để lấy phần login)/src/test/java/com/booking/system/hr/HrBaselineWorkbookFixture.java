package com.booking.system.hr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class HrBaselineWorkbookFixture {

    private static final List<String> HEADERS = List.of(
            "STT", "STT(Phòng ban)", "MÃ SỐ", "Số sổ BHXH", "HỌ VÀ TÊN", "BHYT",
            "Lương", "Phụ cấp", "TỔNG THU NHẬP", "GIỚI TÍNH", "DÂN TỘC", "TÔN GIÁO",
            "CHỨC VỤ", "ĐƠN VỊ CÔNG TÁC", "NGÀY SINH", "NGÀY LÀM", "HĐLĐ", "SỐ HĐLĐ",
            "NĂM CÔNG TÁC", "CMND", "CCCD", "NGÀY CẤP", "MÔI TRƯỜNG LÀM VIỆC",
            "NƠI CẤP", "NƠI SINH", "NƠI SINH (Sau sát nhập)", "ĐỊA CHỈ THƯỜNG TRÚ",
            "ĐỊA CHỈ HIỆN NAY", "SỐ ĐIỆN THOẠI", "TRÌNH ĐỘ", "CHUYÊN NGÀNH",
            "CÔNG VIỆC PHẢI LÀM", "TUỔI", "NGÀY NGHỈ PHÉP"
    );

    private HrBaselineWorkbookFixture() {
    }

    static byte[] validWorkbook() {
        return workbook(false, false, false, false);
    }

    static byte[] withWrongHeader() {
        return workbook(true, false, false, false);
    }

    static byte[] withFormula() {
        return workbook(false, true, false, false);
    }

    static byte[] withDataAfterContract() {
        return workbook(false, false, true, false);
    }

    static byte[] withNumericLegacyIdentity() {
        return workbook(false, false, false, true);
    }

    static byte[] workforceSnapshotWorkbook() {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                add(zip, "xl/workbook.xml", workforceSnapshotWorkbookXml());
                add(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
                add(zip, "xl/worksheets/sheet1.xml", emptySheet());
                add(zip, "xl/worksheets/sheet2.xml", emptySheet());
                add(zip, "xl/worksheets/sheet3.xml", workforceSnapshotSourceSheet());
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] workbook(boolean wrongHeader, boolean formula, boolean extraRow,
                                   boolean numericLegacyIdentity) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                add(zip, "xl/workbook.xml", workbookXml());
                add(zip, "xl/_rels/workbook.xml.rels", workbookRelationships());
                add(zip, "xl/worksheets/sheet1.xml", emptySheet());
                add(zip, "xl/worksheets/sheet2.xml", emptySheet());
                add(zip, "xl/worksheets/sheet3.xml", sourceSheet(wrongHeader, formula, extraRow, numericLegacyIdentity));
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String workbookXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="GIAM" sheetId="1" state="visible" r:id="rId1"/>
                    <sheet name="TĂNG" sheetId="2" state="visible" r:id="rId2"/>
                    <sheet name="T6-26" sheetId="3" state="visible" r:id="rId3"/>
                  </sheets>
                </workbook>
                """;
    }

    private static String workbookRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
                </Relationships>
                """;
    }

    private static String workforceSnapshotWorkbookXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="TĂNG" sheetId="1" state="visible" r:id="rId1"/>
                    <sheet name="GIẢM" sheetId="2" state="visible" r:id="rId2"/>
                    <sheet name="T6-26" sheetId="3" state="visible" r:id="rId3"/>
                  </sheets>
                </workbook>
                """;
    }

    private static String emptySheet() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <dimension ref="A1"/><sheetData/>
                </worksheet>
                """;
    }

    private static String sourceSheet(boolean wrongHeader, boolean formula, boolean extraRow,
                                      boolean numericLegacyIdentity) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <dimension ref="A1:AH333"/>
                  <sheetData>
                """);
        xml.append("<row r=\"4\">");
        for (int column = 0; column < HEADERS.size(); column++) {
            String value = wrongHeader && column == 2 ? "MÃ KHÁC" : HEADERS.get(column);
            xml.append(inlineCell(columnName(column) + "4", value));
        }
        xml.append("</row>");

        for (int index = 1; index <= 329; index++) {
            int row = index + 4;
            String employeeCode = "T%03d".formatted(index);
            xml.append("<row r=\"").append(row).append("\">");
            xml.append(numberCell("A" + row, Integer.toString(index)));
            xml.append(numberCell("B" + row, Integer.toString(index)));
            xml.append(inlineCell("C" + row, employeeCode));
            xml.append(inlineCell("D" + row, "99%08d".formatted(index)));
            xml.append(inlineCell("E" + row, "Nhân sự thử nghiệm %03d".formatted(index)));
            xml.append(inlineCell("F" + row, "TE%013d".formatted(index)));
            xml.append(numberCell("G" + row, "1000"));
            xml.append(numberCell("H" + row, "100"));
            if (formula && index == 1) {
                xml.append("<c r=\"I5\"><f>G5+H5</f><v>1100</v></c>");
            } else {
                xml.append(numberCell("I" + row, "1100"));
            }
            xml.append(inlineCell("J" + row, index % 2 == 0 ? "Nữ" : "Nam"));
            xml.append(inlineCell("K" + row, "Kinh"));
            xml.append(inlineCell("L" + row, "Không"));
            xml.append(inlineCell("M" + row, "Chuyên viên"));
            xml.append(inlineCell("N" + row, "Phòng thử nghiệm"));
            xml.append(numberCell("O" + row, "36526"));
            xml.append(numberCell("P" + row, "43831"));
            xml.append(inlineCell("Q" + row, "Không thời hạn"));
            xml.append(inlineCell("R" + row, "HD-%03d".formatted(index)));
            xml.append(inlineCell("S" + row, "6 năm"));
            if (numericLegacyIdentity && index == 1) {
                xml.append(numberCell("T" + row, "123456789"));
            } else {
                xml.append(inlineCell("T" + row, "1%08d".formatted(index)));
            }
            xml.append(inlineCell("U" + row, "001%09d".formatted(index)));
            xml.append(numberCell("V" + row, "43831"));
            xml.append(inlineCell("W" + row, "Bình Thường"));
            xml.append(inlineCell("X" + row, "Nơi cấp thử nghiệm"));
            xml.append(inlineCell("Y" + row, "Địa điểm thử nghiệm"));
            xml.append(index <= 29 ? errorCell("Z" + row, "#N/A")
                    : inlineCell("Z" + row, "Địa điểm mới thử nghiệm"));
            xml.append(inlineCell("AA" + row, "Địa chỉ thử nghiệm"));
            xml.append(inlineCell("AB" + row, "Địa chỉ hiện tại thử nghiệm"));
            xml.append(inlineCell("AC" + row, "090000%04d".formatted(index)));
            xml.append(inlineCell("AD" + row, "Đại học"));
            xml.append(inlineCell("AE" + row, "Ngành thử nghiệm"));
            xml.append(inlineCell("AF" + row, "Công việc thử nghiệm"));
            xml.append(numberCell("AG" + row, "26"));
            xml.append("</row>");
        }
        if (extraRow) xml.append("<row r=\"334\">").append(numberCell("A334", "1")).append("</row>");
        xml.append("</sheetData><autoFilter ref=\"A4:AH333\"/></worksheet>");
        return xml.toString();
    }

    private static String workforceSnapshotSourceSheet() {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <dimension ref="A1:AH343"/>
                  <sheetData>
                """);
        xml.append("<row r=\"4\">");
        for (int column = 0; column < HEADERS.size(); column++) {
            xml.append(inlineCell(columnName(column) + "4", HEADERS.get(column)));
        }
        xml.append("</row>");

        List<Integer> existingIndexes = java.util.stream.IntStream.rangeClosed(3, 329)
                .boxed()
                .toList();
        int displayOrder = 0;
        for (int originalIndex : existingIndexes) {
            displayOrder++;
            appendWorkforceRow(
                    xml,
                    displayOrder + 4,
                    displayOrder,
                    "T%03d".formatted(originalIndex),
                    "Nhân sự thử nghiệm %03d".formatted(originalIndex),
                    originalIndex,
                    originalIndex == 3
            );
        }
        for (int increaseIndex = 1; increaseIndex <= 12; increaseIndex++) {
            displayOrder++;
            appendWorkforceRow(
                    xml,
                    displayOrder + 4,
                    displayOrder,
                    "U%03d".formatted(increaseIndex),
                    "Nhân sự tăng %03d".formatted(increaseIndex),
                    400 + increaseIndex,
                    false
            );
        }
        xml.append("</sheetData><autoFilter ref=\"A4:AH343\"/></worksheet>");
        return xml.toString();
    }

    private static void appendWorkforceRow(
            StringBuilder xml,
            int row,
            int displayOrder,
            String employeeCode,
            String fullName,
            int uniqueIndex,
            boolean updateSalary
    ) {
        String baseSalary = updateSalary ? "2000" : "1000";
        String totalIncome = updateSalary ? "2100" : "1100";
        xml.append("<row r=\"").append(row).append("\">");
        xml.append(numberCell("A" + row, Integer.toString(displayOrder)));
        xml.append(numberCell("B" + row, Integer.toString(displayOrder)));
        xml.append(inlineCell("C" + row, employeeCode));
        xml.append(inlineCell("D" + row, "88%08d".formatted(uniqueIndex)));
        xml.append(inlineCell("E" + row, fullName));
        xml.append(inlineCell("F" + row, "UP%013d".formatted(uniqueIndex)));
        xml.append(numberCell("G" + row, baseSalary));
        xml.append(numberCell("H" + row, "100"));
        xml.append(numberCell("I" + row, totalIncome));
        xml.append(inlineCell("J" + row, uniqueIndex % 2 == 0 ? "Nữ" : "Nam"));
        xml.append(inlineCell("K" + row, "Kinh"));
        xml.append(inlineCell("L" + row, "Không"));
        xml.append(inlineCell("M" + row, "Chuyên viên"));
        xml.append(inlineCell("N" + row, "Phòng thử nghiệm"));
        xml.append(numberCell("O" + row, "36526"));
        xml.append(numberCell("P" + row, "45809"));
        xml.append(inlineCell("Q" + row, "Không thời hạn"));
        xml.append(inlineCell("R" + row, "UP-HD-%03d".formatted(uniqueIndex)));
        xml.append(inlineCell("S" + row, "6 năm"));
        xml.append(inlineCell("T" + row, "2%08d".formatted(uniqueIndex)));
        xml.append(inlineCell("U" + row, "002%09d".formatted(uniqueIndex)));
        xml.append(numberCell("V" + row, "43831"));
        xml.append(inlineCell("W" + row, "Bình Thường"));
        xml.append(inlineCell("X" + row, "Nơi cấp thử nghiệm"));
        xml.append(inlineCell("Y" + row, "Địa điểm thử nghiệm"));
        xml.append(inlineCell("Z" + row, "Địa điểm mới thử nghiệm"));
        xml.append(inlineCell("AA" + row, "Địa chỉ thử nghiệm"));
        xml.append(inlineCell("AB" + row, "Địa chỉ hiện tại thử nghiệm"));
        xml.append(inlineCell("AC" + row, "091000%04d".formatted(uniqueIndex)));
        xml.append(inlineCell("AD" + row, "Đại học"));
        xml.append(inlineCell("AE" + row, "Ngành thử nghiệm"));
        xml.append(inlineCell("AF" + row, "Công việc thử nghiệm"));
        xml.append(numberCell("AG" + row, "26"));
        xml.append("</row>");
    }

    private static String inlineCell(String reference, String value) {
        return "<c r=\"" + reference + "\" t=\"inlineStr\"><is><t>" + escape(value) + "</t></is></c>";
    }

    private static String numberCell(String reference, String value) {
        return "<c r=\"" + reference + "\"><v>" + value + "</v></c>";
    }

    private static String errorCell(String reference, String value) {
        return "<c r=\"" + reference + "\" t=\"e\"><v>" + value + "</v></c>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String columnName(int zeroBasedColumn) {
        int value = zeroBasedColumn + 1;
        StringBuilder name = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.append((char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.reverse().toString();
    }

    private static void add(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
