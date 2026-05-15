package com.app.dao;

import com.app.auth.Session;
import com.app.model.TicketCloseRequest;
import com.app.util.DB;
import com.app.util.DbConfig;
import com.app.util.TicketUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

public class TicketCloseRequestDAO {

    private static Integer getUserDirectionId(Connection c, int userId) throws Exception {
        String sql = "SELECT direction_id FROM users WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int directionId = rs.getInt("direction_id");
                return rs.wasNull() ? null : directionId;
            }
            return null;
        }
    }

    public static int createRequest(int ticketId, int requestedTo, String reason) {
        String checkSql = """
            SELECT id
            FROM ticket_close_requests
            WHERE ticket_id = ?
              AND requested_to = ?
              AND status = 'PENDING'
            LIMIT 1
        """;
        String insertSql = """
            INSERT INTO ticket_close_requests(ticket_id, requested_by, requested_to, reason, status)
            VALUES (?, ?, ?, ?, 'PENDING')
        """;

        try (Connection c = DB.getConnection()) {
            String requesterRole = Session.getRole() == null ? "" : Session.getRole().trim().toUpperCase();
            if (!"ADMIN".equals(requesterRole)) {
                Integer requesterDirectionId = getUserDirectionId(c, Session.getUserId());
                Integer approverDirectionId = getUserDirectionId(c, requestedTo);
                if (requesterDirectionId == null || approverDirectionId == null
                        || !requesterDirectionId.equals(approverDirectionId)) {
                    throw new RuntimeException("You can request close only to a user in your direction.");
                }
            }

            try (PreparedStatement check = c.prepareStatement(checkSql)) {
                check.setInt(1, ticketId);
                check.setInt(2, requestedTo);
                ResultSet rs = check.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }

            try (PreparedStatement ps = DbConfig.isOracle()
                    ? c.prepareStatement(insertSql, new String[] { "ID" })
                    : c.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, ticketId);
                ps.setInt(2, Session.getUserId());
                ps.setInt(3, requestedTo);
                ps.setString(4, reason);
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    Number generatedId = (Number) keys.getObject(1);
                    if (generatedId == null) {
                        throw new RuntimeException("Unable to create close request: no numeric ID returned.");
                    }
                    return generatedId.intValue();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to create close request: " + e.getMessage(), e);
        }

        throw new RuntimeException("Unable to create close request.");
    }

    public static boolean hasPendingRequestForApprover(int ticketId, int approverId) {
        String sql = """
            SELECT 1
            FROM ticket_close_requests
            WHERE ticket_id = ?
              AND requested_to = ?
              AND status = 'PENDING'
            LIMIT 1
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, approverId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    public static ObservableList<TicketCloseRequest> getPendingForApprover(int approverId) {
        ObservableList<TicketCloseRequest> list = FXCollections.observableArrayList();
        String sql = """
            SELECT r.id,
                   r.ticket_id,
                   r.requested_by,
                   r.requested_to,
                   rb.username AS requested_by_name,
                   rt.username AS requested_to_name,
                   r.reason,
                   r.status,
                   r.requested_at
            FROM ticket_close_requests r
            LEFT JOIN users rb ON rb.id = r.requested_by
            LEFT JOIN users rt ON rt.id = r.requested_to
            WHERE r.requested_to = ?
              AND r.status = 'PENDING'
            ORDER BY r.requested_at DESC
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, approverId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TicketCloseRequest r = new TicketCloseRequest();
                r.setId(rs.getInt("id"));
                r.setTicketId(rs.getInt("ticket_id"));
                r.setTicketRef(TicketUtil.formatTicketRef(rs.getInt("ticket_id")));
                r.setRequestedBy(rs.getInt("requested_by"));
                r.setRequestedTo(rs.getInt("requested_to"));
                r.setRequestedByName(rs.getString("requested_by_name"));
                r.setRequestedToName(rs.getString("requested_to_name"));
                r.setReason(rs.getString("reason"));
                r.setStatus(rs.getString("status"));
                r.setRequestedAt(rs.getString("requested_at"));
                list.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void approveAndClose(int requestId, String note) {
        String getSql = """
            SELECT ticket_id, requested_to, status
            FROM ticket_close_requests
            WHERE id = ?
        """;
        String updateSql = """
            UPDATE ticket_close_requests
            SET status = 'APPROVED',
                decided_by = ?,
                decided_at = datetime('now','localtime'),
                decision_note = ?
            WHERE id = ?
        """;
        int ticketId;
        int requestedTo;
        String status;

        // Read request in a short-lived connection to avoid long locks.
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(getSql)) {
            ps.setInt(1, requestId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Close request not found.");
            }
            ticketId = rs.getInt("ticket_id");
            requestedTo = rs.getInt("requested_to");
            status = rs.getString("status");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new RuntimeException("Close request already processed.");
        }
        if (requestedTo != Session.getUserId()) {
            throw new RuntimeException("You are not allowed to approve this close request.");
        }

        // Close ticket first; if this fails, request stays pending.
        TicketDAO.closeTicket(ticketId);

        // Then mark request approved in a new short-lived connection.
        try (Connection c = DB.getConnection();
             PreparedStatement up = c.prepareStatement(updateSql)) {
            up.setInt(1, Session.getUserId());
            up.setString(2, note);
            up.setInt(3, requestId);
            up.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static void reject(int requestId, String note) {
        String sql = """
            UPDATE ticket_close_requests
            SET status = 'REJECTED',
                decided_by = ?,
                decided_at = datetime('now','localtime'),
                decision_note = ?
            WHERE id = ?
              AND requested_to = ?
              AND status = 'PENDING'
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Session.getUserId());
            ps.setString(2, note);
            ps.setInt(3, requestId);
            ps.setInt(4, Session.getUserId());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Unable to reject close request.");
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static ObservableList<com.app.model.User> getCloseRequestCandidatesForCurrentUser() {
        String role = Session.getRole();
        List<String> roles = new java.util.ArrayList<>(com.app.util.RoleFlowUtil.getHigherRolesForCloseRequest(role));
        if (roles.isEmpty()) {
            return FXCollections.observableArrayList();
        }
        return UserDAO.findActiveUsersByRoles(roles);
    }
}
