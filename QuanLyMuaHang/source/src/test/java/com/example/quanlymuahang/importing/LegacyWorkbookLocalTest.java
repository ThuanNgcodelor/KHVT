package com.example.quanlymuahang.importing;

import com.example.quanlymuahang.importing.application.LegacyWorkbookImportService;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, isolated H2 file test. Does not read .env or connect to MySQL/Redis. */
@SpringBootTest(properties = {"app.bootstrap-admin.email=", "app.bootstrap-admin.password=", "spring.jpa.hibernate.ddl-auto=create", "logging.level.org.springframework.jdbc=WARN", "logging.level.org.hibernate.SQL=WARN"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "QMH_TEST_WORKBOOK", matches = ".+")
class LegacyWorkbookLocalTest {
    private static final String DATABASE = "workbook-h2-" + UUID.randomUUID();
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:file:./target/runtime/" + DATABASE + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=120000");
    }
    @Autowired LegacyWorkbookImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test void previewReconcileAndCommitOnlyWhenTheOriginalWorkbookHasNoErrors() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS import_rows(id BIGINT AUTO_INCREMENT PRIMARY KEY,batch_id BIGINT,sheet_name VARCHAR(80),`row_number` INT,raw_json CLOB,mapped_json CLOB,issues_json CLOB,status VARCHAR(20),committed_entity_type VARCHAR(80),committed_entity_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS audit_logs(id BIGINT AUTO_INCREMENT PRIMARY KEY,actor_user_id BIGINT,action VARCHAR(80),entity_type VARCHAR(80),entity_id BIGINT,after_json CLOB,request_id VARCHAR(64),created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS po_daily_sequences(sequence_date DATE PRIMARY KEY,next_number INT NOT NULL)");
        Path path = Path.of(System.getenv("QMH_TEST_WORKBOOK")).toAbsolutePath();
        byte[] bytes = Files.readAllBytes(path);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            // Count populated source rows independently; never access CONFIG.
            for (String name : List.of("NCC", "LICH_SU", "DON_HANG")) {
                var sheet = workbook.getSheet(name); assertNotNull(sheet);
                int count = 0;
                for (var row : sheet) {
                    if (row.getRowNum() == sheet.getFirstRowNum()) continue;
                    boolean populated = false;
                    for (var cell : row) if (!cell.toString().isBlank()) { populated = true; break; }
                    if (populated) count++;
                }
                sourceCounts.put(name, count);
            }
        }
        var file = new MockMultipartFile("file", path.getFileName().toString(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var preview = imports.preview(file, 1L);
        assertEquals(sourceCounts, preview.summary().rowsPerSheet());
        assertEquals(sha, preview.sha256());
        assertEquals(preview.summary().totalRows(), jdbc.queryForObject("SELECT COUNT(*) FROM import_rows WHERE batch_id=?", Integer.class, preview.batchId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM historical_purchases", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM import_rows WHERE sheet_name='CONFIG'", Integer.class));
        var duplicate = imports.preview(file, 1L);
        assertTrue(duplicate.duplicate()); assertEquals(preview.batchId(), duplicate.batchId());
        assertEquals(preview.summary(), duplicate.summary());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("checkedAt", Instant.now().toString()); report.put("databaseEngine", "H2 MySQL mode - not MySQL/Redis validation");
        report.put("databaseFile", "target/runtime/" + DATABASE + ".mv.db"); report.put("workbook", path.getFileName().toString());
        report.put("sha256", sha); report.put("sourceCounts", sourceCounts); report.put("summary", preview.summary());
        report.put("issueCodes", jdbc.queryForList("SELECT issues_json FROM import_rows WHERE batch_id=?", String.class, preview.batchId()).stream()
                .flatMap(json -> { try { return Arrays.stream(mapper.readValue(json, String[].class)); } catch (Exception error) { throw new IllegalStateException(error); } })
                .map(issue -> { String[] parts = issue.split(":", 3); return parts.length >= 2 ? parts[0] + ":" + parts[1] : parts[0]; })
                .collect(java.util.stream.Collectors.groupingBy(code -> code, TreeMap::new, java.util.stream.Collectors.counting())));
        if (preview.summary().errorRows() > 0) {
            ApiException rejected = assertThrows(ApiException.class, () -> imports.commit(preview.batchId(), 1L));
            assertEquals(409, rejected.status().value()); report.put("commit", "REJECTED_SOURCE_ERRORS");
            report.put("rejectionCode", rejected.code());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM historical_purchases", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders", Integer.class));
        } else {
            LegacyWorkbookImportService.CommitResult committed;
            // Two requests for one batch must produce one commit, not duplicate history.
            try (var executor = Executors.newFixedThreadPool(2)) {
                Callable<Object> attempt = () -> { try { return imports.commit(preview.batchId(), 1L); } catch (ApiException error) { return error; } };
                var attempts = executor.invokeAll(List.of(attempt, attempt));
                var results = List.of(attempts.get(0).get(), attempts.get(1).get());
                var successful = results.stream().filter(result -> result instanceof LegacyWorkbookImportService.CommitResult).toList();
                assertEquals(1, successful.size());
                committed = (LegacyWorkbookImportService.CommitResult) successful.getFirst();
                var rejected = results.stream().filter(result -> result instanceof ApiException).map(result -> (ApiException) result).toList();
                assertEquals(1, rejected.size()); assertEquals("IMPORT_ALREADY_PROCESSED", rejected.getFirst().code());
                report.put("concurrentCommit", "one committed, one rejected");
            }
            assertEquals(sourceCounts.get("LICH_SU").intValue(), committed.historyRowsImported());
            assertEquals(preview.summary().legacyPurchaseOrderGroups(), committed.purchaseOrdersImported());
            assertEquals(committed.historyRowsImported(), jdbc.queryForObject("SELECT COUNT(*) FROM historical_purchases", Integer.class));
            assertEquals(committed.purchaseOrdersImported(), jdbc.queryForObject("SELECT COUNT(*) FROM purchase_orders", Integer.class));
            assertEquals(409, assertThrows(ApiException.class, () -> imports.commit(preview.batchId(), 1L)).status().value());
            var afterCommit = imports.preview(file, 1L);
            assertTrue(afterCommit.duplicate()); assertEquals("COMMITTED", afterCommit.status());
            assertEquals(preview.summary(), afterCommit.summary()); assertEquals(preview.rowIssues(), afterCommit.rowIssues());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM import_batches", Integer.class));
            report.put("commit", committed);
            report.put("replayAfterCommit", "same batch, warnings and groups preserved");
        }
        assertEquals(sha, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
        report.put("originalUnchanged", true); report.put("completed", true);
        Path reportPath = Path.of("target/runtime/workbook-h2-report.json");
        Files.writeString(reportPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("WORKBOOK TEST PASS: isolated H2; sanitized report: " + reportPath);
    }
}
