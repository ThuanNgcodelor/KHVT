package com.example.quanlymuahang.procurement.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.purchaseorder.GeneratedDocument;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrder;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderItem;
import com.example.quanlymuahang.repository.*;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Optional;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseOrderDocumentTest {
    @TempDir Path temp;
    private final PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
    private final GeneratedDocumentRepository documents = mock(GeneratedDocumentRepository.class);
    private final HistoricalPurchaseRepository history = mock(HistoricalPurchaseRepository.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private PurchaseOrderService service() {
        return new PurchaseOrderService(orders, mock(SupplierRepository.class), mock(MaterialRepository.class),
                mock(PurchaseOrderRevisionRepository.class), documents, history, audit, mock(JdbcTemplate.class),
                new ObjectMapper(), temp.toString(), temp.resolve("font.ttf").toString(), "Asia/Ho_Chi_Minh");
    }
    private PurchaseOrder order() {
        var order = new PurchaseOrder("PO-TEST-01", LocalDate.of(2026, 10, 10), "Nhà cung cấp tổng hợp");
        ReflectionTestUtils.setField(order, "id", 7L);
        order.setCurrency(CurrencyCode.VND); order.setVatPercent(new BigDecimal("8"));
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        when(orders.findLockedById(7L)).thenReturn(Optional.of(order));
        return order;
    }
    @Test void downloadUnissuedDoesNotIssueOrWriteHistory() {
        var order = order();
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().pdf(7L, 1L)).isInstanceOf(ApiException.class).hasMessageContaining("chưa có PDF");
        verifyNoInteractions(history, audit); verify(documents, never()).save(any());
        assertThat(order.getStatus().name()).isEqualTo("DRAFT");
    }
    @Test void downloadsStoredBytesWithoutChangingCancelledOrder() throws Exception {
        var order = order(); order.cancel("Test cancellation");
        var file = temp.resolve("purchase-orders/7/r1.pdf"); Files.createDirectories(file.getParent()); Files.write(file, new byte[]{1,2,3});
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF"))
                .thenReturn(Optional.of(new GeneratedDocument(7L, order.getRevision(), "PO_PDF", "test.pdf", "purchase-orders/7/r1.pdf", "application/pdf", "test-checksum", 1L)));
        assertThat(service().pdf(7L, 1L).content()).containsExactly(1,2,3);
        verifyNoInteractions(history, audit); assertThat(order.getStatus().name()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service().issue(7L, 1L)).isInstanceOf(ApiException.class).hasMessageContaining("hủy");
    }
    @Test void unicodeTemplateKeepsTotalsTextQuantityAndRepeatsTableAcrossPages() throws Exception {
        String configured = System.getenv("QMH_TEST_PDF_FONT_PATH");
        Path font = configured == null ? Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf") : Path.of(configured);
        assumeTrue(Files.isRegularFile(font), "Set QMH_TEST_PDF_FONT_PATH to a Unicode font for PDF rendering test");
        var order = order(); order.setPreparedBy("Người lập thử nghiệm");
        for (int i=0;i<50;i++) { var item = new PurchaseOrderItem("Vật tư kiểm thử " + i, "kg", new BigDecimal("2"), new BigDecimal("100")); item.update("0001", "Quy cách thử nghiệm tiếng Việt", new BigDecimal("2"), null); order.addItem(item); }
        var text = new PurchaseOrderItem("Vật tư qua cân", "kg", null, new BigDecimal("100")); text.update(null, "Thử nghiệm", null, "Qua cân thực tế"); order.addItem(text);
        byte[] bytes = PurchaseOrderPdfRenderer.render(PurchaseOrderService.OrderView.from(order), font);
        try (var pdf = Loader.loadPDF(bytes)) {
            String extracted = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(extracted).contains("PHÂN BÓN VÀ HÓA CHẤT CẦN THƠ", "ĐƠN ĐẶT HÀNG", "Qua cân thực tế", "TRƯỞNG P. KHVT", "TỔNG GIÁM ĐỐC", "10800");
            var imagePath = Path.of("target/runtime/pdf-template-test.png"); Files.createDirectories(imagePath.getParent());
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 100), "png", imagePath.toFile());
        }
    }
}
