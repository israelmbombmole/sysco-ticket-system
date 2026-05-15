package com.sysco.web.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DisplayDateFormatter {

    /** Display dates as dd/MM/yyyy everywhere (aligned with date pickers). */
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    /** Chat thread: day + French month name + year + time (e.g. {@code 09 Mai 2026 11:40}). */
    private static final DateTimeFormatter CHAT_MESSAGE_DATE_TIME =
            DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm", Locale.FRENCH);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);

    private DisplayDateFormatter() {}

    public static String formatInstant(Instant value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FMT.withZone(ZoneId.systemDefault()).format(value);
    }

    /** Same zone as {@link #formatInstant}; month word title-cased for UI (e.g. Mai, Décembre). */
    public static String formatChatMessageInstant(Instant value) {
        if (value == null) {
            return "";
        }
        String raw = CHAT_MESSAGE_DATE_TIME.withZone(ZoneId.systemDefault()).format(value);
        return capitalizeFrenchMonthWord(raw);
    }

    private static String capitalizeFrenchMonthWord(String formatted) {
        if (formatted == null || formatted.isBlank()) {
            return formatted;
        }
        int spaceAfterDay = formatted.indexOf(' ');
        if (spaceAfterDay < 0) {
            return formatted;
        }
        int spaceAfterMonth = formatted.indexOf(' ', spaceAfterDay + 1);
        if (spaceAfterMonth < 0) {
            return formatted;
        }
        String month = formatted.substring(spaceAfterDay + 1, spaceAfterMonth);
        if (month.isEmpty()) {
            return formatted;
        }
        String titled =
                month.substring(0, 1).toUpperCase(Locale.FRENCH) + month.substring(1).toLowerCase(Locale.FRENCH);
        return formatted.substring(0, spaceAfterDay + 1) + titled + formatted.substring(spaceAfterMonth);
    }

    public static String formatLocalDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DATE_TIME_FMT.format(value);
    }

    public static String formatLocalDate(LocalDate value) {
        if (value == null) {
            return "";
        }
        return DATE_FMT.format(value);
    }
}
