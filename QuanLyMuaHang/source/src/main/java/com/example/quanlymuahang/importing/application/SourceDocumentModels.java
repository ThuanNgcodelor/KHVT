package com.example.quanlymuahang.importing.application;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Source extraction only. A quotation is not a historical purchase or an issued PO. */
public final class SourceDocumentModels {
    private SourceDocumentModels() {}

    public record SheetInfo(String name, int detectedLines, List<String> requestNumbers, String quotationDate, int excludedHiddenRows) {
        public SheetInfo(String name, int detectedLines, List<String> requestNumbers, String quotationDate) {
            this(name, detectedLines, requestNumbers, quotationDate, 0);
        }
        public SheetInfo { requestNumbers = requestNumbers == null ? List.of() : List.copyOf(requestNumbers); }
    }

    public record DocumentLine(String lineKey, String sourceSheet, int sourceRow, Integer sourcePage,
                               String requestNumber, String materialCode, String materialName,
                               String specification, String unit,
                               @JsonSerialize(using = ToStringSerializer.class) BigDecimal quantity, String quantityText,
                               @JsonSerialize(using = ToStringSerializer.class) BigDecimal unitPrice,
                               @JsonSerialize(using = ToStringSerializer.class) BigDecimal lineTotal,
                               @JsonSerialize(using = ToStringSerializer.class) BigDecimal vatPercent,
                               String priceBasis, String currency, String note,
                               Map<String, String> raw, List<String> issues) {
        public DocumentLine {
            raw = raw == null ? Map.of() : Map.copyOf(raw);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public record ParsedDocument(String kind, String fileName, String format, List<SheetInfo> sheets,
                                 List<DocumentLine> lines, String supplierName, String quotationDate,
                                 String currency, @JsonSerialize(using = ToStringSerializer.class) BigDecimal vatPercent, String priceBasis, String validityNote,
                                 List<String> warnings, String parserVersion) {
        public ParsedDocument {
            sheets = sheets == null ? List.of() : List.copyOf(sheets);
            lines = lines == null ? List.of() : List.copyOf(lines);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
