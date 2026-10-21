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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
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

            CellStyle masterHeader = createMasterHeaderStyle(workbook);

            Font dataFont = workbook.createFont();
            dataFont.setFontName("Segoe UI");
            dataFont.setFontHeightInPoints((short) 10);

            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setFont(dataFont);
            applyCellBorders(textStyle);
            textStyle.setAlignment(HorizontalAlignment.LEFT);

            CellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setFont(dataFont);
            applyCellBorders(centerStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setFont(dataFont);
            applyCellBorders(dateStyle);
            dateStyle.setAlignment(HorizontalAlignment.CENTER);
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));

            CellStyle numericStyle = workbook.createCellStyle();
            numericStyle.setFont(dataFont);
            applyCellBorders(numericStyle);
            numericStyle.setAlignment(HorizontalAlignment.RIGHT);
            numericStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            CellStyle vatStyle = workbook.createCellStyle();
            vatStyle.setFont(dataFont);
            applyCellBorders(vatStyle);
            vatStyle.setAlignment(HorizontalAlignment.CENTER);
            vatStyle.setDataFormat(workbook.createDataFormat().getFormat("0.##"));

            // Sheet 1: DON_HANG
            Sheet donHangSheet = workbook.createSheet("DON_HANG");
            donHangSheet.setDisplayGridlines(true);
            Row dhHeaderRow = donHangSheet.createRow(0);
            dhHeaderRow.setHeightInPoints(28f);
            addStyledRow(dhHeaderRow, masterHeader, "Ngay", "SoPO", "MaNCC", "NhaCungCap", "MaHang", "TenHang", "DVT",
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
                    row.setHeightInPoints(21f);
                    setDateCell(row, 0, orderDate, dateStyle, centerStyle);
                    setStyledCell(row, 1, poNumber, centerStyle);
                    setStyledCell(row, 2, maNCC, centerStyle);
                    setStyledCell(row, 3, nhaCungCap, textStyle);
                    setStyledCell(row, 4, "", centerStyle);
                    setStyledCell(row, 5, "", textStyle);
                    setStyledCell(row, 6, "", centerStyle);
                    setStyledCell(row, 7, "", numericStyle);
                    setStyledCell(row, 8, "", numericStyle);
                    setStyledCell(row, 9, nguoiLap, textStyle);
                    setStyledCell(row, 10, "", textStyle);
                    setStyledCell(row, 11, ghiChu, textStyle);
                    setNumericOrEmpty(row, 12, vat, vatStyle, centerStyle);
                    setStyledCell(row, 13, diaChi, textStyle);
                    setStyledCell(row, 14, loaiTien, centerStyle);
                } else {
                    for (PurchaseOrderItem item : items) {
                        Row row = donHangSheet.createRow(donHangRowIdx++);
                        row.setHeightInPoints(21f);
                        setDateCell(row, 0, orderDate, dateStyle, centerStyle);
                        setStyledCell(row, 1, poNumber, centerStyle);
                        setStyledCell(row, 2, maNCC, centerStyle);
                        setStyledCell(row, 3, nhaCungCap, textStyle);
                        String maHang = item.getMaterialCodeSnapshot() != null ? item.getMaterialCodeSnapshot() : (item.getMaterial() != null ? safe(item.getMaterial().getCode()) : "");
                        setStyledCell(row, 4, maHang, centerStyle);
                        setStyledCell(row, 5, item.getMaterialName(), textStyle);
                        setStyledCell(row, 6, item.getUnit(), centerStyle);
                        setQuantityCell(row, 7, item.getQuantity(), item.getQuantityText(), numericStyle, textStyle);
                        setNumericOrEmpty(row, 8, item.getUnitPrice(), numericStyle, textStyle);
                        setStyledCell(row, 9, nguoiLap, textStyle);
                        setStyledCell(row, 10, item.getSpecification(), textStyle);
                        setStyledCell(row, 11, ghiChu, textStyle);
                        setNumericOrEmpty(row, 12, vat, vatStyle, centerStyle);
                        setStyledCell(row, 13, diaChi, textStyle);
                        setStyledCell(row, 14, loaiTien, centerStyle);
                    }
                }
            }

            // Spacious Column Widths for DON_HANG
            donHangSheet.setColumnWidth(0, 14 * 256);  // Ngay
            donHangSheet.setColumnWidth(1, 18 * 256);  // SoPO
            donHangSheet.setColumnWidth(2, 16 * 256);  // MaNCC
            donHangSheet.setColumnWidth(3, 48 * 256);  // NhaCungCap
            donHangSheet.setColumnWidth(4, 16 * 256);  // MaHang
            donHangSheet.setColumnWidth(5, 45 * 256);  // TenHang
            donHangSheet.setColumnWidth(6, 12 * 256);  // DVT
            donHangSheet.setColumnWidth(7, 14 * 256);  // SoLuong
            donHangSheet.setColumnWidth(8, 18 * 256);  // DonGia
            donHangSheet.setColumnWidth(9, 18 * 256);  // NguoiLap
            donHangSheet.setColumnWidth(10, 36 * 256); // QuyCach
            donHangSheet.setColumnWidth(11, 32 * 256); // GhiChu
            donHangSheet.setColumnWidth(12, 12 * 256); // VAT
            donHangSheet.setColumnWidth(13, 58 * 256); // DiaChi
            donHangSheet.setColumnWidth(14, 12 * 256); // LoaiTien
            donHangSheet.createFreezePane(0, 1);
            if (donHangRowIdx > 1) {
                donHangSheet.setAutoFilter(new CellRangeAddress(0, donHangRowIdx - 1, 0, 14));
            }

            // Sheet 2: NCC
            Sheet nccSheet = workbook.createSheet("NCC");
            nccSheet.setDisplayGridlines(true);
            Row nccHeaderRow = nccSheet.createRow(0);
            nccHeaderRow.setHeightInPoints(28f);
            addStyledRow(nccHeaderRow, masterHeader, "MaNCC", "TenNCC", "DiaChi");

            int nccRowIdx = 1;
            List<Supplier> supplierList = suppliers.findAll(Sort.by(Sort.Direction.ASC, "id"));
            for (Supplier s : supplierList) {
                Row row = nccSheet.createRow(nccRowIdx++);
                row.setHeightInPoints(21f);
                setStyledCell(row, 0, s.getCode(), centerStyle);
                setStyledCell(row, 1, s.getName(), textStyle);
                setStyledCell(row, 2, s.getAddress(), textStyle);
            }

            // Spacious Column Widths for NCC
            nccSheet.setColumnWidth(0, 18 * 256);  // MaNCC
            nccSheet.setColumnWidth(1, 55 * 256);  // TenNCC
            nccSheet.setColumnWidth(2, 78 * 256);  // DiaChi
            nccSheet.createFreezePane(0, 1);
            if (nccRowIdx > 1) {
                nccSheet.setAutoFilter(new CellRangeAddress(0, nccRowIdx - 1, 0, 2));
            }

            // Sheet 3: LICH_SU
            Sheet lichSuSheet = workbook.createSheet("LICH_SU");
            lichSuSheet.setDisplayGridlines(true);
            Row lsHeaderRow = lichSuSheet.createRow(0);
            lsHeaderRow.setHeightInPoints(28f);
            addStyledRow(lsHeaderRow, masterHeader, "Ngay", "MaNCC", "NhaCungCap", "MaHang", "TenHang", "DVT", "SoLuong", "DonGia");

            int lichSuRowIdx = 1;
            int page = 0;
            while (true) {
                Page<HistoricalPurchase> batch = history.findAll(PageRequest.of(page, 1000, Sort.by(Sort.Direction.ASC, "id")));
                for (HistoricalPurchase hp : batch.getContent()) {
                    Row row = lichSuSheet.createRow(lichSuRowIdx++);
                    row.setHeightInPoints(21f);
                    setDateCell(row, 0, hp.getPurchaseDate(), dateStyle, centerStyle);
                    String maNCC = hp.getSupplierCodeSnapshot() != null ? hp.getSupplierCodeSnapshot() : (hp.getSupplier() != null ? safe(hp.getSupplier().getCode()) : "");
                    setStyledCell(row, 1, maNCC, centerStyle);
                    String ncc = hp.getSupplierSnapshot() != null ? hp.getSupplierSnapshot() : (hp.getSupplier() != null ? safe(hp.getSupplier().getName()) : "");
                    setStyledCell(row, 2, ncc, textStyle);
                    String maHang = hp.getMaterialCodeSnapshot() != null ? hp.getMaterialCodeSnapshot() : (hp.getMaterial() != null ? safe(hp.getMaterial().getCode()) : "");
                    setStyledCell(row, 3, maHang, centerStyle);
                    setStyledCell(row, 4, hp.getMaterialNameSnapshot(), textStyle);
                    setStyledCell(row, 5, hp.getUnit(), centerStyle);
                    setQuantityCell(row, 6, hp.getQuantity(), hp.getQuantityText(), numericStyle, textStyle);
                    setNumericOrEmpty(row, 7, hp.getUnitPrice(), numericStyle, textStyle);
                }
                if (!batch.hasNext()) break;
                page++;
            }

            // Spacious Column Widths for LICH_SU
            lichSuSheet.setColumnWidth(0, 14 * 256);  // Ngay
            lichSuSheet.setColumnWidth(1, 16 * 256);  // MaNCC
            lichSuSheet.setColumnWidth(2, 48 * 256);  // NhaCungCap
            lichSuSheet.setColumnWidth(3, 16 * 256);  // MaHang
            lichSuSheet.setColumnWidth(4, 45 * 256);  // TenHang
            lichSuSheet.setColumnWidth(5, 12 * 256);  // DVT
            lichSuSheet.setColumnWidth(6, 14 * 256);  // SoLuong
            lichSuSheet.setColumnWidth(7, 18 * 256);  // DonGia
            lichSuSheet.createFreezePane(0, 1);
            if (lichSuRowIdx > 1) {
                lichSuSheet.setAutoFilter(new CellRangeAddress(0, lichSuRowIdx - 1, 0, 7));
            }

            // Sheet 4: CONFIG
            Sheet configSheet = workbook.createSheet("CONFIG");
            configSheet.setDisplayGridlines(true);
            Row configRow0 = configSheet.createRow(0);
            configRow0.setHeightInPoints(24f);
            Cell c00 = configRow0.createCell(0); c00.setCellValue("PO_SEQ"); c00.setCellStyle(masterHeader);
            Cell c01 = configRow0.createCell(1); c01.setCellValue((double) orders.count()); c01.setCellStyle(centerStyle);

            Row configRow1 = configSheet.createRow(1);
            configRow1.setHeightInPoints(24f);
            Cell c10 = configRow1.createCell(0); c10.setCellValue("ACCESS_CODE"); c10.setCellStyle(masterHeader);
            Cell c11 = configRow1.createCell(1); c11.setCellValue("KHVT@2026"); c11.setCellStyle(centerStyle);

            configSheet.setColumnWidth(0, 24 * 256);
            configSheet.setColumnWidth(1, 30 * 256);

            workbook.write(output);
            if (!workbook.dispose()) throw new IllegalStateException("Không dọn được file tạm XLSX");
            return new ExportFile("QUAN_LY_MUA_HANG_KHVT.xlsx", output.toByteArray(), false);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo file Excel", exception);
        }
    }

    private static CellStyle createMasterHeaderStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Segoe UI");
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.WHITE.getIndex());
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBottomBorderColor(IndexedColors.DARK_BLUE.getIndex());
        style.setBorderLeft(BorderStyle.THIN);
        style.setLeftBorderColor(IndexedColors.WHITE.getIndex());
        style.setBorderRight(BorderStyle.THIN);
        style.setRightBorderColor(IndexedColors.WHITE.getIndex());
        return style;
    }

    private static void applyCellBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderLeft(BorderStyle.THIN);
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBorderRight(BorderStyle.THIN);
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setVerticalAlignment(VerticalAlignment.CENTER);
    }

    private static void addStyledRow(Row row, CellStyle style, String... values) {
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            cell.setCellStyle(style);
        }
    }

    private static void setStyledCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(safe(value));
        if (style != null) cell.setCellStyle(style);
    }

    private static void setDateCell(Row row, int col, LocalDate date, CellStyle dateStyle, CellStyle fallbackStyle) {
        Cell cell = row.createCell(col);
        if (date != null) {
            cell.setCellValue(Date.valueOf(date));
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue("");
            if (fallbackStyle != null) cell.setCellStyle(fallbackStyle);
        }
    }

    private static void setNumericOrEmpty(Row row, int col, BigDecimal value, CellStyle numericStyle, CellStyle fallbackStyle) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
            if (numericStyle != null) cell.setCellStyle(numericStyle);
        } else {
            cell.setCellValue("");
            if (fallbackStyle != null) cell.setCellStyle(fallbackStyle);
        }
    }

    private static void setQuantityCell(Row row, int col, BigDecimal quantity, String quantityText, CellStyle numericStyle, CellStyle textStyle) {
        Cell cell = row.createCell(col);
        if (quantity != null) {
            cell.setCellValue(quantity.doubleValue());
            if (numericStyle != null) cell.setCellStyle(numericStyle);
        } else if (quantityText != null && !quantityText.isBlank()) {
            cell.setCellValue(quantityText);
            if (textStyle != null) cell.setCellStyle(textStyle);
        } else {
            cell.setCellValue("");
            if (textStyle != null) cell.setCellStyle(textStyle);
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
