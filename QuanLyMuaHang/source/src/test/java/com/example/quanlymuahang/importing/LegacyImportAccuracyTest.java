package com.example.quanlymuahang.importing;

import com.example.quanlymuahang.domain.history.HistoricalPurchase;
import com.example.quanlymuahang.domain.importbatch.ImportBatch;
import com.example.quanlymuahang.importing.application.LegacyImportValues;
import com.example.quanlymuahang.importing.application.LegacyWorkbookImportService;
import com.example.quanlymuahang.repository.*;
import com.example.quanlymuahang.sharedkernel.application.AuditRecorder;
import com.example.quanlymuahang.sharedkernel.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Source fidelity tests in isolated H2 staging; no secrets, real workbook or main database. */
class LegacyImportAccuracyTest {
    @Test void ambiguousTextNumbersNeverBecomeDifferentNumericValues() throws Exception {
        for (String value : new String[]{"1.234", "1,234", "1 23", "8%"}) {
            Fixture fixture = new Fixture();
            var preview = fixture.preview(book -> book.getSheet("LICH_SU").getRow(1).getCell(2).setCellValue(value));
            assertTrue(preview.summary().errorRows() > 0, value);
            assertTrue(fixture.issues("LICH_SU").contains("INVALID_UNIT_PRICE"));
        }
        assertEquals(new BigDecimal("1234.56"), LegacyImportValues.decimalText("1.234,56"));
        assertEquals(new BigDecimal("1.5"), LegacyImportValues.decimalText("1,5"));
        assertEquals(new BigDecimal("1000"), LegacyImportValues.decimalText("1 000"));
    }

    @Test void numericExcelTokensRemainExactBeyondDoublePrecision() throws Exception {
        Fixture fixture = new Fixture();
        var preview = fixture.preview(book -> {
            var price = book.getSheet("LICH_SU").getRow(1).getCell(2);
            price.setCellValue(1d); price.getCTCell().setV("9007199254740993");
            var quantity = book.getSheet("LICH_SU").getRow(1).createCell(3);
            quantity.setCellValue(1.234d);
        });
        assertEquals(0, preview.summary().errorRows());
        assertEquals("9007199254740993", fixture.data("LICH_SU", "mapped_json").get("unitPrice"));
        assertEquals("1.234", fixture.data("LICH_SU", "mapped_json").get("quantity"));
        assertEquals("true", fixture.data("LICH_SU", "mapped_json").get("quantity__numeric"));
    }

    @Test void sqlScaleAndPrecisionOverflowAreRejectedBeforeCommit() throws Exception {
        for (String price : new String[]{"1.2345678901234567891", "100000000000000000000"}) {
            Fixture fixture = new Fixture();
            var preview = fixture.preview(book -> {
                var cell = book.getSheet("LICH_SU").getRow(1).getCell(2); cell.setCellValue(1d); cell.getCTCell().setV(price);
            });
            assertTrue(preview.summary().errorRows() > 0);
            assertTrue(fixture.issues("LICH_SU").contains("DECIMAL_NOT_REPRESENTABLE"));
        }
        Fixture fixture = new Fixture();
        fixture.preview(book -> {
            var cell = book.getSheet("LICH_SU").getRow(1).createCell(3); cell.setCellValue(1d); cell.getCTCell().setV("1.2345678901234567891");
        });
        assertTrue(fixture.issues("LICH_SU").contains("DECIMAL_NOT_REPRESENTABLE"));
    }

    @Test void twelveSourceDecimalPlacesArePreservedWithoutRounding() throws Exception {
        Fixture fixture = new Fixture();
        var preview = fixture.preview(book -> {
            var row = book.getSheet("LICH_SU").getRow(1);
            row.getCell(2).setCellValue(1d); row.getCell(2).getCTCell().setV("100.123456789012");
            var quantity = row.createCell(3); quantity.setCellValue(1d); quantity.getCTCell().setV("2.123456789012");
        });
        assertEquals(0, preview.summary().errorRows());
        assertEquals("100.123456789012", fixture.data("LICH_SU", "mapped_json").get("unitPrice"));
        assertEquals("2.123456789012", fixture.data("LICH_SU", "mapped_json").get("quantity"));
    }

    @Test void percentageFormattedNumericVatRequiresExplicitSourceConfirmation() throws Exception {
        Fixture fixture = new Fixture();
        var preview = fixture.preview(book -> {
            var vat = book.getSheet("DON_HANG").getRow(1).getCell(6); vat.setCellValue(0.08d);
            var style = book.createCellStyle(); style.setDataFormat(book.createDataFormat().getFormat("0%")); vat.setCellStyle(style);
        });
        assertEquals(1, preview.summary().errorRows());
        assertTrue(fixture.issues("DON_HANG").contains("PERCENT_CELL_FORMAT"));
        assertEquals("0.08", fixture.data("DON_HANG", "raw_json").get("vatPercent"));
        assertEquals("8%", fixture.data("DON_HANG", "raw_json").get("vatPercent__display"));
    }

    @Test void invalidDatesAreErrorsAndLeapDaysUseStrictParsing() throws Exception {
        Fixture fixture = new Fixture();
        fixture.preview(book -> book.getSheet("LICH_SU").getRow(1).createCell(4).setCellValue("31/2/2026"));
        assertTrue(fixture.issues("LICH_SU").contains("INVALID_DATE"));
        assertNull(LegacyImportValues.date("29/2/2025"));
        assertEquals(LocalDate.of(2024, 2, 29), LegacyImportValues.date("29/2/2024"));
    }

    @Test void excel1904DatesAndFormulaValuesUseWorkbookSemantics() throws Exception {
        Fixture fixture = new Fixture();
        var preview = fixture.preview(book -> {
            book.getCTWorkbook().getWorkbookPr().setDate1904(true);
            var row = book.getSheet("LICH_SU").getRow(1);
            var date = row.createCell(4); date.setCellValue(0d);
            var style = book.createCellStyle(); style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd")); date.setCellStyle(style);
            row.getCell(2).setCellFormula("25*4");
        });
        assertEquals(0, preview.summary().errorRows());
        assertEquals("1904-01-01", fixture.data("LICH_SU", "mapped_json").get("date"));
        assertEquals("100", fixture.data("LICH_SU", "mapped_json").get("unitPrice"));
        assertEquals("25*4", fixture.data("LICH_SU", "raw_json").get("unitPrice__formula"));
    }

    @Test void excelErrorsAndFormulaErrorsCannotDisappearAsEmptyCells() throws Exception {
        for (boolean formula : new boolean[]{true, false}) {
            Fixture fixture = new Fixture();
            fixture.preview(book -> {
                var cell = book.getSheet("LICH_SU").getRow(1).createCell(4);
                if (formula) cell.setCellFormula("1/0"); else cell.setCellErrorValue(FormulaError.VALUE.getCode());
            });
            assertTrue(fixture.issues("LICH_SU").contains("EXCEL_CELL_ERROR"));
            assertFalse(fixture.data("LICH_SU", "raw_json").get("date").isBlank());
        }
    }

    @Test void duplicateAndUnknownHeadersCannotDiscardColumns() throws Exception {
        for (String header : new String[]{"DonGia", "UnmappedCustomField"}) {
            Fixture fixture = new Fixture();
            ApiException exception = assertThrows(ApiException.class, () -> fixture.preview(book -> book.getSheet("LICH_SU").getRow(0).createCell(6).setCellValue(header)));
            assertEquals(header.equals("DonGia") ? "WORKSHEET_DUPLICATE_HEADER" : "WORKSHEET_UNKNOWN_HEADER", exception.code());
            verify(fixture.batches, never()).saveAndFlush(any());
        }
    }

    @Test void unheadedDataRowsRemainVisibleAsErrors() throws Exception {
        Fixture fixture = new Fixture();
        var preview = fixture.preview(book -> book.getSheet("LICH_SU").createRow(2).createCell(9).setCellValue("source-only value"));
        assertEquals(2, preview.summary().rowsPerSheet().get("LICH_SU"));
        assertEquals(1, preview.summary().errorRows());
        assertTrue(fixture.jdbc.queryForObject("SELECT issues_json FROM import_rows WHERE sheet_name='LICH_SU' AND `row_number`=3", String.class).contains("UNMAPPED_CELL"));
    }

    @Test void unsupportedCurrencyAndInvalidVatNeverDefaultSilently() throws Exception {
        Fixture fixture = new Fixture();
        fixture.preview(book -> {
            book.getSheet("LICH_SU").getRow(1).createCell(5).setCellValue("EUR");
            book.getSheet("DON_HANG").getRow(1).getCell(6).setCellValue("eight");
        });
        assertTrue(fixture.issues("LICH_SU").contains("UNSUPPORTED_CURRENCY"));
        assertTrue(fixture.issues("DON_HANG").contains("INVALID_VAT"));
    }

    @Test void blankVsKnownPoHeadersAreRejectedButEquivalentNumericVatIsAccepted() throws Exception {
        Fixture conflict = new Fixture();
        var bad = conflict.preview(book -> {
            addSecondOrderRow(book); book.getSheet("DON_HANG").getRow(2).getCell(6).setBlank();
        });
        assertEquals(2, bad.summary().errorRows());
        assertTrue(conflict.issues("DON_HANG").contains("PO_HEADER_CONFLICT"));
        Fixture exact = new Fixture();
        var good = exact.preview(book -> {
            addSecondOrderRow(book); book.getSheet("DON_HANG").getRow(1).getCell(6).setCellValue(8d);
            book.getSheet("DON_HANG").getRow(2).getCell(6).setCellValue("8.0");
        });
        assertEquals(0, good.summary().errorRows());
    }

    @Test void rawWhitespaceAndTextQuantityArePreservedSeparatelyFromMappedValues() throws Exception {
        Fixture fixture = new Fixture();
        fixture.preview(book -> {
            book.getSheet("LICH_SU").getRow(1).getCell(0).setCellValue("  Vật tư thử  ");
            book.getSheet("LICH_SU").getRow(1).createCell(3).setCellValue(" theo thực tế ");
        });
        assertEquals("  Vật tư thử  ", fixture.data("LICH_SU", "raw_json").get("materialName"));
        assertEquals("Vật tư thử", fixture.data("LICH_SU", "mapped_json").get("materialName"));
        assertEquals(" theo thực tế ", fixture.data("LICH_SU", "raw_json").get("quantity"));
        assertTrue(fixture.issues("LICH_SU").contains("QUANTITY_TEXT"));
    }

    @Test void oldPreviewValuesAreRevalidatedBeforeAnyBusinessWrite() throws Exception {
        Fixture fixture = new Fixture();
        fixture.preview(book -> {});
        var data = fixture.data("LICH_SU", "mapped_json"); data.put("unitPrice", "1.234");
        fixture.jdbc.update("UPDATE import_rows SET mapped_json=? WHERE sheet_name='LICH_SU'", fixture.mapper.writeValueAsString(data));
        assertEquals("IMPORT_HAS_ERRORS", assertThrows(ApiException.class, () -> fixture.service.commit(1L, 1L)).code());
        verify(fixture.suppliers, never()).findAll();
    }

    @Test void explicitHistoricalCurrencyIsRetainedAndMissingCurrencyIsMarkedAssumed() {
        Fixture fixture = new Fixture();
        for (String currency : new String[]{"USD", ""}) {
            HistoricalPurchase history = ReflectionTestUtils.invokeMethod(fixture.service, "historical",
                    Map.of("materialName", "Vật tư", "unitPrice", "100", "currency", currency), 2, "[]", 1L,
                    Map.of(), Map.of(), Map.of(), Map.of());
            assertNotNull(history);
            assertEquals(currency.isEmpty() ? "VND" : "USD", history.getCurrency().name());
            assertEquals(currency.isEmpty() ? "ASSUMED_LEGACY" : "SOURCE", history.getCurrencyBasis());
        }
    }

    private static void addSecondOrderRow(XSSFWorkbook book) {
        var sheet = book.getSheet("DON_HANG"); var row = sheet.createRow(2);
        for (int col = 0; col < 7; col++) row.createCell(col).setCellValue(sheet.getRow(1).getCell(col).getStringCellValue());
    }

    private static final class Fixture {
        final ObjectMapper mapper = new ObjectMapper();
        final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:accuracy-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        final ImportBatchRepository batches = mock(ImportBatchRepository.class);
        final SupplierRepository suppliers = mock(SupplierRepository.class);
        final LegacyWorkbookImportService service;
        Fixture() {
            jdbc.execute("CREATE TABLE import_rows(batch_id BIGINT,sheet_name VARCHAR(80),`row_number` INT,raw_json CLOB,mapped_json CLOB,issues_json CLOB,status VARCHAR(20),committed_entity_type VARCHAR(80),committed_entity_id BIGINT)");
            when(batches.findBySha256AndMode(anyString(), anyString())).thenReturn(Optional.empty());
            when(batches.saveAndFlush(any())).thenAnswer(call -> {
                ImportBatch batch = call.getArgument(0); ReflectionTestUtils.setField(batch, "id", 1L);
                when(batches.findLockedById(1L)).thenReturn(Optional.of(batch)); return batch;
            });
            service = new LegacyWorkbookImportService(batches, mock(HistoricalPurchaseRepository.class), suppliers,
                    mock(MaterialRepository.class), mock(PurchaseOrderRepository.class), jdbc, mapper, mock(AuditRecorder.class));
        }
        LegacyWorkbookImportService.PreviewResult preview(Consumer<XSSFWorkbook> change) throws Exception {
            byte[] bytes;
            try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
                String[][][] data = {
                    {{"MaNCC", "TenNCC", "DiaChi"}, {"NCC-1", "NCC thử", "Địa chỉ"}},
                    {{"TenHang", "DVT", "DonGia", "SoLuong", "Ngay", "LoaiTien"}, {"Vật tư thử", "cái", "100", "2", "2026-10-10", "VND"}},
                    {{"SoPO", "TenHang", "DVT", "DonGia", "Ngay", "LoaiTien", "VAT"}, {"PO-261010-001", "Vật tư thử", "cái", "100", "2026-10-10", "VND", "8"}}
                };
                String[] names = {"NCC", "LICH_SU", "DON_HANG"};
                for (int sheet = 0; sheet < names.length; sheet++) {
                    var target = book.createSheet(names[sheet]);
                    for (int row = 0; row < data[sheet].length; row++) {
                        var targetRow = target.createRow(row);
                        for (int col = 0; col < data[sheet][row].length; col++) targetRow.createCell(col).setCellValue(data[sheet][row][col]);
                    }
                }
                change.accept(book); book.write(out); bytes = out.toByteArray();
            }
            return service.preview(new MockMultipartFile("file", "synthetic.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), 1L);
        }
        Map<String, String> data(String sheet, String column) throws Exception {
            assertTrue(column.equals("raw_json") || column.equals("mapped_json"));
            return mapper.readValue(jdbc.queryForObject("SELECT " + column + " FROM import_rows WHERE sheet_name=? AND `row_number`=2", String.class, sheet), new TypeReference<>() {});
        }
        String issues(String sheet) { return jdbc.queryForObject("SELECT issues_json FROM import_rows WHERE sheet_name=? AND `row_number`=2", String.class, sheet); }
    }
}
