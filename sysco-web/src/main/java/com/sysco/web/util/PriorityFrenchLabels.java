package com.sysco.web.util;

import java.util.Locale;

/**
 * French labels for ticket / task / courier priority codes in PDFs and other non-Thymeleaf outputs.
 */
public final class PriorityFrenchLabels {

    private PriorityFrenchLabels() {}

    public static String french(String code) {
        if (code == null || code.isBlank()) {
            return "-";
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> "Faible";
            case "MEDIUM" -> "Moyenne";
            case "HIGH" -> "Haute";
            case "CRITICAL" -> "Critique";
            default -> code.trim();
        };
    }
}
