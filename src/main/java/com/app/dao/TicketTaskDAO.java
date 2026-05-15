    package com.app.dao;

    import com.app.auth.Session;
import com.app.controller.MainController;
import com.app.controller.TaskDetailsController;
import static com.app.dao.TicketAssignmentDAO.close;
import com.app.model.Escalation;
import com.app.model.TicketEvent;
    import com.app.model.TicketTask;
    import com.app.model.User;
import com.app.security.RoleUtil;
import com.app.service.AuditService;
    import com.app.util.DB;
import com.app.util.DbConfig;
import com.app.util.RoleFlowUtil;
import com.app.util.SqlDialect;

    import java.sql.*;
import java.time.LocalDateTime;
    import java.util.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

    public class TicketTaskDAO {

    public static List<TicketTask> getTasksByTicket(int ticketId) {

    List<TicketTask> list = new ArrayList<>();

    String sql = """
    SELECT 
        t.id,
        t.ticket_id,
        t.title,
        t.description,
        t.parent_task_id,
        t.assigned_to,
        t.created_at,
        t.assigned_by,
        t.status,
        t.started_at,
        t.completed_at,
        t.duration_minutes,
        COALESCE(u.username, 'Unassigned') AS assigned_name,
        COALESCE(ua.username, '-') AS assigned_by_name

    FROM ticket_tasks t
    LEFT JOIN users u ON t.assigned_to = u.id
    LEFT JOIN users ua ON t.assigned_by = ua.id

    WHERE t.ticket_id = ?
      AND LOWER(TRIM(COALESCE(t.title, ''))) != 'task assignment'

    ORDER BY t.id DESC
""";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketTask task = new TicketTask();

            // =========================
            // CORE DATA
            // =========================
            task.setId(rs.getInt("id"));
            task.setTicketId(rs.getInt("ticket_id"));
            task.setTitle(rs.getString("title"));
            task.setStatus(rs.getString("status"));
            try {
                String created = rs.getString("created_at");
                if (created != null && !created.isBlank()) {
                    task.setCreatedAt(java.time.LocalDateTime.parse(created.replace(" ", "T")));
                }
            } catch (Exception e) {
                System.out.println("⚠️ CREATED parse error: " + rs.getString("created_at"));
            }

            // =========================
            // DURATION (SAFE)
            // =========================
            Object duration = rs.getObject("duration_minutes");

            if (duration != null) {
                task.setDurationMinutes(((Number) duration).intValue());
            } else {
                task.setDurationMinutes(0); // 👈 important fallback
            }

            // =========================
            // PARENT
            // =========================
            Object parentObj = rs.getObject("parent_task_id");
            task.setParentTaskId(parentObj != null ? ((Number) parentObj).intValue() : null);

            // =========================
            // ASSIGNED USER
            // =========================
            int assignedId = rs.getInt("assigned_to");

            if (rs.wasNull() || assignedId == 0) {
                task.setAssignedTo(0);
                task.setAssignedToName("Unassigned");
            } else {
                task.setAssignedTo(assignedId);
                task.setAssignedToName(rs.getString("assigned_name"));
            }
            task.setAssignedBy(rs.getInt("assigned_by"));
            task.setCreatedByName(rs.getString("assigned_by_name"));

            // =========================
            // START DATE
            // =========================
            try {
                String started = rs.getString("started_at");

                if (started != null && !started.isBlank()) {
                    task.setStartedAt(
                        java.time.LocalDateTime.parse(started.replace(" ", "T"))
                    );
                }
            } catch (Exception e) {
                System.out.println("⚠️ START parse error: " + rs.getString("started_at"));
            }

            // =========================
            // CLOSE DATE (🔥 FIX HERE)
            // =========================
            try {
                String closed = rs.getString("completed_at");
System.out.println("DEBUG closed = " + closed);
                if (closed != null && !closed.isBlank()) {
                    task.setClosedAt(
                        java.time.LocalDateTime.parse(closed.replace(" ", "T"))
                    );
                }
            } catch (Exception e) {
                System.out.println("⚠️ CLOSE parse error: " + rs.getString("completed_at"));
            }

            list.add(task);
        }

        System.out.println("✅ TASKS LOADED: " + list.size());

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

    


    private static int insertTask(Connection c,
                              int ticketId,
                              String title,
                              Integer parentId,
                              int assignedTo,
                              int assignedBy,
                              String description) throws SQLException {

    String sql = """
        INSERT INTO ticket_tasks (
            ticket_id,
            parent_task_id,
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

        if (parentId == null) {
            ps.setNull(2, Types.INTEGER);
        } else {
            ps.setInt(2, parentId);
        }

        ps.setString(3, title);
        ps.setString(4, description); // ✅ FIXED (you were missing this)
        ps.setInt(5, assignedTo);     // ✅ assigned_to
        ps.setInt(6, assignedBy);     // ✅ assigned_by

        int affected = ps.executeUpdate();

        if (affected == 0) {
            throw new SQLException("Creating task failed, no rows affected.");
        }

        ResultSet rs = ps.getGeneratedKeys();

        if (rs.next()) {
            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Creating task failed, no numeric ID obtained.");
            }
            int id = generatedId.intValue();
            System.out.println("✅ TASK CREATED ID = " + id);
            return id;
        } else {
            throw new SQLException("Creating task failed, no ID obtained.");
        }
    }
}


    public static void updateStatus(int taskId, String status) {

    String sql = "UPDATE ticket_tasks SET status = ? WHERE id = ?";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setString(1, status);
        ps.setInt(2, taskId);
        ps.executeUpdate();

        System.out.println("Task updated: " + taskId + " -> " + status);

    } catch (Exception e) {
        e.printStackTrace();
    }
    }
    
    public static void addTask(int ticketId, int assignedTo, String title) {

    String sql = """
        INSERT INTO ticket_tasks
        (ticket_id, title, assigned_to, assigned_by, status, created_at)
        VALUES (?, ?, ?, ?, 'PENDING', datetime('now','localtime'))
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setString(2, title);
        ps.setInt(3, assignedTo);
        ps.setInt(4, Session.getUserId());

        ps.executeUpdate();

        System.out.println("✅ Manual task added: " + title);

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
public static boolean allAgentsHaveTasks(int ticketId, List<User> agents) {

    String sql = """
        SELECT assigned_to, COUNT(*) as total
        FROM ticket_tasks
        WHERE ticket_id = ?
        GROUP BY assigned_to
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ResultSet rs = ps.executeQuery();

        List<Integer> covered = new ArrayList<>();

        while (rs.next()) {
            covered.add(rs.getInt("assigned_to"));
        }

        for (User u : agents) {
            if (!covered.contains(u.getId())) {
                return false;
            }
        }

        return true;

    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
}

    /**
     * Users who have at least one <strong>completed</strong> normal task on this ticket
     * (excludes synthetic "task assignment" rows). Used for sequential multi-assign workflow.
     */
    public static Set<Integer> getUserIdsWithCompletedNormalTasks(int ticketId) {
        Set<Integer> ids = new HashSet<>();
        String sql = """
            SELECT DISTINCT assigned_to
            FROM ticket_tasks
            WHERE ticket_id = ?
              AND LOWER(TRIM(COALESCE(status, ''))) = 'completed'
              AND LOWER(TRIM(COALESCE(title, ''))) != 'task assignment'
            """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int uid = rs.getInt("assigned_to");
                if (!rs.wasNull() && uid > 0) {
                    ids.add(uid);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ids;
    }



public static void createTask(
        int ticketId,
        String title,
        String description,
        int userId,
        String attachmentPath
) {

    String sql = """
        INSERT INTO ticket_tasks(
            ticket_id,
            title,
            description,
            assigned_to,
            assigned_by,
            attachment_path,
            status
        )
        VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setString(2, title);
        ps.setString(3, description);
        ps.setInt(4, userId);

        // ✅ keep your existing logic
        ps.setInt(5, com.app.auth.Session.getUserId());

        ps.setString(6, attachmentPath);

        ps.executeUpdate();

        System.out.println("✅ Task created with attachment: " + title);

    } catch (Exception e) {
        e.printStackTrace();
    }
}



public static ObservableList<TicketTask> getTasksForAgent(int userId) {

    ObservableList<TicketTask> list = FXCollections.observableArrayList();
    String sql = """
    SELECT 
        tt.*,
        u.username AS assigned_to_name,
        t.title AS ticket_title
    FROM ticket_tasks tt
    LEFT JOIN users u ON u.id = tt.assigned_to
    LEFT JOIN tickets t ON t.id = tt.ticket_id
    WHERE tt.assigned_to = ?
      AND LOWER(TRIM(COALESCE(tt.title, ''))) != 'task assignment'
    ORDER BY tt.created_at DESC
""";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketTask task = new TicketTask();

            // ================= BASIC =================
            task.setId(rs.getInt("id"));
            task.setTicketId(rs.getInt("ticket_id"));
            Object parentObj = rs.getObject("parent_task_id");
            task.setParentTaskId(parentObj == null ? null : ((Number) parentObj).intValue());
            task.setTitle(rs.getString("title"));
            task.setDescription(rs.getString("description"));
            task.setAssignedTo(rs.getInt("assigned_to"));
            task.setAssignedToName(rs.getString("assigned_to_name"));
            task.setAssignedBy(rs.getInt("assigned_by"));
            task.setStatus(rs.getString("status"));
            task.setAttachmentPath(rs.getString("attachment_path"));
            task.setTicketTitle(rs.getString("ticket_title"));

            // ================= CREATED =================
            String created = rs.getString("created_at");
            if (created != null && !created.isBlank()) {
                try {
                    task.setCreatedAt(
                        java.time.LocalDateTime.parse(created.replace(" ", "T"))
                    );
                } catch (Exception e) {
                    System.out.println("⚠️ CREATED parse error: " + created);
                }
            }

            // ================= START (FIXED) =================
            String started = rs.getString("started_at");
            if (started != null && !started.isBlank()) {
                try {
                    task.setStartedAt(
                        java.time.LocalDateTime.parse(started.replace(" ", "T"))
                    );
                } catch (Exception e) {
                    System.out.println("⚠️ START parse error: " + started);
                }
            }

            // ================= CLOSE (🔥 FIXED) =================
            String closed = rs.getString("completed_at");
            if (closed != null && !closed.isBlank()) {
                try {
                    task.setClosedAt(
                        java.time.LocalDateTime.parse(closed.replace(" ", "T"))
                    );
                } catch (Exception e) {
                    System.out.println("⚠️ CLOSE parse error: " + closed);
                }
            }

            // ================= DURATION =================
            Object duration = rs.getObject("duration_minutes");
            if (duration != null) {
                task.setDurationMinutes(((Number) duration).intValue());
            } else {
                task.setDurationMinutes(0);
            }

            list.add(task);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}





public static void updateTaskDetails(int taskId, String status, int progress, String description) {

    String sql = """
        UPDATE ticket_tasks
        SET status = ?, description = ?, progress = ?
        WHERE id = ?
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setString(1, status);
        ps.setString(2, description);
        ps.setInt(3, progress);
        ps.setInt(4, taskId);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static void logTaskEvent(int taskId, String type, String description) {

    String sql = DbConfig.isOracle()
            ? """
        INSERT INTO task_events(task_id, user_id, event_type, description)
        VALUES (?, ?, ?, ?)
    """
            : """
        INSERT INTO task_events(task_id, username, type, description)
        VALUES (?, ?, ?, ?)
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        if (DbConfig.isOracle()) {
            int userId = Session.getUserId();
            if (userId <= 0) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, userId);
            }
            ps.setString(3, type);
            ps.setString(4, description);
        } else {
            ps.setString(2, Session.getUsername());
            ps.setString(3, type);
            ps.setString(4, description);
        }

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static List<TicketEvent> getTaskEvents(int taskId) {

    List<TicketEvent> list = new ArrayList<>();

    String sql = DbConfig.isOracle()
            ? """
        SELECT
            COALESCE(u.username, '-') AS username,
            te.event_type AS type,
            te.description AS description,
            te.created_at AS created_at
        FROM task_events te
        LEFT JOIN users u ON u.id = te.user_id
        WHERE te.task_id = ?
        ORDER BY te.created_at DESC
    """
            : """
        SELECT
            username,
            type,
            description,
            created_at
        FROM task_events
        WHERE task_id = ?
        ORDER BY created_at DESC
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, taskId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketEvent ev = new TicketEvent();

            ev.setUsername(rs.getString("username"));
            ev.setType(rs.getString("type"));
            ev.setDescription(rs.getString("description"));
            ev.setCreatedAt(rs.getString("created_at")); // ✅ FIX

            list.add(ev);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static List<TicketEvent> getTaskTrackingEvents(int taskId) {
    List<TicketEvent> list = new ArrayList<>();
    String sql = """
        SELECT t.created_at AS created_at,
               COALESCE(ub.username, '-') AS assigned_by_name,
               COALESCE(ut.username, '-') AS assigned_to_name,
               COALESCE(t.title, 'Task') AS title
        FROM ticket_tasks t
        LEFT JOIN users ub ON ub.id = t.assigned_by
        LEFT JOIN users ut ON ut.id = t.assigned_to
        WHERE t.id = ?
    """;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, taskId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            TicketEvent ev = new TicketEvent();
            ev.setUsername(rs.getString("assigned_by_name"));
            ev.setType("TASK_CREATED");
            ev.setDescription("Created task \"" + rs.getString("title")
                    + "\" and assigned to " + rs.getString("assigned_to_name"));
            ev.setCreatedAt(rs.getString("created_at"));
            list.add(ev);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    list.addAll(getTaskEvents(taskId));
    return list;
}

public static void assignUsersToTask(int taskId, List<Integer> userIds) {

    try (Connection c = DB.getConnection()) {

        for (int userId : userIds) {

            User targetUser = UserDAO.findById(userId);
            if (targetUser == null) continue;

            String fromRole = Session.getRole();
            String toRole = targetUser.getRole();

            int fromLevel = RoleUtil.getLevel(fromRole);
            int toLevel = RoleUtil.getLevel(toRole);

            // 🔥 ESCALATE if higher
            if (toLevel > fromLevel) {

                escalateTask(taskId, userId);
                continue;
            }

            // ✅ NORMAL ASSIGN
            String sql = """
                INSERT INTO task_assignments(task_id, user_id)
                VALUES (?, ?)
            """;

            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, taskId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        }

        System.out.println("✅ Assignment + escalation processed");

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static String getAssignedUsersNames(int taskId) {

    String sql = """
        SELECT u.username
        FROM ticket_tasks t
        LEFT JOIN users u ON t.assigned_to = u.id
        WHERE t.id = ?
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, taskId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getString("username");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}



public static String getSlaStatus(int taskId) {

    String sql = "SELECT due_date FROM ticket_tasks WHERE id = ?";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {

            String due = rs.getString("due_date");

            if (due == null) return "NO SLA";

            LocalDateTime dueDate = LocalDateTime.parse(due);
            long hoursLeft = java.time.Duration
                    .between(LocalDateTime.now(), dueDate)
                    .toHours();

            if (hoursLeft < 0) return "BREACHED";
            if (hoursLeft < 4) return "WARNING";

            return "OK";
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return "UNKNOWN";
}


public static void createNotification(int userId, String message) {

    String sql = """
        INSERT INTO notifications(user_id, title, "message", type)
        VALUES (?, ?, ?, ?)
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.setString(2, "Task Notification");
        ps.setString(3, message);
        ps.setString(4, "TASK");
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


public static List<String> getNotifications(int userId) {

    List<String> list = new ArrayList<>();

    String sql = """
        SELECT "message" AS message_text FROM notifications
        WHERE user_id = ? AND is_read = 0
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            list.add(rs.getString("message_text"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}


public static void startTask(int taskId) {

    Integer ticketId = null;
    Integer assignedTo = null;
    Integer assignedBy = null;
    String taskTitle = null;
    String metaSql = "SELECT ticket_id, assigned_to, assigned_by, title FROM ticket_tasks WHERE id = ?";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(metaSql)) {
        ps.setInt(1, taskId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            ticketId = rs.getInt("ticket_id");
            assignedTo = rs.getInt("assigned_to");
            assignedBy = rs.getInt("assigned_by");
            taskTitle = rs.getString("title");
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    String sql = """
        UPDATE ticket_tasks
        SET 
            status = 'IN_PROGRESS',
            started_at = datetime('now','localtime')
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        ps.executeUpdate();

        System.out.println("✅ Task started");
        logTaskEvent(taskId, "TASK_STARTED", "Task started by " + Session.getUsername());

        if (assignedBy != null
                && assignedTo != null
                && assignedTo == Session.getUserId()
                && assignedBy > 0
                && assignedBy != Session.getUserId()) {
            NotificationDAO.create(
                    assignedBy,
                    "Task Started",
                    Session.getUsername() + " started task \"" + (taskTitle != null ? taskTitle : ("#" + taskId))
                            + "\" for ticket " + com.app.util.TicketUtil.formatTicketRef(ticketId != null ? ticketId : 0),
                    "TASK_STARTED",
                    "TASK",
                    taskId,
                    ticketId != null ? com.app.util.TicketUtil.formatTicketRef(ticketId) : null
            );
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}


public static void completeTask(int taskId) {

    Integer ticketId = null;
    Integer assignedTo = null;
    Integer assignedBy = null;
    String taskTitle = null;
    String metaSql = "SELECT ticket_id, assigned_to, assigned_by, title FROM ticket_tasks WHERE id = ?";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(metaSql)) {
        ps.setInt(1, taskId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            ticketId = rs.getInt("ticket_id");
            assignedTo = rs.getInt("assigned_to");
            assignedBy = rs.getInt("assigned_by");
            taskTitle = rs.getString("title");
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    String sql =
        "UPDATE ticket_tasks\n" +
        "SET status='COMPLETED',\n" +
        "    completed_at=datetime('now','localtime'),\n" +
        "    duration_minutes =\n" +
        "        CASE\n" +
        "            WHEN started_at IS NOT NULL\n" +
        "            THEN CASE\n" +
        "                WHEN (" + SqlDialect.ceilMinutesBetweenNowAnd("started_at") + ") < 1 THEN 1\n" +
        "                ELSE CAST(" + SqlDialect.ceilMinutesBetweenNowAnd("started_at") + " AS INTEGER)\n" +
        "            END\n" +
        "            ELSE NULL\n" +
        "        END\n" +
        "WHERE id=?";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        ps.executeUpdate();
        logTaskEvent(taskId, "TASK_COMPLETED", "Task completed by " + Session.getUsername());

        if (assignedBy != null
                && assignedTo != null
                && assignedTo == Session.getUserId()
                && assignedBy > 0
                && assignedBy != Session.getUserId()) {
            NotificationDAO.create(
                    assignedBy,
                    "Task Completed",
                    Session.getUsername() + " completed task \"" + (taskTitle != null ? taskTitle : ("#" + taskId))
                            + "\" for ticket " + com.app.util.TicketUtil.formatTicketRef(ticketId != null ? ticketId : 0),
                    "TASK_COMPLETED",
                    "TASK",
                    taskId,
                    ticketId != null ? com.app.util.TicketUtil.formatTicketRef(ticketId) : null
            );
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static void reassignTask(int taskId, int newUserId) {
    
    

    try (Connection c = DB.getConnection()) {

        User targetUser = UserDAO.findById(newUserId);

        if (targetUser == null) {
            throw new RuntimeException("Target user not found");
        }

        String fromRole = Session.getRole();
        String toRole = targetUser.getRole();

        int fromLevel = RoleUtil.getLevel(fromRole);
        int toLevel = RoleUtil.getLevel(toRole);

        // ============================
        // 🔥 ESCALATION CASE
        // ============================
        if (toLevel > fromLevel) {

            System.out.println("⚠ Escalating task instead of assigning...");

            escalateTask(taskId, newUserId);

            return;
        }

        // ============================
        // ✅ NORMAL ASSIGNMENT
        // ============================
        String sql = """
            UPDATE ticket_tasks
            SET assigned_to = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newUserId);
            ps.setInt(2, taskId);
            ps.executeUpdate();
        }

        System.out.println("✅ Task reassigned");
        logTaskEvent(taskId, "TASK_REASSIGNED", "Task reassigned to " + targetUser.getUsername());

    } catch (Exception e) {
        e.printStackTrace();
    }
}




public static void escalateTask(int taskId, int toUserId) {

    String sql = """
        INSERT INTO ticket_escalations(
            ticket_id,
            escalated_by,
            escalated_to,
            reason
        )
        VALUES (
            (SELECT ticket_id FROM ticket_tasks WHERE id=?),
            ?,
            ?,
            ?
        )
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        ps.setInt(2, Session.getUserId());
        ps.setInt(3, toUserId);
        ps.setString(4, "Escalated due to higher role");

        ps.executeUpdate();

        System.out.println("🚨 Task escalated to userId: " + toUserId);

    } catch (Exception e) {
        e.printStackTrace();
    }
}



public static List<Integer> getAssignedUserIds(int taskId) {

    List<Integer> list = new ArrayList<>();

    String sql = """
        SELECT user_id
        FROM ticket_task_assignments
        WHERE task_id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            list.add(rs.getInt("user_id"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static int createTaskAndReturnId(
        int ticketId,
        String title,
        int assignedTo,
        String description) {

    String sql = """
        INSERT INTO ticket_tasks (
            ticket_id,
            title,
            description,
            assigned_to,
            assigned_by,
            status,
            created_at
        )
        VALUES (?, ?, ?, ?, ?, 'PENDING', datetime('now','localtime'))
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = DbConfig.isOracle()
                 ? c.prepareStatement(sql, new String[] { "ID" })
                 : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setInt(1, ticketId);
        ps.setString(2, title);
        ps.setString(3, description);
        ps.setInt(4, assignedTo);
        ps.setInt(5, Session.getUserId()); // 🔥 important

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();

        if (rs.next()) {
            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Creating task failed, no numeric ID obtained.");
            }
            int id = generatedId.intValue();
            System.out.println("✅ TASK CREATED ID = " + id);

            if (assignedTo > 0 && assignedTo != Session.getUserId()) {
                NotificationDAO.create(
                        assignedTo,
                        "New Task Assigned",
                        Session.getUsername() + " assigned you task \"" + title + "\" for ticket "
                                + com.app.util.TicketUtil.formatTicketRef(ticketId),
                        "TICKET_TASK_ASSIGNED",
                        "TASK",
                        id,
                        com.app.util.TicketUtil.formatTicketRef(ticketId)
                );
            }
            return id;
        }

        return -1;

    } catch (Exception e) {
        e.printStackTrace();
        return -1;
    }
}


public static ObservableList<TicketTask> getTasksByUser(int userId) {

    ObservableList<TicketTask> list = FXCollections.observableArrayList();

    String sql = """
        SELECT 
            id,
            title,
            status,
            started_at,
            completed_at,
            duration_minutes
        FROM ticket_tasks
        WHERE assigned_to = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketTask task = new TicketTask();

            task.setId(rs.getInt("id"));
            task.setTitle(rs.getString("title"));
            task.setStatus(rs.getString("status"));

            Timestamp start = rs.getTimestamp("started_at");
            if (start != null) {
                task.setStartedAt(start.toLocalDateTime());
            }

            Timestamp closed = rs.getTimestamp("completed_at"); 
            if (closed != null) {
                task.setClosedAt(closed.toLocalDateTime());
            }

            Object duration = rs.getObject("duration_minutes");

            if (duration != null) {
                task.setDurationMinutes(((Number) duration).intValue());
            }

            list.add(task);

            // 🔥 DEBUG
            System.out.println("Loaded Task: " + task.getTitle());
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static void closeTask(int taskId) {

    Integer ticketId = null;
    Integer assignedTo = null;
    Integer assignedBy = null;
    String taskTitle = null;
    String metaSql = "SELECT ticket_id, assigned_to, assigned_by, title FROM ticket_tasks WHERE id = ?";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(metaSql)) {
        ps.setInt(1, taskId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            ticketId = rs.getInt("ticket_id");
            assignedTo = rs.getInt("assigned_to");
            assignedBy = rs.getInt("assigned_by");
            taskTitle = rs.getString("title");
        }
    } catch (Exception e) {
        e.printStackTrace();
    }

    String sql =
        "UPDATE ticket_tasks\n" +
        "SET\n" +
        "    status = 'COMPLETED',\n" +
        "    completed_at = datetime('now','localtime'),\n" +
        "    duration_minutes =\n" +
        "        CASE\n" +
        "            WHEN started_at IS NOT NULL\n" +
        "            THEN CASE\n" +
        "                WHEN (" + SqlDialect.ceilMinutesBetweenNowAnd("started_at") + ") < 1 THEN 1\n" +
        "                ELSE CAST(" + SqlDialect.ceilMinutesBetweenNowAnd("started_at") + " AS INTEGER)\n" +
        "            END\n" +
        "            ELSE NULL\n" +
        "        END\n" +
        "WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        ps.executeUpdate();

        System.out.println("✅ Task closed with duration");

        if (assignedBy != null
                && assignedTo != null
                && assignedTo == Session.getUserId()
                && assignedBy > 0
                && assignedBy != Session.getUserId()) {
            NotificationDAO.create(
                    assignedBy,
                    "Task Completed",
                    Session.getUsername() + " completed task \"" + (taskTitle != null ? taskTitle : ("#" + taskId))
                            + "\" for ticket " + com.app.util.TicketUtil.formatTicketRef(ticketId != null ? ticketId : 0),
                    "TASK_COMPLETED",
                    "TASK",
                    taskId,
                    ticketId != null ? com.app.util.TicketUtil.formatTicketRef(ticketId) : null
            );
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public static ObservableList<TicketTask> getSubTasks(int parentTaskId) {

    ObservableList<TicketTask> list = FXCollections.observableArrayList();

    String sql = """
        SELECT t.*, u.username AS assigned_name
        FROM ticket_tasks t
        LEFT JOIN users u ON t.assigned_to = u.id
        WHERE t.parent_task_id = ?
        ORDER BY t.created_at DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, parentTaskId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            TicketTask task = new TicketTask();

            task.setId(rs.getInt("id"));
            task.setTitle(rs.getString("title"));
            task.setDescription(rs.getString("description"));
            task.setStatus(rs.getString("status"));
            task.setAssignedTo(rs.getInt("assigned_to"));
            task.setAssignedToName(rs.getString("assigned_name"));

            list.add(task);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

public static TicketTask getTaskById(int taskId) {
    String sql = """
        SELECT t.*, 
               COALESCE(u.username, 'Unassigned') AS assigned_name,
               COALESCE(ua.username, '-') AS assigned_by_name
        FROM ticket_tasks t
        LEFT JOIN users u ON t.assigned_to = u.id
        LEFT JOIN users ua ON t.assigned_by = ua.id
        WHERE t.id = ?
        LIMIT 1
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, taskId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            TicketTask task = new TicketTask();
            task.setId(rs.getInt("id"));
            task.setTicketId(rs.getInt("ticket_id"));
            task.setTitle(rs.getString("title"));
            task.setDescription(rs.getString("description"));
            task.setStatus(rs.getString("status"));
            task.setAssignedTo(rs.getInt("assigned_to"));
            task.setAssignedToName(rs.getString("assigned_name"));
            task.setAssignedBy(rs.getInt("assigned_by"));
            task.setCreatedByName(rs.getString("assigned_by_name"));
            String created = rs.getString("created_at");
            if (created != null && !created.isBlank()) {
                task.setCreatedAt(LocalDateTime.parse(created.replace(" ", "T")));
            }
            String started = rs.getString("started_at");
            if (started != null && !started.isBlank()) {
                task.setStartedAt(LocalDateTime.parse(started.replace(" ", "T")));
            }
            String completed = rs.getString("completed_at");
            if (completed != null && !completed.isBlank()) {
                task.setClosedAt(LocalDateTime.parse(completed.replace(" ", "T")));
            }
            Object duration = rs.getObject("duration_minutes");
            if (duration != null) {
                task.setDurationMinutes(((Number) duration).intValue());
            }
            return task;
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return null;
}




}