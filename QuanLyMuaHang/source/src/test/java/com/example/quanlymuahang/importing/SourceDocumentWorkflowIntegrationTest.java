package com.example.quanlymuahang.importing;

import com.example.quanlymuahang.identity.infrastructure.persistence.RoleJpaRepository;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountEntity;
import com.example.quanlymuahang.identity.infrastructure.persistence.UserAccountJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Full controller/security/persistence with H2 servlet sessions; real MySQL/Redis is tested separately.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:source_documents;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "app.bootstrap-admin.email=", "app.bootstrap-admin.password=", "app.files.root=target/runtime/document-h2-files",
        "logging.level.org.springframework.web=INFO", "debug=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class SourceDocumentWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserAccountJpaRepository users;
    @Autowired RoleJpaRepository roles;
    @Autowired PasswordEncoder encoder;
    private static final String PASSWORD = "document-synthetic-password";
    private static final String BASE = "/api/imports/documents";

    @Test void directApisSourceDownloadsAndImportActionsRequireTheirPermissionsAndCsrf() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        var writer = login("IMPORT_OPERATIONAL");
        var file = requestFile(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(BASE + "/preview")
                .file(file).param("kind", "REQUEST").session(writer).servletPath(BASE + "/preview"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("CSRF_INVALID"));
        long id = preview(writer, file, "REQUEST").get("id").asLong();
        var reader = login("PRICE_READ");
        mvc.perform(get(BASE + "/" + id).session(reader)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + id + "/source").session(reader)).andExpect(status().isForbidden());
        postJson(writer, BASE + "/" + id + "/lookup", json.createObjectNode()).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + id + "/comparison").session(writer)).andExpect(status().isForbidden());
    }

    @Test void previewSurvivesReloadAndStoresTheOriginalFileWithoutPurchasesOrOrders() throws Exception {
        var session = login("*"); var file = requestFile(false);
        int history = count("historical_purchases"), orders = count("purchase_orders");
        var document = preview(session, file, "REQUEST"); long id = document.get("id").asLong();
        var restored = read(mvc.perform(get(BASE + "/" + id).session(session)).andExpect(status().isOk()).andReturn());
        assertThat(restored.path("parsed")).isEqualTo(document.path("parsed"));
        assertThat(restored.path("status").asText()).isEqualTo("PREVIEW");
        byte[] source = mvc.perform(get(BASE + "/" + id + "/source").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(source).isEqualTo(file.getBytes());
        assertThat(count("historical_purchases")).isEqualTo(history); assertThat(count("purchase_orders")).isEqualTo(orders);
        assertThat(count("source_document_imports")).isEqualTo(1);
    }

    @Test void confirmedEditsKeepImmutableSourceProvenanceAndIdempotentRetriesCannotRewriteASavedRequest() throws Exception {
        var session = login("*"); var document = preview(session, requestFile(false), "REQUEST");
        long id = document.get("id").asLong(); var body = confirmation(document, "Test request", "1");
        var line = (ObjectNode) body.withArray("lines").get(0);
        line.put("quantity", 123); line.put("sourceRow", 999); line.put("sourceSheet", "fabricated");
        line.set("raw", json.valueToTree(Map.of("E6", "fabricated")));
        var saved = read(postJson(session, BASE + "/" + id + "/confirm", body).andExpect(status().isOk()).andReturn());
        assertThat(saved.path("confirmed").path("lines").get(0).path("quantity").asInt()).isEqualTo(123);
        assertThat(saved.path("confirmed").path("lines").get(0).path("sourceRow").asInt()).isEqualTo(6);
        assertThat(saved.path("confirmed").path("lines").get(0).path("raw")).isEqualTo(document.path("parsed").path("lines").get(0).path("raw"));
        assertThat(saved.path("parsed")).isEqualTo(document.path("parsed"));
        postJson(session, BASE + "/" + id + "/confirm", body).andExpect(status().isOk()).andExpect(jsonPath("duplicate").value(true));
        body.put("title", "Different title");
        postJson(session, BASE + "/" + id + "/confirm", body).andExpect(status().isConflict()).andExpect(jsonPath("code").value("DOCUMENT_ALREADY_CONFIRMED"));
        mvc.perform(get(BASE + "/" + saved.path("id").asLong()).session(session)).andExpect(status().isOk()).andExpect(jsonPath("title").value("Test request"));
    }

    @Test void aYearlyWorkbookCannotBeConfirmedAsOneRequestAcrossDifferentPhieu() throws Exception {
        var session = login("*"); var document = preview(session, requestFile(true), "REQUEST");
        var body = confirmation(document, "Mixed request", "1");
        postJson(session, BASE + "/" + document.get("id").asLong() + "/confirm", body)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("MIXED_REQUEST_GROUPS"));
    }

    @Test void oneYearlySourceWorkbookCanSupplyDifferentPhieuWithoutLockingOutTheRemainingItems() throws Exception {
        var session = login("*"); var source = preview(session, requestFile(true), "REQUEST"); long id = source.path("id").asLong();
        var firstBody = confirmation(source, "PCVT 1", "1"); firstBody.set("lines", json.createArrayNode().add(source.path("parsed").path("lines").get(0)));
        var secondBody = confirmation(source, "PCVT 2", "2"); secondBody.set("lines", json.createArrayNode().add(source.path("parsed").path("lines").get(1)));
        var first = read(postJson(session, BASE + "/" + id + "/confirm", firstBody).andExpect(status().isOk()).andReturn());
        var second = read(postJson(session, BASE + "/" + id + "/confirm", secondBody).andExpect(status().isOk()).andReturn());
        assertThat(first.path("id")).isNotEqualTo(second.path("id"));
        assertThat(first.path("sourceDocumentId").asLong()).isEqualTo(id); assertThat(second.path("sourceDocumentId").asLong()).isEqualTo(id);
        var original = read(mvc.perform(get(BASE + "/" + id).session(session)).andExpect(status().isOk()).andReturn());
        assertThat(original.path("status").asText()).isEqualTo("PREVIEW"); assertThat(original.path("confirmed").isNull()).isTrue(); assertThat(original.path("parsed").path("lines")).hasSize(2);
        assertThat(count("source_document_imports")).isEqualTo(3); assertThat(count("purchase_orders")).isZero(); assertThat(count("historical_purchases")).isZero();
    }

    @Test void confirmingRequiresAcknowledgementAndAtLeastOneRealItem() throws Exception {
        var session = login("*"); var document = preview(session, requestFile(false), "REQUEST");
        var body = confirmation(document, "No review", "1"); body.put("acknowledged", false);
        String path = BASE + "/" + document.get("id").asLong() + "/confirm";
        postJson(session, path, body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("REVIEW_REQUIRED"));
        body.put("acknowledged", true); body.set("lines", json.createArrayNode());
        postJson(session, path, body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("LINES_REQUIRED"));
        assertThat(count("historical_purchases")).isZero(); assertThat(count("purchase_orders")).isZero();
    }

    @Test void duplicateUploadReturnsTheExistingDocumentAndDoesNotDuplicateSourceRows() throws Exception {
        var session = login("*"); var file = requestFile(false);
        var first = preview(session, file, "REQUEST");
        var second = preview(session, file, "REQUEST");
        assertThat(second.path("id")).isEqualTo(first.path("id")); assertThat(second.path("duplicate").asBoolean()).isTrue();
        assertThat(second.path("parsed")).isEqualTo(first.path("parsed")); assertThat(count("source_document_imports")).isEqualTo(1);
    }

    @Test void manualScanReviewCanBeSavedWithoutPretendingThatAutomaticExtractionWorked() throws Exception {
        var session = login("*"); var document = preview(session, scan(), "QUOTATION");
        assertThat(document.path("parsed").path("format").asText()).isEqualTo("PDF_SCAN");
        assertThat(document.path("parsed").path("lines")).isEmpty();
        var body = confirmation(document, "Manual scan quote", "");
        body.put("supplierName", "Manually verified supplier"); body.put("quotationDate", "2026-10-08"); body.put("currency", "VND");
        body.withArray("lines").add(manualLine("manual-scan-1", "Lưới thép", 2, 100));
        var saved = read(postJson(session, BASE + "/" + document.get("id").asLong() + "/confirm", body).andExpect(status().isOk()).andReturn());
        assertThat(saved.path("parsed").path("lines")).isEmpty();
        assertThat(saved.path("confirmed").path("lines").get(0).path("raw")).isEmpty();
        assertThat(saved.path("confirmed").path("lines").get(0).path("issues").toString()).contains("MANUAL_ENTRY");
        assertThat(count("historical_purchases")).isZero(); assertThat(count("purchase_orders")).isZero();
    }

    @Test void aQuoteCanOnlyLinkToActualLinesOfAConfirmedRequest() throws Exception {
        var session = login("*"); var request = preview(session, requestFile(false), "REQUEST"); long sourceId = request.path("id").asLong();
        var requestBody = confirmation(request, "Request to compare", "1");
        long requestId = read(postJson(session, BASE + "/" + sourceId + "/confirm", requestBody).andExpect(status().isOk()).andReturn()).path("id").asLong();
        var quote = preview(session, scan(), "QUOTATION"); var body = confirmation(quote, "Linked quote", "");
        body.put("supplierName", "Test supplier"); body.put("quotationDate", "2026-10-08"); body.put("currency", "VND");
        body.withArray("lines").add(manualLine("manual-q1", "Vòng bi 6204", 2, 100)); body.put("requestId", requestId);
        body.set("requestLineMappings", json.valueToTree(Map.of("manual-q1", "missing-request-line")));
        String path = BASE + "/" + quote.path("id").asLong() + "/confirm";
        postJson(session, path, body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("REQUEST_LINE_INVALID"));
        body.set("requestLineMappings", json.valueToTree(Map.of("manual-q1", requestBody.path("lines").get(0).path("lineKey").asText())));
        postJson(session, path, body).andExpect(status().isOk()).andExpect(jsonPath("confirmed.requestId").value(requestId));
    }

    @Test void historicalLookupIncludesEveryDistinctSnapshotSupplierAndPoOnlyRowsWithoutCountingRevisionsTwice() throws Exception {
        var session = login("*"); var document = preview(session, requestFile(false), "REQUEST"); long sourceId = document.path("id").asLong();
        long id = read(postJson(session, BASE + "/" + sourceId + "/confirm", confirmation(document, "Lookup request", "1")).andExpect(status().isOk()).andReturn()).path("id").asLong();
        long current = seedPo("LOOKUP-CURRENT", "NCC A", "EXPORTED", 2, "Cái", "VND", 120);
        history("NCC A", "Cái", "VND", 120, "PO:" + current + ":R:2:L:1", "2026-10-07");
        history("NCC A", "Cái", "VND", 99, "PO:" + current + ":R:1:L:1", "2026-10-06");
        long cancelled = seedPo("LOOKUP-CANCELLED", "NCC cancelled", "CANCELLED", 1, "Cái", "VND", 10);
        history("NCC cancelled", "Cái", "VND", 10, "PO:" + cancelled + ":R:1:L:1", "2026-10-07");
        history("NCC B", "Cái", "VND", 100, "LEGACY:B:1", "2026-10-01");
        history("NCC B", "Cái", "VND", 110, "LEGACY:B:2", "2026-10-02");
        history("NCC B 2", "Cái", "VND", 130, "LEGACY:C:1", "2026-10-03");
        var lookup = read(postJson(session, BASE + "/" + id + "/lookup", json.createObjectNode()).andExpect(status().isOk()).andReturn());
        var candidates = lookup.path("lines").get(0).path("candidates");
        assertThat(candidates).hasSize(3);
        var byName = new HashMap<String, JsonNode>(); candidates.forEach(c -> byName.put(c.path("supplierName").asText(), c));
        assertThat(byName.keySet()).containsExactlyInAnyOrder("NCC A", "NCC B", "NCC B 2");
        assertThat(byName.get("NCC A").path("count").asInt()).isEqualTo(1);
        assertThat(byName.get("NCC A").path("source").asText()).isEqualTo("PURCHASE_ORDER");
        assertThat(byName.get("NCC B").path("count").asInt()).isEqualTo(2);
        assertThat(byName.get("NCC B").path("unitPrice").asText()).startsWith("110");
        assertThat(byName.get("NCC B").path("supplierId").isNull()).isTrue();
        String lineKey = document.path("parsed").path("lines").get(0).path("lineKey").asText();
        var selections = json.createArrayNode(); byName.values().forEach(c -> selections.add(json.valueToTree(Map.of("lineKey", lineKey, "supplierKey", c.path("supplierKey").asText()))));
        var body = json.createObjectNode().set("selections", selections);
        byte[] xlsx = postJson(session, BASE + "/" + id + "/inquiries.xlsx", body).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            for (var sheet : workbook) for (var row : sheet) for (var cell : row) assertThat(cell.toString()).doesNotContain("120", "110", "130", "Đơn giá");
        }
        assertThat(count("historical_purchases")).isEqualTo(6); assertThat(count("purchase_orders")).isEqualTo(2);
    }

    @Test void comparisonsOnlyCalculateDeltaForTheSameUnitCurrencySpecificationAndVatBasis() throws Exception {
        var session = login("*"); var request = preview(session, requestFile(false), "REQUEST"); long sourceId = request.path("id").asLong();
        var requestBody = confirmation(request, "Comparison request", "1");
        long id = read(postJson(session, BASE + "/" + sourceId + "/confirm", requestBody).andExpect(status().isOk()).andReturn()).path("id").asLong();
        long prior = seedPo("COMPARISON", "NCC prior", "EXPORTED", 1, "Cái", "VND", 100);
        history("Newer unverified legacy", "Cái", "VND", 120, "LEGACY:NEWER", "2026-10-09");
        long zero = seedPo("COMPARISON-ZERO", "NCC zero", "EXPORTED", 1, "Cái", "VND", 0);
        long negative = seedPo("COMPARISON-NEGATIVE", "NCC negative", "EXPORTED", 1, "Cái", "VND", -1);
        jdbc.update("update purchase_orders set order_date=DATE '2026-10-10' where id=?", zero);
        jdbc.update("update purchase_orders set order_date=DATE '2026-10-11' where id=?", negative);
        var quote = preview(session, scan(), "QUOTATION"); var body = confirmation(quote, "USD quote", "");
        body.put("supplierName", "New supplier"); body.put("quotationDate", "2026-10-08"); body.put("currency", "USD"); body.put("priceBasis", "EXCL_VAT");
        var line = manualLine("manual-usd", "Vòng bi 6204", 2, 110); line.put("specification", "6204 2RS"); line.put("currency", "USD");
        var differentUnit = manualLine("manual-unit", "Vòng bi 6204", 2, 110); differentUnit.put("unit", "Sợi"); differentUnit.put("specification", "6204 2RS");
        var comparable = manualLine("manual-good", "Vòng bi 6204", 2, 110); comparable.put("specification", "6204 2RS");
        body.withArray("lines").add(line).add(differentUnit).add(comparable); body.put("requestId", id);
        String requestKey = requestBody.path("lines").get(0).path("lineKey").asText();
        body.set("requestLineMappings", json.valueToTree(Map.of("manual-usd", requestKey, "manual-unit", requestKey, "manual-good", requestKey)));
        postJson(session, BASE + "/" + quote.path("id").asLong() + "/confirm", body).andExpect(status().isOk());
        var result = read(mvc.perform(get(BASE + "/" + id + "/comparison").session(session)).andExpect(status().isOk()).andReturn());
        var quoted = result.path("lines").get(0).path("quotes").get(0);
        assertThat(quoted.path("comparable").asBoolean()).isFalse(); assertThat(quoted.path("delta").isNull()).isTrue(); assertThat(quoted.path("reason").asText()).isNotBlank();
        var unitMismatch = result.path("lines").get(0).path("quotes").get(1);
        assertThat(unitMismatch.path("comparable").asBoolean()).isFalse(); assertThat(unitMismatch.path("delta").isNull()).isTrue();
        var good = result.path("lines").get(0).path("quotes").get(2);
        assertThat(good.path("comparable").asBoolean()).isTrue(); assertThat(good.path("delta").asDouble()).isEqualTo(10); assertThat(good.path("percent").asDouble()).isEqualTo(10);
        assertThat(good.path("baseline").path("sourceReference").asText()).isEqualTo("PO:" + prior + ":R:1:L:1");
        assertThat(good.path("baseline").path("purchaseDate").asText()).isEqualTo("2026-10-07");
    }

    @Test void anExplicitMaterialCodeKeepsSpecificationVariantsSeparateWithoutInvalidatingCompatibleHistory() throws Exception {
        var session = login("*"); var request = preview(session, requestFile(false), "REQUEST");
        var requestBody = confirmation(request, "Internal material code review", "1");
        ((ObjectNode) requestBody.withArray("lines").get(0)).put("materialCode", "VT-6204");
        long id = read(postJson(session, BASE + "/" + request.path("id").asLong() + "/confirm", requestBody).andExpect(status().isOk()).andReturn()).path("id").asLong();
        long compatible = seedPo("CODE-COMPATIBLE", "NCC same", "EXPORTED", 1, "Cái", "VND", 100);
        long variant = seedPo("CODE-VARIANT", "NCC same", "EXPORTED", 1, "Cái", "VND", 90);
        jdbc.update("update purchase_order_items set material_code_snapshot='VT-6204' where purchase_order_id in (?,?)", compatible, variant);
        jdbc.update("update purchase_order_items set specification='6204 ZZ' where purchase_order_id=?", variant);
        jdbc.update("update purchase_orders set order_date=DATE '2026-10-09' where id=?", variant);
        history("NCC same", "Cái", "VND", 95, "LEGACY:CODE-UNKNOWN-SPEC", "2026-10-10");
        jdbc.update("update historical_purchases set material_code_snapshot='VT-6204' where source_reference='LEGACY:CODE-UNKNOWN-SPEC'");
        var lookup = read(postJson(session, BASE + "/" + id + "/lookup", json.createObjectNode()).andExpect(status().isOk()).andReturn()).path("lines").get(0);
        assertThat(lookup.path("matchStatus").asText()).isEqualTo("MATCHED_CODE");
        assertThat(lookup.path("candidates")).hasSize(3);
        lookup.path("candidates").forEach(candidate -> assertThat(candidate.path("qualityWarnings").toString()).doesNotContain("MATERIAL_MATCH_NEEDS_REVIEW"));
        var quote = preview(session, scan(), "QUOTATION"); var body = confirmation(quote, "Compatible exact-code quotation", "");
        body.put("supplierName", "New supplier"); body.put("quotationDate", "2026-10-11"); body.put("currency", "VND"); body.put("priceBasis", "EXCL_VAT");
        var line = manualLine("manual-exact-code", "Vòng bi 6204", 2, 110); line.put("specification", "6204 2RS"); body.withArray("lines").add(line); body.put("requestId", id);
        body.set("requestLineMappings", json.valueToTree(Map.of("manual-exact-code", requestBody.path("lines").get(0).path("lineKey").asText())));
        postJson(session, BASE + "/" + quote.path("id").asLong() + "/confirm", body).andExpect(status().isOk());
        var compared = read(mvc.perform(get(BASE + "/" + id + "/comparison").session(session)).andExpect(status().isOk()).andReturn()).path("lines").get(0).path("quotes").get(0);
        assertThat(compared.path("comparable").asBoolean()).isTrue();
        assertThat(compared.path("delta").asDouble()).isEqualTo(10);
        assertThat(compared.path("baseline").path("sourceReference").asText()).isEqualTo("PO:" + compatible + ":R:1:L:1");
    }

    @Test void aNewerUnverifiedLegacyPoCannotHideAnOlderCompatiblePriceFromTheSameSupplier() throws Exception {
        var session = login("*"); var request = preview(session, requestFile(false), "REQUEST");
        var requestBody = confirmation(request, "Same supplier VAT-basis review", "1");
        ((ObjectNode) requestBody.withArray("lines").get(0)).put("materialCode", "VT-6204");
        long id = read(postJson(session, BASE + "/" + request.path("id").asLong() + "/confirm", requestBody).andExpect(status().isOk()).andReturn()).path("id").asLong();
        long compatible = seedPo("BASIS-KNOWN", "NCC same basis", "EXPORTED", 1, "Cái", "VND", 100);
        long unverified = seedPo("BASIS-LEGACY", "NCC same basis", "EXPORTED", 1, "Cái", "VND", 90);
        jdbc.update("update purchase_order_items set material_code_snapshot='VT-6204' where purchase_order_id in (?,?)", compatible, unverified);
        jdbc.update("insert into import_batches(file_name,sha256,file_type,mode,status,total_rows,success_rows,error_rows,created_at) values('basis-legacy.xlsx',?,'XLSX','LEGACY','COMMITTED',1,1,0,CURRENT_TIMESTAMP)", "0".repeat(64));
        long batch = jdbc.queryForObject("select id from import_batches where file_name='basis-legacy.xlsx'", Long.class);
        jdbc.update("update purchase_orders set source_import_batch_id=?,order_date=DATE '2026-10-09' where id=?", batch, unverified);
        var candidates = read(postJson(session, BASE + "/" + id + "/lookup", json.createObjectNode()).andExpect(status().isOk()).andReturn()).path("lines").get(0).path("candidates");
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).path("supplierKey")).isEqualTo(candidates.get(1).path("supplierKey"));
        assertThat(candidates.get(0).path("currencyBasis").asText()).isEqualTo("LEGACY_UNVERIFIED");
        assertThat(candidates.get(0).path("priceBasis").asText()).isEqualTo("UNKNOWN");
        assertThat(candidates.get(1).path("currencyBasis").asText()).isEqualTo("SOURCE");
        var quote = preview(session, scan(), "QUOTATION"); var body = confirmation(quote, "Same-supplier compatible quotation", "");
        body.put("supplierName", "New supplier"); body.put("quotationDate", "2026-10-11"); body.put("currency", "VND"); body.put("priceBasis", "EXCL_VAT");
        var line = manualLine("manual-basis", "Vòng bi 6204", 2, 110); line.put("specification", "6204 2RS"); body.withArray("lines").add(line); body.put("requestId", id);
        body.set("requestLineMappings", json.valueToTree(Map.of("manual-basis", requestBody.path("lines").get(0).path("lineKey").asText())));
        postJson(session, BASE + "/" + quote.path("id").asLong() + "/confirm", body).andExpect(status().isOk());
        var compared = read(mvc.perform(get(BASE + "/" + id + "/comparison").session(session)).andExpect(status().isOk()).andReturn()).path("lines").get(0).path("quotes").get(0);
        assertThat(compared.path("comparable").asBoolean()).isTrue();
        assertThat(new java.math.BigDecimal(compared.path("delta").asText())).isEqualByComparingTo("10");
        assertThat(compared.path("baseline").path("sourceReference").asText()).isEqualTo("PO:" + compatible + ":R:1:L:1");
        assertThat(compared.path("baseline").path("currencyBasis").asText()).isEqualTo("SOURCE");
        assertThat(compared.path("baseline").path("purchaseDate").asText()).isEqualTo("2026-10-07");
    }

    @Test void anUnverifiedLegacyPriceIsShownButCannotBeUsedToAssertACertainPriceDifference() throws Exception {
        var session = login("*"); var request = preview(session, requestFile(false), "REQUEST"); long sourceId = request.path("id").asLong();
        var requestBody = confirmation(request, "Legacy comparison", "1");
        long id = read(postJson(session, BASE + "/" + sourceId + "/confirm", requestBody).andExpect(status().isOk()).andReturn()).path("id").asLong();
        history("Legacy supplier", "Cái", "VND", 100, "LEGACY:UNCERTAIN", "2026-10-01");
        var quote = preview(session, scan(), "QUOTATION"); var body = confirmation(quote, "Reviewed new quote", "");
        body.put("supplierName", "New supplier"); body.put("quotationDate", "2026-10-08"); body.put("currency", "VND"); body.put("priceBasis", "EXCL_VAT");
        var line = manualLine("manual-new", "Vòng bi 6204", 2, 110); line.put("specification", "6204 2RS"); body.withArray("lines").add(line); body.put("requestId", id);
        body.set("requestLineMappings", json.valueToTree(Map.of("manual-new", requestBody.path("lines").get(0).path("lineKey").asText())));
        postJson(session, BASE + "/" + quote.path("id").asLong() + "/confirm", body).andExpect(status().isOk());
        var result = read(mvc.perform(get(BASE + "/" + id + "/comparison").session(session)).andExpect(status().isOk()).andReturn());
        assertThat(result.path("lines").get(0).path("history").get(0).path("qualityWarnings").toString()).contains("CURRENCY_ASSUMED", "VAT_BASIS_UNKNOWN");
        var candidate = result.path("lines").get(0).path("quotes").get(0);
        assertThat(candidate.path("comparable").asBoolean()).isFalse(); assertThat(candidate.path("delta").isNull()).isTrue();
    }

    private long seedPo(String number, String supplier, String status, int revision, String unit, String currency, int price) {
        jdbc.update("insert into purchase_orders(po_number,order_date,supplier_name_snapshot,currency,vat_percent,status,revision,created_at,updated_at,version) values(?,DATE '2026-10-07',?,?,8,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)", number, supplier, currency, status, revision);
        long id = jdbc.queryForObject("select id from purchase_orders where po_number=?", Long.class, number);
        jdbc.update("insert into purchase_order_items(purchase_order_id,line_no,material_name,specification,unit,quantity,unit_price) values(?,1,'Vòng bi 6204','6204 2RS',?,2,?)", id, unit, price);
        return id;
    }
    private void history(String supplier, String unit, String currency, int price, String reference, String date) {
        jdbc.update("insert into historical_purchases(supplier_snapshot,material_name_snapshot,unit,quantity,unit_price,currency,currency_basis,source,source_reference,category,purchase_date) values(?,'Vòng bi 6204',?,2,?,?,'ASSUMED_LEGACY','LEGACY',?,'MATERIAL',CAST(? AS DATE))", supplier, unit, price, currency, reference, date);
    }

    private ObjectNode confirmation(JsonNode document, String title, String requestNumber) {
        var body = json.createObjectNode(); body.put("title", title); body.put("requestNumber", requestNumber);
        body.put("supplierName", document.path("parsed").path("supplierName").asText("")); body.put("quotationDate", document.path("parsed").path("quotationDate").asText(""));
        body.put("currency", "VND"); body.put("priceBasis", "UNKNOWN"); body.set("vatPercent", document.path("parsed").path("vatPercent"));
        body.set("lines", document.path("parsed").path("lines").deepCopy()); body.put("acknowledged", true); body.set("requestLineMappings", json.createObjectNode());
        return body;
    }
    private ObjectNode manualLine(String key, String name, int quantity, int price) {
        var line = json.createObjectNode(); line.put("lineKey", key); line.put("sourceRow", 0); line.put("sourcePage", 1);
        line.put("materialName", name); line.put("specification", "Verified manually"); line.put("unit", "Cái"); line.put("quantity", quantity);
        line.put("unitPrice", price); line.put("lineTotal", quantity * price); line.put("priceBasis", "EXCL_VAT"); line.put("currency", "VND");
        line.set("raw", json.valueToTree(Map.of("source", "untrusted-client-value"))); line.set("issues", json.createArrayNode()); return line;
    }
    private JsonNode preview(MockHttpSession session, MockMultipartFile file, String kind) throws Exception {
        var csrf = csrf(session); String path = BASE + "/preview";
        return read(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(path).file(file).param("kind", kind)
                .session(session).cookie(csrf.cookie()).header(csrf.header(), csrf.token()).servletPath(path)).andExpect(status().isOk()).andReturn());
    }
    private org.springframework.test.web.servlet.ResultActions postJson(MockHttpSession session, String path, Object body) throws Exception {
        var csrf = csrf(session); return mvc.perform(post(path).session(session).cookie(csrf.cookie()).header(csrf.header(), csrf.token()).contentType("application/json").content(json.writeValueAsString(body)));
    }
    private JsonNode read(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }
    private MockHttpSession login(String... permissions) throws Exception {
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 8), role = "DOC_" + tag, email = tag + "@example.test";
        jdbc.update("insert into roles(code,name,system_role,active,created_at,updated_at) values(?,?,true,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", role, role);
        for (String permission : permissions) {
            if (jdbc.queryForObject("select count(*) from permissions where code=?", Integer.class, permission) == 0) jdbc.update("insert into permissions(code,name,system_permission) values(?,?,true)", permission, permission);
            jdbc.update("insert into role_permissions(role_id,permission_id) select r.id,p.id from roles r,permissions p where r.code=? and p.code=?", role, permission);
        }
        var account = new UserAccountEntity(email, "Document test", encoder.encode(PASSWORD)); account.changePassword(account.getPasswordHash(), false);
        account.replaceRoles(Set.of(roles.findByCode(role).orElseThrow())); users.saveAndFlush(account);
        var csrf = csrf(null);
        var result = mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header(csrf.header(), csrf.token()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    private Csrf csrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf"); if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn(); var body = read(result);
        return new Csrf(result.getResponse().getCookie("XSRF-TOKEN"), body.path("headerName").asText(), body.path("token").asText());
    }
    private static MockMultipartFile requestFile(boolean mixed) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("2026"); sheet.createRow(0).createCell(0).setCellValue("PHIẾU CẤP VẬT TƯ");
            String[] header = {"STT", "TÊN VẬT TƯ", "QUI CÁCH", "ĐVT", "SỐ LƯỢNG", "MỤC ĐÍCH SỬ DỤNG", "Vị trí sử dụng", "", "CẤP THEO", "BP THI CÔNG", "KẾ HOẠCH", "NGÀY CẦN VT CHẬM NHẤT", "GHI CHÚ", "NGÀY CẤP", "SỐ PHIẾU"};
            var h = sheet.createRow(3); for (int col = 0; col < header.length; col++) h.createCell(col).setCellValue(header[col]);
            var r = sheet.createRow(5); r.createCell(0).setCellValue(1); r.createCell(1).setCellValue("Vòng bi 6204"); r.createCell(2).setCellValue("6204 2RS"); r.createCell(3).setCellValue("Cái"); r.createCell(4).setCellValue(2); r.createCell(14).setCellValue(1);
            if (mixed) { var second = sheet.createRow(6); second.createCell(0).setCellValue(2); second.createCell(1).setCellValue("Dây curoa A42"); second.createCell(3).setCellValue("Sợi"); second.createCell(4).setCellValue(3); second.createCell(14).setCellValue(2); }
            book.write(out); return new MockMultipartFile("file", "request.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
    private static MockMultipartFile scan() throws Exception { try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) { pdf.addPage(new PDPage()); pdf.save(out); return new MockMultipartFile("file", "scan.pdf", "application/pdf", out.toByteArray()); } }
    private record Csrf(Cookie cookie, String header, String token) {}
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String path) { return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).servletPath(path); }
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path) { return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).servletPath(path); }
}
