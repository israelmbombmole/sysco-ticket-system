package com.app.dao;

import com.app.auth.Session;
import com.app.controller.AdminDashboardController.AgentRow;
import com.app.model.CourierPacket;
import com.app.model.Department;
import com.app.model.ExternalEscalation;
import com.app.model.Ticket;
import com.app.model.TicketEvent;
import com.app.model.TicketTask;
import com.app.model.User;
import com.app.service.EmailService;
import static com.app.service.SLAMonitorService.countSlaBreaches;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.sql.*;
import com.app.util.BusinessRuleException;
import com.app.util.DB;
import com.app.util.DbConfig;
import com.app.util.I18n;
import com.app.util.SqlDialect;
import com.app.util.TicketUtil;
import com.sun.source.util.TaskEvent;
import java.io.File;
import java.text.Normalizer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TreeItem;

public class TicketDAO {

    private static void assertUserNotOnLeaveForTicketAssignment(int userId) {
        if (userId <= 0) {
            return;
        }
        if (UserAbsenceDAO.isUserAbsentOn(userId, LocalDate.now())) {
            throw new BusinessRuleException(I18n.t("ticket.assign.targetOnLeave",
                    "This user is on leave or holiday and cannot receive ticket assignments."));
        }
    }

    /**
     * For DIRECTEUR / SOUS-DIRECTEUR: determine which {@code departments.id} belongs
     * to the user's assigned {@code directions.id}.
     *
     * Returns:
     * - a valid department id if a match is found
     * - -1 if the user has no direction (or no match found) -> meaning "see nothing"
     * - null if the current role is not DIRECTEUR / SOUS-DIRECTEUR -> no department scoping
     */
    public static Integer getAllowedDepartmentIdForCurrentUser() {
        String role = Session.getRole();
        if (role == null) return null;

        boolean isDirecteur = "DIRECTEUR".equalsIgnoreCase(role);
        boolean isSousDirecteur = "SOUS-DIRECTEUR".equalsIgnoreCase(role);
        if (!isDirecteur && !isSousDirecteur) return null;

        String directionName = null;

        String dirSql = """
            SELECT dir.name
            FROM users u
            LEFT JOIN directions dir ON u.direction_id = dir.id
            WHERE u.id = ?
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(dirSql)) {

            ps.setInt(1, Session.getUserId());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                directionName = rs.getString("name");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (directionName == null || directionName.isBlank()) {
            return -1;
        }

        String target = normalizeForCompare(directionName);
        if (target.isBlank()) {
            return -1;
        }

        int bestId = -1;
        int bestScore = 0;

        String deptSql = "SELECT id, name FROM departments";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(deptSql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int deptId = rs.getInt("id");
                String deptName = rs.getString("name");
                String nDept = normalizeForCompare(deptName);

                if (nDept.isBlank()) continue;

                boolean matches =
                        nDept.equals(target)
                        || nDept.contains(target)
                        || target.contains(nDept);

                if (!matches) continue;

                // prefer the most specific match (longest normalized dept name)
                int score = nDept.length();
                if (score > bestScore) {
                    bestScore = score;
                    bestId = deptId;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bestId;
    }

    private static String normalizeForCompare(String s) {
        if (s == null) return "";

        String out = s;
        out = out.replace('’', '\'');

        // Remove diacritics
        out = Normalizer.normalize(out, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        // Remove parenthesis segments (e.g. acronyms) for comparison
        out = out.replaceAll("\\([^)]*\\)", " ");

        // Normalize common French pattern differences: "de l'X" -> "d'X"
        out = out.replaceAll("(?i)\\bde l'","d'");
        out = out.replaceAll("(?i)\\bde l ","d ");

        // Collapse whitespace and lowercase
        out = out.replaceAll("\\s+", " ").trim().toLowerCase();

        return out;
    }

    private static boolean isDirectionScopedRole(String role) {
        return role != null
                && ("DIRECTEUR".equalsIgnoreCase(role) || "SOUS-DIRECTEUR".equalsIgnoreCase(role));
    }

    /**
     * Only users with role {@code ADMIN} see all tickets and all directions (full system dashboard / monitoring).
     * Directors and other internal roles are limited to their {@code direction_id}.
     */
    public static boolean hasOrgWideTicketAccess() {
        return com.app.util.AccessContext.isSystemSuperAdmin();
    }

    /**
     * When {@code true}, ticket aggregates and lists are filtered to the session user's direction.
     * {@code false} for {@link #hasOrgWideTicketAccess()} (full org) and for {@code EXTERNAL} (not direction-based).
     */
    public static boolean useDirectionScopeForTicketAggregates() {
        if (hasOrgWideTicketAccess()) {
            return false;
        }
        String role = Session.getRole();
        if (role != null && "EXTERNAL".equalsIgnoreCase(role.trim())) {
            return false;
        }
        return true;
    }

    public static Integer getCurrentUserDirectionId() {
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

    // =====================================================
    // GET TICKETS FOR AGENT
    // =====================================================

   public static List<Ticket> getTicketsForUser(int userId) {

    List<Ticket> list = new ArrayList<>();

    String role = Session.getRole();
    boolean orgWide = com.app.util.AccessContext.isSystemSuperAdmin();
    boolean restrictToDirection = isDirectionScopedRole(role)
            || (role != null && "ADMIN".equalsIgnoreCase(role) && !com.app.util.AccessContext.isSystemSuperAdmin());
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

    String sql = orgWide
            ? """
        SELECT
            t.id,
            t.title,
            t.status,
            u.username AS assigned_to_name
        FROM tickets t
        LEFT JOIN users u ON t.assigned_to = u.id
        WHERE t.merged_into IS NULL
          AND t.status <> 'MERGED'
        ORDER BY t.created_at DESC
        """
            : restrictToDirection
            ? """
        SELECT
            t.id,
            t.title,
            t.status,
            u.username AS assigned_to_name
        FROM tickets t
        LEFT JOIN users u ON t.assigned_to = u.id
        JOIN users creator ON creator.id = t.created_by
        WHERE t.merged_into IS NULL
          AND t.status <> 'MERGED'
          AND (creator.direction_id = ? OR u.direction_id = ?)
        ORDER BY t.created_at DESC
        """
            : """
        SELECT
            t.id,
            t.title,
            t.status,
            u.username AS assigned_to_name
        FROM tickets t
        LEFT JOIN users u ON t.assigned_to = u.id
        WHERE t.assigned_to = ?
          AND t.merged_into IS NULL
          AND t.status <> 'MERGED'
        ORDER BY t.created_at DESC
        """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        if (orgWide) {
            // no filter needed
        } else if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return list;
            }
            ps.setInt(1, currentDirectionId);
            ps.setInt(2, currentDirectionId);
        } else {
            ps.setInt(1, userId);
        }

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            Ticket t = new Ticket();
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setAssignedToName(rs.getString("assigned_to_name"));

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
         PreparedStatement ps = DbConfig.isOracle()
                 ? conn.prepareStatement(sql, new String[] { "ID" })
                 : conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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
    Number generatedId = (Number) rs.getObject(1);
    if (generatedId == null) {
        throw new SQLException("Creating external ticket failed: no numeric generated ID.");
    }
    int ticketId = generatedId.intValue();

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

    if (!isTicketInCurrentUserDirection(ticketId)) {
        throw new RuntimeException("You cannot assign a ticket outside your direction.");
    }

    User targetUser = UserDAO.findById(assignedToUserId);

    if (targetUser == null) {
        throw new RuntimeException("Assigned user not found.");
    }

    String fromRole = Session.getRole();
    String toRole = targetUser.getRole();

    if (!com.app.util.RoleFlowUtil.canAssign(fromRole, toRole)) {
        throw new RuntimeException("Assignment not allowed: " + fromRole + " → " + toRole);
    }

    assertUserNotOnLeaveForTicketAssignment(assignedToUserId);

    String updateTicket = """
        UPDATE tickets
        SET assigned_to = ?, 
            status = 'ASSIGNED',
            updated_by = ?, 
            updated_at = datetime('now','localtime')
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

        // 1. deactivate old assignments
        try (PreparedStatement ps = c.prepareStatement(deactivateOldAssignments)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }

        // 2. update ticket
        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            ps.setInt(1, assignedToUserId);
            ps.setInt(2, Session.getUserId());
            ps.setInt(3, ticketId);
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

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException("Assignment failed: " + e.getMessage());
    }

    NotificationDAO.create(
            assignedToUserId,
            "Ticket Assigned",
            "You have been assigned ticket " + ref,
            "TICKET_ASSIGNED",
            "TICKET",
            ticketId,
            ref
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

    for (Integer userId : assignedUserIds) {
        User targetUser = UserDAO.findById(userId);
        if (targetUser == null) {
            throw new RuntimeException("Assigned user not found.");
        }
        String toRole = targetUser.getRole();
        if (!com.app.util.RoleFlowUtil.canAssign(fromRole, toRole)) {
            throw new RuntimeException("Multi-assignment not allowed: " + fromRole + " → " + toRole);
        }
        assertUserNotOnLeaveForTicketAssignment(userId);
    }

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

    List<Integer> notificationRecipients = new ArrayList<>();

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

            int assignmentId;

            try (PreparedStatement ps = DbConfig.isOracle()
                    ? c.prepareStatement(insertAssignment, new String[] { "ID" })
                    : c.prepareStatement(insertAssignment, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, ticketId);
                ps.setInt(2, userId);
                ps.setInt(3, userId);
                ps.setInt(4, Session.getUserId());
                ps.setString(5, fromRole);
                ps.setString(6, toRole);
                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                rs.next();
                Number generatedId = (Number) rs.getObject(1);
                if (generatedId == null) {
                    throw new SQLException("Creating assignment failed: no numeric generated ID.");
                }
                assignmentId = generatedId.intValue();
            }

            notificationRecipients.add(userId);
        }

        c.commit();

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage(), e);
    }

    // Insert notifications after transaction commit to avoid SQLITE_BUSY on SQLite writer lock.
    for (Integer userId : notificationRecipients) {
        try {
            NotificationDAO.create(
                    userId,
                    "Ticket Assigned",
                    "You have been assigned ticket " + TicketUtil.formatTicketRef(ticketId),
                    "TICKET_ASSIGNED",
                    "TICKET",
                    ticketId,
                    TicketUtil.formatTicketRef(ticketId)
            );
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
    
   
   private static int createTaskInternal(Connection c,
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

    try (PreparedStatement ps = DbConfig.isOracle()
            ? c.prepareStatement(sql, new String[] { "ID" })
            : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
        ResultSet rs = ps.getGeneratedKeys();
        if (rs.next()) {
            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Creating task failed: no numeric generated ID.");
            }
            return generatedId.intValue();
        }
        return -1;
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

    boolean restrictToDirection = useDirectionScopeForTicketAggregates();
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

    String sql = restrictToDirection
            ? """
        SELECT t.*
        FROM tickets t
        LEFT JOIN ticket_assignments ta 
            ON t.id = ta.ticket_id AND ta.active = 1
        JOIN users creator
            ON creator.id = t.created_by
        WHERE ta.id IS NULL
          AND UPPER(COALESCE(t.status, '')) = 'OPEN'
          AND creator.direction_id = ?
        ORDER BY t.created_at DESC
    """
            : """
        SELECT t.*
        FROM tickets t
        LEFT JOIN ticket_assignments ta 
            ON t.id = ta.ticket_id AND ta.active = 1
        WHERE ta.id IS NULL
          AND UPPER(COALESCE(t.status, '')) = 'OPEN'
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return list;
            }
            ps.setInt(1, currentDirectionId);
        }

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Ticket t = new Ticket();
                t.setId(rs.getInt("id"));
                t.setTitle(rs.getString("title"));
                t.setStatus(rs.getString("status"));
                t.setTicketType(rs.getString("ticket_type"));

                list.add(t);
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    mergeUnassignedInternalTicketsLinkedFromCourierForSousDirecteur(list);
    return list;
}

    /**
     * Internal tickets created from courrier (see {@link #createInternalFromCourier}) are OPEN and
     * unassigned, but the main unassigned list filters by the ticket <em>creator</em>&apos;s direction.
     * Sous-directeurs must still see courriers routed to their {@code sous_direction} in
     * &quot;Assigner un ticket&quot;, so we merge those rows by
     * {@code courier_packets.target_sous_direction_id}.
     */
    private static void mergeUnassignedInternalTicketsLinkedFromCourierForSousDirecteur(
            ObservableList<Ticket> list) {
        String role = Session.getRole();
        if (role == null || !"SOUS-DIRECTEUR".equalsIgnoreCase(role.trim())) {
            return;
        }
        Integer[] dirSous = UserDAO.getDirectionSousForUser(Session.getUserId());
        if (dirSous == null || dirSous.length < 2) {
            return;
        }
        Integer sousId = dirSous[1];
        if (sousId == null || sousId <= 0) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Ticket t : list) {
            if (t != null && t.getId() > 0) {
                seen.add(t.getId());
            }
        }
        String sql = """
                SELECT t.*
                FROM tickets t
                INNER JOIN courier_packets cp ON cp.linked_ticket_id = t.id
                LEFT JOIN ticket_assignments ta ON t.id = ta.ticket_id AND ta.active = 1
                WHERE ta.id IS NULL
                  AND UPPER(COALESCE(t.status, '')) = 'OPEN'
                  AND cp.target_sous_direction_id = ?
                ORDER BY t.created_at DESC
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sousId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (seen.add(id)) {
                        Ticket t = new Ticket();
                        t.setId(id);
                        t.setTitle(rs.getString("title"));
                        t.setStatus(rs.getString("status"));
                        t.setTicketType(rs.getString("ticket_type"));
                        list.add(t);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    Integer assignerIdForNotice = null;
    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);
        assignerIdForNotice = getActiveAssignerId(c, ticketId, userId);

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

    if (assignerIdForNotice != null && assignerIdForNotice > 0 && assignerIdForNotice != userId) {
        NotificationDAO.create(
                assignerIdForNotice,
                "Ticket Started",
                Session.getUsername() + " started working on ticket " + TicketUtil.formatTicketRef(ticketId),
                "TICKET_STARTED",
                "TICKET",
                ticketId,
                TicketUtil.formatTicketRef(ticketId)
        );
    }
}

    // =====================================================
    // CLOSE TICKET
    // =====================================================

    public static void closeTicket(int ticketId) {

    int userId = Session.getUserId();
    String role = Session.getRole();
    boolean isVerificateur = "VERIFICATEUR".equalsIgnoreCase(role)
            || "VERIFICATEUR-ASSISTANT".equalsIgnoreCase(role);
    boolean hasPendingApproval = TicketCloseRequestDAO.hasPendingRequestForApprover(ticketId, userId);

    if (useDirectionScopeForTicketAggregates() && !isTicketInCurrentUserDirection(ticketId) && !hasPendingApproval) {
        throw new RuntimeException("You cannot close a ticket outside your direction.");
    }

    if (isVerificateur) {
        if (!isUserCurrentOwner(ticketId, userId)) {
            throw new RuntimeException("You can close only tickets assigned to you.");
        }
        if (hasTasks(ticketId)) {
            throw new RuntimeException("Direct close is not allowed when tasks exist. Please request close escalation.");
        }
    }

    if (!com.app.util.RoleFlowUtil.canCloseTicket(role) && !isVerificateur) {
        throw new RuntimeException("You are not allowed to close tickets.");
    }

    // CONTROLEUR closes own tickets, unless the ticket was escalated to them for closure.
    if ("CONTROLEUR".equalsIgnoreCase(role)) {
        boolean isOwner = isUserCurrentOwner(ticketId, userId);
        if (!isOwner && !hasPendingApproval) {
            throw new RuntimeException("You can close only tickets assigned to you.");
        }
    }

    String checkTicket = """
        SELECT status
        FROM tickets
        WHERE id = ?
    """;

    String closeAssignments =
        "UPDATE ticket_assignments\n" +
        "SET status = 'CLOSED',\n" +
        "    closed_at = datetime('now','localtime'),\n" +
        "    duration_minutes =\n" +
        "        CASE\n" +
        "            WHEN COALESCE(started_at, assigned_at) IS NOT NULL\n" +
        "            THEN " + SqlDialect.minutesBetweenNowAnd("COALESCE(started_at, assigned_at)") + "\n" +
        "            ELSE duration_minutes\n" +
        "        END,\n" +
        "    active = 0\n" +
        "WHERE ticket_id = ?\n" +
        "  AND active = 1";

    String closeTicketSql =
        "UPDATE tickets\n" +
        "SET status = 'CLOSED',\n" +
        "    closed_at = datetime('now','localtime'),\n" +
        "    resolution_minutes =\n" +
        "        CASE\n" +
        "            WHEN COALESCE(started_at, created_at) IS NOT NULL\n" +
        "            THEN " + SqlDialect.minutesBetweenNowAnd("COALESCE(started_at, created_at)") + "\n" +
        "            ELSE resolution_minutes\n" +
        "        END,\n" +
        "    updated_by = ?,\n" +
        "    updated_at = datetime('now','localtime')\n" +
        "WHERE id = ?";

    Integer assignerIdForNotice = null;
    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);
        assignerIdForNotice = getActiveAssignerId(c, ticketId, userId);

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

    if (assignerIdForNotice != null && assignerIdForNotice > 0 && assignerIdForNotice != userId) {
        NotificationDAO.create(
                assignerIdForNotice,
                "Ticket Completed",
                Session.getUsername() + " completed ticket " + TicketUtil.formatTicketRef(ticketId),
                "TICKET_COMPLETED",
                "TICKET",
                ticketId,
                TicketUtil.formatTicketRef(ticketId)
        );
    }
}

public static void requestCloseEscalation(int ticketId, int requestedToUserId, String reason) {
    int requesterId = Session.getUserId();

    if (!isUserCurrentOwner(ticketId, requesterId)) {
        throw new RuntimeException("You can request close only for tickets assigned to you.");
    }
    if (!hasTasks(ticketId)) {
        throw new RuntimeException("Escalation request is required only when tasks exist.");
    }

    User approver = UserDAO.findById(requestedToUserId);
    if (approver == null || !approver.isActive()) {
        throw new RuntimeException("Selected approver is not available.");
    }

    String ticketRef = TicketUtil.formatTicketRef(ticketId);
    String note = (reason == null || reason.isBlank()) ? "Close escalation requested" : reason.trim();
    String message = Session.getUsername() + " requested close approval for ticket " + ticketRef + ". " + note;

    if (approver.getId() == requesterId) {
        throw new RuntimeException("You cannot request close approval from yourself.");
    }

    int requestId = TicketCloseRequestDAO.createRequest(ticketId, approver.getId(), note);
    NotificationDAO.create(
            approver.getId(),
            "Close Request",
            message,
            "TICKET_CLOSE_REQUEST",
            "CLOSE_REQUEST",
            requestId,
            ticketRef
    );
}

    
    
public static int countAll() {

    if (hasOrgWideTicketAccess()) {
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

    if (!useDirectionScopeForTicketAggregates()) {
        String sql = """
                SELECT COUNT(*) FROM tickets
                WHERE merged_into IS NULL AND COALESCE(UPPER(status),'') <> 'MERGED'
                """;
        try (var conn = DB.getConnection();
             var stmt = conn.prepareStatement(sql);
             var rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    Integer dirId = getCurrentUserDirectionId();
    if (dirId == null || dirId <= 0) {
        return 0;
    }

    String sql = """
            SELECT COUNT(*)
            FROM tickets t
            JOIN users creator ON creator.id = t.created_by
            LEFT JOIN users assignee ON assignee.id = t.assigned_to
            WHERE t.merged_into IS NULL
              AND COALESCE(UPPER(t.status),'') <> 'MERGED'
              AND (creator.direction_id = ? OR assignee.direction_id = ?)
            """;

    try (var conn = DB.getConnection();
         var stmt = conn.prepareStatement(sql)) {
        stmt.setInt(1, dirId);
        stmt.setInt(2, dirId);
        var rs = stmt.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}




 public static int countByStatus(String status) {

    boolean restrictToDirection = useDirectionScopeForTicketAggregates();
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

    String sql = restrictToDirection
            ? """
        SELECT COUNT(*)
        FROM tickets t
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE t.status = ?
          AND (creator.direction_id = ? OR assignee.direction_id = ?)
    """
            : "SELECT COUNT(*) FROM tickets WHERE status = ?";

    try (var conn = DB.getConnection();
         var stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, status);
        if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return 0;
            }
            stmt.setInt(2, currentDirectionId);
            stmt.setInt(3, currentDirectionId);
        }

        var rs = stmt.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}
 
 public static List<Ticket> findLast5() {

    List<Ticket> list = new ArrayList<>();

    boolean scope = useDirectionScopeForTicketAggregates();
    Integer dirId = scope ? getCurrentUserDirectionId() : null;
    if (scope && (dirId == null || dirId <= 0)) {
        return list;
    }

    String sql = hasOrgWideTicketAccess()
            ? """
            SELECT * FROM tickets
            ORDER BY id DESC
            LIMIT 10
            """
            : scope
            ? """
            SELECT t.*
            FROM tickets t
            JOIN users creator ON creator.id = t.created_by
            LEFT JOIN users assignee ON assignee.id = t.assigned_to
            WHERE t.merged_into IS NULL
              AND COALESCE(UPPER(t.status),'') <> 'MERGED'
              AND (creator.direction_id = ? OR assignee.direction_id = ?)
            ORDER BY t.id DESC
            LIMIT 10
            """
            : """
            SELECT * FROM tickets
            WHERE merged_into IS NULL AND COALESCE(UPPER(status),'') <> 'MERGED'
            ORDER BY id DESC
            LIMIT 10
            """;

    try (var conn = DB.getConnection();
         var stmt = conn.prepareStatement(sql)) {

        if (scope) {
            stmt.setInt(1, dirId);
            stmt.setInt(2, dirId);
        }

        try (var rs = stmt.executeQuery()) {
            while (rs.next()) {
                Ticket t = new Ticket();
                t.setId(rs.getInt("id"));
                t.setTitle(rs.getString("title"));
                t.setStatus(rs.getString("status"));
                Timestamp created = rs.getTimestamp("created_at");
                t.setCreatedAt(toLocalTime(created).toString());
                list.add(t);
            }
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

    boolean scope = useDirectionScopeForTicketAggregates();
    Integer dirId = scope ? getCurrentUserDirectionId() : null;
    if (scope && (dirId == null || dirId <= 0)) {
        return 0;
    }

    String sql = scope
            ? """
        SELECT COUNT(*)
        FROM ticket_assignments ta
        JOIN tickets t ON t.id = ta.ticket_id
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE ta.assigned_to = ?
          AND (creator.direction_id = ? OR assignee.direction_id = ?)
    """
            : """
        SELECT COUNT(*)
        FROM ticket_assignments
        WHERE assigned_to = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        if (scope) {
            ps.setInt(2, dirId);
            ps.setInt(3, dirId);
        }
        ResultSet rs = ps.executeQuery();

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}

    /** Rows for agent dashboard total tile ({@link #countTicketsByUserAssignments(int)}). */
    public static List<Ticket> listTicketsForUserAssignmentsDashboard(int userId) {
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        String sql = scope
                ? """
                SELECT DISTINCT t.*, assignee.username AS assigned_to_name
                FROM ticket_assignments ta
                JOIN tickets t ON t.id = ta.ticket_id
                JOIN users creator ON creator.id = t.created_by
                LEFT JOIN users assignee ON assignee.id = t.assigned_to
                WHERE ta.assigned_to = ?
                  AND (creator.direction_id = ? OR assignee.direction_id = ?)
                ORDER BY t.id DESC
                LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT
                : """
                SELECT DISTINCT t.*, assignee.username AS assigned_to_name
                FROM ticket_assignments ta
                JOIN tickets t ON t.id = ta.ticket_id
                LEFT JOIN users assignee ON assignee.id = t.assigned_to
                WHERE ta.assigned_to = ?
                ORDER BY t.id DESC
                LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
        return queryDashboardTickets(sql, ps -> {
            ps.setInt(1, userId);
            if (scope) {
                ps.setInt(2, dirId);
                ps.setInt(3, dirId);
            }
        });
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
        INSERT INTO ticket_comments(ticket_id,user_id,"comment",image_path,created_at)
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
        SELECT tc."comment" AS comment_text,
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
            event.setDescription(rs.getString("comment_text"));
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

    String sql =
        "SELECT\n" +
        "    (" + SqlDialect.minutesBetweenNowAnd("created_at") + " / 60.0) > sla_hours\n" +
        "FROM tickets\n" +
        "WHERE id = ?";

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

    boolean restrictToDirection = useDirectionScopeForTicketAggregates();
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

    String sql = restrictToDirection
            ? """
        SELECT 
            t.id,
            t.title,
            t.status,
            t.priority,
            t.assigned_to,
            t.started_at,
            t.closed_at,
            t.resolution_minutes
        FROM tickets t
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE t.merged_into IS NULL
          AND t.status <> 'MERGED'
          AND (creator.direction_id = ? OR assignee.direction_id = ?)
        ORDER BY created_at DESC
        """
            : """
        SELECT 
            id,
            title,
            status,
            priority,
            assigned_to,
            started_at,
            closed_at,
            resolution_minutes
        FROM tickets
        WHERE merged_into IS NULL
          AND status <> 'MERGED'
        ORDER BY created_at DESC
        """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return list;
            }
            ps.setInt(1, currentDirectionId);
            ps.setInt(2, currentDirectionId);
        }

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
            Number assignedTo = (Number) rs.getObject("assigned_to");
            t.setAssignedTo(assignedTo == null ? null : assignedTo.intValue());

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
    mergeTicketsBulk(masterId, java.util.List.of(mergedId));
}

public static void mergeTicketsBulk(int masterId, List<Integer> mergedIds) {
    if (mergedIds == null || mergedIds.isEmpty()) {
        throw new RuntimeException("Select at least one ticket to merge.");
    }
    if (mergedIds.contains(masterId)) {
        throw new RuntimeException("Cannot merge ticket into itself.");
    }

    String selectTicketSql = "SELECT description, status FROM tickets WHERE id = ?";
    String updateMasterDescSql = "UPDATE tickets SET description=?, updated_by=?, updated_at=datetime('now','localtime') WHERE id=?";
    String updateMergedSql = """
        UPDATE tickets
        SET status='MERGED',
            merged_into=?,
            merge_note=?,
            assigned_to=NULL,
            updated_by=?,
            updated_at=datetime('now','localtime')
        WHERE id=?
    """;

    try (Connection c = DB.getConnection()) {
        c.setAutoCommit(false);

        String masterDescription = "";
        try (PreparedStatement ps = c.prepareStatement(selectTicketSql)) {
            ps.setInt(1, masterId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Master ticket not found.");
            }
            String st = rs.getString("status");
            if ("MERGED".equalsIgnoreCase(st)) {
                throw new RuntimeException("Master ticket is already merged.");
            }
            masterDescription = rs.getString("description");
            if (masterDescription == null) masterDescription = "";
        }

        List<Integer> effectiveMerged = new ArrayList<>();
        for (Integer mergedId : mergedIds) {
            if (mergedId == null || mergedId <= 0) continue;
            if (mergedId == masterId) continue;

            String mergedDescription = "";
            try (PreparedStatement ps = c.prepareStatement(selectTicketSql)) {
                ps.setInt(1, mergedId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    continue;
                }
                String status = rs.getString("status");
                if ("MERGED".equalsIgnoreCase(status)) {
                    continue;
                }
                mergedDescription = rs.getString("description");
                if (mergedDescription == null) mergedDescription = "";
            }

            String mergedRef = TicketUtil.formatTicketRef(mergedId);
            masterDescription = masterDescription
                    + "\n\n--- Merged from " + mergedRef + " ---\n"
                    + mergedDescription;

            // Move all related entities into master ticket.
            String[] moveSql = new String[]{
                    "UPDATE ticket_attachments SET ticket_id=? WHERE ticket_id=?",
                    "UPDATE ticket_tasks SET ticket_id=? WHERE ticket_id=?",
                    "UPDATE ticket_comments SET ticket_id=? WHERE ticket_id=?",
                    "UPDATE ticket_events SET ticket_id=? WHERE ticket_id=?",
                    "UPDATE ticket_assignments SET ticket_id=?, active=0 WHERE ticket_id=?",
                    "UPDATE ticket_history SET ticket_id=? WHERE ticket_id=?",
                    "UPDATE ticket_close_requests SET ticket_id=? WHERE ticket_id=?",
                    "UPDATE ticket_external_escalations SET ticket_id=? WHERE ticket_id=?"
            };
            for (String sql : moveSql) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, masterId);
                    ps.setInt(2, mergedId);
                    ps.executeUpdate();
                } catch (Exception ignored) {
                    // Some installations may not have all optional tables/columns populated.
                }
            }

            String mergeMessage = "Merged into " + TicketUtil.formatTicketRef(masterId) + " by " + Session.getUsername();
            try (PreparedStatement ps = c.prepareStatement(updateMergedSql)) {
                ps.setInt(1, masterId);
                ps.setString(2, mergeMessage);
                ps.setInt(3, Session.getUserId());
                ps.setInt(4, mergedId);
                ps.executeUpdate();
            }
            effectiveMerged.add(mergedId);
        }

        if (effectiveMerged.isEmpty()) {
            throw new RuntimeException("No valid tickets selected for merge.");
        }

        try (PreparedStatement ps = c.prepareStatement(updateMasterDescSql)) {
            ps.setString(1, masterDescription);
            ps.setInt(2, Session.getUserId());
            ps.setInt(3, masterId);
            ps.executeUpdate();
        }

        String mergedNumber = "TCKMGD-" + java.time.Year.now().getValue() + "-" + String.format("%03d", masterId);
        try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET ticket_number=? WHERE id=?")) {
            ps.setString(1, mergedNumber);
            ps.setInt(2, masterId);
            ps.executeUpdate();
        }

        String refs = effectiveMerged.stream()
                .map(TicketUtil::formatTicketRef)
                .reduce((a, b) -> a + ", " + b)
                .orElse("-");

        logEvent(c, masterId, "TICKET_MERGED",
                "Merged tickets into " + TicketUtil.formatTicketRef(masterId) + ": " + refs);
        TicketHistoryDAO.log(c, masterId, "MERGED",
                "Merged by " + Session.getUsername() + " | Sources: " + refs);

        c.commit();
    } catch (Exception e) {
        throw new RuntimeException("Merge failed: " + e.getMessage(), e);
    }
}





    
public static List<TicketEvent> getEvents(int ticketId) {

    List<TicketEvent> list = new ArrayList<>();

    String sql = """
        SELECT *
        FROM (
            SELECT te.event_type AS type,
                   te."comment" AS description,
                   te.created_at AS created_at,
                   u.username AS username
            FROM ticket_events te
            LEFT JOIN users u ON te.user_id = u.id
            WHERE te.ticket_id = ?

            UNION ALL

            SELECT 'COMMENT' AS type,
                   tc."comment" AS description,
                   tc.created_at AS created_at,
                   u.username AS username
            FROM ticket_comments tc
            LEFT JOIN users u ON tc.user_id = u.id
            WHERE tc.ticket_id = ?
        ) ev
        ORDER BY ev.created_at ASC
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

    String sql =
        "SELECT\n" +
        "CASE\n" +
        "    WHEN COALESCE(sla_hours, 0) <= 0 THEN 0\n" +
        "    ELSE ((" + SqlDialect.minutesBetweenNowAnd("created_at") + " / 60.0) / sla_hours) * 100\n" +
        "END\n" +
        "FROM tickets\n" +
        "WHERE id = ?";

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
        INSERT INTO ticket_events(ticket_id, user_id, event_type, "comment")
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

    try {
        assertUserNotOnLeaveForTicketAssignment(userId);
    } catch (RuntimeException ex) {
        System.out.println("Force reassign blocked (user on leave): " + ex.getMessage());
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

public static boolean isTicketInCurrentUserDirection(int ticketId) {
    if (!useDirectionScopeForTicketAggregates()) {
        return true;
    }

    Integer currentDirectionId = getCurrentUserDirectionId();
    if (currentDirectionId == null || currentDirectionId <= 0) {
        return false;
    }

    String sql = """
        SELECT creator.direction_id AS creator_direction_id,
               assignee.direction_id AS assignee_direction_id
        FROM tickets t
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE t.id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, ticketId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            int creatorDirectionId = rs.getInt("creator_direction_id");
            boolean creatorMatches = !rs.wasNull() && creatorDirectionId == currentDirectionId;

            int assigneeDirectionId = rs.getInt("assignee_direction_id");
            boolean assigneeMatches = !rs.wasNull() && assigneeDirectionId == currentDirectionId;

            return creatorMatches || assigneeMatches;
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
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

    boolean scope = useDirectionScopeForTicketAggregates();
    Integer dirId = scope ? getCurrentUserDirectionId() : null;
    if (scope && (dirId == null || dirId <= 0)) {
        return 0;
    }

    String sql = scope
            ? """
        SELECT AVG(t.resolution_minutes)
        FROM tickets t
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE t.status = 'CLOSED'
        AND t.resolution_minutes IS NOT NULL
        AND (creator.direction_id = ? OR assignee.direction_id = ?)
    """
            : """
        SELECT AVG(resolution_minutes)
        FROM tickets
        WHERE status = 'CLOSED'
        AND resolution_minutes IS NOT NULL
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (scope) {
            ps.setInt(1, dirId);
            ps.setInt(2, dirId);
        }

        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        }

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

    boolean scope = useDirectionScopeForTicketAggregates();
    Integer dirId = scope ? getCurrentUserDirectionId() : null;
    if (scope && (dirId == null || dirId <= 0)) {
        return 0;
    }

    String startedMinutesScoped = SqlDialect.minutesBetweenNowAnd("t.started_at");
    String startedMinutesUnscoped = SqlDialect.minutesBetweenNowAnd("started_at");
    String sql = scope
            ? """
        SELECT COUNT(*)
        FROM tickets t
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE t.started_at IS NOT NULL
        AND t.closed_at IS NULL
        AND (
            """ + startedMinutesScoped + """
        ) > (t.sla_hours * 60)
        AND (creator.direction_id = ? OR assignee.direction_id = ?)
    """
            : """
        SELECT COUNT(*)
        FROM tickets
        WHERE started_at IS NOT NULL
        AND closed_at IS NULL
        AND (
            """ + startedMinutesUnscoped + """
        ) > (sla_hours * 60)
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (scope) {
            ps.setInt(1, dirId);
            ps.setInt(2, dirId);
        }

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return 0;
}

public static int countEscalations() {

    boolean scope = useDirectionScopeForTicketAggregates();
    Integer dirId = scope ? getCurrentUserDirectionId() : null;
    if (scope && (dirId == null || dirId <= 0)) {
        return 0;
    }

    String sql = scope
            ? """
        SELECT COUNT(*)
        FROM ticket_escalations te
        JOIN tickets t ON t.id = te.ticket_id
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE (creator.direction_id = ? OR assignee.direction_id = ?)
    """
            : "SELECT COUNT(*) FROM ticket_escalations";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        if (scope) {
            ps.setInt(1, dirId);
            ps.setInt(2, dirId);
        }

        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
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

    boolean restrictToDirection = useDirectionScopeForTicketAggregates();
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

    String sql = restrictToDirection
            ? """
        SELECT u.username, COUNT(*) as total
        FROM ticket_assignments ta
        JOIN users u ON ta.assigned_to = u.id
        JOIN tickets t ON ta.ticket_id = t.id
        JOIN users creator ON t.created_by = creator.id
        LEFT JOIN users ticketAssignee ON ticketAssignee.id = t.assigned_to
        WHERE (creator.direction_id = ? OR ticketAssignee.direction_id = ? OR u.direction_id = ?)
        GROUP BY u.username
        ORDER BY total DESC
    """
            : """
        SELECT u.username, COUNT(*) as total
        FROM ticket_assignments ta
        JOIN users u ON ta.assigned_to = u.id
        GROUP BY u.username
        ORDER BY total DESC
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return performance;
            }
            stmt.setInt(1, currentDirectionId);
            stmt.setInt(2, currentDirectionId);
            stmt.setInt(3, currentDirectionId);
        }

        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String user = rs.getString("username");
                int total = rs.getInt("total");
                performance.put(user, total);
            }
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
            created_at
        )
        VALUES(?, ?, ?, 'OPEN', ?, ?, ?, NULL, datetime('now','localtime'))
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = DbConfig.isOracle()
                 ? c.prepareStatement(sql, new String[] { "ID" })
                 : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, priority);
        ps.setString(4, ticketType);
        ps.setInt(5, department.getId());
        ps.setInt(6, currentUserId);

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();

        if (rs.next()) {

            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Ticket insert succeeded but no numeric generated ID was returned");
            }
            int ticketId = generatedId.intValue();

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
         PreparedStatement ps = DbConfig.isOracle()
                 ? c.prepareStatement(sql, new String[] { "ID" })
                 : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setString(1, title);
        ps.setString(2, description);
        ps.setString(3, priority);
        ps.setInt(4, departmentId);
        ps.setInt(5, agentId);
        ps.setInt(6, Session.getUserId());

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();

        if (rs.next()) {
            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Creating internal ticket failed: no numeric generated ID.");
            }
            return generatedId.intValue();
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return -1;
}

    /**
     * When a courrier is routed to a sous-direction, create an open internal ticket (unassigned) for the ticket pipeline.
     */
    public static int createInternalFromCourier(CourierPacket p, int departmentId) {
        if (p == null || departmentId <= 0) {
            return -1;
        }
        int createdBy = Session.getUserId();
        String title = "[Courrier] " + p.getRefCode() + " — " + p.getTitle();
        String desc = (p.getDescription() != null ? p.getDescription().trim() : "");
        if (p.getSender() != null && !p.getSender().isBlank()) {
            desc = (desc.isEmpty() ? "" : desc + "\n\n") + "Expéditeur: " + p.getSender();
        }
        desc = desc + "\n\n— Courrier: " + p.getRefCode();
        String pr = p.getPriority() != null && !p.getPriority().isBlank() ? p.getPriority() : "MEDIUM";
        String sql;
        if (DbConfig.isOracle()) {
            sql = """
                    INSERT INTO tickets (title, description, priority, status, ticket_type, department_id, assigned_to, created_by, created_at)
                    VALUES (?, ?, ?, 'OPEN', 'INTERNAL', ?, NULL, ?, CURRENT_TIMESTAMP)
                    """;
        } else {
            sql = """
                    INSERT INTO tickets (title, description, priority, status, ticket_type, department_id, assigned_to, created_by, created_at)
                    VALUES (?, ?, ?, 'OPEN', 'INTERNAL', ?, NULL, ?, datetime('now','localtime'))
                    """;
        }
        try (Connection c = DB.getConnection();
             PreparedStatement ps = DbConfig.isOracle()
                     ? c.prepareStatement(sql, new String[] { "ID" })
                     : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setString(2, desc);
            ps.setString(3, pr);
            ps.setInt(4, departmentId);
            ps.setInt(5, createdBy);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Number id = (Number) rs.getObject(1);
                    if (id == null) {
                        return -1;
                    }
                    int ticketId = id.intValue();
                    int year = java.time.Year.now().getValue();
                    String num = "TCK-" + year + "-" + String.format("%03d", ticketId);
                    updateTicketNumber(ticketId, num);
                    return ticketId;
                }
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

    if (newUserId == Session.getUserId()) {
        throw new RuntimeException("You cannot reassign a ticket to yourself.");
    }

    User targetUser = UserDAO.findById(newUserId);

    if (targetUser == null) {
        throw new RuntimeException("Target user not found.");
    }

    String fromRole = Session.getRole();
    String toRole = targetUser.getRole();

    if (!com.app.util.RoleFlowUtil.canAssign(fromRole, toRole)) {
        throw new RuntimeException("Reassignment not allowed: " + fromRole + " → " + toRole);
    }

    assertUserNotOnLeaveForTicketAssignment(newUserId);

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
            "TICKET_REASSIGNED",
            "TICKET",
            ticketId,
            TicketUtil.formatTicketRef(ticketId)
    );
}

public static ObservableList<User> getTicketReassignCandidatesForCurrentUser() {
    ObservableList<User> list = FXCollections.observableArrayList();
    String myRole = Session.getRole();
    if (myRole == null || myRole.isBlank()) {
        return list;
    }
    Set<String> allowedRoles = com.app.util.RoleFlowUtil.getAllowedTargetRoles(myRole);
    if (allowedRoles.isEmpty()) {
        return list;
    }
    ObservableList<User> users = UserDAO.findActiveUsersByRoles(new ArrayList<>(allowedRoles));
    java.util.Set<Integer> absent = UserAbsenceDAO.userIdsAbsentOn(LocalDate.now());
    for (User u : users) {
        if (u == null) continue;
        if (u.getId() == Session.getUserId()) continue; // never self
        if (absent.contains(u.getId())) continue;
        list.add(u);
    }
    return list;
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

    boolean fromAdmin = "ADMIN".equalsIgnoreCase(fromRole);
    boolean systemSuper = com.app.util.AccessContext.isBuiltInSuperAdminUsername(fromUser.getUsername());
    if (fromAdmin && !systemSuper) {
        Integer dFrom = fromUser.getDirectionId();
        Integer dTo = toUser.getDirectionId();
        if (dFrom == null || dTo == null || !dFrom.equals(dTo)) {
            throw new RuntimeException("Reassignment and escalation for a direction admin must stay in the same direction.");
        }
    } else if (!fromAdmin) {
        Set<String> allowedEscalationTargets =
                com.app.util.RoleFlowUtil.getAllowedInternalEscalationRoles(fromRole);
        if (!allowedEscalationTargets.contains(com.app.util.RoleFlowUtil.normalize(toRole))) {
            throw new RuntimeException("This reassignment is not a valid escalation path.");
        }
    }

    assertUserNotOnLeaveForTicketAssignment(toUserId);

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
            "TICKET_ESCALATED",
            "TICKET",
            ticketId,
            TicketUtil.formatTicketRef(ticketId)
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

    if (!isTicketInCurrentUserDirection(ticketId)) {
        throw new RuntimeException("You cannot delete a ticket outside your direction.");
    }

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        try {

            // =====================================================
            // IMPORTANT:
            // ticket_tasks.ticket_id has a FK -> tickets(id) WITHOUT ON DELETE CASCADE.
            // Therefore we must delete dependent task rows first,
            // otherwise DELETE FROM tickets fails for tickets with tasks.
            // =====================================================

            // task children referencing ticket_tasks(id)
            try {
                try (PreparedStatement psTaskComments = c.prepareStatement(
                        "DELETE FROM task_comments " +
                        "WHERE task_id IN (SELECT id FROM ticket_tasks WHERE ticket_id=?)")) {
                    psTaskComments.setInt(1, ticketId);
                    psTaskComments.executeUpdate();
                }
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                if (!msg.contains("no such table")) throw ex;
            }

            try {
                try (PreparedStatement psTaskAttachments = c.prepareStatement(
                        "DELETE FROM task_attachments " +
                        "WHERE task_id IN (SELECT id FROM ticket_tasks WHERE ticket_id=?)")) {
                    psTaskAttachments.setInt(1, ticketId);
                    psTaskAttachments.executeUpdate();
                }
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                if (!msg.contains("no such table")) throw ex;
            }

            try {
                try (PreparedStatement psTaskEvents = c.prepareStatement(
                        "DELETE FROM task_events " +
                        "WHERE task_id IN (SELECT id FROM ticket_tasks WHERE ticket_id=?)")) {
                    psTaskEvents.setInt(1, ticketId);
                    psTaskEvents.executeUpdate();
                }
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                if (!msg.contains("no such table")) throw ex;
            }

            // delete all tasks for this ticket
            try {
                try (PreparedStatement psTasks = c.prepareStatement(
                        "DELETE FROM ticket_tasks WHERE ticket_id=?")) {
                    psTasks.setInt(1, ticketId);
                    psTasks.executeUpdate();
                }
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                if (!msg.contains("no such table")) throw ex;
            }

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

    boolean restrictToDirection = useDirectionScopeForTicketAggregates();
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

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

    if (restrictToDirection) {
        sql = sql.replace(
                "WHERE t.merged_into IS NULL",
                "WHERE t.merged_into IS NULL AND (c.direction_id = ? OR u.direction_id = ?)"
        );
    }

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return list;
            }
            ps.setInt(1, currentDirectionId);
            ps.setInt(2, currentDirectionId);
        }

        try (ResultSet rs = ps.executeQuery()) {
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

            ticket.setCreatedByName(rs.getString("creator_name"));
            ticket.setCreatedByRole(rs.getString("creator_role"));
            ticket.setCreatedBySousDirection(rs.getString("creator_sd"));

                list.add(ticket);
            }
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
        LEFT JOIN sous_directions d ON t.department_id = d.id
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

    boolean scope = useDirectionScopeForTicketAggregates();
    Integer dirId = scope ? getCurrentUserDirectionId() : null;
    if (scope && (dirId == null || dirId <= 0)) {
        return 0;
    }

    String sql = scope
            ? """
        SELECT COUNT(DISTINCT t.id)
        FROM tickets t
        LEFT JOIN ticket_tasks tt ON t.id = tt.ticket_id AND tt.assigned_to = ?
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users ticketAssignee ON ticketAssignee.id = t.assigned_to
        WHERE t.status = ?
          AND (t.assigned_to = ? OR tt.id IS NOT NULL)
          AND (creator.direction_id = ? OR ticketAssignee.direction_id = ?)
    """
            : """
        SELECT COUNT(DISTINCT t.id)
        FROM tickets t
        LEFT JOIN ticket_tasks tt ON t.id = tt.ticket_id AND tt.assigned_to = ?
        WHERE t.status = ?
          AND (t.assigned_to = ? OR tt.id IS NOT NULL)
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, agentId);
        ps.setString(2, status);
        ps.setInt(3, agentId);
        if (scope) {
            ps.setInt(4, dirId);
            ps.setInt(5, dirId);
        }

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return 0;
}

    /** Rows for agent dashboard status tiles ({@link #countAssignedTicketsByStatus(int, String)}). */
    public static List<Ticket> listAssignedTicketsByStatusForDashboardAgent(int agentId, String status) {
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        String sql = scope
                ? """
                SELECT DISTINCT t.*, ticketAssignee.username AS assigned_to_name
                FROM tickets t
                LEFT JOIN ticket_tasks tt ON t.id = tt.ticket_id AND tt.assigned_to = ?
                JOIN users creator ON creator.id = t.created_by
                LEFT JOIN users ticketAssignee ON ticketAssignee.id = t.assigned_to
                WHERE t.status = ?
                  AND (t.assigned_to = ? OR tt.id IS NOT NULL)
                  AND (creator.direction_id = ? OR ticketAssignee.direction_id = ?)
                ORDER BY t.id DESC
                LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT
                : """
                SELECT DISTINCT t.*, ticketAssignee.username AS assigned_to_name
                FROM tickets t
                LEFT JOIN ticket_tasks tt ON t.id = tt.ticket_id AND tt.assigned_to = ?
                LEFT JOIN users ticketAssignee ON ticketAssignee.id = t.assigned_to
                WHERE t.status = ?
                  AND (t.assigned_to = ? OR tt.id IS NOT NULL)
                ORDER BY t.id DESC
                LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
        return queryDashboardTickets(sql, ps -> {
            ps.setInt(1, agentId);
            ps.setString(2, status);
            ps.setInt(3, agentId);
            if (scope) {
                ps.setInt(4, dirId);
                ps.setInt(5, dirId);
            }
        });
    }

public static Map<String,Integer> getAgentDailyPerformance(int agentId){

    Map<String,Integer> data = new HashMap<>();

    String sql = SqlDialect.isOracle()
            ? """
            SELECT TO_CHAR(TRUNC(closed_at), 'YYYY-MM-DD') AS day,
                   COUNT(*) AS total
            FROM tickets
            WHERE assigned_to = ?
            AND status = 'CLOSED'
            AND closed_at IS NOT NULL
            GROUP BY TRUNC(closed_at)
            ORDER BY TRUNC(closed_at)
            """
            : """
            SELECT date(closed_at) AS day,
                   COUNT(*) AS total
            FROM tickets
            WHERE assigned_to = ?
            AND status = 'CLOSED'
            GROUP BY date(closed_at)
            ORDER BY date(closed_at)
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

    boolean scope = useDirectionScopeForTicketAggregates();
    Integer dirId = scope ? getCurrentUserDirectionId() : null;
    if (scope && (dirId == null || dirId <= 0)) {
        return 0;
    }

    String startedMinutesScoped = SqlDialect.minutesBetweenNowAnd("t.started_at");
    String startedMinutesUnscoped = SqlDialect.minutesBetweenNowAnd("started_at");
    String sql = scope
            ? """
        SELECT COUNT(*)
        FROM tickets t
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE t.assigned_to = ?
        AND t.started_at IS NOT NULL
        AND t.closed_at IS NULL
        AND (
            """ + startedMinutesScoped + """
        ) > (t.sla_hours * 60)
        AND (creator.direction_id = ? OR assignee.direction_id = ?)
    """
            : """
        SELECT COUNT(*)
        FROM tickets
        WHERE assigned_to = ?
        AND started_at IS NOT NULL
        AND closed_at IS NULL
        AND (
            """ + startedMinutesUnscoped + """
        ) > (sla_hours * 60)
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, agentId);
        if (scope) {
            ps.setInt(2, dirId);
            ps.setInt(3, dirId);
        }

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt(1);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return 0;
}

    /** Agent-scoped SLA breach rows ({@link #countAgentSlaBreaches(int)}). */
    public static List<Ticket> listAgentSlaBreachesDashboard(int agentId) {
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        String startedMinutesScoped = SqlDialect.minutesBetweenNowAnd("t.started_at");
        String startedMinutesUnscoped = SqlDialect.minutesBetweenNowAnd("started_at");
        String sql = scope
                ? """
                SELECT t.*, assignee.username AS assigned_to_name
                FROM tickets t
                JOIN users creator ON creator.id = t.created_by
                LEFT JOIN users assignee ON assignee.id = t.assigned_to
                WHERE t.assigned_to = ?
                AND t.started_at IS NOT NULL
                AND t.closed_at IS NULL
                AND (
                    """ + startedMinutesScoped + """
                ) > (t.sla_hours * 60)
                AND (creator.direction_id = ? OR assignee.direction_id = ?)
                ORDER BY t.id DESC
                LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT
                : """
                SELECT t.*, assignee.username AS assigned_to_name
                FROM tickets t
                LEFT JOIN users assignee ON assignee.id = t.assigned_to
                WHERE t.assigned_to = ?
                AND t.started_at IS NOT NULL
                AND t.closed_at IS NULL
                AND (
                    """ + startedMinutesUnscoped + """
                ) > (t.sla_hours * 60)
                ORDER BY t.id DESC
                LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
        return queryDashboardTickets(sql, ps -> {
            ps.setInt(1, agentId);
            if (scope) {
                ps.setInt(2, dirId);
                ps.setInt(3, dirId);
            }
        });
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
        INSERT INTO ticket_events(ticket_id, user_id, event_type, "comment")
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

    try (PreparedStatement ps = DbConfig.isOracle()
            ? c.prepareStatement(sql, new String[] { "ID" })
            : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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
        if (!rs.next()) {
            return 0;
        }
        Number generatedId = (Number) rs.getObject(1);
        if (generatedId == null) {
            throw new SQLException("Creating child task failed: no numeric generated ID.");
        }
        return generatedId.intValue();
    }
}
    
    // =====================================================
// ASSIGN TICKET TO MULTIPLE USERS
// =====================================================
public static void assignUsersToTicket(int ticketId, List<Integer> userIds) {

    if (userIds != null) {
        for (Integer uid : userIds) {
            if (uid != null && uid > 0) {
                assertUserNotOnLeaveForTicketAssignment(uid);
            }
        }
    }

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
    
    /**
     * Active ticket assignees in assignment order (multi-assign sequence).
     */
    public static List<User> getActiveAssignedUsersOrdered(int ticketId) {

        List<User> list = new ArrayList<>();

        String sql = """
            SELECT u.id, u.username, u.role
            FROM ticket_assignments ta
            JOIN users u ON ta.assigned_to = u.id
            WHERE ta.ticket_id = ?
              AND ta.active = 1
            ORDER BY ta.assigned_at ASC, ta.id ASC
            """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, ticketId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setRole(rs.getString("role"));
                list.add(u);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    /**
     * Combo for "Add task" on ticket details: if several users are assigned, only the next
     * assignee in sequence (no completed task yet) appears. Single assignee → that user only.
     * Unassigned ticket → all assignable users (role hierarchy).
     */
    public static ObservableList<User> buildAssigneeComboForNewTask(int ticketId) {

        List<User> ordered = getActiveAssignedUsersOrdered(ticketId);

        if (ordered.isEmpty()) {
            return FXCollections.observableArrayList(UserDAO.getAssignableUsers());
        }
        if (ordered.size() == 1) {
            return FXCollections.observableArrayList(ordered.get(0));
        }

        Set<Integer> completed = TicketTaskDAO.getUserIdsWithCompletedNormalTasks(ticketId);
        for (User u : ordered) {
            if (!completed.contains(u.getId())) {
                return FXCollections.observableArrayList(u);
            }
        }

        return FXCollections.observableArrayList();
    }

    /**
     * Returns active users from the same direction as the ticket creator, filtered by role flow
     * so the current session user only sees valid assignees.
     */
    public static ObservableList<User> getUsersInTicketDirectionForTasks(int ticketId) {
        ObservableList<User> list = FXCollections.observableArrayList();
        String currentRole = Session.getRole();

        // Roles that can assign (DIRECTEUR, SOUS-DIRECTEUR, INSPECTEUR, CONTROLEUR, ADMIN)
        // can add tasks on any existing ticket and choose from their allowed role targets.
        if (com.app.util.RoleFlowUtil.canSeeUnassignedTicketsForAssignment(currentRole)) {
            return getAssignableUsersForRoleGlobal(currentRole);
        }

        if (!isTicketInCurrentUserDirection(ticketId)) {
            return list;
        }

        String sql = """
            SELECT u.id, u.username, u.role
            FROM tickets t
            JOIN users creator ON creator.id = t.created_by
            JOIN users u ON u.direction_id = creator.direction_id
            WHERE t.id = ?
              AND u.active = 1
              AND NOT EXISTS (
                  SELECT 1 FROM user_absences ua
                  WHERE ua.user_id = u.id AND ua.start_date <= ? AND ua.end_date >= ?""" 
                + UserAbsenceDAO.andClauseCongeVacancesOnly("ua.") + """
              )
            ORDER BY u.username
        """;

        String myRole = currentRole;
        String today = LocalDate.now().toString();

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ps.setString(2, today);
            ps.setString(3, today);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String targetRole = rs.getString("role");
                if (myRole != null && targetRole != null
                        && !com.app.util.RoleFlowUtil.canAssign(myRole, targetRole)) {
                    continue;
                }

                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setRole(targetRole);
                list.add(u);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    private static ObservableList<User> getAssignableUsersForRoleGlobal(String fromRole) {
        ObservableList<User> list = FXCollections.observableArrayList();
        Set<String> allowedRoles = com.app.util.RoleFlowUtil.getAllowedTargetRoles(fromRole);
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return list;
        }

        List<String> roles = new ArrayList<>(allowedRoles);
        String placeholders = String.join(",", java.util.Collections.nCopies(roles.size(), "?"));
        String sql = "SELECT u.id, u.username, u.role FROM users u WHERE u.active = 1 AND u.role IN ("
                + placeholders + ") AND NOT EXISTS (SELECT 1 FROM user_absences ua WHERE ua.user_id = u.id "
                + "AND ua.start_date <= ? AND ua.end_date >= ?"
                + UserAbsenceDAO.andClauseCongeVacancesOnly("ua.")
                + ") ORDER BY u.username";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < roles.size(); i++) {
                ps.setString(i + 1, roles.get(i));
            }
            String today = LocalDate.now().toString();
            ps.setString(roles.size() + 1, today);
            ps.setString(roles.size() + 2, today);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setRole(rs.getString("role"));
                list.add(u);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
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
         PreparedStatement ps = DbConfig.isOracle()
                 ? c.prepareStatement(sql, new String[] { "ID" })
                 : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setInt(1, ticketId);
        ps.setString(2, title);
        ps.setInt(3, userId);
        ps.setInt(4, Session.getUserId());
        ps.setString(5, description);

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();
        if (rs.next()) {
            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Creating task failed: no numeric generated ID.");
            }
            return generatedId.intValue();
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

public static boolean isUserTicketCreator(int ticketId, int userId) {

    String sql = """
        SELECT 1
        FROM tickets
        WHERE id = ?
          AND created_by = ?
        LIMIT 1
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

    boolean restrictToDirection = useDirectionScopeForTicketAggregates();
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

    String sql = restrictToDirection
            ? """
        SELECT t.*
        FROM tickets t
        WHERE EXISTS (
            SELECT 1
            FROM ticket_assignments ta
            JOIN users creator ON creator.id = t.created_by
            JOIN users assignee ON assignee.id = ta.assigned_to
            WHERE ta.ticket_id = t.id
              AND ta.assigned_to = ?
              AND ta.active = 1
              AND (creator.direction_id = ? OR assignee.direction_id = ?)
        )
        ORDER BY t.updated_at DESC
        """
            : """
        SELECT t.*
        FROM tickets t
        WHERE EXISTS (
            SELECT 1
            FROM ticket_assignments ta
            WHERE ta.ticket_id = t.id
              AND ta.assigned_to = ?
              AND ta.active = 1
        )
        ORDER BY t.updated_at DESC
        """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return list;
            }
            ps.setInt(2, currentDirectionId);
            ps.setInt(3, currentDirectionId);
        }

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

    private static final int DASHBOARD_DETAIL_LIMIT = 5000;

    private static void applyOptionalAssigneeName(Ticket ticket, ResultSet rs) {
        try {
            String n = rs.getString("assigned_to_name");
            if (n != null && !n.isBlank()) {
                ticket.setAssignedToName(n);
            }
        } catch (SQLException ignored) {
        }
    }

    @FunctionalInterface
    private interface DashboardStatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private static List<Ticket> queryDashboardTickets(String sql, DashboardStatementBinder bind) {
        List<Ticket> list = new ArrayList<>();
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (bind != null) {
                bind.bind(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        Ticket t = mapTicket(rs);
                        applyOptionalAssigneeName(t, rs);
                        list.add(t);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /** Rows backing the dashboard “total tickets” tile (same scope as {@link #countAll()}). */
    public static List<Ticket> listDashboardTicketsAll() {
        try {
            if (hasOrgWideTicketAccess()) {
                String sql = """
                        SELECT t.*, assignee.username AS assigned_to_name
                        FROM tickets t
                        LEFT JOIN users assignee ON assignee.id = t.assigned_to
                        ORDER BY t.id DESC
                        LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
                return queryDashboardTickets(sql, null);
            }
            if (!useDirectionScopeForTicketAggregates()) {
                String sql = """
                        SELECT t.*, assignee.username AS assigned_to_name
                        FROM tickets t
                        LEFT JOIN users assignee ON assignee.id = t.assigned_to
                        WHERE t.merged_into IS NULL AND COALESCE(UPPER(t.status),'') <> 'MERGED'
                        ORDER BY t.id DESC
                        LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
                return queryDashboardTickets(sql, null);
            }
            Integer dirId = getCurrentUserDirectionId();
            if (dirId == null || dirId <= 0) {
                return List.of();
            }
            String sql = """
                    SELECT t.*, assignee.username AS assigned_to_name
                    FROM tickets t
                    JOIN users creator ON creator.id = t.created_by
                    LEFT JOIN users assignee ON assignee.id = t.assigned_to
                    WHERE t.merged_into IS NULL
                      AND COALESCE(UPPER(t.status),'') <> 'MERGED'
                      AND (creator.direction_id = ? OR assignee.direction_id = ?)
                    ORDER BY t.id DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
            return queryDashboardTickets(sql, ps -> {
                ps.setInt(1, dirId);
                ps.setInt(2, dirId);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /** Same scope as {@link #countByStatus(String)}. */
    public static List<Ticket> listDashboardTicketsByStatus(String status) {
        try {
            boolean restrictToDirection = useDirectionScopeForTicketAggregates();
            Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;
            if (restrictToDirection && (currentDirectionId == null || currentDirectionId <= 0)) {
                return List.of();
            }
            String sql = restrictToDirection
                    ? """
                    SELECT t.*, assignee.username AS assigned_to_name
                    FROM tickets t
                    JOIN users creator ON creator.id = t.created_by
                    LEFT JOIN users assignee ON assignee.id = t.assigned_to
                    WHERE t.status = ?
                      AND (creator.direction_id = ? OR assignee.direction_id = ?)
                    ORDER BY t.id DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT
                    : """
                    SELECT t.*, assignee.username AS assigned_to_name
                    FROM tickets t
                    LEFT JOIN users assignee ON assignee.id = t.assigned_to
                    WHERE t.status = ?
                    ORDER BY t.id DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
            return queryDashboardTickets(sql, ps -> {
                ps.setString(1, status);
                if (restrictToDirection) {
                    ps.setInt(2, currentDirectionId);
                    ps.setInt(3, currentDirectionId);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /** Tickets counted as active SLA breaches (same rules as {@link #countSlaBreaches()}). */
    public static List<Ticket> listDashboardTicketsSlaBreaches() {
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        String startedMinutesScoped = SqlDialect.minutesBetweenNowAnd("t.started_at");
        String startedMinutesUnscoped = SqlDialect.minutesBetweenNowAnd("started_at");
        try {
            if (scope) {
                String sql = """
                        SELECT t.*, assignee.username AS assigned_to_name
                        FROM tickets t
                        JOIN users creator ON creator.id = t.created_by
                        LEFT JOIN users assignee ON assignee.id = t.assigned_to
                        WHERE t.started_at IS NOT NULL
                        AND t.closed_at IS NULL
                        AND (
                            """ + startedMinutesScoped + """
                        ) > (t.sla_hours * 60)
                        AND (creator.direction_id = ? OR assignee.direction_id = ?)
                        ORDER BY t.id DESC
                        LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
                return queryDashboardTickets(sql, ps -> {
                    ps.setInt(1, dirId);
                    ps.setInt(2, dirId);
                });
            }
            String sql = """
                    SELECT t.*, assignee.username AS assigned_to_name
                    FROM tickets t
                    LEFT JOIN users assignee ON assignee.id = t.assigned_to
                    WHERE t.started_at IS NOT NULL
                    AND t.closed_at IS NULL
                    AND (
                        """ + startedMinutesUnscoped + """
                    ) > (t.sla_hours * 60)
                    ORDER BY t.id DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
            return queryDashboardTickets(sql, null);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /** Tickets that appear in {@link #countEscalations()} at least once. */
    public static List<Ticket> listDashboardTicketsWithEscalations() {
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        try {
            if (scope) {
                String sql = """
                        SELECT DISTINCT t.*, assignee.username AS assigned_to_name
                        FROM ticket_escalations te
                        JOIN tickets t ON t.id = te.ticket_id
                        JOIN users creator ON creator.id = t.created_by
                        LEFT JOIN users assignee ON assignee.id = t.assigned_to
                        WHERE (creator.direction_id = ? OR assignee.direction_id = ?)
                        ORDER BY t.id DESC
                        LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
                return queryDashboardTickets(sql, ps -> {
                    ps.setInt(1, dirId);
                    ps.setInt(2, dirId);
                });
            }
            String sql = """
                    SELECT DISTINCT t.*, assignee.username AS assigned_to_name
                    FROM ticket_escalations te
                    JOIN tickets t ON t.id = te.ticket_id
                    LEFT JOIN users assignee ON assignee.id = t.assigned_to
                    ORDER BY t.id DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
            return queryDashboardTickets(sql, null);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /** Closed tickets used in average resolution KPI. */
    public static List<Ticket> listDashboardClosedTicketsWithResolution() {
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        try {
            if (scope) {
                String sql = """
                        SELECT t.*, assignee.username AS assigned_to_name
                        FROM tickets t
                        JOIN users creator ON creator.id = t.created_by
                        LEFT JOIN users assignee ON assignee.id = t.assigned_to
                        WHERE t.status = 'CLOSED'
                        AND t.resolution_minutes IS NOT NULL
                        AND (creator.direction_id = ? OR assignee.direction_id = ?)
                        ORDER BY t.closed_at DESC
                        LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
                return queryDashboardTickets(sql, ps -> {
                    ps.setInt(1, dirId);
                    ps.setInt(2, dirId);
                });
            }
            String sql = """
                    SELECT t.*, assignee.username AS assigned_to_name
                    FROM tickets t
                    LEFT JOIN users assignee ON assignee.id = t.assigned_to
                    WHERE t.status = 'CLOSED'
                    AND t.resolution_minutes IS NOT NULL
                    ORDER BY t.closed_at DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
            return queryDashboardTickets(sql, null);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /** Closed tickets resolved within configured SLA duration (detail for compliance tile). */
    public static List<Ticket> listDashboardClosedTicketsResolvedWithinSla() {
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        try {
            if (scope) {
                String sql = """
                        SELECT t.*, assignee.username AS assigned_to_name
                        FROM tickets t
                        JOIN users creator ON creator.id = t.created_by
                        LEFT JOIN users assignee ON assignee.id = t.assigned_to
                        WHERE t.status = 'CLOSED'
                        AND t.resolution_minutes IS NOT NULL
                        AND t.sla_hours IS NOT NULL
                        AND t.resolution_minutes <= (t.sla_hours * 60)
                        AND (creator.direction_id = ? OR assignee.direction_id = ?)
                        ORDER BY t.closed_at DESC
                        LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
                return queryDashboardTickets(sql, ps -> {
                    ps.setInt(1, dirId);
                    ps.setInt(2, dirId);
                });
            }
            String sql = """
                    SELECT t.*, assignee.username AS assigned_to_name
                    FROM tickets t
                    LEFT JOIN users assignee ON assignee.id = t.assigned_to
                    WHERE t.status = 'CLOSED'
                    AND t.resolution_minutes IS NOT NULL
                    AND t.sla_hours IS NOT NULL
                    AND t.resolution_minutes <= (t.sla_hours * 60)
                    ORDER BY t.closed_at DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
            return queryDashboardTickets(sql, null);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    /** Closed tickets assigned to {@code username} (top performer tile). */
    public static List<Ticket> listDashboardClosedTicketsForAssigneeUsername(String username) {
        if (username == null || username.isBlank() || "N/A".equalsIgnoreCase(username.trim())) {
            return List.of();
        }
        boolean scope = useDirectionScopeForTicketAggregates();
        Integer dirId = scope ? getCurrentUserDirectionId() : null;
        if (scope && (dirId == null || dirId <= 0)) {
            return List.of();
        }
        String uname = username.trim();
        try {
            if (scope) {
                String sql = """
                        SELECT t.*, assignee.username AS assigned_to_name
                        FROM tickets t
                        JOIN users creator ON creator.id = t.created_by
                        JOIN users assignee ON assignee.id = t.assigned_to
                        WHERE t.status = 'CLOSED'
                        AND assignee.username = ?
                        AND (creator.direction_id = ? OR assignee.direction_id = ?)
                        ORDER BY t.closed_at DESC
                        LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
                return queryDashboardTickets(sql, ps -> {
                    ps.setString(1, uname);
                    ps.setInt(2, dirId);
                    ps.setInt(3, dirId);
                });
            }
            String sql = """
                    SELECT t.*, assignee.username AS assigned_to_name
                    FROM tickets t
                    JOIN users assignee ON assignee.id = t.assigned_to
                    WHERE t.status = 'CLOSED'
                    AND assignee.username = ?
                    ORDER BY t.closed_at DESC
                    LIMIT """ + " " + DASHBOARD_DETAIL_LIMIT;
            return queryDashboardTickets(sql, ps -> ps.setString(1, uname));
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
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

public static List<TicketEvent> getTicketTrackingEvents(int ticketId) {
    List<TicketEvent> list = new ArrayList<>();

    String sql = DbConfig.isOracle()
            ? """
        SELECT t.created_at AS created_at,
               COALESCE(uc.username, '-') AS username,
               'TICKET_CREATED' AS type,
               TO_CLOB('Created ticket') AS description
        FROM tickets t
        LEFT JOIN users uc ON uc.id = t.created_by
        WHERE t.id = ?

        UNION ALL

        SELECT ta.assigned_at AS created_at,
               COALESCE(ub.username, '-') AS username,
               CASE
                 WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'ESCALATION' THEN 'TICKET_ESCALATED'
                 WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'REASSIGNMENT' THEN 'TICKET_REASSIGNED'
                 ELSE 'TICKET_ASSIGNED'
               END AS type,
               TO_CLOB(
                 CASE
                   WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'ESCALATION'
                        THEN 'Escalated ticket to ' || COALESCE(ut.username, '-')
                   WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'REASSIGNMENT'
                        THEN 'Reassigned ticket to ' || COALESCE(ut.username, '-')
                   ELSE 'Assigned ticket to ' || COALESCE(ut.username, '-')
                 END
               ) AS description
        FROM ticket_assignments ta
        LEFT JOIN users ub ON ub.id = ta.assigned_by
        LEFT JOIN users ut ON ut.id = ta.assigned_to
        WHERE ta.ticket_id = ?

        UNION ALL

        SELECT tt.created_at AS created_at,
               COALESCE(ua.username, '-') AS username,
               'TASK_ADDED' AS type,
               TO_CLOB('Added task "' || COALESCE(tt.title, 'Task') || '" and assigned to ' || COALESCE(ut.username, '-')) AS description
        FROM ticket_tasks tt
        LEFT JOIN users ua ON ua.id = tt.assigned_by
        LEFT JOIN users ut ON ut.id = tt.assigned_to
        WHERE tt.ticket_id = ?

        UNION ALL

        SELECT th.created_at AS created_at,
               COALESCE(th.username, '-') AS username,
               'TICKET_MERGED' AS type,
               COALESCE(th.description, TO_CLOB('Merged ticket')) AS description
        FROM ticket_history th
        WHERE th.ticket_id = ?
          AND UPPER(COALESCE(th.action,'')) = 'MERGED'

        ORDER BY created_at ASC
    """
            : """
        SELECT t.created_at AS created_at,
               COALESCE(uc.username, '-') AS username,
               'TICKET_CREATED' AS type,
               'Created ticket' AS description
        FROM tickets t
        LEFT JOIN users uc ON uc.id = t.created_by
        WHERE t.id = ?

        UNION ALL

        SELECT ta.assigned_at AS created_at,
               COALESCE(ub.username, '-') AS username,
               CASE
                 WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'ESCALATION' THEN 'TICKET_ESCALATED'
                 WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'REASSIGNMENT' THEN 'TICKET_REASSIGNED'
                 ELSE 'TICKET_ASSIGNED'
               END AS type,
               CASE
                 WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'ESCALATION'
                      THEN 'Escalated ticket to ' || COALESCE(ut.username, '-')
                 WHEN UPPER(COALESCE(ta.assignment_type,'')) = 'REASSIGNMENT'
                      THEN 'Reassigned ticket to ' || COALESCE(ut.username, '-')
                 ELSE 'Assigned ticket to ' || COALESCE(ut.username, '-')
               END AS description
        FROM ticket_assignments ta
        LEFT JOIN users ub ON ub.id = ta.assigned_by
        LEFT JOIN users ut ON ut.id = ta.assigned_to
        WHERE ta.ticket_id = ?

        UNION ALL

        SELECT tt.created_at AS created_at,
               COALESCE(ua.username, '-') AS username,
               'TASK_ADDED' AS type,
               'Added task "' || COALESCE(tt.title, 'Task') || '" and assigned to ' || COALESCE(ut.username, '-') AS description
        FROM ticket_tasks tt
        LEFT JOIN users ua ON ua.id = tt.assigned_by
        LEFT JOIN users ut ON ut.id = tt.assigned_to
        WHERE tt.ticket_id = ?

        UNION ALL

        SELECT th.created_at AS created_at,
               COALESCE(th.username, '-') AS username,
               'TICKET_MERGED' AS type,
               COALESCE(th.description, 'Merged ticket')
        FROM ticket_history th
        WHERE th.ticket_id = ?
          AND UPPER(COALESCE(th.action,'')) = 'MERGED'

        ORDER BY created_at ASC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, ticketId);
        ps.setInt(2, ticketId);
        ps.setInt(3, ticketId);
        ps.setInt(4, ticketId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            TicketEvent ev = new TicketEvent();
            ev.setUsername(rs.getString("username"));
            ev.setType(rs.getString("type"));
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

    boolean restrictToDirection = useDirectionScopeForTicketAggregates();
    Integer currentDirectionId = restrictToDirection ? getCurrentUserDirectionId() : null;

    String sql = restrictToDirection
            ? """
        SELECT 
            t.id,
            t.title,
            t.status,
            t.priority,
            t.assigned_to,
            t.started_at,
            t.closed_at,
            t.resolution_minutes
        FROM tickets t
        JOIN users creator ON creator.id = t.created_by
        LEFT JOIN users assignee ON assignee.id = t.assigned_to
        WHERE t.assigned_to = ?
          AND t.merged_into IS NULL
          AND t.status <> 'MERGED'
          AND (creator.direction_id = ? OR assignee.direction_id = ?)
        ORDER BY created_at DESC
        """
            : """
        SELECT 
            id,
            title,
            status,
            priority,
            assigned_to,
            started_at,
            closed_at,
            resolution_minutes
        FROM tickets
        WHERE assigned_to = ?
          AND merged_into IS NULL
          AND status <> 'MERGED'
        ORDER BY created_at DESC
        """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        if (restrictToDirection) {
            if (currentDirectionId == null || currentDirectionId <= 0) {
                return list;
            }
            ps.setInt(2, currentDirectionId);
            ps.setInt(3, currentDirectionId);
        }

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Ticket t = new Ticket();

            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setStatus(rs.getString("status"));
            t.setPriority(rs.getString("priority"));
            Number assignedTo = (Number) rs.getObject("assigned_to");
            t.setAssignedTo(assignedTo == null ? null : assignedTo.intValue());

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

        Integer assignerIdForNotice = getActiveAssignerId(c, ticketId, Session.getUserId());
        if (assignerIdForNotice != null
                && assignerIdForNotice > 0
                && assignerIdForNotice != Session.getUserId()) {
            NotificationDAO.create(
                    assignerIdForNotice,
                    "Ticket Started",
                    Session.getUsername() + " started working on ticket " + TicketUtil.formatTicketRef(ticketId),
                    "TICKET_STARTED",
                    "TICKET",
                    ticketId,
                    TicketUtil.formatTicketRef(ticketId)
            );
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

private static Integer getActiveAssignerId(Connection c, int ticketId, int assignedToUserId) {
    String sql = """
        SELECT assigned_by
        FROM ticket_assignments
        WHERE ticket_id = ?
          AND assigned_to = ?
          AND active = 1
        ORDER BY id DESC
        LIMIT 1
    """;
    try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, ticketId);
        ps.setInt(2, assignedToUserId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            int v = rs.getInt("assigned_by");
            if (!rs.wasNull()) {
                return v;
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return null;
}
   
public static void escalateTicket(int ticketId,
                                  int fromUserId,
                                  int toUserId,
                                  String reason) {
    escalateTicket(ticketId, fromUserId, toUserId, null, reason);
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

public static ObservableList<User> getInternalEscalationCandidatesForCurrentUser() {
    ObservableList<User> list = FXCollections.observableArrayList();
    String myRole = Session.getRole();
    Set<String> allowedRoles = com.app.util.RoleFlowUtil.getAllowedInternalEscalationRoles(myRole);
    if (allowedRoles.isEmpty()) {
        return list;
    }

    String placeholders = String.join(",", java.util.Collections.nCopies(allowedRoles.size(), "?"));
    StringBuilder sql = new StringBuilder("""
        SELECT id, username, role
        FROM users
        WHERE active = 1
          AND id <> ?
    """);
    if (!com.app.util.AccessContext.isSystemSuperAdmin()) {
        sql.append(" AND direction_id = (SELECT direction_id FROM users WHERE id = ?) ");
    }
    sql.append(" AND role IN (").append(placeholders).append(") ORDER BY username");

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql.toString())) {
        int idx = 1;
        ps.setInt(idx++, Session.getUserId());
        if (!com.app.util.AccessContext.isSystemSuperAdmin()) {
            ps.setInt(idx++, Session.getUserId());
        }
        for (String r : allowedRoles) {
            ps.setString(idx++, r);
        }
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));
            list.add(u);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return list;
}

public static void escalateTicketInternalHigherOnly(int ticketId, int toUserId, String reason) {
    int fromUserId = Session.getUserId();
    if (fromUserId == toUserId) {
        throw new RuntimeException("You cannot escalate to yourself.");
    }
    String fromRole = Session.getRole();
    String toRole = null;
    Integer toDirectionId = null;
    String sql = "SELECT role, direction_id FROM users WHERE id = ? AND active = 1";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, toUserId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            toRole = rs.getString("role");
            int v = rs.getInt("direction_id");
            if (!rs.wasNull()) {
                toDirectionId = v;
            }
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to validate escalation target: " + e.getMessage(), e);
    }
    if (toRole == null) {
        throw new RuntimeException("Selected target user is invalid.");
    }

    Set<String> allowedRoles = com.app.util.RoleFlowUtil.getAllowedInternalEscalationRoles(fromRole);
    if (!allowedRoles.contains(com.app.util.RoleFlowUtil.normalize(toRole))) {
        throw new RuntimeException("Escalation is not allowed for this role target.");
    }
    if (!com.app.util.AccessContext.isSystemSuperAdmin()) {
        Integer myDirectionId = getCurrentUserDirectionId();
        if (myDirectionId == null || toDirectionId == null || !myDirectionId.equals(toDirectionId)) {
            throw new RuntimeException("Internal escalation must stay in your direction.");
        }
    }
    escalateTicket(ticketId, fromUserId, toUserId,
            reason == null || reason.isBlank() ? "Internal escalation" : reason.trim());
}

public static boolean canCreateExternalEscalation() {
    String role = Session.getRole();
    if (role == null) return false;
    return "ADMIN".equalsIgnoreCase(role)
            || "DIRECTEUR".equalsIgnoreCase(role)
            || "SOUS-DIRECTEUR".equalsIgnoreCase(role)
            || "INSPECTEUR".equalsIgnoreCase(role)
            || "CONTROLEUR".equalsIgnoreCase(role)
            || "VERIFICATEUR".equalsIgnoreCase(role)
            || "VERIFICATEUR-ASSISTANT".equalsIgnoreCase(role);
}

public static boolean canViewExternalEscalations() {
    String role = Session.getRole();
    if (role == null) return false;
    return "ADMIN".equalsIgnoreCase(role)
            || "DIRECTEUR".equalsIgnoreCase(role)
            || "SOUS-DIRECTEUR".equalsIgnoreCase(role)
            || "INSPECTEUR".equalsIgnoreCase(role)
            || "CONTROLEUR".equalsIgnoreCase(role);
}

public static boolean canAssignExternalEscalations() {
    String role = Session.getRole();
    if (role == null) return false;
    return "ADMIN".equalsIgnoreCase(role)
            || "DIRECTEUR".equalsIgnoreCase(role)
            || "SOUS-DIRECTEUR".equalsIgnoreCase(role)
            || "INSPECTEUR".equalsIgnoreCase(role);
}

public static ObservableList<User> getExternalEscalationApproversForCurrentUser() {
    ObservableList<User> list = FXCollections.observableArrayList();
    String myRole = Session.getRole();
    Set<String> approverRoles = com.app.util.RoleFlowUtil.getExternalEscalationApproverRoles(myRole);
    if (approverRoles.isEmpty()) {
        return list;
    }
    String placeholders = String.join(",", java.util.Collections.nCopies(approverRoles.size(), "?"));
    String sql = """
        SELECT id, username, role
        FROM users
        WHERE active = 1
          AND id <> ?
          AND direction_id = (SELECT direction_id FROM users WHERE id = ?)
          AND role IN (%s)
        ORDER BY username
    """.formatted(placeholders);
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        int idx = 1;
        ps.setInt(idx++, Session.getUserId());
        ps.setInt(idx++, Session.getUserId());
        for (String r : approverRoles) {
            ps.setString(idx++, r);
        }
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));
            list.add(u);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return list;
}

public static void escalateTicketExternally(int ticketId, int toDirectionId, int toSousDirectionId, String note) {
    if (!canCreateExternalEscalation()) {
        throw new RuntimeException("You are not allowed to create external escalations.");
    }
    Integer fromDirectionId = getCurrentUserDirectionId();
    if (toDirectionId <= 0 || toSousDirectionId <= 0) {
        throw new RuntimeException("Direction and sous-direction are required for external escalation.");
    }
    String sql = """
        INSERT INTO ticket_external_escalations(
            ticket_id, from_direction_id, to_direction_id, to_sous_direction_id, escalated_by, status, note, created_at
        ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, datetime('now','localtime'))
    """;
    try (Connection c = DB.getConnection()) {
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            if (fromDirectionId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, fromDirectionId);
            ps.setInt(3, toDirectionId);
            ps.setInt(4, toSousDirectionId);
            ps.setInt(5, Session.getUserId());
            ps.setString(6, note == null ? null : note.trim());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET status='ESCALATED' WHERE id=?")) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }
        c.commit();
    } catch (Exception e) {
        throw new RuntimeException("Failed to escalate externally: " + e.getMessage(), e);
    }
}

public static void requestExternalEscalationApproval(int ticketId,
                                                     int toDirectionId,
                                                     int toSousDirectionId,
                                                     int approverUserId,
                                                     String note) {
    if (!canCreateExternalEscalation()) {
        throw new RuntimeException("You are not allowed to request external escalations.");
    }
    Integer fromDirectionId = getCurrentUserDirectionId();
    String myRole = Session.getRole();
    Set<String> allowedApproverRoles = com.app.util.RoleFlowUtil.getExternalEscalationApproverRoles(myRole);
    if (allowedApproverRoles.isEmpty()) {
        throw new RuntimeException("This role does not require external escalation approval.");
    }
    String approverRole = null;
    Integer approverDirectionId = null;
    String approverSql = "SELECT role, direction_id FROM users WHERE id=? AND active=1";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(approverSql)) {
        ps.setInt(1, approverUserId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            approverRole = rs.getString("role");
            int v = rs.getInt("direction_id");
            if (!rs.wasNull()) approverDirectionId = v;
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to validate approver: " + e.getMessage(), e);
    }
    if (approverRole == null
            || !allowedApproverRoles.contains(com.app.util.RoleFlowUtil.normalize(approverRole))) {
        throw new RuntimeException("Selected approver role is not allowed.");
    }
    if (fromDirectionId == null || approverDirectionId == null || !fromDirectionId.equals(approverDirectionId)) {
        throw new RuntimeException("Approver must be in your direction.");
    }
    String sql = """
        INSERT INTO ticket_external_escalations(
            ticket_id, from_direction_id, to_direction_id, to_sous_direction_id,
            escalated_by, status, note, requested_approver_id, created_at
        ) VALUES (?, ?, ?, ?, ?, 'PENDING_APPROVAL', ?, ?, datetime('now','localtime'))
    """;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, ticketId);
        if (fromDirectionId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, fromDirectionId);
        ps.setInt(3, toDirectionId);
        ps.setInt(4, toSousDirectionId);
        ps.setInt(5, Session.getUserId());
        ps.setString(6, note == null ? null : note.trim());
        ps.setInt(7, approverUserId);
        ps.executeUpdate();
    } catch (Exception e) {
        throw new RuntimeException("Failed to create external escalation approval request: " + e.getMessage(), e);
    }

    NotificationDAO.create(
            approverUserId,
            "External Escalation Approval",
            Session.getUsername() + " requested external escalation approval for " + TicketUtil.formatTicketRef(ticketId),
            "EXTERNAL_ESCALATION_APPROVAL",
            "TICKET",
            ticketId,
            TicketUtil.formatTicketRef(ticketId)
    );
}

public static void approveExternalEscalationRequest(int escalationId) {
    String role = Session.getRole();
    boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
    String fetch = """
        SELECT requested_approver_id, status
        FROM ticket_external_escalations
        WHERE id = ?
    """;
    Integer requestedApprover = null;
    String status = null;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(fetch)) {
        ps.setInt(1, escalationId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            int v = rs.getInt("requested_approver_id");
            if (!rs.wasNull()) requestedApprover = v;
            status = rs.getString("status");
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to load request: " + e.getMessage(), e);
    }
    if (!"PENDING_APPROVAL".equalsIgnoreCase(status)) {
        throw new RuntimeException("This external escalation is not pending approval.");
    }
    if (!isAdmin && (requestedApprover == null || requestedApprover != Session.getUserId())) {
        throw new RuntimeException("You are not allowed to approve this request.");
    }
    String update = """
        UPDATE ticket_external_escalations
        SET status='PENDING',
            approved_by=?,
            approved_at=datetime('now','localtime')
        WHERE id=?
    """;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(update)) {
        ps.setInt(1, Session.getUserId());
        ps.setInt(2, escalationId);
        ps.executeUpdate();
    } catch (Exception e) {
        throw new RuntimeException("Failed to approve external escalation: " + e.getMessage(), e);
    }
}

public static ObservableList<ExternalEscalation> getExternalEscalationsForCurrentUser() {
    ObservableList<ExternalEscalation> list = FXCollections.observableArrayList();
    if (!canViewExternalEscalations()) {
        return list;
    }
    String role = Session.getRole();
    StringBuilder sql = new StringBuilder("""
        SELECT e.id,
               e.ticket_id,
               fd.name AS from_direction,
               td.id AS to_direction_id,
               td.name AS to_direction,
               sd.id AS to_sous_direction_id,
               sd.name AS to_sous_direction,
               ub.username AS escalated_by_name,
               e.requested_approver_id,
               ur.username AS requested_approver_name,
               e.status,
               e.created_at,
               e.assigned_to,
               ua.username AS assigned_to_name
        FROM ticket_external_escalations e
        LEFT JOIN directions fd ON fd.id = e.from_direction_id
        JOIN directions td ON td.id = e.to_direction_id
        JOIN sous_directions sd ON sd.id = e.to_sous_direction_id
        JOIN users ub ON ub.id = e.escalated_by
        LEFT JOIN users ur ON ur.id = e.requested_approver_id
        LEFT JOIN users ua ON ua.id = e.assigned_to
        WHERE e.status IN ('PENDING_APPROVAL', 'PENDING', 'ESCALATED')
    """);
    if ("CONTROLEUR".equalsIgnoreCase(role)) {
        sql.append(" AND e.status = 'PENDING_APPROVAL' AND e.requested_approver_id = ? ");
    } else if ("INSPECTEUR".equalsIgnoreCase(role) || "SOUS-DIRECTEUR".equalsIgnoreCase(role)) {
        sql.append(" AND (e.to_direction_id = ? OR e.requested_approver_id = ?) ");
    } else if (!"ADMIN".equalsIgnoreCase(role)) {
        sql.append(" AND e.to_direction_id = ? ");
    }
    sql.append(" ORDER BY e.created_at DESC, e.id DESC ");

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql.toString())) {
        if ("CONTROLEUR".equalsIgnoreCase(role)) {
            ps.setInt(1, Session.getUserId());
        } else if ("INSPECTEUR".equalsIgnoreCase(role) || "SOUS-DIRECTEUR".equalsIgnoreCase(role)) {
            Integer myDir = getCurrentUserDirectionId();
            if (myDir == null) return list;
            ps.setInt(1, myDir);
            ps.setInt(2, Session.getUserId());
        } else if (!"ADMIN".equalsIgnoreCase(role)) {
            Integer myDir = getCurrentUserDirectionId();
            if (myDir == null) return list;
            ps.setInt(1, myDir);
        }
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            ExternalEscalation x = new ExternalEscalation();
            x.setId(rs.getInt("id"));
            int tId = rs.getInt("ticket_id");
            x.setTicketId(tId);
            x.setTicketRef(TicketUtil.formatTicketRef(tId));
            x.setFromDirection(rs.getString("from_direction"));
            x.setToDirectionId(rs.getInt("to_direction_id"));
            x.setToDirection(rs.getString("to_direction"));
            x.setToSousDirectionId(rs.getInt("to_sous_direction_id"));
            x.setToSousDirection(rs.getString("to_sous_direction"));
            x.setEscalatedBy(rs.getString("escalated_by_name"));
            int reqApprover = rs.getInt("requested_approver_id");
            if (!rs.wasNull()) {
                x.setRequestedApproverId(reqApprover);
            }
            x.setRequestedApproverName(rs.getString("requested_approver_name"));
            x.setStatus(rs.getString("status"));
            x.setCreatedAt(rs.getString("created_at"));
            int assignedTo = rs.getInt("assigned_to");
            if (!rs.wasNull()) {
                x.setAssignedTo(assignedTo);
            }
            x.setAssignedToName(rs.getString("assigned_to_name"));
            list.add(x);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return list;
}

public static void assignExternalEscalation(int escalationId, int assignToUserId) {
    if (!canAssignExternalEscalations()) {
        throw new RuntimeException("You are not allowed to assign external escalations.");
    }
    String role = Session.getRole();
    Integer myDirection = getCurrentUserDirectionId();
    String fetchSql = """
        SELECT ticket_id, to_direction_id, to_sous_direction_id, status
        FROM ticket_external_escalations
        WHERE id = ?
    """;
    Integer ticketId = null;
    Integer toDirectionId = null;
    Integer toSousDirectionId = null;
    String escalationStatus = null;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(fetchSql)) {
        ps.setInt(1, escalationId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            ticketId = rs.getInt("ticket_id");
            toDirectionId = rs.getInt("to_direction_id");
            toSousDirectionId = rs.getInt("to_sous_direction_id");
            escalationStatus = rs.getString("status");
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to load external escalation: " + e.getMessage(), e);
    }
    if (ticketId == null || toDirectionId == null) {
        throw new RuntimeException("External escalation request not found.");
    }
    if (!"PENDING".equalsIgnoreCase(escalationStatus) && !"ESCALATED".equalsIgnoreCase(escalationStatus)) {
        throw new RuntimeException("External escalation is not ready for assignment.");
    }
    if (!"ADMIN".equalsIgnoreCase(role) && (myDirection == null || !myDirection.equals(toDirectionId))) {
        throw new RuntimeException("You cannot manage external escalations for another direction.");
    }

    Integer assigneeDirection = null;
    Integer assigneeSousDirection = null;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement("SELECT direction_id, sous_direction_id FROM users WHERE id=? AND active=1")) {
        ps.setInt(1, assignToUserId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            int dir = rs.getInt("direction_id");
            if (!rs.wasNull()) assigneeDirection = dir;
            int sous = rs.getInt("sous_direction_id");
            if (!rs.wasNull()) assigneeSousDirection = sous;
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to validate selected user: " + e.getMessage(), e);
    }
    if (assigneeDirection == null || !assigneeDirection.equals(toDirectionId)
            || assigneeSousDirection == null || !assigneeSousDirection.equals(toSousDirectionId)) {
        throw new RuntimeException("Selected user must belong to the target direction and sous-direction.");
    }

    String deactivateAssignments = "UPDATE ticket_assignments SET active=0 WHERE ticket_id=? AND active=1";
    String updateTicket = "UPDATE tickets SET assigned_to=?, status='ASSIGNED' WHERE id=?";
    String insertAssignment = """
        INSERT INTO ticket_assignments(
            ticket_id, agent_id, assigned_to, assigned_by, status, active, assigned_at
        ) VALUES (?, ?, ?, ?, 'ASSIGNED', 1, datetime('now','localtime'))
    """;
    String updateEsc = """
        UPDATE ticket_external_escalations
        SET status='ASSIGNED', assigned_to=?, assigned_at=datetime('now','localtime')
        WHERE id=?
    """;
    try (Connection c = DB.getConnection()) {
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement(deactivateAssignments)) {
            ps.setInt(1, ticketId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(updateTicket)) {
            ps.setInt(1, assignToUserId);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(insertAssignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, assignToUserId); // compatibility
            ps.setInt(3, assignToUserId);
            ps.setInt(4, Session.getUserId());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(updateEsc)) {
            ps.setInt(1, assignToUserId);
            ps.setInt(2, escalationId);
            ps.executeUpdate();
        }
        c.commit();
    } catch (Exception e) {
        throw new RuntimeException("Failed to assign external escalation: " + e.getMessage(), e);
    }
}


    
}





