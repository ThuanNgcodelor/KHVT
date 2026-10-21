package com.example.quanlymuahang.exporting.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.pricing.application.PricingService;
import com.example.quanlymuahang.procurement.application.PurchaseOrderService;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrder;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderItem;
import com.example.quanlymuahang.domain.supplier.Supplier;
import com.example.quanlymuahang.repository.HistoricalPurchaseRepository;
import com.example.quanlymuahang.repository.PurchaseOrderRepository;
import com.example.quanlymuahang.repository.SupplierRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;

@Service
public class ExcelExportService {
    private final PricingService pricing;
    private final PurchaseOrderService purchaseOrders;
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final HistoricalPurchaseRepository history;

    public ExcelExportService(PricingService pricing, PurchaseOrderService purchaseOrders,
                              PurchaseOrderRepository orders, SupplierRepository suppliers,
                              HistoricalPurchaseRepository history) {
        this.pricing = pricing;
        this.purchaseOrders = purchaseOrders;
        this.orders = orders;
        this.suppliers = suppliers;
        this.history = history;
    }

    public ExportFile exportPriceHistory(String query, CurrencyCode currency) {
        return exportPriceHistory(query, currency, null);
    }

    public ExportFile exportPriceHistory(String query, CurrencyCode currency, MaterialCategory category) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Lich su gia");
            CellStyle header = headerStyle(workbook);
            addRow(sheet, 0, header, "Ngày mua", "Tên hàng/vật tư", "Mã hàng", "ĐVT", "Số lượng", "Đơn giá", "Loại tiền", "Cơ sở tiền tệ", "Nhà cung cấp", "Mã NCC", "Nguồn", "Sheet nguồn");
            int rowNumber = 1;
            boolean truncated = false;
            for (int pageNumber = 0; pageNumber < 50; pageNumber++) {
                Page<PricingService.PriceView> page = pricing.search(query, currency, category, PageRequest.of(pageNumber, 1000));
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
            CellStyle numeric = workbook.createCellStyle(); numeric.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
            cell(sheet.createRow(0), 0, "Số PO"); cell(sheet.getRow(0), 1, order.poNumber());
            cell(sheet.createRow(1), 0, "Ngày"); cell(sheet.getRow(1), 1, order.orderDate() == null ? "" : order.orderDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));
            cell(sheet.createRow(2), 0, "Nhà cung cấp"); cell(sheet.getRow(2), 1, order.supplierName());
            cell(sheet.createRow(3), 0, "Địa chỉ"); cell(sheet.getRow(3), 1, order.supplierAddress());
            cell(sheet.createRow(4), 0, "Tiền tệ"); cell(sheet.getRow(4), 1, order.currency().name());
            cell(sheet.createRow(5), 0, "VAT"); cell(sheet.getRow(5), 1, order.vatPercent() == null ? "Chưa xác định" : order.vatPercent().toPlainString());
            cell(sheet.createRow(6), 0, "Ghi chú"); cell(sheet.getRow(6), 1, order.note());
            addRow(sheet, 8, header, "STT", "Tên hàng/vật tư", "Quy cách", "ĐVT", "Số lượng", "Đơn giá chưa VAT");
            int rowNumber = 9;
            for (PurchaseOrderService.OrderItemView item : order.items()) {
                Row row = sheet.createRow(rowNumber++);
                cell(row, 0, Integer.toString(item.lineNo())); cell(row, 1, item.materialName()); cell(row, 2, item.specification());
                cell(row, 3, item.unit());
                cell(row, 4, item.quantity() == null ? safe(item.quantityText()) : grouped(item.quantity(), true));
                cell(row, 5, grouped(item.unitPrice(), false));
                row.getCell(4).setCellStyle(numeric); row.getCell(5).setCellStyle(numeric);
            }
            sheet.setColumnWidth(0, 8 * 256); sheet.setColumnWidth(1, 38 * 256); sheet.setColumnWidth(2, 34 * 256);
            sheet.setColumnWidth(3, 12 * 256); sheet.setColumnWidth(4, 16 * 256); sheet.setColumnWidth(5, 22 * 256);
            sheet.createFreezePane(0, 9); sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(8, Math.max(8, rowNumber - 1), 0, 5));
            workbook.write(output); workbook.dispose();
            return new ExportFile("don-hang-" + order.id() + "-r" + order.revision() + ".xlsx", output.toByteArray(), false);
        } catch (IOException exception) { throw new IllegalStateException("Không thể tạo file Excel", exception); }
    }

    @Transactional(readOnly = true)
    public ExportFile exportMasterWorkbook() {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);

            CellStyle header = headerStyle(workbook);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle numericStyle = workbook.createCellStyle();
            numericStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // Sheet 1: DON_HANG
            Sheet donHangSheet = workbook.createSheet("DON_HANG");
            addRow(donHangSheet, 0, header, "Ngay", "SoPO", "MaNCC", "NhaCungCap", "MaHang", "TenHang", "DVT",
                    "SoLuong", "DonGia", "NguoiLap", "QuyCach", "GhiChu", "VAT", "DiaChi", "LoaiTien");

            int donHangRowIdx = 1;
            List<PurchaseOrder> poList = orders.findAll(Sort.by(Sort.Direction.ASC, "poNumber").and(Sort.by(Sort.Direction.ASC, "id")));
            for (PurchaseOrder po : poList) {
                String poNumber = po.getPoNumber();
                LocalDate orderDate = po.getOrderDate();
                String maNCC = (po.getSupplier() != null && po.getSupplier().getCode() != null) ? po.getSupplier().getCode() : "";
                String nhaCungCap = (po.getSupplierNameSnapshot() != null) ? po.getSupplierNameSnapshot() : (po.getSupplier() != null ? po.getSupplier().getName() : "");
                String nguoiLap = safe(po.getPreparedBy());
                String ghiChu = safe(po.getNote());
                BigDecimal vat = po.getVatPercent();
                String diaChi = (po.getSupplierAddressSnapshot() != null) ? po.getSupplierAddressSnapshot() : (po.getSupplier() != null ? safe(po.getSupplier().getAddress()) : "");
                String loaiTien = po.getCurrency() != null ? po.getCurrency().name() : "VND";

                List<PurchaseOrderItem> items = po.getItems();
                if (items.isEmpty()) {
                    Row row = donHangSheet.createRow(donHangRowIdx++);
                    setDateCell(row, 0, orderDate, dateStyle);
                    cell(row, 1, poNumber);
                    cell(row, 2, maNCC);
                    cell(row, 3, nhaCungCap);
                    cell(row, 4, "");
                    cell(row, 5, "");
                    cell(row, 6, "");
                    cell(row, 7, "");
                    cell(row, 8, "");
                    cell(row, 9, nguoiLap);
                    cell(row, 10, "");
                    cell(row, 11, ghiChu);
                    setNumericOrEmpty(row, 12, vat, numericStyle);
                    cell(row, 13, diaChi);
                    cell(row, 14, loaiTien);
                } else {
                    for (PurchaseOrderItem item : items) {
                        Row row = donHangSheet.createRow(donHangRowIdx++);
                        setDateCell(row, 0, orderDate, dateStyle);
                        cell(row, 1, poNumber);
                        cell(row, 2, maNCC);
                        cell(row, 3, nhaCungCap);
                        String maHang = item.getMaterialCodeSnapshot() != null ? item.getMaterialCodeSnapshot() : (item.getMaterial() != null ? safe(item.getMaterial().getCode()) : "");
                        cell(row, 4, maHang);
                        cell(row, 5, item.getMaterialName());
                        cell(row, 6, item.getUnit());
                        setQuantityCell(row, 7, item.getQuantity(), item.getQuantityText(), numericStyle);
                        setNumericOrEmpty(row, 8, item.getUnitPrice(), numericStyle);
                        cell(row, 9, nguoiLap);
                        cell(row, 10, item.getSpecification());
                        cell(row, 11, ghiChu);
                        setNumericOrEmpty(row, 12, vat, numericStyle);
                        cell(row, 13, diaChi);
                        cell(row, 14, loaiTien);
                    }
                }
            }
            donHangSheet.createFreezePane(0, 1);

            // Sheet 2: NCC
            Sheet nccSheet = workbook.createSheet("NCC");
            addRow(nccSheet, 0, header, "MaNCC", "TenNCC", "DiaChi");
            int nccRowIdx = 1;
            List<Supplier> supplierList = suppliers.findAll(Sort.by(Sort.Direction.ASC, "id"));
            for (Supplier s : supplierList) {
                Row row = nccSheet.createRow(nccRowIdx++);
                cell(row, 0, s.getCode());
                cell(row, 1, s.getName());
                cell(row, 2, s.getAddress());
            }
            nccSheet.createFreezePane(0, 1);

            // Sheet 3: LICH_SU
            Sheet lichSuSheet = workbook.createSheet("LICH_SU");
            addRow(lichSuSheet, 0, header, "Ngay", "MaNCC", "NhaCungCap", "MaHang", "TenHang", "DVT", "SoLuong", "DonGia");
            int lichSuRowIdx = 1;
            int page = 0;
            while (true) {
                Page<HistoricalPurchase> batch = history.findAll(PageRequest.of(page, 1000, Sort.by(Sort.Direction.ASC, "id")));
                for (HistoricalPurchase hp : batch.getContent()) {
                    Row row = lichSuSheet.createRow(lichSuRowIdx++);
                    setDateCell(row, 0, hp.getPurchaseDate(), dateStyle);
                    String maNCC = hp.getSupplierCodeSnapshot() != null ? hp.getSupplierCodeSnapshot() : (hp.getSupplier() != null ? safe(hp.getSupplier().getCode()) : "");
                    cell(row, 1, maNCC);
                    String ncc = hp.getSupplierSnapshot() != null ? hp.getSupplierSnapshot() : (hp.getSupplier() != null ? safe(hp.getSupplier().getName()) : "");
                    cell(row, 2, ncc);
                    String maHang = hp.getMaterialCodeSnapshot() != null ? hp.getMaterialCodeSnapshot() : (hp.getMaterial() != null ? safe(hp.getMaterial().getCode()) : "");
                    cell(row, 3, maHang);
                    cell(row, 4, hp.getMaterialNameSnapshot());
                    cell(row, 5, hp.getUnit());
                    setQuantityCell(row, 6, hp.getQuantity(), hp.getQuantityText(), numericStyle);
                    setNumericOrEmpty(row, 7, hp.getUnitPrice(), numericStyle);
                }
                if (!batch.hasNext()) break;
                page++;
            }
            lichSuSheet.createFreezePane(0, 1);

            // Sheet 4: CONFIG
            Sheet configSheet = workbook.createSheet("CONFIG");
            Row configRow0 = configSheet.createRow(0);
            configRow0.createCell(0).setCellValue("PO_SEQ");
            configRow0.createCell(1).setCellValue((double) orders.count());

            Row configRow1 = configSheet.createRow(1);
            configRow1.createCell(0).setCellValue("ACCESS_CODE");
            configRow1.createCell(1).setCellValue("KHVT@2026");

            workbook.write(output);
            if (!workbook.dispose()) throw new IllegalStateException("Không dọn được file tạm XLSX");
            return new ExportFile("QUAN_LY_MUA_HANG_KHVT.xlsx", output.toByteArray(), false);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo file Excel", exception);
        }
    }

    private static void setDateCell(Row row, int col, LocalDate date, CellStyle dateStyle) {
        if (date != null) {
            Cell cell = row.createCell(col);
            cell.setCellValue(Date.valueOf(date));
            cell.setCellStyle(dateStyle);
        } else {
            row.createCell(col).setCellValue("");
        }
    }

    private static void setNumericOrEmpty(Row row, int col, BigDecimal value, CellStyle numericStyle) {
        if (value != null) {
            Cell cell = row.createCell(col);
            cell.setCellValue(value.doubleValue());
            if (numericStyle != null) cell.setCellStyle(numericStyle);
        } else {
            row.createCell(col).setCellValue("");
        }
    }

    private static void setQuantityCell(Row row, int col, BigDecimal quantity, String quantityText, CellStyle numericStyle) {
        if (quantity != null) {
            Cell cell = row.createCell(col);
            cell.setCellValue(quantity.doubleValue());
            if (numericStyle != null) cell.setCellStyle(numericStyle);
        } else if (quantityText != null && !quantityText.isBlank()) {
            row.createCell(col).setCellValue(quantityText);
        } else {
            row.createCell(col).setCellValue("");
        }
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
    private static String grouped(BigDecimal value, boolean quantity) {
        DecimalFormat format = new DecimalFormat(quantity ? "#,##0.00##" : "#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN")));
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return format.format(value);
    }
    private static String safe(String value) { return value == null ? "" : value; }
    public record ExportFile(String fileName, byte[] content, boolean truncated) {}
}
