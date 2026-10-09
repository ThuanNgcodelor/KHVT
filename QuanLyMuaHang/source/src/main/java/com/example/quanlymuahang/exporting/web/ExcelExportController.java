package com.example.quanlymuahang.exporting.web;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.exporting.application.ExcelExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exports")
public class ExcelExportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ExcelExportService exports;
    public ExcelExportController(ExcelExportService exports) { this.exports = exports; }

    @GetMapping("/prices.xlsx")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PRICE_READ')")
    public ResponseEntity<byte[]> prices(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) CurrencyCode currency) {
        ExcelExportService.ExportFile file = exports.exportPriceHistory(q, currency);
        return response(file);
    }

    @GetMapping("/purchase-orders/{id}.xlsx")
    @PreAuthorize("hasAuthority('*') or hasAuthority('PO_READ')")
    public ResponseEntity<byte[]> purchaseOrder(@PathVariable long id) { return response(exports.exportPurchaseOrder(id)); }

    private ResponseEntity<byte[]> response(ExcelExportService.ExportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        if (file.truncated()) headers.set("X-Export-Truncated", "true");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
