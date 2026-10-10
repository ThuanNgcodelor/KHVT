package com.example.quanlymuahang.importing.application;

import com.example.quanlymuahang.domain.common.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Exact legacy values. Ambiguous separators require correction/confirmation in the source. */
public final class LegacyImportValues {
    private LegacyImportValues() {}

    public static String value(Map<String, String> data, String key) {
        String result = data.get(key);
        return result == null || result.isBlank() ? null : result.trim();
    }

    public static BigDecimal decimal(Map<String, String> data, String field) {
        String text = value(data, field);
        if (text == null) return null;
        if (!"vatPercent".equals(field) && text.endsWith("%")) return null;
        if ("true".equals(data.get(field + "__numeric"))) {
            try { return new BigDecimal(text); } catch (NumberFormatException ignored) { return null; }
        }
        return decimalText(text);
    }

    public static BigDecimal decimalText(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        // Space groups must be complete thousands groups; arbitrary spaces are never removed.
        if (text.matches("[+-]?\\d{1,3}(?:[ \\u00a0]\\d{3})+(?:[.,]\\d+)?"))
            text = text.replace(" ", "").replace("\u00a0", "");
        if (text.endsWith("%")) text = text.substring(0, text.length() - 1);
        if (text.matches("[+-]?\\d{1,3}(?:\\.\\d{3})+,\\d+")) text = text.replace(".", "").replace(',', '.');
        else if (text.matches("[+-]?\\d{1,3}(?:,\\d{3})+\\.\\d+")) text = text.replace(",", "");
        else if (text.matches("[+-]?\\d{1,3}(?:[.,]\\d{3}){2,}")) text = text.replace(",", "").replace(".", "");
        else if (text.matches("[+-]?\\d+[.,]\\d{3}")) return null;
        else if (text.matches("[+-]?\\d+,\\d+")) text = text.replace(',', '.');
        if (!text.matches("[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?")) return null;
        try { return new BigDecimal(text); } catch (NumberFormatException ignored) { return null; }
    }

    public static boolean looksNumeric(String text) {
        return text != null && text.trim().matches("[+-]?[\\d.,% \\u00a0]+(?:[eE][+-]?\\d+)?");
    }

    public static LocalDate date(String text) {
        if (text == null || text.isBlank()) return null;
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT))) {
            try { return LocalDate.parse(text.trim(), formatter); } catch (RuntimeException ignored) { }
        }
        return null;
    }

    public static CurrencyCode currency(String text) {
        if (text == null || text.isBlank()) return CurrencyCode.VND;
        return switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "VND", "VNĐ" -> CurrencyCode.VND;
            case "USD" -> CurrencyCode.USD;
            default -> throw new IllegalArgumentException("Unsupported source currency");
        };
    }
}
