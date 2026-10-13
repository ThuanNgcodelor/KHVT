package com.example.quanlymuahang.procurement.application;

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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/** Native PDF counterpart of legacy buildPoHtml_; does not evaluate workbook HTML. */
final class PurchaseOrderPdfRenderer {
    private static final float LEFT = 40, WIDTH = 515;
    // Keep the grid inside the A4 content width. Give unit and numeric columns
    // enough room for readable values while preserving a wide material name.
    // Proportions follow PO-261012-02: a wide item name, compact specification,
    // and enough room for quantity and the wrapped unit-price heading.
    private static final float[] COLUMNS = {28, 176, 96, 46, 90, 79};
    private static final Color NAVY = new Color(31, 56, 100);
    private static final Color HEADER_FILL = Color.WHITE;
    private final PDDocument document;
    private final PDType0Font font;
    private final PDType0Font boldFont;
    private final PurchaseOrderService.OrderView order;
    private PDPageContentStream stream;
    private float y;

    private PurchaseOrderPdfRenderer(PDDocument document, PDType0Font font, PDType0Font boldFont, PurchaseOrderService.OrderView order) {
        this.document = document; this.font = font; this.boldFont = boldFont; this.order = order;
    }
    static byte[] render(PurchaseOrderService.OrderView order, Path fontPath) {
        if (!Files.isRegularFile(fontPath)) throw new IllegalStateException("Thiếu font Unicode cho PDF; cấu hình APP_PDF_FONT_PATH");
        try (PDDocument document = new PDDocument(); var input = Files.newInputStream(fontPath); var output = new ByteArrayOutputStream()) {
            var regular = PDType0Font.load(document, input);
            var renderer = new PurchaseOrderPdfRenderer(document, regular, loadBoldFont(document, fontPath, regular), order);
            renderer.draw(); document.save(output); return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("Không thể tạo PDF đơn mua", exception); }
    }
    private void draw() throws IOException {
        newPage(false);
        for (var item : order.items()) {
            String quantity = item.quantity() == null ? item.quantityText() : number(item.quantity(), true);
            row(List.of(Integer.toString(item.lineNo()), safe(item.materialName()), safe(item.specification()), safe(item.unit()),
                    safe(quantity), money(item.unitPrice())), false);
        }
        ensure(100, false); y -= 28;
        String[] roles = {"NGƯỜI LẬP", "TRƯỞNG P. KHVT", "TỔNG GIÁM ĐỐC"};
        for (int i = 0; i < roles.length; i++) {
            centeredBold(roles[i], LEFT + WIDTH * i / 3, WIDTH / 3, y, 10, NAVY);
            centered("(Ký, họ tên)", LEFT + WIDTH * i / 3, WIDTH / 3, y - 16, 8, Color.GRAY);
        }
        if (order.preparedBy() != null) centered(order.preparedBy(), LEFT, WIDTH / 3, y - 50, 9, Color.BLACK);
        stream.close();
    }
    private void newPage(boolean continuation) throws IOException {
        boolean first = document.getNumberOfPages() == 0;
        if (stream != null) stream.close();
        var page = new PDPage(PDRectangle.A4); document.addPage(page);
        stream = new PDPageContentStream(document, page); y = 780;
        if (first) {
            centered("TẬP ĐOÀN HÓA CHẤT VIỆT NAM", LEFT, WIDTH, y, 8, Color.GRAY); y -= 13;
            centeredBold("CÔNG TY CỔ PHẦN PHÂN BÓN VÀ HÓA CHẤT CẦN THƠ", LEFT, WIDTH, y, 11, NAVY); y -= 13;
            centered("KCN Trà Nóc 1, Phường Thới An Đông, TP Cần Thơ | ĐT: (0292) 3.841.599 | www.cfccobay.com", LEFT, WIDTH, y, 7, Color.BLACK); y -= 9;
            stream.setStrokingColor(NAVY); stream.setLineWidth(2); stream.moveTo(LEFT, y); stream.lineTo(LEFT + WIDTH, y); stream.stroke(); y -= 18;
            centeredBold("ĐƠN ĐẶT HÀNG", LEFT, WIDTH, y, 16, Color.BLACK); y -= 17;
            poMetadata(); y -= 22;
            text("THÔNG TIN NHÀ CUNG CẤP", LEFT + 8, y, 8.5f, Color.GRAY); y -= 15;
            supplierField("Bên Bán:", order.supplierName());
            supplierField("Địa Chỉ:", safe(order.supplierAddress()));
            if (order.note() != null && !order.note().isBlank()) supplierField("Ghi chú:", order.note());
            y -= 11;
        } else {
            text("ĐƠN ĐẶT HÀNG " + order.poNumber() + " (tiếp)", LEFT, y, 11, NAVY); y -= 25;
        }
        if (first || continuation) row(List.of("STT", "TÊN HÀNG HÓA / VẬT TƯ", "QUY CÁCH / MÔ TẢ", "ĐVT", "SỐ LƯỢNG", "ĐƠN GIÁ CHƯA VAT (VND)"), true);
    }
    private void ensure(float height, boolean table) throws IOException { if (y - height < 55) newPage(table); }
    private void row(List<String> cells, boolean header) throws IOException {
        List<List<String>> wrapped = new ArrayList<>(); int count = 1;
        for (int i = 0; i < cells.size(); i++) { var lines = wrap(cells.get(i), COLUMNS[i] - 10, header ? 7.5f : 8.5f, header || i == 1); wrapped.add(lines); count = Math.max(count, lines.size()); }
        float lineHeight = header ? 10 : 11;
        float height = Math.max(header ? 28 : 25, count * lineHeight + 12);
        if (!header) ensure(height, true);
        float x = LEFT;
        for (int i = 0; i < cells.size(); i++) {
            if (header) {
                stream.setNonStrokingColor(HEADER_FILL); stream.addRect(x, y - height, COLUMNS[i], height); stream.fill();
            }
            stream.setStrokingColor(header ? NAVY : new Color(145, 155, 165)); stream.setLineWidth(header ? .7f : .5f); stream.addRect(x, y - height, COLUMNS[i], height); stream.stroke();
            float blockHeight = wrapped.get(i).size() * lineHeight;
            float lineY = y - (height + blockHeight) / 2 + lineHeight - (header ? 1 : 2);
            for (String line : wrapped.get(i)) {
                float size = header ? 7.5f : 8.5f;
                boolean emphasize = header || i == 1;
                float textWidth = measure(line, size, emphasize);
                float textX;
                if (header) textX = x + (COLUMNS[i] - textWidth) / 2;
                else if (i == 0 || i == 3) textX = x + (COLUMNS[i] - textWidth) / 2;
                else if (i == 4 || i == 5) textX = x + COLUMNS[i] - 5 - textWidth;
                else textX = x + 5;
                if (emphasize) textBold(line, textX, lineY, size, header ? NAVY : Color.BLACK);
                else text(line, textX, lineY, size, Color.BLACK);
                lineY -= lineHeight;
            }
            x += COLUMNS[i];
        }
        y -= height;
    }
    private void supplierField(String label, String value) throws IOException {
        float labelX = LEFT + 8, valueX = LEFT + 108;
        List<String> lines = wrap(value == null || value.isBlank() ? "—" : value, WIDTH - (valueX - LEFT), 9, false);
        ensure(lines.size() * 13, false);
        textBold(label, labelX, y, 9, Color.BLACK);
        for (String line : lines) { text(line, valueX, y, 9, Color.BLACK); y -= 13; }
    }
    private void poMetadata() throws IOException {
        String prefix = "Số PO: ", number = safe(order.poNumber());
        String suffix = "    Ngày: " + (order.orderDate() == null ? "Chưa xác định" : order.orderDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));
        float total = measure(prefix, 9, false) + measure(number, 9, true) + measure(suffix, 9, false);
        float x = LEFT + (WIDTH - total) / 2;
        text(prefix, x, y, 9, Color.BLACK); x += measure(prefix, 9, false);
        textBold(number, x, y, 9, new Color(46, 116, 181)); x += measure(number, 9, true);
        text(suffix, x, y, 9, Color.BLACK);
    }
    private void centered(String value, float left, float width, float baseline, float size, Color color) throws IOException {
        text(value, left + Math.max(0, (width - font.getStringWidth(safe(value)) * size / 1000) / 2), baseline, size, color);
    }
    private void centeredBold(String value, float left, float width, float baseline, float size, Color color) throws IOException {
        textBold(value, left + Math.max(0, (width - measure(value, size, true)) / 2), baseline, size, color);
    }
    private float measure(String value, float size, boolean bold) throws IOException {
        return (bold ? boldFont : font).getStringWidth(safe(value)) * size / 1000;
    }
    private void text(String value, float x, float baseline, float size, Color color) throws IOException {
        stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color); stream.newLineAtOffset(x, baseline); stream.showText(safe(value)); stream.endText();
    }
    private void textBold(String value, float x, float baseline, float size, Color color) throws IOException {
        stream.beginText(); stream.setFont(boldFont, size); stream.setNonStrokingColor(color); stream.newLineAtOffset(x, baseline); stream.showText(safe(value)); stream.endText();
    }
    private static PDType0Font loadBoldFont(PDDocument document, Path regularPath, PDType0Font fallback) throws IOException {
        String name = regularPath.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();
        if (lower.equals("arial.ttf")) candidates.add("arialbd.ttf");
        if (lower.equals("dejavusans.ttf")) candidates.add("DejaVuSans-Bold.ttf");
        if (lower.endsWith("-regular.ttf")) candidates.add(name.substring(0, name.length() - "Regular.ttf".length()) + "Bold.ttf");
        if (lower.endsWith(".ttf")) candidates.add(name.substring(0, name.length() - 4) + "-Bold.ttf");
        for (String candidate : candidates) {
            Path boldPath = regularPath.resolveSibling(candidate);
            if (Files.isRegularFile(boldPath)) try (var input = Files.newInputStream(boldPath)) { return PDType0Font.load(document, input); }
        }
        return fallback;
    }
    private List<String> wrap(String value, float width, float size, boolean bold) throws IOException {
        List<String> result = new ArrayList<>(); StringBuilder line = new StringBuilder();
        PDType0Font measureFont = bold ? boldFont : font;
        for (String word : safe(value).trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && measureFont.getStringWidth(candidate) * size / 1000 > width) { result.add(line.toString()); line.setLength(0); }
            if (measureFont.getStringWidth(word) * size / 1000 > width) {
                for (int codePoint : word.codePoints().toArray()) {
                    String next = new String(Character.toChars(codePoint));
                    if (!line.isEmpty() && measureFont.getStringWidth(line + next) * size / 1000 > width) { result.add(line.toString()); line.setLength(0); }
                    line.append(next);
                }
            } else { if (!line.isEmpty()) line.append(' '); line.append(word); }
        }
        if (!line.isEmpty() || result.isEmpty()) result.add(line.toString());
        return result;
    }
    private String money(BigDecimal value) { return value == null ? "" : number(value, false); }
    private String number(BigDecimal value, boolean quantity) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN"));
        DecimalFormat format = new DecimalFormat(quantity ? "#,##0.####" : "#,##0.##", symbols);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return format.format(value);
    }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("[\\p{Cc}]", " "); }
}
