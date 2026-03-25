package com.app.dao;

import com.app.auth.Session;
import static com.app.dao.TicketDAO.logEvent;
import com.app.model.AgentStats;
import com.app.model.TicketAssignment;
import com.app.util.DB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class TicketAssignmentDAO {

    // =========================
    // Assign Ticket
    // =========================
    public static void assignTicket(int ticketId, int agentId) {

    String insertAssignment = """
        INSERT INTO ticket_assignments
        (ticket_id, agent_id, status, active)
        VALUES (?, ?, 'ASSIGNED', 1)
    """;

    String updateTicket = """
        UPDATE tickets
        SET assigned_to = ?,
            status = CASE 
                WHEN status = 'OPEN' THEN 'ASSIGNED'
                ELSE status
            END
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        try (PreparedStatement ps = c.prepareStatement(insertAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, agentId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps2 = c.prepareStatement(updateTicket)) {
            ps2.setInt(1, agentId);
            ps2.setInt(2, ticketId);
            ps2.executeUpdate();
        }

        c.commit();

        System.out.println("✅ Ticket assigned successfully.");

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    // =========================
    // Find All Assignments (ADMIN)
    // =========================
    public static ObservableList<TicketAssignment> findAll() {

        ObservableList<TicketAssignment> list =
                FXCollections.observableArrayList();

        String sql = """
    SELECT ta.*, u.username
    FROM ticket_assignments ta
    JOIN users u ON ta.agent_id = u.id
    WHERE ta.active = 1
""";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                list.add(new TicketAssignment(
                        rs.getInt("id"),
                        rs.getInt("ticket_id"),
                        rs.getInt("agent_id"),
                        rs.getString("status"),
                        rs.getTimestamp("started_at") == null ? null :
                                rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getTimestamp("closed_at") == null ? null :
                                rs.getTimestamp("closed_at").toLocalDateTime(),
                        rs.getObject("duration_minutes") == null ? null :
                                rs.getInt("duration_minutes"),
                        rs.getString("username")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // =========================
    // Start Work
    // =========================
    public static void startWork(int ticketId) {

    String updateAssignment = """
        UPDATE ticket_assignments
        SET status = 'IN_PROGRESS',
            started_at = CURRENT_TIMESTAMP
        WHERE ticket_id = ?
          AND agent_id = ?
          AND active = 1
    """;

    String updateTicket = """
        UPDATE tickets
        SET status = 'IN_PROGRESS',
            started_at = CURRENT_TIMESTAMP
        WHERE id = ?
    """;

    try (Connection conn = DB.getConnection()) {

        conn.setAutoCommit(false);

        int agentId = Session.getUserId();   // 🔥 ALWAYS use session

        try (PreparedStatement ps = conn.prepareStatement(updateAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, agentId);

            int rows = ps.executeUpdate();

            if (rows == 0) {
                throw new RuntimeException("Ticket not assigned to you.");
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(updateTicket)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        logEvent(ticketId, "STARTED",
                "Work started by " + Session.getUsername());

        conn.commit();

        System.out.println("Work started successfully.");

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage());
    }
}



    // =========================
    // Close Ticket
    // =========================
    public static void close(int ticketId, int userId) {

    String sql = """
        UPDATE ticket_assignments
        SET status = 'CLOSED',
            closed_at = CURRENT_TIMESTAMP,
            duration_minutes =
                CAST((julianday(CURRENT_TIMESTAMP) - julianday(started_at)) * 24 * 60 AS INTEGER)
        WHERE ticket_id = ?
          AND agent_id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setInt(2, userId);

        int rows = ps.executeUpdate();
        System.out.println("Close rows updated: " + rows);

    } catch (Exception e) {
        e.printStackTrace();
    }
}



    // =========================
    // Agent Performance Stats
    // =========================
    public static ObservableList<AgentStats> getAgentStats() {

        ObservableList<AgentStats> list =
                FXCollections.observableArrayList();

        String sql = """
            SELECT u.username,
                   COUNT(*) AS total,
                   COALESCE(SUM(duration_minutes),0) AS total_minutes
            FROM ticket_assignments ta
            JOIN users u ON ta.agent_id = u.id
            WHERE ta.status = 'CLOSED'
            GROUP BY u.username
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                list.add(new AgentStats(
                        rs.getString("username"),
                        rs.getInt("total"),
                        rs.getInt("total_minutes")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}
