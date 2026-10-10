package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.domain.importbatch.ImportBatch;
import com.example.quanlymuahang.domain.importbatch.ImportBatchStatus;
import com.example.quanlymuahang.domain.material.Material;
import com.example.quanlymuahang.domain.material.MaterialCategory;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrder;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderItem;
import com.example.quanlymuahang.domain.purchaseorder.PurchaseOrderStatus;
import com.example.quanlymuahang.domain.supplier.Supplier;
import com.example.quanlymuahang.repository.HistoricalPurchaseRepository;
import com.example.quanlymuahang.repository.ImportBatchRepository;
import com.example.quanlymuahang.repository.MaterialRepository;
import com.example.quanlymuahang.repository.PurchaseOrderRepository;
import com.example.quanlymuahang.repository.SupplierRepository;
import com.example.quanlymuahang.service.TextNormalizer;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.Date1904Support;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LegacyWorkbookImportService {
    private static final String MODE = "LEGACY_WORKBOOK";
    private static final Set<String> EXPECTED_SHEETS = Set.of("NCC", "LICH_SU", "DON_HANG");
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final Pattern PO_NUMBER = Pattern.compile("^PO-(\\d{6})-(\\d+)$");

    private final ImportBatchRepository batches;
    private final HistoricalPurchaseRepository history;
    private final SupplierRepository suppliers;
    private final MaterialRepository materials;
    private final PurchaseOrderRepository orders;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditRecorder audit;

    public LegacyWorkbookImportService(ImportBatchRepository batches, HistoricalPurchaseRepository history,
                                       SupplierRepository suppliers, MaterialRepository materials,
                                       PurchaseOrderRepository orders, JdbcTemplate jdbc, ObjectMapper mapper, AuditRecorder audit) {
        this.batches = batches; this.history = history; this.suppliers = suppliers; this.materials = materials;
        this.orders = orders; this.jdbc = jdbc; this.mapper = mapper; this.audit = audit;
    }

    @Transactional
    public PreviewResult preview(MultipartFile file, long actorId) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("FILE_REQUIRED", "Chọn tệp Excel để import");
        if (file.getSize() > 25L * 1024 * 1024) throw ApiException.badRequest("FILE_TOO_LARGE", "Tệp không được vượt quá 25 MB");
        String name = safeFileName(file.getOriginalFilename());
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")))
            throw ApiException.badRequest("UNSUPPORTED_FILE", "Chỉ hỗ trợ tệp .xlsx hoặc .xls");
        byte[] content;
        try { content = file.getBytes(); }
        catch (IOException exception) { throw ApiException.badRequest("FILE_READ_FAILED", "Không thể đọc tệp đã tải lên"); }
        String sha = sha256(content);
        var previous = batches.findBySha256AndMode(sha, MODE);
        if (previous.isPresent()) return result(previous.get(), true, "Tệp này đã có trong lịch sử import; không tạo lô trùng.");

        ZipSecureFile.setMinInflateRatio(0.01d);
        WorkbookData parsed;
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            parsed = parse(workbook);
        } catch (Exception exception) {
            if (exception instanceof ApiException apiException) throw apiException;
            throw ApiException.badRequest("INVALID_WORKBOOK", "Tệp Excel không đọc được hoặc bị hỏng");
        }
        List<String> missing = EXPECTED_SHEETS.stream().filter(sheet -> !parsed.sheetRows().containsKey(sheet)).toList();
        if (!missing.isEmpty()) throw ApiException.badRequest("WORKSHEET_MISSING", "Thiếu sheet bắt buộc: " + String.join(", ", missing));

        int total = parsed.rows().size();
        int errors = (int) parsed.rows().stream().filter(row -> row.status().equals("ERROR")).count();
        int warnings = (int) parsed.rows().stream().filter(row -> row.status().equals("WARNING")).count();
        ImportBatch batch = new ImportBatch(name, sha, lowerName.endsWith(".xlsx") ? "XLSX" : "XLS", MODE);
        batch.setCreatedBy(actorId); batch.setTotalRows(total); batch.setErrorRows(errors); batch.setSuccessRows(total - errors);
        ImportBatch saved = batches.saveAndFlush(batch);
        persistPreviewRows(saved.getId(), parsed.rows());
        audit.record(actorId, "LEGACY_IMPORT_PREVIEWED", "IMPORT_BATCH", saved.getId(),
                Map.of("fileName", saved.getFileName(), "sha256", saved.getSha256(), "totalRows", total, "errorRows", errors, "warningRows", warnings));
        return new PreviewResult(saved.getId(), saved.getFileName(), saved.getSha256(), saved.getStatus().name(),
                summary(parsed, errors, warnings), parsed.rows().stream().filter(row -> !row.issues().isEmpty()).limit(30)
                .map(row -> new RowIssue(row.sheet(), row.rowNumber(), row.status(), row.issues())).toList(), false, null);
    }

    @Transactional
    public CommitResult commit(long batchId, long actorId) {
        ImportBatch batch = batches.findLockedById(batchId).orElseThrow(() -> ApiException.notFound("Không tìm thấy lô import"));
        if (!MODE.equals(batch.getMode())) throw ApiException.badRequest("INVALID_IMPORT_MODE", "Lô import không phải workbook legacy");
        if (batch.getStatus() != ImportBatchStatus.PREVIEW) throw ApiException.conflict("IMPORT_ALREADY_PROCESSED", "Lô import đã được xử lý trước đó");
        List<StoredRow> rows = jdbc.query("SELECT sheet_name,`row_number`,mapped_json,status,issues_json FROM import_rows WHERE batch_id=? ORDER BY sheet_name,`row_number`",
                (rs, index) -> new StoredRow(rs.getString(1), rs.getInt(2), readJson(rs.getString(3)), rs.getString(4), rs.getString(5)), batchId);
        if (rows.size() != batch.getTotalRows())
            throw ApiException.conflict("IMPORT_SOURCE_INVALID", "Staging không còn khớp số dòng hoặc có giá trị không thể lưu chính xác; chưa commit");
        if (rows.stream().anyMatch(row -> validateRow(row.sheet(), row.data()).stream().anyMatch(issue -> issue.startsWith("ERROR:"))))
            throw ApiException.conflict("IMPORT_HAS_ERRORS", "Staging có giá trị không thể lưu chính xác; chưa commit");
        Map<String, List<LegacyRow>> stagedGroups = rows.stream().map(row -> new LegacyRow(row.sheet(), row.rowNumber(), Map.of(), row.data(), row.status(), List.of()))
                .collect(Collectors.groupingBy(LegacyRow::sheet, LinkedHashMap::new, Collectors.toList()));
        addPurchaseOrderHeaderConflicts(stagedGroups);
        if (stagedGroups.values().stream().flatMap(List::stream).anyMatch(row -> row.issues().stream().anyMatch(issue -> issue.contains("PO_HEADER_CONFLICT"))))
            throw ApiException.conflict("PO_HEADER_CONFLICT", "Staging có header PO không nhất quán; chưa commit");
        if (rows.stream().anyMatch(row -> row.issuesJson() != null && row.issuesJson().contains("PO_HEADER_CONFLICT")))
            throw ApiException.conflict("PO_HEADER_CONFLICT", "Một hoặc nhiều số PO có header khác nhau giữa các dòng. Hãy chỉnh workbook cho nhất quán rồi tải lại; chưa có dữ liệu nào được commit.");
        if (rows.stream().anyMatch(row -> row.status().equals("ERROR")))
            throw ApiException.conflict("IMPORT_HAS_ERRORS", "Lô có dòng lỗi. Hãy sửa file và tải lại; chưa có dữ liệu nào được commit.");
        rows = rows.stream().map(this::revalidated).toList();
        for (StoredRow row : rows) jdbc.update("UPDATE import_rows SET issues_json=? WHERE batch_id=? AND sheet_name=? AND `row_number`=?",
                row.issuesJson(), batchId, row.sheet(), row.rowNumber());

        Map<String, Supplier> supplierByCode = new HashMap<>();
        Map<String, Supplier> supplierByName = new HashMap<>();
        Set<Supplier> changedSuppliers = new LinkedHashSet<>();
        Map<Integer, Supplier> supplierSourceRows = new LinkedHashMap<>();
        for (Supplier entity : suppliers.findAll()) indexSupplier(entity, supplierByCode, supplierByName);
        for (StoredRow row : rows) {
            if (!row.sheet().equals("NCC") || row.status().equals("ERROR")) continue;
            String code = value(row.data(), "supplierCode");
            String name = value(row.data(), "supplierName");
            Supplier entity = findSupplier(code, name, supplierByCode, supplierByName);
            if (entity == null) entity = new Supplier(name, TextNormalizer.normalize(name));
            Supplier updatedSupplier = entity;
            supplierByName.entrySet().removeIf(entry -> entry.getValue() == updatedSupplier);
            entity.update(code == null ? entity.getCode() : code, name, TextNormalizer.normalize(name),
                    value(row.data(), "address") == null ? entity.getAddress() : value(row.data(), "address"),
                    entity.getTaxCode(), entity.getPhone(), entity.getEmail(), entity.isActive());
            indexSupplier(entity, supplierByCode, supplierByName); changedSuppliers.add(entity);
            supplierSourceRows.put(row.rowNumber(), entity);
        }
        for (StoredRow row : rows) {
            if (row.status().equals("ERROR") || row.sheet().equals("NCC")) continue;
            String supplierName = value(row.data(), "supplierName");
            String supplierCode = value(row.data(), "supplierCode");
            if (supplierName == null && supplierCode == null) continue;
            if (findSupplier(supplierCode, supplierName, supplierByCode, supplierByName) != null) continue;
            if (supplierCode == null) continue; // Keep ambiguous or unmatched supplier names as transaction snapshots only.
            String name = supplierName == null ? supplierCode : supplierName;
            Supplier entity = new Supplier(name, TextNormalizer.normalize(name));
            entity.update(supplierCode, name, TextNormalizer.normalize(name), value(row.data(), "address"), null, null, null, true);
            indexSupplier(entity, supplierByCode, supplierByName); changedSuppliers.add(entity);
        }
        suppliers.saveAll(changedSuppliers);
        suppliers.flush();
        supplierSourceRows.forEach((rowNumber, entity) -> jdbc.update(
                "UPDATE import_rows SET committed_entity_type='SUPPLIER', committed_entity_id=? WHERE batch_id=? AND sheet_name='NCC' AND `row_number`=?",
                entity.getId(), batchId, rowNumber));

        Map<String, Material> materialByCode = new HashMap<>();
        Map<String, Material> materialByName = new HashMap<>();
        Set<Material> changedMaterials = new LinkedHashSet<>();
        for (Material entity : materials.findAll()) indexMaterial(entity, materialByCode, materialByName);
        for (StoredRow row : rows) {
            if (row.status().equals("ERROR") || !(row.sheet().equals("LICH_SU") || row.sheet().equals("DON_HANG"))) continue;
            String name = value(row.data(), "materialName");
            String code = value(row.data(), "materialCode");
            String unit = value(row.data(), "unit");
            if (name == null) continue;
            Material entity = findMaterial(code, name, materialByCode, materialByName);
            if (entity == null && code == null) continue; // No fuzzy/name-only master creation for ambiguous legacy rows.
            if (entity == null) entity = new Material(name, TextNormalizer.normalize(name));
            if (entity.getDefaultUnit() == null && unit != null) entity.setDefaultUnit(unit);
            if (entity.getCode() == null && code != null) entity.setCode(code);
            indexMaterial(entity, materialByCode, materialByName); changedMaterials.add(entity);
        }
        materials.saveAll(changedMaterials);

        List<HistoricalPurchase> historyRows = new ArrayList<>();
        List<StoredRow> historySourceRows = new ArrayList<>();
        List<StoredRow> orderRows = new ArrayList<>();
        int errors = 0, warnings = 0, success = 0;
        for (StoredRow row : rows) {
            if (row.status().equals("ERROR")) { errors++; continue; }
            if (row.status().equals("WARNING")) warnings++;
            if (row.sheet().equals("LICH_SU")) {
                HistoricalPurchase h = historical(row.data(), row.rowNumber(), row.issuesJson(), batchId, supplierByCode, supplierByName, materialByCode, materialByName);
                historyRows.add(h);
                historySourceRows.add(row);
            } else if (row.sheet().equals("DON_HANG")) orderRows.add(row);
            success++;
        }
        List<HistoricalPurchase> savedHistory = history.saveAll(historyRows);
        for (int index = 0; index < savedHistory.size(); index++) {
            StoredRow source = historySourceRows.get(index);
            jdbc.update("UPDATE import_rows SET committed_entity_type='HISTORICAL_PURCHASE', committed_entity_id=? WHERE batch_id=? AND sheet_name=? AND `row_number`=?",
                    savedHistory.get(index).getId(), batchId, source.sheet(), source.rowNumber());
        }

        int importedOrders = importOrders(orderRows, batchId, supplierByCode, supplierByName, materialByCode, materialByName);
        suppliers.flush(); materials.flush(); history.flush(); orders.flush();
        LegacyImportReconciler.Verification verification = new LegacyImportReconciler(jdbc).verify(batchId,
                rows.stream().map(row -> new LegacyImportReconciler.SourceRow(row.sheet(), row.rowNumber(), row.data())).toList());
        jdbc.update("UPDATE import_rows SET status='COMMITTED' WHERE batch_id=? AND status IN ('READY','WARNING')", batchId);
        batch.setSuccessRows(success); batch.setErrorRows(errors);
        batch.setStatus(errors == 0 ? ImportBatchStatus.COMMITTED : ImportBatchStatus.PARTIAL);
        CommitResult result = new CommitResult(batchId, batch.getStatus().name(), success, errors, warnings, changedSuppliers.size(), changedMaterials.size(),
                historyRows.size(), importedOrders, "Đã đối chiếu từng dòng nguồn với dữ liệu lưu; loại tiền thiếu vẫn là giả định có cảnh báo.", verification);
        audit.record(actorId, "LEGACY_IMPORT_COMMITTED", "IMPORT_BATCH", batchId, result);
        return result;
    }

    private StoredRow revalidated(StoredRow row) {
        List<String> issues;
        try { issues = row.issuesJson() == null ? new ArrayList<>() : new ArrayList<>(mapper.readValue(row.issuesJson(), new TypeReference<List<String>>() {})); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cảnh báo staging JSON bị lỗi", exception); }
        issues.addAll(validateRow(row.sheet(), row.data()));
        issues = issues.stream().distinct().toList();
        String status = issues.isEmpty() ? "READY" : "WARNING";
        return new StoredRow(row.sheet(), row.rowNumber(), row.data(), status, json(issues));
    }

    private int importOrders(List<StoredRow> rows, long batchId, Map<String, Supplier> supplierByCode,
                             Map<String, Supplier> supplierByName, Map<String, Material> materialByCode,
                             Map<String, Material> materialByName) {
        Map<String, List<StoredRow>> groups = rows.stream().collect(Collectors.groupingBy(row -> safeGroup(value(row.data(), "poNumber")), LinkedHashMap::new, Collectors.toList()));
        List<PurchaseOrder> result = new ArrayList<>();
        Set<String> usedNumbers = new HashSet<>();
        Map<String, Integer> duplicateNumbers = new HashMap<>();
        for (List<StoredRow> group : groups.values()) {
            if (group.isEmpty()) continue;
            Map<String, String> first = group.get(0).data();
            String sourceNumber = value(first, "poNumber");
            if (sourceNumber == null) throw ApiException.conflict("IMPORT_SOURCE_CHANGED", "Staging thiếu số PO; không commit thiếu dòng");
            String number = internalOrderNumber(sourceNumber, usedNumbers, duplicateNumbers);
            LocalDate date = parseDate(firstValue(group, "date"));
            String supplierCode = firstValue(group, "supplierCode");
            String supplierName = firstValue(group, "supplierName");
            Supplier supplier = findSupplier(supplierCode, supplierName, supplierByCode, supplierByName);
            if (supplierName == null && supplier != null) supplierName = supplier.getName();
            if (supplierName == null) supplierName = "Nhà cung cấp chưa xác định";
            PurchaseOrder order = new PurchaseOrder(number, date, supplierName);
            order.setSupplier(supplier); order.setSupplierAddressSnapshot(firstValue(group, "address"));
            order.setSourcePoNumber(sourceNumber); order.setSourceImportBatchId(batchId);
            order.setStatus(PurchaseOrderStatus.EXPORTED); order.setCurrency(parseCurrency(firstValue(group, "currency")));
            order.setVatPercent(parseFieldDecimal(first, "vatPercent")); order.setPreparedBy(firstValue(group, "preparedBy"));
            order.setNote(firstValue(group, "note"));
            for (StoredRow row : group) {
                Map<String, String> data = row.data();
                BigDecimal quantity = parseFieldDecimal(data, "quantity");
                String quantityText = quantity == null ? value(data, "quantity") : null;
                BigDecimal price = parseFieldDecimal(data, "unitPrice");
                if (price == null) throw ApiException.conflict("IMPORT_SOURCE_CHANGED", "Staging có đơn giá không hợp lệ; không commit thiếu dòng");
                String itemName = value(data, "materialName");
                if (itemName == null) throw ApiException.conflict("IMPORT_SOURCE_CHANGED", "Staging thiếu tên hàng; không commit thiếu dòng");
                Material material = findMaterial(value(data, "materialCode"), itemName, materialByCode, materialByName);
                PurchaseOrderItem item = new PurchaseOrderItem(itemName, value(data, "unit"), quantity, price);
                item.setMaterial(material);
                item.setMaterialCodeSnapshot(value(data, "materialCode"));
                item.update(value(data, "materialCode"), value(data, "specification"), quantity, quantityText);
                order.addItem(item);
            }
            result.add(order);
            updateDailySequenceFromImportedNumber(number, date);
        }
        List<List<StoredRow>> sourceGroups = groups.values().stream().filter(group -> !group.isEmpty()
                && value(group.get(0).data(), "poNumber") != null).toList();
        List<PurchaseOrder> savedOrders = orders.saveAll(result);
        for (int index = 0; index < savedOrders.size(); index++) {
            PurchaseOrder order = savedOrders.get(index);
            List<StoredRow> sourceGroup = sourceGroups.get(index);
            for (StoredRow source : sourceGroup) {
                jdbc.update("UPDATE import_rows SET committed_entity_type='PURCHASE_ORDER', committed_entity_id=? WHERE batch_id=? AND sheet_name=? AND `row_number`=?",
                        order.getId(), batchId, source.sheet(), source.rowNumber());
            }
        }
        return savedOrders.size();
    }

    private String internalOrderNumber(String sourceNumber, Set<String> usedNumbers, Map<String, Integer> duplicateNumbers) {
        if (usedNumbers.add(sourceNumber) && !orders.existsByPoNumber(sourceNumber)) return sourceNumber;
        int suffix = duplicateNumbers.merge(sourceNumber, 1, Integer::sum) + 1;
        while (true) {
            String suffixText = "-" + suffix;
            int prefixLength = Math.max(1, 40 - suffixText.length());
            String candidate = sourceNumber.substring(0, Math.min(sourceNumber.length(), prefixLength)) + suffixText;
            if (usedNumbers.add(candidate) && !orders.existsByPoNumber(candidate)) return candidate;
            suffix++;
        }
    }

    private HistoricalPurchase historical(Map<String, String> data, int rowNumber,
                                           String issuesJson, long batchId,
                                           Map<String, Supplier> supplierByCode, Map<String, Supplier> supplierByName,
                                           Map<String, Material> materialByCode, Map<String, Material> materialByName) {
        String name = value(data, "materialName");
        String code = value(data, "materialCode");
        Material material = findMaterial(code, name, materialByCode, materialByName);
        Supplier supplier = findSupplier(value(data, "supplierCode"), value(data, "supplierName"), supplierByCode, supplierByName);
        HistoricalPurchase h = new HistoricalPurchase();
        h.setPurchaseDate(parseDate(value(data, "date")));
        h.setSupplier(supplier);
        h.setSupplierSnapshot(value(data, "supplierName") == null && supplier != null ? supplier.getName() : value(data, "supplierName"));
        h.setSupplierCodeSnapshot(value(data, "supplierCode"));
        h.setMaterial(material); h.setMaterialCodeSnapshot(code); h.setMaterialNameSnapshot(name);
        h.setMaterialNameNormalizedSnapshot(TextNormalizer.normalize(name)); h.setUnit(value(data, "unit"));
        BigDecimal quantity = parseFieldDecimal(data, "quantity");
        h.setQuantity(quantity); h.setQuantityText(quantity == null ? value(data, "quantity") : null);
        h.setUnitPrice(parseFieldDecimal(data, "unitPrice"));
        h.setCurrency(parseCurrency(value(data, "currency")));
        h.setCurrencyBasis(value(data, "currency") == null ? "ASSUMED_LEGACY" : "SOURCE");
        h.setSource("LEGACY_LICH_SU"); h.setSourceRowNumber(rowNumber); h.setCategory(MaterialCategory.MATERIAL);
        h.setSourceSheet("LICH_SU"); h.setImportBatchId(batchId); h.setDataQualityFlags(issuesJson);
        h.setSourceReference("LEGACY:" + batchId + ":LICH_SU:" + rowNumber);
        return h;
    }

    private WorkbookData parse(Workbook workbook) {
        Map<String, List<LegacyRow>> bySheet = new LinkedHashMap<>();
        List<LegacyRow> all = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        for (String name : List.of("NCC", "LICH_SU", "DON_HANG")) {
            Sheet sheet = workbook.getSheet(name);
            if (sheet == null) continue;
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) continue;
            Map<Integer, String> columns = new HashMap<>();
            for (int col = 0; col < headerRow.getLastCellNum(); col++) {
                Cell headerCell = headerRow.getCell(col);
                if (headerCell != null && effectiveType(headerCell, evaluator) == CellType.ERROR)
                    throw ApiException.badRequest("WORKSHEET_HEADER_INVALID", "Ô header lỗi tại " + name + " cột " + (col + 1));
                String header = TextNormalizer.normalize(cellText(headerCell, formatter, evaluator));
                String field = canonicalField(header);
                if (field == null && !header.isBlank())
                    throw ApiException.badRequest("WORKSHEET_UNKNOWN_HEADER", "Header chưa có mapping tại " + name + " cột " + (col + 1));
                if (field != null) {
                    if (columns.containsValue(field))
                        throw ApiException.badRequest("WORKSHEET_DUPLICATE_HEADER", "Hai cột cùng mapping " + field + " tại sheet " + name);
                    columns.put(col, field);
                }
            }
            Set<String> headerFields = Set.copyOf(columns.values());
            Set<String> requiredHeaders = switch (name) {
                case "NCC" -> Set.of("supplierCode", "supplierName");
                case "LICH_SU" -> Set.of("materialName", "unitPrice");
                default -> Set.of("poNumber", "materialName", "unitPrice");
            };
            if (!headerFields.containsAll(requiredHeaders))
                throw ApiException.badRequest("WORKSHEET_HEADER_INVALID", "Các cột bắt buộc của sheet " + name + " chưa đúng");
            List<LegacyRow> sheetRows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || row.getFirstCellNum() < 0) continue;
                Map<String, String> mapped = new LinkedHashMap<>();
                Map<String, String> raw = new LinkedHashMap<>();
                List<String> issues = new ArrayList<>();
                for (int col = 0; col < row.getLastCellNum(); col++) {
                    Cell cell = row.getCell(col);
                    String field = columns.get(col);
                    String rawKey = field == null ? "__column_" + (col + 1) : field;
                    if (cell != null && cell.getCellType() == CellType.FORMULA) {
                        raw.put(rawKey + "__formula", cell.getCellFormula());
                        if (cell instanceof XSSFCell formulaCell && formulaCell.getRawValue() != null)
                            raw.put(rawKey + "__cachedValue", formulaCell.getRawValue());
                    } else if (cell instanceof XSSFCell numericCell && cell.getCellType() == CellType.NUMERIC && numericCell.getRawValue() != null)
                        raw.put(rawKey + "__numericToken", numericCell.getRawValue());
                    CellType effectiveType;
                    String text;
                    try {
                        effectiveType = cell == null ? CellType.BLANK : effectiveType(cell, evaluator);
                        text = cell == null ? "" : (("supplierCode".equals(field) || "materialCode".equals(field) || "poNumber".equals(field))
                                ? formatter.formatCellValue(cell, evaluator) : cellText(cell, formatter, evaluator));
                    } catch (RuntimeException exception) {
                        raw.put(field == null ? "__column_" + (col + 1) : field, cell == null ? "" : cell.toString());
                        issues.add("ERROR:CELL_READ_FAILED: Không đọc được ô cột " + (col + 1));
                        continue;
                    }
                    if (effectiveType == CellType.ERROR) {
                        raw.put(field == null ? "__column_" + (col + 1) : field, text);
                        issues.add("ERROR:EXCEL_CELL_ERROR: Ô Excel/công thức lỗi tại cột " + (col + 1));
                        continue;
                    }
                    if (text == null || text.isEmpty()) continue;
                    raw.put(field == null ? "__column_" + (col + 1) : field, text);
                    if (text.isBlank()) continue;
                    if (field != null) {
                        if (!allowedFields(name).contains(field))
                            issues.add("ERROR:UNMAPPED_FIELD: Sheet " + name + " không lưu trường " + field);
                        mapped.put(field, text.trim());
                        if (effectiveType == CellType.NUMERIC) raw.put(field + "__display", formatter.formatCellValue(cell, evaluator));
                        if (effectiveType == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)
                                && Set.of("quantity", "unitPrice", "vatPercent").contains(field))
                            mapped.put(field + "__numeric", "true");
                        if (effectiveType == CellType.NUMERIC && Set.of("quantity", "unitPrice", "vatPercent").contains(field)
                                && formatter.formatCellValue(cell, evaluator).trim().endsWith("%"))
                            issues.add("ERROR:PERCENT_CELL_FORMAT: Ô số có định dạng phần trăm; cần xác nhận tỷ lệ/giá trị nguồn trước khi nhập, không tự nhân 100");
                        if ("date".equals(field) && effectiveType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                            BigDecimal serial = numericValue(cell, evaluator);
                            raw.put("date__serial", serial.toPlainString());
                            if (serial.stripTrailingZeros().scale() > 0)
                                issues.add("ERROR:DATE_HAS_TIME: Ngày nguồn chứa giờ, cột DATE không giữ được giờ");
                        }
                    } else issues.add("ERROR:UNMAPPED_CELL: Ô có dữ liệu nhưng không có header mapping tại cột " + (col + 1));
                }
                if (mapped.isEmpty() && issues.isEmpty()) continue;
                issues.addAll(validateRow(name, mapped));
                String status = issues.stream().anyMatch(issue -> issue.startsWith("ERROR:")) ? "ERROR"
                        : issues.isEmpty() ? "READY" : "WARNING";
                LegacyRow item = new LegacyRow(name, rowIndex + 1, raw, mapped, status, issues);
                sheetRows.add(item); all.add(item);
            }
            bySheet.put(name, sheetRows);
        }
        addPurchaseOrderHeaderConflicts(bySheet);
        addSourceCodeVariants(bySheet);
        all = bySheet.values().stream().flatMap(List::stream).toList();
        return new WorkbookData(bySheet, all);
    }

    private void addPurchaseOrderHeaderConflicts(Map<String, List<LegacyRow>> bySheet) {
        List<LegacyRow> poRows = bySheet.getOrDefault("DON_HANG", List.of());
        Map<String, List<LegacyRow>> groups = poRows.stream().collect(Collectors.groupingBy(row -> safeGroup(value(row.mapped(), "poNumber")), LinkedHashMap::new, Collectors.toList()));
        Set<String> compareFields = Set.of("date", "supplierCode", "supplierName", "currency", "vatPercent", "preparedBy", "note", "address");
        Map<Integer, LegacyRow> replacements = new HashMap<>();
        for (List<LegacyRow> group : groups.values()) {
            if (group.size() < 2) continue;
            List<String> conflicts = new ArrayList<>();
            for (String field : compareFields) {
                Set<String> values = group.stream().map(row -> normalizeHeaderValue(field, row.mapped()))
                        .map(value -> value == null ? "<missing>" : value).collect(Collectors.toSet());
                if (values.size() > 1) conflicts.add(field);
            }
            if (conflicts.isEmpty()) continue;
            for (int i = 0; i < group.size(); i++) {
                LegacyRow old = group.get(i);
                List<String> issues = new ArrayList<>(old.issues());
                issues.add("ERROR:PO_HEADER_CONFLICT: Các trường header khác nhau giữa các dòng cùng SoPO: " + String.join(", ", conflicts));
                replacements.put(old.rowNumber(), new LegacyRow(old.sheet(), old.rowNumber(), old.raw(), old.mapped(), "ERROR", List.copyOf(issues)));
            }
        }
        for (int i = 0; i < poRows.size(); i++) {
            LegacyRow replacement = replacements.get(poRows.get(i).rowNumber());
            if (replacement != null) poRows.set(i, replacement);
        }
        bySheet.put("DON_HANG", poRows);
    }

    private String normalizeHeaderValue(String field, Map<String, String> data) {
        String value = value(data, field);
        if (value == null) return null;
        return switch (field) {
            case "currency" -> switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "USD" -> "USD";
                case "VND", "VNĐ" -> "VND";
                default -> "UNSUPPORTED:" + value.trim().toUpperCase(Locale.ROOT);
            };
            case "vatPercent" -> {
                BigDecimal decimal = parseFieldDecimal(data, field);
                yield decimal == null ? value.trim() : decimal.stripTrailingZeros().toPlainString();
            }
            case "date" -> parseDate(value) == null ? value : parseDate(value).toString();
            default -> value.trim();
        };
    }

    private static Set<String> allowedFields(String sheet) {
        return switch (sheet) {
            case "NCC" -> Set.of("supplierCode", "supplierName", "address");
            case "LICH_SU" -> Set.of("date", "supplierCode", "supplierName", "materialCode", "materialName", "unit", "quantity", "unitPrice", "currency");
            default -> Set.of("date", "poNumber", "supplierCode", "supplierName", "address", "materialCode", "materialName", "unit", "quantity", "unitPrice", "preparedBy", "specification", "note", "vatPercent", "currency");
        };
    }

    private void addSourceCodeVariants(Map<String, List<LegacyRow>> bySheet) {
        Map<String, Set<String>> names = new HashMap<>();
        Map<String, Set<String>> units = new HashMap<>();
        for (List<LegacyRow> sheetRows : bySheet.values()) for (LegacyRow row : sheetRows) {
            for (String kind : List.of("supplier", "material")) {
                String code = value(row.mapped(), kind + "Code");
                String name = value(row.mapped(), kind + "Name");
                if (code != null && name != null) names.computeIfAbsent(kind + ":" + code.toLowerCase(Locale.ROOT), ignored -> new HashSet<>()).add(TextNormalizer.normalize(name));
            }
            String code = value(row.mapped(), "materialCode"), unit = value(row.mapped(), "unit");
            if (code != null && unit != null) units.computeIfAbsent(code.toLowerCase(Locale.ROOT), ignored -> new HashSet<>()).add(TextNormalizer.normalize(unit));
        }
        bySheet.replaceAll((sheet, sheetRows) -> sheetRows.stream().map(row -> {
            List<String> issues = new ArrayList<>(row.issues());
            for (String kind : List.of("supplier", "material")) {
                String code = value(row.mapped(), kind + "Code");
                if (code != null && names.getOrDefault(kind + ":" + code.toLowerCase(Locale.ROOT), Set.of()).size() > 1)
                    issues.add("WARNING:CODE_NAME_VARIANT: Mã " + kind + " có nhiều tên nguồn; giữ snapshot, cần đối chiếu");
            }
            String code = value(row.mapped(), "materialCode");
            if (code != null && units.getOrDefault(code.toLowerCase(Locale.ROOT), Set.of()).size() > 1)
                issues.add("WARNING:CODE_UNIT_VARIANT: Mã hàng có nhiều đơn vị nguồn; không tự quy đổi");
            return new LegacyRow(row.sheet(), row.rowNumber(), row.raw(), row.mapped(),
                    row.status().equals("ERROR") ? "ERROR" : issues.isEmpty() ? "READY" : "WARNING", List.copyOf(issues));
        }).collect(Collectors.toCollection(ArrayList::new)));
    }

    private List<String> validateRow(String sheet, Map<String, String> data) {
        List<String> issues = new ArrayList<>();
        Map<String, Integer> lengths = Map.ofEntries(Map.entry("supplierCode", 50), Map.entry("supplierName", 500),
                Map.entry("materialCode", 80), Map.entry("materialName", 500), Map.entry("unit", 100),
                Map.entry("address", 1000), Map.entry("specification", 1000), Map.entry("note", 1000),
                Map.entry("preparedBy", 255), Map.entry("poNumber", 40));
        lengths.forEach((field, limit) -> {
            String text = value(data, field);
            if (text != null && text.codePointCount(0, text.length()) > limit)
                issues.add("ERROR:FIELD_TOO_LONG: " + field + " vượt quá " + limit + " ký tự");
        });
        for (String field : data.keySet()) {
            if (!field.endsWith("__numeric") && !allowedFields(sheet).contains(field) && value(data, field) != null)
                issues.add("ERROR:UNMAPPED_FIELD: Sheet " + sheet + " không lưu trường " + field);
        }
        if (!sheet.equals("NCC")) {
            validateDecimal(data, "unitPrice", 38, 18, issues);
            validateDecimal(data, "quantity", 38, 18, issues);
            String quantityText = value(data, "quantity");
            if (quantityText != null && parseFieldDecimal(data, "quantity") == null) {
                if (LegacyImportValues.looksNumeric(quantityText) || "true".equals(data.get("quantity__numeric")))
                    issues.add("ERROR:AMBIGUOUS_QUANTITY: Số lượng dạng số không đọc được chắc chắn; không biến thành số lượng chữ");
                else if (quantityText.codePointCount(0, quantityText.length()) > 255)
                    issues.add("ERROR:FIELD_TOO_LONG: quantity_text vượt quá 255 ký tự");
                else issues.add("WARNING:QUANTITY_TEXT: Giữ nguyên số lượng chữ, không tính tổng");
            }
            if (value(data, "unit") == null) issues.add("WARNING:MISSING_UNIT: Nguồn không ghi đơn vị tính");
            for (String field : List.of("unitPrice", "quantity")) {
                BigDecimal number = parseFieldDecimal(data, field);
                if (number != null && number.signum() <= 0) issues.add("WARNING:NONPOSITIVE_" + field.toUpperCase(Locale.ROOT) + ": Giữ giá trị nguồn bằng 0/âm, cần đối chiếu chứng từ");
            }
            if (value(data, "currency") == null)
                issues.add("WARNING:ASSUMED_LEGACY_CURRENCY: Nguồn không ghi loại tiền; VND là giả định, chưa xác minh");
            else if (!List.of("VND", "VNĐ", "USD").contains(value(data, "currency").toUpperCase(Locale.ROOT)))
                issues.add("ERROR:UNSUPPORTED_CURRENCY: Loại tiền nguồn chưa hỗ trợ; không thay bằng VND");
        }
        if (sheet.equals("NCC")) {
            required(data, "supplierName", "MISSING_SUPPLIER_NAME", "Tên NCC", issues);
            if (value(data, "supplierCode") == null) issues.add("WARNING:MISSING_SUPPLIER_CODE: Không có mã NCC; giữ nhà cung cấp theo tên chính xác nếu duy nhất");
        } else if (sheet.equals("LICH_SU")) {
            required(data, "materialName", "MISSING_MATERIAL_NAME", "Tên hàng", issues);
            required(data, "unitPrice", "MISSING_UNIT_PRICE", "Đơn giá", issues);
            if (parseFieldDecimal(data, "unitPrice") == null) issues.add("ERROR:INVALID_UNIT_PRICE: Đơn giá không phải số hợp lệ");
            if (value(data, "date") == null) issues.add("WARNING:MISSING_DATE: Không có ngày mua; sẽ giữ NULL");
            else if (parseDate(value(data, "date")) == null) issues.add("ERROR:INVALID_DATE: Ngày mua có dữ liệu nhưng không đọc được chính xác");
            if (value(data, "supplierCode") == null) issues.add("WARNING:MISSING_SUPPLIER_CODE: Không có mã NCC; sẽ chỉ liên kết khi tên khớp chính xác và duy nhất");
            if (value(data, "supplierCode") == null && value(data, "supplierName") == null) issues.add("WARNING:MISSING_SUPPLIER: Không có nhà cung cấp");
            if (value(data, "materialCode") == null) issues.add("WARNING:MISSING_MATERIAL_CODE: Không có mã hàng; giữ snapshot và không tự ghép mơ hồ");
        } else {
            required(data, "poNumber", "MISSING_PO_NUMBER", "Số PO", issues);
            if (value(data, "poNumber") != null && value(data, "poNumber").length() > 40)
                issues.add("ERROR:PO_NUMBER_TOO_LONG: Số PO vượt quá 40 ký tự");
            required(data, "materialName", "MISSING_MATERIAL_NAME", "Tên hàng", issues);
            required(data, "unitPrice", "MISSING_UNIT_PRICE", "Đơn giá", issues);
            if (parseFieldDecimal(data, "unitPrice") == null) issues.add("ERROR:INVALID_UNIT_PRICE: Đơn giá không phải số hợp lệ");
            if (value(data, "date") == null) issues.add("WARNING:MISSING_DATE: Không có ngày PO; sẽ giữ NULL");
            else if (parseDate(value(data, "date")) == null) issues.add("ERROR:INVALID_DATE: Ngày PO có dữ liệu nhưng không đọc được chính xác");
            if (value(data, "supplierCode") == null) issues.add("WARNING:MISSING_SUPPLIER_CODE: Không có mã NCC; giữ snapshot tên");
            if (value(data, "materialCode") == null) issues.add("WARNING:MISSING_MATERIAL_CODE: Không có mã hàng; giữ snapshot tên");
            if (value(data, "vatPercent") == null) issues.add("WARNING:UNKNOWN_VAT: Không có VAT; để NULL, không tự suy đoán");
            else {
                BigDecimal vat = parseFieldDecimal(data, "vatPercent");
                if (vat == null || vat.signum() < 0 || vat.compareTo(new BigDecimal("100")) > 0)
                    issues.add("ERROR:INVALID_VAT: VAT có dữ liệu nhưng không phải tỷ lệ 0–100 đọc được chính xác");
                validateDecimal(data, "vatPercent", 5, 2, issues);
            }
        }
        return issues;
    }

    private static void validateDecimal(Map<String, String> data, String field, int precision, int scale, List<String> issues) {
        BigDecimal number = parseFieldDecimal(data, field);
        if (number == null) return;
        BigDecimal exact = number.stripTrailingZeros();
        if (Math.max(0, exact.scale()) > scale || Math.max(0L, (long) exact.precision() - exact.scale()) > precision - scale)
            issues.add("ERROR:DECIMAL_NOT_REPRESENTABLE: " + field + " không lưu chính xác được trong DECIMAL(" + precision + "," + scale + "); không làm tròn");
    }

    private void persistPreviewRows(long batchId, List<LegacyRow> rows) {
        jdbc.batchUpdate("INSERT INTO import_rows (batch_id,sheet_name,`row_number`,raw_json,mapped_json,issues_json,status) VALUES (?,?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                        LegacyRow row = rows.get(i);
                        ps.setLong(1, batchId); ps.setString(2, row.sheet()); ps.setInt(3, row.rowNumber());
                        ps.setString(4, json(row.raw())); ps.setString(5, json(row.mapped())); ps.setString(6, json(row.issues())); ps.setString(7, row.status());
                    }
                    @Override public int getBatchSize() { return rows.size(); }
                });
    }

    private Summary summary(WorkbookData parsed, int errors, int warnings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        parsed.sheetRows().forEach((name, rows) -> counts.put(name, rows.size()));
        int legacyPurchaseOrders = (int) parsed.sheetRows().getOrDefault("DON_HANG", List.of()).stream()
                .map(row -> value(row.mapped(), "poNumber")).filter(value -> value != null).distinct().count();
        return new Summary(counts, errors, warnings, legacyPurchaseOrders, parsed.rows().size(),
                "Sheet CONFIG bị bỏ qua hoàn toàn; ACCESS_CODE và PO_SEQ không được đọc hoặc lưu.");
    }

    private PreviewResult result(ImportBatch batch, boolean duplicate, String message) {
        List<LegacyRow> rows = jdbc.query("SELECT sheet_name,`row_number`,mapped_json,issues_json FROM import_rows WHERE batch_id=? ORDER BY CASE sheet_name WHEN 'NCC' THEN 1 WHEN 'LICH_SU' THEN 2 WHEN 'DON_HANG' THEN 3 ELSE 4 END,`row_number`", (rs, n) -> {
            List<String> issues;
            try { issues = mapper.readValue(rs.getString(4), new TypeReference<List<String>>() {}); }
            catch (JsonProcessingException exception) { throw new IllegalStateException("Cảnh báo staging JSON bị lỗi", exception); }
            String status = issues.stream().anyMatch(issue -> issue.startsWith("ERROR:")) ? "ERROR" : issues.isEmpty() ? "READY" : "WARNING";
            return new LegacyRow(rs.getString(1), rs.getInt(2), Map.of(), readJson(rs.getString(3)), status, issues);
        }, batch.getId());
        Map<String, List<LegacyRow>> bySheet = rows.stream().collect(Collectors.groupingBy(LegacyRow::sheet, LinkedHashMap::new, Collectors.toList()));
        int errors = (int) rows.stream().filter(row -> row.status().equals("ERROR")).count();
        int warnings = (int) rows.stream().filter(row -> row.status().equals("WARNING")).count();
        return new PreviewResult(batch.getId(), batch.getFileName(), batch.getSha256(), batch.getStatus().name(),
                summary(new WorkbookData(bySheet, rows), errors, warnings), rows.stream().filter(row -> !row.issues().isEmpty()).limit(30)
                .map(row -> new RowIssue(row.sheet(), row.rowNumber(), row.status(), row.issues())).toList(), duplicate, message);
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellValue formulaValue = cell.getCellType() == CellType.FORMULA ? evaluator.evaluate(cell) : null;
        CellType type = formulaValue == null ? cell.getCellType() : formulaValue.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            double serial = numericValue(cell, evaluator).doubleValue();
            if (!DateUtil.isValidExcelDate(serial)) throw new IllegalArgumentException("Invalid Excel date");
            Workbook workbook = cell.getSheet().getWorkbook();
            boolean date1904 = workbook instanceof Date1904Support support && support.isDate1904();
            if (workbook instanceof org.apache.poi.hssf.usermodel.HSSFWorkbook hssf)
                date1904 = hssf.getWorkbook().isUsing1904DateWindowing();
            if (!date1904 && serial >= 60 && serial < 61) throw new IllegalArgumentException("Excel fictitious 1900 leap day");
            return DateUtil.getLocalDateTime(serial, date1904).toLocalDate().toString();
        }
        if (type == CellType.NUMERIC) return numericValue(cell, evaluator).stripTrailingZeros().toPlainString();
        if (type == CellType.STRING) return formulaValue == null ? cell.getStringCellValue() : formulaValue.getStringValue();
        if (type == CellType.BOOLEAN) return Boolean.toString(formulaValue == null ? cell.getBooleanCellValue() : formulaValue.getBooleanValue());
        if (type == CellType.BLANK) return "";
        return formatter.formatCellValue(cell, evaluator);
    }

    private static CellType effectiveType(Cell cell, FormulaEvaluator evaluator) {
        if (cell.getCellType() != CellType.FORMULA) return cell.getCellType();
        CellValue evaluated = evaluator.evaluate(cell);
        if (evaluated == null) throw new IllegalArgumentException("Formula has no value");
        return evaluated.getCellType();
    }

    private static BigDecimal numericValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell.getCellType() == CellType.FORMULA) return BigDecimal.valueOf(evaluator.evaluate(cell).getNumberValue());
        if (cell instanceof XSSFCell xssf && xssf.getRawValue() != null) return new BigDecimal(xssf.getRawValue());
        return BigDecimal.valueOf(cell.getNumericCellValue());
    }

    private String canonicalField(String header) {
        return switch (header) {
            case "ngay" -> "date";
            case "sopo" -> "poNumber";
            case "mancc" -> "supplierCode";
            case "nhacungcap", "tenncc" -> "supplierName";
            case "diachi" -> "address";
            case "mahang" -> "materialCode";
            case "tenhang" -> "materialName";
            case "dvt" -> "unit";
            case "soluong" -> "quantity";
            case "dongia" -> "unitPrice";
            case "nguoilap" -> "preparedBy";
            case "quycach" -> "specification";
            case "ghichu" -> "note";
            case "vat" -> "vatPercent";
            case "loaitien" -> "currency";
            default -> null;
        };
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Không thể lưu dữ liệu preview", exception); }
    }
    private Map<String, String> readJson(String value) {
        try { return mapper.readValue(value == null ? "{}" : value, STRING_MAP); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Dữ liệu staging JSON bị lỗi", exception); }
    }
    private void required(Map<String, String> data, String key, String code, String label, List<String> issues) {
        if (value(data, key) == null) issues.add("ERROR:" + code + ": Thiếu " + label);
    }
    private static String value(Map<String, String> data, String key) {
        if (data == null) return null;
        String result = data.get(key);
        return result == null || result.isBlank() ? null : result.trim();
    }
    private static BigDecimal parseDecimal(String text) {
        return LegacyImportValues.decimalText(text);
    }
    private static BigDecimal parseFieldDecimal(Map<String, String> data, String field) {
        return LegacyImportValues.decimal(data, field);
    }
    private static LocalDate parseDate(String text) {
        return LegacyImportValues.date(text);
    }
    private static CurrencyCode parseCurrency(String text) {
        return LegacyImportValues.currency(text);
    }
    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) return "legacy-import.xlsx";
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        return name.length() > 240 ? name.substring(name.length() - 240) : name;
    }
    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String safeGroup(String value) { return value == null ? "" : value.trim(); }
    private static String firstValue(List<StoredRow> rows, String field) {
        return rows.stream().map(row -> value(row.data(), field)).filter(value -> value != null).findFirst().orElse(null);
    }
    private static void indexSupplier(Supplier entity, Map<String, Supplier> byCode, Map<String, Supplier> byName) {
        if (entity.getCode() != null) byCode.put(entity.getCode().trim().toLowerCase(Locale.ROOT), entity);
        String key = TextNormalizer.normalize(entity.getName());
        if (!byName.containsKey(key)) byName.put(key, entity);
        else if (byName.get(key) != entity) byName.put(key, null);
    }
    private static Supplier findSupplier(String code, String name, Map<String, Supplier> byCode, Map<String, Supplier> byName) {
        if (code != null) return byCode.get(code.trim().toLowerCase(Locale.ROOT));
        return name == null ? null : byName.get(TextNormalizer.normalize(name));
    }
    private static void indexMaterial(Material entity, Map<String, Material> byCode, Map<String, Material> byName) {
        if (entity.getCode() != null) byCode.put(entity.getCode().trim().toLowerCase(Locale.ROOT), entity);
        String key = TextNormalizer.normalize(entity.getName());
        if (!byName.containsKey(key)) byName.put(key, entity);
        else if (byName.get(key) != entity) byName.put(key, null);
    }
    private static Material findMaterial(String code, String name, Map<String, Material> byCode, Map<String, Material> byName) {
        if (code != null) return byCode.get(code.trim().toLowerCase(Locale.ROOT));
        return name == null ? null : byName.get(TextNormalizer.normalize(name));
    }
    private void updateDailySequenceFromImportedNumber(String number, LocalDate date) {
        if (date == null) return;
        var matcher = PO_NUMBER.matcher(number);
        if (!matcher.matches()) return;
        try {
            int seq = Integer.parseInt(matcher.group(2));
            jdbc.update("INSERT INTO po_daily_sequences(sequence_date,next_number) VALUES (?,?) ON DUPLICATE KEY UPDATE next_number=GREATEST(next_number, ?)",
                    java.sql.Date.valueOf(date), seq, seq);
        } catch (NumberFormatException ignored) { }
    }
    private record LegacyRow(String sheet, int rowNumber, Map<String, String> raw, Map<String, String> mapped, String status, List<String> issues) {}
    private record WorkbookData(Map<String, List<LegacyRow>> sheetRows, List<LegacyRow> rows) {}
    private record StoredRow(String sheet, int rowNumber, Map<String, String> data, String status, String issuesJson) {}
    public record RowIssue(String sheet, int rowNumber, String status, List<String> issues) {}
    public record Summary(Map<String, Integer> rowsPerSheet, int errorRows, int warningRows, int legacyPurchaseOrderGroups, int totalRows, String note) {}
    public record PreviewResult(Long batchId, String fileName, String sha256, String status, Summary summary,
                                List<RowIssue> rowIssues, boolean duplicate, String message) {}
    public record CommitResult(Long batchId, String status, int committedRows, int errorRows, int warningRows,
                               int supplierRowsProcessed, int materialRowsProcessed, int historyRowsImported,
                               int purchaseOrdersImported, String message, LegacyImportReconciler.Verification verification) {
        public CommitResult(Long batchId, String status, int committedRows, int errorRows, int warningRows,
                            int supplierRowsProcessed, int materialRowsProcessed, int historyRowsImported,
                            int purchaseOrdersImported, String message) {
            this(batchId, status, committedRows, errorRows, warningRows, supplierRowsProcessed, materialRowsProcessed,
                    historyRowsImported, purchaseOrdersImported, message, null);
        }
    }
}
