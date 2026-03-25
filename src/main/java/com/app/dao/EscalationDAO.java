package com.app.dao;

import com.app.model.Escalation;
import com.app.util.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class EscalationDAO {

    // =========================================
    // GET ESCALATIONS BY TASK
    // =========================================
    public static List<Escalation> getByTask(int taskId) {

    List<Escalation> list = new ArrayList<>();

    String sql = """
        SELECT e.escalated_at,
               u1.username as from_name,
               u2.username as to_name
        FROM ticket_escalations e
        JOIN users u1 ON e.escalated_by = u1.id
        JOIN users u2 ON e.escalated_to = u2.id
        WHERE e.ticket_id = (
            SELECT ticket_id FROM ticket_tasks WHERE id=?
        )
        ORDER BY e.escalated_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Escalation e = new Escalation();

            e.setFromUserName(rs.getString("from_name"));
            e.setToUserName(rs.getString("to_name"));
            e.setCreatedAt(rs.getString("escalated_at")); // ✅ FIX

            list.add(e);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
    
    
    
}