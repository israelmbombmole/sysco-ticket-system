package com.app.dao;

import com.app.auth.Session;
import com.app.model.FieldMission;
import com.app.model.FieldMissionAttachment;
import com.app.util.AccessContext;
import com.app.util.DB;
import com.app.util.SqlDialect;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public final class MissionDAO {

    private MissionDAO() {}

    private static String placeholderCode() {
        return "P-" + System.currentTimeMillis();
    }

    private static String finalCodeForId(int id) {
        return "M-" + Year.now().getValue() + "-" + String.format("%04d", id);
    }

    /**
     * Oracle {@code getGeneratedKeys()} often returns non-numeric columns first (ORA-17132 on {@code getInt(1)}).
     * Resolving by unique placeholder {@code mission_code} is reliable on both SQLite and Oracle.
     */
    private static int fetchMissionIdByMissionCode(Connection conn, String missionCode) throws SQLException {
        String q = "SELECT id FROM field_missions WHERE mission_code = ?";
        try (PreparedStatement p = conn.prepareStatement(q)) {
            p.setString(1, missionCode);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public static int insert(FieldMission m, List<Integer> participantUserIds) {
        String sql = """
                INSERT INTO field_missions (
                    mission_code, title, site_location, start_date, end_date, description, objectives,
                    status, report_text, report_submitted_at, lead_user_id, created_by,
                    order_reference, order_issue_date, order_issued_by, order_body
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String placeholderMissionCode = placeholderCode();
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, placeholderMissionCode);
            ps.setString(2, nullToEmpty(m.getTitle()));
            ps.setString(3, emptyToNull(m.getSiteLocation()));
            ps.setString(4, emptyToNull(m.getStartDate()));
            ps.setString(5, emptyToNull(m.getEndDate()));
            ps.setString(6, emptyToNull(m.getDescription()));
            ps.setString(7, emptyToNull(m.getObjectives()));
            ps.setString(8, m.getStatus() != null ? m.getStatus() : FieldMission.STATUS_PLANNED);
            ps.setString(9, emptyToNull(m.getReportText()));
            ps.setString(10, emptyToNull(m.getReportSubmittedAt()));
            if (m.getLeadUserId() != null) {
                ps.setInt(11, m.getLeadUserId());
            } else {
                ps.setNull(11, java.sql.Types.INTEGER);
            }
            ps.setInt(12, Session.getUserId());
            ps.setString(13, emptyToNull(m.getOrderReference()));
            ps.setString(14, emptyToNull(m.getOrderIssueDate()));
            ps.setString(15, emptyToNull(m.getOrderIssuedBy()));
            ps.setString(16, emptyToNull(m.getOrderBody()));
            ps.executeUpdate();
            int id = 0;
            if (SqlDialect.isOracle()) {
                id = fetchMissionIdByMissionCode(conn, placeholderMissionCode);
            } else {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
                if (id <= 0) {
                    id = fetchMissionIdByMissionCode(conn, placeholderMissionCode);
                }
            }
            if (id <= 0) {
                return 0;
            }
            String code = finalCodeForId(id);
            String upd = SqlDialect.isOracle()
                    ? "UPDATE field_missions SET mission_code = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                    : "UPDATE field_missions SET mission_code = ?, updated_at = datetime('now','localtime') WHERE id = ?";
            try (PreparedStatement up = conn.prepareStatement(upd)) {
                up.setString(1, code);
                up.setInt(2, id);
                up.executeUpdate();
            }
            replaceParticipants(conn, id, participantUserIds);
            UserAbsenceDAO.syncAbsencesForFieldMission(conn, id, m.getLeadUserId(), participantUserIds,
                    m.getStartDate(), m.getEndDate(), Session.getUserId());
            assignReportAuthorIfUnset(conn, id, m.getReportText());
            return id;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static boolean update(FieldMission m, List<Integer> participantUserIds) {
        if (m.getId() <= 0) {
            return false;
        }
        String sql = """
                UPDATE field_missions SET
                    title = ?, site_location = ?, start_date = ?, end_date = ?, description = ?, objectives = ?,
                    status = ?, report_text = ?, report_submitted_at = ?, lead_user_id = ?,
                    order_reference = ?, order_issue_date = ?, order_issued_by = ?, order_body = ?,
                    updated_at = """ + (SqlDialect.isOracle() ? "CURRENT_TIMESTAMP" : "datetime('now','localtime')") + """
                 WHERE id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nullToEmpty(m.getTitle()));
            ps.setString(2, emptyToNull(m.getSiteLocation()));
            ps.setString(3, emptyToNull(m.getStartDate()));
            ps.setString(4, emptyToNull(m.getEndDate()));
            ps.setString(5, emptyToNull(m.getDescription()));
            ps.setString(6, emptyToNull(m.getObjectives()));
            ps.setString(7, m.getStatus() != null ? m.getStatus() : FieldMission.STATUS_PLANNED);
            ps.setString(8, emptyToNull(m.getReportText()));
            ps.setString(9, emptyToNull(m.getReportSubmittedAt()));
            if (m.getLeadUserId() != null) {
                ps.setInt(10, m.getLeadUserId());
            } else {
                ps.setNull(10, java.sql.Types.INTEGER);
            }
            ps.setString(11, emptyToNull(m.getOrderReference()));
            ps.setString(12, emptyToNull(m.getOrderIssueDate()));
            ps.setString(13, emptyToNull(m.getOrderIssuedBy()));
            ps.setString(14, emptyToNull(m.getOrderBody()));
            ps.setInt(15, m.getId());
            ps.executeUpdate();
            replaceParticipants(conn, m.getId(), participantUserIds);
            UserAbsenceDAO.syncAbsencesForFieldMission(conn, m.getId(), m.getLeadUserId(), participantUserIds,
                    m.getStartDate(), m.getEndDate(), Session.getUserId());
            if (userMayEditMissionReport(m.getId(), Session.getUserId(), missionFullAdminAccess(m.getId()))) {
                assignReportAuthorIfUnset(conn, m.getId(), m.getReportText());
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Updates only {@code report_text} (compte rendu); assigns {@code report_author_id} on first non-empty save. */
    public static boolean updateMissionReportOnly(int missionId, String reportText) {
        if (missionId <= 0) {
            return false;
        }
        boolean hasBody = emptyToNull(reportText) != null;
        String sql = SqlDialect.isOracle()
                ? """
                UPDATE field_missions SET
                    report_text = ?,
                    report_author_id = CASE WHEN ? = 1 THEN NVL(report_author_id, ?) ELSE report_author_id END,
                    updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """
                : """
                UPDATE field_missions SET
                    report_text = ?,
                    report_author_id = CASE WHEN ? = 1 THEN COALESCE(report_author_id, ?) ELSE report_author_id END,
                    updated_at = datetime('now','localtime')
                 WHERE id = ?
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, emptyToNull(reportText));
            ps.setInt(2, hasBody ? 1 : 0);
            ps.setInt(3, Session.getUserId());
            ps.setInt(4, missionId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void setReportSubmittedNow(int missionId) {
        String sql = SqlDialect.isOracle()
                ? "UPDATE field_missions SET report_submitted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
                : "UPDATE field_missions SET report_submitted_at = datetime('now','localtime'), updated_at = datetime('now','localtime') WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean delete(int missionId) {
        UserAbsenceDAO.deleteByMissionId(null, missionId);
        List<FieldMissionAttachment> atts = listAttachments(missionId);
        for (FieldMissionAttachment a : atts) {
            try {
                File f = new File(a.getFilePath());
                if (f.isFile()) {
                    f.delete();
                }
            } catch (Exception ignored) { }
        }
        for (FieldMissionAttachment a : listOrderAttachments(missionId)) {
            try {
                File f = new File(a.getFilePath());
                if (f.isFile()) {
                    f.delete();
                }
            } catch (Exception ignored) { }
        }
        for (FieldMissionAttachment a : listDetailAttachments(missionId)) {
            try {
                File f = new File(a.getFilePath());
                if (f.isFile()) {
                    f.delete();
                }
            } catch (Exception ignored) { }
        }
        String sql = "DELETE FROM field_missions WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static FieldMission findById(int id) {
        String sql = """
                SELECT fm.*, lu.username AS lead_username, cb.username AS created_by_username
                FROM field_missions fm
                LEFT JOIN users lu ON fm.lead_user_id = lu.id
                LEFT JOIN users cb ON fm.created_by = cb.id
                WHERE fm.id = ?
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapMission(rs);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Admin sees any mission; others only if lead, creator, or participant. */
    public static boolean userMayViewMission(int missionId, int userId, boolean adminSeesAll) {
        if (adminSeesAll) {
            return true;
        }
        String sql = """
                SELECT 1 FROM field_missions fm
                WHERE fm.id = ?
                  AND (fm.lead_user_id = ? OR fm.created_by = ?
                   OR EXISTS (SELECT 1 FROM field_mission_participants fmp
                              WHERE fmp.mission_id = fm.id AND fmp.user_id = ?))
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            ps.setInt(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Full edit (mission fields, ordre de mission, delete, attachments, “rapport transmis”): ADMIN or mission creator only.
     */
    public static boolean userMayManageMission(int missionId, int userId, boolean adminSeesAll) {
        if (adminSeesAll) {
            return true;
        }
        String sql = """
                SELECT 1 FROM field_missions fm
                WHERE fm.id = ?
                  AND fm.created_by = ?
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Compte rendu: administrator; or designated {@code report_author_id}; or, if still unset, mission creator or lead
     * (first save assigns the author). Simple participants cannot edit the report.
     */
    public static boolean userMayEditMissionReport(int missionId, int userId, boolean adminSeesAll) {
        if (adminSeesAll) {
            return true;
        }
        String sql = """
                SELECT report_author_id, created_by, lead_user_id
                FROM field_missions WHERE id = ?
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                int authorCol = rs.getInt("report_author_id");
                if (!rs.wasNull()) {
                    return userId == authorCol;
                }
                if (userId == rs.getInt("created_by")) {
                    return true;
                }
                int lead = rs.getInt("lead_user_id");
                return !rs.wasNull() && userId == lead;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Mark report submitted: administrator; or report author once assigned; or creator/lead while author is still unset.
     */
    public static boolean userMayMarkReportSubmitted(int missionId, int userId, boolean adminSeesAll) {
        if (adminSeesAll) {
            return true;
        }
        String sql = """
                SELECT report_author_id, created_by, lead_user_id
                FROM field_missions WHERE id = ?
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                int authorCol = rs.getInt("report_author_id");
                if (!rs.wasNull()) {
                    return userId == authorCol;
                }
                if (userId == rs.getInt("created_by")) {
                    return true;
                }
                int lead = rs.getInt("lead_user_id");
                return !rs.wasNull() && userId == lead;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * System super {@code admin} can manage any mission; direction-limited {@code ADMIN} can manage
     * missions whose creator, lead, or a participant belongs to the same {@link com.app.auth.Session#getDirectionId()}.
     */
    public static boolean missionFullAdminAccess(int missionId) {
        if (missionId <= 0) {
            return false;
        }
        if (AccessContext.isSystemSuperAdmin()) {
            return true;
        }
        if (AccessContext.isDirectionAdmin()) {
            Integer d = Session.getDirectionId();
            return d != null && d > 0 && missionTouchesDirection(missionId, d);
        }
        return false;
    }

    private static boolean missionTouchesDirection(int missionId, int directionId) {
        String sql = """
                SELECT 1 FROM field_missions fm
                WHERE fm.id = ?
                  AND (
                    EXISTS (SELECT 1 FROM users u WHERE u.id = fm.created_by AND u.direction_id = ?)
                 OR EXISTS (SELECT 1 FROM users u WHERE u.id = fm.lead_user_id AND u.direction_id = ?)
                 OR EXISTS (SELECT 1 FROM field_mission_participants fmp
                             INNER JOIN users u ON u.id = fmp.user_id
                             WHERE fmp.mission_id = fm.id AND u.direction_id = ?)
                  )
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            ps.setInt(2, directionId);
            ps.setInt(3, directionId);
            ps.setInt(4, directionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void assignReportAuthorIfUnset(Connection conn, int missionId, String reportText) {
        if (missionId <= 0 || emptyToNull(reportText) == null) {
            return;
        }
        String sql = "UPDATE field_missions SET report_author_id = ? WHERE id = ? AND report_author_id IS NULL";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Session.getUserId());
            ps.setInt(2, missionId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param systemSuper when true, no org filter. When {@code directionScopeId} is non-null, only missions
     *                      touching that direction. Otherwise the user-scoped filter (lead, creator, participant).
     */
    public static List<FieldMission> listAll(String statusFilter, String titleSearch,
            LocalDate dateFrom, LocalDate dateTo, boolean systemSuper, Integer directionScopeId, int userId) {
        List<FieldMission> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT fm.*, lu.username AS lead_username, cb.username AS created_by_username
                FROM field_missions fm
                LEFT JOIN users lu ON fm.lead_user_id = lu.id
                LEFT JOIN users cb ON fm.created_by = cb.id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (systemSuper) {
            // all missions
        } else if (directionScopeId != null && directionScopeId > 0) {
            sql.append(" AND ("
                    + "EXISTS (SELECT 1 FROM users u WHERE u.id = fm.created_by AND u.direction_id = ?) "
                    + "OR EXISTS (SELECT 1 FROM users u WHERE u.id = fm.lead_user_id AND u.direction_id = ?) "
                    + "OR EXISTS (SELECT 1 FROM field_mission_participants fmp "
                    + "INNER JOIN users u ON u.id = fmp.user_id "
                    + "WHERE fmp.mission_id = fm.id AND u.direction_id = ?))");
            params.add(directionScopeId);
            params.add(directionScopeId);
            params.add(directionScopeId);
        } else {
            sql.append(" AND (fm.lead_user_id = ? OR fm.created_by = ?"
                    + " OR EXISTS (SELECT 1 FROM field_mission_participants fmp"
                    + " WHERE fmp.mission_id = fm.id AND fmp.user_id = ?))");
            params.add(userId);
            params.add(userId);
            params.add(userId);
        }
        if (statusFilter != null && !statusFilter.isBlank() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append(" AND fm.status = ?");
            params.add(statusFilter);
        }
        if (titleSearch != null && !titleSearch.isBlank()) {
            // Oracle: COALESCE(clob, '') raises ORA-00932; cast CLOB to VARCHAR2 for LIKE.
            if (SqlDialect.isOracle()) {
                sql.append(" AND (LOWER(fm.title) LIKE ? OR LOWER(fm.mission_code) LIKE ?"
                        + " OR LOWER(COALESCE(fm.site_location,'')) LIKE ?"
                        + " OR LOWER(CAST(fm.description AS VARCHAR2(4000))) LIKE ?"
                        + " OR LOWER(CAST(fm.objectives AS VARCHAR2(4000))) LIKE ?"
                        + " OR LOWER(COALESCE(fm.order_reference,'')) LIKE ?"
                        + " OR LOWER(CAST(fm.order_body AS VARCHAR2(4000))) LIKE ?)");
            } else {
                sql.append(" AND (LOWER(fm.title) LIKE ? OR LOWER(fm.mission_code) LIKE ?"
                        + " OR LOWER(COALESCE(fm.site_location,'')) LIKE ?"
                        + " OR LOWER(COALESCE(fm.description,'')) LIKE ?"
                        + " OR LOWER(COALESCE(fm.objectives,'')) LIKE ?"
                        + " OR LOWER(COALESCE(fm.order_reference,'')) LIKE ?"
                        + " OR LOWER(COALESCE(fm.order_body,'')) LIKE ?)");
            }
            String p = "%" + titleSearch.trim().toLowerCase() + "%";
            params.add(p);
            params.add(p);
            params.add(p);
            params.add(p);
            params.add(p);
            params.add(p);
            params.add(p);
        }
        if (dateFrom != null) {
            sql.append(" AND (fm.start_date IS NULL OR fm.start_date >= ?)");
            params.add(dateFrom.toString());
        }
        if (dateTo != null) {
            sql.append(" AND (fm.end_date IS NULL OR fm.end_date <= ?)");
            params.add(dateTo.toString());
        }
        sql.append(" ORDER BY CASE WHEN fm.start_date IS NULL OR fm.start_date = '' THEN 1 ELSE 0 END, fm.start_date DESC, fm.id DESC");
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMission(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * Missions where the user is responsible (lead) or a participant — for Mon travail.
     * Does not include creator-only access.
     */
    public static List<FieldMission> listForMyWork(int userId) {
        List<FieldMission> out = new ArrayList<>();
        String sql = """
                SELECT fm.*, lu.username AS lead_username, cb.username AS created_by_username
                FROM field_missions fm
                LEFT JOIN users lu ON fm.lead_user_id = lu.id
                LEFT JOIN users cb ON fm.created_by = cb.id
                WHERE (fm.lead_user_id = ? OR EXISTS (
                    SELECT 1 FROM field_mission_participants fmp
                    WHERE fmp.mission_id = fm.id AND fmp.user_id = ?))
                ORDER BY CASE WHEN fm.start_date IS NULL OR fm.start_date = '' THEN 1 ELSE 0 END,
                         fm.start_date DESC, fm.id DESC
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapMission(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /** Comma-separated participant usernames for display. */
    public static String getParticipantNamesCsv(int missionId) {
        List<String> names = new ArrayList<>();
        String sql = """
                SELECT u.username FROM field_mission_participants fmp
                INNER JOIN users u ON u.id = fmp.user_id
                WHERE fmp.mission_id = ?
                ORDER BY u.username
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return String.join(", ", names);
    }

    private static FieldMission mapMission(ResultSet rs) throws Exception {
        FieldMission m = new FieldMission();
        m.setId(rs.getInt("id"));
        m.setMissionCode(rs.getString("mission_code"));
        m.setTitle(rs.getString("title"));
        m.setSiteLocation(rs.getString("site_location"));
        m.setStartDate(rs.getString("start_date"));
        m.setEndDate(rs.getString("end_date"));
        m.setDescription(rs.getString("description"));
        m.setObjectives(rs.getString("objectives"));
        m.setStatus(rs.getString("status"));
        m.setReportText(rs.getString("report_text"));
        m.setReportSubmittedAt(readDateTime(rs, "report_submitted_at"));
        try {
            int ra = rs.getInt("report_author_id");
            m.setReportAuthorId(rs.wasNull() ? null : ra);
        } catch (Exception ignored) {
            m.setReportAuthorId(null);
        }
        int lead = rs.getInt("lead_user_id");
        if (rs.wasNull()) {
            m.setLeadUserId(null);
        } else {
            m.setLeadUserId(lead);
        }
        m.setLeadUsername(rs.getString("lead_username"));
        m.setCreatedBy(rs.getInt("created_by"));
        m.setCreatedByUsername(rs.getString("created_by_username"));
        m.setCreatedAt(readDateTime(rs, "created_at"));
        m.setUpdatedAt(readDateTime(rs, "updated_at"));
        m.setOrderReference(rs.getString("order_reference"));
        m.setOrderIssueDate(rs.getString("order_issue_date"));
        m.setOrderIssuedBy(rs.getString("order_issued_by"));
        m.setOrderBody(rs.getString("order_body"));
        return m;
    }

    private static String readDateTime(ResultSet rs, String col) {
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

    public static void loadParticipants(FieldMission m) {
        m.getParticipantUserIds().clear();
        String sql = "SELECT user_id FROM field_mission_participants WHERE mission_id = ? ORDER BY user_id";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, m.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    m.getParticipantUserIds().add(rs.getInt("user_id"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void replaceParticipants(Connection conn, int missionId, List<Integer> userIds) throws Exception {
        try (PreparedStatement del = conn.prepareStatement(
                "DELETE FROM field_mission_participants WHERE mission_id = ?")) {
            del.setInt(1, missionId);
            del.executeUpdate();
        }
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String ins = "INSERT INTO field_mission_participants(mission_id, user_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(ins)) {
            for (Integer uid : userIds) {
                if (uid == null || uid <= 0) {
                    continue;
                }
                ps.setInt(1, missionId);
                ps.setInt(2, uid);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static List<FieldMissionAttachment> listAttachments(int missionId) {
        List<FieldMissionAttachment> list = new ArrayList<>();
        String sql = "SELECT id, mission_id, file_name, file_path FROM field_mission_attachments WHERE mission_id = ? ORDER BY id";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FieldMissionAttachment a = new FieldMissionAttachment();
                    a.setId(rs.getInt("id"));
                    a.setMissionId(rs.getInt("mission_id"));
                    a.setFileName(rs.getString("file_name"));
                    a.setFilePath(rs.getString("file_path"));
                    list.add(a);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean addAttachment(int missionId, File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        File dir = new File("uploads/missions/" + missionId);
        dir.mkdirs();
        String stored = System.currentTimeMillis() + "_" + file.getName();
        File dest = new File(dir, stored);
        try {
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String sql = """
                INSERT INTO field_mission_attachments (mission_id, file_name, file_path, uploaded_by)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            ps.setString(2, file.getName());
            ps.setString(3, dest.getPath());
            ps.setInt(4, Session.getUserId());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteAttachment(int attachmentId) {
        String path = null;
        String q = "SELECT file_path FROM field_mission_attachments WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, attachmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    path = rs.getString("file_path");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String del = "DELETE FROM field_mission_attachments WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setInt(1, attachmentId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if (path != null) {
            try {
                File f = new File(path);
                if (f.isFile()) {
                    f.delete();
                }
            } catch (Exception ignored) { }
        }
        return true;
    }

    public static List<FieldMissionAttachment> listOrderAttachments(int missionId) {
        List<FieldMissionAttachment> list = new ArrayList<>();
        String sql = "SELECT id, mission_id, file_name, file_path FROM field_mission_order_attachments WHERE mission_id = ? ORDER BY id";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FieldMissionAttachment a = new FieldMissionAttachment();
                    a.setId(rs.getInt("id"));
                    a.setMissionId(rs.getInt("mission_id"));
                    a.setFileName(rs.getString("file_name"));
                    a.setFilePath(rs.getString("file_path"));
                    list.add(a);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean addOrderAttachment(int missionId, File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        File dir = new File("uploads/missions/" + missionId + "/order");
        dir.mkdirs();
        String stored = System.currentTimeMillis() + "_" + file.getName();
        File dest = new File(dir, stored);
        try {
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String sql = """
                INSERT INTO field_mission_order_attachments (mission_id, file_name, file_path, uploaded_by)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            ps.setString(2, file.getName());
            ps.setString(3, dest.getPath());
            ps.setInt(4, Session.getUserId());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteOrderAttachment(int attachmentId) {
        String path = null;
        String q = "SELECT file_path FROM field_mission_order_attachments WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, attachmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    path = rs.getString("file_path");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String del = "DELETE FROM field_mission_order_attachments WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setInt(1, attachmentId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if (path != null) {
            try {
                File f = new File(path);
                if (f.isFile()) {
                    f.delete();
                }
            } catch (Exception ignored) { }
        }
        return true;
    }

    public static List<FieldMissionAttachment> listDetailAttachments(int missionId) {
        List<FieldMissionAttachment> list = new ArrayList<>();
        String sql = "SELECT id, mission_id, file_name, file_path FROM field_mission_detail_attachments WHERE mission_id = ? ORDER BY id";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FieldMissionAttachment a = new FieldMissionAttachment();
                    a.setId(rs.getInt("id"));
                    a.setMissionId(rs.getInt("mission_id"));
                    a.setFileName(rs.getString("file_name"));
                    a.setFilePath(rs.getString("file_path"));
                    list.add(a);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean addDetailAttachment(int missionId, File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        File dir = new File("uploads/missions/" + missionId + "/detail");
        dir.mkdirs();
        String stored = System.currentTimeMillis() + "_" + file.getName();
        File dest = new File(dir, stored);
        try {
            Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String sql = """
                INSERT INTO field_mission_detail_attachments (mission_id, file_name, file_path, uploaded_by)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, missionId);
            ps.setString(2, file.getName());
            ps.setString(3, dest.getPath());
            ps.setInt(4, Session.getUserId());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteDetailAttachment(int attachmentId) {
        String path = null;
        String q = "SELECT file_path FROM field_mission_detail_attachments WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, attachmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    path = rs.getString("file_path");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String del = "DELETE FROM field_mission_detail_attachments WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(del)) {
            ps.setInt(1, attachmentId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        if (path != null) {
            try {
                File f = new File(path);
                if (f.isFile()) {
                    f.delete();
                }
            } catch (Exception ignored) { }
        }
        return true;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
