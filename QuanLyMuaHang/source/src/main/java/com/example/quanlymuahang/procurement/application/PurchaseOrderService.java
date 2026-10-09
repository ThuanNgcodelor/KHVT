package com.example.quanlymuahang.procurement.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.domain.material.Material;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.domain.purchaseorder.GeneratedDocument;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrder;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderItem;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderRevision;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderStatus;
import com.example.quanlymuahang.domain.supplier.Supplier;
import com.example.quanlymuahang.repository.GeneratedDocumentRepository;
import com.example.quanlymuahang.repository.HistoricalPurchaseRepository;
import com.example.quanlymuahang.repository.MaterialRepository;
import com.example.quanlymuahang.repository.PurchaseOrderRepository;
import com.example.quanlymuahang.repository.PurchaseOrderRevisionRepository;
import com.example.quanlymuahang.repository.SupplierRepository;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final MaterialRepository materials;
    private final PurchaseOrderRevisionRepository revisions;
    private final GeneratedDocumentRepository documents;
    private final HistoricalPurchaseRepository history;
    private final AuditRecorder audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final Path fontPath;
    private final ZoneId zoneId;

    public PurchaseOrderService(PurchaseOrderRepository orders, SupplierRepository suppliers, MaterialRepository materials,
                                PurchaseOrderRevisionRepository revisions, GeneratedDocumentRepository documents,
                                HistoricalPurchaseRepository history, AuditRecorder audit,
                                JdbcTemplate jdbc, ObjectMapper objectMapper,
                                @Value("${app.files.root:./data/files}") String storageRoot,
                                @Value("${app.pdf.font-path:/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf}") String fontPath,
                                @Value("${app.timezone:Asia/Ho_Chi_Minh}") String timezone) {
        this.orders = orders; this.suppliers = suppliers; this.materials = materials; this.revisions = revisions;
        this.documents = documents; this.history = history; this.audit = audit; this.jdbc = jdbc; this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize(); this.fontPath = Path.of(fontPath);
        this.zoneId = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public Page<OrderView> search(String query, PurchaseOrderStatus status, Pageable pageable) {
        return orders.search(query == null ? null : query.trim(), status, pageable).map(OrderView::from);
    }

    @Transactional(readOnly = true)
    public OrderView get(long id) { return OrderView.from(order(id)); }

    @Transactional
    public OrderView create(OrderCommand command, long actorId) {
        validate(command);
        Supplier supplier = suppliers.findById(command.supplierId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        LocalDate orderDate = command.orderDate() == null ? LocalDate.now(zoneId) : command.orderDate();
        String poNumber = nextPoNumber(orderDate);
        PurchaseOrder order = new PurchaseOrder(poNumber, orderDate, supplier.getName());
        order.setSupplier(supplier); order.setSupplierAddressSnapshot(supplier.getAddress());
        order.setCurrency(command.currency()); order.setVatPercent(command.vatPercent()); order.setNote(clean(command.note()));
        order.setPreparedBy(clean(command.preparedBy())); order.setCreatedBy(actorId); order.setUpdatedBy(actorId);
        order.replaceItems(buildItems(command.items()));
        orders.saveAndFlush(order);
        saveRevision(order, actorId, "Khởi tạo đơn mua");
        OrderView view = OrderView.from(order);
        audit.record(actorId, "PURCHASE_ORDER_CREATED", "PURCHASE_ORDER", order.getId(), view);
        return view;
    }

    @Transactional
    public OrderView update(long id, OrderCommand command, long actorId) {
        validate(command);
        PurchaseOrder order = order(id);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) throw ApiException.conflict("PO_CANCELLED", "Đơn mua đã hủy không thể sửa");
        Supplier supplier = suppliers.findById(command.supplierId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        order.setOrderDate(command.orderDate() == null ? order.getOrderDate() : command.orderDate());
        order.setSupplier(supplier); order.setSupplierNameSnapshot(supplier.getName()); order.setSupplierAddressSnapshot(supplier.getAddress());
        order.setCurrency(command.currency()); order.setVatPercent(command.vatPercent()); order.setNote(clean(command.note()));
        order.setPreparedBy(clean(command.preparedBy())); order.setUpdatedBy(actorId);
        order.replaceItems(buildItems(command.items()));
        order.setRevision(order.getRevision() + 1);
        order.setStatus(PurchaseOrderStatus.DRAFT);
        orders.saveAndFlush(order);
        saveRevision(order, actorId, clean(command.changeReason()) == null ? "Cập nhật đơn mua" : command.changeReason());
        OrderView view = OrderView.from(order);
        audit.record(actorId, "PURCHASE_ORDER_REVISED", "PURCHASE_ORDER", order.getId(), view);
        return view;
    }

    @Transactional
    public OrderView cancel(long id, String reason, long actorId) {
        PurchaseOrder order = order(id);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) return OrderView.from(order);
        order.cancel(reason == null || reason.isBlank() ? "Hủy theo yêu cầu người dùng" : reason.trim());
        order.setUpdatedBy(actorId);
        OrderView view = OrderView.from(order);
        audit.record(actorId, "PURCHASE_ORDER_CANCELLED", "PURCHASE_ORDER", order.getId(), view);
        return view;
    }

    @Transactional
    public PdfFile pdf(long id, long actorId) {
        PurchaseOrder order = order(id);
        return pdfForRevision(order, order.getRevision(), actorId);
    }

    @Transactional
    public PdfFile pdf(long id, int revisionNumber, long actorId) {
        PurchaseOrder order = order(id);
        if (revisionNumber == order.getRevision()) return pdfForRevision(order, revisionNumber, actorId);
        PurchaseOrderRevision revision = revisions.findByPurchaseOrderIdAndRevision(id, revisionNumber)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy revision của đơn mua"));
        try {
            OrderView snapshot = objectMapper.readValue(revision.getSnapshotJson(), OrderView.class);
            return generatePdf(order, snapshot, revisionNumber, actorId, false);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể đọc snapshot PDF của revision", exception);
        }
    }

    private PdfFile pdfForRevision(PurchaseOrder order, int revision, long actorId) {
        return generatePdf(order, OrderView.from(order), revision, actorId, true);
    }

    private PdfFile generatePdf(PurchaseOrder order, OrderView snapshot, int revision, long actorId, boolean currentRevision) {
        Optional<GeneratedDocument> existing = documents.findByPurchaseOrderIdAndRevisionAndDocumentType(order.getId(), revision, "PO_PDF");
        if (existing.isPresent()) {
            Path path = safeStoredPath(existing.get().getStorageKey());
            if (Files.isRegularFile(path)) return readPdf(existing.get(), path);
        }
        if (snapshot.vatPercent() == null)
            throw ApiException.conflict("VAT_UNKNOWN", "Đơn legacy chưa xác định VAT. Hãy bổ sung VAT và tạo revision trước khi phát hành PDF.");
        if (currentRevision && order.getStatus() == PurchaseOrderStatus.CANCELLED)
            throw ApiException.conflict("PO_CANCELLED", "Đơn mua đã hủy không thể phát hành PDF mới");
        byte[] content = renderPdf(snapshot);
        String fileName = snapshot.poNumber() + "-r" + revision + ".pdf";
        String storageKey = "purchase-orders/" + order.getId() + "/r" + revision + ".pdf";
        Path path = safeStoredPath(storageKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể lưu PDF đơn mua", exception);
        }
        if (existing.isEmpty()) documents.save(new GeneratedDocument(order.getId(), revision, "PO_PDF", fileName, storageKey,
                "application/pdf", sha256(content), actorId));
        if (currentRevision) {
            recordPriceHistory(order);
            if (order.getStatus() == PurchaseOrderStatus.DRAFT) order.setStatus(PurchaseOrderStatus.EXPORTED);
        }
        audit.record(actorId, "PURCHASE_ORDER_PDF_GENERATED", "PURCHASE_ORDER", order.getId(),
                java.util.Map.of("revision", revision, "fileName", fileName, "sha256", sha256(content)));
        return new PdfFile(fileName, content);
    }

    private void recordPriceHistory(PurchaseOrder order) {
        for (PurchaseOrderItem item : order.getItems()) {
            String reference = "PO:" + order.getId() + ":R:" + order.getRevision() + ":L:" + item.getLineNo();
            if (history.existsBySourceReference(reference)) continue;
            HistoricalPurchase record = new HistoricalPurchase();
            record.setPurchaseDate(order.getOrderDate()); record.setSupplier(order.getSupplier());
            record.setSupplierSnapshot(order.getSupplierNameSnapshot());
            record.setSupplierCodeSnapshot(order.getSupplier() == null ? null : order.getSupplier().getCode());
            record.setMaterial(item.getMaterial()); record.setMaterialCodeSnapshot(item.getMaterialCodeSnapshot());
            record.setMaterialNameSnapshot(item.getMaterialName());
            record.setMaterialNameNormalizedSnapshot(com.example.quanlymuahang.service.TextNormalizer.normalize(item.getMaterialName()));
            record.setUnit(item.getUnit()); record.setQuantity(item.getQuantity()); record.setQuantityText(item.getQuantityText());
            record.setUnitPrice(item.getUnitPrice()); record.setCurrency(order.getCurrency()); record.setCurrencyBasis("SOURCE");
            record.setSource("PURCHASE_ORDER"); record.setSourceRowNumber(item.getLineNo());
            record.setCategory(item.getMaterial() == null ? MaterialCategory.MATERIAL : item.getMaterial().getCategory());
            record.setSourceReference(reference);
            if (item.getQuantity() == null && item.getQuantityText() != null) record.setDataQualityFlags("[\"QUANTITY_TEXT_NOT_COUNTED\"]");
            history.save(record);
        }
    }

    private String nextPoNumber(LocalDate date) {
        jdbc.update("INSERT INTO po_daily_sequences (sequence_date, next_number) VALUES (?, LAST_INSERT_ID(1)) " +
                "ON DUPLICATE KEY UPDATE next_number = LAST_INSERT_ID(next_number + 1)", java.sql.Date.valueOf(date));
        Integer sequence = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
        if (sequence == null || sequence < 1) throw new IllegalStateException("Không lấy được số thứ tự PO");
        return "PO-" + date.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    private List<PurchaseOrderItem> buildItems(List<ItemCommand> commands) {
        List<PurchaseOrderItem> result = new ArrayList<>();
        for (ItemCommand command : commands) {
            Material material = command.materialId() == null ? null : materials.findById(command.materialId()).orElseThrow(() -> ApiException.notFound("Không tìm thấy vật tư"));
            String name = command.materialName() == null || command.materialName().isBlank() ? (material == null ? null : material.getName()) : command.materialName().trim();
            if (name == null || name.isBlank()) throw ApiException.badRequest("ITEM_NAME_REQUIRED", "Tên hàng/vật tư bắt buộc");
            PurchaseOrderItem item = new PurchaseOrderItem(name, clean(command.unit()), command.quantity(), command.unitPrice());
            item.setMaterial(material);
            item.setMaterialCodeSnapshot(material == null ? clean(command.materialCode()) : material.getCode());
            item.update(item.getMaterialCodeSnapshot(), clean(command.specification()), command.quantity(), clean(command.quantityText()));
            result.add(item);
        }
        return result;
    }

    private void validate(OrderCommand command) {
        if (command.items() == null || command.items().isEmpty() || command.items().size() > 200)
            throw ApiException.badRequest("INVALID_ITEMS", "Đơn mua cần có từ 1 đến 200 dòng hàng");
        if (command.currency() == null) throw ApiException.badRequest("CURRENCY_REQUIRED", "Loại tiền bắt buộc");
        if (command.vatPercent() == null || !List.of(new BigDecimal("0"), new BigDecimal("5"), new BigDecimal("8"), new BigDecimal("10")).contains(command.vatPercent().stripTrailingZeros()))
            throw ApiException.badRequest("INVALID_VAT", "VAT của PO mới phải là 0%, 5%, 8% hoặc 10%");
        for (ItemCommand item : command.items()) {
            if (item.unitPrice() == null || item.unitPrice().signum() <= 0) throw ApiException.badRequest("INVALID_UNIT_PRICE", "Đơn giá phải lớn hơn 0");
            boolean numeric = item.quantity() != null && item.quantity().signum() > 0;
            boolean text = item.quantityText() != null && !item.quantityText().isBlank();
            if (numeric == text) throw ApiException.badRequest("INVALID_QUANTITY", "Mỗi dòng phải có số lượng dạng số hoặc số lượng chữ, không được để trống hoặc nhập cả hai");
        }
    }

    private void saveRevision(PurchaseOrder order, long actorId, String reason) {
        try {
            revisions.save(new PurchaseOrderRevision(order.getId(), order.getRevision(), objectMapper.writeValueAsString(OrderView.from(order)), actorId, reason));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể lưu bản chụp revision", exception);
        }
    }

    private byte[] renderPdf(OrderView order) {
        if (!Files.isRegularFile(fontPath)) throw new IllegalStateException("Thiếu font Unicode cho PDF; cấu hình APP_PDF_FONT_PATH đến font DejaVu Sans hoặc Noto Sans");
        try (PDDocument document = new PDDocument(); InputStream fontInput = Files.newInputStream(fontPath); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontInput);
            PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = 790;
            List<String> lines = new ArrayList<>();
            lines.add("ĐƠN ĐẶT HÀNG / PURCHASE ORDER");
            lines.add("Số PO: " + order.poNumber() + "    Ngày: " + (order.orderDate() == null ? "Chưa xác định" : order.orderDate()));
            lines.add("Nhà cung cấp: " + order.supplierName());
            lines.add("Địa chỉ: " + (order.supplierAddress() == null ? "" : order.supplierAddress()));
            lines.add("Tiền tệ: " + order.currency() + "    VAT: " + order.vatPercent() + "%");
            lines.add("--------------------------------------------------------------------------------------------------------------------------------");
            lines.add("STT | Tên hàng/vật tư | Quy cách | ĐVT | Số lượng | Đơn giá | Thành tiền");
            for (OrderItemView item : order.items()) {
                String qty = item.quantity() == null ? item.quantityText() : item.quantity().stripTrailingZeros().toPlainString();
                String line = item.lineNo() + " | " + item.materialName() + " | " + safe(item.specification()) + " | " + safe(item.unit())
                        + " | " + safe(qty) + " | " + money(item.unitPrice(), order.currency()) + " | " + (item.lineTotal() == null ? "Không cộng tự động" : money(item.lineTotal(), order.currency()));
                lines.addAll(wrap(line, 94));
            }
            lines.add("--------------------------------------------------------------------------------------------------------------------------------");
            lines.add("Tạm tính (không gồm dòng số lượng chữ): " + money(order.subtotal(), order.currency()) + " " + order.currency());
            lines.add("VAT: " + money(order.taxAmount(), order.currency()) + " " + order.currency());
            lines.add("Tổng cộng: " + money(order.grandTotal(), order.currency()) + " " + order.currency());
            if (order.quantityTextLineCount() > 0) lines.add("Lưu ý: " + order.quantityTextLineCount() + " dòng số lượng dạng chữ không được cộng vào tổng.");
            lines.add("Ghi chú: " + safe(order.note()));
            lines.add("Người lập: " + safe(order.preparedBy()));
            for (String line : lines) {
                if (y < 45) {
                    stream.close(); page = new PDPage(PDRectangle.A4); document.addPage(page);
                    stream = new PDPageContentStream(document, page); y = 790;
                }
                stream.beginText(); stream.setFont(font, 8.5f); stream.newLineAtOffset(40, y); stream.showText(safe(line)); stream.endText();
                y -= 15;
            }
            stream.close(); document.save(bytes); return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo PDF", exception);
        }
    }

    private static List<String> wrap(String line, int max) {
        List<String> chunks = new ArrayList<>();
        while (line.length() > max) { chunks.add(line.substring(0, max)); line = "   " + line.substring(max); }
        chunks.add(line); return chunks;
    }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").replaceAll("\\p{Cntrl}", " "); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String money(BigDecimal value, CurrencyCode currency) { return value.setScale(scale(currency), RoundingMode.HALF_UP).toPlainString(); }
    private static int scale(CurrencyCode currency) { return currency == CurrencyCode.VND ? 0 : 2; }
    private Path safeStoredPath(String key) {
        Path resolved = storageRoot.resolve(key).normalize();
        if (!resolved.startsWith(storageRoot)) throw new IllegalStateException("Storage key không hợp lệ");
        return resolved;
    }
    private PdfFile readPdf(GeneratedDocument document, Path path) {
        try { return new PdfFile(document.getFileName(), Files.readAllBytes(path)); }
        catch (IOException exception) { throw new IllegalStateException("Không thể đọc PDF đã lưu", exception); }
    }
    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private PurchaseOrder order(long id) { return orders.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn mua")); }

    @Transactional(readOnly = true)
    public List<RevisionView> revisions(long id) {
        order(id);
        return revisions.findAllByPurchaseOrderIdOrderByRevisionDesc(id).stream()
                .map(revision -> new RevisionView(revision.getRevision(), revision.getChangedBy(), revision.getChangeReason(), revision.getCreatedAt()))
                .toList();
    }

    public record OrderCommand(Long supplierId, LocalDate orderDate, CurrencyCode currency, BigDecimal vatPercent,
                               String note, String preparedBy, String changeReason, List<ItemCommand> items) {}
    public record ItemCommand(Long materialId, String materialCode, String materialName, String specification,
                              String unit, BigDecimal quantity, String quantityText, BigDecimal unitPrice) {}
    public record PdfFile(String fileName, byte[] content) {}
    public record RevisionView(int revision, Long changedBy, String changeReason, java.time.Instant createdAt) {}
    public record OrderView(Long id, String poNumber, LocalDate orderDate, Long supplierId, String supplierName,
                            String supplierAddress, CurrencyCode currency, BigDecimal vatPercent, String note,
                            String preparedBy, PurchaseOrderStatus status, int revision, long version,
                            List<OrderItemView> items, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal grandTotal,
                            int quantityTextLineCount) {
        public static OrderView from(PurchaseOrder order) {
            List<OrderItemView> itemViews = order.getItems().stream().map(item -> OrderItemView.from(item, order.getCurrency())).toList();
            int scale = scale(order.getCurrency());
            BigDecimal subtotal = itemViews.stream().map(OrderItemView::lineTotal).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(scale, RoundingMode.HALF_UP);
            BigDecimal tax = order.getVatPercent() == null ? null : subtotal.multiply(order.getVatPercent()).divide(new BigDecimal("100"), scale, RoundingMode.HALF_UP);
            BigDecimal grand = tax == null ? null : subtotal.add(tax).setScale(scale, RoundingMode.HALF_UP);
            return new OrderView(order.getId(), order.getPoNumber(), order.getOrderDate(), order.getSupplier() == null ? null : order.getSupplier().getId(),
                    order.getSupplierNameSnapshot(), order.getSupplierAddressSnapshot(), order.getCurrency(), order.getVatPercent(), order.getNote(),
                    order.getPreparedBy(), order.getStatus(), order.getRevision(), order.getVersion(), itemViews, subtotal, tax, grand,
                    (int) itemViews.stream().filter(item -> item.quantity() == null && item.quantityText() != null && !item.quantityText().isBlank()).count());
        }
    }
    public record OrderItemView(Long id, int lineNo, Long materialId, String materialCode, String materialName, String specification,
                                String unit, BigDecimal quantity, String quantityText, BigDecimal unitPrice, BigDecimal lineTotal) {
        static OrderItemView from(PurchaseOrderItem item, CurrencyCode currency) {
            BigDecimal total = item.getQuantity() == null ? null : item.getUnitPrice().multiply(item.getQuantity()).setScale(scale(currency), RoundingMode.HALF_UP);
            return new OrderItemView(item.getId(), item.getLineNo(), item.getMaterial() == null ? null : item.getMaterial().getId(), item.getMaterialCodeSnapshot(),
                    item.getMaterialName(), item.getSpecification(), item.getUnit(), item.getQuantity(), item.getQuantityText(), item.getUnitPrice(), total);
        }
    }
}
