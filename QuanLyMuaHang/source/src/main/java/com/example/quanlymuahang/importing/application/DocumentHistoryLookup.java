package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.importing.application.SourceDocumentModels.DocumentLine;
import com.example.quanlymuahang.importing.application.DocumentImportService.*;
import com.example.quanlymuahang.service.TextNormalizer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

/** Read-only, bounded query count; source snapshots remain intact. */
@Service
public class DocumentHistoryLookup {
    private final JdbcTemplate jdbc;
    private static final Pattern PO_REF = Pattern.compile("PO:(\\d+):R:(\\d+):L:(\\d+)");
    public DocumentHistoryLookup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Lookup lookup(Confirmation request) {
        List<HistoryRow> rows = load();
        Map<String, List<HistoryRow>> byCode = new HashMap<>(), byName = new HashMap<>();
        for (HistoryRow r : rows) {
            if (!r.nCode.isBlank()) byCode.computeIfAbsent(r.nCode, k -> new ArrayList<>()).add(r);
            byName.computeIfAbsent(r.nName, k -> new ArrayList<>()).add(r);
        }
        List<LookupLine> output = new ArrayList<>();
        for (DocumentLine l : request.lines()) {
            List<HistoryRow> found; String match;
            if (!norm(l.materialCode()).isBlank()) { found = byCode.getOrDefault(norm(l.materialCode()), List.of()); match = "MATCHED_CODE"; }
            else { found = byName.getOrDefault(norm(l.materialName()), List.of()); match = "MATCHED_NAME"; }
            if (found.isEmpty()) {
                // Suggestions are never silently treated as the same material.
                String needle = norm(l.materialName());
                if (needle.length() >= 3) {
                    Set<String> names = new LinkedHashSet<>();
                    for (HistoryRow r : rows) if (!r.nName.isBlank() && (r.nName.contains(needle) || needle.contains(r.nName))) {
                        if (names.size() < 10 || names.contains(r.nName)) names.add(r.nName);
                    }
                    found = names.stream().flatMap(n -> byName.getOrDefault(n, List.of()).stream()).toList();
                }
                match = found.isEmpty() ? "NOT_FOUND" : "NEEDS_REVIEW";
            }
            Set<String> identities = new HashSet<>();
            for (HistoryRow r : found) identities.add(r.nCode + "|" + r.nName);
            // An explicit internal code identifies the material. Unit/specification differences
            // remain visible and are checked for each candidate instead of blocking every source.
            if (!"MATCHED_CODE".equals(match) && identities.size() > 1) match = "NEEDS_REVIEW";
            Map<String, List<HistoryRow>> groups = new LinkedHashMap<>();
            for (HistoryRow r : found) groups.computeIfAbsent(r.supplierKey + "|" + r.nUnit + "|" + r.currency + "|" + r.nCode + "|" + r.nName + "|" + r.nSpecification, k -> new ArrayList<>()).add(r);
            List<HistoryCandidate> candidates = new ArrayList<>();
            for (List<HistoryRow> group : groups.values()) {
                group.sort(Comparator.comparing((HistoryRow r) -> r.date, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(r -> r.reference));
                HistoryRow r = group.stream().filter(x -> x.date != null && x.price != null && x.price.signum() > 0).findFirst().orElse(group.getFirst());
                List<String> warnings = new ArrayList<>(r.warnings);
                if (!Objects.equals(norm(l.unit()), norm(r.unit)) || norm(l.unit()).isBlank()) warnings.add("UNIT_NOT_CONFIRMED");
                if (!"MATCHED_CODE".equals(match) && !"MATCHED_NAME".equals(match)) warnings.add("MATERIAL_MATCH_NEEDS_REVIEW");
                if (norm(r.specification).isBlank() || !Objects.equals(norm(l.specification()), norm(r.specification))) warnings.add("SPECIFICATION_NOT_CONFIRMED");
                if (r.date == null) warnings.add("PURCHASE_DATE_UNKNOWN");
                if (r.price == null || r.price.signum() <= 0) warnings.add("PRICE_NOT_POSITIVE");
                candidates.add(new HistoryCandidate(r.supplierKey, r.supplierId, r.supplierCode, r.supplierName, r.code, r.name, r.unit,
                        r.price, r.currency, r.currencyBasis, r.date, r.source, r.reference, List.copyOf(new LinkedHashSet<>(warnings)), group.size(),
                        r.specification, r.priceBasis, r.vat));
            }
            candidates.sort(Comparator.comparing(HistoryCandidate::purchaseDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(HistoryCandidate::supplierName));
            output.add(new LookupLine(l.lineKey(), match, l.materialCode(), l.materialName(), List.copyOf(candidates)));
        }
        return new Lookup(List.copyOf(output));
    }
    private List<HistoryRow> load() {
        // Two bulk queries instead of one database call per requested material.
        List<PoRow> po = jdbc.query("""
            SELECT p.id, p.revision, p.status, p.order_date, p.supplier_id, p.supplier_name_snapshot,
                   s.code supplier_code, i.line_no, i.material_code_snapshot, i.material_name, i.specification,
                   i.unit, i.unit_price, p.currency, p.vat_percent, p.source_import_batch_id
            FROM purchase_orders p JOIN purchase_order_items i ON i.purchase_order_id=p.id
            LEFT JOIN suppliers s ON s.id=p.supplier_id
            """, (rs, n) -> {
            Long batch = number(rs.getObject("source_import_batch_id"));
            return new PoRow(rs.getLong("id"), rs.getInt("revision"), rs.getString("status"),
                    new HistoryRow(number(rs.getObject("supplier_id")), text(rs, "supplier_code"), text(rs, "supplier_name_snapshot"),
                            text(rs, "material_code_snapshot"), text(rs, "material_name"), text(rs, "unit"), rs.getBigDecimal("unit_price"),
                            text(rs, "currency"), batch == null ? "SOURCE" : "LEGACY_UNVERIFIED", date(rs, "order_date"), "PURCHASE_ORDER",
                            "PO:" + rs.getLong("id") + ":R:" + rs.getInt("revision") + ":L:" + rs.getInt("line_no"),
                            text(rs, "specification"), batch == null ? "EXCL_VAT" : "UNKNOWN", rs.getBigDecimal("vat_percent"),
                            batch == null ? List.of() : List.of("LEGACY_PO_BASIS_UNVERIFIED")));
        });
        Map<Long, PoRow> current = new HashMap<>(); Set<String> activeReferences = new HashSet<>(); List<HistoryRow> result = new ArrayList<>();
        for (PoRow p : po) { current.put(p.id, p); if ("EXPORTED".equals(p.status)) { result.add(p.row); activeReferences.add(p.row.reference); } }
        List<HistoryRow> historical = jdbc.query("""
            SELECT h.id, h.supplier_id, h.supplier_snapshot, h.supplier_code_snapshot,
                   s.name current_supplier_name, s.code current_supplier_code,
                   h.material_code_snapshot,h.material_name_snapshot,h.unit,h.unit_price,h.currency,
                   h.currency_basis,h.purchase_date,h.source,h.source_reference,h.data_quality_flags
            FROM historical_purchases h LEFT JOIN suppliers s ON s.id=h.supplier_id
            """, (rs, n) -> {
            String supplier = text(rs, "supplier_snapshot"); if (supplier.isBlank()) supplier = text(rs, "current_supplier_name");
            String code = text(rs, "supplier_code_snapshot"); if (code.isBlank()) code = text(rs, "current_supplier_code");
            String ref = text(rs, "source_reference"); if (ref.isBlank()) ref = "HISTORY:" + rs.getLong("id");
            List<String> warnings = new ArrayList<>(); warnings.add("VAT_BASIS_UNKNOWN");
            if (!"SOURCE".equals(text(rs, "currency_basis"))) warnings.add("CURRENCY_ASSUMED");
            if (!text(rs, "data_quality_flags").isBlank() && !"[]".equals(text(rs, "data_quality_flags"))) warnings.add("SOURCE_QUALITY_WARNINGS");
            return new HistoryRow(number(rs.getObject("supplier_id")), code, supplier, text(rs, "material_code_snapshot"),
                    text(rs, "material_name_snapshot"), text(rs, "unit"), rs.getBigDecimal("unit_price"), text(rs, "currency"),
                    text(rs, "currency_basis"), date(rs, "purchase_date"), text(rs, "source"), ref, "", "UNKNOWN", null, warnings);
        });
        for (HistoryRow r : historical) {
            Matcher m = PO_REF.matcher(r.reference);
            if (m.matches()) {
                PoRow p = current.get(Long.parseLong(m.group(1)));
                if (p == null || !"EXPORTED".equals(p.status) || p.revision != Integer.parseInt(m.group(2))) continue;
                if (activeReferences.contains(r.reference)) continue;
            }
            result.add(r);
        }
        return result;
    }
    public Comparison comparison(Confirmation request, List<DocumentView> quotes) {
        Lookup lookup = lookup(request); Map<String, List<HistoryCandidate>> old = new HashMap<>();
        lookup.lines.forEach(l -> old.put(l.lineKey, l.candidates));
        List<ComparisonLine> result = new ArrayList<>();
        for (DocumentLine l : request.lines()) {
            List<HistoryCandidate> history = old.getOrDefault(l.lineKey(), List.of()); List<QuoteCandidate> prices = new ArrayList<>();
            for (DocumentView doc : quotes) for (DocumentLine q : doc.confirmed().lines()) {
                if (!l.lineKey().equals(doc.confirmed().requestLineMappings().get(q.lineKey()))) continue;
                HistoryCandidate baseline = history.stream().filter(h -> reason(l, q, h).isBlank()).findFirst()
                        .orElseGet(() -> history.stream().filter(h -> h.unitPrice() != null && h.unitPrice().signum() > 0).findFirst().orElse(null));
                String reason = reason(l, q, baseline);
                BigDecimal delta = reason.isBlank() ? q.unitPrice().subtract(baseline.unitPrice()) : null;
                BigDecimal percent = delta == null ? null : delta.multiply(BigDecimal.valueOf(100)).divide(baseline.unitPrice(), 4, RoundingMode.HALF_UP);
                prices.add(new QuoteCandidate(doc.id(), doc.confirmed().supplierName(), q.unit(), q.quantity(), q.unitPrice(), q.currency(), q.vatPercent(), q.priceBasis(), reason.isBlank(), reason, delta, percent,
                        reason.isBlank() ? baseline : null));
            }
            result.add(new ComparisonLine(l.lineKey(), l.materialName(), l.unit(), l.quantity(), history, List.copyOf(prices)));
        }
        return new Comparison(List.copyOf(result));
    }
    private static String reason(DocumentLine request, DocumentLine quote, HistoryCandidate old) {
        if (quote.unitPrice() == null) return "Báo giá chưa có đơn giá";
        if (!norm(request.unit()).equals(norm(quote.unit())) || norm(request.unit()).isBlank()) return "ĐVT báo giá và yêu cầu khác hoặc chưa xác định";
        if (!norm(request.specification()).equals(norm(quote.specification()))) return "Quy cách báo giá và yêu cầu chưa xác nhận tương đương";
        if (old == null || old.unitPrice() == null || old.unitPrice().signum() <= 0 || old.purchaseDate() == null) return "Chưa có giá cũ dương, có ngày phù hợp";
        if (!"SOURCE".equals(old.currencyBasis()) || !Objects.equals(old.currency(), quote.currency())) return "Tiền tệ cũ giả định/chưa xác nhận hoặc khác tiền tệ báo giá";
        if (old.qualityWarnings().contains("SOURCE_QUALITY_WARNINGS")) return "Nguồn giá cũ còn cảnh báo cần xác minh";
        if (!norm(old.unit()).equals(norm(quote.unit()))) return "ĐVT giá cũ khác báo giá";
        if (old.qualityWarnings().contains("MATERIAL_MATCH_NEEDS_REVIEW") || old.qualityWarnings().contains("SPECIFICATION_NOT_CONFIRMED")) return "Vật tư/quy cách cũ chưa xác nhận tương đương";
        if ("UNKNOWN".equals(old.priceBasis()) || "UNKNOWN".equals(quote.priceBasis()) || !Objects.equals(old.priceBasis(), quote.priceBasis())) return "Cơ sở VAT của giá chưa xác nhận tương đương";
        if ("INCL_VAT".equals(quote.priceBasis()) && (old.vatPercent() == null || quote.vatPercent() == null || old.vatPercent().compareTo(quote.vatPercent()) != 0)) return "Thuế suất giá gồm VAT chưa tương đương";
        return "";
    }
    private static String norm(String s) { return TextNormalizer.normalize(s); }
    private static String text(ResultSet rs, String column) throws SQLException { String v = rs.getString(column); return v == null ? "" : v.trim(); }
    private static Long number(Object v) { return v == null ? null : ((Number) v).longValue(); }
    private static LocalDate date(ResultSet rs, String column) throws SQLException { java.sql.Date d = rs.getDate(column); return d == null ? null : d.toLocalDate(); }
    private static String supplierKey(Long id, String code, String name) { return id == null ? "SNAP:" + norm(code) + ":" + norm(name) : "ID:" + id; }
    private static final class HistoryRow {
        final Long supplierId; final String supplierKey, supplierCode, supplierName, code, name, unit, currency, currencyBasis, source, reference, specification, priceBasis;
        final String nCode, nName, nUnit, nSpecification;
        final BigDecimal price, vat; final LocalDate date; final List<String> warnings;
        HistoryRow(Long id, String supplierCode, String supplierName, String code, String name, String unit, BigDecimal price, String currency,
                   String currencyBasis, LocalDate date, String source, String reference, String specification, String priceBasis, BigDecimal vat, List<String> warnings) {
            this.supplierId=id; this.supplierCode=supplierCode; this.supplierName=supplierName; this.supplierKey=supplierKey(id,supplierCode,supplierName);
            this.code=code; this.name=name; this.unit=unit; this.price=price; this.currency=currency; this.currencyBasis=currencyBasis; this.date=date;
            this.source=source; this.reference=reference; this.specification=specification; this.priceBasis=priceBasis; this.vat=vat; this.warnings=List.copyOf(warnings);
            this.nCode=norm(code); this.nName=norm(name); this.nUnit=norm(unit); this.nSpecification=norm(specification);
        }
    }
    private record PoRow(long id, int revision, String status, HistoryRow row) {}
    public record HistoryCandidate(String supplierKey, Long supplierId, String supplierCode, String supplierName, String materialCode,
                                   String materialName, String unit, @JsonSerialize(using = ToStringSerializer.class) BigDecimal unitPrice, String currency, String currencyBasis, LocalDate purchaseDate,
                                   String source, String sourceReference, List<String> qualityWarnings, int count, String specification, String priceBasis, @JsonSerialize(using = ToStringSerializer.class) BigDecimal vatPercent) {}
    public record LookupLine(String lineKey, String matchStatus, String materialCode, String materialName, List<HistoryCandidate> candidates) {}
    public record Lookup(List<LookupLine> lines) {}
    public record QuoteCandidate(long documentId, String supplierName, String unit, @JsonSerialize(using = ToStringSerializer.class) BigDecimal quantity,
                                 @JsonSerialize(using = ToStringSerializer.class) BigDecimal unitPrice, String currency,
                                 @JsonSerialize(using = ToStringSerializer.class) BigDecimal vatPercent, String priceBasis, boolean comparable, String reason,
                                 @JsonSerialize(using = ToStringSerializer.class) BigDecimal delta, @JsonSerialize(using = ToStringSerializer.class) BigDecimal percent,
                                 HistoryCandidate baseline) {}
    public record ComparisonLine(String lineKey, String materialName, String unit, @JsonSerialize(using = ToStringSerializer.class) BigDecimal quantity, List<HistoryCandidate> history, List<QuoteCandidate> quotes) {}
    public record Comparison(List<ComparisonLine> lines) {}
}
