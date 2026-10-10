package com.example.quanlymuahang.importing;

import com.example.quanlymuahang.domain.importbatch.ImportBatch;
import com.example.quanlymuahang.importing.application.LegacyImportReportService;
import com.example.quanlymuahang.importing.application.OperationalRequestImportService;
import com.example.quanlymuahang.repository.ImportBatchRepository;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyImportReportTest {
    @Test void fullCsvDoesNotLoseIssuesPastTheThirtyRowPreviewLimitOrExportSourceValues() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE import_rows(batch_id BIGINT,sheet_name VARCHAR(80),`row_number` INT,mapped_json CLOB,issues_json CLOB)");
        var batches = mock(ImportBatchRepository.class);
        var batch = new ImportBatch("synthetic.xlsx", "test-sha", "XLSX", "LEGACY_WORKBOOK");
        when(batches.findById(1L)).thenReturn(Optional.of(batch));
        var mapper = new ObjectMapper();
        for (int row = 2; row < 37; row++)
            jdbc.update("INSERT INTO import_rows VALUES(1,'LICH_SU',?,?,?)", row,
                    "{\"supplierName\":\"PRIVATE_SOURCE_VALUE\"}",
                    mapper.writeValueAsString(java.util.List.of("WARNING:MISSING_UNIT: Chưa có đơn vị", "WARNING:REVIEW: =Do not execute, check \"unit\"")));
        var service = new LegacyImportReportService(batches, jdbc, mapper);
        var preview = service.preview(1);
        assertEquals(35, preview.summary().totalRows());
        assertEquals(35, preview.summary().warningRows());
        assertEquals(30, preview.rowIssues().size());
        String csv = new String(service.issuesCsv(1), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFSheet"));
        assertEquals(71, csv.lines().count()); // Header + every issue (2 per row).
        assertTrue(csv.contains("\"LICH_SU\",36,"));
        assertTrue(csv.contains("\"'=Do not execute, check \"\"unit\"\"\""));
        assertFalse(csv.contains("PRIVATE_SOURCE_VALUE"));
    }

    @Test void operationalPreviewRejectsLegacyWorkbookRatherThanReadingOnlyTheFirstSheet() throws Exception {
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet("DON_HANG"); workbook.createSheet("NCC"); workbook.createSheet("LICH_SU");
            workbook.write(output); bytes = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "synthetic.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var error = assertThrows(ApiException.class, () -> new OperationalRequestImportService().preview(file));
        assertEquals("LEGACY_WORKBOOK_DETECTED", error.code());
    }
}
