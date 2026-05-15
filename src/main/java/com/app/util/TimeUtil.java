package com.app.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;

public class TimeUtil {

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Oracle JDBC often returns {@code getString} on TIMESTAMP as
     * {@code 31-MAR-26 11.50.00.000000000 AM} (dots in time, English month, AM/PM).
     */
    private static final DateTimeFormatter ORACLE_JDBC_TS_YY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-yy")
            .appendLiteral(' ')
            .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
            .appendLiteral('.')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral('.')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendLiteral('.')
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, false)
            .optionalEnd()
            .appendLiteral(' ')
            .appendPattern("a")
            .toFormatter(Locale.ENGLISH);

    private static final DateTimeFormatter ORACLE_JDBC_TS_YYYY = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-uuuu")
            .appendLiteral(' ')
            .appendValue(ChronoField.CLOCK_HOUR_OF_AMPM, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
            .appendLiteral('.')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral('.')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendLiteral('.')
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, false)
            .optionalEnd()
            .appendLiteral(' ')
            .appendPattern("a")
            .toFormatter(Locale.ENGLISH);

    private static LocalDateTime tryParseOracleJdbcTimestamp(String s) {
        if (s == null || s.length() < 14) {
            return null;
        }
        if (s.indexOf('-') < 0 || !s.regionMatches(true, 2, "-", 0, 1)) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, ORACLE_JDBC_TS_YY);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(s, ORACLE_JDBC_TS_YYYY);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /**
     * Parses timestamps returned from JDBC / SQLite / Oracle ({@code yyyy-MM-dd HH:mm:ss},
     * optional fractional seconds, optional {@code T} separator). Used by job scheduler and reports.
     */
    public static LocalDateTime parseDbLocalDateTime(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        LocalDateTime oracle = tryParseOracleJdbcTimestamp(s);
        if (oracle != null) {
            return oracle;
        }
        int tz = s.indexOf('+', 10);
        if (tz < 0) {
            tz = s.indexOf('-', 19);
        }
        if (tz > 19) {
            s = s.substring(0, tz).trim();
        }
        if (s.length() >= 10 && s.charAt(10) == 'T') {
            s = s.substring(0, 10) + " " + s.substring(11);
        }
        if (s.length() > 19 && s.charAt(19) == '.') {
            s = s.substring(0, 19);
        }
        s = s.trim();
        try {
            return LocalDateTime.parse(s, formatter);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    public static String timeAgo(String timestamp) {

        try {

            LocalDateTime created =
                    LocalDateTime.parse(timestamp.substring(0,19), formatter);

            // Use PC system time
            LocalDateTime now = LocalDateTime.now();

            Duration duration = Duration.between(created, now);

            long seconds = Math.abs(duration.getSeconds());

            if (seconds < 60)
                return I18n.t("time.justNow", "just now");

            long minutes = seconds / 60;

            if (minutes < 60)
                return minutes + " " + I18n.t("time.minAgo", "min ago");

            long hours = minutes / 60;

            if (hours < 24)
                return hours + " " + I18n.t("time.hrAgo", "hr ago");

            long days = hours / 24;

            if (days == 1)
                return I18n.t("time.yesterday", "yesterday");

            return days + " " + I18n.t("time.daysAgo", "days ago");

        } catch (Exception e) {
            return "";
        }
    }

    public static String formatDurationMinutes(Integer minutes) {
        if (minutes == null || minutes < 0) {
            return "-";
        }
        if (minutes < 60) {
            return minutes + " " + I18n.t("minutes", "min");
        }
        long total = minutes.longValue();
        long days = total / (24L * 60L);
        long remainingAfterDays = total % (24L * 60L);
        long hours = remainingAfterDays / 60L;
        long mins = remainingAfterDays % 60L;

        String dayUnit = I18n.t("time.dayShort", "d");
        String hourUnit = I18n.t("time.hourShort", "h");
        String minuteUnit = I18n.t("minutes", "min");

        if (days > 0) {
            return days + dayUnit + " " + hours + hourUnit + " " + mins + " " + minuteUnit;
        }
        return hours + hourUnit + " " + mins + " " + minuteUnit;
    }
}