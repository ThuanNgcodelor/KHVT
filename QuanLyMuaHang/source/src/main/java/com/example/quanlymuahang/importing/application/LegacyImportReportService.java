package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.domain.importbatch.ImportBatch;
import com.example.quanlymuahang.repository.ImportBatchRepository;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Read-only reports: every issue is exported, without source values or CONFIG data. */
@Service
@Transactional(readOnly = true)
public class LegacyImportReportService {
    private final ImportBatchRepository batches;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public LegacyImportReportService(ImportBatchRepository batches, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.batches = batches; this.jdbc = jdbc; this.mapper = mapper;
    }

    public LegacyWorkbookImportService.PreviewResult preview(long id) {
        ImportBatch batch = batch(id);
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> numbers = new HashSet<>();
        List<LegacyWorkbookImportService.RowIssue> issues = new ArrayList<>();
        int errors = 0, warnings = 0;
        List<ReportRow> rows = rows(id);
        for (ReportRow row : rows) {
            counts.merge(row.sheet(), 1, Integer::sum);
            List<String> messages = messages(row.issues());
            boolean error = messages.stream().anyMatch(value -> value.startsWith("ERROR:"));
            if (error) errors++; else if (!messages.isEmpty()) warnings++;
            if (!messages.isEmpty() && issues.size() < 30)
                issues.add(new LegacyWorkbookImportService.RowIssue(row.sheet(), row.number(), error ? "ERROR" : "WARNING", messages));
            if (row.sheet().equals("DON_HANG")) {
                String number = data(row.mapped()).get("poNumber");
                if (number != null && !number.isBlank()) numbers.add(number.trim());
            }
        }
        var summary = new LegacyWorkbookImportService.Summary(counts, errors, warnings, numbers.size(), rows.size(),
                "CONFIG được bỏ qua. Cảnh báo là thông tin cần xác minh; không phải dữ liệu đã được duyệt nghiệp vụ.");
        return new LegacyWorkbookImportService.PreviewResult(batch.getId(), batch.getFileName(), batch.getSha256(),
                batch.getStatus().name(), summary, issues, false, null);
    }

    public byte[] issuesCsv(long id) {
        batch(id);
        StringBuilder csv = new StringBuilder("\uFEFFSheet,DongExcel,MucDo,Ma,MoTa\r\n");
        for (ReportRow row : rows(id)) {
            for (String issue : messages(row.issues())) {
                String[] parts = issue.split(":", 3);
                csv.append(cell(row.sheet())).append(',').append(row.number()).append(',')
                        .append(cell(parts[0])).append(',').append(cell(parts.length > 1 ? parts[1].trim() : ""))
                        .append(',').append(cell(parts.length > 2 ? parts[2].trim() : "")).append("\r\n");
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ImportBatch batch(long id) {
        ImportBatch batch = batches.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy lô import"));
        if (!"LEGACY_WORKBOOK".equals(batch.getMode())) throw ApiException.badRequest("INVALID_IMPORT_MODE", "Lô import không phải workbook legacy");
        return batch;
    }

    private List<ReportRow> rows(long id) {
        return jdbc.query("SELECT sheet_name,`row_number`,mapped_json,issues_json FROM import_rows WHERE batch_id=? ORDER BY CASE sheet_name WHEN 'NCC' THEN 1 WHEN 'LICH_SU' THEN 2 ELSE 3 END,`row_number`",
                (rs, index) -> new ReportRow(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)), id);
    }

    private List<String> messages(String json) {
        if (json == null) return List.of();
        try { return mapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Không đọc được báo cáo staging", error); }
    }

    private Map<String, String> data(String json) {
        try { return mapper.readValue(json, new TypeReference<Map<String, String>>() {}); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Không đọc được mapping staging", error); }
    }

    private static String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private record ReportRow(String sheet, int number, String mapped, String issues) {}
}
