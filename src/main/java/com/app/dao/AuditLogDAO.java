package com.app.dao;

import com.app.auth.Session;
import com.app.model.AuditLog;
import com.app.util.DB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AuditLogDAO {

    private static boolean shouldRestrictByDirection() {
        String role = Session.getRole();
        return role != null && !"ADMIN".equalsIgnoreCase(role);
    }

    private static Integer getCurrentUserDirectionId() {
        String sql = "SELECT direction_id FROM users WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Session.getUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int directionId = rs.getInt("direction_id");
                return rs.wasNull() ? null : directionId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ==========================
    // FIND ALL
    // ==========================
    public static ObservableList<AuditLog> findAll() {

        ObservableList<AuditLog> list = FXCollections.observableArrayList();
        boolean restrictByDirection = shouldRestrictByDirection();
        Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
        if (restrictByDirection && (directionId == null || directionId <= 0)) {
            return list;
        }

        String sql = """
                SELECT *
                FROM system_audit
                """;

        if (restrictByDirection) {
            sql += """
                WHERE EXISTS (
                    SELECT 1
                    FROM users u
                    WHERE u.username = system_audit.username
                      AND u.direction_id = ?
                )
            """;
        }
        sql += " ORDER BY created_at DESC";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            if (restrictByDirection) {
                ps.setInt(1, directionId);
            }
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapAudit(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ==========================
    // ADVANCED SEARCH
    // ==========================
    public static ObservableList<AuditLog> advancedSearch(
            String user,
            LocalDate from,
            LocalDate to,
            String keyword) {

        ObservableList<AuditLog> list = FXCollections.observableArrayList();
        boolean restrictByDirection = shouldRestrictByDirection();
        Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
        if (restrictByDirection && (directionId == null || directionId <= 0)) {
            return list;
        }

        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM system_audit
                WHERE 1=1
                """);

        List<Object> params = new ArrayList<>();

        if (restrictByDirection) {
            sql.append("""
                 AND EXISTS (
                    SELECT 1
                    FROM users u
                    WHERE u.username = system_audit.username
                      AND u.direction_id = ?
                 )
            """);
            params.add(directionId);
        }

        if (user != null && !"All".equals(user)) {
            sql.append(" AND username = ?");
            params.add(user);
        }


        if (from != null) {
            sql.append(" AND date(created_at) >= date(?)");
            params.add(from.toString());
        }

        if (to != null) {
            sql.append(" AND date(created_at) <= date(?)");
            params.add(to.toString());
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND lower(details) LIKE ?");
            params.add("%" + keyword.toLowerCase() + "%");
        }

        sql.append(" ORDER BY created_at DESC");

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapAudit(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ==========================
    // DISTINCT USERS
    // ==========================
    public static List<String> getDistinctUsers() {
        return getDistinctColumn("username");
    }

    public static List<String> getDistinctActions() {
        return getDistinctColumn("action");
    }

    public static List<String> getDistinctEntities() {
        return getDistinctColumn("entity");
    }

    private static List<String> getDistinctColumn(String column) {

        List<String> list = new ArrayList<>();
        boolean restrictByDirection = shouldRestrictByDirection();
        Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
        if (restrictByDirection && (directionId == null || directionId <= 0)) {
            return list;
        }

        String sql = "SELECT DISTINCT " + column + " FROM system_audit";
        if (restrictByDirection) {
            sql += """
                WHERE EXISTS (
                    SELECT 1
                    FROM users u
                    WHERE u.username = system_audit.username
                      AND u.direction_id = ?
                )
            """;
        }
        sql += " ORDER BY " + column;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            if (restrictByDirection) {
                ps.setInt(1, directionId);
            }
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(rs.getString(column));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ==========================
    // MAP RESULTSET
    // ==========================
    private static AuditLog mapAudit(ResultSet rs) throws SQLException {

        AuditLog audit = new AuditLog();

        audit.setUsername(rs.getString("username"));
        audit.setAction(rs.getString("action"));
        audit.setEntity(rs.getString("entity"));
        audit.setEntityId(rs.getObject("entity_id") != null ?
                rs.getInt("entity_id") : null);
        audit.setDetails(rs.getString("details"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            audit.setCreatedAt(ts.toLocalDateTime());
        }

        return audit;
    }
}