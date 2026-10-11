package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.quanlymuahang.importing.application.SourceDocumentModels.*;

/** Bounded, deterministic extraction. Never evaluates formulas, invokes OCR, or writes business data. */
@Service
public class SourceDocumentParser {
    private static final int MAX_LINES = 5000, MAX_SCAN_ROWS = 10000, MAX_SHEETS = 20, MAX_COLUMNS = 100, MAX_PAGES = 30;
    private static final String VERSION = "source-v1";
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](20\\d{2})(?!\\d)");
    private static final Pattern WORD_DATE = Pattern.compile("ngay\\s*(\\d{1,2})\\s*thang\\s*(\\d{1,2})\\s*nam\\s*(20\\d{2})");

    public ParsedDocument parse(MultipartFile file, String kind, String sheetName) {
        kind = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        if (!Set.of("REQUEST", "QUOTATION").contains(kind)) throw ApiException.badRequest("INVALID_DOCUMENT_KIND", "Chọn yêu cầu mua hoặc báo giá NCC");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("FILE_REQUIRED", "Chọn chứng từ cần nhập");
        if (file.getSize() > 10L * 1024 * 1024) throw ApiException.badRequest("FILE_TOO_LARGE", "Chứng từ không được vượt quá 10 MB");
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String extension = name.toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (extension.endsWith(".xlsx") || extension.endsWith(".xls")) return spreadsheet(name, bytes, kind, sheetName);
            if (extension.endsWith(".pdf")) return pdf(name, bytes, kind);
            if (extension.endsWith(".csv") || extension.endsWith(".tsv") || extension.endsWith(".txt")) return delimited(name, bytes, kind);
            throw ApiException.badRequest("UNSUPPORTED_FILE", "Hỗ trợ Excel, CSV/TSV và PDF; PDF scan cần nhập tay");
        } catch (ApiException exception) { throw exception; }
        catch (Exception exception) { throw ApiException.badRequest("FILE_READ_FAILED", "Không đọc được chứng từ; kiểm tra định dạng và tệp gốc"); }
    }

    private ParsedDocument spreadsheet(String name, byte[] bytes, String kind, String selected) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() > MAX_SHEETS) throw ApiException.badRequest("TOO_MANY_SHEETS", "Tối đa 20 sheet cho một chứng từ");
            Set<String> sheetNames = new HashSet<>();
            for (Sheet sheet : workbook) sheetNames.add(sheet.getSheetName().toUpperCase(Locale.ROOT));
            if (sheetNames.containsAll(Set.of("NCC", "LICH_SU", "DON_HANG")))
                throw ApiException.badRequest("LEGACY_WORKBOOK_DETECTED", "Đây là dữ liệu Apps Script cũ; dùng luồng di trú lịch sử, không nhập như PCVT hoặc báo giá mới");
            List<SheetData> results = new ArrayList<>();
            int detectedLines = 0;
            for (Sheet sheet : workbook) {
                if (sheet.getSheetName().equalsIgnoreCase("CONFIG")) continue;
                SheetData data = readSheet(sheet, kind);
                detectedLines += data.lines.size();
                if (detectedLines > MAX_LINES)
                    throw ApiException.badRequest("TOO_MANY_DOCUMENT_LINES", "Workbook vượt quá 5.000 dòng vật tư; tách chứng từ trước khi nhập");
                results.add(data);
            }
            if (results.isEmpty()) throw ApiException.badRequest("EMPTY_WORKBOOK", "Không có sheet nghiệp vụ để đọc");
            List<SheetInfo> sheets = results.stream().map(s -> new SheetInfo(s.name, s.lines.size(),
                    s.lines.stream().map(DocumentLine::requestNumber).filter(Objects::nonNull).distinct().toList(), s.meta.date)).toList();
            if ((selected == null || selected.isBlank()) && results.size() > 1)
                return document(kind, name, name.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? "XLSX" : "XLS", sheets, List.of(), new Meta(), List.of("SHEET_SELECTION_REQUIRED"));
            SheetData chosen = selected == null || selected.isBlank() ? results.getFirst() : results.stream().filter(s -> s.name.equals(selected)).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("SHEET_NOT_FOUND", "Sheet đã chọn không có trong workbook"));
            List<String> warnings = new ArrayList<>(chosen.warnings);
            if (chosen.lines.isEmpty()) warnings.add("ITEM_HEADER_NOT_FOUND_OR_EMPTY");
            return document(kind, name, name.toLowerCase(Locale.ROOT).endsWith(".xlsx") ? "XLSX" : "XLS", sheets, chosen.lines, chosen.meta, warnings);
        }
    }

    private SheetData readSheet(Sheet sheet, String kind) {
        if (sheet.getLastRowNum() >= MAX_SCAN_ROWS) throw ApiException.badRequest("TOO_MANY_SOURCE_ROWS", "Sheet vượt quá 10.000 vị trí dòng; tách chứng từ trước khi nhập");
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        formatter.setUseCachedValuesForFormulaCells(true);
        int header = -1;
        Map<String, Integer> columns = new LinkedHashMap<>();
        List<String> introductory = new ArrayList<>();
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 49); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            checkColumns(row);
            Map<String, Integer> candidate = new LinkedHashMap<>();
            for (Cell cell : row) {
                String text = display(cell, formatter);
                String field = headerField(text);
                if (field != null) candidate.putIfAbsent(field, cell.getColumnIndex());
                if (r < 8 && !text.isBlank()) introductory.add(text);
            }
            if (candidate.containsKey("name")) { header = r; columns = candidate; break; }
        }
        Meta meta = metadata(introductory, kind);
        SheetData result = new SheetData(sheet.getSheetName(), meta);
        if (header < 0) return result;
        String unitHeader = headerCell(sheet, header, columns, "price", formatter);
        meta.priceBasis = basis(unitHeader);
        if ("REQUEST".equals(kind)) meta.priceBasis = "UNKNOWN";
        String totalBasis = basis(headerCell(sheet, header, columns, "total", formatter));
        boolean totalIncludesVat = "INCL_VAT".equals(totalBasis) && "EXCL_VAT".equals(meta.priceBasis);
        // Subsequent header-only rows are recognized by missing material names, never filled into data.
        for (int r = header + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            checkColumns(row);
            String itemName = value(row, columns, "name", formatter);
            if (itemName == null || isFooter(itemName) || headerField(itemName) != null) continue;
            List<String> issues = new ArrayList<>();
            Map<String, String> raw = rawCells(row, formatter, issues);
            String spec = value(row, columns, "spec", formatter);
            String unit = value(row, columns, "unit", formatter);
            if (unit == null && spec != null) {
                unit = packingUnit(spec);
                if (unit != null) {
                    raw.put("__unitBasis", "PACKAGING_REQUIRES_CONFIRMATION");
                    issues.add("UNIT_FROM_PACKAGING_REQUIRES_CONFIRMATION");
                }
            }
            String requestNumber = value(row, columns, "request", formatter);
            if (columns.containsKey("request") && requestNumber == null) issues.add("REQUEST_NUMBER_MISSING");
            BigDecimal quantity = cellNumber(cell(row, columns, "quantity"), formatter);
            String quantityText = quantity == null ? value(row, columns, "quantity", formatter) : null;
            BigDecimal price = cellNumber(cell(row, columns, "price"), formatter);
            BigDecimal total = cellNumber(cell(row, columns, "total"), formatter);
            BigDecimal vat = cellNumber(cell(row, columns, "vat"), formatter);
            for (String field : List.of("quantity", "price", "total", "vat")) {
                Cell numericCell = cell(row, columns, field);
                if (numericCell == null) continue;
                CellType numericType = numericCell.getCellType() == CellType.FORMULA ? numericCell.getCachedFormulaResultType() : numericCell.getCellType();
                if (numericType != CellType.NUMERIC && ambiguousNumber(display(numericCell, formatter)))
                    issues.add("NUMERIC_SEPARATOR_REQUIRES_CONFIRMATION");
            }
            if (vat != null && vat.signum() >= 0 && vat.compareTo(BigDecimal.ONE) <= 0) vat = vat.multiply(BigDecimal.valueOf(100));
            if (!columns.containsKey("vat")) vat = meta.vat;
            String currency = value(row, columns, "currency", formatter);
            if (currency == null) currency = meta.currency;
            else currency = currency(currency);
            raw.put("__lineTotalBasis", totalBasis);
            if (value(row, columns, "manufacturerCode", formatter) != null) issues.add("MANUFACTURER_CODE_NOT_INTERNAL_CODE");
            review(issues, kind, unit, quantity, quantityText, price, vat, currency, meta.priceBasis);
            if (!"UNKNOWN".equals(totalBasis) && !"UNKNOWN".equals(meta.priceBasis))
                arithmetic(issues, quantity, price, total, vat, totalIncludesVat);
            result.lines.add(new DocumentLine(sheet.getSheetName() + ":" + (r + 1), sheet.getSheetName(), r + 1, null,
                    requestNumber, value(row, columns, "code", formatter), itemName, spec, unit, quantity, quantityText,
                    price, total, vat, meta.priceBasis, currency, value(row, columns, "note", formatter), raw, distinct(issues)));
            if (result.lines.size() > MAX_LINES) throw ApiException.badRequest("TOO_MANY_DOCUMENT_LINES", "Tối đa 5.000 dòng; hệ thống không tự cắt bớt");
        }
        List<BigDecimal> vats = result.lines.stream().map(DocumentLine::vatPercent).filter(Objects::nonNull).map(BigDecimal::stripTrailingZeros).distinct().toList();
        if (vats.size() == 1 && result.lines.stream().allMatch(line -> line.vatPercent() != null)) meta.vat = vats.getFirst();
        else if (vats.size() == 1) { meta.vat = null; result.warnings.add("PARTIAL_LINE_VAT_REQUIRES_CONFIRMATION"); }
        else if (vats.size() > 1) { meta.vat = null; result.warnings.add("MIXED_LINE_VAT"); }
        if ("QUOTATION".equals(kind)) {
            if (meta.currency == null) result.warnings.add("CURRENCY_UNKNOWN");
            if (meta.vat == null && vats.isEmpty()) result.warnings.add("VAT_UNKNOWN");
        }
        return result;
    }

    private ParsedDocument delimited(String name, byte[] bytes, String kind) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString().replaceFirst("^\\uFEFF", "");
        List<List<String>> rows = csvRows(text);
        if (rows.size() > MAX_SCAN_ROWS) throw ApiException.badRequest("TOO_MANY_SOURCE_ROWS", "Chứng từ vượt quá 10.000 dòng");
        // A small in-memory workbook uses the same mapping/validation without treating numeric text as a double.
        try (Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CSV");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                if (rows.get(r).size() > MAX_COLUMNS) throw ApiException.badRequest("TOO_MANY_COLUMNS", "Tối đa 100 cột");
                for (int c = 0; c < rows.get(r).size(); c++) row.createCell(c).setCellValue(rows.get(r).get(c));
            }
            SheetData data = readSheet(sheet, kind);
            if (data.lines.isEmpty()) data.warnings.add("ITEM_HEADER_NOT_FOUND_OR_EMPTY");
            return document(kind, name, "CSV", List.of(new SheetInfo("CSV", data.lines.size(), data.lines.stream().map(DocumentLine::requestNumber).filter(Objects::nonNull).distinct().toList(), data.meta.date)), data.lines, data.meta, data.warnings);
        }
    }

    private ParsedDocument pdf(String name, byte[] bytes, String kind) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PAGES) throw ApiException.badRequest("TOO_MANY_PAGES", "PDF tối đa 30 trang; tách chứng từ trước khi nhập");
            PositionedText extractor = new PositionedText();
            String allText = extractor.getText(document);
            if (allText == null || allText.isBlank()) return document(kind, name, "PDF_SCAN", List.of(), List.of(), new Meta(), List.of("OCR_NOT_SUPPORTED"));
            Meta meta = metadata(extractor.chunks, kind);
            Meta fullTextMeta = metadata(allText.lines().toList(), kind);
            if (meta.date == null) meta.date = fullTextMeta.date;
            if (meta.currency == null) meta.currency = fullTextMeta.currency;
            if (meta.vat == null) meta.vat = fullTextMeta.vat;
            if (meta.validity == null) meta.validity = fullTextMeta.validity;
            if ("UNKNOWN".equals(meta.priceBasis)) meta.priceBasis = fullTextMeta.priceBasis;
            List<String> warnings = new ArrayList<>();
            List<DocumentLine> lines = new ArrayList<>();
            List<Double> boundaries = List.of();
            Map<String, Integer> columnMap = Map.of();
            boolean minimumQuantity = false;
            for (int p = 0; p < document.getNumberOfPages(); p++) {
                PDPage page = document.getPage(p);
                List<TextRow> textRows = rows(extractor.pages.getOrDefault(p + 1, List.of()));
                Rules rules = new Rules(page); rules.processPage(page);
                TextRow heading = textRows.stream().filter(r -> norm(r.text()).startsWith("stt")).findFirst().orElse(null);
                double minY = 0;
                if (heading != null) {
                    double serialX = heading.glyphs.getFirst().x;
                    TextRow first = textRows.stream().filter(r -> r.y > heading.y + 12 && r.text(serialX - 8, serialX + 25).matches("\\d{1,4}")).findFirst().orElse(null);
                    double firstY = first == null ? heading.y + 35 : first.y;
                    final double hx = serialX;
                    boundaries = mergeCoordinates(rules.vertical.stream().filter(v -> v.x >= hx - 25 && (v.covers(heading.y) || v.covers(firstY))).map(v -> v.x).toList());
                    if (boundaries.size() >= 4) {
                        Map<String, Integer> map = new LinkedHashMap<>();
                        for (int c = 0; c < boundaries.size() - 1; c++) {
                            String header = textRegion(textRows, boundaries.get(c), boundaries.get(c + 1), heading.y - 18, heading.y + 14);
                            String field = headerField(header);
                            if (field != null) map.putIfAbsent(field, c);
                            if (norm(header).contains("toithieu")) minimumQuantity = true;
                        }
                        columnMap = map;
                    }
                    minY = heading.y + 14;
                }
                if (!columnMap.containsKey("name") || !columnMap.containsKey("serial") || boundaries.size() < 4) continue;
                final List<Double> bs = boundaries;
                int serialColumn = columnMap.get("serial");
                double serialLeft = bs.get(serialColumn), serialRight = bs.get(serialColumn + 1);
                double tableStart = minY;
                List<TextRow> numbered = textRows.stream().filter(r -> r.y > tableStart && r.text(serialLeft, serialRight).matches("\\d{1,4}")).toList();
                for (int n = 0; n < numbered.size(); n++) {
                    TextRow anchor = numbered.get(n);
                    int serial = Integer.parseInt(anchor.text(serialLeft, serialRight));
                    List<Double> horizontal = mergeCoordinates(rules.horizontal.stream().filter(h -> h.left <= serialLeft + 3 && h.right >= serialRight - 3).map(h -> h.y).toList());
                    Double top = horizontal.stream().filter(y -> y < anchor.y - 1).max(Double::compare).orElse(null);
                    Double bottom = horizontal.stream().filter(y -> y > anchor.y + 1).min(Double::compare).orElse(null);
                    List<String> issues = new ArrayList<>();
                    if (top == null || bottom == null) {
                        issues.add("POSITIONAL_REVIEW_REQUIRED");
                        top = n == 0 ? tableStart : (numbered.get(n - 1).y + anchor.y) / 2;
                        bottom = n + 1 == numbered.size() ? anchor.y + 20 : (anchor.y + numbered.get(n + 1).y) / 2;
                    }
                    Map<String, String> values = new LinkedHashMap<>(), raw = new LinkedHashMap<>();
                    for (int c = 0; c < bs.size() - 1; c++) {
                        String text = clean(textRegion(textRows, bs.get(c), bs.get(c + 1), top + .1, bottom - .1));
                        if (text != null) raw.put("page:" + (p + 1) + ":row:" + serial + ":column:" + (c + 1), text);
                        for (var entry : columnMap.entrySet()) if (entry.getValue() == c) values.put(entry.getKey(), text);
                    }
                    raw.put("__stt", Integer.toString(serial));
                    raw.put("__bounds", "x=" + bs.getFirst() + ":" + bs.getLast() + ",y=" + top + ":" + bottom);
                    String itemName = values.get("name");
                    if (itemName == null) { itemName = "Dòng " + serial + " chưa đọc được tên"; issues.add("MATERIAL_NAME_UNREADABLE"); }
                    String spec = values.get("spec");
                    if (values.get("manufacturerCode") != null) { spec = join(spec, values.get("manufacturerCode")); issues.add("MANUFACTURER_CODE_NOT_INTERNAL_CODE"); }
                    BigDecimal quantity = number(values.get("quantity"));
                    String quantityText = quantity == null ? values.get("quantity") : null;
                    BigDecimal price = number(values.get("price")), total = number(values.get("total"));
                    if (List.of("quantity", "price", "total").stream().anyMatch(field -> ambiguousNumber(values.get(field))))
                        issues.add("NUMERIC_SEPARATOR_REQUIRES_CONFIRMATION");
                    if (minimumQuantity) { raw.put("__quantityBasis", "MINIMUM"); issues.add("MINIMUM_QUANTITY_NOT_REQUESTED_QUANTITY"); }
                    if (norm(itemName).startsWith("tien")) issues.add("ONE_TIME_COST");
                    review(issues, kind, values.get("unit"), quantity, quantityText, price, meta.vat, meta.currency, meta.priceBasis);
                    arithmetic(issues, quantity, price, total, meta.vat, false);
                    lines.add(new DocumentLine("page:" + (p + 1) + ":" + serial, null, serial, p + 1, null, values.get("code"), itemName,
                            spec, values.get("unit"), quantity, quantityText, price, total, meta.vat, meta.priceBasis, meta.currency, values.get("note"), raw, distinct(issues)));
                    if (lines.size() > MAX_LINES) throw ApiException.badRequest("TOO_MANY_DOCUMENT_LINES", "PDF vượt quá 5.000 dòng; hệ thống không tự cắt bớt");
                }
            }
            if (lines.isEmpty()) warnings.add("PDF_TABLE_NOT_RECOGNIZED");
            if ("QUOTATION".equals(kind)) {
                if (meta.currency == null) warnings.add("CURRENCY_UNKNOWN");
                if (meta.vat == null) warnings.add("VAT_UNKNOWN");
                if ("UNKNOWN".equals(meta.priceBasis)) warnings.add("PRICE_BASIS_UNKNOWN");
            }
            if (meta.validity != null && meta.date != null) {
                String date = date(meta.validity);
                if (date != null && !date.equals(meta.date)) warnings.add("QUOTATION_VALIDITY_DATE_DIFFERS");
            }
            return document(kind, name, "PDF_TEXT", List.of(), lines, meta, warnings);
        }
    }

    private static ParsedDocument document(String kind, String name, String format, List<SheetInfo> sheets, List<DocumentLine> lines, Meta meta, List<String> warnings) {
        return new ParsedDocument(kind, name, format, sheets, lines, meta.supplier, meta.date, meta.currency, meta.vat, meta.priceBasis, meta.validity, distinct(warnings), VERSION);
    }

    private static String headerField(String text) {
        String n = norm(text);
        if (n.equals("stt")) return "serial";
        if (n.startsWith("tenhang") || n.startsWith("tenvattu") || n.startsWith("tensanpham") || n.equals("diengiai") || n.equals("hanghoavattu")) return "name";
        if (n.equals("mavattu") || n.equals("mahang") || n.equals("mavt")) return "code";
        if (n.equals("code") || n.equals("mauin")) return "manufacturerCode";
        if (n.startsWith("quycach") || n.startsWith("quicach") || n.equals("vatlieu") || n.equals("mota")) return "spec";
        if (n.equals("dvt") || n.equals("donvi") || n.equals("donvitinh")) return "unit";
        if (n.startsWith("soluong") || n.equals("sl")) return "quantity";
        if (n.startsWith("dongia")) return "price";
        if (n.startsWith("thanhtien")) return "total";
        if (n.equals("vat") || n.equals("thuesuat") || n.equals("vat%")) return "vat";
        if (n.startsWith("ghichu")) return "note";
        if (n.equals("sophieu") || n.equals("sopcvt") || n.equals("pcvt")) return "request";
        if (n.equals("tiente") || n.equals("loaitien") || n.equals("currency")) return "currency";
        return null;
    }

    private static Meta metadata(List<String> text, String kind) {
        Meta meta = new Meta();
        for (String line : text) {
            String simple = spacedNorm(line), n = norm(line);
            if (meta.supplier == null && (simple.startsWith("cong ty ") || simple.startsWith("cty ")) && !simple.contains("kinh gui")) meta.supplier = clean(line);
            if (meta.date == null && (simple.contains("ngay") || simple.contains("date"))) meta.date = date(line);
            if (meta.currency == null) meta.currency = currency(line);
            if (meta.validity == null && (n.contains("hieuluc") || n.contains("cogiatri") || n.contains("giatritrong"))) meta.validity = clean(line);
            if ("UNKNOWN".equals(meta.priceBasis)) meta.priceBasis = basis(line);
            if (meta.vat == null && n.contains("vat")) {
                Matcher matcher = Pattern.compile("(?i)VAT.{0,100}?(\\d{1,2})\\s*%").matcher(line);
                if (matcher.find()) meta.vat = new BigDecimal(matcher.group(1));
            }
        }
        if ("REQUEST".equals(kind)) { meta.supplier = null; meta.currency = null; meta.priceBasis = "UNKNOWN"; meta.vat = null; }
        return meta;
    }

    private static void review(List<String> issues, String kind, String unit, BigDecimal quantity, String quantityText, BigDecimal price, BigDecimal vat, String currency, String basis) {
        if (unit == null) issues.add("UNIT_MISSING");
        if (quantity == null && quantityText == null) issues.add("QUANTITY_MISSING");
        else if (quantityText != null) issues.add("QUANTITY_TEXT_REQUIRES_CONFIRMATION");
        else if (quantity.signum() <= 0) issues.add("QUANTITY_NON_POSITIVE");
        if ("QUOTATION".equals(kind)) {
            if (price == null) issues.add("UNIT_PRICE_MISSING_OR_UNREADABLE");
            else if (price.signum() <= 0) issues.add("UNIT_PRICE_NON_POSITIVE");
            if (vat == null) issues.add("VAT_UNKNOWN");
            if (currency == null) issues.add("CURRENCY_UNKNOWN");
            if ("UNKNOWN".equals(basis)) issues.add("PRICE_BASIS_UNKNOWN");
        }
    }

    private static void arithmetic(List<String> issues, BigDecimal quantity, BigDecimal price, BigDecimal total, BigDecimal vat, boolean includesVat) {
        if (quantity == null || price == null || total == null || (includesVat && vat == null)) return;
        BigDecimal expected = quantity.multiply(price);
        if (includesVat) expected = expected.multiply(BigDecimal.ONE.add(vat.movePointLeft(2)));
        if (expected.subtract(total).abs().compareTo(new BigDecimal("0.01")) > 0) issues.add("LINE_TOTAL_MISMATCH_REVIEW_SOURCE");
    }

    private static Map<String, String> rawCells(Row row, DataFormatter formatter, List<String> issues) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (Cell cell : row) {
            String address = cell.getAddress().formatAsString();
            String value = display(cell, formatter);
            if (!value.isBlank()) raw.put(address, value);
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) { raw.put(address + ".formula", cell.getCellFormula()); issues.add("CACHED_FORMULA_NOT_RECALCULATED"); type = cell.getCachedFormulaResultType(); }
            if (type == CellType.NUMERIC) {
                String numeric = cell instanceof XSSFCell x ? x.getRawValue() : BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                if (numeric != null) raw.put(address + ".numeric", numeric);
            }
            if (type == CellType.ERROR) { issues.add("EXCEL_CELL_ERROR"); raw.put(address + ".error", Byte.toString(cell.getErrorCellValue())); }
        }
        return raw;
    }

    private static String display(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        try { return formatter.formatCellValue(cell).trim(); } catch (RuntimeException exception) { return "#UNREADABLE_CELL"; }
    }

    private static BigDecimal cellNumber(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            String value = cell instanceof XSSFCell x ? x.getRawValue() : BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            try { return value == null ? null : new BigDecimal(value); } catch (NumberFormatException exception) { return null; }
        }
        return number(display(cell, formatter));
    }

    private static BigDecimal number(String text) {
        if (text == null || text.isBlank()) return null;
        String value = text.trim().replace("\u00a0", "").replace(" ", "").replace("%", "");
        if (value.matches("[+-]?[1-9]\\d{0,2}(?:[.,]\\d{3})+")) value = value.replace(".", "").replace(",", "");
        else if (value.contains(",") && value.contains(".")) {
            if (value.lastIndexOf(',') > value.lastIndexOf('.')) value = value.replace(".", "").replace(',', '.');
            else value = value.replace(",", "");
        } else if (value.contains(",")) value = value.replace(',', '.');
        try { return new BigDecimal(value); } catch (NumberFormatException exception) { return null; }
    }

    private static boolean ambiguousNumber(String text) {
        return text != null && text.trim().replace("\u00a0", "").replace(" ", "")
                .matches("[+-]?[1-9]\\d{0,2}[.,]\\d{3}");
    }

    private static String value(Row row, Map<String, Integer> columns, String field, DataFormatter formatter) { return clean(display(cell(row, columns, field), formatter)); }
    private static Cell cell(Row row, Map<String, Integer> columns, String field) { Integer c = columns.get(field); return c == null ? null : row.getCell(c); }
    private static String headerCell(Sheet sheet, int r, Map<String, Integer> columns, String field, DataFormatter formatter) { return value(sheet.getRow(r), columns, field, formatter); }
    private static void checkColumns(Row row) { if (row.getLastCellNum() > MAX_COLUMNS) throw ApiException.badRequest("TOO_MANY_COLUMNS", "Tối đa 100 cột trong một chứng từ"); }
    private static String packingUnit(String spec) {
        String value = spec.contains("/") ? spec.substring(spec.lastIndexOf('/') + 1).trim() : spec.trim();
        return Set.of("can", "chai", "ong", "cai", "hop", "goi", "lo", "bao", "kg", "lit", "m", "bo", "cuon", "tam").contains(norm(value)) ? value : null;
    }
    private static boolean isFooter(String text) { String n = norm(text); return n.startsWith("tongcong") || n.startsWith("thanhtien") || n.startsWith("ghichu") || n.startsWith("dieukhoan") || n.startsWith("tongtien"); }
    private static String basis(String text) { String n = norm(text); if (n.contains("chuavat") || n.contains("truocthue") || n.contains("chuabaogomthue")) return "EXCL_VAT"; if (n.contains("davat") || n.contains("dabaogomthue") || n.contains("sauthue")) return "INCL_VAT"; return "UNKNOWN"; }
    private static String currency(String text) { String n = spacedNorm(text); if (n.matches(".*\\b(vnd|vndong)\\b.*") || n.equals("dong") || text != null && text.contains("₫")) return "VND"; if (n.matches(".*\\busd\\b.*")) return "USD"; if (n.matches(".*\\beur\\b.*")) return "EUR"; return null; }
    private static String date(String text) {
        if (text == null) return null;
        Matcher word = WORD_DATE.matcher(spacedNorm(text));
        Matcher numeric = DATE.matcher(text);
        Matcher match = word.find() ? word : numeric.find() ? numeric : null;
        if (match == null) return null;
        try { return LocalDate.of(Integer.parseInt(match.group(3)), Integer.parseInt(match.group(2)), Integer.parseInt(match.group(1))).toString(); } catch (RuntimeException exception) { return null; }
    }
    private static String norm(String text) { return spacedNorm(text).replaceAll("[\\s_\\-/().]", ""); }
    private static String spacedNorm(String text) { return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    private static String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static String join(String a, String b) { return a == null ? b : b == null ? a : a + "\n" + b; }
    private static List<String> distinct(List<String> values) { return List.copyOf(new LinkedHashSet<>(values)); }

    private static List<List<String>> csvRows(String text) {
        String first = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        char delimiter = first.contains("\t") ? '\t' : first.chars().filter(c -> c == ';').count() > first.chars().filter(c -> c == ',').count() ? ';' : ',';
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quote = false, closedQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quote && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append(c); i++; }
                else if (quote) { quote = false; closedQuote = true; }
                else if (cell.isEmpty() && !closedQuote) quote = true;
                else throw ApiException.badRequest("INVALID_CSV", "CSV có dấu ngoặc kép không hợp lệ; lưu lại bằng định dạng CSV UTF-8");
            }
            else if (!quote && c == delimiter) {
                if (row.size() >= MAX_COLUMNS - 1) throw ApiException.badRequest("TOO_MANY_COLUMNS", "Tối đa 100 cột");
                row.add(cell.toString()); cell.setLength(0); closedQuote = false;
            }
            else if (!quote && (c == '\r' || c == '\n')) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                if (rows.size() >= MAX_SCAN_ROWS) throw ApiException.badRequest("TOO_MANY_SOURCE_ROWS", "Chứng từ vượt quá 10.000 dòng");
                row.add(cell.toString()); cell.setLength(0); rows.add(List.copyOf(row)); row.clear(); closedQuote = false;
            }
            else if (closedQuote && !quote) {
                if (!Character.isWhitespace(c)) throw ApiException.badRequest("INVALID_CSV", "CSV có dữ liệu sau dấu ngoặc kép đã đóng");
            }
            else cell.append(c);
        }
        if (quote) throw ApiException.badRequest("INVALID_CSV", "CSV có dấu ngoặc kép chưa đóng");
        if (!row.isEmpty() || !cell.isEmpty()) {
            if (rows.size() >= MAX_SCAN_ROWS) throw ApiException.badRequest("TOO_MANY_SOURCE_ROWS", "Chứng từ vượt quá 10.000 dòng");
            row.add(cell.toString()); rows.add(List.copyOf(row));
        }
        return rows;
    }

    private static List<TextRow> rows(List<Glyph> glyphs) {
        List<Glyph> sorted = glyphs.stream().sorted(Comparator.comparingDouble((Glyph g) -> g.y).thenComparingDouble(g -> g.x)).toList();
        List<TextRow> result = new ArrayList<>();
        for (Glyph glyph : sorted) {
            TextRow row = result.isEmpty() ? null : result.getLast();
            if (row == null || Math.abs(row.y - glyph.y) > 1.2) { row = new TextRow(glyph.y); result.add(row); }
            row.glyphs.add(glyph);
        }
        result.forEach(r -> r.glyphs.sort(Comparator.comparingDouble(g -> g.x)));
        return result;
    }
    private static String textRegion(List<TextRow> rows, double left, double right, double top, double bottom) {
        return rows.stream().filter(r -> r.y >= top && r.y <= bottom).map(r -> r.text(left, right)).filter(s -> !s.isBlank()).reduce((a, b) -> a + "\n" + b).orElse("");
    }
    private static List<Double> mergeCoordinates(List<Double> values) {
        List<Double> result = new ArrayList<>();
        for (Double value : values.stream().sorted().toList()) {
            if (result.isEmpty() || value - result.getLast() > 2.1) result.add(value);
            else result.set(result.size() - 1, (result.getLast() + value) / 2);
        }
        return result;
    }

    private static final class Meta { String supplier, date, currency, validity; BigDecimal vat; String priceBasis = "UNKNOWN"; }
    private static final class SheetData { final String name; final Meta meta; final List<DocumentLine> lines = new ArrayList<>(); final List<String> warnings = new ArrayList<>(); SheetData(String name, Meta meta) { this.name = name; this.meta = meta; } }
    private record Glyph(double x, double y, double width, String text) {}
    private static final class TextRow {
        final double y; final List<Glyph> glyphs = new ArrayList<>(); TextRow(double y) { this.y = y; }
        String text() { return text(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY); }
        String text(double left, double right) {
            StringBuilder result = new StringBuilder(); Glyph previous = null;
            for (Glyph glyph : glyphs) {
                if (glyph.x + glyph.width / 2 < left || glyph.x + glyph.width / 2 >= right) continue;
                if (previous != null && glyph.x - (previous.x + previous.width) > Math.max(1.2, glyph.width * .4)) result.append(' ');
                result.append(glyph.text); previous = glyph;
            }
            return result.toString().replaceAll("\\s+", " ").trim();
        }
    }
    private static final class PositionedText extends PDFTextStripper {
        final Map<Integer, List<Glyph>> pages = new LinkedHashMap<>(); final List<String> chunks = new ArrayList<>(); int page;
        PositionedText() throws IOException { setSortByPosition(true); }
        @Override protected void startPage(PDPage page) { this.page++; }
        @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
            chunks.add(text.replaceAll("\\s+", " ").trim());
            List<Glyph> glyphs = pages.computeIfAbsent(page, ignored -> new ArrayList<>());
            for (TextPosition position : positions) glyphs.add(new Glyph(position.getXDirAdj(), position.getYDirAdj(), position.getWidthDirAdj(), position.getUnicode()));
            super.writeString(text, positions);
        }
    }
    private record Vertical(double x, double top, double bottom) { boolean covers(double y) { return y >= top - 2 && y <= bottom + 2; } }
    private record Horizontal(double y, double left, double right) {}
    private static final class Rules extends PDFGraphicsStreamEngine {
        final List<Vertical> vertical = new ArrayList<>(); final List<Horizontal> horizontal = new ArrayList<>(); Point2D current, start;
        Rules(PDPage page) { super(page); }
        private void edge(Point2D a, Point2D b) {
            if (a == null || b == null) return;
            double height = getPage().getMediaBox().getHeight();
            if (Math.abs(a.getX() - b.getX()) < .8 && Math.abs(a.getY() - b.getY()) > 5)
                vertical.add(new Vertical(a.getX(), height - Math.max(a.getY(), b.getY()), height - Math.min(a.getY(), b.getY())));
            if (Math.abs(a.getY() - b.getY()) < .8 && Math.abs(a.getX() - b.getX()) > 5)
                horizontal.add(new Horizontal(height - a.getY(), Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX())));
        }
        @Override public void appendRectangle(Point2D a, Point2D b, Point2D c, Point2D d) { edge(a,b); edge(b,c); edge(c,d); edge(d,a); }
        @Override public void drawImage(PDImage image) {}
        @Override public void clip(int windingRule) {}
        @Override public void moveTo(float x, float y) { current = start = new Point2D.Float(x, y); }
        @Override public void lineTo(float x, float y) { Point2D next = new Point2D.Float(x, y); edge(current, next); current = next; }
        @Override public void curveTo(float a, float b, float c, float d, float e, float f) { current = new Point2D.Float(e, f); }
        @Override public Point2D getCurrentPoint() { return current; }
        @Override public void closePath() { edge(current, start); current = start; }
        @Override public void endPath() {}
        @Override public void strokePath() {}
        @Override public void fillPath(int windingRule) {}
        @Override public void fillAndStrokePath(int windingRule) {}
        @Override public void shadingFill(COSName shadingName) {}
    }
}
