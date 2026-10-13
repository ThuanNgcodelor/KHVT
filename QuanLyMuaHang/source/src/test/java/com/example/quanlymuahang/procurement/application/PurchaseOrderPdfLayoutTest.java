package com.example.quanlymuahang.procurement.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrder;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderItem;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Inspect rendered PDF coordinates, not just the strings passed to the renderer. */
class PurchaseOrderPdfLayoutTest {
    @Test void oneAndTwoLineCellsShareTheSameVerticalCenter() throws Exception {
        var order = order("PO-261012-02", "CÔNG TY TNHH MỘT THÀNH VIÊN VÂN ANH",
                "50/38E, Trần Hoàng Na, Phường Tân An, TP Cần Thơ, Việt Nam.");
        order.setNote("Hỗ trợ sản xuất Bột giặt Bảo Phát");
        add(order, "Chỉ may bao công nghiệp (PE 20 một màu - Cuộn lớn)", "PE 20/9 Màu đỏ", "Kg +/-10%", "50", "72000");
        try (var pdf = render(order, "po-02-aligned")) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertTableGeometry(pdf);
            assertThat(new PDFTextStripper().getText(pdf)).contains("50,00", "72.000");
        }
    }

    @Test void renderLiveR2ToImage() throws Exception {
        Path r2 = Path.of("data/files/purchase-orders/293/r2-v6.pdf");
        if (Files.exists(r2)) {
            try (var doc = Loader.loadPDF(Files.readAllBytes(r2))) {
                ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 150), "png",
                        Path.of("target/runtime/live_r2.png").toFile());
            }
        }
    }

    @Test void longSpecificationsAndShortCellsStayCenteredWithoutSpillingIntoTheNextRow() throws Exception {
        var order = order("PO-261012-06", "CÔNG TY CỔ PHẦN TRUNG ĐÔNG",
                "Cụm công nghiệp Tam Phước 1, Phường Tam Phước, Thành phố Biên Hoà, Tỉnh Đồng Nai, Việt Nam");
        order.setNote("Theo định mức tồn min");
        add(order, "Bao Bột giặt Bảo Phát 25kg", "Bao trắng, trắng ngoài, flexo 1 mặt Kích thước (55*85+/-1)cm Định lượng (110+/-3)g", "Cái", "3000", "5700");
        add(order, "Bản in bao BG Bảo Phát", "Làm mới", "Cái", "1", "2500000");
        add(order, "Bao đóng BG 25kg", "Bao trắng, trắng ngoài, không in Kích thước (50*80+/-1)cm Định lượng (82+/-3)g", "Cái", "3000", "5700");
        try (var pdf = render(order, "po-06-aligned")) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertTableGeometry(pdf);
        }
    }

    @Test void bottleSpecificationsAndRepeatedPageHeadersHaveConsistentAlignment() throws Exception {
        var order = order("PO-LAYOUT-MULTIPAGE", "NHÀ CUNG CẤP KIỂM THỬ", "Địa chỉ kiểm thử");
        for (int i = 0; i < 24; i++) {
            add(order, "Chai nước giặt 1 lít", "Nhựa HDPE màu trắng 65g ± 2.5", "Chai", "10000", "4730");
            add(order, "Nắp nước giặt 1 lít", "Nhựa PP màu tím 15g ±0.5", "Cái", "5000", "1840");
        }
        try (var pdf = render(order, "po-multiline-aligned")) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertTableGeometry(pdf);
            var stripper = new PDFTextStripper();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page); stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                if (text.contains("Chai nước giặt")) assertThat(text).contains("STT", "SỐ LƯỢNG");
            }
        }
    }

    private static PurchaseOrder order(String number, String supplier, String address) {
        var order = new PurchaseOrder(number, LocalDate.of(2026, 10, 12), supplier);
        order.setSupplierAddressSnapshot(address);
        order.setCurrency(CurrencyCode.VND); order.setVatPercent(new BigDecimal("8"));
        return order;
    }

    private static void add(PurchaseOrder order, String name, String spec, String unit, String quantity, String price) {
        var item = new PurchaseOrderItem(name, unit, new BigDecimal(quantity), new BigDecimal(price));
        item.update(null, spec, new BigDecimal(quantity), null); order.addItem(item);
    }

    private static PDDocument render(PurchaseOrder order, String filename) throws Exception {
        String configured = System.getenv("QMH_TEST_PDF_FONT_PATH");
        Path font = configured != null ? Path.of(configured)
                : Files.isRegularFile(Path.of("C:/Windows/Fonts/arial.ttf")) ? Path.of("C:/Windows/Fonts/arial.ttf")
                : Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
        assumeTrue(Files.isRegularFile(font), "Set QMH_TEST_PDF_FONT_PATH to a Unicode font");
        byte[] bytes = PurchaseOrderPdfRenderer.render(PurchaseOrderService.OrderView.from(order), font);
        Path directory = Path.of("target/runtime/pdf-alignment-v6"); Files.createDirectories(directory);
        Files.write(directory.resolve(filename + ".pdf"), bytes);
        var pdf = Loader.loadPDF(bytes);
        for (int page = 0; page < pdf.getNumberOfPages(); page++) {
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(page, 120), "png",
                    directory.resolve(filename + "-page-" + (page + 1) + ".png").toFile());
        }
        return pdf;
    }

    private record Cell(float left, float bottom, float width, float height) {}

    private static List<Cell> cells(PDPage page) throws IOException {
        var tokens = new PDFStreamParser(page).parse();
        var cells = new LinkedHashSet<Cell>();
        for (int i = 4; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator op && op.getName().equals("re")) {
                cells.add(new Cell(((COSNumber) tokens.get(i - 4)).floatValue(),
                        ((COSNumber) tokens.get(i - 3)).floatValue(),
                        ((COSNumber) tokens.get(i - 2)).floatValue(),
                        ((COSNumber) tokens.get(i - 1)).floatValue()));
            }
        }
        return cells.stream().sorted(Comparator.comparing(Cell::bottom).reversed().thenComparing(Cell::left)).toList();
    }

    private static void assertTableGeometry(PDDocument pdf) throws IOException {
        for (int p = 0; p < pdf.getNumberOfPages(); p++) {
            var positions = new ArrayList<TextPosition>();
            var stripper = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition position) {
                    if (!position.getUnicode().isBlank()) positions.add(position);
                    super.processTextPosition(position);
                }
            };
            stripper.setStartPage(p + 1); stripper.setEndPage(p + 1); stripper.getText(pdf);
            var page = pdf.getPage(p);
            var tableCells = cells(page);
            assertThat(tableCells).as("page %s must not contain only signatures", p + 1).isNotEmpty();
            assertThat(tableCells.size() % 6).isZero();
            for (int c = 0; c < tableCells.size(); c++) {
                var cell = tableCells.get(c);
                float top = page.getMediaBox().getHeight() - cell.bottom() - cell.height();
                float bottom = top + cell.height();
                var text = positions.stream().filter(t -> t.getXDirAdj() >= cell.left() - .1f
                        && t.getXDirAdj() < cell.left() + cell.width() - .1f
                        && t.getYDirAdj() >= top && t.getYDirAdj() < bottom).toList();
                assertThat(text).as("page %s cell %s has content", p + 1, c).isNotEmpty();
                double textTop = text.stream().mapToDouble(t -> t.getYDirAdj() - t.getHeightDir()).min().orElseThrow();
                double textBottom = text.stream().mapToDouble(TextPosition::getYDirAdj).max().orElseThrow();
                assertThat(textTop).as("text stays below top border").isGreaterThan(top + 2.0);
                assertThat(textBottom).as("text stays above bottom border").isLessThan(bottom - 2.0);
                assertThat((textTop + textBottom) / 2).as("page %s cell %s vertical center", p + 1, c)
                        .isCloseTo((top + bottom) / 2.0, within(1.5));
                boolean header = top == page.getMediaBox().getHeight()
                        - tableCells.get(0).bottom() - tableCells.get(0).height();
                int column = c % 6;
                var baselines = text.stream().map(TextPosition::getYDirAdj).distinct().toList();
                for (float baseline : baselines) {
                    var line = text.stream().filter(t -> Math.abs(t.getYDirAdj() - baseline) < .01).toList();
                    double left = line.stream().mapToDouble(TextPosition::getXDirAdj).min().orElseThrow();
                    double right = line.stream().mapToDouble(t -> t.getXDirAdj() + t.getWidthDirAdj()).max().orElseThrow();
                    assertThat(left).isGreaterThanOrEqualTo(cell.left() + 2.0);
                    assertThat(right).isLessThanOrEqualTo(cell.left() + cell.width() - 2.0);
                    if (header || column == 0 || column == 2 || column == 3) {
                        assertThat((left + right) / 2).as("page %s column %s horizontal center", p + 1, column)
                                .isCloseTo(cell.left() + cell.width() / 2.0, within(.8));
                    } else if (column >= 4) {
                        assertThat(right).isCloseTo(cell.left() + cell.width() - 5.0, within(.3));
                    }
                }
            }
        }
    }
}
