package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.service.TextNormalizer;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reads the flushed database, independently of the entities returned by the importer. */
public final class LegacyImportReconciler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private final JdbcTemplate jdbc;

    public LegacyImportReconciler(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Must run in the caller's import transaction, after flushing all JPA writes. */
    public Verification verify(long batchId, List<SourceRow> sourceRows) {
        List<SourceRow> rows = new ArrayList<>(sourceRows);
        rows.sort(Comparator.comparing(SourceRow::sheet).thenComparingInt(SourceRow::rowNumber));
        Map<RowKey, DbRow> staging = new HashMap<>();
        for (DbRow row : select("SELECT sheet_name,`row_number`,mapped_json,issues_json,status,committed_entity_type,committed_entity_id FROM import_rows WHERE batch_id=?", batchId)) {
            if (staging.put(new RowKey(row.string("sheet_name"), row.integer("row_number")), row) != null)
                throw failed(null, "stagingRows");
        }
        if (staging.size() != rows.size()) throw failed(null, "sourceRows");

        Map<Long, DbRow> histories = index(select("""
                SELECT h.*, s.id AS linked_supplier_id,s.code AS linked_supplier_code,s.name AS linked_supplier_name,
                       m.id AS linked_material_id,m.code AS linked_material_code,m.name AS linked_material_name
                FROM historical_purchases h LEFT JOIN suppliers s ON s.id=h.supplier_id
                LEFT JOIN materials m ON m.id=h.material_id WHERE h.import_batch_id=?
                """, batchId));
        Map<Long, DbRow> orders = index(select("""
                SELECT p.*,s.id AS linked_supplier_id,s.code AS linked_supplier_code,s.name AS linked_supplier_name
                FROM purchase_orders p LEFT JOIN suppliers s ON s.id=p.supplier_id
                WHERE p.source_import_batch_id=?
                """, batchId));
        List<DbRow> itemRows = select("""
                SELECT i.*,m.id AS linked_material_id,m.code AS linked_material_code,m.name AS linked_material_name
                FROM purchase_order_items i JOIN purchase_orders p ON p.id=i.purchase_order_id
                LEFT JOIN materials m ON m.id=i.material_id WHERE p.source_import_batch_id=?
                """, batchId);
        Map<ItemKey, DbRow> items = new HashMap<>();
        for (DbRow item : itemRows) {
            if (items.put(new ItemKey(item.id("purchase_order_id"), item.integer("line_no")), item) != null)
                throw failed(null, "lineNo");
        }
        Map<Long, DbRow> suppliers = index(select("""
                SELECT DISTINCT s.id,s.code,s.name,s.normalized_name,s.address
                FROM suppliers s JOIN import_rows r ON r.committed_entity_id=s.id
                WHERE r.batch_id=? AND r.sheet_name='NCC' AND r.committed_entity_type='SUPPLIER'
                """, batchId));

        Set<RowKey> checkedRows = new HashSet<>();
        Set<Long> checkedHistory = new HashSet<>();
        Map<String, List<SourceRow>> groups = new LinkedHashMap<>();
        Map<Long, SupplierExpectation> expectedSuppliers = new LinkedHashMap<>();
        int supplierRows = 0;
        for (SourceRow source : rows) {
            RowKey key = new RowKey(source.sheet(), source.rowNumber());
            if (!checkedRows.add(key)) throw failed(source, "sourceRow");
            DbRow staged = staging.get(key);
            if (staged == null) throw failed(source, "sourceRow");
            if (staged.string("status") == null || !Set.of("READY", "WARNING", "COMMITTED").contains(staged.string("status")))
                throw failed(source, "status");
            equal(source, "mappedValues", source.data(), mappedJson(staged.string("mapped_json"), source));
            switch (source.sheet()) {
                case "LICH_SU" -> {
                    long id = linkedId(source, staged, "HISTORICAL_PURCHASE");
                    DbRow history = histories.get(id);
                    if (history == null || !checkedHistory.add(id)) throw failed(source, "historyLink");
                    verifyHistory(batchId, source, staged, history);
                }
                case "DON_HANG" -> {
                    if (value(source, "poNumber") == null) throw failed(source, "poNumber");
                    groups.computeIfAbsent(value(source, "poNumber"), ignored -> new ArrayList<>()).add(source);
                }
                case "NCC" -> {
                    long id = linkedId(source, staged, "SUPPLIER");
                    if (!suppliers.containsKey(id)) throw failed(source, "supplierLink");
                    String sourceCode = value(source, "supplierCode");
                    if (sourceCode != null && !sourceCode.equalsIgnoreCase(suppliers.get(id).string("code")))
                        throw failed(source, "supplierCodeLink");
                    SupplierExpectation expectation = expectedSuppliers.computeIfAbsent(id, ignored -> new SupplierExpectation());
                    expectation.source = source;
                    expectation.name = value(source, "supplierName");
                    if (expectation.name == null) throw failed(source, "supplierName");
                    if (value(source, "supplierCode") != null) expectation.code = value(source, "supplierCode");
                    // A missing address retains the pre-import master value; supplied values must survive exactly.
                    if (value(source, "address") != null) expectation.address = value(source, "address");
                    supplierRows++;
                }
                default -> throw failed(null, "sourceSheet");
            }
        }
        if (checkedHistory.size() != histories.size()) throw failed(null, "historyRows");

        Set<Long> checkedOrders = new HashSet<>();
        int expectedItems = 0;
        for (Map.Entry<String, List<SourceRow>> entry : groups.entrySet()) {
            List<SourceRow> group = entry.getValue();
            SourceRow first = group.getFirst();
            long id = linkedId(first, staging.get(new RowKey(first.sheet(), first.rowNumber())), "PURCHASE_ORDER");
            DbRow order = orders.get(id);
            if (order == null || !checkedOrders.add(id)) throw failed(first, "purchaseOrderLink");
            verifyOrder(batchId, group, order);
            for (int index = 0; index < group.size(); index++) {
                SourceRow source = group.get(index);
                long linkedOrder = linkedId(source, staging.get(new RowKey(source.sheet(), source.rowNumber())), "PURCHASE_ORDER");
                equal(source, "purchaseOrderLink", id, linkedOrder);
                DbRow item = items.get(new ItemKey(id, index + 1));
                if (item == null) throw failed(source, "lineNo");
                verifyItem(source, item);
                expectedItems++;
            }
        }
        if (checkedOrders.size() != orders.size()) throw failed(null, "purchaseOrders");
        if (expectedItems != itemRows.size()) throw failed(null, "purchaseOrderItems");
        for (Map.Entry<Long, SupplierExpectation> entry : expectedSuppliers.entrySet()) {
            SupplierExpectation expected = entry.getValue();
            DbRow actual = suppliers.get(entry.getKey());
            if (expected.code != null) equal(expected.source, "supplierCode", expected.code, actual.string("code"));
            equal(expected.source, "supplierName", expected.name, actual.string("name"));
            equal(expected.source, "supplierNameNormalized", TextNormalizer.normalize(expected.name), actual.string("normalized_name"));
            if (expected.address != null) equal(expected.source, "address", expected.address, actual.string("address"));
        }
        return new Verification(rows.size(), checkedRows.size(), checkedHistory.size(), checkedOrders.size(), expectedItems, supplierRows);
    }

    private void verifyHistory(long batchId, SourceRow source, DbRow staged, DbRow actual) {
        equal(source, "date", date(source, "date"), actual.date("purchase_date"));
        String supplierName = value(source, "supplierName");
        if (supplierName == null && actual.id("supplier_id") != null) supplierName = actual.string("linked_supplier_name");
        equal(source, "supplierName", supplierName, actual.string("supplier_snapshot"));
        equal(source, "supplierCode", value(source, "supplierCode"), actual.string("supplier_code_snapshot"));
        equal(source, "materialCode", value(source, "materialCode"), actual.string("material_code_snapshot"));
        equal(source, "materialName", value(source, "materialName"), actual.string("material_name_snapshot"));
        equal(source, "materialNameNormalized", TextNormalizer.normalize(value(source, "materialName")), actual.string("material_name_normalized_snapshot"));
        equal(source, "unit", value(source, "unit"), actual.string("unit"));
        verifyQuantity(source, actual);
        decimalEqual(source, "unitPrice", decimal(source, "unitPrice"), actual.decimal("unit_price"));
        equal(source, "currency", currency(source, "currency"), actual.string("currency"));
        equal(source, "currencyBasis", value(source, "currency") == null ? "ASSUMED_LEGACY" : "SOURCE", actual.string("currency_basis"));
        equal(source, "source", "LEGACY_LICH_SU", actual.string("source"));
        equal(source, "sourceSheet", source.sheet(), actual.string("source_sheet"));
        equal(source, "sourceRowNumber", source.rowNumber(), actual.integer("source_row_number"));
        equal(source, "sourceReference", "LEGACY:" + batchId + ":LICH_SU:" + source.rowNumber(), actual.string("source_reference"));
        equal(source, "importBatchId", batchId, actual.id("import_batch_id"));
        equal(source, "category", "MATERIAL", actual.string("category"));
        equal(source, "dataQualityFlags", jsonTree(staged.string("issues_json"), source), jsonTree(actual.string("data_quality_flags"), source));
        verifyMasterLink(source, actual, "supplier", "supplierCode");
        verifyMasterLink(source, actual, "material", "materialCode");
    }

    private void verifyOrder(long batchId, List<SourceRow> group, DbRow actual) {
        SourceRow first = group.getFirst();
        equal(first, "sourcePoNumber", value(first, "poNumber"), actual.string("source_po_number"));
        if (actual.string("po_number") == null || actual.string("po_number").isBlank()) throw failed(first, "poNumber");
        equal(first, "importBatchId", batchId, actual.id("source_import_batch_id"));
        SourceRow dateRow = firstWithValue(group, "date");
        equal(first, "date", date(dateRow, "date"), actual.date("order_date"));
        String supplierName = firstValue(group, "supplierName");
        if (supplierName == null && actual.id("supplier_id") != null) supplierName = actual.string("linked_supplier_name");
        if (supplierName == null) supplierName = "Nhà cung cấp chưa xác định";
        equal(first, "supplierName", supplierName, actual.string("supplier_name_snapshot"));
        equal(first, "address", firstValue(group, "address"), actual.string("supplier_address_snapshot"));
        equal(first, "currency", currency(firstWithValue(group, "currency"), "currency"), actual.string("currency"));
        decimalEqual(first, "vatPercent", decimal(first, "vatPercent"), actual.decimal("vat_percent"));
        equal(first, "preparedBy", firstValue(group, "preparedBy"), actual.string("prepared_by"));
        equal(first, "note", firstValue(group, "note"), actual.string("note"));
        equal(first, "status", "EXPORTED", actual.string("status"));
        equal(first, "revision", 1, actual.integer("revision"));
        verifyMasterLink(firstValue(group, "supplierCode") == null ? firstWithValue(group, "supplierName")
                : firstWithValue(group, "supplierCode"), actual, "supplier", "supplierCode");
    }

    private void verifyItem(SourceRow source, DbRow actual) {
        equal(source, "materialCode", value(source, "materialCode"), actual.string("material_code_snapshot"));
        equal(source, "materialName", value(source, "materialName"), actual.string("material_name"));
        equal(source, "specification", value(source, "specification"), actual.string("specification"));
        equal(source, "unit", value(source, "unit"), actual.string("unit"));
        verifyQuantity(source, actual);
        decimalEqual(source, "unitPrice", decimal(source, "unitPrice"), actual.decimal("unit_price"));
        verifyMasterLink(source, actual, "material", "materialCode");
    }

    private static void verifyQuantity(SourceRow source, DbRow actual) {
        BigDecimal quantity = decimal(source, "quantity");
        decimalEqual(source, "quantity", quantity, actual.decimal("quantity"));
        equal(source, "quantityText", quantity == null ? value(source, "quantity") : null, actual.string("quantity_text"));
    }

    private static void verifyMasterLink(SourceRow source, DbRow actual, String kind, String codeField) {
        Long id = actual.id(kind + "_id");
        if (id != null && !Objects.equals(id, actual.id("linked_" + kind + "_id"))) throw failed(source, kind + "Link");
        String code = value(source, codeField);
        if (code != null && (id == null || !code.equalsIgnoreCase(actual.string("linked_" + kind + "_code"))))
            throw failed(source, kind + "CodeLink");
        String name = value(source, kind + "Name");
        if (code == null && id != null && (name == null || !TextNormalizer.normalize(name)
                .equals(TextNormalizer.normalize(actual.string("linked_" + kind + "_name")))))
            throw failed(source, kind + "NameLink");
    }

    private List<DbRow> select(String sql, long batchId) { return jdbc.queryForList(sql, batchId).stream().map(DbRow::new).toList(); }

    private static Map<Long, DbRow> index(List<DbRow> rows) {
        Map<Long, DbRow> result = new HashMap<>();
        for (DbRow row : rows) if (result.put(row.id("id"), row) != null) throw failed(null, "entityId");
        return result;
    }

    private static long linkedId(SourceRow source, DbRow staged, String expectedType) {
        equal(source, "committedEntityType", expectedType, staged.string("committed_entity_type"));
        Long id = staged.id("committed_entity_id");
        if (id == null) throw failed(source, "committedEntityId");
        return id;
    }

    private static String value(SourceRow source, String field) { return LegacyImportValues.value(source.data(), field); }
    private static String firstValue(List<SourceRow> group, String field) { return value(firstWithValue(group, field), field); }
    private static SourceRow firstWithValue(List<SourceRow> group, String field) {
        return group.stream().filter(row -> value(row, field) != null).findFirst().orElse(group.getFirst());
    }

    private static BigDecimal decimal(SourceRow source, String field) {
        BigDecimal result = LegacyImportValues.decimal(source.data(), field);
        String text = value(source, field);
        if (result == null && text != null && (!"quantity".equals(field)
                || "true".equals(source.data().get(field + "__numeric")) || LegacyImportValues.looksNumeric(text)))
            throw failed(source, field);
        if (result == null && "unitPrice".equals(field)) throw failed(source, field);
        return result;
    }

    private static LocalDate date(SourceRow source, String field) {
        LocalDate result = LegacyImportValues.date(value(source, field));
        if (result == null && value(source, field) != null) throw failed(source, field);
        return result;
    }

    private static String currency(SourceRow source, String field) {
        try { return LegacyImportValues.currency(value(source, field)).name(); }
        catch (IllegalArgumentException exception) { throw failed(source, field); }
    }

    private static Map<String, String> mappedJson(String json, SourceRow source) {
        try { return json == null ? null : JSON.readValue(json, STRING_MAP); }
        catch (JsonProcessingException exception) { throw failed(source, "mappedValues"); }
    }

    private static Object jsonTree(String json, SourceRow source) {
        try {
            if (json == null) return null;
            var tree = JSON.readTree(json);
            // Hibernate's H2 JSON mapping stores a JSON string; MySQL stores the JSON value itself.
            return tree != null && tree.isTextual() ? JSON.readTree(tree.textValue()) : tree;
        }
        catch (JsonProcessingException exception) { throw failed(source, "dataQualityFlags"); }
    }

    private static void equal(SourceRow source, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw failed(source, field);
    }

    private static void decimalEqual(SourceRow source, String field, BigDecimal expected, BigDecimal actual) {
        if (expected == null ? actual != null : actual == null || expected.compareTo(actual) != 0) throw failed(source, field);
    }

    private static ApiException failed(SourceRow source, String field) {
        String location = source == null ? "số lượng hoặc liên kết của lô" : "sheet " + source.sheet() + ", dòng " + source.rowNumber();
        return ApiException.conflict("IMPORT_RECONCILIATION_FAILED", "Đối chiếu dữ liệu không khớp tại " + location
                + ", trường " + field + ". Lô chưa được commit; toàn bộ thay đổi của lượt nhập sẽ được hoàn tác.");
    }

    public record SourceRow(String sheet, int rowNumber, Map<String, String> data) {
        public SourceRow { data = Collections.unmodifiableMap(new LinkedHashMap<>(data)); }
    }
    public record Verification(int sourceRows, int verifiedRows, int historyRows, int purchaseOrders,
                               int purchaseOrderItems, int supplierRows) {}
    private record RowKey(String sheet, int rowNumber) {}
    private record ItemKey(long orderId, int lineNo) {}
    private static final class SupplierExpectation {
        private SourceRow source;
        private String code;
        private String name;
        private String address;
    }
    private record DbRow(Map<String, Object> data) {
        String string(String field) {
            Object value = data.get(field);
            return value == null ? null : value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : value.toString();
        }
        Long id(String field) { Object value = data.get(field); return value == null ? null : ((Number) value).longValue(); }
        Integer integer(String field) { Object value = data.get(field); return value == null ? null : ((Number) value).intValue(); }
        BigDecimal decimal(String field) { Object value = data.get(field); return value == null ? null : new BigDecimal(value.toString()); }
        LocalDate date(String field) {
            Object value = data.get(field);
            return value == null ? null : value instanceof Date date ? date.toLocalDate() : LocalDate.parse(value.toString());
        }
    }
}
