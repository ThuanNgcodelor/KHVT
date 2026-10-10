package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.importing.application.LegacyImportReconciler.SourceRow;
import com.example.quanlymuahang.service.TextNormalizer;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegacyImportReconcilerTest {
    private static final long BATCH = 7L;
    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate jdbc;
    private LegacyImportReconciler reconciler;
    private List<SourceRow> rows;

    @BeforeEach void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:reconcile-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        reconciler = new LegacyImportReconciler(jdbc);
        jdbc.execute("CREATE TABLE suppliers(id BIGINT PRIMARY KEY,code VARCHAR(50),name VARCHAR(500),normalized_name VARCHAR(500),address VARCHAR(1000))");
        jdbc.execute("CREATE TABLE materials(id BIGINT PRIMARY KEY,code VARCHAR(80),name VARCHAR(500))");
        jdbc.execute("CREATE TABLE import_rows(batch_id BIGINT,sheet_name VARCHAR(80),`row_number` INT,mapped_json CLOB,issues_json CLOB,status VARCHAR(20),committed_entity_type VARCHAR(60),committed_entity_id BIGINT)");
        jdbc.execute("""
                CREATE TABLE historical_purchases(id BIGINT PRIMARY KEY,purchase_date DATE,supplier_id BIGINT,
                supplier_snapshot VARCHAR(500),supplier_code_snapshot VARCHAR(50),material_id BIGINT,material_code_snapshot VARCHAR(80),
                material_name_snapshot VARCHAR(500),material_name_normalized_snapshot VARCHAR(500),unit VARCHAR(100),
                quantity DECIMAL(38,18),quantity_text VARCHAR(255),unit_price DECIMAL(38,18),currency VARCHAR(3),currency_basis VARCHAR(30),
                source VARCHAR(30),source_sheet VARCHAR(80),source_row_number INT,import_batch_id BIGINT,data_quality_flags CLOB,
                source_reference VARCHAR(120),category VARCHAR(20))
                """);
        jdbc.execute("""
                CREATE TABLE purchase_orders(id BIGINT PRIMARY KEY,po_number VARCHAR(40),order_date DATE,supplier_id BIGINT,
                supplier_name_snapshot VARCHAR(500),supplier_address_snapshot VARCHAR(1000),currency VARCHAR(3),vat_percent DECIMAL(5,2),
                prepared_by VARCHAR(255),note VARCHAR(1000),status VARCHAR(20),revision INT,source_po_number VARCHAR(40),source_import_batch_id BIGINT)
                """);
        jdbc.execute("""
                CREATE TABLE purchase_order_items(id BIGINT PRIMARY KEY,purchase_order_id BIGINT,line_no INT,material_id BIGINT,
                material_code_snapshot VARCHAR(80),material_name VARCHAR(500),specification VARCHAR(1000),unit VARCHAR(100),
                quantity DECIMAL(38,18),quantity_text VARCHAR(255),unit_price DECIMAL(38,18))
                """);
        jdbc.update("INSERT INTO suppliers VALUES (1,'NCC-01',?,?,?)", "NCC tên cuối", TextNormalizer.normalize("NCC tên cuối"), "Địa chỉ nguồn");
        jdbc.update("INSERT INTO materials VALUES (2,'VT-01','Tên danh mục')");

        SourceRow ncc1 = row("NCC", 2, "supplierCode", "NCC-01", "supplierName", "NCC tên trước", "address", "Địa chỉ nguồn");
        SourceRow ncc2 = row("NCC", 5, "supplierCode", "NCC-01", "supplierName", "NCC tên cuối");
        SourceRow history1 = row("LICH_SU", 2, "date", "2024-02-29", "supplierCode", "NCC-01", "supplierName", "NCC snapshot gốc",
                "materialCode", "VT-01", "materialName", "Vật tư nguồn", "unit", "kg", "quantity", "1.234567890123", "quantity__numeric", "true",
                "unitPrice", "987.654321098765", "unitPrice__numeric", "true", "currency", "USD");
        SourceRow history2 = row("LICH_SU", 8, "materialName", "Vật tư không mã", "quantity", "Theo thực tế", "unitPrice", "2.345,67");
        SourceRow order1 = row("DON_HANG", 3, "poNumber", "PO-TEST", "date", "29/2/2024", "supplierCode", "NCC-01", "supplierName", "NCC PO gốc",
                "address", "Địa chỉ PO", "currency", "VND", "vatPercent", "8", "preparedBy", "Kỹ sư An", "note", "Ghi chú nguồn",
                "materialCode", "VT-01", "materialName", "Tên hàng PO", "specification", "Quy cách gốc", "unit", "kg",
                "quantity", "0.123456789012", "quantity__numeric", "true", "unitPrice", "1.987654321012", "unitPrice__numeric", "true");
        SourceRow order2 = row("DON_HANG", 9, "poNumber", "PO-TEST", "date", "29/2/2024", "supplierCode", "NCC-01", "supplierName", "NCC PO gốc",
                "address", "Địa chỉ PO", "currency", "VND", "vatPercent", "8", "preparedBy", "Kỹ sư An", "note", "Ghi chú nguồn",
                "materialName", "Hàng âm hoặc bằng không", "quantity", "-1", "quantity__numeric", "true", "unitPrice", "0", "unitPrice__numeric", "true");
        rows = new ArrayList<>(List.of(ncc1, ncc2, history1, history2, order1, order2));
        stage(ncc1, "SUPPLIER", 1); stage(ncc2, "SUPPLIER", 1);
        stage(history1, "HISTORICAL_PURCHASE", 10); stage(history2, "HISTORICAL_PURCHASE", 11);
        stage(order1, "PURCHASE_ORDER", 20); stage(order2, "PURCHASE_ORDER", 20);
        history(history1, 10, 1L, 2L); history(history2, 11, null, null);
        jdbc.update("INSERT INTO purchase_orders VALUES (20,'PO-TEST-2',?,1,?,'Địa chỉ PO','VND',8,'Kỹ sư An','Ghi chú nguồn','EXPORTED',1,'PO-TEST',?)",
                Date.valueOf("2024-02-29"), "NCC PO gốc", BATCH);
        item(order1, 30, 1, 2L); item(order2, 31, 2, null);
    }

    @AfterEach void shutdown() { jdbc.execute("SHUTDOWN"); }

    @Test void verifiesEverySourceRowAndExactFractionsWithDuplicateSupplierRows() {
        Collections.reverse(rows);
        var result = reconciler.verify(BATCH, rows);
        assertEquals(new LegacyImportReconciler.Verification(6, 6, 2, 1, 2, 2), result);
        assertEquals(new BigDecimal("987.654321098765000000"), jdbc.queryForObject("SELECT unit_price FROM historical_purchases WHERE id=10", BigDecimal.class));
        assertEquals("Địa chỉ nguồn", jdbc.queryForObject("SELECT address FROM suppliers WHERE id=1", String.class));
    }

    @ParameterizedTest
    @CsvSource({"quantity,quantity,1.234567890124", "unit_price,unitPrice,987.654321098766"})
    void rejectsOneTrillionthValueCorruption(String column, String field, String value) {
        jdbc.update("UPDATE historical_purchases SET " + column + "=? WHERE id=10", new BigDecimal(value));
        ApiException error = failure();
        assertTrue(error.getMessage().contains(field));
        assertFalse(error.getMessage().contains(value));
    }

    @Test void detectsRoundingByAnOlderSqlScaleInsteadOfRoundingTheExpectedValue() {
        jdbc.execute("ALTER TABLE historical_purchases ALTER COLUMN unit_price DECIMAL(20,4)");
        ApiException error = failure();
        assertTrue(error.getMessage().contains("unitPrice"));
    }

    @Test void rejectsDroppedPurchaseOrderLine() {
        jdbc.update("DELETE FROM purchase_order_items WHERE id=31");
        ApiException error = failure();
        assertTrue(error.getMessage().contains("dòng 9"));
        assertTrue(error.getMessage().contains("lineNo"));
    }

    @Test void rejectsExtraPurchaseOrderLine() {
        jdbc.update("INSERT INTO purchase_order_items(id,purchase_order_id,line_no) VALUES (32,20,3)");
        assertTrue(failure().getMessage().contains("purchaseOrderItems"));
    }

    @ParameterizedTest
    @CsvSource({"source_reference,sourceReference", "currency_basis,currencyBasis", "material_code_snapshot,materialCode", "supplier_snapshot,supplierName"})
    void rejectsCorruptedHistoryMetadataAndSnapshots(String column, String field) {
        jdbc.update("UPDATE historical_purchases SET " + column + "='CHANGED' WHERE id=10");
        assertTrue(failure().getMessage().contains(field));
    }

    @Test void rejectsExplicitCurrencyStoredAsAssumed() {
        jdbc.update("UPDATE historical_purchases SET currency_basis='ASSUMED_LEGACY' WHERE id=10");
        assertTrue(failure().getMessage().contains("currencyBasis"));
    }

    @Test void rejectsMislinkedSupplierEvenWhenBothMastersExist() {
        jdbc.update("INSERT INTO suppliers VALUES (3,'NCC-OTHER',?,?,?)", "NCC tên cuối", TextNormalizer.normalize("NCC tên cuối"), "Địa chỉ nguồn");
        jdbc.update("UPDATE import_rows SET committed_entity_id=3 WHERE sheet_name='NCC' AND `row_number`=2");
        assertTrue(failure().getMessage().contains("supplierCodeLink"));
    }

    @Test void rejectsRawSupplierNameLossEvenWhenNormalizationIsUnchanged() {
        jdbc.update("UPDATE suppliers SET name='NCC ten cuoi' WHERE id=1");
        assertTrue(failure().getMessage().contains("supplierName"));
    }

    @Test void rejectsUnlinkedOrDoubleLinkedHistory() {
        jdbc.update("UPDATE import_rows SET committed_entity_id=10 WHERE sheet_name='LICH_SU' AND `row_number`=8");
        assertTrue(failure().getMessage().contains("historyLink"));
    }

    @Test void checksHeaderAndDoesNotAcceptTheWrongNote() {
        jdbc.update("UPDATE purchase_orders SET note='CHANGED' WHERE id=20");
        assertTrue(failure().getMessage().contains("note"));
    }

    @Test void comparesJsonFlagsByContentInsteadOfMysqlWhitespace() {
        jdbc.update("UPDATE import_rows SET issues_json='[ \"WARNING:SOURCE\" ]' WHERE sheet_name='LICH_SU' AND `row_number`=2");
        jdbc.update("UPDATE historical_purchases SET data_quality_flags='[\"WARNING:SOURCE\"]' WHERE id=10");
        assertEquals(6, reconciler.verify(BATCH, rows).verifiedRows());
    }

    @Test void alsoReadsTheH2JsonRepresentationUsedByHibernateIntegrationTests() {
        jdbc.execute("ALTER TABLE historical_purchases ALTER COLUMN data_quality_flags JSON");
        jdbc.update("UPDATE historical_purchases SET data_quality_flags='[]'");
        assertEquals(6, reconciler.verify(BATCH, rows).verifiedRows());
    }

    @Test void rejectsMissingStagingStatusWithTheReconciliationError() {
        jdbc.update("UPDATE import_rows SET status=NULL WHERE sheet_name='LICH_SU' AND `row_number`=2");
        assertTrue(failure().getMessage().contains("status"));
    }

    @Test void rejectsAnExtraHistoryArtifactWithoutAStagingRow() {
        jdbc.update("INSERT INTO historical_purchases(id,import_batch_id) VALUES (99,?)", BATCH);
        assertTrue(failure().getMessage().contains("historyRows"));
    }

    @Test void detectsStagingValueMutation() throws Exception {
        jdbc.update("UPDATE import_rows SET mapped_json=? WHERE sheet_name='LICH_SU' AND `row_number`=2", mapper.writeValueAsString(Map.of("materialName", "CHANGED")));
        assertTrue(failure().getMessage().contains("mappedValues"));
    }

    @Test void mismatchRollsBackAllWritesMadeInTheCallingTransaction() {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(ApiException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE suppliers SET address='TRANSACTION-CHANGE' WHERE id=1");
            jdbc.update("UPDATE historical_purchases SET unit_price=1 WHERE id=10");
            reconciler.verify(BATCH, rows);
        }));
        assertEquals("Địa chỉ nguồn", jdbc.queryForObject("SELECT address FROM suppliers WHERE id=1", String.class));
        assertEquals(new BigDecimal("987.654321098765000000"), jdbc.queryForObject("SELECT unit_price FROM historical_purchases WHERE id=10", BigDecimal.class));
    }

    private ApiException failure() {
        ApiException error = assertThrows(ApiException.class, () -> reconciler.verify(BATCH, rows));
        assertEquals("IMPORT_RECONCILIATION_FAILED", error.code());
        assertEquals(409, error.status().value());
        return error;
    }

    private static SourceRow row(String sheet, int number, String... fields) {
        Map<String, String> data = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) data.put(fields[index], fields[index + 1]);
        return new SourceRow(sheet, number, data);
    }

    private void stage(SourceRow source, String type, long id) throws Exception {
        jdbc.update("INSERT INTO import_rows VALUES (?,?,?,?,?,'READY',?,?)", BATCH, source.sheet(), source.rowNumber(),
                mapper.writeValueAsString(source.data()), "[]", type, id);
    }

    private void history(SourceRow source, long id, Long supplierId, Long materialId) {
        Map<String, String> data = source.data();
        BigDecimal quantity = LegacyImportValues.decimal(data, "quantity");
        jdbc.update("""
                INSERT INTO historical_purchases VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, LegacyImportValues.date(data.get("date")), supplierId, data.get("supplierName"), data.get("supplierCode"),
                materialId, data.get("materialCode"), data.get("materialName"), TextNormalizer.normalize(data.get("materialName")),
                data.get("unit"), quantity, quantity == null ? data.get("quantity") : null, LegacyImportValues.decimal(data, "unitPrice"),
                LegacyImportValues.currency(data.get("currency")).name(), data.get("currency") == null ? "ASSUMED_LEGACY" : "SOURCE",
                "LEGACY_LICH_SU", "LICH_SU", source.rowNumber(), BATCH, "[]", "LEGACY:" + BATCH + ":LICH_SU:" + source.rowNumber(), "MATERIAL");
    }

    private void item(SourceRow source, long id, int line, Long materialId) {
        Map<String, String> data = source.data();
        BigDecimal quantity = LegacyImportValues.decimal(data, "quantity");
        jdbc.update("INSERT INTO purchase_order_items VALUES (?,20,?,?,?,?,?,?,?,?,?)", id, line, materialId, data.get("materialCode"),
                data.get("materialName"), data.get("specification"), data.get("unit"), quantity,
                quantity == null ? data.get("quantity") : null, LegacyImportValues.decimal(data, "unitPrice"));
    }
}
