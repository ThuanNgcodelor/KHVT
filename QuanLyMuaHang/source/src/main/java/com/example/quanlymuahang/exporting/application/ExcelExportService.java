package com.example.quanlymuahang.exporting.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.pricing.application.PricingService;
import com.example.quanlymuahang.procurement.application.PurchaseOrderService;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ExcelExportService {
    private final PricingService pricing;
    private final PurchaseOrderService purchaseOrders;
    public ExcelExportService(PricingService pricing, PurchaseOrderService purchaseOrders) { this.pricing = pricing; this.purchaseOrders = purchaseOrders; }

    public ExportFile exportPriceHistory(String query, CurrencyCode currency) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Lich su gia");
            CellStyle header = headerStyle(workbook);
            addRow(sheet, 0, header, "Ngày mua", "Tên hàng/vật tư", "Mã hàng", "ĐVT", "Số lượng", "Đơn giá", "Loại tiền", "Cơ sở tiền tệ", "Nhà cung cấp", "Mã NCC", "Nguồn", "Sheet nguồn");
            int rowNumber = 1;
            boolean truncated = false;
            for (int pageNumber = 0; pageNumber < 50; pageNumber++) {
                Page<PricingService.PriceView> page = pricing.search(query, currency, PageRequest.of(pageNumber, 1000));
                for (PricingService.PriceView item : page.getContent()) {
                    if (rowNumber > 50_000) { truncated = true; break; }
                    Row row = sheet.createRow(rowNumber++);
                    cell(row, 0, item.purchaseDate() == null ? "" : item.purchaseDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    cell(row, 1, item.materialName()); cell(row, 2, item.materialCode()); cell(row, 3, item.unit());
                    cell(row, 4, item.quantity() == null ? safe(item.quantityText()) : item.quantity().stripTrailingZeros().toPlainString());
                    cell(row, 5, item.unitPrice() == null ? "" : item.unitPrice().toPlainString()); cell(row, 6, item.currency().name());
                    cell(row, 7, item.currencyBasis()); cell(row, 8, item.supplierName()); cell(row, 9, item.supplierCode());
                    cell(row, 10, item.source()); cell(row, 11, item.sourceSheet());
                }
                if (truncated || !page.hasNext()) break;
                if (pageNumber == 49) truncated = true;
            }
            sheet.createFreezePane(0, 1); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowNumber - 1), 0, 11));
            workbook.write(output);
            if (!workbook.dispose()) throw new IllegalStateException("Không dọn được file tạm XLSX");
            return new ExportFile("lich-su-gia.xlsx", output.toByteArray(), truncated);
        } catch (IOException exception) { throw new IllegalStateException("Không thể tạo file Excel", exception); }
    }

    public ExportFile exportPurchaseOrder(long id) {
        PurchaseOrderService.OrderView order = purchaseOrders.get(id);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Don mua");
            CellStyle header = headerStyle(workbook);
            cell(sheet.createRow(0), 0, "Số PO"); cell(sheet.getRow(0), 1, order.poNumber());
            cell(sheet.createRow(1), 0, "Ngày"); cell(sheet.getRow(1), 1, order.orderDate() == null ? "" : order.orderDate().toString());
            cell(sheet.createRow(2), 0, "Nhà cung cấp"); cell(sheet.getRow(2), 1, order.supplierName());
            cell(sheet.createRow(3), 0, "Địa chỉ"); cell(sheet.getRow(3), 1, order.supplierAddress());
            cell(sheet.createRow(4), 0, "Tiền tệ"); cell(sheet.getRow(4), 1, order.currency().name());
            cell(sheet.createRow(5), 0, "VAT"); cell(sheet.getRow(5), 1, order.vatPercent() == null ? "Chưa xác định" : order.vatPercent().toPlainString());
            cell(sheet.createRow(6), 0, "Ghi chú"); cell(sheet.getRow(6), 1, order.note());
            addRow(sheet, 8, header, "STT", "Mã hàng", "Tên hàng/vật tư", "Quy cách", "ĐVT", "Số lượng", "Đơn giá", "Thành tiền");
            int rowNumber = 9;
            for (PurchaseOrderService.OrderItemView item : order.items()) {
                Row row = sheet.createRow(rowNumber++);
                cell(row, 0, Integer.toString(item.lineNo())); cell(row, 1, item.materialCode()); cell(row, 2, item.materialName());
                cell(row, 3, item.specification()); cell(row, 4, item.unit());
                cell(row, 5, item.quantity() == null ? safe(item.quantityText()) : item.quantity().stripTrailingZeros().toPlainString());
                cell(row, 6, item.unitPrice().toPlainString()); cell(row, 7, item.lineTotal() == null ? "Không cộng tự động" : item.lineTotal().toPlainString());
            }
            int totalRow = rowNumber + 1;
            cell(sheet.createRow(totalRow), 6, "Tạm tính"); cell(sheet.getRow(totalRow), 7, order.subtotal() == null ? "Chưa xác định" : order.subtotal().toPlainString());
            cell(sheet.createRow(totalRow + 1), 6, "VAT"); cell(sheet.getRow(totalRow + 1), 7, order.taxAmount() == null ? "Chưa xác định" : order.taxAmount().toPlainString());
            cell(sheet.createRow(totalRow + 2), 6, "Tổng cộng"); cell(sheet.getRow(totalRow + 2), 7, order.grandTotal() == null ? "Chưa xác định" : order.grandTotal().toPlainString());
            sheet.createFreezePane(0, 9); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(8, Math.max(8, rowNumber - 1), 0, 7));
            workbook.write(output); workbook.dispose();
            return new ExportFile("don-hang-" + order.id() + "-r" + order.revision() + ".xlsx", output.toByteArray(), false);
        } catch (IOException exception) { throw new IllegalStateException("Không thể tạo file Excel", exception); }
    }

    private static CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle(); Font font = workbook.createFont(); font.setBold(true);
        style.setFont(font); style.setWrapText(true); return style;
    }
    private static void addRow(Sheet sheet, int number, CellStyle style, String... values) {
        Row row = sheet.createRow(number);
        for (int i = 0; i < values.length; i++) { row.createCell(i).setCellValue(values[i]); row.getCell(i).setCellStyle(style); }
    }
    private static void cell(Row row, int col, String value) { row.createCell(col).setCellValue(safe(value)); }
    private static String safe(String value) { return value == null ? "" : value; }
    public record ExportFile(String fileName, byte[] content, boolean truncated) {}
}
