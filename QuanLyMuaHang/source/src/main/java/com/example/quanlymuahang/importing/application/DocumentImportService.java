package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.importing.application.SourceDocumentModels.*;
import com.example.quanlymuahang.importing.infrastructure.persistence.*;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDate;
import java.util.*;

/** Stores reviewed source documents, never purchase orders or purchased-price history. */
@Service
public class DocumentImportService {
    private final SourceDocumentParser parser;
    private final DocumentImportRepository documents;
    private final ObjectMapper json;
    private final AuditRecorder audit;
    private final DocumentHistoryLookup history;
    private final Path storage;
    public DocumentImportService(SourceDocumentParser parser, DocumentImportRepository documents, ObjectMapper json,
                                 AuditRecorder audit, DocumentHistoryLookup history, @Value("${app.files.root}") String storage) {
        this.parser = parser; this.documents = documents; this.json = json; this.audit = audit; this.history = history;
        this.storage = Path.of(storage).toAbsolutePath().normalize();
    }
    @Transactional
    public DocumentView preview(MultipartFile file, String kind, String sheet, long actor) {
        if (!Set.of("REQUEST", "QUOTATION").contains(kind)) throw bad("DOCUMENT_KIND_INVALID", "Chọn yêu cầu mua hoặc báo giá");
        if (file == null || file.isEmpty()) throw bad("FILE_REQUIRED", "Chọn tệp");
        if (file.getSize() > 10 * 1024 * 1024) throw bad("FILE_TOO_LARGE", "Tệp tối đa 10 MB");
        String sheetKey = clean(sheet);
        if (sheetKey.length() > 80) throw bad("SHEET_INVALID", "Tên sheet quá dài");
        try {
            byte[] content = file.getBytes();
            String checksum = checksum(content);
            var existing = documents.findByChecksumAndKindAndSheetKey(checksum, kind, sheetKey);
            if (existing.isPresent()) return view(existing.get(), true);
            ParsedDocument parsed = parser.parse(file, kind, sheetKey.isBlank() ? null : sheetKey);
            String fileName = clean(parsed.fileName()).replaceAll("[\\r\\n\\x00]", "");
            if (fileName.isBlank() || fileName.length() > 255) throw bad("FILE_NAME_INVALID", "Tên tệp không hợp lệ hoặc quá dài");
            String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (!Set.of(".xlsx", ".xls", ".csv", ".pdf").contains(extension)) throw bad("UNSUPPORTED_FILE", "Định dạng tệp chưa hỗ trợ");
            String key = "source-imports/" + checksum + extension;
            Path path = path(key); Files.createDirectories(path.getParent());
            try { Files.write(path, content, StandardOpenOption.CREATE_NEW); }
            catch (FileAlreadyExistsException ignored) { if (!checksum(Files.readAllBytes(path)).equals(checksum)) throw ApiException.conflict("SOURCE_CHANGED", "Tệp nguồn đã thay đổi"); }
            DocumentImportEntity entity = documents.saveAndFlush(new DocumentImportEntity(kind, fileName, checksum, sheetKey, key, write(parsed), actor));
            audit.record(actor, "DOCUMENT_PREVIEW", "SOURCE_DOCUMENT", entity.getId(), Map.of("kind", kind, "rows", parsed.lines().size()));
            return view(entity, false);
        } catch (IOException e) { throw bad("FILE_READ_FAILED", "Không đọc/lưu được tệp nguồn"); }
        catch (DataIntegrityViolationException e) { throw ApiException.conflict("DOCUMENT_ALREADY_UPLOADED", "Tệp đã được tải lên đồng thời; mở lại danh sách lô nhập"); }
    }
    @Transactional(readOnly = true)
    public List<DocumentView> list() { return documents.findAllByOrderByIdDesc(PageRequest.of(0, 50)).stream().map(d -> view(d, false)).toList(); }
    @Transactional(readOnly = true)
    public DocumentView get(long id) { return view(document(id, false), false); }
    @Transactional
    public DocumentView confirm(long id, Confirmation input, long actor) {
        DocumentImportEntity entity = document(id, true);
        if (input == null || !input.acknowledged()) throw bad("REVIEW_REQUIRED", "Xác nhận đã kiểm tra chứng từ trước khi lưu");
        ParsedDocument parsed = read(entity.getParsedJson(), ParsedDocument.class);
        Confirmation reviewed = review(entity.getKind(), parsed, input);
        if (entity.getConfirmedJson() != null) {
            if (read(entity.getConfirmedJson(), Confirmation.class).equals(reviewed)) return view(entity, true);
            throw ApiException.conflict("DOCUMENT_ALREADY_CONFIRMED", "Lô đã lưu; tải bản chứng từ mới để thay đổi, không ghi đè bản đã xác nhận");
        }
        entity.confirm(reviewed.title(), write(reviewed));
        audit.record(actor, "DOCUMENT_CONFIRMED", "SOURCE_DOCUMENT", id, Map.of("kind", entity.getKind(), "rows", reviewed.lines().size()));
        return view(entity, false);
    }
    private Confirmation review(String kind, ParsedDocument parsed, Confirmation input) {
        String title = bounded(input.title(), 255, "Tiêu đề");
        if (title.isBlank()) throw bad("TITLE_REQUIRED", "Nhập tiêu đề yêu cầu/báo giá");
        if (input.lines() == null || input.lines().isEmpty() || input.lines().size() > 5000) throw bad("LINES_REQUIRED", "Chọn từ 1 đến 5.000 dòng");
        String supplier = bounded(input.supplierName(), 500, "Nhà cung cấp");
        String currency = clean(input.currency()).toUpperCase(Locale.ROOT);
        if (!currency.isBlank() && !Set.of("VND", "USD").contains(currency)) throw bad("CURRENCY_INVALID", "Loại tiền chỉ hỗ trợ VND hoặc USD");
        String date = clean(input.quotationDate());
        if (!date.isBlank()) { try { LocalDate.parse(date); } catch (Exception e) { throw bad("DATE_INVALID", "Ngày phải là ngày hợp lệ dạng yyyy-MM-dd"); } }
        if ("QUOTATION".equals(kind) && (supplier.isBlank() || currency.isBlank() || date.isBlank())) throw bad("QUOTATION_METADATA_REQUIRED", "Xác nhận NCC, ngày báo giá và loại tiền");
        String basis = basis(input.priceBasis()); vat(input.vatPercent());
        String requestNumber = bounded(input.requestNumber(), 100, "Số phiếu");
        Map<String, DocumentLine> originals = new LinkedHashMap<>(); parsed.lines().forEach(l -> originals.put(l.lineKey(), l));
        Set<String> groups = new HashSet<>(); parsed.lines().forEach(l -> { if (!clean(l.requestNumber()).isBlank()) groups.add(l.requestNumber()); });
        Set<String> used = new HashSet<>(); List<DocumentLine> lines = new ArrayList<>();
        for (DocumentLine l : input.lines()) {
            if (l == null || l.lineKey() == null || !used.add(l.lineKey())) throw bad("DUPLICATE_LINE", "Dòng nguồn bị trùng hoặc thiếu mã");
            DocumentLine original = originals.get(l.lineKey());
            boolean manual = original == null && l.lineKey().matches("manual-[A-Za-z0-9-]{1,80}");
            if (original == null && !manual) throw bad("UNKNOWN_SOURCE_LINE", "Dòng không thuộc chứng từ nguồn");
            String name = bounded(l.materialName(), 500, "Tên vật tư");
            if (name.isBlank()) throw bad("MATERIAL_NAME_REQUIRED", "Dòng vật tư cần có tên");
            decimal(l.quantity()); decimal(l.unitPrice()); decimal(l.lineTotal()); vat(l.vatPercent());
            if (l.quantity() != null && l.quantity().signum() <= 0) throw bad("QUANTITY_INVALID", "Số lượng phải lớn hơn 0");
            if (l.unitPrice() != null && l.unitPrice().signum() < 0) throw bad("PRICE_INVALID", "Đơn giá không được âm");
            String qtyText = bounded(l.quantityText(), 255, "Số lượng chữ");
            if ("REQUEST".equals(kind) && l.quantity() == null && qtyText.isBlank()) throw bad("QUANTITY_REQUIRED", "Yêu cầu mua cần số lượng hoặc số lượng dạng chữ");
            String group = "REQUEST".equals(kind) && !requestNumber.isBlank() ? requestNumber : clean(l.requestNumber());
            if ("REQUEST".equals(kind) && !groups.isEmpty()) {
                if (requestNumber.isBlank()) throw bad("REQUEST_NUMBER_REQUIRED", "Chọn một số phiếu trước khi lưu");
                if (original != null && !clean(original.requestNumber()).isBlank() && !original.requestNumber().equals(requestNumber)) throw bad("MIXED_REQUEST_GROUPS", "Không gộp nhiều phiếu của sổ năm thành một yêu cầu");
            }
            List<String> issues = new ArrayList<>(original == null ? List.of("MANUAL_ENTRY") : original.issues());
            if (clean(l.unit()).isBlank() && !issues.contains("UNIT_MISSING")) issues.add("UNIT_MISSING");
            if ("QUOTATION".equals(kind) && l.unitPrice() == null && !issues.contains("PRICE_MISSING")) issues.add("PRICE_MISSING");
            String lineCurrency = clean(l.currency()).isBlank() ? currency : clean(l.currency()).toUpperCase(Locale.ROOT);
            if (!lineCurrency.isBlank() && !Set.of("VND", "USD").contains(lineCurrency)) throw bad("CURRENCY_INVALID", "Loại tiền dòng không hợp lệ");
            lines.add(new DocumentLine(l.lineKey(), original == null ? null : original.sourceSheet(), original == null ? 0 : original.sourceRow(),
                    original == null ? l.sourcePage() : original.sourcePage(), group, bounded(l.materialCode(), 80, "Mã vật tư"), name,
                    bounded(l.specification(), 1000, "Quy cách"), bounded(l.unit(), 100, "ĐVT"), l.quantity(), qtyText,
                    l.unitPrice(), l.lineTotal(), l.vatPercent() == null ? input.vatPercent() : l.vatPercent(),
                    clean(l.priceBasis()).isBlank() ? basis : basis(l.priceBasis()), lineCurrency, bounded(l.note(), 2000, "Ghi chú"),
                    original == null ? Map.of() : original.raw(), List.copyOf(issues)));
        }
        Map<String, String> mappings = input.requestLineMappings() == null ? Map.of() : Map.copyOf(input.requestLineMappings());
        if (!mappings.isEmpty() && input.requestId() == null) throw bad("REQUEST_LINK_REQUIRED", "Chọn yêu cầu để nối dòng báo giá");
        if (input.requestId() != null) {
            if (!"QUOTATION".equals(kind)) throw bad("REQUEST_LINK_INVALID", "Chỉ báo giá được nối yêu cầu");
            DocumentImportEntity target = document(input.requestId(), false);
            if (!"REQUEST".equals(target.getKind()) || target.getConfirmedJson() == null) throw bad("REQUEST_LINK_INVALID", "Chọn yêu cầu đã được xác nhận");
            Set<String> targetKeys = new HashSet<>(); read(target.getConfirmedJson(), Confirmation.class).lines().forEach(l -> targetKeys.add(l.lineKey()));
            mappings.forEach((key, value) -> { if (!used.contains(key) || !targetKeys.contains(value)) throw bad("REQUEST_LINE_INVALID", "Liên kết dòng không thuộc yêu cầu hoặc báo giá đã chọn"); });
        }
        return new Confirmation(title, requestNumber, supplier, date, currency, basis, input.vatPercent(), List.copyOf(lines), input.requestId(), mappings, true);
    }
    @Transactional(readOnly = true)
    public SourceFile source(long id) {
        DocumentImportEntity entity = document(id, false);
        try {
            byte[] bytes = Files.readAllBytes(path(entity.getStorageKey()));
            if (!checksum(bytes).equals(entity.getChecksum())) throw ApiException.conflict("SOURCE_CHANGED", "Tệp gốc đã thay đổi; cần đối chiếu trước khi dùng");
            return new SourceFile(entity.getFileName(), bytes);
        } catch (IOException e) { throw ApiException.notFound("Tệp nguồn không còn trên ổ lưu trữ"); }
    }
    @Transactional(readOnly = true)
    public DocumentHistoryLookup.Lookup lookup(long id) { return history.lookup(request(id)); }
    @Transactional
    public DocumentView saveInquiries(long id, InquiryBody input, long actor) {
        DocumentImportEntity entity = document(id, true);
        Confirmation request = confirmedRequest(entity);
        if (input == null || input.selections() == null || input.selections().size() > 10000) throw bad("INQUIRY_INVALID", "Danh sách hỏi giá không hợp lệ");
        var lookup = history.lookup(request);
        Map<String, Set<String>> allowed = new HashMap<>();
        lookup.lines().forEach(l -> { Set<String> keys = new HashSet<>(); l.candidates().forEach(c -> keys.add(c.supplierKey())); allowed.put(l.lineKey(), keys); });
        Set<InquirySelection> selected = new LinkedHashSet<>();
        for (InquirySelection s : input.selections()) {
            if (s == null || !allowed.getOrDefault(s.lineKey(), Set.of()).contains(s.supplierKey())) throw bad("INQUIRY_SUPPLIER_INVALID", "NCC/dòng hỏi giá không thuộc kết quả tra cứu");
            selected.add(s);
        }
        entity.inquiries(write(selected));
        audit.record(actor, "DOCUMENT_INQUIRIES_SAVED", "SOURCE_DOCUMENT", id, Map.of("selections", selected.size()));
        return view(entity, false);
    }
    @Transactional
    public SourceFile inquiriesXlsx(long id, InquiryBody body, long actor) {
        DocumentView saved = saveInquiries(id, body, actor);
        if (saved.inquirySelections().isEmpty()) throw bad("INQUIRY_EMPTY", "Chọn NCC cho các dòng cần hỏi giá");
        Map<String, DocumentLine> lines = new HashMap<>(); saved.confirmed().lines().forEach(l -> lines.put(l.lineKey(), l));
        Map<String, DocumentHistoryLookup.HistoryCandidate> suppliers = new HashMap<>();
        history.lookup(saved.confirmed()).lines().forEach(l -> l.candidates().forEach(c -> suppliers.put(l.lineKey() + "|" + c.supplierKey(), c)));
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Map<String, Sheet> sheets = new LinkedHashMap<>();
            for (InquirySelection s : saved.inquirySelections()) {
                DocumentLine l = lines.get(s.lineKey()); var supplier = suppliers.get(s.lineKey() + "|" + s.supplierKey());
                Sheet sheet = sheets.get(s.supplierKey());
                if (sheet == null) {
                    sheet = book.createSheet("NCC " + (sheets.size() + 1)); sheets.put(s.supplierKey(), sheet);
                    row(sheet, 0, "Nhà cung cấp", supplier.supplierName()); row(sheet, 1, "Yêu cầu", saved.title());
                    row(sheet, 3, "STT", "Tên vật tư", "Quy cách", "ĐVT", "Số lượng", "Ghi chú", "Dòng nguồn"); sheet.createFreezePane(0, 4);
                }
                int n = sheet.getLastRowNum() + 1;
                row(sheet, n, Integer.toString(n - 3), l.materialName(), l.specification(), l.unit(), l.quantity() == null ? l.quantityText() : l.quantity().toPlainString(), l.note(), String.valueOf(l.sourceRow()));
            }
            book.write(out); return new SourceFile("hoi-gia-" + id + ".xlsx", out.toByteArray());
        } catch (IOException e) { throw new IllegalStateException("Không tạo được file hỏi giá", e); }
    }
    @Transactional(readOnly = true)
    public DocumentHistoryLookup.Comparison comparison(long id) {
        List<DocumentView> quotes = documents.findByKindAndStatusOrderByIdDesc("QUOTATION", "CONFIRMED").stream().map(d -> view(d, false))
                .filter(d -> Objects.equals(d.confirmed().requestId(), id)).toList();
        return history.comparison(request(id), quotes);
    }
    private Confirmation request(long id) { return confirmedRequest(document(id, false)); }
    private Confirmation confirmedRequest(DocumentImportEntity entity) {
        if (!"REQUEST".equals(entity.getKind()) || entity.getConfirmedJson() == null) throw bad("REQUEST_NOT_CONFIRMED", "Lưu và xác nhận yêu cầu trước khi tra lịch sử");
        return read(entity.getConfirmedJson(), Confirmation.class);
    }
    private DocumentView view(DocumentImportEntity d, boolean duplicate) {
        return new DocumentView(d.getId(), d.getKind(), d.getTitle(), d.getFileName(), d.getStatus(), duplicate,
                read(d.getParsedJson(), ParsedDocument.class), d.getConfirmedJson() == null ? null : read(d.getConfirmedJson(), Confirmation.class),
                readList(d.getInquiriesJson()));
    }
    private DocumentImportEntity document(long id, boolean lock) { return (lock ? documents.findLockedById(id) : documents.findById(id)).orElseThrow(() -> ApiException.notFound("Không tìm thấy lô nhập")); }
    private Path path(String key) { Path p = storage.resolve(key).normalize(); if (!p.startsWith(storage)) throw new IllegalStateException("Đường dẫn không hợp lệ"); return p; }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (IOException e) { throw new IllegalStateException("Không lưu được dữ liệu chứng từ", e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (IOException e) { throw new IllegalStateException("Không đọc được dữ liệu chứng từ", e); } }
    private List<InquirySelection> readList(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch (IOException e) { throw new IllegalStateException(e); } }
    private static void row(Sheet s, int n, String... cells) { Row r = s.createRow(n); for (int i = 0; i < cells.length; i++) r.createCell(i, CellType.STRING).setCellValue(clean(cells[i])); }
    private static String checksum(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    private static String bounded(String s, int max, String field) { s = clean(s); if (s.length() > max) throw bad("FIELD_TOO_LONG", field + " quá dài"); return s; }
    private static String basis(String s) { s = clean(s); if (s.isBlank()) return "UNKNOWN"; if (!Set.of("EXCL_VAT", "INCL_VAT", "UNKNOWN").contains(s)) throw bad("PRICE_BASIS_INVALID", "Chọn cơ sở VAT của giá"); return s; }
    private static void vat(BigDecimal v) { if (v != null && !(v.compareTo(BigDecimal.ZERO) == 0 || v.compareTo(BigDecimal.valueOf(5)) == 0 || v.compareTo(BigDecimal.valueOf(8)) == 0 || v.compareTo(BigDecimal.TEN) == 0)) throw bad("VAT_INVALID", "VAT hỗ trợ 0, 5, 8 hoặc 10% hoặc chưa xác định"); }
    private static void decimal(BigDecimal v) { if (v != null) { BigDecimal n = v.stripTrailingZeros(); if (Math.max(0, n.scale()) > 18 || Math.max(0, n.precision() - n.scale()) > 20) throw bad("DECIMAL_OUT_OF_RANGE", "Giá trị vượt độ chính xác DECIMAL(38,18)"); } }
    private static ApiException bad(String c, String m) { return ApiException.badRequest(c, m); }

    public record Confirmation(String title, String requestNumber, String supplierName, String quotationDate, String currency,
                               String priceBasis, BigDecimal vatPercent, List<DocumentLine> lines, Long requestId,
                               Map<String, String> requestLineMappings, boolean acknowledged) {}
    public record DocumentView(long id, String kind, String title, String fileName, String status, boolean duplicate,
                               ParsedDocument parsed, Confirmation confirmed, List<InquirySelection> inquirySelections) {}
    public record InquirySelection(String lineKey, String supplierKey) {}
    public record InquiryBody(List<InquirySelection> selections) {}
    public record SourceFile(String fileName, byte[] content) {}
}
