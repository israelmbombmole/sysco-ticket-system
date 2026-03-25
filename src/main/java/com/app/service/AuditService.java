package com.app.service;

import com.app.util.DB;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class AuditService {

    public static void log(String username,
                           String action,
                           String entity,
                           Integer entityId,
                           String details) {

        String sql = """
            INSERT INTO system_audit
            (username, action, entity, entity_id, details)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, action);
            ps.setString(3, entity);
            if (entityId != null) {
                ps.setInt(4, entityId);
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, details);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}