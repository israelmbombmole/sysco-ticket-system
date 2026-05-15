package com.sysco.web.service;

import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.TicketAssignment;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.security.RoleKeys;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TicketMonitoringService {

    private final TicketRepository tickets;
    private final UserAccountRepository users;
    private final TicketAssignmentRepository ticketAssignments;
    private final AutomatedJobRepository jobs;
    private final TicketTimelineService ticketTimeline;
    private final DirectionScopeService directionScopeService;
    private final NotificationService notificationService;
    private final TicketManagementService ticketManagementService;
    private final MessageSource messageSource;
    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;
    private static final DateTimeFormatter TASK_DUE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Set<String> ASSIGNABLE_BY_DIRECTEUR = Set.of(
            "SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR", "VERIFICATEUR", "VERIFICATEUR-ASSISTANT", "SECRETAIRE");
    private static final Set<String> ASSIGNABLE_BY_SOUS_DIRECTEUR = Set.of(
            "INSPECTEUR", "CONTROLEUR", "VERIFICATEUR", "VERIFICATEUR-ASSISTANT", "SECRETAIRE");
    private static final Set<String> ASSIGNABLE_BY_INSPECTEUR = Set.of(
            "CONTROLEUR", "VERIFICATEUR", "VERIFICATEUR-ASSISTANT", "SECRETAIRE");
    private static final Set<String> ASSIGNABLE_BY_CONTROLEUR = Set.of("VERIFICATEUR", "VERIFICATEUR-ASSISTANT", "SECRETAIRE");

    public TicketMonitoringPage page(String actorUsername) {
        UserAccount viewer = directionScopeService.requireUser(actorUsername);
        Instant now = Instant.now();
        String viewerRole = RoleKeys.normalizeForScope(viewer.getRole());
        Map<Long, String> userNames = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (a, b) -> a));
        Map<Long, List<Long>> assigneeIdsByTicket = loadAssigneeIdsByTicketForVisibleTickets(now);

        List<TicketCard> allTickets = tickets.findAll().stream()
                .filter(t -> t.getMergedIntoTicketId() == null)
                .filter(t -> !"MERGED".equalsIgnoreCase(safe(t.getStatus())))
                .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                .filter(t -> directionScopeService.canAccessTicket(viewer, t))
                .sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Ticket::getId, Comparator.reverseOrder()))
                .map(t -> toCard(t, userNames, assigneeIdsByTicket))
                .toList();

        List<TicketCard> assignable = allTickets.stream()
                .filter(t -> "OPEN".equalsIgnoreCase(safe(t.status())))
                .limit(15)
                .toList();
        Set<Long> assignableIds = assignable.stream().map(TicketCard::id).collect(Collectors.toSet());
        List<TicketCard> assigned = allTickets.stream()
                .filter(t -> !assignableIds.contains(t.id()))
                .filter(t -> !"OPEN".equalsIgnoreCase(safe(t.status())))
                .toList();
        List<TicketCard> escalated = allTickets.stream()
                .filter(t -> "ESCALATED".equalsIgnoreCase(t.status()))
                .toList();

        List<AgentStat> stats = computeAgentStats(assigneeIdsByTicket, userNames);

        long open = allTickets.stream().filter(t -> isOneOf(t.status(), Set.of("OPEN", "IN_PROGRESS", "ASSIGNED"))).count();
        long closed = allTickets.stream().filter(t -> isOneOf(t.status(), Set.of("CLOSED", "RESOLVED"))).count();
        int openPct = (open + closed) == 0 ? 100 : (int) Math.round((open * 100.0) / (open + closed));
        int closedPct = Math.max(0, 100 - openPct);
        String donutGradient = "conic-gradient(#f59e0b 0 " + openPct + "%, #22c55e " + openPct + "% 100%)";

        List<AgentChoice> agentChoices = users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> isAdminLike(viewer)
                        || (viewer.getDirectionId() != null
                                && u.getDirectionId() != null
                                && viewer.getDirectionId().equals(u.getDirectionId())))
                .filter(u -> canAssignRole(viewerRole, RoleKeys.normalizeForScope(u.getRole())))
                .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new AgentChoice(u.getId(), u.getUsername(), u.getRole(), u.getMatricule()))
                .toList();

        Set<Long> accessibleTicketIds = allTickets.stream().map(TicketCard::id).collect(Collectors.toSet());
        List<MonitoringTaskRow> monitoringTasks =
                jobs.findAll().stream()
                        .filter(job -> !isPlannerLinkedJobDeferred(job, now))
                        .filter(job -> isMonitoringTaskVisible(viewer, job, accessibleTicketIds))
                        .sorted(Comparator.comparing(AutomatedJob::getId).reversed())
                        .map(job -> toMonitoringTaskRow(job, userNames))
                        .toList();

        return new TicketMonitoringPage(
                assignable, assigned, escalated, stats, agentChoices, donutGradient, viewerRole, monitoringTasks);
    }

    /**
     * CSV export for the agent stats block — same aggregation as {@link #page(String)} (assignment rows per username).
     */
    @Transactional(readOnly = true)
    public byte[] exportAgentStatsCsv(String actorUsername, Locale locale) {
        directionScopeService.requireUser(actorUsername);
        Locale loc = locale != null ? locale : Locale.ENGLISH;
        Instant now = Instant.now();
        Map<Long, String> userNames = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (a, b) -> a));
        Map<Long, List<Long>> assigneeIdsByTicket = loadAssigneeIdsByTicketForVisibleTickets(now);
        List<AgentStat> stats = computeAgentStats(assigneeIdsByTicket, userNames);

        String agentCol = messageSource.getMessage("ticketMonitoring.col.agent", null, loc);
        String totalCol = messageSource.getMessage("ticketMonitoring.col.totalTickets", null, loc);
        String metaExported =
                messageSource.getMessage("ticketMonitoring.exportAgentStats.metaExported", null, loc);
        String metaScope = messageSource.getMessage("ticketMonitoring.exportAgentStats.metaScope", null, loc);
        String summaryAgents =
                messageSource.getMessage("ticketMonitoring.exportAgentStats.summaryAgents", null, loc);
        String summaryAssignments =
                messageSource.getMessage("ticketMonitoring.exportAgentStats.summaryAssignments", null, loc);

        int sumAssignments = stats.stream().mapToInt(AgentStat::totalTickets).sum();
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(csvCell(metaExported)).append(',').append(csvCell(Instant.now().toString())).append('\n');
        sb.append(csvCell(metaScope)).append('\n');
        sb.append('\n');
        sb.append(csvCell(agentCol)).append(',').append(csvCell(totalCol)).append('\n');
        for (AgentStat row : stats) {
            sb.append(csvCell(row.agent())).append(',').append(row.totalTickets()).append('\n');
        }
        sb.append('\n');
        sb.append(csvCell(summaryAgents)).append(',').append(stats.size()).append('\n');
        sb.append(csvCell(summaryAssignments)).append(',').append(sumAssignments).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<Long, List<Long>> loadAssigneeIdsByTicketForVisibleTickets(Instant now) {
        return ticketAssignments.findByTicketIdIn(
                        tickets.findAll().stream()
                                .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                                .map(Ticket::getId)
                                .toList())
                .stream()
                .collect(Collectors.groupingBy(
                        TicketAssignment::getTicketId, Collectors.mapping(TicketAssignment::getUserId, Collectors.toList())));
    }

    private static List<AgentStat> computeAgentStats(
            Map<Long, List<Long>> assigneeIdsByTicket, Map<Long, String> userNames) {
        return assigneeIdsByTicket.values().stream()
                .flatMap(List::stream)
                .map(uid -> userNames.getOrDefault(uid, ""))
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
                .entrySet()
                .stream()
                .map(e -> new AgentStat(e.getKey(), Math.toIntExact(e.getValue())))
                .sorted(Comparator.comparingInt(AgentStat::totalTickets).reversed())
                .toList();
    }

    private static String csvCell(String s) {
        if (s == null) {
            return "";
        }
        String v = s.replace("\"", "\"\"");
        if (v.indexOf(',') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0 || v.indexOf('"') >= 0) {
            return '"' + v + '"';
        }
        return v;
    }

    private boolean isPlannerLinkedJobDeferred(AutomatedJob job, Instant now) {
        Long tid = job.getTicketId();
        if (tid == null) {
            return false;
        }
        return tickets.findById(tid).map(t -> PlannerTicketVisibility.isHiddenFromOperationalViews(t, now)).orElse(false);
    }

    private boolean isMonitoringTaskVisible(UserAccount viewer, AutomatedJob job, Set<Long> accessibleTicketIds) {
        Long tid = job.getTicketId();
        if (tid != null) {
            if (accessibleTicketIds.contains(tid)) {
                return true;
            }
            return tickets.findById(tid).map(t -> directionScopeService.canAccessTicket(viewer, t)).orElse(false);
        }
        Long aid = job.getAssigneeUserId();
        if (aid != null) {
            return directionScopeService.canAccessUser(viewer, aid);
        }
        return isAdminLike(viewer);
    }

    /**
     * Same visibility as rows in the monitoring “Tasks” table: ticket-linked tasks follow ticket scope; standalone tasks
     * follow assignee scope; admins see unattached tasks.
     */
    public boolean canViewerSeeTaskInMonitoring(String actorUsername, long jobId) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return false;
        }
        UserAccount viewer = users.findByUsernameIgnoreCase(actorUsername).orElse(null);
        if (viewer == null) {
            return false;
        }
        AutomatedJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return false;
        }
        if (isPlannerLinkedJobDeferred(job, Instant.now())) {
            return false;
        }
        Long tid = job.getTicketId();
        if (tid != null) {
            return tickets.findById(tid).map(t -> directionScopeService.canAccessTicket(viewer, t)).orElse(false);
        }
        Long aid = job.getAssigneeUserId();
        if (aid != null) {
            return directionScopeService.canAccessUser(viewer, aid);
        }
        return isAdminLike(viewer);
    }

    private MonitoringTaskRow toMonitoringTaskRow(AutomatedJob job, Map<Long, String> userNames) {
        Long tid = job.getTicketId();
        String ticketRef = "—";
        if (tid != null) {
            ticketRef = tickets.findById(tid).map(TicketMonitoringService::ticketNumber).orElse("—");
        }
        String assignee =
                job.getAssigneeUserId() == null
                        ? "—"
                        : userNames.getOrDefault(job.getAssigneeUserId(), "—");
        return new MonitoringTaskRow(
                job.getId(),
                job.getJobTitle() == null ? "" : job.getJobTitle(),
                assignee,
                ticketRef,
                tid,
                job.getStatus() == null ? "" : job.getStatus(),
                job.getDueAt() == null ? "" : job.getDueAt(),
                job.getPriority() == null ? "" : job.getPriority());
    }

    /**
     * Single-assign from monitoring: sets {@code assigned_to} to this user (primary handler).
     *
     * @see #assignAddingParticipants(Long, Long, String) for bulk multi-assign (keeps first primary)
     */
    @Transactional
    public void assign(Long ticketId, Long userId, String actorUsername) {
        assignImpl(ticketId, userId, actorUsername, true);
    }

    /**
     * Adds (or refreshes) an assignment row without replacing {@code assigned_to} when a primary is already set —
     * used by bulk / multi-assign so every selected agent stays on the ticket.
     */
    @Transactional
    public void assignAddingParticipants(Long ticketId, Long userId, String actorUsername) {
        assignImpl(ticketId, userId, actorUsername, false);
    }

    private void assignImpl(Long ticketId, Long userId, String actorUsername, boolean replacePrimaryAssignee) {
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount viewer = directionScopeService.requireUser(actorUsername);
        if (PlannerTicketVisibility.isHiddenFromOperationalViews(t, Instant.now())) {
            throw new IllegalArgumentException("notFound");
        }
        if (!directionScopeService.canAccessTicket(viewer, t) || !directionScopeService.canAccessUser(viewer, userId)) {
            throw new IllegalArgumentException("notFound");
        }
        UserAccount assignee = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("badAssignee"));
        ensureAssignmentAllowed(viewer, assignee);
        if (!assignee.isActiveBool()) {
            throw new IllegalStateException("assigneeInactive");
        }
        if (!ticketAssignments.existsByTicketIdAndUserId(ticketId, userId)) {
            TicketAssignment ta = new TicketAssignment();
            ta.setTicketId(ticketId);
            ta.setUserId(userId);
            ta.setAssignedAt(Instant.now());
            ticketAssignments.save(ta);
        }
        if (replacePrimaryAssignee || t.getAssignedTo() == null) {
            t.setAssignedTo(userId);
        }
        if (!"CLOSED".equalsIgnoreCase(t.getStatus()) && !"RESOLVED".equalsIgnoreCase(t.getStatus())) {
            t.setStatus("ASSIGNED");
        }
        t.setUpdatedAt(Instant.now());
        resolveActorId(actorUsername).ifPresent(t::setUpdatedBy);
        tickets.save(t);
        ticketTimeline.log("ASSIGNED", ticketId, actorUsername, userId, "Ticket assign\u00e9");
        notificationService.notifyTicketAssignedToYou(userId, ticketNumber(t), t.getId());
    }

    @Transactional
    public void assignBulk(List<Long> ticketIds, List<Long> userIds, boolean createTaskPerUser, String actorUsername) {
        if (ticketIds == null || ticketIds.isEmpty() || userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("invalidSelection");
        }
        List<Long> uniqueTicketIds = ticketIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        List<Long> uniqueUserIds = new LinkedHashSet<>(userIds).stream().toList();
        if (uniqueTicketIds.isEmpty() || uniqueUserIds.isEmpty()) {
            throw new IllegalArgumentException("invalidSelection");
        }
        for (Long ticketId : uniqueTicketIds) {
            for (Long assigneeId : uniqueUserIds) {
                assignAddingParticipants(ticketId, assigneeId, actorUsername);
            }
            if (createTaskPerUser && uniqueUserIds.size() >= 2) {
                for (Long selectedUserId : uniqueUserIds) {
                    createAssignmentTask(ticketId, selectedUserId, actorUsername);
                }
            }
        }
    }

    @Transactional
    public void assignWithTasks(
            Long ticketId,
            List<Long> assigneeIds,
            List<String> taskTitles,
            List<String> taskDescriptions,
            List<String> taskDueAts,
            List<Integer> reminderMinutes,
            List<String> priorities,
            List<List<MultipartFile>> attachmentGroups,
            String actorUsername) {
        if (ticketId == null || assigneeIds == null || assigneeIds.isEmpty()) {
            throw new IllegalArgumentException("invalidSelection");
        }
        List<Long> uniqueUserIds = new LinkedHashSet<>(assigneeIds).stream()
                .filter(id -> id != null && id > 0)
                .toList();
        if (uniqueUserIds.isEmpty()) {
            throw new IllegalArgumentException("invalidSelection");
        }
        for (int i = 0; i < uniqueUserIds.size(); i++) {
            Long assigneeId = uniqueUserIds.get(i);
            assignAddingParticipants(ticketId, assigneeId, actorUsername);
            String title = (taskTitles != null && i < taskTitles.size()) ? taskTitles.get(i) : "";
            String desc = (taskDescriptions != null && i < taskDescriptions.size()) ? taskDescriptions.get(i) : "";
            String due = (taskDueAts != null && i < taskDueAts.size()) ? taskDueAts.get(i) : "";
            Integer rem = (reminderMinutes != null && i < reminderMinutes.size()) ? reminderMinutes.get(i) : null;
            String priority = (priorities != null && i < priorities.size()) ? priorities.get(i) : "MEDIUM";
            List<MultipartFile> attachments = (attachmentGroups != null && i < attachmentGroups.size()) ? attachmentGroups.get(i) : List.of();
            createDelegationTask(ticketId, assigneeId, title, desc, due, rem, priority, attachments, actorUsername);
        }
    }

    @Transactional
    public void createDelegationTask(
            Long ticketId,
            Long assigneeId,
            String title,
            String description,
            String dueAt,
            Integer reminderMinutes,
            String priority,
            List<MultipartFile> attachments,
            String actorUsername) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount viewer = directionScopeService.requireUser(actorUsername);
        if (PlannerTicketVisibility.isHiddenFromOperationalViews(ticket, Instant.now())) {
            throw new IllegalArgumentException("notFound");
        }
        if (!directionScopeService.canAccessTicket(viewer, ticket) || !directionScopeService.canAccessUser(viewer, assigneeId)) {
            throw new IllegalArgumentException("notFound");
        }
        UserAccount assignee = users.findById(assigneeId).orElseThrow(() -> new IllegalArgumentException("badAssignee"));
        ensureAssignmentAllowed(viewer, assignee);
        if (!assignee.isActiveBool()) {
            throw new IllegalStateException("assigneeInactive");
        }
        AutomatedJob job = new AutomatedJob();
        job.setJobTitle((title == null || title.isBlank()) ? ("Traitement " + ticketNumber(ticket)) : title.trim());
        job.setJobDescription(description == null ? "" : description.trim());
        job.setAssigneeUserId(assignee.getId());
        job.setTicketId(ticket.getId());
        job.setPriority(normalizePriority(priority));
        job.setReminderMinutes(reminderMinutes == null ? 30 : reminderMinutes);
        job.setRecurrence("ONCE");
        job.setActive(1);
        job.setStatus("OPEN");
        job.setDueAt((dueAt == null || dueAt.isBlank()) ? LocalDateTime.now().plusHours(8).format(TASK_DUE_FMT) : dueAt.trim());
        job.setCreatedAt(Instant.now());
        resolveActorId(actorUsername).ifPresent(job::setCreatedBy);
        job.setAttachmentPaths(saveTaskAttachments(ticket.getId(), attachments));
        jobs.save(job);
        ticketTimeline.log(
                "TASK_CREATED",
                ticketId,
                actorUsername,
                assigneeId,
                "T\u00e2che d\u00e9l\u00e9gu\u00e9e depuis le suivi des tickets");
        notificationService.notifyNewTaskOnTicket(
                assignee.getId(), ticketNumber(ticket), job.getJobTitle(), ticket.getId());
    }

    @Transactional
    public void createDelegationTasks(
            Long ticketId,
            List<Long> assigneeIds,
            List<String> taskTitles,
            List<String> taskDescriptions,
            List<String> taskDueAts,
            List<Integer> reminderMinutes,
            String actorUsername) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            throw new IllegalArgumentException("invalidSelection");
        }
        for (int i = 0; i < assigneeIds.size(); i++) {
            Long aid = assigneeIds.get(i);
            String title = (taskTitles != null && i < taskTitles.size()) ? taskTitles.get(i) : "";
            String desc = (taskDescriptions != null && i < taskDescriptions.size()) ? taskDescriptions.get(i) : "";
            String due = (taskDueAts != null && i < taskDueAts.size()) ? taskDueAts.get(i) : "";
            Integer rem = (reminderMinutes != null && i < reminderMinutes.size()) ? reminderMinutes.get(i) : null;
            createDelegationTask(ticketId, aid, title, desc, due, rem, "MEDIUM", List.of(), actorUsername);
        }
    }

    @Transactional
    public void escalate(Long ticketId, String actorUsername) {
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount viewer = directionScopeService.requireUser(actorUsername);
        if (!directionScopeService.canAccessTicket(viewer, t)) {
            throw new IllegalArgumentException("notFound");
        }
        if ("CLOSED".equalsIgnoreCase(t.getStatus()) || "RESOLVED".equalsIgnoreCase(t.getStatus())) {
            throw new IllegalStateException("alreadyClosed");
        }
        String actorRole = RoleKeys.normalizeForScope(viewer.getRole());
        if (!canEscalate(actorRole)) {
            throw new IllegalStateException("escalationNotAllowed");
        }
        applyEscalationRouting(t, viewer, actorRole);
        t.setStatus("ESCALATED");
        t.setUpdatedAt(Instant.now());
        resolveActorId(actorUsername).ifPresent(t::setUpdatedBy);
        tickets.save(t);
        ticketTimeline.log("ESCALATED", ticketId, actorUsername, null, "Ticket escalad\u00e9");
    }

    @Transactional
    public void close(Long ticketId, String actorUsername) {
        ticketManagementService.close(ticketId, actorUsername);
    }

    private java.util.Optional<Long> resolveActorId(String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return java.util.Optional.empty();
        }
        return users.findByUsernameIgnoreCase(actorUsername).map(UserAccount::getId);
    }

    private static boolean isOneOf(String status, Set<String> set) {
        if (status == null) {
            return false;
        }
        return set.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    private void createAssignmentTask(Long ticketId, Long assigneeId, String actorUsername) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount assignee = users.findById(assigneeId).orElseThrow(() -> new IllegalArgumentException("badAssignee"));
        AutomatedJob job = new AutomatedJob();
        job.setJobTitle("Traitement ticket " + ticketNumber(ticket));
        job.setJobDescription("Action requise après assignation SYSCO FX: " + safe(ticket.getTitle()));
        job.setAssigneeUserId(assignee.getId());
        job.setTicketId(ticket.getId());
        job.setPriority("MEDIUM");
        job.setReminderMinutes(30);
        job.setRecurrence("ONCE");
        job.setActive(1);
        job.setStatus("OPEN");
        job.setDueAt(LocalDateTime.now().plusHours(8).format(TASK_DUE_FMT));
        job.setCreatedAt(Instant.now());
        resolveActorId(actorUsername).ifPresent(job::setCreatedBy);
        jobs.save(job);
        ticketTimeline.log(
                "TASK_CREATED",
                ticketId,
                actorUsername,
                assigneeId,
                "T\u00e2che auto-cr\u00e9\u00e9e pour assignation multiple");
        notificationService.notifyNewTaskOnTicket(
                assignee.getId(), ticketNumber(ticket), job.getJobTitle(), ticket.getId());
    }

    private static String normalizePriority(String p) {
        String v = p == null ? "" : p.trim().toUpperCase(Locale.ROOT);
        if (Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(v)) {
            return v;
        }
        return "MEDIUM";
    }

    private String saveTaskAttachments(Long ticketId, List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        try {
            Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("task-attachments").resolve(String.valueOf(ticketId));
            Files.createDirectories(dir);
            String out = "";
            for (MultipartFile file : attachments) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String name = file.getOriginalFilename() == null ? "attachment.bin" : stripPath(file.getOriginalFilename());
                Path dest = dir.resolve(System.currentTimeMillis() + "_" + name);
                Files.copy(file.getInputStream(), dest);
                out = out.isBlank() ? dest.toString() : (out + ";;" + dest);
            }
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripPath(String name) {
        String s = name.replace('\\', '/');
        int idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    private static String ticketNumber(Ticket t) {
        return (t.getTicketNumber() == null || t.getTicketNumber().isBlank()) ? ("TCK-" + t.getId()) : t.getTicketNumber();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean isAdminLike(UserAccount user) {
        String role = user == null ? "" : RoleKeys.normalizeForScope(user.getRole());
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private static boolean canAssignRole(String actorRole, String targetRole) {
        if ("ADMIN".equals(actorRole) || "SUPER_ADMIN".equals(actorRole)) {
            return true;
        }
        return switch (actorRole) {
            case "DIRECTEUR" -> ASSIGNABLE_BY_DIRECTEUR.contains(targetRole);
            case "SOUS-DIRECTEUR" -> ASSIGNABLE_BY_SOUS_DIRECTEUR.contains(targetRole);
            case "INSPECTEUR" -> ASSIGNABLE_BY_INSPECTEUR.contains(targetRole);
            case "CONTROLEUR" -> ASSIGNABLE_BY_CONTROLEUR.contains(targetRole);
            default -> false;
        };
    }

    private void ensureAssignmentAllowed(UserAccount viewer, UserAccount assignee) {
        String actorRole = RoleKeys.normalizeForScope(viewer.getRole());
        String targetRole = RoleKeys.normalizeForScope(assignee.getRole());
        if (!canAssignRole(actorRole, targetRole)) {
            throw new IllegalStateException("assignmentNotAllowed");
        }
        if (!isAdminLike(viewer)) {
            if (viewer.getDirectionId() == null || assignee.getDirectionId() == null
                    || !viewer.getDirectionId().equals(assignee.getDirectionId())) {
                throw new IllegalStateException("assignmentNotAllowed");
            }
        }
    }

    private static boolean canEscalate(String actorRole) {
        return Set.of("DIRECTEUR", "SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR", "VERIFICATEUR", "VERIFICATEUR-ASSISTANT", "SECRETAIRE", "ADMIN", "SUPER_ADMIN")
                .contains(actorRole);
    }

    private void applyEscalationRouting(Ticket ticket, UserAccount actor, String actorRole) {
        String nextRole = switch (actorRole) {
            case "VERIFICATEUR-ASSISTANT" -> "VERIFICATEUR";
            case "VERIFICATEUR" -> "CONTROLEUR";
            case "CONTROLEUR" -> "INSPECTEUR";
            case "INSPECTEUR" -> "SOUS-DIRECTEUR";
            case "SOUS-DIRECTEUR" -> "DIRECTEUR";
            default -> "";
        };
        if (nextRole.isBlank()) {
            return;
        }
        Long nextAssignee = users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> nextRole.equals(RoleKeys.normalizeForScope(u.getRole())))
                .filter(u -> isAdminLike(actor)
                        || (actor.getDirectionId() != null && u.getDirectionId() != null
                                && actor.getDirectionId().equals(u.getDirectionId())))
                .map(UserAccount::getId)
                .findFirst()
                .orElse(null);
        if (nextAssignee != null) {
            ticket.setAssignedTo(nextAssignee);
            if (!ticketAssignments.existsByTicketIdAndUserId(ticket.getId(), nextAssignee)) {
                TicketAssignment ta = new TicketAssignment();
                ta.setTicketId(ticket.getId());
                ta.setUserId(nextAssignee);
                ta.setAssignedAt(Instant.now());
                ticketAssignments.save(ta);
            }
        }
    }

    private static TicketCard toCard(Ticket t, Map<Long, String> userNames, Map<Long, List<Long>> assigneeIdsByTicket) {
        String ticketNumber = (t.getTicketNumber() == null || t.getTicketNumber().isBlank())
                ? ("TCK-" + t.getId())
                : t.getTicketNumber();
        String assigned = assigneeIdsByTicket.getOrDefault(t.getId(), List.of()).stream()
                .map(uid -> userNames.getOrDefault(uid, ""))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return new TicketCard(
                t.getId(),
                ticketNumber,
                t.getTitle() == null ? "" : t.getTitle(),
                t.getStatus() == null ? "" : t.getStatus(),
                t.getPriority() == null ? "" : t.getPriority(),
                assigned);
    }

    public record TicketMonitoringPage(
            List<TicketCard> assignableTickets,
            List<TicketCard> assignedTickets,
            List<TicketCard> escalatedTickets,
            List<AgentStat> agentStats,
            List<AgentChoice> agentChoices,
            String donutGradient,
            String roleKey,
            List<MonitoringTaskRow> monitoringTasks) {}

    public record MonitoringTaskRow(
            Long jobId,
            String title,
            String assigneeUsername,
            String ticketRef,
            Long ticketId,
            String status,
            String dueAt,
            String priority) {}

    public record TicketCard(
            Long id, String ticketNumber, String title, String status, String priority, String assignedToUsername) {}

    public record AgentStat(String agent, int totalTickets) {}

    public record AgentChoice(Long id, String username, String role, String matricule) {}
}
