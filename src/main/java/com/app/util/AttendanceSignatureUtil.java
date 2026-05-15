package com.app.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

/**
 * Stable per-user registry code for MyShift / attendance "Signature" column.
 */
public final class AttendanceSignatureUtil {

    public static final int MAX_LEN = 64;

    private AttendanceSignatureUtil() {}

    /** Trim and cap length; returns null if blank after trim. */
    public static String clamp(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > MAX_LEN ? t.substring(0, MAX_LEN) : t;
    }

    /**
     * Default code: matricule fragment + numeric user id (unique). User may override in user admin.
     */
    public static String generateForUser(int userId, String matricule) {
        StringBuilder sb = new StringBuilder();
        if (matricule != null && !matricule.isBlank()) {
            String clean = matricule.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
            if (!clean.isEmpty()) {
                if (clean.length() > 24) {
                    clean = clean.substring(0, 24);
                }
                sb.append(clean).append('-');
            }
        }
        sb.append(String.format(Locale.ROOT, "%05d", userId));
        String sig = sb.toString();
        return sig.length() > MAX_LEN ? sig.substring(0, MAX_LEN) : sig;
    }

    /**
     * Assign codes for rows where {@code attendance_signature} is null/blank (portable across DBs).
     */
    public static void backfillMissing(Connection c) throws Exception {
        String q = "SELECT id, matricule, attendance_signature FROM users";
        try (PreparedStatement ps = c.prepareStatement(q);
             ResultSet rs = ps.executeQuery()) {
            String upd = "UPDATE users SET attendance_signature = ? WHERE id = ?";
            try (PreparedStatement up = c.prepareStatement(upd)) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String mat = rs.getString(2);
                    String existing = rs.getString(3);
                    if (existing != null && !existing.trim().isEmpty()) {
                        continue;
                    }
                    String sig = generateForUser(id, mat);
                    up.setString(1, sig);
                    up.setInt(2, id);
                    up.executeUpdate();
                }
            }
        }
    }
}
