package com.example.quanlymuahang.importing;

import com.example.quanlymuahang.importing.application.SourceDocumentParser;
import com.example.quanlymuahang.importing.application.SourceDocumentModels.DocumentLine;
import com.example.quanlymuahang.importing.application.SourceDocumentModels.ParsedDocument;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.*;

class SourceDocumentParserTest {
    private final SourceDocumentParser parser = new SourceDocumentParser();

    @Test void classifiesContentAndPreservesSeparateRequestNumbersRatherThanGuessingFromTheFilename() throws Exception {
        try (var book = requestWorkbook()) {
            var sheet = book.getSheetAt(0);
            requestRow(sheet.createRow(5), "1", "Vòng bi 6204", "6204 2RS", "Cái", "2", "11");
            requestRow(sheet.createRow(6), "2", "Dây curoa A42", "A42", "Sợi", "3", "12");
            var parsed = parse(book, "report-renamed.xlsx", "REQUEST", null);
            assertThat(parsed.lines()).hasSize(2);
            assertThat(parsed.lines()).extracting(DocumentLine::requestNumber).containsExactly("11", "12");
            assertThat(parsed.lines().getFirst().unitPrice()).isNull();
            assertThat(parsed.lines().getFirst().sourceRow()).isEqualTo(6);
            assertThat(parsed.lines().getFirst().specification()).isEqualTo("6204 2RS");
        }
    }

    @Test void sourceSeparatorsAreExcludedButAnItemWithoutAPhieuIsVisibleAndNotAssignedToItsNeighbour() throws Exception {
        try (var book = requestWorkbook()) {
            var sheet = book.getSheetAt(0);
            requestRow(sheet.createRow(5), "1", "Vòng bi", "6204", "Cái", "1", "7");
            sheet.createRow(6).createCell(0).setCellValue(2);
            requestRow(sheet.createRow(7), "3", "Vật tư chưa có phiếu", "M8", "Cái", "5", "");
            var parsed = parse(book, "anything.xlsx", "REQUEST", null);
            assertThat(parsed.lines()).hasSize(2);
            var unassigned = parsed.lines().getLast();
            assertThat(unassigned.requestNumber()).isNullOrEmpty();
            assertThat(unassigned.issues()).anyMatch(issue -> issue.contains("REQUEST_NUMBER_MISSING"));
        }
    }

    @Test void hiddenWorkbookRowsAreExcludedFromTheDefaultPreview() throws Exception {
        try (var book = requestWorkbook()) {
            var sheet = book.getSheetAt(0);
            requestRow(sheet.createRow(5), "1", "Visible material", "A", "Cái", "1", "40");
            requestRow(sheet.createRow(6), "2", "Filtered material", "B", "Cái", "2", "39");
            sheet.getRow(6).setZeroHeight(true);
            var parsed = parse(book, "filtered.xlsx", "REQUEST", null);
            assertThat(parsed.lines()).extracting(DocumentLine::materialName).containsExactly("Visible material");
            assertThat(parsed.sheets().getFirst().excludedHiddenRows()).isEqualTo(1);
            assertThat(parsed.warnings()).contains("HIDDEN_ROWS_EXCLUDED");
        }
    }

    @Test void multipleSheetsRequireExplicitChoiceAndOnlyTheChosenSheetSuppliesRows() throws Exception {
        try (var book = requestWorkbook()) {
            requestRow(book.getSheetAt(0).createRow(5), "1", "First material", "Model 1", "Cái", "2", "1");
            var other = book.createSheet("Other");
            copyRequestHeaders(other);
            requestRow(other.createRow(5), "1", "Second material", "Model 2", "Cái", "3", "2");
            var undecided = parse(book, "both.xlsx", "REQUEST", null);
            assertThat(undecided.lines()).isEmpty();
            assertThat(undecided.sheets()).hasSize(2);
            assertThat(undecided.warnings()).anyMatch(issue -> issue.contains("SHEET_SELECTION_REQUIRED"));
            var chosen = parse(book, "both.xlsx", "REQUEST", "Other");
            assertThat(chosen.lines()).extracting(DocumentLine::materialName).containsExactly("Second material");
            assertThat(chosen.lines().getFirst().sourceSheet()).isEqualTo("Other");
        }
    }

    @Test void quotationOptionsWithoutSttAreRetainedAndVendorCodeIsNotAnInternalMaterialIdentity() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("Quote");
            sheet.createRow(0).createCell(0).setCellValue("Công Ty TNHH Thiên Phú");
            sheet.createRow(1).createCell(0).setCellValue("Ngày báo giá: 05/05/2026");
            String[] headers = {"STT", "Code", "Tên Hàng", "NXS", "Quy cách", "Đơn giá / chưa VAT", "SL", "Thành tiền (đã VAT)", "VAT", "Ghi chú"};
            var header = sheet.createRow(2);
            for (int col = 0; col < headers.length; col++) header.createCell(col).setCellValue(headers[col]);
            var first = sheet.createRow(3);
            String[] row = {"1", "vendor-code", "Nitric acid", "Merck", "2.5L/chai", "970000", "2", "2095200", "8", "Có sẵn"};
            for (int col = 0; col < row.length; col++) first.createCell(col).setCellValue(row[col]);
            var option = sheet.createRow(4);
            option.createCell(2).setCellValue("Nitric acid"); option.createCell(4).setCellValue("500ml/chai");
            option.createCell(5).setCellValue(65000); option.createCell(6).setCellValue(10);
            option.createCell(7).setCellValue(702000); option.createCell(8).setCellValue(0.08);
            var parsed = parse(book, "sctx.xlsx", "QUOTATION", null);
            assertThat(parsed.lines()).hasSize(2);
            assertThat(parsed.lines().getFirst().materialCode()).isNullOrEmpty();
            assertThat(parsed.lines().getFirst().raw().get("B4")).isEqualTo("vendor-code");
            assertThat(parsed.lines().getLast().vatPercent()).isEqualByComparingTo("8");
            assertThat(parsed.lines().getLast().unit()).isEqualTo("chai");
            assertThat(parsed.quotationDate()).isEqualTo("2026-05-05");
        }
    }

    @Test void cachedFormulaIsNeverRecalculatedAndItsUntrustedSourceIsRetained() throws Exception {
        try (var book = requestWorkbook()) {
            var sheet = book.getSheetAt(0);
            requestRow(sheet.createRow(5), "1", "Vật tư có công thức", "", "Cái", "2", "1");
            var formula = sheet.getRow(5).getCell(4);
            formula.setCellValue(2);
            formula.setCellFormula("99+1"); formula.getCTCell().setV("2");
            var parsed = parse(book, "cached.xlsx", "REQUEST", null);
            var line = parsed.lines().getFirst();
            assertThat(line.quantity()).isEqualByComparingTo("2");
            assertThat(line.raw().get("E6.formula")).isEqualTo("99+1");
            assertThat(line.issues()).anyMatch(issue -> issue.contains("CACHED_FORMULA_NOT_RECALCULATED"));
        }
    }

    @Test void aPartiallySpecifiedLineVatMustNotBecomeADefaultForUnspecifiedRows() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("Quote");
            String[] headers = {"Tên Hàng", "ĐVT", "SL", "Đơn giá chưa VAT", "VAT"};
            var header = sheet.createRow(0);
            for (int col = 0; col < headers.length; col++) header.createCell(col).setCellValue(headers[col]);
            for (int index = 1; index <= 2; index++) {
                var row = sheet.createRow(index); row.createCell(0).setCellValue("Vật tư " + index);
                row.createCell(1).setCellValue("Cái"); row.createCell(2).setCellValue(1); row.createCell(3).setCellValue(100);
            }
            sheet.getRow(1).createCell(4).setCellValue(0.08);
            var parsed = parse(book, "partial-vat.xlsx", "QUOTATION", null);
            assertThat(parsed.lines()).hasSize(2); assertThat(parsed.lines().getFirst().vatPercent()).isEqualByComparingTo("8");
            assertThat(parsed.lines().getLast().vatPercent()).isNull(); assertThat(parsed.vatPercent()).isNull();
            assertThat(parsed.warnings()).contains("PARTIAL_LINE_VAT_REQUIRES_CONFIRMATION");
        }
    }

    @Test void oldAppsScriptDataCannotBeImportedAsANewRequestOrSupplierQuotation() throws Exception {
        try (var book = new XSSFWorkbook()) {
            for (String sheet : List.of("NCC", "LICH_SU", "DON_HANG")) book.createSheet(sheet);
            for (String kind : List.of("REQUEST", "QUOTATION")) {
                assertThatThrownBy(() -> parse(book, "renamed-historical-data.xlsx", kind, "DON_HANG"))
                        .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("LEGACY_WORKBOOK_DETECTED"));
            }
        }
    }

    @Test void exactDecimalTokensAndTextQuantityAreNotRoundedOrSilentlyReplaced() throws Exception {
        try (var book = requestWorkbook()) {
            var sheet = book.getSheetAt(0);
            requestRow(sheet.createRow(5), "1", "Decimal item", "", "kg", "2", "1");
            var cell = sheet.getRow(5).getCell(4); cell.setCellValue(1); cell.getCTCell().setV("1.123456789012345678");
            requestRow(sheet.createRow(6), "2", "Weighed item", "", "kg", "Qua cân thực tế", "1");
            var parsed = parse(book, "precise.xlsx", "REQUEST", null);
            assertThat(parsed.lines().getFirst().quantity()).isEqualByComparingTo("1.123456789012345678");
            assertThat(parsed.lines().getFirst().raw().get("E6.numeric")).isEqualTo("1.123456789012345678");
            assertThat(parsed.lines().getLast().quantity()).isNull();
            assertThat(parsed.lines().getLast().quantityText()).isEqualTo("Qua cân thực tế");
        }
    }

    @Test void scannedOrBlankPdfReturnsAnExplicitManualReviewStateRatherThanAnEmptySuccess() throws Exception {
        byte[] bytes;
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) { pdf.addPage(new PDPage()); pdf.save(out); bytes = out.toByteArray(); }
        var parsed = parser.parse(new MockMultipartFile("file", "new-quote.pdf", "application/pdf", bytes), "QUOTATION", null);
        assertThat(parsed.format()).isEqualTo("PDF_SCAN");
        assertThat(parsed.lines()).isEmpty();
        assertThat(parsed.warnings()).anyMatch(issue -> issue.contains("OCR_NOT_SUPPORTED"));
    }

    @Test void malformedUtf8CannotReplaceSourceCharactersDuringCsvImport() {
        var file = new MockMultipartFile("file", "broken.csv", "text/csv", new byte[]{(byte) 0xc3, 0x28});
        assertThatThrownBy(() -> parser.parse(file, "REQUEST", null))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("FILE_READ_FAILED"));
    }

    @Test void csvLimitsRejectTooManyRowsOrColumnsBeforeBuildingTheWorkbook() {
        var rows = new MockMultipartFile("file", "rows.csv", "text/csv", "value\n".repeat(10001).getBytes(StandardCharsets.UTF_8));
        var columns = new MockMultipartFile("file", "columns.csv", "text/csv", String.join(",", Collections.nCopies(101, "column")).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse(rows, "REQUEST", null))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("TOO_MANY_SOURCE_ROWS"));
        assertThatThrownBy(() -> parser.parse(columns, "REQUEST", null))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("TOO_MANY_COLUMNS"));
    }

    @Test void anUnrecognizedCsvLayoutShowsAnExplicitWarningInsteadOfPretendingItFoundItems() {
        var file = new MockMultipartFile("file", "notes.csv", "text/csv", "Metadata,Comment\nA note,No item table".getBytes(StandardCharsets.UTF_8));
        var parsed = parser.parse(file, "REQUEST", null);
        assertThat(parsed.lines()).isEmpty();
        assertThat(parsed.warnings()).anyMatch(issue -> issue.contains("ITEM_HEADER_NOT_FOUND_OR_EMPTY"));
    }

    @Test void malformedAndOversizedFilesCannotBecomeSuccessfulPreviews() {
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", new byte[]{1,2,3}), "REQUEST", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("file", "huge.xlsx", "application/octet-stream", new byte[10 * 1024 * 1024 + 1]), "REQUEST", null)).isInstanceOf(ApiException.class);
    }

    @Test void pdfPageLimitFailsExplicitlyInsteadOfTruncatingToTheFirstPages() throws Exception {
        byte[] bytes;
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) { for (int i = 0; i < 31; i++) pdf.addPage(new PDPage()); pdf.save(out); bytes = out.toByteArray(); }
        assertThatThrownBy(() -> parser.parse(new MockMultipartFile("file", "31-pages.pdf", "application/pdf", bytes), "QUOTATION", null)).isInstanceOf(ApiException.class);
    }

    @Test void worksheetItemLimitDoesNotSilentlyDropRowsAtTheEnd() throws Exception {
        try (var book = requestWorkbook()) {
            var sheet = book.getSheetAt(0);
            for (int i = 0; i < 5001; i++) requestRow(sheet.createRow(i + 5), String.valueOf(i + 1), "Item " + i, "", "Cái", "1", "1");
            assertThatThrownBy(() -> parse(book, "large-request.xlsx", "REQUEST", null)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "QMH_DOCUMENT_SAMPLE_ROOT", matches = ".+")
    void actualCvtWorkbookMatchesIndependentOpenXmlAtEveryItemCoordinate() throws Exception {
        Path path = sample("Copy of CVT 2026 - SCTX.xlsx");
        var parsed = parser.parse(upload(path), "REQUEST", "2026");
        Map<Integer, Map<String, String>> xml = openXmlSheet(path, "2026");
        var expected = xml.entrySet().stream().filter(e -> e.getKey() >= 6 && !value(e.getValue(), "B").isBlank()).toList();
        assertThat(expected).hasSize(2013); assertThat(parsed.lines()).hasSize(expected.size());
        Map<Integer, DocumentLine> lines = uniqueCoordinates(parsed);
        for (var source : expected) {
            DocumentLine line = lines.remove(source.getKey()); assertThat(line).as("Source row %s", source.getKey()).isNotNull();
            assertThat(line.materialName()).isEqualTo(value(source.getValue(), "B"));
            assertThat(Objects.toString(line.specification(), "")).isEqualTo(value(source.getValue(), "C"));
            assertThat(Objects.toString(line.unit(), "")).isEqualTo(value(source.getValue(), "D"));
            assertDecimal(line.quantity(), value(source.getValue(), "E"));
            assertThat(Objects.toString(line.requestNumber(), "")).isEqualTo(value(source.getValue(), "O"));
        }
        assertThat(lines).isEmpty();
        assertThat(parsed.lines().stream().filter(l -> l.sourceRow() == 1416).findFirst().orElseThrow().requestNumber()).isNullOrEmpty();
        assertThat(parsed.lines().stream().map(DocumentLine::requestNumber).filter(Objects::nonNull).filter(n -> !n.isBlank()).distinct()).hasSize(40);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "QMH_DOCUMENT_SAMPLE_ROOT", matches = ".+")
    void bothActualQuotationSheetsMatchIndependentExactOpenXmlIncludingMixedVatAndAlternatives() throws Exception {
        Path path = sample("08.09.2026-Báo Giá Công ty Cổ phần Phân Bón và Hóa Chất Cần Thơ.xlsx");
        assertThat(parser.parse(upload(path), "QUOTATION", null).lines()).isEmpty();
        for (String sheet : List.of("BG", "Sheet1")) {
            var parsed = parser.parse(upload(path), "QUOTATION", sheet);
            var xml = openXmlSheet(path, sheet);
            int end = sheet.equals("BG") ? 52 : 27;
            var expected = xml.entrySet().stream().filter(e -> e.getKey() >= 10 && e.getKey() <= end && !value(e.getValue(), "C").isBlank()).toList();
            assertThat(expected).hasSize(sheet.equals("BG") ? 43 : 18); assertThat(parsed.lines()).hasSize(expected.size());
            assertThat(parsed.quotationDate()).isEqualTo(sheet.equals("BG") ? "2026-05-05" : "2026-09-08");
            var lines = uniqueCoordinates(parsed);
            for (var source : expected) {
                var cells = source.getValue(); var line = lines.remove(source.getKey()); assertThat(line).isNotNull();
                assertThat(line.materialName()).isEqualTo(value(cells, "C"));
                assertThat(Objects.toString(line.specification(), "")).isEqualTo(value(cells, sheet.equals("BG") ? "E" : "F"));
                assertDecimal(line.quantity(), value(cells, sheet.equals("BG") ? "G" : "H"));
                assertDecimal(line.unitPrice(), value(cells, sheet.equals("BG") ? "F" : "G"));
                assertDecimal(line.lineTotal(), value(cells, sheet.equals("BG") ? "H" : "I"));
                String vat = value(cells, sheet.equals("BG") ? "I" : "J");
                assertDecimal(line.vatPercent(), vat.isBlank() ? "" : new BigDecimal(vat).movePointRight(2).toPlainString());
                assertThat(line.materialCode()).isNullOrEmpty();
            }
            assertThat(lines).isEmpty();
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "QMH_DOCUMENT_SAMPLE_ROOT", matches = ".+")
    void actualTextPdfsPreserveItemCountsPrintedSttPricesAndWrappedProductDetails() throws Exception {
        var van = parser.parse(upload(sample("08.10.2026 BÁO GIÁ CÔNG TY PHÂN BÓN VÀ HÓA CHẤT CẦN THƠ - VẠN PHÚC.pdf")), "QUOTATION", null);
        assertThat(van.lines()).hasSize(8); assertThat(van.quotationDate()).isEqualTo("2026-10-08"); assertThat(van.vatPercent()).isEqualByComparingTo("8");
        assertThat(van.lines().getFirst().quantity()).isEqualByComparingTo("4000"); assertThat(van.lines().getFirst().unitPrice()).isEqualByComparingTo("350"); assertThat(van.lines().getFirst().lineTotal()).isEqualByComparingTo("1400000");
        assertThat(van.lines().getFirst().materialName() + " " + van.lines().getFirst().specification()).contains("M8");
        var aChau = parser.parse(upload(sample("BÁO GIÁ VẬT TƯ C.F.C - 02.10.2026.pdf")), "QUOTATION", null);
        assertThat(aChau.lines()).hasSize(31); assertThat(aChau.quotationDate()).isEqualTo("2026-10-02");
        assertThat(aChau.lines()).extracting(DocumentLine::sourceRow).doesNotContain(16, 28, 29).contains(34);
        assertThat(aChau.vatPercent()).isNull();
        var wrapped = aChau.lines().stream().filter(l -> l.sourceRow() == 30).findFirst().orElseThrow();
        assertThat(wrapped.materialName() + " " + wrapped.specification()).contains("GXSV 1004", "122K9321");
        assertThat(wrapped.unitPrice()).isEqualByComparingTo("5460000");
        var trung = parser.parse(upload(sample("C021_PB&HC CAN THO 07.10.26.pdf")), "QUOTATION", null);
        assertThat(trung.lines()).hasSize(11); assertThat(trung.quotationDate()).isEqualTo("2026-10-07"); assertThat(trung.vatPercent()).isEqualByComparingTo("8");
        var sourceMismatch = trung.lines().stream().filter(l -> l.sourceRow() == 9).findFirst().orElseThrow();
        assertThat(sourceMismatch.quantity()).isEqualByComparingTo("8000"); assertThat(sourceMismatch.unitPrice()).isEqualByComparingTo("12350"); assertThat(sourceMismatch.lineTotal()).isEqualByComparingTo("74400000");
        assertThat(sourceMismatch.quantity().multiply(sourceMismatch.unitPrice())).isNotEqualByComparingTo(sourceMismatch.lineTotal());
        assertThat(trung.lines().get(1).materialName()).contains("Tiền làm mới bản in");
        assertThat(trung.lines()).allMatch(l -> l.sourcePage() != null && l.sourcePage() > 0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "QMH_DOCUMENT_SAMPLE_ROOT", matches = ".+")
    void actualScannedQuoteIsExplicitlyMarkedUnsupportedRatherThanInventingItsSingleVisibleItem() throws Exception {
        var parsed = parser.parse(upload(sample("BAO GIA CTY HOA CHAT CAN THO - 081026.pdf")), "QUOTATION", null);
        assertThat(parsed.format()).isEqualTo("PDF_SCAN"); assertThat(parsed.lines()).isEmpty();
        assertThat(parsed.warnings()).anyMatch(issue -> issue.contains("OCR_NOT_SUPPORTED"));
    }

    private ParsedDocument parse(XSSFWorkbook book, String filename, String kind, String sheet) throws Exception {
        try (var out = new ByteArrayOutputStream()) { book.write(out); return parser.parse(new MockMultipartFile("file", filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()), kind, sheet); }
    }
    private XSSFWorkbook requestWorkbook() {
        var book = new XSSFWorkbook(); var sheet = book.createSheet("2026");
        sheet.createRow(0).createCell(0).setCellValue("PHIẾU CẤP VẬT TƯ PKT NĂM 2026");
        copyRequestHeaders(sheet); return book;
    }
    private void copyRequestHeaders(org.apache.poi.ss.usermodel.Sheet sheet) {
        String[] headers = {"STT", "TÊN VẬT TƯ", "QUI CÁCH", "ĐVT", "SỐ LƯỢNG", "MỤC ĐÍCH SỬ DỤNG", "Vị trí sử dụng", "", "CẤP THEO", "BP THI CÔNG", "KẾ HOẠCH", "NGÀY CẦN VT CHẬM NHẤT", "GHI CHÚ", "NGÀY CẤP", "SỐ PHIẾU"};
        var row = sheet.createRow(3); for (int col = 0; col < headers.length; col++) row.createCell(col).setCellValue(headers[col]);
    }
    private void requestRow(org.apache.poi.ss.usermodel.Row row, String stt, String name, String specification, String unit, String quantity, String request) {
        String[] values = {stt, name, specification, unit, quantity};
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
        row.createCell(14).setCellValue(request);
    }
    private static Path sample(String name) { return Path.of(System.getenv("QMH_DOCUMENT_SAMPLE_ROOT"), name); }
    private static MockMultipartFile upload(Path path) throws Exception { return new MockMultipartFile("file", "renamed" + (path.toString().endsWith(".pdf") ? ".pdf" : ".xlsx"), "application/octet-stream", Files.readAllBytes(path)); }
    private static void assertDecimal(BigDecimal actual, String expected) { if (expected.isBlank()) assertThat(actual).isNull(); else assertThat(actual).isEqualByComparingTo(new BigDecimal(expected)); }
    private static String value(Map<String,String> row, String col) { return row.getOrDefault(col, "").trim(); }
    private static Map<Integer,DocumentLine> uniqueCoordinates(ParsedDocument parsed) {
        Map<Integer,DocumentLine> out = new LinkedHashMap<>();
        for (var line : parsed.lines()) assertThat(out.put(line.sourceRow(), line)).as("No duplicate source row").isNull();
        return out;
    }

    // Independent ZIP/XML extraction: no POI DataFormatter and no production parser helpers.
    private static Map<Integer,Map<String,String>> openXmlSheet(Path path, String name) throws Exception {
        try (var zip = new ZipFile(path.toFile())) {
            var shared = new ArrayList<String>();
            var strings = xml(zip, "xl/sharedStrings.xml");
            if (strings != null) { var nodes = strings.getElementsByTagName("si"); for (int i = 0; i < nodes.getLength(); i++) shared.add(text((Element) nodes.item(i))); }
            String relId = null;
            var sheets = xml(zip, "xl/workbook.xml").getElementsByTagName("sheet");
            for (int i = 0; i < sheets.getLength(); i++) { var s = (Element) sheets.item(i); if (s.getAttribute("name").equals(name)) relId = s.getAttribute("r:id"); }
            assertThat(relId).isNotNull(); String target = null;
            var rels = xml(zip, "xl/_rels/workbook.xml.rels").getElementsByTagName("Relationship");
            for (int i = 0; i < rels.getLength(); i++) { var r = (Element) rels.item(i); if (r.getAttribute("Id").equals(relId)) target = r.getAttribute("Target"); }
            assertThat(target).isNotNull(); if (target.startsWith("/")) target = target.substring(1); else target = "xl/" + target;
            Map<Integer,Map<String,String>> rows = new LinkedHashMap<>();
            var xmlRows = xml(zip, target).getElementsByTagName("row");
            for (int i = 0; i < xmlRows.getLength(); i++) {
                var row = (Element) xmlRows.item(i); Map<String,String> cells = new HashMap<>();
                var cn = row.getElementsByTagName("c");
                for (int j = 0; j < cn.getLength(); j++) { var cell = (Element) cn.item(j); String type = cell.getAttribute("t"), value = child(cell, "v");
                    if (type.equals("s")) value = shared.get(Integer.parseInt(value)); else if (type.equals("inlineStr")) value = text(cell);
                    cells.put(cell.getAttribute("r").replaceAll("\\d+$", ""), value);
                }
                rows.put(Integer.parseInt(row.getAttribute("r")), cells);
            }
            return rows;
        }
    }
    private static org.w3c.dom.Document xml(ZipFile zip, String path) throws Exception {
        var entry = zip.getEntry(path); if (entry == null) return null;
        var factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var input = zip.getInputStream(entry)) { return factory.newDocumentBuilder().parse(input); }
    }
    private static String child(Element element, String tag) { NodeList nodes = element.getElementsByTagName(tag); return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent(); }
    private static String text(Element element) { StringBuilder out = new StringBuilder(); NodeList nodes = element.getElementsByTagName("t"); for (int i = 0; i < nodes.getLength(); i++) out.append(nodes.item(i).getTextContent()); return out.toString(); }
}
