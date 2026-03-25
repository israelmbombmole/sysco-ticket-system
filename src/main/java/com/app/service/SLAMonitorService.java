package com.app.service;

import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.Ticket;
import com.app.util.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class SLAMonitorService {

    // =========================================
    // CHECK ALL ACTIVE TICKETS
    // =========================================
    public static void checkAllTickets() {

        List<Ticket> tickets = TicketDAO.getActiveTicketsForSLA();

        for (Ticket ticket : tickets) {

            int ticketId = ticket.getId();

            double percentUsed = TicketDAO.getSlaUsagePercent(ticketId);

            // =========================================
            // SLA BREACHED
            // =========================================
            if (percentUsed >= 100) {

                if (!TicketDAO.hasSlaEvent(ticketId, "SLA_BREACHED")) {

                    TicketDAO.logSystemEvent(
                            ticketId,
                            "SLA_BREACHED",
                            "SLA deadline exceeded."
                    );

                    escalateTicket(ticketId);
                }
            }

            // =========================================
            // SLA WARNING
            // =========================================
            else if (percentUsed >= 70) {

                if (!TicketDAO.hasSlaEvent(ticketId, "SLA_WARNING")) {

                    TicketDAO.logSystemEvent(
                            ticketId,
                            "SLA_WARNING",
                            "Ticket approaching SLA breach (" + (int) percentUsed + "% used)."
                    );
                }
            }

            // =========================================
            // LEVEL 2 ESCALATION (VERY LATE)
            // =========================================
            if (percentUsed >= 150) {

                Integer departmentId = TicketDAO.getDepartmentId(ticketId);

                if (departmentId == null) return;

                Integer level2 = TicketDAO.getEscalationLevel(departmentId, 2);

                if (level2 != null) {

                    TicketDAO.updatePriority(ticketId, "CRITICAL");
                    TicketDAO.forceReassign(ticketId, level2);

                    TicketDAO.logSystemEvent(
                            ticketId,
                            "ESCALATED_L2",
                            "Escalated to Level 2 authority due to severe SLA breach."
                    );
                }
            }
        }
    }

    // =========================================
    // ESCALATE TO LEVEL 1 SUPERVISOR
    // =========================================
    private static void escalateTicket(int ticketId) {

    Integer departmentId = TicketDAO.getDepartmentId(ticketId);

    if (departmentId == null) return;

    Integer level1 = TicketDAO.getEscalationLevel(departmentId, 1);

    if (level1 == null || level1 <= 0) {
        System.out.println("No valid escalation user for department " + departmentId);
        return;
    }

    // Validate user exists
    if (UserDAO.findById(level1) == null) {
        System.out.println("Escalation user not found: " + level1);
        return;
    }

    TicketDAO.updatePriority(ticketId, "HIGH");

    TicketDAO.forceReassign(ticketId, level1);

    TicketDAO.logSystemEvent(
            ticketId,
            "ESCALATED_L1",
            "Escalated to Level 1 supervisor."
    );
}

    // =========================================
    // COUNT SLA BREACHES PER DEPARTMENT
    // =========================================
    public static int countSlaBreaches(int departmentId) {

        String sql = """
            SELECT COUNT(*)
            FROM tickets
            WHERE sla_breached = 1
            AND department_id = ?
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, departmentId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) return rs.getInt(1);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    // =========================================
    // AVERAGE RESOLUTION TIME
    // =========================================
    public static double averageResolution(int departmentId) {

        String sql = """
            SELECT AVG(resolution_minutes)
            FROM tickets
            WHERE department_id=?
            AND status='CLOSED'
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, departmentId);

            ResultSet rs = ps.executeQuery();

            return rs.next() ? rs.getDouble(1) : 0;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // =========================================
    // BEST AGENT IN DEPARTMENT
    // =========================================
    public static String getTopAgent(int departmentId) {

        String sql = """
            SELECT u.username, COUNT(*) AS closed_count
            FROM tickets t
            JOIN users u ON t.assigned_to = u.id
            WHERE t.department_id=?
            AND t.status='CLOSED'
            GROUP BY u.username
            ORDER BY closed_count DESC
            LIMIT 1
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, departmentId);

            ResultSet rs = ps.executeQuery();

            return rs.next() ? rs.getString("username") : "N/A";

        } catch (Exception e) {
            e.printStackTrace();
            return "N/A";
        }
    }

    // =========================================
    // SLA COMPLIANCE RATE
    // =========================================
    public static double slaComplianceRate(int departmentId) {

        String sql = """
            SELECT
                COUNT(CASE WHEN sla_breached = 0 AND status='CLOSED' THEN 1 END) * 100.0
                /
                COUNT(CASE WHEN status='CLOSED' THEN 1 END)
            FROM tickets
            WHERE department_id = ?
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, departmentId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) return rs.getDouble(1);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    // =========================================
    // AUTO MARK SLA BREACHED
    // =========================================
    public static void autoMarkBreached() {

        String sql = """
            UPDATE tickets
            SET sla_breached = 1
            WHERE status != 'CLOSED'
            AND CURRENT_TIMESTAMP > sla_deadline
        """;

        try (Connection c = DB.getConnection();
             Statement st = c.createStatement()) {

            st.executeUpdate(sql);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}