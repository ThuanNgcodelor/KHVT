package com.example.quanlymuahang.importing.application;

import java.math.BigDecimal;

public final class LocalizedNumberParser {
    private LocalizedNumberParser() {}

    public static BigDecimal parse(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim().replace("\u00a0", "").replace(" ", "");
        if (text.endsWith("%")) text = text.substring(0, text.length() - 1);
        int comma = text.lastIndexOf(',');
        int dot = text.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) text = text.replace(".", "").replace(',', '.');
            else text = text.replace(",", "");
        } else if (comma >= 0) {
            int decimals = text.length() - comma - 1;
            if (text.indexOf(',') == comma && decimals > 0 && decimals <= 2) text = text.replace(',', '.');
            else text = text.replace(",", "");
        } else if (dot >= 0) {
            int decimals = text.length() - dot - 1;
            if (text.indexOf('.') != dot || decimals == 3) text = text.replace(".", "");
        }
        try { return new BigDecimal(text); }
        catch (NumberFormatException exception) { return null; }
    }
}
