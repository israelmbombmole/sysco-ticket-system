package com.app.dao;

import com.app.model.UserAbsence;
import com.app.util.DB;
import com.app.util.DbConfig;
import com.app.util.SqlDialect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class UserAbsenceDAO {

    private UserAbsenceDAO() {}

    /**
     * SQL AND-clause: rows that count as congé/vacances (block assignment, appear in snapshot).
     * {@link UserAbsence#TYPE_ABSENCE} is excluded.
     *
     * @param columnPrefix table alias with dot, e.g. {@code "ua."}, or empty for unqualified {@code absence_type}
     */
    public static String andClauseCongeVacancesOnly(String columnPrefix) {
        String c = columnPrefix + "absence_type";
        return " AND (" + c + " IS NULL OR TRIM(" + c + ") = '' OR UPPER(" + c + ") <> '"
                + UserAbsence.TYPE_ABSENCE + "')";
    }

    /** Exclude field-mission rows from the "En congé ou en vacances" panel (missions stay in history only). */
    public static String andClauseExcludeMission(String columnPrefix) {
        String c = columnPrefix + "absence_type";
        return " AND UPPER(TRIM(" + c + ")) <> UPPER('" + UserAbsence.TYPE_MISSION + "')";
    }

    /** User IDs with congé/vacances overlapping {@code date} (excludes {@link UserAbsence#TYPE_ABSENCE}). */
    public static Set<Integer> userIdsAbsentOn(LocalDate date) {
        Set<Integer> out = new HashSet<>();
        if (date == null) {
            return out;
        }
        String d = date.toString();
        String sql = "SELECT DISTINCT user_id FROM user_absences WHERE start_date <= ? AND end_date >= ?"
                + andClauseCongeVacancesOnly("");
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d);
            ps.setString(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static boolean isUserAbsentOn(int userId, LocalDate date) {
        if (date == null || userId <= 0) {
            return false;
        }
        String d = date.toString();
        String sql = """
                SELECT 1 FROM user_absences
                WHERE user_id = ? AND start_date <= ? AND end_date >= ?
                """ + andClauseCongeVacancesOnly("");
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, d);
            ps.setString(3, d);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<UserAbsence> listOverlapping(LocalDate date) {
        List<UserAbsence> out = new ArrayList<>();
        if (date == null) {
            return out;
        }
        String d = date.toString();
        String sql = """
                SELECT ua.id, ua.user_id, u.username, ua.start_date, ua.end_date,
                       ua.absence_type, ua.notes, ua.created_by, ua.created_at, ua.mission_id
                FROM user_absences ua
                JOIN users u ON u.id = ua.user_id
                WHERE ua.start_date <= ? AND ua.end_date >= ?
                """ + andClauseCongeVacancesOnly("ua.") + """
                ORDER BY u.username, ua.start_date
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d);
            ps.setString(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * Same as {@link #listOverlapping} but excludes {@link UserAbsence#TYPE_MISSION} (for the congé/vacances table only).
     */
    public static List<UserAbsence> listOverlappingCongeVacancesOnly(LocalDate date) {
        List<UserAbsence> out = new ArrayList<>();
        if (date == null) {
            return out;
        }
        String d = date.toString();
        String sql = """
                SELECT ua.id, ua.user_id, u.username, ua.start_date, ua.end_date,
                       ua.absence_type, ua.notes, ua.created_by, ua.created_at, ua.mission_id
                FROM user_absences ua
                JOIN users u ON u.id = ua.user_id
                WHERE ua.start_date <= ? AND ua.end_date >= ?
                """ + andClauseCongeVacancesOnly("ua.") + andClauseExcludeMission("ua.") + """
                ORDER BY u.username, ua.start_date
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d);
            ps.setString(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * MISSION rows with {@code start_date > asOf} and {@code start_date <= asOf + maxDaysAheadInclusive}
     * (ISO date string comparison). Used so planned missions hide users from "Disponibles" before day 1.
     */
    public static List<UserAbsence> listUpcomingMissions(LocalDate asOf, int maxDaysAheadInclusive) {
        List<UserAbsence> out = new ArrayList<>();
        if (asOf == null || maxDaysAheadInclusive < 0) {
            return out;
        }
        LocalDate horizon = asOf.plusDays(maxDaysAheadInclusive);
        String dAfter = asOf.toString();
        String dHorizon = horizon.toString();
        String sql = """
                SELECT ua.id, ua.user_id, u.username, ua.start_date, ua.end_date,
                       ua.absence_type, ua.notes, ua.created_by, ua.created_at, ua.mission_id
                FROM user_absences ua
                JOIN users u ON u.id = ua.user_id
                WHERE UPPER(TRIM(ua.absence_type)) = UPPER(?)
                  AND ua.start_date > ?
                  AND ua.start_date <= ?
                ORDER BY u.username, ua.start_date
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, UserAbsence.TYPE_MISSION);
            ps.setString(2, dAfter);
            ps.setString(3, dHorizon);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static Set<Integer> userIdsWithUpcomingMissions(LocalDate asOf, int maxDaysAheadInclusive) {
        Set<Integer> out = new LinkedHashSet<>();
        for (UserAbsence a : listUpcomingMissions(asOf, maxDaysAheadInclusive)) {
            if (a.getUserId() > 0) {
                out.add(a.getUserId());
            }
        }
        return out;
    }

    public static List<UserAbsence> listAllRecent(int maxRows) {
        List<UserAbsence> out = new ArrayList<>();
        int n = Math.max(1, Math.min(maxRows, 2000));
        String base = """
                SELECT ua.id, ua.user_id, u.username, ua.start_date, ua.end_date,
                       ua.absence_type, ua.notes, ua.created_by, ua.created_at, ua.mission_id
                FROM user_absences ua
                JOIN users u ON u.id = ua.user_id
                ORDER BY ua.start_date DESC, ua.id DESC
                """;
        try (Connection c = DB.getConnection()) {
            String sql = SqlDialect.isOracle() ? base + " FETCH FIRST " + n + " ROWS ONLY" : base + " LIMIT ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (!SqlDialect.isOracle()) {
                    ps.setInt(1, n);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(map(rs, true));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /** Recent rows with {@link UserAbsence#TYPE_MISSION} only (leave management mission table). */
    public static List<UserAbsence> listMissionsRecent(int maxRows) {
        List<UserAbsence> out = new ArrayList<>();
        int n = Math.max(1, Math.min(maxRows, 2000));
        String base = """
                SELECT ua.id, ua.user_id, u.username, ua.start_date, ua.end_date,
                       ua.absence_type, ua.notes, ua.created_by, ua.created_at, ua.mission_id
                FROM user_absences ua
                JOIN users u ON u.id = ua.user_id
                WHERE ua.absence_type = ?
                ORDER BY ua.start_date DESC, ua.id DESC
                """;
        try (Connection c = DB.getConnection()) {
            String sql = SqlDialect.isOracle() ? base + " FETCH FIRST " + n + " ROWS ONLY" : base + " LIMIT ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, UserAbsence.TYPE_MISSION);
                if (!SqlDialect.isOracle()) {
                    ps.setInt(2, n);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(map(rs, true));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /** All absence and mission rows for a user (for calendar / history). */
    public static List<UserAbsence> listByUserId(int userId) {
        List<UserAbsence> out = new ArrayList<>();
        if (userId <= 0) {
            return out;
        }
        String sql = """
                SELECT ua.id, ua.user_id, u.username, ua.start_date, ua.end_date,
                       ua.absence_type, ua.notes, ua.created_by, ua.created_at, ua.mission_id
                FROM user_absences ua
                JOIN users u ON u.id = ua.user_id
                WHERE ua.user_id = ?
                ORDER BY ua.start_date, ua.id
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static int insert(UserAbsence a) {
        String sql = SqlDialect.isOracle()
                ? """
                INSERT INTO user_absences (user_id, start_date, end_date, absence_type, notes, created_by, mission_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """
                : """
                INSERT INTO user_absences (user_id, start_date, end_date, absence_type, notes, created_by, mission_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = DbConfig.isOracle()
                     ? conn.prepareStatement(sql, new String[] { "ID" })
                     : conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getUserId());
            ps.setString(2, a.getStartDate());
            ps.setString(3, a.getEndDate());
            ps.setString(4, a.getAbsenceType() != null ? a.getAbsenceType() : UserAbsence.TYPE_LEAVE);
            ps.setString(5, emptyToNull(a.getNotes()));
            if (a.getCreatedBy() != null) {
                ps.setInt(6, a.getCreatedBy());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            if (a.getMissionId() != null) {
                ps.setInt(7, a.getMissionId());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
            if (DbConfig.isOracle()) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        Object o = keys.getObject(1);
                        if (o instanceof Number) {
                            int id = ((Number) o).intValue();
                            if (id > 0) {
                                return id;
                            }
                        }
                    }
                } catch (Exception ignored) { }
                return fetchLastIdOracle(conn, a.getUserId(), a.getStartDate(), a.getEndDate());
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static int fetchLastIdOracle(Connection conn, int userId, String start, String end) throws Exception {
        String q = """
                SELECT id FROM user_absences
                WHERE user_id = ? AND start_date = ? AND end_date = ?
                ORDER BY id DESC FETCH FIRST 1 ROWS ONLY
                """;
        try (PreparedStatement p = conn.prepareStatement(q)) {
            p.setInt(1, userId);
            p.setString(2, start);
            p.setString(3, end);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public static boolean delete(int id) {
        String sql = "DELETE FROM user_absences WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Removes auto-generated mission absences before mission delete or resync. */
    public static void deleteByMissionId(Connection conn, int missionId) {
        if (missionId <= 0) {
            return;
        }
        String sql = "DELETE FROM user_absences WHERE mission_id = ?";
        try {
            if (conn != null) {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, missionId);
                    ps.executeUpdate();
                }
                return;
            }
            try (Connection c = DB.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, missionId);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Replaces {@code user_absences} rows for {@code missionId}: one MISSION row per distinct lead + participant,
     * dates from the mission, type {@link UserAbsence#TYPE_MISSION}, notes {@link UserAbsence#NOTE_MISSION}.
     */
    public static void syncAbsencesForFieldMission(Connection conn, int missionId, Integer leadUserId,
            List<Integer> participantUserIds, String startDateRaw, String endDateRaw, Integer createdBy) {
        if (missionId <= 0) {
            return;
        }
        String start = normalizeDateIso(startDateRaw);
        String end = normalizeDateIso(endDateRaw);
        try {
            deleteByMissionId(conn, missionId);
            if (start == null || end == null) {
                return;
            }
            LocalDate ds = LocalDate.parse(start);
            LocalDate de = LocalDate.parse(end);
            if (de.isBefore(ds)) {
                LocalDate tmp = ds;
                ds = de;
                de = tmp;
                start = ds.toString();
                end = de.toString();
            }
            LinkedHashSet<Integer> users = new LinkedHashSet<>();
            if (leadUserId != null && leadUserId > 0) {
                users.add(leadUserId);
            }
            if (participantUserIds != null) {
                for (Integer uid : participantUserIds) {
                    if (uid != null && uid > 0) {
                        users.add(uid);
                    }
                }
            }
            if (users.isEmpty()) {
                return;
            }
            String sql = """
                    INSERT INTO user_absences (user_id, start_date, end_date, absence_type, notes, created_by, mission_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            Connection c = conn != null ? conn : DB.getConnection();
            boolean closeConn = (conn == null);
            try {
                if (closeConn) {
                    c.setAutoCommit(false);
                }
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    for (int uid : users) {
                        ps.setInt(1, uid);
                        ps.setString(2, start);
                        ps.setString(3, end);
                        ps.setString(4, UserAbsence.TYPE_MISSION);
                        ps.setString(5, UserAbsence.NOTE_MISSION);
                        if (createdBy != null) {
                            ps.setInt(6, createdBy);
                        } else {
                            ps.setNull(6, java.sql.Types.INTEGER);
                        }
                        ps.setInt(7, missionId);
                        ps.executeUpdate();
                    }
                }
                if (closeConn) {
                    c.commit();
                }
            } catch (Exception e) {
                if (closeConn) {
                    try {
                        c.rollback();
                    } catch (Exception ignored) { }
                }
                e.printStackTrace();
            } finally {
                if (closeConn && c != null) {
                    try {
                        c.setAutoCommit(true);
                        c.close();
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String normalizeDateIso(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        return t.length() >= 10 ? t.substring(0, 10) : t;
    }

    private static UserAbsence map(ResultSet rs, boolean withUsername) throws Exception {
        UserAbsence a = new UserAbsence();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getInt("user_id"));
        if (withUsername) {
            a.setUsername(rs.getString("username"));
        }
        a.setStartDate(rs.getString("start_date"));
        a.setEndDate(rs.getString("end_date"));
        a.setAbsenceType(rs.getString("absence_type"));
        a.setNotes(rs.getString("notes"));
        int cb = rs.getInt("created_by");
        if (rs.wasNull()) {
            a.setCreatedBy(null);
        } else {
            a.setCreatedBy(cb);
        }
        a.setCreatedAt(readTs(rs, "created_at"));
        try {
            int mid = rs.getInt("mission_id");
            if (!rs.wasNull()) {
                a.setMissionId(mid);
            }
        } catch (Exception ignored) {
        }
        return a;
    }

    private static String readTs(ResultSet rs, String col) {
        try {
            Timestamp t = rs.getTimestamp(col);
            if (t != null) {
                return t.toLocalDateTime().toString();
            }
        } catch (Exception ignored) { }
        try {
            return rs.getString(col);
        } catch (Exception e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }

}
