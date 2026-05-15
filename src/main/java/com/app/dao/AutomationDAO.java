package com.app.dao;

import com.app.auth.Session;
import com.app.service.EmailService;
import com.app.model.MonthlyReportSummary;
import com.app.model.ScheduledJob;
import com.app.model.User;
import com.app.util.DB;
import com.app.util.DbConfig;
import com.app.util.SqlDialect;
import com.app.util.TicketUtil;
import com.app.util.TimeUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

public class AutomationDAO {
    private static final DateTimeFormatter DB_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private record AgentMonthlyWork(
            int userId,
            String username,
            String role,
            int assignedCount,
            int startedCount,
            int closedCount,
            int tasksCompletedCount,
            int escalationsSentCount,
            int reassignmentsSentCount
    ) {}

    private static volatile boolean automatedJobAssigneesReady;

    private static void ensureAutomatedJobAssigneesInfrastructure() {
        if (automatedJobAssigneesReady) {
            return;
        }
        synchronized (AutomationDAO.class) {
            if (automatedJobAssigneesReady) {
                return;
            }
            try (Connection c = DB.getConnection()) {
                if (DbConfig.isOracle()) {
                    try (Statement st = c.createStatement()) {
                        st.execute("""
                                CREATE TABLE automated_job_assignees (
                                  job_id NUMBER NOT NULL,
                                  user_id NUMBER NOT NULL,
                                  CONSTRAINT pk_aja PRIMARY KEY (job_id, user_id),
                                  CONSTRAINT fk_aja_job FOREIGN KEY (job_id) REFERENCES automated_jobs(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_aja_user FOREIGN KEY (user_id) REFERENCES users(id)
                                )
                                """);
                    } catch (Exception e) {
                        String m = e.getMessage() != null ? e.getMessage() : "";
                        if (!m.contains("ORA-00955") && !m.contains("already been created")) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    try (Statement st = c.createStatement()) {
                        st.execute("""
                                CREATE TABLE IF NOT EXISTS automated_job_assignees (
                                  job_id INTEGER NOT NULL,
                                  user_id INTEGER NOT NULL,
                                  PRIMARY KEY (job_id, user_id),
                                  FOREIGN KEY (job_id) REFERENCES automated_jobs(id) ON DELETE CASCADE,
                                  FOREIGN KEY (user_id) REFERENCES users(id)
                                )
                                """);
                    }
                }
                backfillAutomatedJobAssignees(c);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            automatedJobAssigneesReady = true;
        }
    }

    private static void backfillAutomatedJobAssignees(Connection c) throws SQLException {
        if (DbConfig.isOracle()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    MERGE INTO automated_job_assignees t
                    USING (
                      SELECT id AS job_id, assignee_user_id AS user_id
                      FROM automated_jobs
                      WHERE assignee_user_id IS NOT NULL
                    ) s
                    ON (t.job_id = s.job_id AND t.user_id = s.user_id)
                    WHEN NOT MATCHED THEN
                      INSERT (job_id, user_id) VALUES (s.job_id, s.user_id)
                    """)) {
                ps.executeUpdate();
            }
        } else {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                        INSERT OR IGNORE INTO automated_job_assignees (job_id, user_id)
                        SELECT id, assignee_user_id FROM automated_jobs WHERE assignee_user_id IS NOT NULL
                        """);
            }
        }
    }

    private static Map<Integer, List<Integer>> loadAllJobAssigneeUserIds() {
        ensureAutomatedJobAssigneesInfrastructure();
        Map<Integer, List<Integer>> map = new HashMap<>();
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT job_id, user_id FROM automated_job_assignees ORDER BY job_id, user_id")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.computeIfAbsent(rs.getInt("job_id"), k -> new ArrayList<>()).add(rs.getInt("user_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, assignee_user_id FROM automated_jobs WHERE assignee_user_id IS NOT NULL")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int jid = rs.getInt("id");
                int uid = rs.getInt("assignee_user_id");
                List<Integer> list = map.computeIfAbsent(jid, k -> new ArrayList<>());
                if (!list.contains(uid)) {
                    list.add(uid);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    private static void attachAssigneesToScheduledJobs(List<ScheduledJob> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        Map<Integer, List<Integer>> map = loadAllJobAssigneeUserIds();
        for (ScheduledJob j : jobs) {
            List<Integer> ids = map.get(j.getId());
            if (ids == null || ids.isEmpty()) {
                Integer single = j.getAssigneeUserId();
                if (single != null) {
                    j.setAssigneeUserIds(List.of(single));
                }
            } else {
                j.setAssigneeUserIds(ids);
                if (j.getAssigneeUserId() == null) {
                    j.setAssigneeUserId(ids.get(0));
                }
            }
        }
    }

    /** Users who receive tickets/reminders for this scheduled job (junction + legacy column). */
    public static List<Integer> getAssigneeUserIdsForJob(int jobId) {
        ensureAutomatedJobAssigneesInfrastructure();
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id FROM automated_job_assignees WHERE job_id = ? ORDER BY user_id")) {
            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                set.add(rs.getInt("user_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (set.isEmpty()) {
            try (Connection c = DB.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT assignee_user_id FROM automated_jobs WHERE id = ?")) {
                ps.setInt(1, jobId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    int a = rs.getInt("assignee_user_id");
                    if (!rs.wasNull()) {
                        set.add(a);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new ArrayList<>(set);
    }

    public static void createScheduledJob(String title,
                                          String description,
                                          LocalDateTime dueAt,
                                          int reminderMinutes,
                                          int assigneeUserId,
                                          String recurrence) {
        if (assigneeUserId <= 0) {
            throw new RuntimeException("Assignee is required.");
        }
        createScheduledJob(title, description, dueAt, reminderMinutes, List.of(assigneeUserId), recurrence);
    }

    public static void createScheduledJob(String title,
                                          String description,
                                          LocalDateTime dueAt,
                                          int reminderMinutes,
                                          List<Integer> assigneeUserIds,
                                          String recurrence) {
        ensureAutomatedJobAssigneesInfrastructure();
        List<Integer> ids = assigneeUserIds == null ? List.of() : assigneeUserIds.stream()
                .filter(Objects::nonNull)
                .filter(i -> i > 0)
                .distinct()
                .sorted()
                .toList();
        if (ids.isEmpty()) {
            throw new RuntimeException("At least one assignee is required.");
        }
        int primaryAssignee = ids.get(0);
        String insertJob = """
                INSERT INTO automated_jobs(
                    job_title, job_description, due_at, reminder_minutes,
                    assignee_user_id, created_by, recurrence, active, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, datetime('now','localtime'))
                """;
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int jobId;
                try (PreparedStatement ps = DbConfig.isOracle()
                        ? c.prepareStatement(insertJob, new String[] { "ID" })
                        : c.prepareStatement(insertJob, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, title);
                    ps.setString(2, description);
                    ps.setString(3, dueAt.format(DB_DT));
                    ps.setInt(4, Math.max(0, reminderMinutes));
                    ps.setInt(5, primaryAssignee);
                    ps.setInt(6, Session.getUserId());
                    ps.setString(7, recurrence == null ? "ONCE" : recurrence.trim().toUpperCase());
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    if (!keys.next()) {
                        throw new RuntimeException("Scheduled job insert failed: no id.");
                    }
                    Number nid = (Number) keys.getObject(1);
                    jobId = nid != null ? nid.intValue() : keys.getInt(1);
                }
                String insertLink = "INSERT INTO automated_job_assignees (job_id, user_id) VALUES (?, ?)";
                try (PreparedStatement ps = c.prepareStatement(insertLink)) {
                    for (int uid : ids) {
                        ps.setInt(1, jobId);
                        ps.setInt(2, uid);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (Exception ignored) {
                }
                throw new RuntimeException("Failed to save scheduled job: " + e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save scheduled job: " + e.getMessage(), e);
        }
    }

    public static ObservableList<ScheduledJob> getAllScheduledJobs() {
        ensureAutomatedJobAssigneesInfrastructure();
        ObservableList<ScheduledJob> list = FXCollections.observableArrayList();
        String assigneeExpr = SqlDialect.isOracle()
                ? """
                  COALESCE(
                    (SELECT LISTAGG(u.username, ', ') WITHIN GROUP (ORDER BY u.username)
                     FROM automated_job_assignees ja JOIN users u ON u.id = ja.user_id
                     WHERE ja.job_id = j.id),
                    u2.username
                  )
                  """
                : """
                  COALESCE(
                    (SELECT GROUP_CONCAT(u.username, ', ')
                     FROM automated_job_assignees ja JOIN users u ON u.id = ja.user_id
                     WHERE ja.job_id = j.id),
                    u2.username
                  )
                  """;
        String sql = """
            SELECT j.id, j.job_title, j.job_description, j.due_at, j.reminder_minutes,
                   j.assignee_user_id,
                   %s AS assignee_username,
                   j.recurrence, j.active
            FROM automated_jobs j
            LEFT JOIN users u2 ON u2.id = j.assignee_user_id
            ORDER BY j.due_at ASC, j.id DESC
            """.formatted(assigneeExpr.replace("\n", " ").trim());
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ScheduledJob j = new ScheduledJob();
                j.setId(rs.getInt("id"));
                j.setTitle(rs.getString("job_title"));
                j.setDescription(rs.getString("job_description"));
                j.setDueAt(rs.getString("due_at"));
                j.setReminderMinutes(rs.getInt("reminder_minutes"));
                int assignee = rs.getInt("assignee_user_id");
                if (!rs.wasNull()) {
                    j.setAssigneeUserId(assignee);
                }
                j.setAssigneeUsername(rs.getString("assignee_username"));
                j.setRecurrence(rs.getString("recurrence"));
                j.setActive(rs.getInt("active") == 1);
                list.add(j);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        attachAssigneesToScheduledJobs(list);
        return list;
    }

    public static void setScheduledJobActive(int jobId, boolean active) {
        String sql = "UPDATE automated_jobs SET active=? WHERE id=?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, jobId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update job status: " + e.getMessage(), e);
        }
    }

    public static void deleteScheduledJob(int jobId) {
        String sql = "DELETE FROM automated_jobs WHERE id=?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, jobId);
            int n = ps.executeUpdate();
            if (n == 0) {
                throw new RuntimeException("Scheduled job not found.");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete scheduled job: " + e.getMessage(), e);
        }
    }

    public static List<ScheduledJob> getActiveJobs() {
        List<ScheduledJob> list = new ArrayList<>();
        String sql = """
            SELECT id, job_title, job_description, due_at, reminder_minutes,
                   assignee_user_id, recurrence, active,
                   last_reminder_at, last_ticket_created_at
            FROM automated_jobs
            WHERE active = 1
            ORDER BY due_at ASC
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ScheduledJob j = new ScheduledJob();
                j.setId(rs.getInt("id"));
                j.setTitle(rs.getString("job_title"));
                j.setDescription(rs.getString("job_description"));
                j.setDueAt(rs.getString("due_at"));
                j.setReminderMinutes(rs.getInt("reminder_minutes"));
                int assignee = rs.getInt("assignee_user_id");
                if (!rs.wasNull()) {
                    j.setAssigneeUserId(assignee);
                }
                j.setRecurrence(rs.getString("recurrence"));
                j.setActive(rs.getInt("active") == 1);
                list.add(j);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        attachAssigneesToScheduledJobs(list);
        return list;
    }

    public static String getJobLastReminderAt(int jobId) {
        return getSingleDateValue(jobId, "last_reminder_at");
    }

    public static String getJobLastTicketCreatedAt(int jobId) {
        return getSingleDateValue(jobId, "last_ticket_created_at");
    }

    private static String getSingleDateValue(int jobId, String column) {
        String sql = "SELECT " + column + " FROM automated_jobs WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString(column);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void markReminderSent(int jobId) {
        String sql = "UPDATE automated_jobs SET last_reminder_at=datetime('now','localtime') WHERE id=?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, jobId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int insertAutoScheduledTicket(Connection c, String ticketTitle, String ticketDesc,
                                                 Integer assignee, int creatorId) throws SQLException {
        String insertTicket = """
                INSERT INTO tickets(
                    title, description, priority, status, ticket_type,
                    department_id, assigned_to, created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, 'MEDIUM', ?, 'INTERNAL', NULL, ?, ?, ?, datetime('now','localtime'), datetime('now','localtime'))
                """;
        try (PreparedStatement ps = DbConfig.isOracle()
                ? c.prepareStatement(insertTicket, new String[] { "ID" })
                : c.prepareStatement(insertTicket, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ticketTitle);
            ps.setString(2, ticketDesc);
            ps.setString(3, assignee == null ? "OPEN" : "ASSIGNED");
            if (assignee == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, assignee);
            }
            ps.setInt(5, creatorId);
            ps.setInt(6, creatorId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (!rs.next()) {
                throw new SQLException("Ticket creation failed.");
            }
            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Ticket creation failed: no generated ID.");
            }
            return generatedId.intValue();
        }
    }

    private static String updateTicketNumberAndGet(Connection c, int ticketId) throws SQLException {
        String number = "TCK-" + java.time.Year.now().getValue() + "-" + String.format("%03d", ticketId);
        try (PreparedStatement ps = c.prepareStatement("UPDATE tickets SET ticket_number=? WHERE id=?")) {
            ps.setString(1, number);
            ps.setInt(2, ticketId);
            ps.executeUpdate();
        }
        return number;
    }

    private static void insertAutoAssignment(Connection c, int ticketId, int assignee, int creatorId) throws SQLException {
        String roleFrom = "SYSTEM";
        String roleTo = "AGENT";
        User u = UserDAO.findById(assignee);
        if (u != null && u.getRole() != null) {
            roleTo = u.getRole();
        }
        String assignment = """
                INSERT INTO ticket_assignments(
                    ticket_id, agent_id, assigned_to, assigned_by, assigned_at,
                    status, active, from_role, to_role, assignment_type
                ) VALUES (?, ?, ?, ?, datetime('now','localtime'), 'ASSIGNED', 1, ?, ?, 'AUTO_SCHEDULED')
                """;
        try (PreparedStatement ps = c.prepareStatement(assignment)) {
            ps.setInt(1, ticketId);
            ps.setInt(2, assignee);
            ps.setInt(3, assignee);
            ps.setInt(4, creatorId);
            ps.setString(5, roleFrom);
            ps.setString(6, roleTo);
            ps.executeUpdate();
        }
    }

    /**
     * Creates one ticket per assignee linked to the job (same title/description).
     * If the job has no assignees, creates a single OPEN unassigned ticket.
     * @return last created ticket id, or -1 if none
     */
    public static int createTicketForScheduledJob(int jobId) {
        ensureAutomatedJobAssigneesInfrastructure();
        String sqlJob = "SELECT job_title, job_description, created_by FROM automated_jobs WHERE id=?";
        String title;
        String desc;
        int creatorId = Session.getUserId();
        try (Connection cx = DB.getConnection();
             PreparedStatement ps = cx.prepareStatement(sqlJob)) {
            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                throw new RuntimeException("Scheduled job not found.");
            }
            title = rs.getString("job_title");
            desc = rs.getString("job_description");
            int cb = rs.getInt("created_by");
            if (!rs.wasNull() && cb > 0) {
                creatorId = cb;
            } else if (creatorId <= 0) {
                creatorId = 1;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Scheduled job not found.", e);
        }

        List<Integer> assignees = getAssigneeUserIdsForJob(jobId);
        String ticketTitle = "[AUTO JOB] " + (title == null ? "Scheduled Job" : title);
        String ticketDesc = desc == null ? "" : desc;

        record TicketNotify(int ticketId, String ticketRef, int assigneeUserId) {}

        List<TicketNotify> toNotify = new ArrayList<>();
        int lastTicketId = -1;

        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (assignees.isEmpty()) {
                    int tid = insertAutoScheduledTicket(c, ticketTitle, ticketDesc, null, creatorId);
                    updateTicketNumberAndGet(c, tid);
                    TicketHistoryDAO.log(c, tid, "CREATED",
                            "Auto-created from scheduled job #" + jobId);
                    lastTicketId = tid;
                } else {
                    for (int assignee : assignees) {
                        int tid = insertAutoScheduledTicket(c, ticketTitle, ticketDesc, assignee, creatorId);
                        String ref = updateTicketNumberAndGet(c, tid);
                        insertAutoAssignment(c, tid, assignee, creatorId);
                        TicketHistoryDAO.log(c, tid, "CREATED",
                                "Auto-created from scheduled job #" + jobId);
                        toNotify.add(new TicketNotify(tid, ref, assignee));
                        lastTicketId = tid;
                    }
                }
                c.commit();
            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (Exception ignored) {
                }
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to auto-create ticket: " + e.getMessage(), e);
        }

        for (TicketNotify tn : toNotify) {
            NotificationDAO.create(
                    tn.assigneeUserId(),
                    "Scheduled Job Ticket",
                    "A scheduled job is due. Ticket " + tn.ticketRef() + " was created.",
                    "TICKET_AUTO_CREATED",
                    "TICKET",
                    tn.ticketId(),
                    tn.ticketRef()
            );
            String toEmail = UserDAO.getEmailByUserId(tn.assigneeUserId());
            EmailService.sendSimpleNotificationEmail(
                    toEmail,
                    "SYSCO Auto-Created Ticket",
                    "A ticket was auto-created for a scheduled job.\nTicket: " + tn.ticketRef()
            );
        }

        return lastTicketId;
    }

    public static void markTicketCreatedForJob(int jobId) {
        String sql = "UPDATE automated_jobs SET last_ticket_created_at=datetime('now','localtime') WHERE id=?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, jobId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void advanceJobToNextMonthlyCycle(int jobId) {
        String dueSql = "SELECT due_at FROM automated_jobs WHERE id=?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(dueSql)) {
            ps.setInt(1, jobId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return;
            String due = rs.getString("due_at");
            if (due == null || due.isBlank()) return;
            LocalDateTime currentDue = TimeUtil.parseDbLocalDateTime(due);
            if (currentDue == null) return;
            LocalDateTime next = currentDue.plusMonths(1);
            try (PreparedStatement up = c.prepareStatement("""
                UPDATE automated_jobs
                SET due_at=?,
                    last_reminder_at=NULL,
                    last_ticket_created_at=NULL
                WHERE id=?
            """)) {
                up.setString(1, next.format(DB_DT));
                up.setInt(2, jobId);
                up.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deactivateJob(int jobId) {
        setScheduledJobActive(jobId, false);
    }

    public static ObservableList<MonthlyReportSummary> getMonthlyReports() {
        ObservableList<MonthlyReportSummary> list = FXCollections.observableArrayList();
        String sql = """
            SELECT id, month_key, generated_at, file_path,
                   total_tickets, open_tickets, in_progress_tickets, closed_tickets
            FROM monthly_reports
            ORDER BY month_key DESC
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                MonthlyReportSummary r = new MonthlyReportSummary();
                r.setId(rs.getInt("id"));
                r.setMonthKey(rs.getString("month_key"));
                r.setGeneratedAt(rs.getString("generated_at"));
                r.setFilePath(rs.getString("file_path"));
                r.setTotalTickets(rs.getInt("total_tickets"));
                r.setOpenTickets(rs.getInt("open_tickets"));
                r.setInProgressTickets(rs.getInt("in_progress_tickets"));
                r.setClosedTickets(rs.getInt("closed_tickets"));
                list.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static MonthlyReportSummary getMonthlyReportByMonthKey(String monthKey) {
        if (monthKey == null || monthKey.isBlank()) return null;
        String sql = """
            SELECT id, month_key, generated_at, file_path,
                   total_tickets, open_tickets, in_progress_tickets, closed_tickets
            FROM monthly_reports
            WHERE month_key = ?
            LIMIT 1
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, monthKey);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            MonthlyReportSummary r = new MonthlyReportSummary();
            r.setId(rs.getInt("id"));
            r.setMonthKey(rs.getString("month_key"));
            r.setGeneratedAt(rs.getString("generated_at"));
            r.setFilePath(rs.getString("file_path"));
            r.setTotalTickets(rs.getInt("total_tickets"));
            r.setOpenTickets(rs.getInt("open_tickets"));
            r.setInProgressTickets(rs.getInt("in_progress_tickets"));
            r.setClosedTickets(rs.getInt("closed_tickets"));
            return r;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read report: " + e.getMessage(), e);
        }
    }

    public static void deleteMonthlyReport(int reportId) {
        String readSql = "SELECT file_path FROM monthly_reports WHERE id = ?";
        String deleteSql = "DELETE FROM monthly_reports WHERE id = ?";
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            String filePath = null;
            try (PreparedStatement read = c.prepareStatement(readSql)) {
                read.setInt(1, reportId);
                ResultSet rs = read.executeQuery();
                if (rs.next()) filePath = rs.getString("file_path");
            }
            try (PreparedStatement del = c.prepareStatement(deleteSql)) {
                del.setInt(1, reportId);
                del.executeUpdate();
            }
            c.commit();

            if (filePath != null && !filePath.isBlank()) {
                try {
                    Files.deleteIfExists(new File(filePath).toPath());
                } catch (Exception ignored) {
                    // DB delete already committed; file cleanup failure is non-fatal.
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete report: " + e.getMessage(), e);
        }
    }

    public static void deleteMonthlyReportByMonthKey(String monthKey) {
        MonthlyReportSummary existing = getMonthlyReportByMonthKey(monthKey);
        if (existing != null) {
            deleteMonthlyReport(existing.getId());
        }
    }

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH);
    private static final DateTimeFormatter FR_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRENCH);

    private static String periodKey(LocalDate startInclusive, LocalDate endInclusive) {
        return startInclusive.toString() + "_au_" + endInclusive.toString();
    }

    private static String statutFr(String s) {
        if (s == null || s.isBlank()) return "-";
        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "OPEN" -> "Ouvert";
            case "CLOSED", "FERMÉ", "FERME" -> "Fermé";
            case "ASSIGNED" -> "Assigné";
            case "IN_PROGRESS" -> "En cours";
            case "WAITING_ON_TASKS" -> "En attente de tâches";
            case "RESOLVED" -> "Résolu";
            case "ESCALATED" -> "Escaladé";
            case "MERGED" -> "Fusionné";
            case "PENDING" -> "En attente";
            case "COMPLETED" -> "Terminé";
            case "PENDING_APPROVAL" -> "En attente d'approbation";
            default -> s;
        };
    }

    private static String prioriteFr(String p) {
        if (p == null || p.isBlank()) return "-";
        return switch (p.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> "Basse";
            case "MEDIUM" -> "Moyenne";
            case "HIGH" -> "Haute";
            case "CRITICAL" -> "Critique";
            default -> p;
        };
    }

    private static void boldTableHeaderRow(XWPFTable table) {
        if (table == null || table.getNumberOfRows() == 0) return;
        XWPFTableRow row = table.getRow(0);
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    r.setBold(true);
                }
            }
        }
    }

    private static void addHeading(XWPFDocument doc, String text, int fontSize, boolean center) {
        XWPFParagraph p = doc.createParagraph();
        if (center) p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(fontSize);
        r.setText(text);
    }

    private static void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontSize(11);
        r.setText(text);
    }

    /**
     * Rapport mensuel automatique (mois civil précédent), sans écraser si déjà présent.
     */
    public static void generateMonthlyReport(YearMonth month) {
        generateOperationalReport(month.atDay(1), month.atEndOfMonth(), false);
    }

    /**
     * Rapport d'exploitation technique (contenu en français) pour une période donnée.
     *
     * @param replaceExisting si vrai, supprime l'entrée et le fichier existants pour cette période avant régénération.
     */
    public static void generateOperationalReport(LocalDate startInclusive, LocalDate endInclusive, boolean replaceExisting) {
        if (startInclusive == null || endInclusive == null) {
            throw new IllegalArgumentException("Les dates de période sont obligatoires.");
        }
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure ou égale au début.");
        }
        LocalDate endExclusive = endInclusive.plusDays(1);
        String periodKey = periodKey(startInclusive, endInclusive);
        String fromTs = startInclusive.toString() + " 00:00:00";
        String toTs = endExclusive.toString() + " 00:00:00";

        if (replaceExisting) {
            deleteMonthlyReportByMonthKey(periodKey);
        } else {
            try (Connection cx = DB.getConnection();
                 PreparedStatement exists = cx.prepareStatement("SELECT 1 FROM monthly_reports WHERE month_key = ?")) {
                exists.setString(1, periodKey);
                if (exists.executeQuery().next()) {
                    return;
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                runFrenchOperationalReport(c, periodKey, startInclusive, endInclusive, fromTs, toTs);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Échec de la génération du rapport : " + e.getMessage(), e);
        }
    }

    private static void runFrenchOperationalReport(Connection c, String periodKey,
                                                   LocalDate startInclusive, LocalDate endInclusive,
                                                   String fromTs, String toTs) throws Exception {

        LocalDate endExclusiveDate = endInclusive.plusDays(1);

        int activeUsers = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE active = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) activeUsers = rs.getInt(1);
        }

        String statsSql = """
            SELECT
                COUNT(*) AS total,
                SUM(CASE WHEN UPPER(COALESCE(status,''))='OPEN' THEN 1 ELSE 0 END) AS open_count,
                SUM(CASE WHEN UPPER(COALESCE(status,''))='IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress_count,
                SUM(CASE WHEN UPPER(COALESCE(status,'')) IN ('CLOSED','FERMÉ','FERME') THEN 1 ELSE 0 END) AS closed_count
            FROM tickets
            WHERE merged_into IS NULL
              AND datetime(created_at) >= datetime(?)
              AND datetime(created_at) < datetime(?)
            """;
        int total = 0, open = 0, inProg = 0, closed = 0;
        try (PreparedStatement ps = c.prepareStatement(statsSql)) {
            ps.setString(1, fromTs);
            ps.setString(2, toTs);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                total = rs.getInt("total");
                open = rs.getInt("open_count");
                inProg = rs.getInt("in_progress_count");
                closed = rs.getInt("closed_count");
            }
        }

        int closedInPeriod = 0;
        String closedSql = """
            SELECT COUNT(*) FROM tickets
            WHERE merged_into IS NULL
              AND closed_at IS NOT NULL
              AND datetime(closed_at) >= datetime(?)
              AND datetime(closed_at) < datetime(?)
            """;
        try (PreparedStatement ps = c.prepareStatement(closedSql)) {
            ps.setString(1, fromTs);
            ps.setString(2, toTs);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) closedInPeriod = rs.getInt(1);
        }

        int tasksCreated = 0;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM ticket_tasks
                WHERE datetime(created_at) >= datetime(?) AND datetime(created_at) < datetime(?)""")) {
            ps.setString(1, fromTs);
            ps.setString(2, toTs);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) tasksCreated = rs.getInt(1);
        }

        int tasksCompleted = 0;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM ticket_tasks
                WHERE UPPER(COALESCE(status,'')) = 'COMPLETED'
                  AND datetime(COALESCE(completed_at, closed_at, created_at)) >= datetime(?)
                  AND datetime(COALESCE(completed_at, closed_at, created_at)) < datetime(?)""")) {
            ps.setString(1, fromTs);
            ps.setString(2, toTs);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) tasksCompleted = rs.getInt(1);
        }

        int attachmentsPeriod = 0;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM ticket_attachments
                WHERE datetime(uploaded_at) >= datetime(?) AND datetime(uploaded_at) < datetime(?)""")) {
            ps.setString(1, fromTs);
            ps.setString(2, toTs);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) attachmentsPeriod = rs.getInt(1);
        }

        int auditLogin = 0;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM system_audit
                WHERE UPPER(COALESCE(action,'')) = 'LOGIN'
                  AND datetime(created_at) >= datetime(?) AND datetime(created_at) < datetime(?)""")) {
            ps.setString(1, fromTs);
            ps.setString(2, toTs);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) auditLogin = rs.getInt(1);
        }

        int datashareEvents = 0;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM datashare_audit
                WHERE date IS NOT NULL AND date != ''
                  AND date >= ? AND date < ?""")) {
            ps.setString(1, startInclusive.toString());
            ps.setString(2, endExclusiveDate.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) datashareEvents = rs.getInt(1);
        } catch (Exception ignored) {
            datashareEvents = 0;
        }

        int notificationsCreated = 0;
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*) FROM notifications
                WHERE datetime(created_at) >= datetime(?) AND datetime(created_at) < datetime(?)""")) {
            ps.setString(1, fromTs);
            ps.setString(2, toTs);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) notificationsCreated = rs.getInt(1);
        } catch (Exception ignored) {
            notificationsCreated = 0;
        }

        List<AgentMonthlyWork> agentWorks = new ArrayList<>();
        String agentSql = """
            SELECT u.id AS user_id,
                   u.username,
                   u.role,
                   COALESCE(SUM(CASE WHEN datetime(ta.assigned_at) >= datetime(?) AND datetime(ta.assigned_at) < datetime(?) THEN 1 ELSE 0 END), 0) AS assigned_count,
                   COALESCE(SUM(CASE WHEN ta.started_at IS NOT NULL AND datetime(ta.started_at) >= datetime(?) AND datetime(ta.started_at) < datetime(?) THEN 1 ELSE 0 END), 0) AS started_count,
                   COALESCE(SUM(CASE WHEN ta.closed_at IS NOT NULL AND datetime(ta.closed_at) >= datetime(?) AND datetime(ta.closed_at) < datetime(?) THEN 1 ELSE 0 END), 0) AS closed_count,
                   COALESCE((
                     SELECT COUNT(*)
                     FROM ticket_tasks tt
                     WHERE tt.assigned_to = u.id
                       AND datetime(COALESCE(tt.completed_at, tt.closed_at, tt.created_at)) >= datetime(?)
                       AND datetime(COALESCE(tt.completed_at, tt.closed_at, tt.created_at)) < datetime(?)
                       AND UPPER(COALESCE(tt.status,'')) = 'COMPLETED'
                   ), 0) AS tasks_completed_count,
                   COALESCE((
                     SELECT COUNT(*)
                     FROM ticket_assignments tx
                     WHERE tx.assigned_by = u.id
                       AND UPPER(COALESCE(tx.assignment_type,'')) = 'ESCALATION'
                       AND datetime(tx.assigned_at) >= datetime(?)
                       AND datetime(tx.assigned_at) < datetime(?)
                   ), 0) AS escalations_sent_count,
                   COALESCE((
                     SELECT COUNT(*)
                     FROM ticket_assignments tr
                     WHERE tr.assigned_by = u.id
                       AND UPPER(COALESCE(tr.assignment_type,'')) = 'REASSIGNMENT'
                       AND datetime(tr.assigned_at) >= datetime(?)
                       AND datetime(tr.assigned_at) < datetime(?)
                   ), 0) AS reassignments_sent_count
            FROM users u
            LEFT JOIN ticket_assignments ta ON ta.assigned_to = u.id
            WHERE u.active = 1
            GROUP BY u.id, u.username, u.role
            ORDER BY u.username
            """;
        try (PreparedStatement ps = c.prepareStatement(agentSql)) {
            int i = 1;
            for (int k = 0; k < 6; k++) {
                ps.setString(i++, fromTs);
                ps.setString(i++, toTs);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                AgentMonthlyWork aw = new AgentMonthlyWork(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getInt("assigned_count"),
                        rs.getInt("started_count"),
                        rs.getInt("closed_count"),
                        rs.getInt("tasks_completed_count"),
                        rs.getInt("escalations_sent_count"),
                        rs.getInt("reassignments_sent_count")
                );
                int activityTotal = aw.assignedCount() + aw.startedCount() + aw.closedCount()
                        + aw.tasksCompletedCount() + aw.escalationsSentCount() + aw.reassignmentsSentCount();
                if (activityTotal > 0) {
                    agentWorks.add(aw);
                }
            }
        }

        File dir = new File("reports");
        if (!dir.exists()) dir.mkdirs();
        String safeFileKey = periodKey.replace(":", "-");
        File docx = new File(dir, "rapport-exploitation-" + safeFileKey + ".docx");

        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docx)) {

            LocalDateTime genAt = LocalDateTime.now();
            addHeading(doc, "RAPPORT TECHNIQUE D'EXPLOITATION — SYSCO", 20, true);
            addParagraph(doc, "Document opérationnel et statistique — usage interne");
            doc.createParagraph();

            addHeading(doc, "1. Identification et période couverte", 13, false);
            addParagraph(doc, "Période analysée : du " + startInclusive.format(FR_DATE) + " au " + endInclusive.format(FR_DATE) + " (inclus).");
            addParagraph(doc, "Référence période (clé système) : " + periodKey);
            addParagraph(doc, "Date et heure de génération : " + genAt.format(FR_DATETIME));
            addParagraph(doc, "Utilisateurs actifs enregistrés (total) : " + activeUsers);

            doc.createParagraph();
            addHeading(doc, "2. Synthèse des tickets créés sur la période", 13, false);
            XWPFTable tSummary = doc.createTable(2, 5);
            tSummary.getRow(0).getCell(0).setText("Total créés");
            tSummary.getRow(0).getCell(1).setText("Ouverts");
            tSummary.getRow(0).getCell(2).setText("En cours");
            tSummary.getRow(0).getCell(3).setText("Fermés (statut à la création)");
            tSummary.getRow(0).getCell(4).setText("Taux de clôture (créés)");
            tSummary.getRow(1).getCell(0).setText(String.valueOf(total));
            tSummary.getRow(1).getCell(1).setText(String.valueOf(open));
            tSummary.getRow(1).getCell(2).setText(String.valueOf(inProg));
            tSummary.getRow(1).getCell(3).setText(String.valueOf(closed));
            String closureRate = total <= 0 ? "0 %" : String.format(Locale.FRENCH, "%.1f %%", (closed * 100.0 / total));
            tSummary.getRow(1).getCell(4).setText(closureRate);
            boldTableHeaderRow(tSummary);

            doc.createParagraph();
            addParagraph(doc, "Tickets effectivement clôturés durant la période (tous tickets, date de clôture dans l'intervalle) : " + closedInPeriod);

            doc.createParagraph();
            addHeading(doc, "3. Tâches rattachées aux tickets", 13, false);
            addParagraph(doc, "Tâches créées sur la période : " + tasksCreated);
            addParagraph(doc, "Tâches marquées terminées (événement dans la période) : " + tasksCompleted);

            String taskStatusSql = """
                SELECT UPPER(COALESCE(status,'')) AS st, COUNT(*) AS cnt
                FROM ticket_tasks
                WHERE datetime(created_at) >= datetime(?) AND datetime(created_at) < datetime(?)
                GROUP BY UPPER(COALESCE(status,''))
                ORDER BY cnt DESC
                """;
            XWPFTable tTaskSt = doc.createTable(1, 2);
            tTaskSt.getRow(0).getCell(0).setText("Statut tâche");
            tTaskSt.getRow(0).getCell(1).setText("Volume");
            try (PreparedStatement ps = c.prepareStatement(taskStatusSql)) {
                ps.setString(1, fromTs);
                ps.setString(2, toTs);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    XWPFTableRow row = tTaskSt.createRow();
                    row.getCell(0).setText(statutFr(rs.getString("st")));
                    row.getCell(1).setText(String.valueOf(rs.getInt("cnt")));
                }
            }
            boldTableHeaderRow(tTaskSt);

            doc.createParagraph();
            addHeading(doc, "4. Données transverses (pièces jointes, audit, notifications)", 13, false);
            addParagraph(doc, "Pièces jointes déposées sur la période : " + attachmentsPeriod);
            addParagraph(doc, "Événements d'audit « connexion » (LOGIN) sur la période : " + auditLogin);
            addParagraph(doc, "Événements d'audit DataShare (filtrage par date) : " + datashareEvents);
            addParagraph(doc, "Notifications générées sur la période : " + notificationsCreated);

            doc.createParagraph();
            addHeading(doc, "5. Charge et production par agent", 13, false);
            XWPFTable tAgent = doc.createTable(agentWorks.size() + 1, 9);
            XWPFTableRow hh = tAgent.getRow(0);
            hh.getCell(0).setText("Agent");
            hh.getCell(1).setText("Rôle");
            hh.getCell(2).setText("Assignations");
            hh.getCell(3).setText("Démarrages");
            hh.getCell(4).setText("Clôtures assign.");
            hh.getCell(5).setText("Tâches terminées");
            hh.getCell(6).setText("Escalades émises");
            hh.getCell(7).setText("Réassignations");
            hh.getCell(8).setText("Activité totale");
            int rowIndex = 1;
            for (AgentMonthlyWork aw : agentWorks) {
                XWPFTableRow row = tAgent.getRow(rowIndex++);
                row.getCell(0).setText(aw.username());
                row.getCell(1).setText(aw.role());
                row.getCell(2).setText(String.valueOf(aw.assignedCount()));
                row.getCell(3).setText(String.valueOf(aw.startedCount()));
                row.getCell(4).setText(String.valueOf(aw.closedCount()));
                row.getCell(5).setText(String.valueOf(aw.tasksCompletedCount()));
                row.getCell(6).setText(String.valueOf(aw.escalationsSentCount()));
                row.getCell(7).setText(String.valueOf(aw.reassignmentsSentCount()));
                int totalAct = aw.assignedCount() + aw.startedCount() + aw.closedCount()
                        + aw.tasksCompletedCount() + aw.escalationsSentCount() + aw.reassignmentsSentCount();
                row.getCell(8).setText(String.valueOf(totalAct));
            }
            boldTableHeaderRow(tAgent);

            doc.createParagraph();
            addHeading(doc, "6. Détail des tickets créés sur la période (échantillon complet jusqu'à 400 lignes)", 13, false);
            String ticketsListSql = """
                SELECT t.id, t.ticket_number, t.title, t.status, t.priority, t.created_at,
                       COALESCE(d.name, '-') AS dept
                FROM tickets t
                LEFT JOIN departments d ON d.id = t.department_id
                WHERE t.merged_into IS NULL
                  AND datetime(t.created_at) >= datetime(?)
                  AND datetime(t.created_at) < datetime(?)
                ORDER BY t.created_at DESC
                LIMIT 400
                """;
            XWPFTable tTickets = doc.createTable(1, 6);
            XWPFTableRow hT = tTickets.getRow(0);
            hT.getCell(0).setText("Réf.");
            hT.getCell(1).setText("Titre");
            hT.getCell(2).setText("Statut");
            hT.getCell(3).setText("Priorité");
            hT.getCell(4).setText("Département");
            hT.getCell(5).setText("Créé le");
            int ticketRows = 0;
            try (PreparedStatement ps = c.prepareStatement(ticketsListSql)) {
                ps.setString(1, fromTs);
                ps.setString(2, toTs);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    XWPFTableRow row = tTickets.createRow();
                    int tid = rs.getInt("id");
                    String num = rs.getString("ticket_number");
                    row.getCell(0).setText(num != null && !num.isBlank() ? num : TicketUtil.formatTicketRef(tid));
                    row.getCell(1).setText(rs.getString("title") != null ? rs.getString("title") : "-");
                    row.getCell(2).setText(statutFr(rs.getString("status")));
                    row.getCell(3).setText(prioriteFr(rs.getString("priority")));
                    row.getCell(4).setText(rs.getString("dept"));
                    row.getCell(5).setText(rs.getString("created_at") != null ? rs.getString("created_at") : "-");
                    ticketRows++;
                }
            }
            boldTableHeaderRow(tTickets);
            if (ticketRows == 0) {
                addParagraph(doc, "(Aucun ticket créé sur cette période.)");
            }

            doc.createParagraph();
            addHeading(doc, "7. Détail des tâches créées sur la période (jusqu'à 400 lignes)", 13, false);
            String tasksDetailSql = """
                SELECT tt.id, tt.ticket_id, tt.title, tt.status, tt.created_at,
                       ua.username AS assigne
                FROM ticket_tasks tt
                LEFT JOIN users ua ON ua.id = tt.assigned_to
                WHERE datetime(tt.created_at) >= datetime(?) AND datetime(tt.created_at) < datetime(?)
                ORDER BY tt.created_at DESC
                LIMIT 400
                """;
            XWPFTable tTasks = doc.createTable(1, 5);
            XWPFTableRow hTask = tTasks.getRow(0);
            hTask.getCell(0).setText("Ticket");
            hTask.getCell(1).setText("Tâche");
            hTask.getCell(2).setText("Assigné à");
            hTask.getCell(3).setText("Statut");
            hTask.getCell(4).setText("Créée le");
            int taskDetailRows = 0;
            try (PreparedStatement ps = c.prepareStatement(tasksDetailSql)) {
                ps.setString(1, fromTs);
                ps.setString(2, toTs);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    XWPFTableRow row = tTasks.createRow();
                    row.getCell(0).setText(TicketUtil.formatTicketRef(rs.getInt("ticket_id")));
                    row.getCell(1).setText(rs.getString("title") != null ? rs.getString("title") : "-");
                    row.getCell(2).setText(rs.getString("assigne") != null ? rs.getString("assigne") : "-");
                    row.getCell(3).setText(statutFr(rs.getString("status")));
                    row.getCell(4).setText(rs.getString("created_at") != null ? rs.getString("created_at") : "-");
                    taskDetailRows++;
                }
            }
            boldTableHeaderRow(tTasks);
            if (taskDetailRows == 0) {
                addParagraph(doc, "(Aucune tâche créée sur cette période.)");
            }

            doc.createParagraph();
            addHeading(doc, "8. Historique des tickets (événements enregistrés sur la période)", 13, false);
            String histSql = """
                SELECT th.ticket_id, th.action, th.description, th.username, th.created_at
                FROM ticket_history th
                WHERE datetime(th.created_at) >= datetime(?) AND datetime(th.created_at) < datetime(?)
                ORDER BY th.created_at DESC
                LIMIT 300
                """;
            XWPFTable tHist = doc.createTable(1, 5);
            tHist.getRow(0).getCell(0).setText("Ticket");
            tHist.getRow(0).getCell(1).setText("Action");
            tHist.getRow(0).getCell(2).setText("Description");
            tHist.getRow(0).getCell(3).setText("Utilisateur");
            tHist.getRow(0).getCell(4).setText("Horodatage");
            int histRows = 0;
            try (PreparedStatement ps = c.prepareStatement(histSql)) {
                ps.setString(1, fromTs);
                ps.setString(2, toTs);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    XWPFTableRow row = tHist.createRow();
                    row.getCell(0).setText(TicketUtil.formatTicketRef(rs.getInt("ticket_id")));
                    row.getCell(1).setText(rs.getString("action") != null ? rs.getString("action") : "-");
                    String desc = rs.getString("description");
                    row.getCell(2).setText(desc != null && desc.length() > 200 ? desc.substring(0, 200) + "…" : (desc != null ? desc : "-"));
                    row.getCell(3).setText(rs.getString("username") != null ? rs.getString("username") : "-");
                    row.getCell(4).setText(rs.getString("created_at") != null ? rs.getString("created_at") : "-");
                    histRows++;
                }
            }
            boldTableHeaderRow(tHist);
            if (histRows == 0) {
                addParagraph(doc, "(Aucun événement d'historique sur cette période.)");
            }

            doc.createParagraph();
            addHeading(doc, "9. Demandes de clôture et escalades externes", 13, false);
            String closeReqSql = """
                SELECT cr.ticket_id, cr.status, cr.reason, cr.requested_at
                FROM ticket_close_requests cr
                WHERE datetime(cr.requested_at) >= datetime(?) AND datetime(cr.requested_at) < datetime(?)
                ORDER BY cr.requested_at DESC
                LIMIT 150
                """;
            XWPFTable tCr = doc.createTable(1, 4);
            tCr.getRow(0).getCell(0).setText("Ticket");
            tCr.getRow(0).getCell(1).setText("Statut");
            tCr.getRow(0).getCell(2).setText("Motif");
            tCr.getRow(0).getCell(3).setText("Demandé le");
            int crRows = 0;
            try (PreparedStatement ps = c.prepareStatement(closeReqSql)) {
                ps.setString(1, fromTs);
                ps.setString(2, toTs);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    XWPFTableRow row = tCr.createRow();
                    row.getCell(0).setText(TicketUtil.formatTicketRef(rs.getInt("ticket_id")));
                    row.getCell(1).setText(statutFr(rs.getString("status")));
                    String rsn = rs.getString("reason");
                    row.getCell(2).setText(rsn != null && rsn.length() > 120 ? rsn.substring(0, 120) + "…" : (rsn != null ? rsn : "-"));
                    row.getCell(3).setText(rs.getString("requested_at") != null ? rs.getString("requested_at") : "-");
                    crRows++;
                }
            }
            boldTableHeaderRow(tCr);
            if (crRows == 0) {
                addParagraph(doc, "(Aucune demande de clôture sur cette période.)");
            }

            addParagraph(doc, "Escalades externes (créées sur la période) :");
            String extEscSql = """
                SELECT e.ticket_id, e.status, e.created_at, e.note
                FROM ticket_external_escalations e
                WHERE datetime(e.created_at) >= datetime(?) AND datetime(e.created_at) < datetime(?)
                ORDER BY e.created_at DESC
                LIMIT 150
                """;
            try {
                XWPFTable tEx = doc.createTable(1, 4);
                tEx.getRow(0).getCell(0).setText("Ticket");
                tEx.getRow(0).getCell(1).setText("Statut");
                tEx.getRow(0).getCell(2).setText("Créée le");
                tEx.getRow(0).getCell(3).setText("Note");
                int exRows = 0;
                try (PreparedStatement ps = c.prepareStatement(extEscSql)) {
                    ps.setString(1, fromTs);
                    ps.setString(2, toTs);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        XWPFTableRow row = tEx.createRow();
                        row.getCell(0).setText(TicketUtil.formatTicketRef(rs.getInt("ticket_id")));
                        row.getCell(1).setText(statutFr(rs.getString("status")));
                        row.getCell(2).setText(rs.getString("created_at") != null ? rs.getString("created_at") : "-");
                        String n = rs.getString("note");
                        row.getCell(3).setText(n != null && n.length() > 120 ? n.substring(0, 120) + "…" : (n != null ? n : "-"));
                        exRows++;
                    }
                }
                boldTableHeaderRow(tEx);
                if (exRows == 0) {
                    addParagraph(doc, "(Aucune escalade externe sur cette période.)");
                }
            } catch (Exception e) {
                addParagraph(doc, "(Escalades externes : données non disponibles.)");
            }

            doc.createParagraph();
            addHeading(doc, "10. Activité d'assignation des tickets (lignes d'assignation dans la période)", 13, false);
            String detailSql = """
                SELECT u.username,
                       u.role,
                       t.id AS ticket_id,
                       t.title,
                       t.status,
                       ta.assignment_type,
                       ta.assigned_at,
                       ta.closed_at
                FROM ticket_assignments ta
                JOIN users u ON u.id = ta.assigned_to
                JOIN tickets t ON t.id = ta.ticket_id
                WHERE datetime(ta.assigned_at) >= datetime(?)
                  AND datetime(ta.assigned_at) < datetime(?)
                ORDER BY u.username, ta.assigned_at
                LIMIT 400
                """;
            XWPFTable tDetail = doc.createTable(1, 8);
            XWPFTableRow hd = tDetail.getRow(0);
            hd.getCell(0).setText("Agent");
            hd.getCell(1).setText("Rôle");
            hd.getCell(2).setText("Ticket");
            hd.getCell(3).setText("Titre");
            hd.getCell(4).setText("Statut ticket");
            hd.getCell(5).setText("Type d'assignation");
            hd.getCell(6).setText("Assigné le");
            hd.getCell(7).setText("Clôturé le");
            try (PreparedStatement ps = c.prepareStatement(detailSql)) {
                ps.setString(1, fromTs);
                ps.setString(2, toTs);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    XWPFTableRow row = tDetail.createRow();
                    row.getCell(0).setText(rs.getString("username"));
                    row.getCell(1).setText(rs.getString("role"));
                    row.getCell(2).setText(TicketUtil.formatTicketRef(rs.getInt("ticket_id")));
                    row.getCell(3).setText(rs.getString("title") != null ? rs.getString("title") : "-");
                    row.getCell(4).setText(statutFr(rs.getString("status")));
                    row.getCell(5).setText(rs.getString("assignment_type") != null ? rs.getString("assignment_type") : "-");
                    row.getCell(6).setText(rs.getString("assigned_at") != null ? rs.getString("assigned_at") : "-");
                    row.getCell(7).setText(rs.getString("closed_at") != null ? rs.getString("closed_at") : "-");
                }
            }
            boldTableHeaderRow(tDetail);

            doc.createParagraph();
            addHeading(doc, "11. Synthèse audit système (actions les plus fréquentes)", 13, false);
            String auditAggSql = """
                SELECT action, COUNT(*) AS cnt
                FROM system_audit
                WHERE datetime(created_at) >= datetime(?) AND datetime(created_at) < datetime(?)
                GROUP BY action
                ORDER BY cnt DESC
                LIMIT 25
                """;
            XWPFTable tAud = doc.createTable(1, 2);
            tAud.getRow(0).getCell(0).setText("Action");
            tAud.getRow(0).getCell(1).setText("Occurrences");
            try (PreparedStatement ps = c.prepareStatement(auditAggSql)) {
                ps.setString(1, fromTs);
                ps.setString(2, toTs);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    XWPFTableRow row = tAud.createRow();
                    row.getCell(0).setText(rs.getString("action") != null ? rs.getString("action") : "-");
                    row.getCell(1).setText(String.valueOf(rs.getInt("cnt")));
                }
            }
            boldTableHeaderRow(tAud);

            doc.createParagraph();
            XWPFParagraph pFooter = doc.createParagraph();
            pFooter.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun rFooter = pFooter.createRun();
            rFooter.setItalic(true);
            rFooter.setText("SYSCO — Rapport généré automatiquement. Données extraites de la base applicative. Confidentiel.");

            doc.write(out);
        }

        String insert = """
            INSERT INTO monthly_reports(
                month_key, generated_at, file_path,
                total_tickets, open_tickets, in_progress_tickets, closed_tickets
            ) VALUES (?, datetime('now','localtime'), ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = c.prepareStatement(insert)) {
            ps.setString(1, periodKey);
            ps.setString(2, docx.getAbsolutePath());
            ps.setInt(3, total);
            ps.setInt(4, open);
            ps.setInt(5, inProg);
            ps.setInt(6, closed);
            ps.executeUpdate();
        }
    }
}
