package com.app.dao;

import com.app.util.DB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-user per-day shift planning (one row per (user, calendar day), e.g. DGDA-style JOUR/NUIT/REPOS.
 */
public final class MyShiftPlannedDAO {

    public static final String DEFAULT_WEEKDAY = "JOUR";
    public static final String DEFAULT_WEEKEND = "REPOS";

    private MyShiftPlannedDAO() {}

    public static Map<LocalDate, String> getMonthForUser(int userId, YearMonth ym) {
        if (ym == null) {
            return Map.of();
        }
        String from = ym.atDay(1).toString();
        String to = ym.atEndOfMonth().toString();
        Map<LocalDate, String> m = new HashMap<>();
        String sql = """
                SELECT work_date, shift_code FROM myshift_planned
                WHERE user_id = ? AND work_date >= ? AND work_date <= ?
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, from);
            ps.setString(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    m.put(LocalDate.parse(rs.getString(1)), rs.getString(2));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return m;
    }

    /**
     * Replaces the whole month with a 5/7 pattern: Mon–Fri = weekdayCode, Sat–Sun = weekendCode
     * (e.g. JOUR / REPOS). Typical office schedule; adjust codes to your org (DGDA, etc.).
     */
    public static int applyAndSave5over7(
            int userId,
            YearMonth ym,
            String weekdayCode,
            String weekendCode,
            int createdBy) {
        if (ym == null || weekdayCode == null || weekendCode == null) {
            return 0;
        }
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                deleteUserMonth(c, userId, ym);
                int n = 0;
                for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                    LocalDate date = ym.atDay(d);
                    String code = isWeekend(date) ? weekendCode : weekdayCode;
                    insertPlanned(c, userId, date, code, createdBy);
                    n++;
                }
                c.commit();
                return n;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static void deleteUserMonth(Connection c, int userId, YearMonth ym) throws Exception {
        String from = ym.atDay(1).toString();
        String to = ym.atEndOfMonth().toString();
        String del = "DELETE FROM myshift_planned WHERE user_id = ? AND work_date >= ? AND work_date <= ?";
        try (PreparedStatement ps = c.prepareStatement(del)) {
            ps.setInt(1, userId);
            ps.setString(2, from);
            ps.setString(3, to);
            ps.executeUpdate();
        }
    }

    private static void insertPlanned(
            Connection c, int userId, LocalDate date, String code, int createdBy) throws Exception {
        String sql = """
                INSERT INTO myshift_planned (user_id, work_date, shift_code, created_by)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, date.toString());
            ps.setString(3, code);
            ps.setInt(4, createdBy);
            ps.executeUpdate();
        }
    }

    private static boolean isWeekend(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
    }
}
