package com.app.dao;

import com.app.model.Ticket;
import com.app.util.DB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CourierTrackingDAO {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm");

    public static ObservableList<Ticket> getTicketsCreatedByCourier(int courierUserId) {
        ObservableList<Ticket> list = FXCollections.observableArrayList();

        String sql = """
            SELECT
                t.id,
                t.title,
                t.status,
                t.ticket_type,
                t.created_at,
                t.routing_stage,
                d.name AS department_name,
                owner.username AS owner_name,
                owner.role AS owner_role
            FROM tickets t
            LEFT JOIN departments d
                ON t.department_id = d.id
            LEFT JOIN ticket_assignments ta
                ON ta.ticket_id = t.id
               AND ta.id = (
                    SELECT id
                    FROM ticket_assignments ta2
                    WHERE ta2.ticket_id = t.id
                    ORDER BY ta2.id DESC
                    LIMIT 1
               )
            LEFT JOIN users owner
                ON owner.id = ta.assigned_to
            WHERE t.created_by = ?
            ORDER BY t.created_at DESC
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, courierUserId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Ticket t = new Ticket();
                t.setId(rs.getInt("id"));
                t.setTitle(rs.getString("title"));
                t.setStatus(rs.getString("status"));
                t.setTicketType(rs.getString("ticket_type"));
                t.setRoutingStage(rs.getString("routing_stage"));
                t.setDepartmentName(rs.getString("department_name"));
                String ownerName = rs.getString("owner_name");
                t.setAssignedToName(ownerName == null ? "UNASSIGNED" : ownerName);
                t.setCurrentOwnerRole(rs.getString("owner_role"));

                Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    t.setCreatedAt(createdAt.toLocalDateTime().format(DATE_FORMAT));
                    t.setSmartAging(formatAging(createdAt.toLocalDateTime(), LocalDateTime.now()));
                }

                list.add(t);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static int countByStatus(int courierUserId, String status) {
        String sql = """
            SELECT COUNT(*)
            FROM tickets
            WHERE created_by = ?
              AND status = ?
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, courierUserId);
            ps.setString(2, status);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static int countEscalationRisk(int courierUserId) {
        String sql = """
            SELECT COUNT(*)
            FROM tickets
            WHERE created_by = ?
              AND status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS')
              AND created_at IS NOT NULL
              AND ((strftime('%s','now') - strftime('%s', created_at)) / 3600) >= 24
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, courierUserId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static int countStuckTickets(int courierUserId) {
        String sql = """
            SELECT COUNT(*)
            FROM tickets
            WHERE created_by = ?
              AND status NOT IN ('CLOSED', 'MERGED')
              AND created_at IS NOT NULL
              AND ((strftime('%s','now') - strftime('%s', created_at)) / 3600) >= 12
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, courierUserId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String computeFlowHealth(int courierUserId) {
        int total = getTicketsCreatedByCourier(courierUserId).size();
        if (total == 0) {
            return "100%";
        }
        int risk = countEscalationRisk(courierUserId);
        int stuck = countStuckTickets(courierUserId);
        int healthy = Math.max(0, total - risk - stuck);
        int percent = (int) Math.round((healthy * 100.0) / total);
        return percent + "%";
    }

    public static double computeAverageAgingHours(int courierUserId) {
        String sql = """
            SELECT AVG((strftime('%s','now') - strftime('%s', created_at)) / 3600.0)
            FROM tickets
            WHERE created_by = ?
              AND created_at IS NOT NULL
              AND status NOT IN ('CLOSED', 'MERGED')
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, courierUserId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double avg = rs.getDouble(1);
                if (!rs.wasNull()) {
                    return Math.max(0, avg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    private static String formatAging(LocalDateTime start, LocalDateTime end) {
        Duration duration = Duration.between(start, end);
        long totalHours = Math.max(0, duration.toHours());
        long days = totalHours / 24;
        long hours = totalHours % 24;
        return days + "d " + hours + "h";
    }
}
