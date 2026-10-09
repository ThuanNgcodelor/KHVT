package com.booking.system.hr;

import com.booking.system.hr.entity.HrPayrollImportRow;
import com.booking.system.hr.service.HrPayrollPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class HrPayrollPdfRendererTest {
    @Test
    void createsOnePageVietnamesePdfWithStructuredTotals() throws Exception {
        HrPayrollImportRow row = new HrPayrollImportRow();
        row.setEmployeeCode("D042"); row.setEmployeeName("Võ Nghĩa Hòa");
        row.setPayloadJson("{\"stk\":\"107004757004\",\"cong\":26,\"tienLuong\":14797000,\"tongThu\":1070000,\"bhxh\":880000,\"baoGiat\":100000,\"htkk\":20000,\"doanPhi\":70000,\"nganHangChuyen\":13727000}");

        HrPayrollPdfRenderer.PayrollPdf pdf = HrPayrollPdfRenderer.render(row, "2026-07", false);

        assertThat(pdf.fileName()).isEqualTo("Phieu_luong_2026_07_D042.pdf");
        assertThat(pdf.bytes()).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var document = Loader.loadPDF(new ByteArrayInputStream(pdf.bytes()).readAllBytes())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String content = new PDFTextStripper().getText(document);
            assertThat(content).contains("PHIẾU LƯƠNG", "Võ Nghĩa Hòa", "KHOẢN THU TRONG LƯƠNG", "13.727.000 đ");
            assertThat(content).doesNotContain("B giặt", "BHXH 10,5%", "HTKK");
            assertThat(content).doesNotContain("Số ca đêm");
        }
    }

    @Test
    void rendersNightShiftWhenPresentInPayload() throws Exception {
        HrPayrollImportRow row = new HrPayrollImportRow();
        row.setEmployeeCode("D104"); row.setEmployeeName("Nguyễn Chi Thanh");
        row.setPayloadJson("{\"stk\":\"105003628440\",\"cong\":26,\"soCaDem\":10,\"tienLuong\":15185000,\"tongThu\":844000,\"nganHangChuyen\":14341000}");

        HrPayrollPdfRenderer.PayrollPdf pdf = HrPayrollPdfRenderer.render(row, "2027-08", false);

        try (var document = Loader.loadPDF(new ByteArrayInputStream(pdf.bytes()).readAllBytes())) {
            String content = new PDFTextStripper().getText(document);
            assertThat(content).contains("Số ca đêm", "10", "15.185.000 đ", "844.000 đ");
        }
    }
}
