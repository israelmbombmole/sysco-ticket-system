package com.app.dao;

import com.app.auth.Session;
import com.app.controller.AdminDashboardController.AgentRow;
import com.app.model.Department;
import com.app.model.Ticket;
import com.app.model.TicketEvent;
import com.app.model.TicketTask;
import com.app.model.User;
import com.app.service.EmailService;
import com.app.service.ExcelService;
import static com.app.service.SLAMonitorService.countSlaBreaches;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.*;
import com.app.util.DB;
import com.app.util.TicketUtil;
import com.sun.source.util.TaskEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TreeItem;

public class TicketDAO {

    private static String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }

    private static String monitoringRoutingStageForRole(String role) {
        role = normalizeRole(role);
        return switch (role) {
            case "COURRIER" -> "CREATED";
            case "SECRETAIRE" -> "TO_SECRETAIRE";
            case "SOUS-DIRECTEUR" -> "TO_SOUS_DIRECTEUR";
            default -> null;
        };
    }

    private static String buildCotationValue(int ticketId) {
        return "COT-" + String.format("%05d", ticketId);
    }

    private static void persistExcelSnapshotIfFinalized(Connection c, int ticketId) {
        String sql = """
            SELECT
                t.id,
                t.date_enregistrement,
                t.expediteur,
                t.objet,
                t.cotation,
                t.date_cotation,
                sd.name AS sous_direction_name
            FROM tickets t
            LEFT JOIN sous_directions sd ON sd.id = t.sous_direction_id
            WHERE t.id = ?
        """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return;
            }

            String dateEnreg = rs.getString("date_enregistrement");
            String expediteur = rs.getString("expediteur");
            String objet = rs.getString("objet");
            String cotation = rs.getString("cotation");
            String dateCotation = rs.getString("date_cotation");
            String sousDirection = rs.getString("sous_direction_name");

            if (dateEnreg == null || dateEnreg.isBlank()
                    || expediteur == null || expediteur.isBlank()
                    || objet == null || objet.isBlank()
                    || cotation == null || cotation.isBlank()
                    || dateCotation == null || dateCotation.isBlank()
                    || sousDirection == null || sousDirection.isBlank()) {
                return;
            }

            ExcelService.append(dateEnreg, expediteur, objet, cotation, dateCotation, sousDirection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean hasDateCotation(Connection c, int ticketId) {
        String sql = "SELECT date_cotation FROM tickets WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String val = rs.getString("date_cotation");
                return val != null && !val.isBlank();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static Integer resolveTicketDirectionId(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        if (ticket.getDepartmentId() != null) {
            return ticket.getDepartmentId();
        }
        return DirectionDAO.findDirectionIdByDepartmentName(ticket.getDepartmentName());
    }

    // =====================================================
    // GET TICKETS FOR AGENT
    // =====================================================

   public static List<Ticket> getTicketsForUser(int userId) {

    List<Ticket> list = new ArrayList<>();

    String sql = """
        SELECT DISTINCT
            t.id,
            t.title,
            t.status,
            t.priority,
            t.department_id,
            d.name AS department_name,
            ta.assigned_to,
            u.username AS assigned_to_name
        FROM tickets t
        LEFT JOIN departments d ON d.id = t.department_id
        LEFT JOIN ticket_assignments ta
               ON ta.ticket_id = t.id
              AND ta.id = (
                    SELECT id
                    FROM ticket_assignments ta2
                    WHERE ta2.ticket_id = t.id
                    ORDER BY ta2.id DESC
                    LIMIT 1
                )
        LEFT JOIN users u ON u.id = ta.assigned_to
        WHERE t.merged_into IS NULL
          AND (
                t.assigned_to = ?
                OR (ta.assigned_to = ? AND ta.active = 1)
              )
        ORDER BY t.updated_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.setInt(2, userId);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Ticket t = new Ticket();

            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setPriority(rs.getString("priority"));
            t.setDepartmentName(rs.getString("department_name"));
            if (rs.getObject("department_id") != null) {
                t.setDepartmentId(rs.getInt("department_id"));
            }
            t.setAssignedToName(rs.getString("assigned_to_name"));
            if (rs.getObject("assigned_to") != null) {
                t.setAssignedTo(rs.getInt("assigned_to"));
            }

            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
 
    // =====================================================
    // CREATE TICKET
    // =====================================================

    public static int createExternalTicket(String title,
                                       String description,
                                       String priority,
                                       int departmentId,
                                       int createdBy) {

    String sql = """
        INSERT INTO tickets (
            title,
            description,
            priority,
            status,
            ticket_type,
            department_id,
            created_by,
            created_at
        )
        VALUES (?, ?, ?, 'OPEN', 'EXTERNAL', ?, ?, datetime('now','localtime'))
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, priority);
        ps.setInt(4, departmentId);
        ps.setInt(5, createdBy);

        int rows = ps.executeUpdate();

        if (rows == 0) {
            throw new RuntimeException("Creating external ticket failed.");
        }

        ResultSet rs = ps.getGeneratedKeys();

if (rs.next()) {

    int ticketId = rs.getInt(1);

   logEvent(conn, ticketId, "CREATED",
    "EXTERNAL ticket created by " + Session.getUsername());

TicketHistoryDAO.log(
        conn,
        ticketId,
        "CREATED",
        "Ticket created by " + Session.getUsername()
);

    return ticketId;
}

    } catch (Exception e) {
        e.printStackTrace();
    }

    return -1;
}

    // =====================================================
    // ASSIGN TICKET
    // =====================================================

   public static void assignTicket(int ticketId, int assignedToUserId) {

    User targetUser = UserDAO.findById(assignedToUserId);

    if (targetUser == null) {
        throw new RuntimeException("Assigned user not found.");
    }

    String fromRole = Session.getRole();
    String toRole = targetUser.getRole();

    if (!com.app.util.RoleFlowUtil.canAssign(fromRole, toRole)) {
        throw new RuntimeException("Assignment not allowed: " + fromRole + " → " + toRole);
    }

    String updateTicket = """
        UPDATE tickets
        SET assigned_to = ?, 
            status = 'ASSIGNED',
            updated_by = ?, 
            updated_at = datetime('now','localtime'),
            routing_stage = ?,
            sous_direction_id = CASE
                WHEN ? = 'SECRETAIRE' AND ? = 'SOUS-DIRECTEUR'
                    THEN COALESCE((SELECT sous_direction_id FROM users WHERE id = ?), sous_direction_id)
                ELSE sous_direction_id
            END,
            cotation = CASE
                WHEN ? = 'SOUS-DIRECTEUR' THEN COALESCE(cotation, ?)
                ELSE cotation
            END,
            date_cotation = CASE
                WHEN ? = 'SOUS-DIRECTEUR' THEN COALESCE(date_cotation, date('now','localtime'))
                ELSE date_cotation
            END,
            cotation_assigned_at = CASE
                WHEN ? = 'SOUS-DIRECTEUR' THEN COALESCE(cotation_assigned_at, datetime('now','localtime'))
                ELSE cotation_assigned_at
            END,
            recorded_year = CASE
                WHEN ? = 'SOUS-DIRECTEUR' THEN COALESCE(
                    CAST(substr(date_enregistrement, 1, 4) AS INTEGER),
                    CAST(strftime('%Y', 'now', 'localtime') AS INTEGER)
                )
                ELSE recorded_year
            END,
            recorded_at = CASE
                WHEN ? = 'SOUS-DIRECTEUR' THEN COALESCE(recorded_at, datetime('now','localtime'))
                ELSE recorded_at
            END
        WHERE id = ?
    """;

    String deactivateOldAssignments = """
        UPDATE ticket_assignments
        SET active = 0
        WHERE ticket_id = ?
          AND active = 1
    """;

    String insertAssignment = """
        INSERT INTO ticket_assignments(
            ticket_id,
            agent_id,
            assigned_to,
            assigned_by,
            assigned_at,
            status,
            active,
            from_role,
            to_role,
            assignment_type
        )
        VALUES (?, ?, ?, ?, datetime('now','localtime'), 'ASSIGNED', 1, ?, ?, ?)
    """;

    String ref = TicketUtil.formatTicketRef(ticketId);

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);
        String fromRoleUpper = normalizeRole(fromRole);
        String toRoleUpper = normalizeRole(toRole);
        boolean shouldPersistAfterAssignment = "SOUS-DIRECTEUR".equals(fromRoleUpper)
                && !hasDateCotation(c, ticketId);

        // 1. deactivate old assignments
        try (PreparedStatement ps = c.prepareStatement(deactivateOldAssignments)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        // 2. update ticket
        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            String nextRoutingStage = switch (fromRoleUpper + "->" + toRoleUpper) {
                case "COURRIER->SECRETAIRE" -> "TO_SECRETAIRE";
                case "SECRETAIRE->SOUS-DIRECTEUR" -> "TO_SOUS_DIRECTEUR";
                default -> "ASSIGNED_TO_AGENT";
            };

            ps.setInt(1, assignedToUserId);
            ps.setInt(2, Session.getUserId());
            ps.setString(3, nextRoutingStage);
            ps.setString(4, fromRoleUpper);
            ps.setString(5, toRoleUpper);
            ps.setInt(6, assignedToUserId);
            ps.setString(7, fromRoleUpper);
            ps.setString(8, buildCotationValue(ticketId));
            ps.setString(9, fromRoleUpper);
            ps.setString(10, fromRoleUpper);
            ps.setString(11, fromRoleUpper);
            ps.setString(12, fromRoleUpper);
            ps.setInt(13, ticketId);
            ps.executeUpdate();
        }

        // 3. insert new assignment
        try (PreparedStatement ps = c.prepareStatement(insertAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, assignedToUserId);
            ps.setInt(3, assignedToUserId);
            ps.setInt(4, Session.getUserId());
            ps.setString(5, fromRole);
            ps.setString(6, toRole);
            ps.setString(7, com.app.util.RoleFlowUtil.getAssignmentType(fromRole, toRole, false));
            ps.executeUpdate();
        }

        logEvent(c, ticketId, "ASSIGNED",
                "Ticket assigned to " + targetUser.getUsername() + " (" + toRole + ")");

        TicketHistoryDAO.log(
                c,
                ticketId,
                "ASSIGNED",
                "Assigned by " + Session.getUsername() + " to " + targetUser.getUsername()
        );

        if (shouldPersistAfterAssignment) {
            persistExcelSnapshotIfFinalized(c, ticketId);
        }

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException("Assignment failed: " + e.getMessage());
    }

    NotificationDAO.create(
            assignedToUserId,
            "Ticket Assigned",
            "You have been assigned ticket " + ref,
            "TICKET_ASSIGNED"
    );
}
   
   public static void assignTicketToMultipleUsers(int ticketId,
                                               List<Integer> assignedUserIds,
                                               String taskTitle,
                                               String taskDescription) {

    if (assignedUserIds == null || assignedUserIds.isEmpty()) {
        throw new RuntimeException("No users selected for multi-assignment.");
    }

    String fromRole = Session.getRole();

    String deactivate = """
        UPDATE ticket_assignments
        SET active = 0
        WHERE ticket_id = ?
    """;

    String updateTicket = """
        UPDATE tickets
        SET status = 'WAITING_ON_TASKS',
            updated_by = ?,
            updated_at = datetime('now','localtime')
        WHERE id = ?
    """;

    String insertAssignment = """
        INSERT INTO ticket_assignments(
            ticket_id,
            agent_id,
            assigned_to,
            assigned_by,
            assigned_at,
            status,
            active,
            from_role,
            to_role,
            assignment_type
        )
        VALUES (?, ?, ?, ?, datetime('now','localtime'), 'ASSIGNED', 1, ?, ?, 'MULTI_ASSIGNMENT')
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        // 🔥 FIX: deactivate previous assignments
        try (PreparedStatement ps = c.prepareStatement(deactivate)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            ps.setInt(1, Session.getUserId());
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }

        for (Integer userId : assignedUserIds) {

            User targetUser = UserDAO.findById(userId);

            String toRole = targetUser.getRole();

            if (!com.app.util.RoleFlowUtil.canAssign(fromRole, toRole)) {
                throw new RuntimeException("Multi-assignment not allowed: " + fromRole + " → " + toRole);
            }

            int assignmentId;

            try (PreparedStatement ps = c.prepareStatement(insertAssignment, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, ticketId);
                ps.setInt(2, userId);
                ps.setInt(3, userId);
                ps.setInt(4, Session.getUserId());
                ps.setString(5, fromRole);
                ps.setString(6, toRole);
                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                rs.next();
                assignmentId = rs.getInt(1);
            }

            createTaskInternal(c,
                    ticketId,
                    assignmentId,
                    taskTitle,
                    taskDescription,
                    userId,
                    Session.getUserId());

            NotificationDAO.create(
                    userId,
                    "New Task",
                    "You received a task for ticket " + TicketUtil.formatTicketRef(ticketId),
                    "TICKET_TASK_ASSIGNED"
            );
        }

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }
}
    
   
   private static void createTaskInternal(Connection c,
                                       int ticketId,
                                       Integer parentAssignmentId,
                                       String title,
                                       String description,
                                       int assignedTo,
                                       int assignedBy) throws SQLException {

    String sql = """
        INSERT INTO ticket_tasks(
            ticket_id,
            parent_assignment_id,
            title,
            description,
            assigned_to,
            assigned_by,
            status,
            created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, 'PENDING', datetime('now','localtime'))
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, ticketId);

        if (parentAssignmentId != null) {
            ps.setInt(2, parentAssignmentId);
        } else {
            ps.setNull(2, Types.INTEGER);
        }

        ps.setString(3, title);
        ps.setString(4, description);
        ps.setInt(5, assignedTo);
        ps.setInt(6, assignedBy);
        ps.executeUpdate();
    }
}
   
   
public static boolean areAllTasksCompleted(int ticketId) {

    String sql = """
        SELECT COUNT(*) 
        FROM ticket_tasks
        WHERE ticket_id = ?
          AND status IS NOT NULL
          AND status != 'COMPLETED'
          AND LOWER(title) != 'task assignment'
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            int remaining = rs.getInt(1);

            System.out.println("🔍 Remaining tasks = " + remaining);

            return remaining == 0;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
}
 
 
  public static boolean hasTasks(int ticketId) {

    String sql = """
        SELECT COUNT(*) 
        FROM ticket_tasks
        WHERE ticket_id = ?
          AND LOWER(title) != 'task assignment'
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt(1) > 0;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
}
   
   
   
   
   
   
   
   
   
   
   
    public static String getUsernameById(Integer agentId) {

    if (agentId == null) return null;

    String sql = "SELECT username FROM users WHERE id = ?";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, agentId);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getString("username");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}
    
    
    
    
       
    public static ObservableList<Ticket> getUnassignedTickets() {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT 
            t.*,
            d.name AS department_name
        FROM tickets t
        LEFT JOIN departments d ON d.id = t.department_id
        LEFT JOIN ticket_assignments ta 
            ON t.id = ta.ticket_id AND ta.active = 1
        WHERE ta.id IS NULL
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            Ticket t = new Ticket();
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setTicketType(rs.getString("ticket_type"));
            t.setDepartmentName(rs.getString("department_name"));
            if (rs.getObject("department_id") != null) {
                t.setDepartmentId(rs.getInt("department_id"));
            }

            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static ObservableList<Ticket> getUnassignedTicketsForRole(String role, int userId) {
    String stage = monitoringRoutingStageForRole(role);
    if (stage == null || stage.isBlank()) {
        return FXCollections.observableArrayList();
    }

    ObservableList<Ticket> list = FXCollections.observableArrayList();
    String sql = """
        SELECT
            t.id,
            t.title,
            t.status,
            t.ticket_type,
            t.department_id,
            t.sous_direction_id,
            t.date_enregistrement,
            t.expediteur,
            t.objet,
            t.cotation,
            t.date_cotation,
            d.name AS department_name,
            sd.name AS sous_direction_name
        FROM tickets t
        LEFT JOIN departments d ON d.id = t.department_id
        LEFT JOIN sous_directions sd ON sd.id = t.sous_direction_id
        LEFT JOIN ticket_assignments ta ON ta.id = (
            SELECT ta2.id
            FROM ticket_assignments ta2
            WHERE ta2.ticket_id = t.id
            ORDER BY ta2.id DESC
            LIMIT 1
        )
        WHERE t.routing_stage = ?
          AND (
                (? = 'COURRIER' AND t.created_by = ? AND (ta.id IS NULL OR ta.active = 0))
                OR (? = 'SECRETAIRE' AND ta.assigned_to = ? AND ta.active = 1)
                OR (? = 'SOUS-DIRECTEUR' AND ta.assigned_to = ? AND ta.active = 1)
              )
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, stage);
        String normalizedRole = normalizeRole(role);
        ps.setString(2, normalizedRole);
        ps.setInt(3, userId);
        ps.setString(4, normalizedRole);
        ps.setInt(5, userId);
        ps.setString(6, normalizedRole);
        ps.setInt(7, userId);

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Ticket t = new Ticket();
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setTicketType(rs.getString("ticket_type"));
            t.setDepartmentName(rs.getString("department_name"));
            t.setSubDirection(rs.getString("sous_direction_name"));
            t.setRegistrationDate(rs.getString("date_enregistrement"));
            t.setSenderName(rs.getString("expediteur"));
            t.setSubject(rs.getString("objet"));
            t.setCotation(rs.getString("cotation"));
            t.setCotationDate(rs.getString("date_cotation"));

            if (rs.getObject("department_id") != null) {
                t.setDepartmentId(rs.getInt("department_id"));
            }
            if (rs.getObject("sous_direction_id") != null) {
                t.setSousDirectionId(rs.getInt("sous_direction_id"));
            }

            list.add(t);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static ObservableList<Ticket> getTicketsForMonitoringRole(String role, int userId) {
    role = normalizeRole(role);
    if ("COURRIER".equals(role) || "SECRETAIRE".equals(role) || "SOUS-DIRECTEUR".equals(role)) {
        return getUnassignedTicketsForRole(role, userId);
    }
    return getUnassignedTickets();
}

public static ObservableList<Ticket> getCompletedDataEntries() {
    ObservableList<Ticket> list = FXCollections.observableArrayList();
    String sql = """
        SELECT
            t.id,
            t.date_enregistrement,
            t.expediteur,
            t.objet,
            t.cotation,
            t.date_cotation,
            t.sous_direction_id,
            sd.name AS sous_direction_name,
            t.recorded_year
        FROM tickets t
        LEFT JOIN sous_directions sd ON sd.id = t.sous_direction_id
        WHERE t.date_enregistrement IS NOT NULL
          AND TRIM(t.date_enregistrement) <> ''
          AND t.expediteur IS NOT NULL
          AND TRIM(t.expediteur) <> ''
          AND t.objet IS NOT NULL
          AND TRIM(t.objet) <> ''
          AND t.cotation IS NOT NULL
          AND TRIM(t.cotation) <> ''
          AND t.date_cotation IS NOT NULL
          AND TRIM(t.date_cotation) <> ''
          AND t.sous_direction_id IS NOT NULL
          AND t.recorded_year IS NOT NULL
        ORDER BY t.date_enregistrement DESC, t.id DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            Ticket t = new Ticket();
            t.setId(rs.getInt("id"));
            t.setRegistrationDate(rs.getString("date_enregistrement"));
            t.setSenderName(rs.getString("expediteur"));
            t.setSubject(rs.getString("objet"));
            t.setCotation(rs.getString("cotation"));
            t.setCotationDate(rs.getString("date_cotation"));
            t.setSubDirection(rs.getString("sous_direction_name"));
            if (rs.getObject("sous_direction_id") != null) {
                t.setSousDirectionId(rs.getInt("sous_direction_id"));
            }
            list.add(t);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static ObservableList<com.app.model.DataEntry> getFinalizedDataEntries() {
    ObservableList<com.app.model.DataEntry> list = FXCollections.observableArrayList();
    for (Ticket t : getCompletedDataEntries()) {
        list.add(new com.app.model.DataEntry(
                t.getRegistrationDate(),
                t.getSenderName(),
                t.getSubject(),
                t.getCotation(),
                t.getCotationDate(),
                t.getSubDirection(),
                t.getId(),
                t.getSousDirectionId()
        ));
    }
    return list;
}

public static void clearFinalizedDataEntry(com.app.model.DataEntry entry) {
    if (entry == null || entry.getTicketId() == null) {
        return;
    }

    String sql = """
        UPDATE tickets
        SET cotation = NULL,
            date_cotation = NULL,
            cotation_assigned_at = NULL,
            recorded_year = NULL,
            recorded_at = NULL
        WHERE id = ?
    """;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, entry.getTicketId());
        ps.executeUpdate();
    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static void updateFinalizedDataEntry(com.app.model.DataEntry original,
                                            String newDateEnreg,
                                            String newExp,
                                            String newObj,
                                            String newCot,
                                            String newDateCot,
                                            String newSousDir) {
    if (original == null || original.getTicketId() == null) {
        return;
    }

    String resolveSousDirSql = "SELECT id FROM sous_directions WHERE name = ? LIMIT 1";
    String updateSql = """
        UPDATE tickets
        SET date_enregistrement = ?,
            expediteur = ?,
            objet = ?,
            cotation = ?,
            date_cotation = ?,
            sous_direction_id = ?,
            recorded_year = CASE
                WHEN ? IS NULL OR TRIM(?) = '' THEN NULL
                ELSE CAST(substr(?, 1, 4) AS INTEGER)
            END,
            recorded_at = datetime('now','localtime')
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection()) {
        Integer sousDirectionId = null;
        if (newSousDir != null && !newSousDir.isBlank()) {
            try (PreparedStatement ps = c.prepareStatement(resolveSousDirSql)) {
                ps.setString(1, newSousDir.trim());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    sousDirectionId = rs.getInt("id");
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement(updateSql)) {
            ps.setString(1, newDateEnreg);
            ps.setString(2, newExp);
            ps.setString(3, newObj);
            ps.setString(4, newCot);
            ps.setString(5, newDateCot);
            if (sousDirectionId != null) {
                ps.setInt(6, sousDirectionId);
            } else {
                ps.setNull(6, Types.INTEGER);
            }
            ps.setString(7, newDateEnreg);
            ps.setString(8, newDateEnreg);
            ps.setString(9, newDateEnreg);
            ps.setInt(10, original.getTicketId());
            ps.executeUpdate();
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static ObservableList<Ticket> getUnassignedTicketsCreatedBy(int creatorId) {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT 
            t.*,
            d.name AS department_name
        FROM tickets t
        LEFT JOIN departments d ON d.id = t.department_id
        LEFT JOIN ticket_assignments ta 
            ON t.id = ta.ticket_id AND ta.active = 1
        WHERE ta.id IS NULL
          AND t.created_by = ?
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, creatorId);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            Ticket t = new Ticket();
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setTicketType(rs.getString("ticket_type"));
            t.setDepartmentName(rs.getString("department_name"));
            if (rs.getObject("department_id") != null) {
                t.setDepartmentId(rs.getInt("department_id"));
            }
            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
    public static void startTask(int taskId) {

    String sql = """
        UPDATE ticket_tasks
        SET status = 'IN_PROGRESS',
            started_at = datetime('now','localtime')
        WHERE id = ?
          AND assigned_to = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        ps.setInt(2, Session.getUserId());

        int rows = ps.executeUpdate();

        if (rows == 0) {
            throw new RuntimeException("Task not found or not assigned to you.");
        }

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }
}
    
    
    public static void completeTask(int taskId) {

    String getTaskSql = """
        SELECT ticket_id
        FROM ticket_tasks
        WHERE id = ?
          AND assigned_to = ?
    """;

    String completeSql = """
        UPDATE ticket_tasks
        SET status = 'COMPLETED',
            completed_at = datetime('now','localtime')
        WHERE id = ?
          AND assigned_to = ?
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        int ticketId;

        try (PreparedStatement ps = c.prepareStatement(getTaskSql)) {
            ps.setInt(1, taskId);
            ps.setInt(2, Session.getUserId());

            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new RuntimeException("Task not found or not assigned to you.");
            }

            ticketId = rs.getInt("ticket_id");
        }

        try (PreparedStatement ps = c.prepareStatement(completeSql)) {
            ps.setInt(1, taskId);
            ps.setInt(2, Session.getUserId());
            ps.executeUpdate();
        }

        logEvent(c, ticketId, "TASK_COMPLETED",
        "Task #" + taskId + " completed by " + Session.getUsername());

       TicketHistoryDAO.log(
        c,
        ticketId,
        "TASK_COMPLETED",
        "Task #" + taskId + " completed by " + Session.getUsername()
);

        if (areAllTasksCompleted(ticketId)) {
            updateTicketStatus(ticketId, "RESOLVED");
            logEvent(c, ticketId, "ALL_TASKS_COMPLETED",
                    "All tasks completed for ticket " + TicketUtil.formatTicketRef(ticketId));
        }

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }
}
    
    
    
    
    

    // =====================================================
    // START WORK
    // =====================================================

    public static void startWork(int ticketId, int userId) {

    String checkAssignment = """
        SELECT status
        FROM ticket_assignments
        WHERE ticket_id = ?
          AND assigned_to = ?
          AND active = 1
    """;

    String updateAssignment = """
        UPDATE ticket_assignments
        SET status = 'IN_PROGRESS',
            started_at = datetime('now','localtime')
        WHERE ticket_id = ?
          AND assigned_to = ?
          AND active = 1
    """;

    String updateTicket = """
        UPDATE tickets
        SET status = 'IN_PROGRESS',
            started_at = COALESCE(started_at, datetime('now','localtime')),
            updated_by = ?,
            updated_at = datetime('now','localtime')
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        String currentStatus;

        try (PreparedStatement ps = c.prepareStatement(checkAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, userId);

            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new RuntimeException("Ticket not assigned to you.");
            }

            currentStatus = rs.getString("status");
        }

        if ("IN_PROGRESS".equals(currentStatus)) {
            throw new RuntimeException("Ticket already started.");
        }

        if ("CLOSED".equals(currentStatus)) {
            throw new RuntimeException("Ticket already closed.");
        }

        try (PreparedStatement ps = c.prepareStatement(updateAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            ps.setInt(1, userId);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }

      logEvent(c, ticketId, "STARTED",
        "Work started by " + Session.getUsername());

        TicketHistoryDAO.log(
        c,
        ticketId,
        "STARTED",
        "Work started by " + Session.getUsername()
);

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }
}

    // =====================================================
    // CLOSE TICKET
    // =====================================================

    public static void closeTicket(int ticketId) {

    int userId = Session.getUserId();
    String role = Session.getRole();

    if (!com.app.util.RoleFlowUtil.canCloseTicket(role)) {
        throw new RuntimeException("You are not allowed to close tickets.");
    }

    String checkTicket = """
        SELECT status
        FROM tickets
        WHERE id = ?
    """;

    String closeAssignments = """
        UPDATE ticket_assignments
        SET status = 'CLOSED',
            closed_at = datetime('now','localtime'),
            duration_minutes =
                CASE
                    WHEN started_at IS NOT NULL
                    THEN (strftime('%s','now') - strftime('%s', started_at)) / 60
                    ELSE duration_minutes
                END,
            active = 0
        WHERE ticket_id = ?
          AND active = 1
    """;

    String closeTicketSql = """
        UPDATE tickets
        SET status = 'CLOSED',
            closed_at = datetime('now','localtime'),
            resolution_minutes =
                CASE
                    WHEN started_at IS NOT NULL
                    THEN (strftime('%s','now') - strftime('%s', started_at)) / 60
                    ELSE resolution_minutes
                END,
            updated_by = ?,
            updated_at = datetime('now','localtime')
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        String ticketStatus;

        try (PreparedStatement ps = c.prepareStatement(checkTicket)) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                throw new RuntimeException("Ticket not found.");
            }

            ticketStatus = rs.getString("status");
        }

        if ("CLOSED".equalsIgnoreCase(ticketStatus)) {
            throw new RuntimeException("Ticket is already closed.");
        }

        if (hasTasks(ticketId) && !areAllTasksCompleted(ticketId)) {
            throw new RuntimeException("Ticket cannot be closed until all tasks are completed.");
        }

        try (PreparedStatement ps = c.prepareStatement(closeAssignments)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(closeTicketSql)) {
            ps.setInt(1, userId);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }

        logEvent(c, ticketId, "CLOSED",
        "Ticket closed by " + Session.getUsername());

        TicketHistoryDAO.log(
        c,
        ticketId,
        "CLOSED",
        "Ticket closed by " + Session.getUsername()
);

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }
}

    
    
public static int countAll() {

    String sql = "SELECT COUNT(*) FROM tickets";

    try (var conn = DB.getConnection();
         var stmt = conn.prepareStatement(sql);
         var rs = stmt.executeQuery()) {

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}




 public static int countByStatus(String status) {

    String sql = "SELECT COUNT(*) FROM tickets WHERE status = ?";

    try (var conn = DB.getConnection();
         var stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, status);

        var rs = stmt.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}
 
 public static List<Ticket> findLast5() {

    List<Ticket> list = new ArrayList<>();

    String sql = """
            SELECT * FROM tickets
            ORDER BY id DESC
            LIMIT 10
            """;

    try (var conn = DB.getConnection();
             
         var stmt = conn.prepareStatement(sql);
         var rs = stmt.executeQuery()) {

        while (rs.next()) {

            Ticket t = new Ticket();

            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            Timestamp created = rs.getTimestamp("created_at");
            t.setCreatedAt(toLocalTime(created).toString());

            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
 // =====================================================
// COUNT TICKETS FOR AGENT
// =====================================================

public static int countTicketsByUserAssignments(int userId) {

    String sql = """
        SELECT COUNT(*)
        FROM ticket_assignments
        WHERE assigned_to = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}

public static int countOpenUserAssignments(int userId) {

    String sql = """
        SELECT COUNT(*)
        FROM ticket_assignments
        WHERE assigned_to = ?
        AND status IN ('ASSIGNED','IN_PROGRESS')
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}

public static int countClosedUserAssignments(int userId) {

    String sql = """
        SELECT COUNT(*)
        FROM ticket_assignments
        WHERE assigned_to = ?
        AND status = 'CLOSED'
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}





public static int countAllTickets() {
    return countAll();
}

public static int countOpenTickets() {
    return countByStatus("OPEN")
            + countByStatus("ASSIGNED")
            + countByStatus("IN_PROGRESS");
}
 
 public static int countClosedTickets() {
    return countByStatus("CLOSED");
}
 
 
 public static void addComment(int ticketId, int userId, String comment, String imagePath) {

    String sql = """
        INSERT INTO ticket_comments(ticket_id,user_id,comment,image_path,created_at)
        VALUES(?,?,?, ?, datetime('now','localtime'))
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setInt(2, userId);
        ps.setString(3, comment);
        ps.setString(4, imagePath);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static List<TicketEvent> getComments(int ticketId) {

    List<TicketEvent> list = new ArrayList<>();

    String sql = """
        SELECT tc.comment,
               tc.created_at,
               u.username
        FROM ticket_comments tc
        JOIN users u ON u.id = tc.user_id
        WHERE tc.ticket_id = ?
        ORDER BY tc.created_at ASC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketEvent event = new TicketEvent();

            event.setUsername(rs.getString("username"));
            event.setType("COMMENT");
            event.setDescription(rs.getString("comment"));
            String raw = rs.getString("created_at");

if (raw != null) {
    DateTimeFormatter input = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    LocalDateTime dt = LocalDateTime.parse(raw, input);

    // 🔥 FORCE LOCAL TIME
    dt = dt.atZone(ZoneId.systemDefault())
           .withZoneSameInstant(ZoneId.systemDefault())
           .toLocalDateTime();

    DateTimeFormatter output = DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm");

    event.setCreatedAt(dt.format(output));
}

            list.add(event);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static boolean isOverdue(int ticketId) {

    String sql = """
        SELECT 
        (julianday('now') - julianday(created_at)) * 24 > sla_hours
        FROM tickets
        WHERE id = ?
    """;

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        var rs = ps.executeQuery();

        return rs.next() && rs.getBoolean(1);

    } catch (Exception e) {
        e.printStackTrace();
        return false;
    }
}


 
public static ObservableList<Ticket> getAssignedTickets() {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT 
            id,
            title,
            status,
            priority,
            started_at,
            closed_at,
            resolution_minutes
        FROM tickets
        ORDER BY created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Ticket t = new Ticket();

            // =========================
            // BASIC
            // =========================
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setPriority(rs.getString("priority"));

            // =========================
            // START DATE
            // =========================
            Timestamp startTs = rs.getTimestamp("started_at");
            if (startTs != null) {
                t.setStartedAt(startTs.toLocalDateTime());
            }

            // =========================
            // CLOSE DATE
            // =========================
            Timestamp closeTs = rs.getTimestamp("closed_at");
            if (closeTs != null) {
                t.setClosedAt(closeTs.toLocalDateTime());
            }

            // =========================
            // RESOLUTION
            // =========================
            int res = rs.getInt("resolution_minutes");
            if (!rs.wasNull()) {
                t.setResolutionMinutes(res);
            }

            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}




private static void validateTransition(String currentStatus, String nextStatus) {

    switch (currentStatus) {

        case "OPEN":
            if (!"ASSIGNED".equals(nextStatus)) {
                throw new RuntimeException("Invalid transition from OPEN to " + nextStatus);
            }
            break;

        case "ASSIGNED":
            if (!"IN_PROGRESS".equals(nextStatus)) {
                throw new RuntimeException("Invalid transition from ASSIGNED to " + nextStatus);
            }
            break;

        case "IN_PROGRESS":
            if (!"CLOSED".equals(nextStatus)) {
                throw new RuntimeException("Invalid transition from IN_PROGRESS to " + nextStatus);
            }
            break;

        case "CLOSED":
            throw new RuntimeException("Ticket already closed.");

        default:
            throw new RuntimeException("Unknown ticket state: " + currentStatus);
    }
}
  


public static void mergeTickets(int masterId, int mergedId) {
    
    
    

    if (masterId == mergedId) {
        throw new RuntimeException("Cannot merge ticket into itself.");
    }

    String selectStatusSql = "SELECT status FROM tickets WHERE id=?";
    String selectDescSql = "SELECT description FROM tickets WHERE id=?";

    String updateMergedSql = """
        UPDATE tickets
        SET status='MERGED',
            merged_into=?,
            merge_note=?
        WHERE id=?
    """;

    String updateMasterDescSql = """
        UPDATE tickets
        SET description=?
        WHERE id=?
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        // Validate merged ticket status
        try (PreparedStatement ps = c.prepareStatement(selectStatusSql)) {

            ps.setInt(1, mergedId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {

                String status = rs.getString("status");

                if (!"CLOSED".equals(status)) {
                    throw new RuntimeException("Only CLOSED tickets can be merged.");
                }
            }
        }

        // Get descriptions
        String masterDesc = "";
        String mergedDesc = "";

        try (PreparedStatement ps = c.prepareStatement(selectDescSql)) {

            ps.setInt(1, masterId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) masterDesc = rs.getString("description");

            ps.setInt(1, mergedId);
            rs = ps.executeQuery();
            if (rs.next()) mergedDesc = rs.getString("description");
        }

        // Merge descriptions into MASTER
        String newDescription =
                masterDesc +
                "\n\n--- Merged Ticket #" + mergedId + " ---\n" +
                mergedDesc;

        try (PreparedStatement ps = c.prepareStatement(updateMasterDescSql)) {

            ps.setString(1, newDescription);
            ps.setInt(2, masterId);
            ps.executeUpdate();
        }

        // Mark merged ticket
        String mergeMessage =
                "Ticket #" + mergedId +
                " merged into #" + masterId +
                " by " + Session.getUsername();

        try (PreparedStatement ps = c.prepareStatement(updateMergedSql)) {

            ps.setInt(1, masterId);
            ps.setString(2, mergeMessage);
            ps.setInt(3, mergedId);
            ps.executeUpdate();
        }


        // =============================
        // MOVE ATTACHMENTS TO MASTER
        // =============================
        
        

String selectAttachmentSql = """
    SELECT attachment_name, attachment_path, attachment_type
    FROM tickets
    WHERE id = ?
""";

String updateAttachmentSql = """
    UPDATE tickets
    SET attachment_name = ?,
        attachment_path = ?,
        attachment_type = ?
    WHERE id = ?
""";

try (PreparedStatement ps = c.prepareStatement(selectAttachmentSql)) {

    ps.setInt(1, mergedId);
    ResultSet rs = ps.executeQuery();

    if (rs.next()) {

        String name = rs.getString("attachment_name");
        String path = rs.getString("attachment_path");
        String type = rs.getString("attachment_type");

        try (PreparedStatement update = c.prepareStatement(updateAttachmentSql)) {

            update.setString(1, name);
            update.setString(2, path);
            update.setString(3, type);
            update.setInt(4, masterId);
            update.executeUpdate();
        }
    }
}

        
        String moveAttachmentsSql = """
    UPDATE ticket_attachments
    SET ticket_id = ?
    WHERE ticket_id = ?
""";

try (PreparedStatement ps = c.prepareStatement(moveAttachmentsSql)) {

    ps.setInt(1, masterId);
    ps.setInt(2, mergedId);
    ps.executeUpdate();
}

        
        c.commit();

    } catch (Exception e) {
        e.printStackTrace();
    }

    try (Connection c = DB.getConnection()) {

    c.setAutoCommit(false);

    // ... all merge logic

    logEvent(c, masterId, "MERGED",
            "Ticket #" + mergedId +
            " merged by " + Session.getUsername());

    TicketHistoryDAO.log(
            c,
            masterId,
            "MERGED",
            "Ticket #" + mergedId +
            " merged by " + Session.getUsername()
    );

    c.commit();

} catch (Exception e) {
    e.printStackTrace();
}
    
    
    
    
    
}





    
public static List<TicketEvent> getEvents(int ticketId) {

    List<TicketEvent> list = new ArrayList<>();

    String sql = """
        SELECT event_type AS type,
               comment AS description,
               created_at,
               u.username
        FROM ticket_events te
        LEFT JOIN users u ON te.user_id = u.id
        WHERE te.ticket_id = ?

        UNION ALL

        SELECT 'COMMENT' AS type,
               tc.comment AS description,
               tc.created_at,
               u.username
        FROM ticket_comments tc
        LEFT JOIN users u ON tc.user_id = u.id
        WHERE tc.ticket_id = ?

        ORDER BY created_at ASC
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setInt(2, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketEvent event = new TicketEvent();

            event.setType(rs.getString("type"));
            event.setDescription(rs.getString("description"));
            event.setUsername(rs.getString("username"));
            event.setCreatedAt(rs.getString("created_at"));

            list.add(event);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static List<Ticket> getActiveTicketsForSLA() {

    List<Ticket> list = new java.util.ArrayList<>();

    String sql = """
        SELECT * FROM tickets
        WHERE status != 'CLOSED'
        AND status != 'MERGED'
    """;

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql);
         var rs = ps.executeQuery()) {

        while (rs.next()) {

            Ticket t = new Ticket();
            t.setId(rs.getInt("id"));
            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static double getSlaUsagePercent(int ticketId) {

    String sql = """
        SELECT 
        ((julianday('now') - julianday(created_at)) * 24) / sla_hours * 100
        FROM tickets
        WHERE id = ?
    """;

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        var rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getDouble(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return 0;
}

public static boolean hasSlaEvent(int ticketId, String type) {

    String sql = """
        SELECT COUNT(*) FROM ticket_events
        WHERE ticket_id = ?
        AND event_type = ?
    """;

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setString(2, type);

        var rs = ps.executeQuery();
        return rs.next() && rs.getInt(1) > 0;

    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
}


public static void logSystemEvent(int ticketId, String type, String description) {

    String sql = """
        INSERT INTO ticket_events(ticket_id, user_id, event_type, comment)
        VALUES (?, ?, ?, ?)
    """;

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        // ✅ 1. ticket_id
        ps.setInt(1, ticketId);

        // ✅ 2. user_id (IMPORTANT FIX)
        int userId = Session.getUserId();

        if (userId <= 0) {
            userId = 1; // fallback admin OR create SYSTEM user (better)
        }

        ps.setInt(2, userId);

        // ✅ 3. event_type
        ps.setString(3, type);

        // ✅ 4. comment
        ps.setString(4, description);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}



public static void updatePriority(int ticketId, String priority) {

    String sql = "UPDATE tickets SET priority=? WHERE id=?";

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setString(1, priority);
        ps.setInt(2, ticketId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


public static Integer getAdminUserId() {

    String sql = "SELECT id FROM users WHERE role='ADMIN' LIMIT 1";

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql);
         var rs = ps.executeQuery()) {

        if (rs.next()) {
            return rs.getInt("id");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}

public static void forceReassign(int ticketId, int userId) {

    // Validate user
    if (userId <= 0) {
        System.out.println("Invalid escalation user: " + userId);
        return;
    }

    // Ensure user exists
    User user = UserDAO.findById(userId);

    if (user == null) {
        System.out.println("Escalation user not found: " + userId);
        return;
    }

    String sql = "UPDATE tickets SET assigned_to=? WHERE id=?";

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.setInt(2, ticketId);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static Integer getDepartmentId(int ticketId) {

    String sql = "SELECT department_id FROM tickets WHERE id=?";

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        var rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt("department_id");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}

public static Integer getEscalationLevel(int departmentId, int level) {

    String column = (level == 1)
            ? "escalation_level1"
            : "escalation_level2";

    String sql = "SELECT " + column + " FROM departments WHERE id=?";

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setInt(1, departmentId);
        var rs = ps.executeQuery();

        if (rs.next()) {
    int value = rs.getInt(1);
    return rs.wasNull() ? null : value;
}

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}

public static int countTicketsByDepartment(int departmentId) {

    String sql = "SELECT COUNT(*) FROM tickets WHERE department_id=?";

    try (var conn = DB.getConnection();
         var ps = conn.prepareStatement(sql)) {

        ps.setInt(1, departmentId);
        var rs = ps.executeQuery();

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}



public static double slaComplianceRate(int departmentId) {

    int total = countTicketsByDepartment(departmentId);
    int breaches = countSlaBreaches();

    if (total == 0) return 100;

    return ((double)(total - breaches) / total) * 100;
}


public static double averageResolution() {

    String sql = """
        SELECT AVG(resolution_minutes)
        FROM tickets
        WHERE status = 'CLOSED'
        AND resolution_minutes IS NOT NULL
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        return rs.next() ? rs.getDouble(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}

public static String getTopUser(int departmentId) {

    String sql = """
        SELECT u.username, COUNT(*) AS closed_count
        FROM tickets t
        JOIN users u ON t.assigned_to = u.id
        WHERE t.department_id = ?
        AND t.status = 'CLOSED'
        GROUP BY u.username
        ORDER BY closed_count DESC
        LIMIT 1
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, departmentId);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getString("username");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return "N/A";
}

public static int countSlaBreaches() {

    String sql = """
        SELECT COUNT(*)
        FROM tickets
        WHERE started_at IS NOT NULL
        AND closed_at IS NULL
        AND (
            (strftime('%s','now') - strftime('%s',started_at)) / 60
        ) > (sla_hours * 60)
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        if (rs.next()) {
            return rs.getInt(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return 0;
}

public static int countEscalations() {

    String sql = "SELECT COUNT(*) FROM ticket_escalations";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        if (rs.next()) {
            return rs.getInt(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return 0;
}

private void loadAgentPerformanceTable() {

    ObservableList<AgentRow> list = FXCollections.observableArrayList();

    Map<String, Integer> performance = TicketDAO.getUserPerformance();

    for (Map.Entry<String, Integer> entry : performance.entrySet()) {
        list.add(new AgentRow(entry.getKey(), entry.getValue()));
    }

}


public static Map<String, Integer> getUserPerformance() {

    Map<String, Integer> performance = new HashMap<>();

    String sql = """
        SELECT u.username, COUNT(*) as total
        FROM ticket_assignments ta
        JOIN users u ON ta.assigned_to = u.id
        GROUP BY u.username
        ORDER BY total DESC
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {

            String user = rs.getString("username");
            int total = rs.getInt("total");

            System.out.println("PERF DEBUG → " + user + " = " + total);

            performance.put(user, total);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return performance;
}

public static void mergeTicket(int sourceId, int targetId, String note) {

    String sql = """
        UPDATE tickets
        SET status = 'MERGED',
            merged_into = ?,
            merge_note = ?
        WHERE id = ?
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, targetId);
        ps.setString(2, note);
        ps.setInt(3, sourceId);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException("Error merging ticket.");
    }
}

public static void updateSlaBreachStatus(int ticketId) {

    String sql = """
        UPDATE tickets
        SET sla_breached = 
            CASE 
                WHEN closed_at IS NOT NULL 
                     AND closed_at > sla_deadline
                THEN 1
                ELSE 0
            END
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

// =============================
    // SLA RULES
    // =============================
    private static int getSlaMinutes(String priority) {
        return switch (priority) {
            case "CRITICAL" -> 60;
            case "HIGH" -> 120;
            case "MEDIUM" -> 480;
            default -> 1440; // LOW
        };


}
    

    // =============================
    // CREATE TICKET
    // =============================
    public static int createTicket(String title,
                               String description,
                               String priority,
                               Department department,
                               Ticket mergeTicket,
                               List<File> attachments,
                               String ticketType,
                               List<User> selectedUsers) {

    int currentUserId = Session.getUserId();

    String sql = """
        INSERT INTO tickets(
            title,
            description,
            priority,
            status,
            ticket_type,
            department_id,
            created_by,
            assigned_to,
            date_enregistrement,
            expediteur,
            objet,
            routing_stage,
            created_at
        )
        VALUES(?, ?, ?, 'OPEN', ?, ?, ?, NULL, ?, ?, ?, 'CREATED', datetime('now','localtime'))
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        String dateEnregistrement = LocalDate.now().toString();
        String expediteur = title == null ? "" : title.trim();
        String objet = description == null ? "" : description.trim();

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, priority);
        ps.setString(4, ticketType);
        ps.setInt(5, department.getId());
        ps.setInt(6, currentUserId);
        ps.setString(7, dateEnregistrement);
        ps.setString(8, expediteur);
        ps.setString(9, objet);

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();

        if (rs.next()) {

            int ticketId = rs.getInt(1);

            int year = java.time.Year.now().getValue();
            String ticketNumber = "TCK-" + year + "-" + String.format("%03d", ticketId);

            updateTicketNumber(ticketId, ticketNumber);

            if (attachments != null && !attachments.isEmpty()) {
                saveAttachments(ticketId, attachments);
            }

            logEvent(c, ticketId,
                    "CREATED",
                    ticketType + " ticket created by " + Session.getUsername()
            );

            TicketHistoryDAO.log(
                    c,
                    ticketId,
                    "CREATED",
                    "Ticket created by " + Session.getUsername()
            );

            Ticket ticket = getTicketById(ticketId);
            new Thread(() -> EmailService.notifyAdmins(ticket)).start();

            return ticketId;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return -1;
}

    
    
    
    
public static void updateTicketNumber(int ticketId, String ticketNumber) {

    String sql = "UPDATE tickets SET ticket_number = ? WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, ticketNumber);
        ps.setInt(2, ticketId);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
    
    
public static ObservableList<Ticket> getExternalTickets() {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT t.*, d.name as department_name
        FROM tickets t
        LEFT JOIN departments d ON t.department_id = d.id
        WHERE UPPER(t.ticket_type) = 'EXTERNAL'
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

    Ticket ticket = new Ticket();

    ticket.setId(rs.getInt("id"));
    ticket.setTitle(rs.getString("title"));
    ticket.setPriority(rs.getString("priority"));
    ticket.setStatus(rs.getString("status"));
    ticket.setDepartmentName(rs.getString("department_name"));
    ticket.setTicketType(rs.getString("ticket_type"));

    // LOAD ATTACHMENTS
    ticket.setAttachments(
        TicketDAO.getTicketAttachments(ticket.getId())
    );

    list.add(ticket);
}

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}



public static int createInternalTicketFromExcel(
        String title,
        String description,
        String priority,
        int agentId,
        int departmentId) {

    if (priority == null || priority.isBlank()) {
        priority = "MEDIUM";
    }

    String sql = """
        INSERT INTO tickets(
            title,
            description,
            priority,
            status,
            ticket_type,
            department_id,
            assigned_to,
            created_by,
            created_at
        )
        VALUES(?, ?, ?, 'OPEN', 'INTERNAL', ?, ?, ?, CURRENT_TIMESTAMP)
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, priority);
        ps.setInt(4, departmentId);
        ps.setInt(5, agentId);
        ps.setInt(6, Session.getUserId());

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();

        if (rs.next()) {
            return rs.getInt(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return -1;
}

public static void updateStatus(int ticketId, String status) {

    String sql = "UPDATE tickets SET status=? WHERE id=?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, status);
        ps.setInt(2, ticketId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


public static boolean isAssignedToUser(int ticketId, int userId) {

    String sql = """
        SELECT 1
        FROM ticket_assignments
        WHERE ticket_id = ?
        AND assigned_to = ?
        AND active = 1
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setInt(2, userId);

        ResultSet rs = ps.executeQuery();
        return rs.next();

    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
}


public static void reassignTicket(int ticketId, int newUserId) {

    User targetUser = UserDAO.findById(newUserId);

    if (targetUser == null) {
        throw new RuntimeException("Target user not found.");
    }

    String fromRole = Session.getRole();
    String toRole = targetUser.getRole();

    if (!com.app.util.RoleFlowUtil.canAssign(fromRole, toRole)) {
        throw new RuntimeException("Reassignment not allowed: " + fromRole + " → " + toRole);
    }

    String deactivate = """
        UPDATE ticket_assignments
        SET active = 0
        WHERE ticket_id = ?
          AND active = 1
    """;

    String insert = """
        INSERT INTO ticket_assignments
        (ticket_id, agent_id, assigned_to, assigned_by, status, assigned_at, active, from_role, to_role, assignment_type)
        VALUES (?, ?, ?, ?, 'ASSIGNED', datetime('now','localtime'), 1, ?, ?, 'REASSIGNMENT')
    """;

    String updateTicket = """
        UPDATE tickets
        SET assigned_to = ?,
            status = 'ASSIGNED',
            started_at = NULL,
            closed_at = NULL,
            updated_by = ?,
            updated_at = datetime('now','localtime')
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        try (PreparedStatement ps = c.prepareStatement(deactivate)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, newUserId); // compatibility
            ps.setInt(3, newUserId); // generic
            ps.setInt(4, Session.getUserId());
            ps.setString(5, fromRole);
            ps.setString(6, toRole);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            ps.setInt(1, newUserId);
            ps.setInt(2, Session.getUserId());
            ps.setInt(3, ticketId);
            ps.executeUpdate();
        }

      logEvent(c, ticketId, "REASSIGNED",
        "Ticket reassigned to " + targetUser.getUsername() + " (" + toRole + ")");

       TicketHistoryDAO.log(
        c,
        ticketId,
        "REASSIGNED",
        "Ticket reassigned by " + Session.getUsername() + " to " + targetUser.getUsername()
);

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }

    NotificationDAO.create(
            newUserId,
            "Ticket Reassigned",
            "Ticket " + TicketUtil.formatTicketRef(ticketId) + " has been reassigned to you",
            "TICKET_REASSIGNED"
    );
}



public static void escalateTicket(int ticketId,
                                  int fromUserId,
                                  int toUserId,
                                  String newPriority,
                                  String reason) {

    User fromUser = UserDAO.findById(fromUserId);
    User toUser = UserDAO.findById(toUserId);

    if (fromUser == null || toUser == null) {
        throw new RuntimeException("Escalation users not found.");
    }

    String fromRole = fromUser.getRole();
    String toRole = toUser.getRole();

    if (!com.app.util.RoleFlowUtil.canAssign(fromRole, toRole)) {
        throw new RuntimeException("Escalation not allowed: " + fromRole + " → " + toRole);
    }

    if (!com.app.util.RoleFlowUtil.isEscalation(fromRole, toRole)) {
        throw new RuntimeException("This reassignment is not a valid escalation path.");
    }

    String deactivate = """
        UPDATE ticket_assignments
        SET active = 0
        WHERE ticket_id = ?
          AND active = 1
    """;

    String newAssignment = """
        INSERT INTO ticket_assignments
        (ticket_id, agent_id, assigned_to, assigned_by, status, assigned_at, active, from_role, to_role, assignment_type)
        VALUES (?, ?, ?, ?, 'ESCALATED', datetime('now','localtime'), 1, ?, ?, 'ESCALATION')
    """;

    String escalation = """
        INSERT INTO ticket_escalations
        (ticket_id, escalated_by, escalated_from, escalated_to, reason)
        VALUES (?, ?, ?, ?, ?)
    """;

    String updateTicket = """
        UPDATE tickets
        SET assigned_to = ?,
            status = 'ESCALATED',
            started_at = NULL,
            closed_at = NULL,
            updated_by = ?,
            updated_at = datetime('now','localtime')
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        try (PreparedStatement ps = c.prepareStatement(deactivate)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(newAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, toUserId); // compatibility
            ps.setInt(3, toUserId); // generic
            ps.setInt(4, fromUserId);
            ps.setString(5, fromRole);
            ps.setString(6, toRole);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(escalation)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, fromUserId);
            ps.setInt(3, fromUserId);
            ps.setInt(4, toUserId);
            ps.setString(5, reason);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            ps.setInt(1, toUserId);
            ps.setInt(2, fromUserId);
            ps.setInt(3, ticketId);
            ps.executeUpdate();
        }

        if (newPriority != null && !newPriority.isBlank()) {
            updatePriority(ticketId, newPriority);
        }

        logEvent(c, ticketId, "ESCALATED",
        "Ticket escalated from " + fromUser.getUsername() + " to " + toUser.getUsername());

TicketHistoryDAO.log(
        c,
        ticketId,
        "ESCALATED",
        "Escalated by " + fromUser.getUsername() + " to " + toUser.getUsername()
);

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }

    NotificationDAO.create(
            toUserId,
            "Ticket Escalated",
            "Ticket " + TicketUtil.formatTicketRef(ticketId) + " has been escalated to you",
            "TICKET_ESCALATED"
    );
}



public static void updateTicketStatus(int ticketId, String status) {

    String sql = """
        UPDATE tickets
        SET status = ?
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, status);
        ps.setInt(2, ticketId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}




public static String getAssignedAgent(int ticketId) {

    String sql = """
        SELECT u.username
        FROM ticket_assignments ta
        JOIN users u ON ta.assigned_to = u.id
        WHERE ta.ticket_id = ?
        ORDER BY ta.assigned_at DESC
        LIMIT 1
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getString("username");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return "unassigned";
}

public static void deleteTicket(int ticketId) {

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        try {

            // ticket comments
            PreparedStatement ps1 =
                    c.prepareStatement("DELETE FROM ticket_comments WHERE ticket_id=?");
            ps1.setInt(1, ticketId);
            ps1.executeUpdate();

            // ticket events
            PreparedStatement ps2 =
                    c.prepareStatement("DELETE FROM ticket_events WHERE ticket_id=?");
            ps2.setInt(1, ticketId);
            ps2.executeUpdate();

            // escalations
            PreparedStatement ps3 =
                    c.prepareStatement("DELETE FROM ticket_escalations WHERE ticket_id=?");
            ps3.setInt(1, ticketId);
            ps3.executeUpdate();

            // assignments
            PreparedStatement ps4 =
                    c.prepareStatement("DELETE FROM ticket_assignments WHERE ticket_id=?");
            ps4.setInt(1, ticketId);
            ps4.executeUpdate();

            // audit logs
            PreparedStatement ps5 =
                    c.prepareStatement(
                            "DELETE FROM system_audit WHERE entity='TICKET' AND entity_id=?"
                    );
            ps5.setInt(1, ticketId);
            ps5.executeUpdate();

            // finally delete ticket
            PreparedStatement ps6 =
                    c.prepareStatement("DELETE FROM tickets WHERE id=?");
            ps6.setInt(1, ticketId);
            ps6.executeUpdate();

            c.commit();

        } catch (Exception e) {
            c.rollback();
            e.printStackTrace();
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}




public static ObservableList<Ticket> getAllTickets() {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
    SELECT 
        t.id,
        t.title,
        t.description,
        t.priority,
        t.status,
        t.ticket_type,
        t.created_at,
        t.department_id,
        t.created_by,

        u.username AS agent_name,
        d.name AS department_name,
        c.username AS creator_name,
        c.role AS creator_role,
        dir.name AS creator_sd

    FROM tickets t

    LEFT JOIN ticket_assignments ta
        ON ta.ticket_id = t.id
        AND ta.id = (
            SELECT id
            FROM ticket_assignments ta2
            WHERE ta2.ticket_id = t.id
            ORDER BY ta2.id DESC
            LIMIT 1
        )

    LEFT JOIN users u
        ON ta.assigned_to = u.id

    LEFT JOIN departments d
        ON t.department_id = d.id

    LEFT JOIN users c
        ON t.created_by = c.id

    LEFT JOIN directions dir
        ON c.direction_id = dir.id

    WHERE t.merged_into IS NULL

    ORDER BY t.created_at DESC
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            Ticket ticket = new Ticket();

            ticket.setId(rs.getInt("id"));
            ticket.setTitle(rs.getString("title"));
            ticket.setDescription(rs.getString("description"));
            ticket.setPriority(rs.getString("priority"));
            ticket.setStatus(rs.getString("status"));

            // ⭐ IMPORTANT (fix for EXTERNAL/INTERNAL edit routing)
            ticket.setTicketType(rs.getString("ticket_type"));

            Timestamp created = rs.getTimestamp("created_at");

            if (created != null) {

                DateTimeFormatter formatter =
                        DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm");

                ticket.setCreatedAt(
                        toLocalTime(created).format(formatter)
                );
            }

            ticket.setAssignedToName(rs.getString("agent_name"));
            ticket.setDepartmentName(rs.getString("department_name"));
            if (rs.getObject("department_id") != null) {
                ticket.setDepartmentId(rs.getInt("department_id"));
            }

            ticket.setCreatedByName(rs.getString("creator_name"));
            ticket.setCreatedByRole(rs.getString("creator_role"));
            ticket.setCreatedBySousDirection(rs.getString("creator_sd"));

            list.add(ticket);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static ObservableList<Ticket> searchTickets(String keyword) {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT t.*, u.username
        FROM tickets t
        LEFT JOIN users u ON t.assigned_to = u.id
        WHERE
            t.title LIKE ?
        OR  t.description LIKE ?
        OR  t.status LIKE ?
        OR  t.ticket_type LIKE ?
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        String k = "%" + keyword + "%";

        ps.setString(1, k);
        ps.setString(2, k);
        ps.setString(3, k);
        ps.setString(4, k);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Ticket ticket = new Ticket();

            ticket.setId(rs.getInt("id"));
            ticket.setTitle(rs.getString("title"));
            ticket.setStatus(rs.getString("status"));
            ticket.setTicketType(rs.getString("ticket_type"));

            list.add(ticket);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static ObservableList<Ticket> filterTickets(String status, String agent) {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT 
            t.*,
            u.username AS agent_name
        FROM tickets t
        LEFT JOIN users u ON t.assigned_to = u.id
        WHERE 1=1
    """;

    if (status != null && !status.equals("ALL")) {
        sql += " AND t.status = ?";
    }

    if (agent != null && !agent.equals("ALL")) {
        sql += " AND u.username = ?";
    }

    sql += " ORDER BY t.created_at DESC";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        int index = 1;

        if (status != null && !status.equals("ALL")) {
            ps.setString(index++, status);
        }

        if (agent != null && !agent.equals("ALL")) {
            ps.setString(index++, agent);
        }

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Ticket ticket = new Ticket();

            ticket.setId(rs.getInt("id"));
            ticket.setTitle(rs.getString("title"));
            ticket.setDescription(rs.getString("description"));
            ticket.setPriority(rs.getString("priority"));
            ticket.setStatus(rs.getString("status"));
            ticket.setTicketType(rs.getString("ticket_type"));
            ticket.setAssignedToName(rs.getString("agent_name"));
            Timestamp created = rs.getTimestamp("created_at");
            ticket.setCreatedAt(toLocalTime(created).toString());

            list.add(ticket);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}


public static ObservableList<String> getDistinctAgents() {

    ObservableList<String> list = FXCollections.observableArrayList();

    String sql = """
        SELECT DISTINCT u.username
        FROM tickets t
        LEFT JOIN users u ON t.assigned_to = u.id
        WHERE u.username IS NOT NULL
        ORDER BY u.username
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            list.add(rs.getString("username"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}


public static ObservableList<String> getAgents() {

    ObservableList<String> list = FXCollections.observableArrayList();

    String sql = """
        SELECT username
        FROM users
        WHERE role = 'AGENT'
        ORDER BY username
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            list.add(rs.getString("username"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static int getAgentIdByUsername(String username) {

    String sql = "SELECT id FROM users WHERE username = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return -1;
}

public static int countTicketsByUser(int userId){

    String sql = "SELECT COUNT(*) FROM tickets WHERE created_by=?";

    try(Connection conn = DB.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)){

        ps.setInt(1,userId);

        ResultSet rs = ps.executeQuery();

        if(rs.next())
            return rs.getInt(1);

    }catch(Exception e){
        e.printStackTrace();
    }

    return 0;
}


public static int countTicketsByStatus(int userId, String status){

    String sql = """
        SELECT COUNT(*)
        FROM tickets
        WHERE created_by = ?
        AND status = ?
    """;

    try(Connection conn = DB.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)){

        ps.setInt(1, userId);
        ps.setString(2, status);

        ResultSet rs = ps.executeQuery();

        if(rs.next()){
            return rs.getInt(1);
        }

    }catch(Exception e){
        e.printStackTrace();
    }

    return 0;
}

public static List<Ticket> getTicketsCreatedBy(int userId) {

    List<Ticket> list = new ArrayList<>();

    String sql = """
        SELECT
            t.id,
            t.title,
            t.description,
            t.priority,
            t.status,
            t.ticket_type,
            t.created_at,
            d.name AS department_name
        FROM tickets t
        LEFT JOIN departments d ON t.department_id = d.id
        WHERE t.created_by = ?
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Ticket t = new Ticket();

            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setDescription(rs.getString("description"));
            t.setPriority(rs.getString("priority"));
            t.setStatus(rs.getString("status"));

            // ⭐ THIS FIX
            t.setTicketType(rs.getString("ticket_type"));

            Timestamp created = rs.getTimestamp("created_at");
            t.setCreatedAt(toLocalTime(created).toString());

            t.setDepartmentName(rs.getString("department_name"));

            t.setAttachments(
                TicketDAO.getTicketAttachments(t.getId())
            );

            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}


public static Ticket getTicketById(int ticketId) {

    Ticket ticket = null;

    String sql = """
        SELECT 
            t.*,
            u.username AS created_by_name,
            u2.username AS updated_by_name,
            u3.username AS agent_name,
            u3.id AS agent_id,
            d.name AS department_name
        FROM tickets t
        LEFT JOIN users u ON t.created_by = u.id
        LEFT JOIN users u2 ON t.updated_by = u2.id
        LEFT JOIN users u3 ON t.assigned_to = u3.id
        LEFT JOIN departments d ON t.department_id = d.id
        WHERE t.id = ?
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {

            ticket = new Ticket();

            // =========================
            // BASIC INFO
            // =========================
            ticket.setId(rs.getInt("id"));
            ticket.setTicketNumber(rs.getString("ticket_number"));
            ticket.setTitle(rs.getString("title"));
            ticket.setDescription(rs.getString("description"));
            ticket.setPriority(rs.getString("priority"));
            ticket.setStatus(rs.getString("status"));
            ticket.setTicketType(rs.getString("ticket_type"));

            // =========================
            // CREATED DATE
            // =========================
            Timestamp createdTs = rs.getTimestamp("created_at");
            if (createdTs != null) {
                ticket.setCreatedAt(createdTs.toLocalDateTime().toString());
            }

            // =========================
            // USERS
            // =========================
            ticket.setCreatedBy(rs.getString("created_by_name"));
            ticket.setUpdatedBy(rs.getString("updated_by_name"));

            ticket.setAssignedToName(rs.getString("agent_name"));

            int agentId = rs.getInt("agent_id");
            if (!rs.wasNull()) {
                ticket.setAssignedTo(agentId);
            }

            // =========================
            // DEPARTMENT
            // =========================
            ticket.setDepartmentName(rs.getString("department_name"));
            if (rs.getObject("department_id") != null) {
                ticket.setDepartmentId(rs.getInt("department_id"));
            }

            // =========================
            // START DATE (✅ FIXED SOURCE)
            // =========================
            Timestamp startTs = rs.getTimestamp("started_at");

            if (startTs != null) {
                ticket.setStartedAt(startTs.toLocalDateTime());
            } else if (createdTs != null) {
                ticket.setStartedAt(createdTs.toLocalDateTime());
            }

            // =========================
            // CLOSED DATE
            // =========================
            Timestamp closedTs = rs.getTimestamp("closed_at");

            if (closedTs != null) {
                ticket.setClosedAt(closedTs.toLocalDateTime());
            }

            // =========================
            // RESOLUTION (✅ SAFE)
            // =========================
            int resolution = rs.getInt("resolution_minutes");
            if (!rs.wasNull()) {
                ticket.setResolutionMinutes(resolution);
            }

            // =========================
            // ATTACHMENTS
            // =========================
            ticket.setAttachments(
                TicketDAO.getTicketAttachments(ticketId)
            );
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return ticket;
}



public static void updateTicket(int id,
                                String title,
                                String description,
                                String status,
                                String priority) {

    String sql = """
        UPDATE tickets
        SET title = ?, description = ?, status = ?, priority = ?
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, status);
        ps.setString(4, priority);
        ps.setInt(5, id);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static void updateTicket(int id, String title, String description, String priority) {

    String sql = """
        UPDATE tickets
        SET title = ?, description = ?, priority = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, priority);
        ps.setInt(4, id);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static void updateTicket(int id,
                                String title,
                                String description,
                                String priority,
                                int departmentId,
                                List<File> attachments) {

    String sql = """
        UPDATE tickets
        SET title = ?,
            description = ?,
            priority = ?,
            department_id = ?
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, priority);
        ps.setInt(4, departmentId);
        ps.setInt(5, id);

        ps.executeUpdate();

        // 🔴 DELETE OLD ATTACHMENTS
        String deleteSql = "DELETE FROM ticket_attachments WHERE ticket_id = ?";

        try (PreparedStatement del = c.prepareStatement(deleteSql)) {
            del.setInt(1, id);
            del.executeUpdate();
        }

        // 🟢 SAVE NEW ATTACHMENTS
        if (attachments != null && !attachments.isEmpty()) {
            saveAttachments(id, attachments);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}


public static void updateTicket(int id,
                                String title,
                                String description,
                                String priority,
                                int departmentId,
                                File attachment) {

    String attachmentName = null;
    String attachmentPath = null;
    String attachmentType = null;

    try {

        // ===============================
        // SAVE NEW ATTACHMENT IF PROVIDED
        // ===============================
        if (attachment != null) {

            String fileName =
                    System.currentTimeMillis() + "_" + attachment.getName();

            File dest = new File("uploads/" + fileName);

            Files.copy(
                    attachment.toPath(),
                    dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            attachmentName = attachment.getName();
            attachmentPath = dest.getPath();

            int dot = fileName.lastIndexOf(".");
            if (dot > 0) {
                attachmentType = fileName.substring(dot + 1);
            }
        }

        String sql;

        // ===============================
        // IF NEW FILE
        // ===============================
        if (attachment != null) {

            sql = """
                UPDATE tickets
                SET title = ?,
                    description = ?,
                    priority = ?,
                    department_id = ?,
                    attachment_name = ?,
                    attachment_path = ?,
                    attachment_type = ?
                WHERE id = ?
            """;

        } else {

            sql = """
                UPDATE tickets
                SET title = ?,
                    description = ?,
                    priority = ?,
                    department_id = ?
                WHERE id = ?
            """;
        }

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, priority);
            ps.setInt(4, departmentId);

            if (attachment != null) {

                ps.setString(5, attachmentName);
                ps.setString(6, attachmentPath);
                ps.setString(7, attachmentType);
                ps.setInt(8, id);

            } else {

                ps.setInt(5, id);
            }

            ps.executeUpdate();
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}



private static LocalDateTime toLocalTime(Timestamp ts) {

    if (ts == null) return null;

    return ts.toLocalDateTime();
}


public static void saveAttachments(int ticketId, List<File> files) {

    String sql = """
        INSERT INTO ticket_attachments
        (ticket_id, file_name, file_path, file_type, uploaded_by)
        VALUES (?, ?, ?, ?, ?)
    """;

    try (Connection conn = DB.getConnection()) {

        for (File file : files) {

            String fileName =
                    System.currentTimeMillis() + "_" + file.getName();

            File dest = new File("uploads/" + fileName);

            Files.copy(
                    file.toPath(),
                    dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            String type = "";

            int dot = fileName.lastIndexOf(".");
            if (dot > 0) {
                type = fileName.substring(dot + 1);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, ticketId);
                ps.setString(2, file.getName());
                ps.setString(3, dest.getPath());
                ps.setString(4, type);
                ps.setInt(5, Session.getUserId());

                ps.executeUpdate();
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static List<String> getTicketAttachments(int ticketId) {

    List<String> list = new ArrayList<>();

    String sql = """
        SELECT file_path
        FROM ticket_attachments
        WHERE ticket_id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            list.add(rs.getString("file_path"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static void updateTicketAssignment(int ticketId, int userId, int updatedBy) {

    String deactivate = """
        UPDATE ticket_assignments
        SET active = 0
        WHERE ticket_id = ?
        AND active = 1
    """;

    String updateTicket = """
        UPDATE tickets
        SET assigned_to = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
    """;

    String insert = """
        INSERT INTO ticket_assignments(
            ticket_id,
            agent_id,
            assigned_to,
            assigned_by,
            assigned_at,
            status,
            active
        )
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'ASSIGNED', 1)
    """;

    try (Connection conn = DB.getConnection()) {

        conn.setAutoCommit(false);

        try (PreparedStatement ps = conn.prepareStatement(deactivate)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(updateTicket)) {
            ps.setInt(1, userId);
            ps.setInt(2, updatedBy);
            ps.setInt(3, ticketId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, userId); // compatibility
            ps.setInt(3, userId);
            ps.setInt(4, updatedBy);
            ps.executeUpdate();
        }

        conn.commit();

    } catch (Exception e) {
        e.printStackTrace();
    }
}



public static int countAssignedTicketsByStatus(int agentId, String status) {

    String sql = """
        SELECT DISTINCT t.*
        FROM tickets t
        LEFT JOIN ticket_tasks tt ON t.id = tt.ticket_id
        WHERE 
            t.assigned_to = ?
            OR tt.assigned_to = ?
        ORDER BY t.id DESC;
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, agentId);
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

public static Map<String,Integer> getAgentDailyPerformance(int agentId){

    Map<String,Integer> data = new HashMap<>();

    String sql = """
        SELECT DATE(closed_at) as day,
               COUNT(*) as total
        FROM tickets
        WHERE assigned_to = ?
        AND status = 'CLOSED'
        GROUP BY DATE(closed_at)
        ORDER BY DATE(closed_at)
    """;

    try(Connection conn = DB.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)){

        ps.setInt(1,agentId);

        ResultSet rs = ps.executeQuery();

        while(rs.next()){
            data.put(
                rs.getString("day"),
                rs.getInt("total")
            );
        }

    }catch(Exception e){
        e.printStackTrace();
    }

    return data;
}

public static int countEscalatedTickets(){

    String sql = """
        SELECT COUNT(*)
        FROM tickets
        WHERE status = 'ESCALATED'
    """;

    try(Connection conn = DB.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()){

        if(rs.next()){
            return rs.getInt(1);
        }

    }catch(Exception e){
        e.printStackTrace();
    }

    return 0;
}

public static int countAgentSlaBreaches(int agentId) {

    String sql = """
        SELECT COUNT(*)
        FROM tickets
        WHERE assigned_to = ?
        AND started_at IS NOT NULL
        AND closed_at IS NULL
        AND (
            (strftime('%s','now') - strftime('%s',started_at)) / 60
        ) > (sla_hours * 60)
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, agentId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return 0;
}



public static TreeItem<TicketTask> buildTaskTree(List<TicketTask> tasks) {

    Map<Integer, TreeItem<TicketTask>> map = new HashMap<>();

    TreeItem<TicketTask> root = new TreeItem<>();
    root.setExpanded(true);

    for (TicketTask task : tasks) {
        TreeItem<TicketTask> item = new TreeItem<>(task);
        map.put(task.getId(), item);
    }

    for (TicketTask task : tasks) {

        TreeItem<TicketTask> item = map.get(task.getId());

        if (task.getParentAssignmentId() == null) {
            root.getChildren().add(item);
        } else {
            TreeItem<TicketTask> parent = map.get(task.getParentAssignmentId());
            if (parent != null) {
                parent.getChildren().add(item);
            } else {
                root.getChildren().add(item);
            }
        }
    }

    return root;
}

public static void logEvent(int ticketId, String eventType, String description) {

    try (Connection conn = DB.getConnection()) {

        logEvent(conn, ticketId, eventType, description);

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static void logEvent(Connection c, int ticketId, String type, String description) {

    String sql = """
        INSERT INTO ticket_events(ticket_id, user_id, event_type, comment)
        VALUES (?, ?, ?, ?)
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {

        // ✅ 1. ticket_id
        ps.setInt(1, ticketId);

        // ✅ 2. user_id (CRITICAL FIX)
        int userId = Session.getUserId();
        if (userId <= 0) userId = 1; // fallback

        ps.setInt(2, userId);

        // ✅ 3. event_type
        ps.setString(3, type);

        // ✅ 4. comment
        ps.setString(4, description);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

 
    private static int createTaskInternalReturnId(Connection c,
                                              int ticketId,
                                              Integer parentId,
                                              String title,
                                              String description,
                                              int assignedTo,
                                              int assignedBy) throws SQLException {

    String sql = """
        INSERT INTO ticket_tasks(
            ticket_id,
            parent_assignment_id,
            title,
            description,
            assigned_to,
            assigned_by,
            status,
            created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, 'PENDING', datetime('now','localtime'))
    """;

    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setInt(1, ticketId);

        if (parentId != null)
            ps.setInt(2, parentId);
        else
            ps.setNull(2, Types.INTEGER);

        ps.setString(3, title);
        ps.setString(4, description);
        ps.setInt(5, assignedTo);
        ps.setInt(6, assignedBy);

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();
        return rs.next() ? rs.getInt(1) : 0;
    }
}
    
    // =====================================================
// ASSIGN TICKET TO MULTIPLE USERS
// =====================================================
public static void assignUsersToTicket(int ticketId, List<Integer> userIds) {

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        // 1. deactivate old assignments
        String deactivate = """
            UPDATE ticket_assignments
            SET active = 0
            WHERE ticket_id = ?
        """;

        try (PreparedStatement ps = c.prepareStatement(deactivate)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        // 2. update main ticket (VERY IMPORTANT)
        String updateTicket = """
            UPDATE tickets
            SET status = 'IN_PROGRESS',
                assigned_to = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            ps.setInt(1, userIds.get(0)); // main agent
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }

        // 3. insert assignments
        String insert = """
            INSERT INTO ticket_assignments(
                ticket_id,
                agent_id,
                assigned_to,
                assigned_by,
                assigned_at,
                status,
                active
            )
            VALUES (?, ?, ?, ?, datetime('now','localtime'), 'IN_PROGRESS', 1)
        """;

        for (Integer userId : userIds) {

            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setInt(1, ticketId);
                ps.setInt(2, userId);
                ps.setInt(3, userId);
                ps.setInt(4, Session.getUserId());
                ps.executeUpdate();
            }

            System.out.println("Assigned userId: " + userId);
        }

        c.commit();

        System.out.println("✅ Multi-assign completed for ticket: " + ticketId);

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
 public static ObservableList<User> getAssignedUsers(int ticketId) {

    ObservableList<User> list = FXCollections.observableArrayList();

    String sql = """
        SELECT u.id, u.username
        FROM ticket_assignments ta
        JOIN users u ON ta.assigned_to = u.id
        WHERE ta.ticket_id = ?
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            list.add(u);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}   

 
 public static String getAssignedUsersNames(int ticketId) {

    String sql = """
        SELECT u.username
        FROM ticket_assignments ta
        JOIN users u ON ta.assigned_to = u.id
        WHERE ta.ticket_id = ?
    """;

    List<String> names = new ArrayList<>();

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            names.add(rs.getString("username"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return String.join(", ", names);
}

public static int createTaskAndReturnId(int ticketId, String title, int userId, String description) {

    String sql = """
        INSERT INTO ticket_tasks(ticket_id, title, assigned_to, assigned_by, description, status)
        VALUES (?, ?, ?, ?, ?, 'PENDING')
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setInt(1, ticketId);
        ps.setString(2, title);
        ps.setInt(3, userId);
        ps.setInt(4, Session.getUserId());
        ps.setString(5, description);

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();
        if (rs.next()) {
            return rs.getInt(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return -1;
}

public static boolean isUserCurrentOwner(int ticketId, int userId) {

    String sql = """
        SELECT 1
        FROM ticket_assignments
        WHERE ticket_id = ?
        AND assigned_to = ?
        AND active = 1
        LIMIT 1
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setInt(2, userId);

        ResultSet rs = ps.executeQuery();

        return rs.next(); // TRUE = owner

    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
}

public static String getCurrentOwnerName(int ticketId) {

    String sql = """
        SELECT u.username
        FROM ticket_assignments ta
        JOIN users u ON u.id = ta.assigned_to
        WHERE ta.ticket_id = ?
        AND ta.active = 1
        LIMIT 1
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getString("username");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return "Unassigned";
}

    
public static ObservableList<Ticket> getWorkQueue(int userId) {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT DISTINCT t.*
        FROM tickets t
        JOIN ticket_assignments ta ON t.id = ta.ticket_id
        WHERE ta.assigned_to = ?
        AND ta.active = 1
        ORDER BY t.updated_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            list.add(mapTicket(rs));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

private static Ticket mapTicket(ResultSet rs) throws Exception {

    Ticket t = new Ticket();

    t.setId(rs.getInt("id"));
    t.setTitle(rs.getString("title"));
    t.setDescription(rs.getString("description"));
    t.setPriority(rs.getString("priority"));
    t.setStatus(rs.getString("status"));
    t.setTicketType(rs.getString("ticket_type"));

    t.setAssignedTo(rs.getInt("assigned_to"));

    t.setCreatedAt(rs.getString("created_at"));
    t.setUpdatedAt(rs.getString("updated_at"));

    // Optional fields (safe check)
    try {
        t.setStartedAt(rs.getTimestamp("started_at") != null
                ? rs.getTimestamp("started_at").toLocalDateTime()
                : null);
    } catch (Exception ignored) {}

    try {
        t.setClosedAt(rs.getTimestamp("closed_at") != null
                ? rs.getTimestamp("closed_at").toLocalDateTime()
                : null);
    } catch (Exception ignored) {}

    try {
        t.setResolutionMinutes(rs.getInt("resolution_minutes"));
    } catch (Exception ignored) {}

    return t;
}



public static List<TicketEvent> getAllTaskEventsByTicket(int ticketId) {

    List<TicketEvent> list = new ArrayList<>();

    String sql = """
        SELECT 
            te.event_type,
            te.description,
            te.created_at,
            u.username
        FROM task_events te
        JOIN ticket_tasks t ON te.task_id = t.id
        LEFT JOIN users u ON te.user_id = u.id
        WHERE t.ticket_id = ?
        ORDER BY te.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketEvent ev = new TicketEvent();

            ev.setUsername(rs.getString("username"));
            ev.setType(rs.getString("event_type"));   // 🔥 IMPORTANT
            ev.setDescription(rs.getString("description"));
            ev.setCreatedAt(rs.getString("created_at"));

            list.add(ev);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}



public static ObservableList<Ticket> getAssignedTicketsByUser(int userId) {

    ObservableList<Ticket> list = FXCollections.observableArrayList();

    String sql = """
        SELECT 
            id,
            title,
            status,
            priority,
            started_at,
            closed_at,
            resolution_minutes
        FROM tickets
        WHERE assigned_to = ?
        ORDER BY created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Ticket t = new Ticket();

            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setPriority(rs.getString("priority"));

            // ✅ START
            Timestamp startTs = rs.getTimestamp("started_at");
            if (startTs != null) {
                t.setStartedAt(startTs.toLocalDateTime());
            }

            // ✅ CLOSE
            Timestamp closeTs = rs.getTimestamp("closed_at");
            if (closeTs != null) {
                t.setClosedAt(closeTs.toLocalDateTime());
            }

            // ✅ RESOLUTION
            int res = rs.getInt("resolution_minutes");
            if (!rs.wasNull()) {
                t.setResolutionMinutes(res);
            }

            list.add(t);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static void startTicket(int ticketId) {

    String sql = """
        UPDATE tickets
        SET status = 'IN_PROGRESS',
            started_at = datetime('now','localtime'),
            updated_by = ?,
            updated_at = datetime('now','localtime')
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, Session.getUserId());
        ps.setInt(2, ticketId);

        int updated = ps.executeUpdate();

        if (updated == 0) {
            System.out.println("❌ Ticket not updated");
            return;
        }

        // 🔥 SAFE LOG (will not crash anymore)
        logEvent(c, ticketId, "STARTED",
                "Ticket started by " + Session.getUsername());

    } catch (Exception e) {
        e.printStackTrace();
    }
}
   
public static void escalateTicket(int ticketId,
                                  int fromUserId,
                                  int toUserId,
                                  String reason) {

    String updateAssignment = """
        UPDATE ticket_assignments
        SET active = 0
        WHERE ticket_id = ? AND active = 1
    """;

    String insertAssignment = """
        INSERT INTO ticket_assignments
        (ticket_id, assigned_to, assigned_by, status, active, started_at)
        VALUES (?, ?, ?, 'ASSIGNED', 1, datetime('now'))
    """;

    String insertEscalation = """
        INSERT INTO ticket_escalations
        (ticket_id, escalated_by, escalated_from, escalated_to, reason, escalated_at)
        VALUES (?, ?, ?, ?, ?, datetime('now'))
    """;

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        // 🔥 CLOSE CURRENT ASSIGNMENT
        try (PreparedStatement ps = c.prepareStatement(updateAssignment)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        // 🔥 CREATE NEW ASSIGNMENT
        try (PreparedStatement ps = c.prepareStatement(insertAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, toUserId);
            ps.setInt(3, fromUserId);
            ps.executeUpdate();
        }

        // 🔥 LOG ESCALATION
        try (PreparedStatement ps = c.prepareStatement(insertEscalation)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, fromUserId);
            ps.setInt(3, fromUserId);
            ps.setInt(4, toUserId);
            ps.setString(5, reason);
            ps.executeUpdate();
        }

        // 🔥 UPDATE TICKET STATUS
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE tickets SET status='ESCALATED' WHERE id=?")) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }
if (fromUserId == toUserId) {
    throw new RuntimeException("Cannot escalate to the same user.");
}
        
        c.commit();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


public static void reassignTask(int taskId, int newUserId) {

    String sql = """
        UPDATE ticket_tasks
        SET assigned_to = ?
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, newUserId);
        ps.setInt(2, taskId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


    
}





