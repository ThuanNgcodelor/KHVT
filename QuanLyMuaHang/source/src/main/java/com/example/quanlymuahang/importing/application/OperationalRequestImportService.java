package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.sharedkernel.web.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses only into an editable client draft. It never writes prices or purchase orders. */
@Service
public class OperationalRequestImportService {
    private static final int MAX_ROWS = 2000;

    public DraftPreview preview(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("FILE_REQUIRED", "Chọn tệp yêu cầu hoặc báo giá");
        if (file.getSize() > 10L * 1024 * 1024) throw ApiException.badRequest("FILE_TOO_LARGE", "Tệp import nghiệp vụ không được vượt quá 10 MB");
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) return spreadsheet(name, bytes);
            if (name.endsWith(".csv")) return delimited(name, new String(bytes, StandardCharsets.UTF_8), 0.95, false, "CSV");
            if (name.endsWith(".pdf")) return pdf(name, bytes);
            throw ApiException.badRequest("UNSUPPORTED_FILE", "Luồng này chỉ hỗ trợ .xlsx, .xls, .csv hoặc PDF có text");
        } catch (IOException exception) {
            throw ApiException.badRequest("FILE_READ_FAILED", "Không thể đọc tệp import");
        }
    }

    public DraftPreview paste(String content) {
        if (content == null || content.isBlank()) throw ApiException.badRequest("PASTE_REQUIRED", "Dán các dòng từ Excel trước");
        return delimited("paste-excel.tsv", content, 0.9, true, "PASTE");
    }

    private DraftPreview spreadsheet(String fileName, byte[] bytes) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() == 0) throw ApiException.badRequest("EMPTY_WORKBOOK", "Workbook không có sheet");
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<List<String>> table = new ArrayList<>();
            for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), 15 + MAX_ROWS); r++) {
                Row row = sheet.getRow(r);
                if (row == null) { table.add(List.of()); continue; }
                List<String> values = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) values.add(cellText(row.getCell(c), formatter, evaluator));
                table.add(values);
            }
            return parseTable(fileName, table, 0.95, false, "XLSX");
        }
    }

    private DraftPreview pdf(String fileName, byte[] bytes) throws IOException {
        String extracted;
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            extracted = stripper.getText(document);
        } catch (IOException exception) {
            throw ApiException.badRequest("INVALID_PDF", "PDF không đọc được");
        }
        if (extracted == null || extracted.isBlank())
            throw ApiException.badRequest("OCR_NOT_SUPPORTED", "PDF không có lớp text (có thể là file scan); hãy dán dữ liệu hoặc nhập tay");
        List<List<String>> lines = extracted.lines().limit(MAX_ROWS + 20L).map(line -> splitPdfLine(line)).toList();
        if (lines.stream().noneMatch(line -> line.stream().map(OperationalRequestImportService::normalize).anyMatch(value -> value.equals("tenhang") || value.equals("tenvattu") || value.equals("diengiai"))))
            throw ApiException.badRequest("PDF_TABLE_NOT_RECOGNIZED", "Không nhận ra bảng hàng trong PDF; hãy dán dữ liệu Excel để kiểm tra thủ công");
        return parseTable(fileName, lines, 0.6, true, "PDF_TEXT");
    }

    private DraftPreview delimited(String fileName, String text, double confidence, boolean paste, String kind) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        List<String> lines = text.lines().limit(MAX_ROWS + 20L).toList();
        char delimiter = detectDelimiter(lines);
        List<List<String>> table = lines.stream().map(line -> parseDelimitedLine(line, delimiter)).toList();
        return parseTable(fileName, table, confidence, paste, kind);
    }

    private DraftPreview parseTable(String fileName, List<List<String>> rows, double confidence, boolean paste, String kind) {
        int headerIndex = -1;
        Map<Integer, String> columns = Map.of();
        for (int i = 0; i < Math.min(15, rows.size()); i++) {
            Map<Integer, String> candidate = mapHeaders(rows.get(i));
            if (candidate.containsValue("materialName")) { headerIndex = i; columns = candidate; break; }
        }
        if (headerIndex < 0 && paste && !rows.isEmpty()) {
            List<String> firstRow = rows.get(0);
            boolean hasSerial = !firstRow.isEmpty() && normalize(firstRow.get(0)).equals("stt");
            int nameColumn = hasSerial ? 1 : 0;
            if (firstRow.size() > nameColumn) {
                headerIndex = -1;
                Map<Integer, String> inferred = new LinkedHashMap<>();
                inferred.put(nameColumn, "materialName");
                if (firstRow.size() > nameColumn + 1) inferred.put(nameColumn + 1, "specification");
                if (firstRow.size() > nameColumn + 2) inferred.put(nameColumn + 2, "unit");
                if (firstRow.size() > nameColumn + 3) inferred.put(nameColumn + 3, "quantity");
                if (firstRow.size() > nameColumn + 4) inferred.put(nameColumn + 4, "unitPrice");
                columns = inferred;
            }
        }
        if (columns.isEmpty()) throw ApiException.badRequest("ITEM_HEADER_NOT_FOUND", "Không tìm thấy cột tên hàng/vật tư trong 15 dòng đầu");
        List<DraftItem> items = new ArrayList<>();
        List<RowWarning> warnings = new ArrayList<>();
        for (int r = headerIndex + 1; r < rows.size() && items.size() < MAX_ROWS; r++) {
            List<String> cells = rows.get(r);
            Map<String, String> data = new LinkedHashMap<>();
            columns.forEach((index, field) -> {
                if (index >= cells.size() || cells.get(index).isBlank()) return;
                String cell = cells.get(index).trim();
                if (cell.startsWith("\u0001NUM:")) { data.put(field, cell.substring(5)); data.put(field + "__numeric", "true"); }
                else data.put(field, cell);
            });
            String name = data.get("materialName");
            if (name == null || name.isBlank()) continue;
            BigDecimal quantity = parseNumber(data, "quantity");
            String quantityText = data.get("quantity");
            if (quantity != null) quantityText = null;
            BigDecimal unitPrice = parseNumber(data, "unitPrice");
            List<String> rowWarnings = new ArrayList<>();
            if (data.get("unitPrice") == null) rowWarnings.add("Chưa có đơn giá; cần nhập/ xác nhận trước khi lập PO.");
            else if (unitPrice == null) rowWarnings.add("Không đọc được đơn giá; giữ nguyên nội dung để người dùng kiểm tra.");
            if (quantity == null && quantityText != null) rowWarnings.add("Số lượng dạng chữ được giữ nguyên, không tự cộng vào tổng.");
            if (data.get("quantity") == null) rowWarnings.add("Chưa có số lượng.");
            items.add(new DraftItem(r + 1, name, data.get("materialCode"), data.get("specification"), data.get("unit"),
                    quantity, quantityText, unitPrice, data.get("unitPrice"), List.copyOf(rowWarnings)));
            if (!rowWarnings.isEmpty()) warnings.add(new RowWarning(r + 1, List.copyOf(rowWarnings)));
        }
        if (items.isEmpty()) throw ApiException.badRequest("NO_ITEMS_FOUND", "Không tìm thấy dòng vật tư hợp lệ sau header");
        double resultConfidence = kind.equals("PDF_TEXT") ? confidence : Math.min(confidence, 0.98);
        return new DraftPreview(fileName, kind, resultConfidence, items, warnings,
                paste ? "Đã tách các dòng thành draft; chưa ghi lịch sử giá hoặc tạo PO." : "Đây chỉ là draft để người dùng rà soát và chỉnh mapping; chưa ghi lịch sử giá hoặc tạo PO.");
    }

    private Map<Integer, String> mapHeaders(List<String> headers) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
        String normalized = normalize(headers.get(i)).replace(" ", "").replace("_", "").replace("-", "");
            String field = switch (normalized) {
                case "tenhang", "tenvattu", "tennguyenlieu", "diengiai", "hanghoavattu" -> "materialName";
                case "mahang", "mavattu" -> "materialCode";
                case "quycach", "thongsokythuat" -> "specification";
                case "dvt", "donvi", "donvitinh" -> "unit";
                case "soluong", "slmua", "quantity" -> "quantity";
                case "dongia", "giatien", "unitprice" -> "unitPrice";
                default -> null;
            };
            if (field != null) result.put(i, field);
        }
        return result;
    }

    private static String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? evaluator.evaluateFormulaCell(cell) : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate().toString();
        if (type == CellType.NUMERIC) return "\u0001NUM:" + BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        if (type == CellType.STRING) return cell.getStringCellValue().trim();
        return formatter.formatCellValue(cell, evaluator).trim();
    }

    private static char detectDelimiter(List<String> lines) {
        String sample = lines.stream().filter(line -> !line.isBlank()).limit(5).reduce("", (a, b) -> a + b);
        return Arrays.asList(',', ';', '\t').stream().max(java.util.Comparator.comparingInt(delimiter -> count(sample, delimiter))).orElse('\t');
    }
    private static int count(String text, char delimiter) { return (int) text.chars().filter(value -> value == delimiter).count(); }
    private static List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> values = new ArrayList<>(); StringBuilder current = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == delimiter && !quoted) { values.add(current.toString().trim()); current.setLength(0); }
            else current.append(c);
        }
        values.add(current.toString().trim()); return values;
    }
    private static List<String> splitPdfLine(String line) {
        String cleaned = line.trim();
        if (cleaned.contains("\t")) return Arrays.stream(cleaned.split("\\t", -1)).map(String::trim).toList();
        return Arrays.stream(cleaned.split("\\s{2,}", -1)).map(String::trim).toList();
    }
    private static String normalize(String value) { return com.example.quanlymuahang.service.TextNormalizer.normalize(value); }
    private static BigDecimal parseNumber(Map<String, String> data, String field) {
        String value = data.get(field);
        if (value == null) return null;
        if ("true".equals(data.get(field + "__numeric"))) {
            try { return new BigDecimal(value); } catch (NumberFormatException exception) { return null; }
        }
        return LocalizedNumberParser.parse(value);
    }

    public record DraftItem(int sourceRow, String materialName, String materialCode, String specification, String unit,
                            BigDecimal quantity, String quantityText, BigDecimal unitPrice, String rawUnitPrice,
                            List<String> warnings) {}
    public record RowWarning(int sourceRow, List<String> warnings) {}
    public record DraftPreview(String fileName, String sourceType, double confidence, List<DraftItem> items,
                               List<RowWarning> warnings, String message) {}
}
