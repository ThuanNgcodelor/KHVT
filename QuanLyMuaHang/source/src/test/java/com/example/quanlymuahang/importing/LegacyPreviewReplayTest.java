package com.example.quanlymuahang.importing;

import com.example.quanlymuahang.domain.importbatch.ImportBatch;
import com.example.quanlymuahang.importing.application.LegacyWorkbookImportService;
import com.example.quanlymuahang.repository.*;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyPreviewReplayTest {
    @Test void uploadingTheSameWorkbookPreservesWarningsAndPurchaseOrderGroups() throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:replay" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE import_rows(batch_id BIGINT,sheet_name VARCHAR(80),`row_number` INT,raw_json CLOB,mapped_json CLOB,issues_json CLOB,status VARCHAR(20))");
        var batches = mock(ImportBatchRepository.class);
        when(batches.findBySha256AndMode(anyString(), anyString())).thenReturn(Optional.empty());
        when(batches.saveAndFlush(any())).thenAnswer(invocation -> { ImportBatch batch = invocation.getArgument(0); ReflectionTestUtils.setField(batch, "id", 1L); return batch; });
        var service = new LegacyWorkbookImportService(batches, mock(HistoricalPurchaseRepository.class), mock(SupplierRepository.class), mock(MaterialRepository.class), mock(PurchaseOrderRepository.class), jdbc, new ObjectMapper(), mock(AuditRecorder.class));
        byte[] bytes;
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            String[][][] data = {
                {{"MaNCC", "TenNCC"}, {"NCC-1", "NCC thử"}},
                {{"TenHang", "DonGia"}, {"Vật tư thử", "100"}},
                {{"SoPO", "TenHang", "DonGia"}, {"PO-261010-001", "Vật tư thử", "100"}}
            };
            String[] names = {"NCC", "LICH_SU", "DON_HANG"};
            for (int sheet = 0; sheet < names.length; sheet++) {
                var target = workbook.createSheet(names[sheet]);
                for (int row = 0; row < data[sheet].length; row++) {
                    var targetRow = target.createRow(row);
                    for (int col = 0; col < data[sheet][row].length; col++) targetRow.createCell(col).setCellValue(data[sheet][row][col]);
                }
            }
            workbook.write(out); bytes = out.toByteArray();
        }
        var file = new MockMultipartFile("file", "fixture.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
        var first = service.preview(file, 1L);
        assertTrue(first.summary().warningRows() > 0);
        assertEquals(1, first.summary().legacyPurchaseOrderGroups());
        var batch = new ImportBatch(first.fileName(), first.sha256(), "XLSX", "LEGACY_WORKBOOK");
        ReflectionTestUtils.setField(batch, "id", first.batchId()); batch.setTotalRows(first.summary().totalRows());
        when(batches.findBySha256AndMode(anyString(), anyString())).thenReturn(Optional.of(batch));
        var repeated = service.preview(file, 1L);
        assertTrue(repeated.duplicate()); assertEquals(first.batchId(), repeated.batchId());
        assertEquals(first.summary(), repeated.summary());
        assertEquals(first.rowIssues(), repeated.rowIssues());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM import_rows", Integer.class));
        verify(batches, times(1)).saveAndFlush(any());
    }
}
