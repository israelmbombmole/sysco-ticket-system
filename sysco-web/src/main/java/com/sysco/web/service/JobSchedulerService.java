package com.sysco.web.service;

import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.MonthlyReport;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.DepartmentRepository;
import com.sysco.web.repo.MonthlyReportRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.security.RoleKeys;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobSchedulerService {

    private final AutomatedJobRepository jobs;
    private final MonthlyReportRepository reports;
    private final UserAccountRepository users;
    private final TicketRepository tickets;
    private final DepartmentRepository departments;
    private final TicketTimelineService ticketTimeline;
    private final DirectionScopeService directionScope;
    private final OperationalReportPdfService operationalReportPdf;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private static final Set<String> SCHEDULER_RECURRENCE = Set.of("ONCE", "DAILY", "WEEKLY", "MONTHLY");

    public JobPage page(String viewerUsername) {
        UserAccount viewer = directionScope.requireUser(viewerUsername);
        List<UserChoice> assignees = users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> mayPickAssignee(viewer, u.getId()))
                .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new UserChoice(u.getId(), u.getUsername(), u.getRole(), u.getMatricule()))
                .toList();

        Instant now = Instant.now();
        List<TicketChoice> ticketChoices =
                tickets.findAll().stream()
                        .filter(t -> t.getMergedIntoTicketId() == null)
                        .filter(t -> !"MERGED".equalsIgnoreCase(safe(t.getStatus())))
                        .filter(t -> isSchedulableTicketStatus(t.getStatus()))
                        .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                        .sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(200)
                        .map(t -> new TicketChoice(t.getId(), ticketNumber(t), truncate(safe(t.getTitle()), 80)))
                        .toList();

        List<JobRow> jobRows =
                jobs.findAll().stream()
                        .sorted(Comparator.comparing(AutomatedJob::getId).reversed())
                        .map(j -> new JobRow(
                                j.getId(),
                                j.getJobTitle(),
                                j.getDueAt(),
                                assigneeLabel(j.getAssigneeUserId()),
                                j.getRecurrence(),
                                j.getActive() != null && j.getActive() == 1 ? "Actif" : "Inactif",
                                linkedTicketLabel(j.getTicketId())))
                        .toList();

        List<ReportRow> reportRows =
                reports.findAll().stream()
                        .sorted(Comparator.comparing(MonthlyReport::getGeneratedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(r -> new ReportRow(
                                r.getId(),
                                r.getMonthKey(),
                                r.getGeneratedAt() == null
                                        ? ""
                                        : DT.format(r.getGeneratedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()),
                                String.format(
                                        "%d (Ouvert:%d, En cours:%d, Cloture:%d)",
                                        n(r.getTotalTickets()),
                                        n(r.getOpenTickets()),
                                        n(r.getInProgressTickets()),
                                        n(r.getClosedTickets())),
                                r.getFilePath()))
                        .toList();
        return new JobPage(jobRows, reportRows, assignees, ticketChoices);
    }

    /**
     * {@link DirectionScopeService#isSuperAdmin(UserAccount)} and {@code ADMIN} may assign to any active user; others only
     * users in the same direction (and self), per {@link DirectionScopeService#canAccessUser(UserAccount, Long)}.
     */
    private boolean mayPickAssignee(UserAccount viewer, Long assigneeUserId) {
        if (viewer == null || assigneeUserId == null) {
            return false;
        }
        if (directionScope.isSuperAdmin(viewer)) {
            return true;
        }
        if ("ADMIN".equals(RoleKeys.normalizeForScope(viewer.getRole()))) {
            return true;
        }
        return directionScope.canAccessUser(viewer, assigneeUserId);
    }

    private static boolean isSchedulableTicketStatus(String status) {
        String s = safe(status).toUpperCase(Locale.ROOT);
        if (s.isEmpty()) {
            return true;
        }
        return !"CLOSED".equals(s)
                && !"RESOLVED".equals(s)
                && !"CLOSE_REQUESTED".equals(s)
                && !"MERGED".equals(s);
    }

    private String linkedTicketLabel(Long ticketId) {
        if (ticketId == null) {
            return "";
        }
        return tickets.findById(ticketId).map(t -> ticketNumber(t)).orElse("—");
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private static String ticketNumber(Ticket t) {
        return (t.getTicketNumber() == null || t.getTicketNumber().isBlank())
                ? "TCK-" + t.getId()
                : t.getTicketNumber().trim();
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    @Transactional
    public void saveJob(
            String title,
            String description,
            LocalDate dueDate,
            LocalTime dueTime,
            int reminderMinutes,
            String recurrence,
            List<Long> assigneeIds,
            Long ticketId,
            String actor) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("badTitle");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("badDueDate");
        }
        if (dueTime == null) {
            throw new IllegalArgumentException("badDueTime");
        }
            List<Long> ids = normalizeAssigneeIds(assigneeIds);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("assigneeRequired");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("badAssignee");
        }
        UserAccount viewer = directionScope.requireUser(actor.trim());
        for (Long assigneeId : ids) {
            if (!users.existsById(assigneeId)) {
                throw new IllegalArgumentException("badAssignee");
            }
            UserAccount assignee = users.findById(assigneeId).orElseThrow(() -> new IllegalArgumentException("badAssignee"));
            if (!assignee.isActiveBool()) {
                throw new IllegalArgumentException("inactiveAssignee");
            }
            if (!mayPickAssignee(viewer, assigneeId)) {
                throw new IllegalArgumentException("badAssignee");
            }
        }

        Long resolvedTicketId;
        if (ticketId != null && ticketId > 0) {
            Ticket t = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("ticketNotFound"));
            if (t.getMergedIntoTicketId() != null) {
                throw new IllegalArgumentException("ticketMerged");
            }
            resolvedTicketId = t.getId();
        } else {
            LocalDateTime scheduleAt = LocalDateTime.of(dueDate, dueTime);
            resolvedTicketId =
                    createAutoPlannerTicket(
                            title,
                            description,
                            ids.get(0),
                            dueDate.getYear(),
                            actor,
                            scheduleAt);
        }

        String rec =
                (recurrence == null || recurrence.isBlank())
                        ? "ONCE"
                        : recurrence.trim().toUpperCase(Locale.ROOT);
        if (!SCHEDULER_RECURRENCE.contains(rec)) {
            rec = "ONCE";
        }
        String dueStr = DT.format(LocalDateTime.of(dueDate, dueTime));
        int rem = Math.max(1, reminderMinutes);
        Long createdBy =
                (actor != null && !actor.isBlank())
                        ? users.findByUsernameIgnoreCase(actor).map(UserAccount::getId).orElse(null)
                        : null;

        for (Long assigneeId : ids) {
            AutomatedJob j = new AutomatedJob();
            j.setJobTitle(title.trim());
            j.setJobDescription(description == null ? "" : description.trim());
            j.setDueAt(dueStr);
            j.setReminderMinutes(rem);
            j.setRecurrence(rec);
            j.setAssigneeUserId(assigneeId);
            j.setTicketId(resolvedTicketId);
            j.setPriority("MEDIUM");
            j.setStatus("OPEN");
            j.setActive(1);
            j.setCreatedAt(Instant.now());
            if (createdBy != null) {
                j.setCreatedBy(createdBy);
            }
            jobs.save(j);
        }
    }

    private static List<Long> normalizeAssigneeIds(List<Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : raw) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * SYSCO planner ticket reference: {@code AUTO-TCK-{year}-#####} (e.g. AUTO-TCK-2026-00001), independent of DB id.
     */
    private String allocateNextAutoTicketNumber(int year) {
        String prefix = "AUTO-TCK-" + year + "-";
        int maxSeq = 0;
        for (Ticket t : tickets.findByTicketNumberStartingWith(prefix)) {
            String num = t.getTicketNumber();
            if (num == null || num.length() <= prefix.length()) {
                continue;
            }
            try {
                int seq = Integer.parseInt(num.substring(prefix.length()).trim());
                maxSeq = Math.max(maxSeq, seq);
            } catch (NumberFormatException ignored) {
                // ignore malformed legacy rows
            }
        }
        return prefix + String.format("%05d", maxSeq + 1);
    }

    private Long createAutoPlannerTicket(
            String title,
            String description,
            Long assigneeId,
            int referenceYear,
            String actorUsername,
            LocalDateTime plannerVisibleAfter) {
        Long deptId =
                departments.findAll().stream()
                        .findFirst()
                        .map(d -> d.getId())
                        .orElseThrow(() -> new IllegalArgumentException("noDepartment"));
        Long actorUserId =
                (actorUsername != null && !actorUsername.isBlank())
                        ? users.findByUsernameIgnoreCase(actorUsername.trim())
                                .map(UserAccount::getId)
                                .orElse(assigneeId)
                        : assigneeId;
        Instant now = Instant.now();
        Ticket ticket = new Ticket();
        ticket.setTitle(title.trim());
        ticket.setDescription(description == null ? "" : description.trim());
        ticket.setPriority("MEDIUM");
        ticket.setStatus("OPEN");
        ticket.setTicketType("INTERNAL");
        ticket.setDepartmentId(deptId);
        ticket.setAssignedTo(assigneeId);
        ticket.setCreatedBy(actorUserId);
        ticket.setUpdatedBy(actorUserId);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        if (plannerVisibleAfter != null) {
            ticket.setPlannerVisibleAfter(plannerVisibleAfter.atZone(ZoneId.systemDefault()).toInstant());
        }
        Ticket saved = tickets.save(ticket);
        String ref = allocateNextAutoTicketNumber(referenceYear);
        saved.setTicketNumber(ref);
        tickets.save(saved);
        ticketTimeline.log(
                "CREATED",
                saved.getId(),
                actorUsername == null || actorUsername.isBlank() ? null : actorUsername.trim(),
                assigneeId,
                "Ticket auto (planificateur): " + ref,
                false);
        return saved.getId();
    }

    @Transactional
    public void toggle(long id) {
        AutomatedJob j = jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        j.setActive(j.getActive() != null && j.getActive() == 1 ? 0 : 1);
        jobs.save(j);
    }

    @Transactional
    public void deleteJob(long id) {
        if (!jobs.existsById(id)) {
            throw new IllegalArgumentException("notFound");
        }
        jobs.deleteById(id);
    }

    /**
     * Builds a period PDF (rapport d'exploitation), persists summary row, returns bytes for inline browser display.
     */
    @Transactional
    public byte[] generatePeriodReportPdf(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("badPeriod");
        }
        String monthKey = from + "_au_" + to;
        String fileName = "rapport-exploitation-" + monthKey + ".pdf";
        try {
            OperationalReportPdfService.OperationalReportResult result = operationalReportPdf.build(from, to);
            MonthlyReport r = reports.findByMonthKey(monthKey).orElseGet(MonthlyReport::new);
            r.setMonthKey(monthKey);
            r.setGeneratedAt(Instant.now());
            r.setFilePath(fileName);
            r.setTotalTickets(result.periodTotal());
            r.setOpenTickets(result.periodOpen());
            r.setInProgressTickets(result.periodInProgress());
            r.setClosedTickets(result.periodClosed());
            reports.save(r);
            return result.pdf();
        } catch (IOException e) {
            throw new IllegalStateException("pdf", e);
        }
    }

    private String assigneeLabel(Long id) {
        if (id == null) {
            return "";
        }
        return users.findById(id)
                .map(u -> u.getUsername() + ", " + u.getRole())
                .orElse("");
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    public record JobPage(
            List<JobRow> jobs,
            List<ReportRow> reports,
            List<UserChoice> assignees,
            List<TicketChoice> linkedTickets) {}

    public record JobRow(
            Long id,
            String title,
            String dueAt,
            String assignees,
            String recurrence,
            String status,
            String linkedTicket) {}

    public record ReportRow(Long id, String monthKey, String generatedAt, String summary, String filePath) {}

    public record UserChoice(Long id, String username, String role, String matricule) {}

    public record TicketChoice(Long id, String ticketNumber, String titleShort) {}
}
