package com.example.quanlymuahang.procurement.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Native PDF counterpart of legacy buildPoHtml_; does not evaluate workbook HTML. */
final class PurchaseOrderPdfRenderer {
    private static final float LEFT = 40, WIDTH = 515;
    private static final float[] COLUMNS = {24, 132, 108, 30, 68, 76, 77};
    private static final Color NAVY = new Color(31, 56, 100), PALE = new Color(217, 225, 242);
    private final PDDocument document;
    private final PDType0Font font;
    private final PurchaseOrderService.OrderView order;
    private PDPageContentStream stream;
    private float y;

    private PurchaseOrderPdfRenderer(PDDocument document, PDType0Font font, PurchaseOrderService.OrderView order) {
        this.document = document; this.font = font; this.order = order;
    }
    static byte[] render(PurchaseOrderService.OrderView order, Path fontPath) {
        if (!Files.isRegularFile(fontPath)) throw new IllegalStateException("Thiếu font Unicode cho PDF; cấu hình APP_PDF_FONT_PATH");
        try (PDDocument document = new PDDocument(); var input = Files.newInputStream(fontPath); var output = new ByteArrayOutputStream()) {
            var renderer = new PurchaseOrderPdfRenderer(document, PDType0Font.load(document, input), order);
            renderer.draw(); document.save(output); return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("Không thể tạo PDF đơn mua", exception); }
    }
    private void draw() throws IOException {
        newPage(false);
        for (var item : order.items()) {
            String quantity = item.quantity() == null ? item.quantityText() : item.quantity().stripTrailingZeros().toPlainString();
            row(List.of(Integer.toString(item.lineNo()), safe(item.materialName()), safe(item.specification()), safe(item.unit()),
                    safe(quantity), money(item.unitPrice()), item.lineTotal() == null ? "Không cộng" : money(item.lineTotal())), false);
        }
        ensure(105, false); y -= 14;
        total("Tạm tính", order.subtotal()); total("VAT " + order.vatPercent() + "%", order.taxAmount()); total("Tổng cộng", order.grandTotal());
        if (order.quantityTextLineCount() > 0) paragraph("Lưu ý: " + order.quantityTextLineCount() + " dòng số lượng chữ không được cộng vào tổng tiền.", 9);
        if (order.note() != null) paragraph("Ghi chú: " + order.note(), 9);
        ensure(100, false); y -= 28;
        String[] roles = {"NGƯỜI LẬP", "TRƯỞNG P. KHVT", "TỔNG GIÁM ĐỐC"};
        for (int i = 0; i < roles.length; i++) {
            centered(roles[i], LEFT + WIDTH * i / 3, WIDTH / 3, y, 10, NAVY);
            centered("(Ký, họ tên)", LEFT + WIDTH * i / 3, WIDTH / 3, y - 16, 8, Color.GRAY);
        }
        if (order.preparedBy() != null) centered(order.preparedBy(), LEFT, WIDTH / 3, y - 50, 9, Color.BLACK);
        stream.close();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            try (var footer = new PDPageContentStream(document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                footer.beginText(); footer.setFont(font, 8); footer.newLineAtOffset(LEFT, 25);
                footer.showText(order.poNumber() + " · Phiên bản " + order.revision() + " · Trang " + (i + 1) + "/" + document.getNumberOfPages()); footer.endText();
            }
        }
    }
    private void newPage(boolean continuation) throws IOException {
        boolean first = document.getNumberOfPages() == 0;
        if (stream != null) stream.close();
        var page = new PDPage(PDRectangle.A4); document.addPage(page);
        stream = new PDPageContentStream(document, page); y = 797;
        if (first) {
            centered("TẬP ĐOÀN HÓA CHẤT VIỆT NAM", LEFT, WIDTH, y, 9, Color.GRAY); y -= 20;
            centered("CÔNG TY CỔ PHẦN PHÂN BÓN VÀ HÓA CHẤT CẦN THƠ", LEFT, WIDTH, y, 11, NAVY); y -= 16;
            centered("KCN Trà Nóc 1, Phường Thới An Đông, TP Cần Thơ | ĐT: (0292) 3.841.599 | www.cfccobay.com", LEFT, WIDTH, y, 7, Color.GRAY); y -= 12;
            stream.setStrokingColor(NAVY); stream.setLineWidth(2); stream.moveTo(LEFT, y); stream.lineTo(LEFT + WIDTH, y); stream.stroke(); y -= 30;
            centered("ĐƠN ĐẶT HÀNG", LEFT, WIDTH, y, 18, NAVY); y -= 22;
            centered("Số PO: " + order.poNumber() + "    Ngày: " + (order.orderDate() == null ? "Chưa xác định" : order.orderDate()), LEFT, WIDTH, y, 10, Color.BLACK); y -= 28;
            stream.setNonStrokingColor(NAVY); stream.addRect(LEFT, y - 5, WIDTH, 21); stream.fill();
            text("THÔNG TIN NHÀ CUNG CẤP", LEFT + 8, y + 2, 9, Color.WHITE); y -= 23;
            paragraph("Bên bán: " + order.supplierName(), 10);
            paragraph("Địa chỉ: " + safe(order.supplierAddress()), 9);
            paragraph("Loại tiền: " + order.currency() + "    VAT: " + order.vatPercent() + "%", 9); y -= 8;
        } else {
            text("ĐƠN ĐẶT HÀNG " + order.poNumber() + " (tiếp)", LEFT, y, 11, NAVY); y -= 25;
        }
        if (first || continuation) row(List.of("STT", "TÊN HÀNG / VẬT TƯ", "QUY CÁCH / MÔ TẢ", "ĐVT", "SỐ LƯỢNG", "ĐƠN GIÁ CHƯA VAT", "THÀNH TIỀN"), true);
    }
    private void ensure(float height, boolean table) throws IOException { if (y - height < 55) newPage(table); }
    private void row(List<String> cells, boolean header) throws IOException {
        List<List<String>> wrapped = new ArrayList<>(); int count = 1;
        for (int i = 0; i < cells.size(); i++) { var lines = wrap(cells.get(i), COLUMNS[i] - 8, header ? 8 : 8.5f); wrapped.add(lines); count = Math.max(count, lines.size()); }
        float height = count * 11 + 12;
        if (!header) ensure(height, true);
        float x = LEFT;
        for (int i = 0; i < cells.size(); i++) {
            if (header) { stream.setNonStrokingColor(PALE); stream.addRect(x, y - height, COLUMNS[i], height); stream.fill(); }
            stream.setStrokingColor(new Color(160, 170, 180)); stream.setLineWidth(.5f); stream.addRect(x, y - height, COLUMNS[i], height); stream.stroke();
            float lineY = y - 12;
            for (String line : wrapped.get(i)) { text(line, x + 4, lineY, header ? 8 : 8.5f, Color.BLACK); lineY -= 11; }
            x += COLUMNS[i];
        }
        y -= height;
    }
    private void total(String label, BigDecimal amount) throws IOException {
        text(label, LEFT + 290, y, 10, NAVY);
        String value = money(amount) + " " + order.currency();
        text(value, LEFT + WIDTH - font.getStringWidth(value) * 10 / 1000, y, 10, NAVY); y -= 20;
    }
    private void paragraph(String value, float size) throws IOException {
        for (String line : wrap(value, WIDTH, size)) { ensure(size + 6, false); text(line, LEFT, y, size, Color.BLACK); y -= size + 6; }
    }
    private void centered(String value, float left, float width, float baseline, float size, Color color) throws IOException {
        text(value, left + Math.max(0, (width - font.getStringWidth(safe(value)) * size / 1000) / 2), baseline, size, color);
    }
    private void text(String value, float x, float baseline, float size, Color color) throws IOException {
        stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color); stream.newLineAtOffset(x, baseline); stream.showText(safe(value)); stream.endText();
    }
    private List<String> wrap(String value, float width, float size) throws IOException {
        List<String> result = new ArrayList<>(); StringBuilder line = new StringBuilder();
        for (String word : safe(value).trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringWidth(candidate) * size / 1000 > width) { result.add(line.toString()); line.setLength(0); }
            if (font.getStringWidth(word) * size / 1000 > width) {
                for (int codePoint : word.codePoints().toArray()) {
                    String next = new String(Character.toChars(codePoint));
                    if (!line.isEmpty() && font.getStringWidth(line + next) * size / 1000 > width) { result.add(line.toString()); line.setLength(0); }
                    line.append(next);
                }
            } else { if (!line.isEmpty()) line.append(' '); line.append(word); }
        }
        if (!line.isEmpty() || result.isEmpty()) result.add(line.toString());
        return result;
    }
    private String money(BigDecimal value) { return value == null ? "Chưa xác định" : value.setScale(order.currency() == CurrencyCode.VND ? 0 : 2, RoundingMode.HALF_UP).toPlainString(); }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("[\\p{Cc}]", " "); }
}
