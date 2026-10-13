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
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V4")).thenReturn(Optional.empty());
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V3")).thenReturn(Optional.empty());
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V2")).thenReturn(Optional.empty());
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF"))
                .thenReturn(Optional.of(new GeneratedDocument(7L, order.getRevision(), "PO_PDF", "test.pdf", "purchase-orders/7/r1.pdf", "application/pdf", "test-checksum", 1L)));
        assertThat(service().pdf(7L, 1L).content()).containsExactly(1,2,3);
        verifyNoInteractions(history, audit); assertThat(order.getStatus().name()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> service().issue(7L, 1L)).isInstanceOf(ApiException.class).hasMessageContaining("hủy");
    }
    @Test void downloadsV3WhenNoV4ArtifactExists() throws Exception {
        var order = order();
        var file = temp.resolve("purchase-orders/7/r1-v3.pdf"); Files.createDirectories(file.getParent()); Files.write(file, new byte[]{3, 3, 3});
        var oldV3 = new GeneratedDocument(7L, order.getRevision(), "PO_PDF_V3", "previous-template.pdf", "purchase-orders/7/r1-v3.pdf", "application/pdf", "old-v3-checksum", 1L);
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V4")).thenReturn(Optional.empty());
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V3")).thenReturn(Optional.of(oldV3));
        assertThat(service().pdf(7L, 1L).content()).containsExactly(3, 3, 3);
    }
    @Test void downloadsV2WhenNoV4OrV3ArtifactExists() throws Exception {
        var order = order();
        var file = temp.resolve("purchase-orders/7/r1-v2.pdf"); Files.createDirectories(file.getParent()); Files.write(file, new byte[]{2, 2, 2});
        var oldV2 = new GeneratedDocument(7L, order.getRevision(), "PO_PDF_V2", "older-template.pdf", "purchase-orders/7/r1-v2.pdf", "application/pdf", "old-v2-checksum", 1L);
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V4")).thenReturn(Optional.empty());
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V3")).thenReturn(Optional.empty());
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V2")).thenReturn(Optional.of(oldV2));
        assertThat(service().pdf(7L, 1L).content()).containsExactly(2, 2, 2);
    }
    @Test void reissueCreatesVersionedCurrentTemplateAndKeepsLegacyArtifact() throws Exception {
        var order = order();
        var legacy = new GeneratedDocument(7L, order.getRevision(), "PO_PDF", "legacy.pdf", "purchase-orders/7/r1.pdf", "application/pdf", "legacy-checksum", 1L);
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V4")).thenReturn(Optional.empty());
        var oldV2 = new GeneratedDocument(7L, order.getRevision(), "PO_PDF_V2", "older-template.pdf", "purchase-orders/7/r1-v2.pdf", "application/pdf", "old-v2-checksum", 1L);
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V2")).thenReturn(Optional.of(oldV2));
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF")).thenReturn(Optional.of(legacy));
        try (var renderer = mockStatic(PurchaseOrderPdfRenderer.class)) {
            renderer.when(() -> PurchaseOrderPdfRenderer.render(any(PurchaseOrderService.OrderView.class), any(Path.class)))
                    .thenReturn(new byte[]{4, 5, 6});
            service().issue(7L, 1L);
        }
        var saved = org.mockito.ArgumentCaptor.forClass(GeneratedDocument.class);
        verify(documents).save(saved.capture());
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "documentType")).isEqualTo("PO_PDF_V4");
        assertThat(saved.getValue().getContentType()).isEqualTo("application/pdf");
        assertThat(saved.getValue().getStorageKey()).isEqualTo("purchase-orders/7/r1-v4.pdf");
        when(documents.findByPurchaseOrderIdAndRevisionAndDocumentType(7L, order.getRevision(), "PO_PDF_V4"))
                .thenReturn(Optional.of(saved.getValue()));
        assertThat(service().pdf(7L, 1L).content()).containsExactly(4, 5, 6);
        verify(documents, never()).delete(oldV2);
        verify(documents, never()).delete(legacy);
    }
    @Test void supplierPdfHasSixColumnsNoTotalsVietnameseDateAndRepeatsTableAcrossPages() throws Exception {
        String configured = System.getenv("QMH_TEST_PDF_FONT_PATH");
        Path font = configured == null ? Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf") : Path.of(configured);
        assumeTrue(Files.isRegularFile(font), "Set QMH_TEST_PDF_FONT_PATH to a Unicode font for PDF rendering test");
        var order = order(); order.setPreparedBy("Người lập thử nghiệm"); order.setNote("Ghi chú kiểm thử");
        for (int i=0;i<50;i++) { var item = new PurchaseOrderItem("Vật tư kiểm thử " + i, "kg", new BigDecimal("2"), new BigDecimal("100")); item.update("0001", "Quy cách thử nghiệm tiếng Việt", new BigDecimal("2"), null); order.addItem(item); }
        var text = new PurchaseOrderItem("Vật tư qua cân", "kg", null, new BigDecimal("100")); text.update(null, "Thử nghiệm", null, "Qua cân thực tế"); order.addItem(text);
        byte[] bytes = PurchaseOrderPdfRenderer.render(PurchaseOrderService.OrderView.from(order), font);
        try (var pdf = Loader.loadPDF(bytes)) {
            String extracted = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(extracted).contains("PHÂN BÓN VÀ HÓA CHẤT CẦN THƠ", "ĐƠN ĐẶT HÀNG", "Ngày: 10/10/2026", "Qua cân thực tế", "TRƯỞNG P. KHVT", "TỔNG GIÁM ĐỐC", "SỐ LƯỢNG", "ĐƠN GIÁ CHƯA VAT");
            assertThat(extracted).contains("2,00");
            assertThat(extracted).doesNotContain("THÀNH TIỀN", "Tạm tính", "Tổng cộng", "VAT 8%", "Loại tiền");
            assertThat(extracted).contains("STT", "TÊN HÀNG");
            assertThat(extracted.indexOf("Ghi chú kiểm thử")).isLessThan(extracted.indexOf("STT"));
        }
    }
    @Test void singleLinePdfUsesReferenceStyleAndProducesReviewArtifact() throws Exception {
        String configured = System.getenv("QMH_TEST_PDF_FONT_PATH");
        Path font = configured == null ? Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf") : Path.of(configured);
        assumeTrue(Files.isRegularFile(font), "Set QMH_TEST_PDF_FONT_PATH to a Unicode font for PDF rendering test");
        var order = order(); order.setPreparedBy("Người lập thử nghiệm"); order.setNote("Ghi chú kiểm thử");
        order.addItem(new PurchaseOrderItem("Chỉ may bao công nghiệp (PE 20 một màu - Cuộn lớn)", "Kg +/-10%", new BigDecimal("50"), new BigDecimal("72000")));
        order.getItems().get(0).update("MVT-01", "PE 20/9 Màu đỏ", new BigDecimal("50"), null);
        byte[] bytes = PurchaseOrderPdfRenderer.render(PurchaseOrderService.OrderView.from(order), font);
        var pdfPath = Path.of("target/runtime/pdf-template-test.pdf"); Files.createDirectories(pdfPath.getParent()); Files.write(pdfPath, bytes);
        try (var pdf = Loader.loadPDF(bytes)) {
            String extracted = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(extracted).contains("Số PO: PO-TEST-01", "Ngày: 10/10/2026", "Bên Bán:", "Địa Chỉ:", "Ghi chú:",
                    "Chỉ may bao công nghiệp", "PE 20/9 Màu đỏ", "ĐVT", "SỐ LƯỢNG", "ĐƠN GIÁ CHƯA VAT (VND)", "NGƯỜI LẬP", "TRƯỞNG P. KHVT");
            assertThat(extracted).contains("50,00");
            assertThat(extracted).doesNotContain("THÀNH TIỀN", "Tạm tính", "Tổng cộng", "VAT 8%");
            var imagePath = Path.of("target/runtime/pdf-template-test.png");
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 120), "png", imagePath.toFile());
        }
    }
}
