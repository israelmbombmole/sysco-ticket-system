package com.app.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Duration;

/**
 * Elapsed time since pointage sign-in, for "live" directeur / sous directeur views (QContact-style).
 */
public final class MyShiftDurationUtil {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MyShiftDurationUtil() {}

    /** e.g. {@code 3:15:22} (hours not limited to 24) or "—" if not parseable. */
    public static String formatElapsedSinceSignIn(String signInTime) {
        if (signInTime == null || signInTime.isBlank()) {
            return "—";
        }
        try {
            LocalDateTime t = LocalDateTime.parse(signInTime, TS);
            return formatElapsed(t, LocalDateTime.now());
        } catch (DateTimeParseException e) {
            return "—";
        }
    }

    static String formatElapsed(LocalDateTime start, LocalDateTime end) {
        Duration d = Duration.between(start, end);
        if (d.isNegative()) {
            return "—";
        }
        long total = d.getSeconds();
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        return String.format("%d:%02d:%02d", h, m, s);
    }

    /**
     * For CSV / reports: duration from sign-in to sign-out, or to the current time if sign-out is empty
     * (e.g. open day or not yet closed at export time).
     */
    public static String formatSessionDurationForExport(String signInTime, String signOutTime) {
        if (signInTime == null || signInTime.isBlank()) {
            return "—";
        }
        try {
            LocalDateTime tIn = LocalDateTime.parse(signInTime, TS);
            if (signOutTime != null && !signOutTime.isBlank()) {
                LocalDateTime tOut = LocalDateTime.parse(signOutTime, TS);
                return formatElapsed(tIn, tOut);
            }
            return formatElapsed(tIn, LocalDateTime.now());
        } catch (DateTimeParseException e) {
            return "—";
        }
    }
}
