package com.sysco.web.service;

import com.sysco.web.domain.Department;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.TicketAssignment;
import com.sysco.web.domain.TicketComment;
import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.TicketEvent;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.CourierPacketRepository;
import com.sysco.web.repo.DepartmentRepository;
import com.sysco.web.repo.TicketCommentRepository;
import com.sysco.web.repo.TicketEventRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.security.TicketRoleRanks;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.sysco.web.util.DisplayDateFormatter;
import com.sysco.web.util.PriorityFrenchLabels;
import com.sysco.web.util.TicketSlaCalculator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketManagementService {

    private static final Set<String> ALLOWED_COMMENT_EXT = Set.of("png", "jpg", "jpeg", "pdf", "docx", "xlsx", "txt");
    private static final Set<String> ALLOWED_TASK_EXT = Set.of("png", "jpg", "jpeg", "pdf", "docx", "xlsx", "txt");

    /** Assignee roles that may finalize closure themselves (no escalation to another tier). */
    private static final Set<String> HIGH_SELF_APPROVE_ROLES =
            Set.of("INSPECTEUR", "SOUS-DIRECTEUR", "DIRECTEUR", "ADMIN", "SUPER_ADMIN");

    /**
     * Stored in {@code close_review_role}: any inspector / sous-directeur / directeur in scope may finalize closure
     * (multi-assign collaboration path).
     */
    private static final String CLOSURE_REVIEW_SENIOR_POOL = "CLOSURE_SENIOR";

    /** Roles that may escalate scheduled tasks from the ticket management list. */
    private static final Set<String> TASK_ESCALATION_ROLES =
            Set.of("DIRECTEUR", "SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR", "ADMIN", "SUPER_ADMIN");

    private final TicketRepository tickets;
    private final UserAccountRepository users;
    private final CourierPacketRepository courierPackets;
    private final TicketCommentRepository ticketComments;
    private final TicketEventRepository ticketEvents;
    private final TicketAssignmentRepository ticketAssignments;
    private final AutomatedJobRepository jobs;
    private final TicketTimelineService ticketTimeline;
    private final DirectionScopeService directionScopeService;
    private final NotificationService notificationService;
    private final TicketEmailNotificationService ticketEmailNotificationService;
    private final DepartmentRepository departments;
    private final SousDirectionRepository sousDirections;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final TicketClosureAssistantService ticketClosureAssistant;
    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    public TicketManagementPage page(
            String status,
            String agent,
            String q,
            String taskStatus,
            String taskAgent,
            String taskQ,
            String actorUsername) {
        String s = safe(status);
        String a = safe(agent);
        String query = safe(q).toLowerCase(Locale.ROOT);
        String ts = safe(taskStatus);
        String ta = safe(taskAgent);
        String tquery = safe(taskQ).toLowerCase(Locale.ROOT);
        UserAccount viewer = directionScopeService.requireUser(actorUsername);
        Instant now = Instant.now();

        Map<Long, String> userNames = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (x, y) -> x));
        Map<Long, List<Long>> assigneeIdsByTicket = ticketAssignments.findByTicketIdIn(
                        tickets.findAll().stream()
                                .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                                .map(Ticket::getId)
                                .toList())
                .stream()
                .collect(Collectors.groupingBy(TicketAssignment::getTicketId,
                        Collectors.mapping(TicketAssignment::getUserId, Collectors.toList())));

        Set<Long> accessibleTicketIds =
                tickets.findAll().stream()
                        .filter(t -> t.getMergedIntoTicketId() == null)
                        .filter(t -> !"MERGED".equalsIgnoreCase(safe(t.getStatus())))
                        .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                        .filter(t -> directionScopeService.canAccessTicket(viewer, t))
                        .map(Ticket::getId)
                        .collect(Collectors.toSet());

        List<TicketLine> rows = tickets.findAll().stream()
                .filter(t -> t.getMergedIntoTicketId() == null)
                .filter(t -> !"MERGED".equalsIgnoreCase(safe(t.getStatus())))
                .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                .filter(t -> directionScopeService.canAccessTicket(viewer, t))
                .map(t -> toLine(t, userNames, assigneeIdsByTicket))
                .filter(t -> s.isBlank() || t.status().equalsIgnoreCase(s))
                .filter(t -> a.isBlank() || t.agent().equalsIgnoreCase(a))
                .filter(t -> query.isBlank() || matches(t, query))
                .sorted(Comparator.comparing(TicketLine::id).reversed())
                .toList();

        List<String> agentOptions = rows.stream()
                .map(TicketLine::agent)
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        boolean viewerMayEscalateTasks = canEscalateTasksRole(viewer);
        List<TaskMgmtLine> taskRows =
                buildTaskRows(viewer, accessibleTicketIds, userNames, ts, ta, tquery, viewerMayEscalateTasks, now);
        List<String> taskAgentOptions =
                taskRows.stream()
                        .map(TaskMgmtLine::assignee)
                        .filter(v -> v != null && !v.isBlank())
                        .filter(v -> !"—".equals(v))
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

        return new TicketManagementPage(
                rows,
                s,
                a,
                q == null ? "" : q,
                agentOptions,
                taskRows,
                ts,
                ta,
                taskQ == null ? "" : taskQ,
                taskAgentOptions,
                viewerMayEscalateTasks);
    }

    private static boolean canEscalateTasksRole(UserAccount viewer) {
        if (viewer == null) {
            return false;
        }
        String role = RoleKeys.normalizeForScope(viewer.getRole());
        return TASK_ESCALATION_ROLES.contains(role);
    }

    private boolean isTaskVisibleToViewer(UserAccount viewer, AutomatedJob job, Set<Long> accessibleTicketIds) {
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
        String role = viewer == null ? "" : RoleKeys.normalizeForScope(viewer.getRole());
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private List<TaskMgmtLine> buildTaskRows(
            UserAccount viewer,
            Set<Long> accessibleTicketIds,
            Map<Long, String> userNames,
            String taskStatus,
            String taskAgent,
            String taskQuery,
            boolean viewerMayEscalateTasks,
            Instant now) {
        return jobs.findAll().stream()
                .filter(job -> !isPlannerLinkedJobDeferred(job, now))
                .filter(job -> isTaskVisibleToViewer(viewer, job, accessibleTicketIds))
                .map(job -> toTaskMgmtLine(job, userNames, viewerMayEscalateTasks))
                .filter(row -> taskStatus.isBlank() || row.status().equalsIgnoreCase(taskStatus))
                .filter(row -> taskAgent.isBlank() || row.assignee().equalsIgnoreCase(taskAgent))
                .filter(row -> taskQuery.isBlank() || matchesTask(row, taskQuery))
                .sorted(Comparator.comparing(TaskMgmtLine::id).reversed())
                .toList();
    }

    private boolean isPlannerLinkedJobDeferred(AutomatedJob job, Instant now) {
        Long tid = job.getTicketId();
        if (tid == null) {
            return false;
        }
        return tickets.findById(tid).map(t -> PlannerTicketVisibility.isHiddenFromOperationalViews(t, now)).orElse(false);
    }

    private TaskMgmtLine toTaskMgmtLine(AutomatedJob job, Map<Long, String> userNames, boolean viewerMayEscalateTasks) {
        Long tid = job.getTicketId();
        String ticketRef = "—";
        if (tid != null) {
            ticketRef = tickets.findById(tid).map(TicketManagementService::ticketNumber).orElse("—");
        }
        String assignee =
                job.getAssigneeUserId() == null ? "—" : userNames.getOrDefault(job.getAssigneeUserId(), "—");
        boolean active = job.getActive() != null && job.getActive() == 1;
        boolean closed = "CLOSED".equalsIgnoreCase(safe(job.getStatus()));
        boolean canEscalate = viewerMayEscalateTasks && active && !closed;
        return new TaskMgmtLine(
                job.getId(),
                job.getJobTitle() == null ? "" : job.getJobTitle(),
                assignee,
                ticketRef,
                tid,
                job.getStatus() == null ? "" : job.getStatus(),
                job.getDueAt() == null ? "" : job.getDueAt(),
                job.getPriority() == null ? "" : job.getPriority(),
                active,
                canEscalate);
    }

    private static boolean matchesTask(TaskMgmtLine row, String q) {
        String activeStr = row.active() ? "active" : "inactive";
        return contains(row.title(), q)
                || contains(row.assignee(), q)
                || contains(row.status(), q)
                || contains(row.ticketRef(), q)
                || contains(row.dueAt(), q)
                || contains(row.priority(), q)
                || contains(activeStr, q);
    }

    @Transactional
    public void escalateTask(Long jobId, String actorUsername) {
        UserAccount viewer = directionScopeService.requireUser(actorUsername);
        if (!canEscalateTasksRole(viewer)) {
            throw new IllegalStateException("taskEscalationNotAllowed");
        }
        AutomatedJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        Instant nowEsc = Instant.now();
        if (isPlannerLinkedJobDeferred(job, nowEsc)) {
            throw new IllegalArgumentException("notFound");
        }
        Set<Long> accessibleTicketIds =
                tickets.findAll().stream()
                        .filter(t -> t.getMergedIntoTicketId() == null)
                        .filter(t -> !"MERGED".equalsIgnoreCase(safe(t.getStatus())))
                        .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, nowEsc))
                        .filter(t -> directionScopeService.canAccessTicket(viewer, t))
                        .map(Ticket::getId)
                        .collect(Collectors.toSet());
        if (!isTaskVisibleToViewer(viewer, job, accessibleTicketIds)) {
            throw new IllegalArgumentException("notFound");
        }
        if (job.getActive() == null || job.getActive() != 1) {
            throw new IllegalStateException("taskInactive");
        }
        if ("CLOSED".equalsIgnoreCase(safe(job.getStatus()))) {
            throw new IllegalStateException("alreadyClosed");
        }
        job.setStatus("ESCALATED");
        jobs.save(job);
        Long linkedTicketId = job.getTicketId();
        if (linkedTicketId != null) {
            ticketTimeline.log(
                    "TASK_ESCALATED",
                    linkedTicketId,
                    actorUsername,
                    job.getAssigneeUserId(),
                    "T\u00e2che escalad\u00e9e : " + safe(job.getJobTitle()));
        }
    }

    /**
     * Tickets waiting for this user to complete the closure checklist and confirm (status {@code CLOSE_REQUESTED} and
     * {@link #viewerMayFinalizeClosure}).
     */
    @Transactional(readOnly = true)
    public List<PendingClosureTicketRow> pendingClosureReviewsFor(String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return List.of();
        }
        Map<Long, String> userNames =
                users.findAll().stream().collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (a, b) -> a));
        Instant now = Instant.now();
        return tickets.findAll().stream()
                .filter(t -> t.getMergedIntoTicketId() == null)
                .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                .filter(t -> "CLOSE_REQUESTED".equalsIgnoreCase(safe(t.getStatus())))
                .filter(t -> viewerMayFinalizeClosure(t, actorUsername))
                .sorted(Comparator.comparing(Ticket::getCloseRequestedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Ticket::getId, Comparator.reverseOrder()))
                .map(t ->
                        new PendingClosureTicketRow(
                                t.getId(),
                                ticketNumber(t),
                                safe(t.getTitle()),
                                t.getCloseRequestedBy() == null
                                        ? ""
                                        : userNames.getOrDefault(t.getCloseRequestedBy(), ""),
                                safe(t.getCloseReviewRole())))
                .toList();
    }

    public TicketDetail detail(Long id, String actorUsername) {
        return detail(id, actorUsername, Locale.FRENCH);
    }

    public TicketDetail detail(Long id, String actorUsername, Locale locale) {
        Locale loc = locale != null ? locale : Locale.FRENCH;
        Ticket t = scopedTicket(id, actorUsername);
        Map<Long, String> userNames = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (x, y) -> x));
        String assignedAgents = ticketAssignments.findByTicketId(t.getId()).stream()
                .map(TicketAssignment::getUserId)
                .map(uid -> userNames.getOrDefault(uid, ""))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        if (assignedAgents.isBlank() && t.getAssignedTo() != null) {
            assignedAgents = userNames.getOrDefault(t.getAssignedTo(), "");
        }
        List<LinkedCourierRow> linkedCouriers = courierPackets.findTop10ByLinkedTicketIdOrderByCreatedAtDesc(t.getId()).stream()
                .map(c -> new LinkedCourierRow(
                        c.getId(),
                        safe(c.getRefCode()),
                        safe(c.getTitle()),
                        safe(c.getStatus()).toUpperCase(Locale.ROOT),
                        DisplayDateFormatter.formatInstant(c.getCreatedAt())))
                .toList();
        List<TicketCommentRow> comments = ticketComments.findByTicketIdOrderByCreatedAtDesc(t.getId()).stream()
                .map(c -> new TicketCommentRow(
                        c.getId(),
                        c.getAuthorUserId() == null ? "" : userNames.getOrDefault(c.getAuthorUserId(), ""),
                        safe(c.getCommentText()),
                        safe(c.getAttachmentPath()),
                        joinedFileNames(c.getAttachmentPath()),
                        formatTs(c.getCreatedAt())))
                .toList();
        List<TicketGenealogyRow> genealogy = buildGenealogy(t, userNames);
        List<AutomatedJob> ticketJobs = jobs.findByTicketId(t.getId());
        List<TicketTaskRow> tasks = ticketJobs.stream()
                .sorted(Comparator.comparing(AutomatedJob::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(j -> new TicketTaskRow(
                        j.getId(),
                        safe(j.getJobTitle()),
                        j.getAssigneeUserId() == null ? "" : userNames.getOrDefault(j.getAssigneeUserId(), ""),
                        safe(j.getStatus()).isBlank() ? ((j.getActive() != null && j.getActive() == 1) ? "OPEN" : "CLOSED") : safe(j.getStatus()),
                        safe(j.getDueAt()),
                        DisplayDateFormatter.formatInstant(j.getStartedAt()),
                        DisplayDateFormatter.formatInstant(j.getClosedAt()),
                        durationLabel(j.getStartedAt(), j.getClosedAt()),
                        safe(j.getPriority())))
                .toList();

        LinkedHashSet<Long> distinctAssigneeIds = new LinkedHashSet<>();
        ticketAssignments.findByTicketId(t.getId()).forEach(a -> {
            if (a.getUserId() != null) {
                distinctAssigneeIds.add(a.getUserId());
            }
        });
        if (t.getAssignedTo() != null) {
            distinctAssigneeIds.add(t.getAssignedTo());
        }
        for (AutomatedJob j : ticketJobs) {
            if (j.getAssigneeUserId() != null) {
                distinctAssigneeIds.add(j.getAssigneeUserId());
            }
        }
        List<String> assignedAgentsPluralList = distinctAssigneeIds.stream()
                .map(uid -> userNames.getOrDefault(uid, ""))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        boolean showPluralAgentsRow = assignedAgentsPluralList.size() > 1;
        UserAccount actor = directionScopeService.requireUser(actorUsername);
        List<TaskAgentOption> taskAgentOptions = buildLegacyTaskAgentOptions(t, userNames);

        List<TaskSousDirectionOption> taskSousDirections = List.of();
        Long defaultTaskSd = actor.getSousDirectionId();
        String assigneesJson = "{}";
        boolean sdPicker = taskAssignmentSousDirectionPickerEnabled(actor);

        if (sdPicker) {
            LinkedHashSet<Long> sdIds = distinctSousDirectionIdsVisibleToActor(actor);
            Map<Long, String> sdNames = sousDirections.findAll().stream()
                    .collect(Collectors.toMap(SousDirection::getId, SousDirection::getName, (a, b) -> a));
            taskSousDirections = orderTaskSousDirectionOptions(sdIds, defaultTaskSd, sdNames);
            if (defaultTaskSd == null && !taskSousDirections.isEmpty()) {
                defaultTaskSd = taskSousDirections.get(0).id();
            }
            if (taskSousDirections.isEmpty()) {
                sdPicker = false;
                assigneesJson = serializeAssigneesPayload(Map.of("legacy", taskAgentOptions));
            } else {
                Map<String, List<TaskAgentOption>> bySd = new LinkedHashMap<>();
                for (TaskSousDirectionOption opt : taskSousDirections) {
                    bySd.put(String.valueOf(opt.id()), collectTaskAssigneesForSousDirection(actor, t, opt.id()));
                }
                assigneesJson = serializeAssigneesPayload(bySd);
            }
        } else {
            assigneesJson = serializeAssigneesPayload(Map.of("legacy", taskAgentOptions));
        }

        boolean closeRequested = "CLOSE_REQUESTED".equalsIgnoreCase(safe(t.getStatus()));
        String closeRequestedByUsername =
                t.getCloseRequestedBy() == null ? "" : userNames.getOrDefault(t.getCloseRequestedBy(), "");
        Set<Long> closureReviewerIds = parseCloseReviewUserIds(t);
        boolean closureReviewUsesTaskCreators = closeRequested && !closureReviewerIds.isEmpty();
        String storedCloseReviewRole = safe(t.getCloseReviewRole());
        boolean closureSeniorPoolReview =
                closeRequested
                        && !closureReviewUsesTaskCreators
                        && CLOSURE_REVIEW_SENIOR_POOL.equalsIgnoreCase(RoleKeys.normalizeForScope(storedCloseReviewRole));
        String closeReviewRoleLabel =
                CLOSURE_REVIEW_SENIOR_POOL.equalsIgnoreCase(RoleKeys.normalizeForScope(storedCloseReviewRole))
                        ? messageSource.getMessage("ticketMgmt.closure.reviewRole.closureSenior", null, loc)
                        : storedCloseReviewRole;
        String closureReviewerNames =
                closureReviewerIds.isEmpty()
                        ? ""
                        : closureReviewerIds.stream()
                                .map(uid -> userNames.getOrDefault(uid, ""))
                                .filter(s -> s != null && !s.isBlank())
                                .distinct()
                                .collect(Collectors.joining(", "));
        boolean mayFinalizeClosure = viewerMayFinalizeClosure(t, actorUsername);
        String closureAiBriefingText = "";
        boolean closureAiBriefingLocalFallback = false;
        if (closeRequested) {
            try {
                TicketClosureAssistantService.ClosureBriefing cb =
                        ticketClosureAssistant.closureBriefingForTicket(t, loc);
                closureAiBriefingText = cb.text() == null ? "" : cb.text();
                closureAiBriefingLocalFallback = cb.localFallback();
            } catch (RuntimeException ex) {
                log.warn("closure AI briefing skipped: {}", ex.getMessage());
            }
            if (closureAiBriefingText == null) {
                closureAiBriefingText = "";
            }
        }
        Long mergedIntoId = t.getMergedIntoTicketId();
        String mergedIntoRef =
                mergedIntoId == null
                        ? ""
                        : tickets.findById(mergedIntoId).map(tx -> ticketNumber(tx)).orElse("");
        TicketSlaCalculator.SlaPresentation sla = TicketSlaCalculator.present(t);
        String slaCssClass = slaCssClass(sla.status());

        return new TicketDetail(
                t.getId(),
                ticketNumber(t),
                safe(t.getTitle()),
                safe(t.getDescription()),
                safe(t.getStatus()).toUpperCase(Locale.ROOT),
                safe(t.getPriority()).toUpperCase(Locale.ROOT),
                safe(t.getTicketType()).toUpperCase(Locale.ROOT),
                t.getDepartmentId(),
                assignedAgents,
                t.getCreatedBy() == null ? "" : userNames.getOrDefault(t.getCreatedBy(), ""),
                t.getUpdatedBy() == null ? "" : userNames.getOrDefault(t.getUpdatedBy(), ""),
                formatTs(t.getCreatedAt()),
                formatTs(t.getUpdatedAt()),
                assignedAgentsPluralList,
                showPluralAgentsRow,
                tasks,
                taskAgentOptions,
                taskSousDirections,
                defaultTaskSd,
                assigneesJson,
                sdPicker,
                linkedCouriers,
                comments,
                genealogy,
                closeRequested,
                closeRequestedByUsername,
                closeReviewRoleLabel,
                closureReviewUsesTaskCreators,
                closureReviewerNames,
                mayFinalizeClosure,
                closureSeniorPoolReview,
                closureAiBriefingText,
                closureAiBriefingLocalFallback,
                mergedIntoId,
                mergedIntoRef,
                sla.status().name(),
                sla.dueFormatted(),
                sla.resolvedFormatted(),
                slaCssClass);
    }

    /** Tickets the actor may keep when merging {@code absorbedTicketId} into a survivor (pick list). */
    @Transactional(readOnly = true)
    public MergeTicketPickPage mergeSurvivorChoices(Long absorbedTicketId, String actorUsername, boolean adminBypass) {
        return mergeSurvivorChoices(absorbedTicketId, actorUsername, adminBypass, false);
    }

    /**
     * @param creatorMergeBypass when {@code true}, the absorbed ticket creator may merge without being assignee /
     *     task worker (Mon activité). Not allowed while the absorbed ticket is still {@code OPEN}.
     */
    @Transactional(readOnly = true)
    public MergeTicketPickPage mergeSurvivorChoices(
            Long absorbedTicketId, String actorUsername, boolean adminBypass, boolean creatorMergeBypass) {
        Ticket absorbed = tickets.findById(absorbedTicketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        if (!adminBypass && !directionScopeService.canAccessTicket(actor, absorbed)) {
            throw new IllegalArgumentException("notFound");
        }
        if (!adminBypass) {
            if (creatorMergeBypass) {
                if (!actor.getId().equals(absorbed.getCreatedBy())) {
                    throw new IllegalArgumentException("notFound");
                }
                if ("OPEN".equalsIgnoreCase(safe(absorbed.getStatus()))) {
                    throw new IllegalStateException("notAllowed");
                }
            } else {
                ensureTicketWorker(absorbedTicketId, actorUsername);
            }
        }
        ensureTicketEligibleForMerge(absorbed);
        Instant nowMerge = Instant.now();
        if (PlannerTicketVisibility.isHiddenFromOperationalViews(absorbed, nowMerge)) {
            throw new IllegalArgumentException("notFound");
        }
        List<MergeSurvivorOption> survivors =
                tickets.findAll().stream()
                        .filter(x -> !x.getId().equals(absorbedTicketId))
                        .filter(x -> x.getMergedIntoTicketId() == null)
                        .filter(x -> !"MERGED".equalsIgnoreCase(safe(x.getStatus())))
                        .filter(x -> !PlannerTicketVisibility.isHiddenFromOperationalViews(x, nowMerge))
                        .filter(x -> directionScopeService.canAccessTicket(actor, x))
                        .sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(Ticket::getId, Comparator.reverseOrder()))
                        .map(x -> new MergeSurvivorOption(x.getId(), ticketNumber(x), safe(x.getTitle())))
                        .toList();
        return new MergeTicketPickPage(absorbedTicketId, ticketNumber(absorbed), safe(absorbed.getTitle()), survivors);
    }

    /**
     * Absorbs {@code absorbedTicketId} into {@code survivorTicketId}: survivor receives consolidated data and a new
     * {@code MEG-TCK-YYYY-#####} reference; the absorbed row is marked {@code MERGED}.
     */
    @Transactional
    public Long mergeTicketsAbsorbIntoSurvivor(
            Long absorbedTicketId, Long survivorTicketId, String actorUsername, boolean adminBypass) {
        return mergeTicketsAbsorbIntoSurvivor(
                absorbedTicketId, survivorTicketId, actorUsername, adminBypass, false);
    }

    @Transactional
    public Long mergeTicketsAbsorbIntoSurvivor(
            Long absorbedTicketId,
            Long survivorTicketId,
            String actorUsername,
            boolean adminBypass,
            boolean creatorMergeBypass) {
        if (absorbedTicketId.equals(survivorTicketId)) {
            throw new IllegalArgumentException("sameTicket");
        }
        Ticket absorbed = tickets.findById(absorbedTicketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        Ticket survivor = tickets.findById(survivorTicketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        if (!adminBypass) {
            if (creatorMergeBypass) {
                if (!directionScopeService.canAccessTicket(actor, absorbed)) {
                    throw new IllegalArgumentException("notFound");
                }
                if (!actor.getId().equals(absorbed.getCreatedBy())) {
                    throw new IllegalArgumentException("notFound");
                }
                if ("OPEN".equalsIgnoreCase(safe(absorbed.getStatus()))) {
                    throw new IllegalStateException("notAllowed");
                }
            } else {
                ensureTicketWorker(absorbedTicketId, actorUsername);
            }
        }
        if (!directionScopeService.canAccessTicket(actor, absorbed)
                || !directionScopeService.canAccessTicket(actor, survivor)) {
            throw new IllegalArgumentException("notFound");
        }
        Instant nowMerge = Instant.now();
        if (PlannerTicketVisibility.isHiddenFromOperationalViews(absorbed, nowMerge)
                || PlannerTicketVisibility.isHiddenFromOperationalViews(survivor, nowMerge)) {
            throw new IllegalArgumentException("notFound");
        }
        ensureTicketEligibleForMerge(absorbed);
        ensureTicketEligibleForMerge(survivor);

        String absorbedRef = ticketNumber(absorbed);
        String survivorOldRef = ticketNumber(survivor);
        String megRef = nextMegTicketReference();

        String mergedBlock =
                "\n\n--- Fusionné depuis "
                        + absorbedRef
                        + " ---\n"
                        + (safe(absorbed.getDescription()).isBlank() ? "" : safe(absorbed.getDescription()));
        survivor.setDescription(
                safe(survivor.getDescription()).isBlank() ? mergedBlock.trim() : safe(survivor.getDescription()) + mergedBlock);

        List<TicketComment> absorbedComments = ticketComments.findByTicketIdOrderByCreatedAtDesc(absorbed.getId());
        for (TicketComment c : absorbedComments) {
            c.setTicketId(survivor.getId());
            ticketComments.save(c);
        }
        List<AutomatedJob> absorbedJobs = jobs.findByTicketId(absorbed.getId());
        for (AutomatedJob j : absorbedJobs) {
            j.setTicketId(survivor.getId());
            jobs.save(j);
        }
        for (TicketEvent ev : ticketEvents.findByTicketIdOrderByCreatedAtAsc(absorbed.getId())) {
            TicketEvent copy = new TicketEvent();
            copy.setTicketId(survivor.getId());
            copy.setEventType(ev.getEventType());
            copy.setActorUserId(ev.getActorUserId());
            copy.setTargetUserId(ev.getTargetUserId());
            copy.setNote(ev.getNote());
            copy.setCreatedAt(ev.getCreatedAt());
            ticketEvents.save(copy);
        }
        courierPackets.relinkPacketsFromAbsorbedTicket(absorbed.getId(), survivor.getId());

        LinkedHashSet<Long> survivorUserIds = new LinkedHashSet<>();
        if (survivor.getAssignedTo() != null) {
            survivorUserIds.add(survivor.getAssignedTo());
        }
        ticketAssignments.findByTicketId(survivor.getId()).stream()
                .map(TicketAssignment::getUserId)
                .filter(Objects::nonNull)
                .forEach(survivorUserIds::add);

        List<Long> absorbedUserIds =
                ticketAssignments.findByTicketId(absorbed.getId()).stream()
                        .map(TicketAssignment::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        ticketAssignments.deleteByTicketId(absorbed.getId());
        for (Long uid : absorbedUserIds) {
            if (survivorUserIds.add(uid)) {
                TicketAssignment na = new TicketAssignment();
                na.setTicketId(survivor.getId());
                na.setUserId(uid);
                na.setAssignedAt(Instant.now());
                ticketAssignments.save(na);
            }
        }

        survivor.setTicketNumber(megRef);
        clearClosureRequestFields(absorbed);
        absorbed.setStatus("MERGED");
        absorbed.setMergedIntoTicketId(survivor.getId());
        absorbed.setAssignedTo(null);
        absorbed.setUpdatedAt(Instant.now());
        survivor.setUpdatedAt(Instant.now());
        applyAuditFields(absorbed, actorUsername);
        applyAuditFields(survivor, actorUsername);
        tickets.save(absorbed);
        tickets.save(survivor);

        ticketTimeline.log(
                "MERGED",
                survivor.getId(),
                actorUsername,
                null,
                "Fusion du ticket "
                        + absorbedRef
                        + " dans "
                        + megRef
                        + " (survivant \u00e9tait "
                        + survivorOldRef
                        + ")");
        return survivor.getId();
    }

    private static void ensureTicketEligibleForMerge(Ticket t) {
        if (t.getMergedIntoTicketId() != null) {
            throw new IllegalStateException("notMergeable");
        }
        String s = safe(t.getStatus()).toUpperCase(Locale.ROOT);
        if ("MERGED".equals(s)) {
            throw new IllegalStateException("notMergeable");
        }
    }

    private String nextMegTicketReference() {
        int year = Year.now().getValue();
        String prefix = "MEG-TCK-" + year + "-";
        int max = 0;
        for (Ticket tx : tickets.findAll()) {
            String n = tx.getTicketNumber();
            if (n == null || !n.startsWith(prefix)) {
                continue;
            }
            String suffix = n.substring(prefix.length()).trim();
            try {
                max = Math.max(max, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // ignore malformed refs
            }
        }
        return prefix + String.format("%05d", max + 1);
    }

    @Transactional
    public void close(Long id, String actorUsername) {
        Ticket t = scopedTicket(id, actorUsername);
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        assertDirectCloseAllowed(t, actor);
        long openTasks =
                jobs.findByTicketId(t.getId()).stream()
                        .filter(AutomatedJobProcessingService::isActiveOpenTask)
                        .count();
        if (openTasks > 0) {
            throw new IllegalStateException("tasksPending");
        }
        clearClosureRequestFields(t);
        t.setStatus("CLOSED");
        if (t.getStartedAt() == null) {
            t.setStartedAt(Instant.now());
        }
        t.setClosedAt(Instant.now());
        t.setClosedBy(actor.getId());
        applyAuditFields(t, actorUsername);
        tickets.save(t);
        ticketTimeline.log("CLOSED", id, actorUsername, null, "Ticket cl\u00f4tur\u00e9");
        notifyTicketClosedEmailIfApplicable(t, actor);
        notifyDirectionAndEscalatorOnClose(t, actorUsername);
    }

    private void notifyTicketClosedEmailIfApplicable(Ticket t, UserAccount closer) {
        if (t == null || closer == null) {
            return;
        }
        LinkedHashSet<String> emailed = new LinkedHashSet<>();
        if (t.getCreatedBy() != null) {
            users.findById(t.getCreatedBy())
                    .ifPresent(
                            creator -> {
                                String em = creator.getEmail();
                                if (em != null && !em.isBlank()) {
                                    ticketEmailNotificationService.sendTicketClosedToRequester(creator, t, closer);
                                    emailed.add(em.trim().toLowerCase(Locale.ROOT));
                                }
                            });
        }
        for (UserAccount u : users.findAll()) {
            if (!u.isActiveBool()) {
                continue;
            }
            String em = u.getEmail();
            if (em == null || em.isBlank()) {
                continue;
            }
            String norm = em.trim().toLowerCase(Locale.ROOT);
            if (emailed.contains(norm)) {
                continue;
            }
            if (closer.getId() != null && closer.getId().equals(u.getId())) {
                continue;
            }
            if (!directionScopeService.canAccessTicket(u, t)) {
                continue;
            }
            if (!TicketRoleRanks.strictlyMoreSeniorThanCloser(u, closer)) {
                continue;
            }
            ticketEmailNotificationService.sendTicketClosedSupervisorMayReopen(u, t, closer);
            emailed.add(norm);
        }
    }

    /**
     * Whether the viewer may reopen this ticket (must be {@code CLOSED}, same ticket-access scope, and strictly senior
     * to the closing officer unless {@code ADMIN}/{@code SUPER_ADMIN}).
     */
    @Transactional(readOnly = true)
    public boolean mayReopenTicket(Long id, String actorUsername) {
        Ticket t;
        try {
            t = scopedTicket(id, actorUsername);
        } catch (RuntimeException e) {
            return false;
        }
        if (!"CLOSED".equalsIgnoreCase(safe(t.getStatus()))) {
            return false;
        }
        if (t.getMergedIntoTicketId() != null || "MERGED".equalsIgnoreCase(safe(t.getStatus()))) {
            return false;
        }
        UserAccount viewer = users.findByUsernameIgnoreCase(actorUsername).orElse(null);
        if (viewer == null || !viewer.isActiveBool()) {
            return false;
        }
        UserAccount closer = resolveCloserAccount(t);
        return TicketRoleRanks.mayReopenRelativeToCloser(viewer, closer);
    }

    @Transactional
    public void reopen(Long id, String actorUsername) {
        Ticket t = scopedTicket(id, actorUsername);
        if (t.getMergedIntoTicketId() != null || "MERGED".equalsIgnoreCase(safe(t.getStatus()))) {
            throw new IllegalStateException("reopenMerged");
        }
        if (!"CLOSED".equalsIgnoreCase(safe(t.getStatus()))) {
            throw new IllegalStateException("notClosed");
        }
        UserAccount viewer = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        UserAccount closer = resolveCloserAccount(t);
        if (!TicketRoleRanks.mayReopenRelativeToCloser(viewer, closer)) {
            throw new IllegalStateException("reopenNotAllowed");
        }
        t.setStatus("OPEN");
        t.setClosedAt(null);
        t.setClosedBy(null);
        applyAuditFields(t, actorUsername);
        tickets.save(t);
        String note =
                closer != null
                        ? ("Rouvert — cl\u00f4ture avait \u00e9t\u00e9 effectu\u00e9e par " + closer.getUsername())
                        : "Ticket rouvert";
        ticketTimeline.log("REOPENED", id, actorUsername, closer != null ? closer.getId() : null, note);
    }

    private UserAccount resolveCloserAccount(Ticket t) {
        if (t == null) {
            return null;
        }
        if (t.getClosedBy() != null) {
            return users.findById(t.getClosedBy()).orElse(null);
        }
        return ticketEvents
                .findTopByTicketIdAndEventTypeOrderByCreatedAtDesc(t.getId(), "CLOSED")
                .map(TicketEvent::getActorUserId)
                .flatMap(users::findById)
                .orElse(null);
    }

    /**
     * Worker requested closure (e.g. from Mon travail): pending review with optional escalation to next role tier.
     */
    @Transactional
    public void requestTicketClosure(Long id, String actorUsername) {
        requestTicketClosure(id, actorUsername, null);
    }

    /**
     * @param preferredSeniorReviewerUserId when set on the multi-assign collaboration path, only this cadre is notified
     *     and may finalize (must be inspecteur / sous-directeur / directeur in scope).
     */
    @Transactional
    public void requestTicketClosure(Long id, String actorUsername, Long preferredSeniorReviewerUserId) {
        Ticket t = scopedTicket(id, actorUsername);
        ensureTicketWorker(t.getId(), actorUsername);
        if ("CLOSED".equalsIgnoreCase(t.getStatus())) {
            throw new IllegalStateException("alreadyClosed");
        }
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        List<AutomatedJob> ticketJobs = jobs.findByTicketId(t.getId());
        long openTasks =
                ticketJobs.stream().filter(AutomatedJobProcessingService::isActiveOpenTask).count();
        if (openTasks > 0) {
            throw new IllegalStateException("tasksPending");
        }

        Set<Long> creators = distinctTaskCreatorIds(t.getId());
        boolean hasJobs = !ticketJobs.isEmpty();
        Long actorId = actor.getId();

        if (distinctCollaborativeAssigneeCount(t) >= 2) {
            clearClosureRequestFields(t);
            t.setStatus("CLOSE_REQUESTED");
            t.setCloseRequestedAt(Instant.now());
            t.setCloseRequestedBy(actorId);
            Optional<Long> primaryOpt = primaryAssigneeUserId(t);
            boolean actorIsPrimary = primaryOpt.map(pid -> pid.equals(actorId)).orElse(true);
            String actorRoleNorm = RoleKeys.normalizeForScope(actor.getRole());
            boolean actorIsSenior = HIGH_SELF_APPROVE_ROLES.contains(actorRoleNorm);
            if (actorIsPrimary && actorIsSenior) {
                t.setCloseReviewRole(null);
                t.setCloseReviewUserIds(null);
            } else if (preferredSeniorReviewerUserId != null) {
                UserAccount delegate =
                        users.findById(preferredSeniorReviewerUserId).orElseThrow(() -> new IllegalArgumentException("badReviewer"));
                assertEligibleSeniorClosureDelegate(t, actor, delegate);
                t.setCloseReviewRole(null);
                t.setCloseReviewUserIds(serializeCloseReviewUserIds(Set.of(delegate.getId())));
                UserAccount assigneeUser = primaryOpt.flatMap(users::findById).orElse(actor);
                notifyClosureReviewers(t, assigneeUser, actorUsername, null, Set.of(delegate.getId()));
            } else {
                t.setCloseReviewRole(CLOSURE_REVIEW_SENIOR_POOL);
                t.setCloseReviewUserIds(null);
                notifySeniorClosureReviewers(t, actorUsername);
            }
            applyAuditFields(t, actorUsername);
            tickets.save(t);
            ticketTimeline.log(
                    "UPDATED",
                    id,
                    actorUsername,
                    primaryOpt.orElse(null),
                    "Demande de cloture (collaboration multi-agents)");
            return;
        }

        clearClosureRequestFields(t);
        t.setStatus("CLOSE_REQUESTED");
        t.setCloseRequestedAt(Instant.now());
        t.setCloseRequestedBy(actorId);

        if (hasJobs && creators.isEmpty()) {
            Optional<Long> assigneeOpt = primaryAssigneeUserId(t);
            UserAccount assigneeUser = assigneeOpt.flatMap(users::findById).orElse(actor);
            String assigneeRole = RoleKeys.normalizeForScope(assigneeUser.getRole());
            String reviewRoleToStore;
            if (HIGH_SELF_APPROVE_ROLES.contains(assigneeRole)) {
                reviewRoleToStore = null;
            } else {
                reviewRoleToStore = nextReviewerRoleForLowAssignee(assigneeRole).orElse("INSPECTEUR");
            }
            t.setCloseReviewRole(reviewRoleToStore);
            applyAuditFields(t, actorUsername);
            tickets.save(t);
            ticketTimeline.log(
                    "UPDATED",
                    id,
                    actorUsername,
                    assigneeOpt.orElse(null),
                    "Demande de cl\u00f4ture (revue en attente)");
            notifyClosureReviewers(t, assigneeUser, actorUsername, reviewRoleToStore, null);
            return;
        }

        Set<Long> reviewers =
                creators.stream()
                        .filter(uid -> !uid.equals(actorId))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (reviewers.isEmpty()) {
            throw new IllegalStateException("noReviewersNeeded");
        }
        t.setCloseReviewUserIds(serializeCloseReviewUserIds(reviewers));
        t.setCloseReviewRole(null);
        applyAuditFields(t, actorUsername);
        tickets.save(t);
        ticketTimeline.log(
                "UPDATED", id, actorUsername, null, "Demande de cl\u00f4ture (revue cr\u00e9ateur de t\u00e2che)");
        Optional<Long> assigneeOpt = primaryAssigneeUserId(t);
        UserAccount assigneeUser = assigneeOpt.flatMap(users::findById).orElse(actor);
        notifyClosureReviewers(t, assigneeUser, actorUsername, null, reviewers);
    }

    /** Finalize after guided review (ticket must be {@code CLOSE_REQUESTED}). */
    @Transactional
    public void finalizeClosureAfterTour(Long id, String actorUsername, boolean tourAcknowledged) {
        Ticket t = scopedTicket(id, actorUsername);
        if (!"CLOSE_REQUESTED".equalsIgnoreCase(safe(t.getStatus()))) {
            throw new IllegalStateException("notCloseRequested");
        }
        if (!tourAcknowledged) {
            throw new IllegalArgumentException("tourRequired");
        }
        if (!viewerMayFinalizeClosure(t, actorUsername)) {
            throw new IllegalStateException("notAllowed");
        }
        long openTasks =
                jobs.findByTicketId(t.getId()).stream()
                        .filter(AutomatedJobProcessingService::isActiveOpenTask)
                        .count();
        if (openTasks > 0) {
            throw new IllegalStateException("tasksPending");
        }
        UserAccount actor =
                users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        clearClosureRequestFields(t);
        t.setStatus("CLOSED");
        if (t.getStartedAt() == null) {
            t.setStartedAt(Instant.now());
        }
        t.setClosedAt(Instant.now());
        t.setClosedBy(actor.getId());
        applyAuditFields(t, actorUsername);
        tickets.save(t);
        ticketTimeline.log(
                "CLOSED", id, actorUsername, null, "Ticket cl\u00f4tur\u00e9 apr\u00e8s revue de cl\u00f4ture");
        notifyTicketClosedEmailIfApplicable(t, actor);
        notifyDirectionAndEscalatorOnClose(t, actorUsername);
    }

    public boolean viewerMayFinalizeClosure(Ticket t, String actorUsername) {
        if (t == null || !"CLOSE_REQUESTED".equalsIgnoreCase(safe(t.getStatus()))) {
            return false;
        }
        UserAccount viewer = users.findByUsernameIgnoreCase(actorUsername).orElse(null);
        if (viewer == null || !viewer.isActiveBool()) {
            return false;
        }
        if (!directionScopeService.canAccessTicket(viewer, t)) {
            return false;
        }
        Optional<Long> primaryId = primaryAssigneeUserId(t);
        UserAccount assigneeAccount = primaryId.flatMap(users::findById).orElse(null);
        Long anchorDirectionId = assigneeAccount != null ? assigneeAccount.getDirectionId() : null;
        if (anchorDirectionId == null && t.getCreatedBy() != null) {
            anchorDirectionId = users.findById(t.getCreatedBy()).map(UserAccount::getDirectionId).orElse(null);
        }
        String viewerRole = RoleKeys.normalizeForScope(viewer.getRole());
        Long viewerDirectionId = viewer.getDirectionId();
        if (!directionScopeService.isSuperAdmin(viewer)) {
            if (anchorDirectionId != null && viewerDirectionId != null && !anchorDirectionId.equals(viewerDirectionId)) {
                return false;
            }
        }
        if ("SUPER_ADMIN".equals(viewerRole) || "ADMIN".equals(viewerRole)) {
            return true;
        }
        Set<Long> closureReviewerUserIds = parseCloseReviewUserIds(t);
        if (!closureReviewerUserIds.isEmpty()) {
            return closureReviewerUserIds.contains(viewer.getId());
        }
        String rr = t.getCloseReviewRole();
        if (rr == null || rr.isBlank()) {
            if (primaryId.isEmpty()) {
                return false;
            }
            return primaryId.filter(pid -> pid.equals(viewer.getId())).isPresent()
                    && HIGH_SELF_APPROVE_ROLES.contains(viewerRole);
        }
        String rrNorm = RoleKeys.normalizeForScope(rr);
        if (CLOSURE_REVIEW_SENIOR_POOL.equals(rrNorm)) {
            return isSeniorClosurePoolReviewerRole(viewerRole);
        }
        return rrNorm.equals(viewerRole);
    }

    @Transactional
    public void delete(Long id, String actorUsername) {
        if (id == null) {
            throw new IllegalArgumentException("notFound");
        }
        scopedTicket(id, actorUsername);
        courierPackets.unlinkCourierPacketsFromTicket(id);
        tickets.deleteById(id);
    }

    @Transactional
    public void update(Long id, String title, String priority, String status, String actorUsername) {
        update(id, title, priority, status, actorUsername, false);
    }

    /**
     * When {@code creatorOnly}, only the user who created the ticket may update (used from Mon activité /
     * MY_ACTIVITY users without ticket-management write).
     */
    @Transactional
    public void update(Long id, String title, String priority, String status, String actorUsername, boolean creatorOnly) {
        Ticket t = scopedTicket(id, actorUsername);
        if (creatorOnly) {
            ensureActorCreatedTicket(t, actorUsername);
        }
        if (title != null && !title.isBlank()) {
            t.setTitle(title.trim());
        }
        if (priority != null && !priority.isBlank()) {
            t.setPriority(priority.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            String next = status.trim().toUpperCase(Locale.ROOT);
            String prev = safe(t.getStatus()).toUpperCase(Locale.ROOT);
            if ("CLOSE_REQUESTED".equals(prev) && !"CLOSE_REQUESTED".equals(next)) {
                clearClosureRequestFields(t);
            }
            t.setStatus(next);
        }
        applyAuditFields(t, actorUsername);
        tickets.save(t);
        ticketTimeline.log("UPDATED", id, actorUsername, firstAssignedUserId(t.getId()), "Ticket mis \u00e0 jour");
    }

    /**
     * Ensures the actor may load this ticket as its creator when using MY_ACTIVITY-only flows.
     */
    public void requireTicketCreator(Long id, String actorUsername) {
        Ticket t = scopedTicket(id, actorUsername);
        ensureActorCreatedTicket(t, actorUsername);
    }

    @Transactional
    public void addComment(Long id, String comment, List<MultipartFile> attachments, String actorUsername, String uploadsDirectory,
            boolean creatorOnly)
            throws IOException {
        Ticket t = scopedTicket(id, actorUsername);
        if (creatorOnly) {
            ensureActorCreatedTicket(t, actorUsername);
        }
        String text = comment == null ? "" : comment.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("commentRequired");
        }
        String attachmentPath = "";
        if (attachments != null && !attachments.isEmpty()) {
            Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("ticket-comments").resolve(String.valueOf(id));
            Files.createDirectories(dir);
            for (MultipartFile attachment : attachments) {
                if (attachment == null || attachment.isEmpty()) {
                    continue;
                }
                validateCommentAttachment(attachment);
                String safe = stripPath(attachment.getOriginalFilename() == null ? "attachment.bin" : attachment.getOriginalFilename());
                Path dest = dir.resolve(System.currentTimeMillis() + "_" + safe);
                Files.copy(attachment.getInputStream(), dest);
                attachmentPath = attachmentPath.isBlank() ? dest.toString() : (attachmentPath + ";;" + dest);
            }
        }
        TicketComment c = new TicketComment();
        c.setTicketId(t.getId());
        c.setAuthorUserId(resolveActorId(actorUsername));
        c.setCommentText(text);
        c.setAttachmentPath(attachmentPath);
        c.setCreatedAt(Instant.now());
        ticketComments.save(c);
        ticketTimeline.log("COMMENTED", id, actorUsername, null, text);
    }

    @Transactional
    public void createTaskFromTicket(Long id, String actorUsername) {
        Ticket t = scopedTicket(id, actorUsername);
        List<Long> assigneeIds = ticketAssignments.findByTicketId(t.getId()).stream()
                .map(TicketAssignment::getUserId)
                .distinct()
                .toList();
        if (assigneeIds.isEmpty()) {
            assigneeIds = t.getAssignedTo() == null ? List.of() : List.of(t.getAssignedTo());
        }
        for (Long assigneeId : assigneeIds) {
            AutomatedJob j = new AutomatedJob();
            j.setTicketId(t.getId());
            j.setJobTitle("Task from " + ticketNumber(t));
            j.setJobDescription((safe(t.getTitle()) + " - " + safe(t.getDescription())).trim());
            j.setDueAt(LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            j.setAssigneeUserId(assigneeId);
            j.setCreatedBy(resolveActorId(actorUsername));
            j.setRecurrence("ONCE");
            j.setActive(1);
            j.setStatus("OPEN");
            j.setCreatedAt(Instant.now());
            jobs.save(j);
            ticketTimeline.log("TASK_CREATED", id, actorUsername, assigneeId, "T\u00e2che cr\u00e9\u00e9e depuis le ticket");
        }
    }

    /**
     * Creates one identical task per assignee (same title, dates, attachments reference).
     *
     * @return number of tasks persisted
     */
    @Transactional
    public int createTask(
            Long id,
            List<Long> assigneeIds,
            Long taskSousDirectionId,
            String title,
            String description,
            String dueAt,
            String priority,
            Integer reminderMinutes,
            List<MultipartFile> attachments,
            String actorUsername) {
        Ticket t = scopedTicket(id, actorUsername);
        List<Long> ids =
                assigneeIds == null
                        ? List.of()
                        : assigneeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("badAssignee");
        }
        UserAccount actor = directionScopeService.requireUser(actorUsername);
        for (Long assigneeId : ids) {
            validateTaskAssignee(assigneeId, taskSousDirectionId, actor, t);
        }
        String jobTitle = (title == null || title.isBlank()) ? ("Task " + ticketNumber(t)) : title.trim();
        String jobDescription = description == null ? "" : description.trim();
        String normalizedDue = (dueAt == null || dueAt.isBlank())
                ? LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : dueAt.trim().replace('T', ' ');
        String prio = normalizePriority(priority);
        int reminder = reminderMinutes == null ? 30 : reminderMinutes;
        Long createdBy = resolveActorId(actorUsername);
        String attachmentPaths = storeTaskAttachments(id, attachments);
        Instant now = Instant.now();
        int created = 0;
        for (Long assigneeId : ids) {
            AutomatedJob j = new AutomatedJob();
            j.setTicketId(t.getId());
            j.setJobTitle(jobTitle);
            j.setJobDescription(jobDescription);
            j.setDueAt(normalizedDue);
            j.setAssigneeUserId(assigneeId);
            j.setPriority(prio);
            j.setReminderMinutes(reminder);
            j.setCreatedBy(createdBy);
            j.setRecurrence("ONCE");
            j.setActive(1);
            j.setStatus("OPEN");
            j.setCreatedAt(now);
            j.setAttachmentPaths(attachmentPaths);
            jobs.save(j);
            created++;
            ticketTimeline.log(
                    "TASK_CREATED", id, actorUsername, assigneeId, "T\u00e2che cr\u00e9\u00e9e depuis le d\u00e9tail du ticket");
            notificationService.notifyNewTaskOnTicket(assigneeId, ticketNumber(t), jobTitle, t.getId());
        }
        return created;
    }

    @Transactional
    public TicketPdfExport exportPdf(Long id, String actorUsername) throws IOException {
        TicketDetail detail = detail(id, actorUsername);
        byte[] pdfBytes;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            TicketPdfLayout layout = new TicketPdfLayout(doc, detail, buildBarcodePayload(detail));
            layout.startPage(true);
            layout.writeAll();
            layout.finishPage();
            doc.save(out);
            pdfBytes = out.toByteArray();
        }
        String ref = detail.ticketNumber();
        ticketTimeline.log("PDF_EXPORTED", id, actorUsername, null, ref, false);
        String attachmentFilename = pdfAttachmentFilename(ref, detail.id());
        return new TicketPdfExport(pdfBytes, attachmentFilename);
    }

    /** PDF download + barcode payload use the same canonical reference as the ticket row ({@code TCK-YYYY-#####}). */
    public record TicketPdfExport(byte[] pdfBytes, String attachmentFilename) {}

    private String departmentLabel(Long departmentId) {
        if (departmentId == null) {
            return "-";
        }
        return departments.findById(departmentId).map(Department::getName).orElse("ID " + departmentId);
    }

    private static String statusPdfLabel(String status) {
        if (status == null || status.isBlank()) {
            return "-";
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "CLOSE_REQUESTED" -> "CLOTURE DEMANDEE";
            case "IN_PROGRESS" -> "EN COURS";
            default -> status.trim().toUpperCase(Locale.ROOT);
        };
    }

    private static String slaExportLine(TicketDetail d) {
        String key = d.slaStatus() == null ? "" : d.slaStatus().trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "NA" -> "Non applicable";
            case "OK" -> "Dans les delais — echeance: " + blankDash(d.slaDueFormatted());
            case "WARNING" -> "Echeance proche — avant: " + blankDash(d.slaDueFormatted());
            case "BREACH" -> "Depasse — etait attendu avant: " + blankDash(d.slaDueFormatted());
            case "CLOSED_OK" -> "Respecte a la cloture — cloture: "
                    + blankDash(d.slaResolvedFormatted())
                    + " (echeance: "
                    + blankDash(d.slaDueFormatted())
                    + ")";
            case "CLOSED_BREACH" -> "Depasse a la cloture — cloture: " + blankDash(d.slaResolvedFormatted());
            default -> blankDash(d.slaStatus()) + " — " + blankDash(d.slaDueFormatted());
        };
    }

    private static String closureExportSummary(TicketDetail d) {
        StringBuilder sb = new StringBuilder();
        sb.append("Demande de cloture en cours. Demandeur: ")
                .append(blankDash(d.closeRequestedByUsername()))
                .append("\n");
        if (d.closureReviewUsesTaskCreators()) {
            sb.append("Revue attendue aupres des createurs de taches: ")
                    .append(blankDash(d.closureReviewerNames()));
        } else if (d.closureSeniorPoolReview()) {
            sb.append("Revue attendue aupres d'un cadre (inspecteur / sous-directeur / directeur).");
        } else if (d.closeReviewRoleLabel() != null && !d.closeReviewRoleLabel().isBlank()) {
            sb.append("Revue attendue (role): ").append(d.closeReviewRoleLabel().trim());
        } else {
            sb.append("Revue attendue aupres de l'assigne.");
        }
        if (d.viewerMayFinalizeClosure()) {
            sb.append("\n[L'utilisateur exportateur est habilite a finaliser la cloture.]");
        }
        return sb.toString();
    }

    /**
     * Multi-page ticket PDF: structured sections matching the ticket detail screen (header, infos, closure,
     * description, tasks, comments, linked couriers, genealogy).
     */
    private final class TicketPdfLayout {

        private static final float MARGIN_X = 48f;
        private static final float MARGIN_BOTTOM = 52f;
        private static final float FOOTER_RESERVE = 112f;

        private final PDDocument doc;
        private final TicketDetail d;
        private final String barcodePayload;
        private PDPage page;
        private PDPageContentStream cs;
        private PDImageXObject barcodeImage;
        private float y;
        private int pageNum;

        private TicketPdfLayout(PDDocument doc, TicketDetail detail, String barcodePayload) {
            this.doc = doc;
            this.d = detail;
            this.barcodePayload = barcodePayload;
        }

        private void startPage(boolean first) throws IOException {
            if (cs != null) {
                throw new IllegalStateException("page already open");
            }
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            pageNum++;
            float ph = page.getMediaBox().getHeight();
            float pw = page.getMediaBox().getWidth();
            drawWatermark(pw, ph);
            if (first) {
                cs.setNonStrokingColor(0.07f, 0.38f, 0.58f);
                cs.addRect(0, ph - 56, pw, 56);
                cs.fill();
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 15);
                cs.newLineAtOffset(MARGIN_X, ph - 34);
                cs.showText(pdfText("TICKET SYSCO"));
                cs.endText();
                cs.setNonStrokingColor(0.86f, 0.95f, 0.98f);
                cs.addRect(0, ph - 56 - 26, pw, 26);
                cs.fill();
                cs.setNonStrokingColor(0.05f, 0.34f, 0.48f);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9);
                cs.newLineAtOffset(MARGIN_X, ph - 68);
                cs.showText(
                        pdfText("Fiche complete generee automatiquement - toutes les informations affichees a l'ecran"));
                cs.endText();
                cs.setNonStrokingColor(0f, 0f, 0f);
                y = ph - 56 - 26 - 28;
            } else {
                cs.setNonStrokingColor(0.91f, 0.93f, 0.96f);
                cs.addRect(0, ph - 32, pw, 32);
                cs.fill();
                cs.setNonStrokingColor(0.15f, 0.22f, 0.35f);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.newLineAtOffset(MARGIN_X, ph - 20);
                cs.showText(pdfText("SYSCO — " + d.ticketNumber() + " (suite)"));
                cs.endText();
                cs.setNonStrokingColor(0f, 0f, 0f);
                y = ph - 32 - 18;
            }
        }

        private void finishPage() throws IOException {
            if (cs == null) {
                return;
            }
            drawBarcodeFooter();
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 8);
            cs.setNonStrokingColor(0.42f, 0.45f, 0.5f);
            cs.newLineAtOffset(MARGIN_X, MARGIN_BOTTOM - 18);
            cs.showText(pdfText("Page " + pageNum + "  |  " + d.ticketNumber() + "  |  SYSCO"));
            cs.endText();
            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.close();
            cs = null;
        }

        private void newPageContinuation() throws IOException {
            finishPage();
            startPage(false);
        }

        private void drawWatermark(float pageWidth, float pageHeight) throws IOException {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 56);
            cs.setNonStrokingColor(0.92f, 0.95f, 0.98f);
            cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), pageWidth * 0.17f, pageHeight * 0.28f));
            cs.showText(pdfText("SYSCO CONFIDENTIEL"));
            cs.endText();
            cs.setNonStrokingColor(0f, 0f, 0f);
        }

        private void drawBarcodeFooter() throws IOException {
            if (barcodeImage == null) {
                barcodeImage = LosslessFactory.createFromImage(doc, generateBarcodeImage(barcodePayload, 340, 56));
            }
            float pageWidth = page.getMediaBox().getWidth();
            float barcodeWidth = 340f;
            float barcodeHeight = 76f;
            float x = (pageWidth - barcodeWidth) / 2f;
            float yFooter = MARGIN_BOTTOM - 6f;

            cs.setNonStrokingColor(0.9f, 0.95f, 0.99f);
            cs.addRect(x - 10, yFooter - 16, barcodeWidth + 20, barcodeHeight + 34);
            cs.fill();
            cs.drawImage(barcodeImage, x, yFooter, barcodeWidth, barcodeHeight);
            cs.setNonStrokingColor(0.08f, 0.33f, 0.49f);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
            cs.newLineAtOffset(x, yFooter + barcodeHeight + 6);
            cs.showText(pdfText("Scan barcode to read core ticket content"));
            cs.endText();
            cs.setNonStrokingColor(0f, 0f, 0f);
        }

        private void ensureSpace(float needBelowCurrentY) throws IOException {
            if (y - needBelowCurrentY < MARGIN_BOTTOM + FOOTER_RESERVE) {
                newPageContinuation();
            }
        }

        private void rule() throws IOException {
            ensureSpace(8);
            float pw = page.getMediaBox().getWidth();
            cs.setStrokingColor(0.78f, 0.82f, 0.88f);
            cs.setLineWidth(0.75f);
            cs.moveTo(MARGIN_X, y + 4);
            cs.lineTo(pw - MARGIN_X, y + 4);
            cs.stroke();
            cs.setStrokingColor(0f, 0f, 0f);
            y -= 10;
        }

        private void section(String title) throws IOException {
            ensureSpace(36);
            y -= 6;
            rule();
            ensureSpace(22);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
            writeLine(cs, MARGIN_X, y, pdfText(title));
            y -= 16;
            cs.setFont(PDType1Font.HELVETICA, 10);
        }

        private void paragraph(String text, int cols) throws IOException {
            String body = text == null || text.isBlank() ? "-" : text;
            for (String line : wrap(pdfText(body), cols)) {
                ensureSpace(13);
                writeLine(cs, MARGIN_X, y, line);
                y -= 12;
            }
        }

        private void keyValue(String label, String value) throws IOException {
            String v = value == null || value.isBlank() ? "-" : value;
            String full = pdfText(label + ": " + v);
            cs.setFont(PDType1Font.HELVETICA, 10);
            for (String line : wrap(full, 86)) {
                ensureSpace(13);
                writeLine(cs, MARGIN_X, y, line);
                y -= 12;
            }
        }

        private void writeAll() throws IOException {
            ensureSpace(96);
            float pageWidth = page.getMediaBox().getWidth();
            cs.setNonStrokingColor(0.9f, 0.96f, 0.99f);
            cs.addRect(MARGIN_X - 6, y - 42, pageWidth - (2 * MARGIN_X) + 12, 56);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
            writeLine(cs, MARGIN_X, y, pdfText("STATUT: " + statusPdfLabel(d.status())));
            y -= 14;
            cs.setFont(PDType1Font.HELVETICA_BOLD, 17);
            writeLine(cs, MARGIN_X, y, pdfText("REF: " + d.ticketNumber()));
            y -= 20;
            cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
            for (String line : wrap(pdfText(d.title()), 68)) {
                ensureSpace(17);
                writeLine(cs, MARGIN_X, y, line);
                y -= 15;
            }
            cs.setFont(PDType1Font.HELVETICA, 10);
            y -= 4;
            ensureSpace(14);
            writeLine(
                    cs,
                    MARGIN_X,
                    y,
                    pdfText(
                            "Cree par: "
                                    + blankDash(d.createdBy())
                                    + "   |   Derniere maj par: "
                                    + blankDash(d.updatedBy())));
            y -= 16;

            if (d.mergedIntoTicketId() != null) {
                ensureSpace(28);
                cs.setNonStrokingColor(0.97f, 0.92f, 0.88f);
                float pw = page.getMediaBox().getWidth();
                cs.addRect(MARGIN_X - 4, y - 6, pw - 2 * MARGIN_X + 8, 36);
                cs.fill();
                cs.setNonStrokingColor(0f, 0f, 0f);
                cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                writeLine(
                        cs,
                        MARGIN_X,
                        y,
                        pdfText("Fusion: ce ticket a ete absorbe dans " + blankDash(d.mergedIntoTicketRef())));
                y -= 12;
                cs.setFont(PDType1Font.HELVETICA, 9);
                writeLine(cs, MARGIN_X, y, pdfText("ID ticket survivant (reference interne): " + d.mergedIntoTicketId()));
                y -= 18;
            }

            section("Informations du ticket");
            keyValue("Priorite", PriorityFrenchLabels.french(d.priority()));
            keyValue("Type", d.type());
            keyValue("Departement", departmentLabel(d.departmentId()));
            keyValue("SLA", slaExportLine(d));
            keyValue("Agent assigne principal", d.assignedAgent());
            if (d.showPluralAgentsRow()) {
                keyValue(
                        "Agents assignes (tous)",
                        d.assignedAgents().isEmpty() ? "-" : String.join(", ", d.assignedAgents()));
            }
            keyValue("Date / heure de creation", d.createdAt());
            keyValue("Derniere mise a jour", d.updatedAt());
            keyValue("Cloture", "CLOSED".equalsIgnoreCase(d.status()) ? d.updatedAt() : "-");
            keyValue("Resolution", "CLOSED".equalsIgnoreCase(d.status()) ? "Resolue" : "-");

            if (d.closeRequested()) {
                section("Demande de cloture");
                paragraph(closureExportSummary(d), 88);
            }

            section("Description");
            paragraph(d.description(), 88);

            section("Taches (" + d.tasks().size() + ")");
            if (d.tasks().isEmpty()) {
                paragraph("Aucune tache liee a ce ticket.", 88);
            } else {
                for (TicketTaskRow t : d.tasks()) {
                    ensureSpace(62);
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                    writeLine(cs, MARGIN_X, y, pdfText("Tache: " + t.title()));
                    y -= 13;
                    cs.setFont(PDType1Font.HELVETICA, 9);
                    writeLine(
                            cs,
                            MARGIN_X + 6,
                            y,
                            pdfText(
                                    "Assigne: "
                                            + blankDash(t.assignee())
                                            + "   |   Statut: "
                                            + t.status()
                                            + "   |   Priorite: "
                                            + PriorityFrenchLabels.french(t.priority())));
                    y -= 11;
                    writeLine(
                            cs,
                            MARGIN_X + 6,
                            y,
                            pdfText(
                                    "Echeance: "
                                            + blankDash(t.dueAt())
                                            + "   |   Debut: "
                                            + blankDash(t.startedAt())
                                            + "   |   Fermeture: "
                                            + blankDash(t.closedAt())));
                    y -= 11;
                    writeLine(
                            cs,
                            MARGIN_X + 6,
                            y,
                            pdfText("Duree: " + blankDash(t.duration()) + "   |   ID tache (Mon travail): " + t.id()));
                    y -= 14;
                }
            }

            section("Activite / Commentaires (" + d.comments().size() + ")");
            if (d.comments().isEmpty()) {
                paragraph("Aucun commentaire.", 88);
            } else {
                for (TicketCommentRow c : d.comments()) {
                    ensureSpace(36);
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
                    writeLine(cs, MARGIN_X, y, pdfText(blankDash(c.createdAt()) + " — " + blankDash(c.author())));
                    y -= 11;
                    cs.setFont(PDType1Font.HELVETICA, 9);
                    for (String line : wrap(pdfText(c.text()), 90)) {
                        ensureSpace(12);
                        writeLine(cs, MARGIN_X + 6, y, line);
                        y -= 11;
                    }
                    if (c.attachmentName() != null && !c.attachmentName().isBlank()) {
                        ensureSpace(12);
                        writeLine(cs, MARGIN_X + 6, y, pdfText("Pieces jointes: " + c.attachmentName()));
                        y -= 11;
                    }
                    y -= 6;
                }
            }

            section("Courriers lies (" + d.linkedCouriers().size() + ")");
            if (d.linkedCouriers().isEmpty()) {
                paragraph("Aucun courrier lie.", 88);
            } else {
                for (LinkedCourierRow c : d.linkedCouriers()) {
                    ensureSpace(16);
                    writeLine(
                            cs,
                            MARGIN_X,
                            y,
                            pdfText(blankDash(c.refCode())
                                    + " — "
                                    + blankDash(c.title())
                                    + "   ["
                                    + blankDash(c.status())
                                    + "]   "
                                    + blankDash(c.createdAt())));
                    y -= 12;
                }
            }

            section("Genealogie du ticket");
            if (d.genealogy().isEmpty()) {
                paragraph("Aucun evenement enregistre.", 88);
            } else {
                for (TicketGenealogyRow g : d.genealogy()) {
                    String head =
                            blankDash(g.when())
                                    + "   |   "
                                    + blankDash(g.title())
                                    + "   |   "
                                    + blankDash(g.actor());
                    if (g.target() != null && !g.target().isBlank()) {
                        head += "   ->   " + g.target();
                    }
                    cs.setFont(PDType1Font.HELVETICA, 9);
                    for (String line : wrap(pdfText(head), 88)) {
                        ensureSpace(12);
                        writeLine(cs, MARGIN_X, y, line);
                        y -= 11;
                    }
                    if (g.note() != null && !g.note().isBlank()) {
                        cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
                        for (String line : wrap(pdfText(g.note()), 92)) {
                            ensureSpace(11);
                            writeLine(cs, MARGIN_X + 8, y, line);
                            y -= 10;
                        }
                        cs.setFont(PDType1Font.HELVETICA, 9);
                    }
                    y -= 6;
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public Optional<TicketCommentRow> findComment(Long commentId) {
        if (commentId == null) {
            return Optional.empty();
        }
        Map<Long, String> userNames = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (x, y) -> x));
        return ticketComments.findById(commentId).map(c -> new TicketCommentRow(
                c.getId(),
                c.getAuthorUserId() == null ? "" : userNames.getOrDefault(c.getAuthorUserId(), ""),
                safe(c.getCommentText()),
                safe(c.getAttachmentPath()),
                joinedFileNames(c.getAttachmentPath()),
                formatTs(c.getCreatedAt())));
    }

    private boolean taskAssignmentSousDirectionPickerEnabled(UserAccount actor) {
        return directionScopeService.isSuperAdmin(actor) || actor.getDirectionId() != null;
    }

    private LinkedHashSet<Long> distinctSousDirectionIdsVisibleToActor(UserAccount actor) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (directionScopeService.isSuperAdmin(actor)) {
            users.findAll().stream()
                    .filter(UserAccount::isActiveBool)
                    .map(UserAccount::getSousDirectionId)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
            return ids;
        }
        Long dirId = actor.getDirectionId();
        if (dirId == null) {
            return ids;
        }
        users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> dirId.equals(u.getDirectionId()))
                .map(UserAccount::getSousDirectionId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        if (actor.getSousDirectionId() != null) {
            ids.add(actor.getSousDirectionId());
        }
        return ids;
    }

    private List<TaskSousDirectionOption> orderTaskSousDirectionOptions(
            LinkedHashSet<Long> ids, Long preferredSdId, Map<Long, String> sdNames) {
        List<Long> list = new ArrayList<>(ids);
        list.sort(
                Comparator.comparing((Long id) -> preferredSdId != null && preferredSdId.equals(id) ? 0 : 1)
                        .thenComparing(id -> sdNames.getOrDefault(id, "").toLowerCase(Locale.ROOT)));
        return list.stream()
                .map(id -> new TaskSousDirectionOption(id, sdNames.getOrDefault(id, "SD " + id)))
                .toList();
    }

    private Set<Long> ticketParticipantUserIds(Ticket t) {
        Set<Long> ids = new LinkedHashSet<>();
        if (t.getAssignedTo() != null) {
            ids.add(t.getAssignedTo());
        }
        ticketAssignments.findByTicketId(t.getId()).forEach(a -> ids.add(a.getUserId()));
        return ids;
    }

    /** Distinct humans assigned to the ticket itself ({@code assigned_to} + {@code ticket_assignments}), not task rows. */
    private int distinctCollaborativeAssigneeCount(Ticket t) {
        if (t == null) {
            return 0;
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (t.getAssignedTo() != null) {
            ids.add(t.getAssignedTo());
        }
        ticketAssignments.findByTicketId(t.getId()).forEach(a -> {
            if (a.getUserId() != null) {
                ids.add(a.getUserId());
            }
        });
        return ids.size();
    }

    private boolean isSeniorClosurePoolReviewerRole(String normalizedViewerRole) {
        return "INSPECTEUR".equals(normalizedViewerRole)
                || "SOUS-DIRECTEUR".equals(normalizedViewerRole)
                || "DIRECTEUR".equals(normalizedViewerRole);
    }

    private void notifySeniorClosureReviewers(Ticket t, String requestedByUsername) {
        String ticketRef = ticketNumber(t);
        UserAccount assigneeUser = primaryAssigneeUserId(t).flatMap(users::findById).orElse(null);
        final Long notifyDirId =
                assigneeUser != null && assigneeUser.getDirectionId() != null
                        ? assigneeUser.getDirectionId()
                        : (t.getCreatedBy() == null
                                ? null
                                : users.findById(t.getCreatedBy()).map(UserAccount::getDirectionId).orElse(null));
        Set<String> seniorRoles = Set.of("INSPECTEUR", "SOUS-DIRECTEUR", "DIRECTEUR");
        users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> seniorRoles.contains(RoleKeys.normalizeForScope(u.getRole())))
                .filter(u -> notifyDirId == null
                        || (u.getDirectionId() != null && u.getDirectionId().equals(notifyDirId)))
                .forEach(u -> notificationService.notifyCloseReview(
                        u.getId(), ticketRef, requestedByUsername, true, t.getId()));
    }

    private Long closureAnchorDirectionId(Ticket t) {
        UserAccount assigneeUser = primaryAssigneeUserId(t).flatMap(users::findById).orElse(null);
        Long anchorDirectionId = assigneeUser != null ? assigneeUser.getDirectionId() : null;
        if (anchorDirectionId == null && t.getCreatedBy() != null) {
            anchorDirectionId = users.findById(t.getCreatedBy()).map(UserAccount::getDirectionId).orElse(null);
        }
        return anchorDirectionId;
    }

    private void assertEligibleSeniorClosureDelegate(Ticket t, UserAccount actor, UserAccount delegate) {
        if (delegate == null || !delegate.isActiveBool()) {
            throw new IllegalArgumentException("badReviewer");
        }
        String norm = RoleKeys.normalizeForScope(delegate.getRole());
        if (!isSeniorClosurePoolReviewerRole(norm)) {
            throw new IllegalArgumentException("badReviewer");
        }
        if (!directionScopeService.canAccessTicket(actor, t) || !directionScopeService.canAccessTicket(delegate, t)) {
            throw new IllegalArgumentException("badReviewer");
        }
        if (!directionScopeService.canAccessUser(actor, delegate.getId())) {
            throw new IllegalArgumentException("badReviewer");
        }
        if (!directionScopeService.isSuperAdmin(actor)) {
            Long anchor = closureAnchorDirectionId(t);
            Long viewerDir = actor.getDirectionId();
            if (anchor != null && viewerDir != null && !anchor.equals(viewerDir)) {
                throw new IllegalArgumentException("badReviewer");
            }
            Long delegateDir = delegate.getDirectionId();
            if (anchor != null && delegateDir != null && !anchor.equals(delegateDir)) {
                throw new IllegalArgumentException("badReviewer");
            }
        }
    }

    /**
     * Cadres habilités à recevoir une demande de clôture ciblée (Mon travail), même direction que le dossier.
     */
    @Transactional(readOnly = true)
    public List<ClosureSeniorPick> listSeniorClosureDelegates(Long ticketId, String actorUsername) {
        Ticket t = scopedTicket(ticketId, actorUsername);
        ensureTicketWorker(t.getId(), actorUsername);
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        if (distinctCollaborativeAssigneeCount(t) < 2) {
            return List.of();
        }
        final Long notifyDirId = closureAnchorDirectionId(t);
        Set<String> seniorRoles = Set.of("INSPECTEUR", "SOUS-DIRECTEUR", "DIRECTEUR");
        return users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> seniorRoles.contains(RoleKeys.normalizeForScope(u.getRole())))
                .filter(u -> notifyDirId == null
                        || (u.getDirectionId() != null && u.getDirectionId().equals(notifyDirId)))
                .filter(u -> directionScopeService.canAccessUser(actor, u.getId()))
                .map(u -> new ClosureSeniorPick(
                        u.getId(),
                        u.getUsername() + " (" + RoleKeys.normalizeForScope(u.getRole()) + ")",
                        u.getSousDirectionId()))
                .sorted(Comparator.comparing(ClosureSeniorPick::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<TaskAgentOption> buildLegacyTaskAgentOptions(Ticket t, Map<Long, String> userNames) {
        List<TaskAgentOption> opts = ticketAssignments.findByTicketId(t.getId()).stream()
                .map(TicketAssignment::getUserId)
                .distinct()
                .map(uid -> users.findById(uid).orElse(null))
                .filter(Objects::nonNull)
                .filter(UserAccount::isActiveBool)
                .map(this::toTaskAgentOption)
                .filter(o -> o.label() != null && !o.label().isBlank())
                .toList();
        if (opts.isEmpty() && t.getAssignedTo() != null) {
            UserAccount u = users.findById(t.getAssignedTo()).orElse(null);
            if (u != null && u.isActiveBool()) {
                opts = List.of(toTaskAgentOption(u));
            }
        }
        return opts;
    }

    private TaskAgentOption toTaskAgentOption(UserAccount u) {
        String role = u.getRole() == null ? "" : u.getRole().trim();
        String label = u.getUsername() + (role.isEmpty() ? "" : " (" + role + ")");
        return new TaskAgentOption(u.getId(), label);
    }

    private List<TaskAgentOption> collectTaskAssigneesForSousDirection(UserAccount actor, Ticket t, Long sousDirectionId) {
        if (sousDirectionId == null) {
            return List.of();
        }
        Set<Long> ticketPeople = ticketParticipantUserIds(t);
        Map<Long, UserAccount> byId =
                users.findAll().stream().collect(Collectors.toMap(UserAccount::getId, u -> u, (a, b) -> a));
        Set<Long> seen = new HashSet<>();
        List<TaskAgentOption> out = new ArrayList<>();
        for (Long uid : ticketPeople) {
            UserAccount u = byId.get(uid);
            if (u == null || !u.isActiveBool()) {
                continue;
            }
            if (!sousDirectionId.equals(u.getSousDirectionId())) {
                continue;
            }
            if (Objects.equals(u.getId(), actor.getId())) {
                continue;
            }
            if (seen.add(uid)) {
                out.add(toTaskAgentOption(u));
            }
        }
        for (UserAccount u : users.findAll()) {
            if (!u.isActiveBool()) {
                continue;
            }
            if (Objects.equals(u.getId(), actor.getId())) {
                continue;
            }
            if (!sousDirectionId.equals(u.getSousDirectionId())) {
                continue;
            }
            if (!directionScopeService.isSuperAdmin(actor)) {
                if (actor.getDirectionId() == null || !actor.getDirectionId().equals(u.getDirectionId())) {
                    continue;
                }
            }
            if (seen.contains(u.getId())) {
                continue;
            }
            if (ticketPeople.contains(u.getId()) || canAssignTaskByHierarchy(actor, u)) {
                seen.add(u.getId());
                out.add(toTaskAgentOption(u));
            }
        }
        out.sort(Comparator.comparing(TaskAgentOption::label, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private boolean canAssignTaskByHierarchy(UserAccount actor, UserAccount candidate) {
        if (directionScopeService.isSuperAdmin(actor)) {
            return true;
        }
        if (isTaskAssignmentManagerRole(actor)) {
            return true;
        }
        return taskRoleRank(candidate.getRole()) < taskRoleRank(actor.getRole());
    }

    private boolean isTaskAssignmentManagerRole(UserAccount actor) {
        String n = RoleKeys.normalizeForScope(actor.getRole());
        return "DIRECTEUR".equals(n)
                || "SOUS-DIRECTEUR".equals(n)
                || "ADMIN".equalsIgnoreCase(actor.getRole() == null ? "" : actor.getRole())
                || "SUPER_ADMIN".equalsIgnoreCase(actor.getRole() == null ? "" : actor.getRole());
    }

    /**
     * Higher rank = more senior. Assignee is allowed when {@code taskRoleRank(assignee) < taskRoleRank(actor)}.
     * Ladder aligns with escalation / field hierarchy (desktop SYSCO).
     */
    private static int taskRoleRank(String role) {
        String n = RoleKeys.normalizeForScope(role);
        if ("SUPER_ADMIN".equalsIgnoreCase(n)) {
            return 1000;
        }
        if ("ADMIN".equalsIgnoreCase(n)) {
            return 999;
        }
        if ("DIRECTEUR".equals(n)) {
            return 100;
        }
        if ("SOUS-DIRECTEUR".equals(n)) {
            return 80;
        }
        if ("INSPECTEUR".equals(n)) {
            return 70;
        }
        if ("CONTROLEUR".equals(n)) {
            return 55;
        }
        if ("SECRETAIRE".equals(n)) {
            return 50;
        }
        if ("VERIFICATEUR".equals(n)) {
            return 45;
        }
        if ("VERIFICATEUR-ASSISTANT".equals(n)) {
            return 40;
        }
        if ("COURIER".equals(n)) {
            return 20;
        }
        return 15;
    }

    private void validateTaskAssignee(Long assigneeId, Long taskSousDirectionId, UserAccount actor, Ticket t) {
        UserAccount assignee = users.findById(assigneeId).orElseThrow(() -> new IllegalArgumentException("badAssignee"));
        if (!assignee.isActiveBool()) {
            throw new IllegalArgumentException("badAssignee");
        }
        if (!taskAssignmentSousDirectionPickerEnabled(actor)) {
            boolean ok = ticketAssignments.existsByTicketIdAndUserId(t.getId(), assigneeId)
                    || (t.getAssignedTo() != null && t.getAssignedTo().equals(assigneeId));
            if (!ok) {
                throw new IllegalArgumentException("badAssignee");
            }
            return;
        }
        if (directionScopeService.isSuperAdmin(actor)) {
            if (taskSousDirectionId != null) {
                if (assignee.getSousDirectionId() == null
                        || !taskSousDirectionId.equals(assignee.getSousDirectionId())) {
                    throw new IllegalArgumentException("badAssignee");
                }
            }
            return;
        }
        if (taskSousDirectionId == null
                || assignee.getSousDirectionId() == null
                || !taskSousDirectionId.equals(assignee.getSousDirectionId())) {
            throw new IllegalArgumentException("badAssignee");
        }
        if (actor.getDirectionId() == null || !actor.getDirectionId().equals(assignee.getDirectionId())) {
            throw new IllegalArgumentException("badAssignee");
        }
        if (ticketParticipantUserIds(t).contains(assigneeId)) {
            return;
        }
        if (!canAssignTaskByHierarchy(actor, assignee)) {
            throw new IllegalArgumentException("badAssignee");
        }
    }

    private String serializeAssigneesPayload(Map<String, List<TaskAgentOption>> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<TaskAgentOption>> e : map.entrySet()) {
            List<Map<String, Object>> rows = e.getValue().stream()
                    .map(o -> Map.<String, Object>of("id", o.id(), "label", o.label()))
                    .toList();
            out.put(e.getKey(), rows);
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialize task assignees map: {}", ex.getMessage());
            return "{}";
        }
    }

    private void clearClosureRequestFields(Ticket t) {
        t.setCloseRequestedAt(null);
        t.setCloseRequestedBy(null);
        t.setCloseReviewRole(null);
        t.setCloseReviewUserIds(null);
    }

    /** Whether the actor may close the ticket immediately (no closure request): no tasks, only own tasks, or admin path. */
    @Transactional(readOnly = true)
    public boolean mayCloseDirectlyWithoutReview(long ticketId, long actorUserId) {
        Ticket tx = tickets.findById(ticketId).orElse(null);
        if (tx != null && distinctCollaborativeAssigneeCount(tx) >= 2) {
            return false;
        }
        List<AutomatedJob> ticketJobs = jobs.findByTicketId(ticketId);
        if (ticketJobs.isEmpty()) {
            return true;
        }
        Set<Long> creators = distinctTaskCreatorIds(ticketId);
        if (creators.isEmpty()) {
            return false;
        }
        Set<Long> reviewers =
                creators.stream()
                        .filter(uid -> !uid.equals(actorUserId))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return reviewers.isEmpty();
    }

    private void assertDirectCloseAllowed(Ticket t, UserAccount actor) {
        String rk = RoleKeys.normalizeForScope(actor.getRole());
        if ("ADMIN".equals(rk) || "SUPER_ADMIN".equals(rk)) {
            return;
        }
        if (!mayCloseDirectlyWithoutReview(t.getId(), actor.getId())) {
            throw new IllegalStateException("closureReviewRequired");
        }
    }

    private Set<Long> distinctTaskCreatorIds(Long ticketId) {
        return jobs.findByTicketId(ticketId).stream()
                .map(AutomatedJob::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> parseCloseReviewUserIds(Ticket t) {
        String raw = t.getCloseReviewUserIds();
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        try {
            List<Long> list = objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
            return list.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (JsonProcessingException e) {
            log.warn("Invalid close_review_user_ids on ticket {}: {}", t.getId(), e.getMessage());
            return Set.of();
        }
    }

    private String serializeCloseReviewUserIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(ids));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("closeReviewUserIds");
        }
    }

    private void ensureTicketWorker(Long ticketId, String actorUsername) {
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalStateException("notAllowed"));
        Ticket tx = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        if (actor.getId().equals(tx.getAssignedTo())) {
            return;
        }
        if (ticketAssignments.existsByTicketIdAndUserId(ticketId, actor.getId())) {
            return;
        }
        if (jobs.findByTicketIdAndAssigneeUserId(ticketId, actor.getId()).stream()
                .anyMatch(AutomatedJobProcessingService::isActiveOpenTask)) {
            return;
        }
        throw new IllegalStateException("notAllowed");
    }

    private Optional<Long> primaryAssigneeUserId(Ticket t) {
        if (t.getAssignedTo() != null) {
            return Optional.of(t.getAssignedTo());
        }
        return ticketAssignments.findByTicketId(t.getId()).stream()
                .map(TicketAssignment::getUserId)
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static Optional<String> nextReviewerRoleForLowAssignee(String normalizedAssigneeRole) {
        if (normalizedAssigneeRole == null || normalizedAssigneeRole.isBlank()) {
            return Optional.of("INSPECTEUR");
        }
        return switch (normalizedAssigneeRole) {
            case "VERIFICATEUR-ASSISTANT" -> Optional.of("VERIFICATEUR");
            case "VERIFICATEUR" -> Optional.of("CONTROLEUR");
            case "CONTROLEUR" -> Optional.of("INSPECTEUR");
            case "INSPECTEUR", "SOUS-DIRECTEUR", "DIRECTEUR", "ADMIN", "SUPER_ADMIN" -> Optional.empty();
            case "SECRETAIRE" -> Optional.of("INSPECTEUR");
            case "COURIER", "COURRIER" -> Optional.of("INSPECTEUR");
            default -> Optional.of("INSPECTEUR");
        };
    }

    private void notifyClosureReviewers(
            Ticket t,
            UserAccount assigneeUser,
            String requestedByUsername,
            String reviewRoleKey,
            Set<Long> explicitReviewerUserIds) {
        String ticketRef = ticketNumber(t);
        if (explicitReviewerUserIds != null && !explicitReviewerUserIds.isEmpty()) {
            for (Long uid : explicitReviewerUserIds) {
                if (uid != null) {
                    notificationService.notifyCloseReview(
                            uid, ticketRef, requestedByUsername, false, t.getId());
                }
            }
            return;
        }
        if (reviewRoleKey == null || reviewRoleKey.isBlank()) {
            if (assigneeUser.getId() != null) {
                notificationService.notifyCloseReview(
                        assigneeUser.getId(), ticketRef, requestedByUsername, false, t.getId());
            }
            return;
        }
        final Long notifyDirId =
                assigneeUser.getDirectionId() != null
                        ? assigneeUser.getDirectionId()
                        : (t.getCreatedBy() == null
                                ? null
                                : users.findById(t.getCreatedBy()).map(UserAccount::getDirectionId).orElse(null));
        users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> reviewRoleKey.equals(RoleKeys.normalizeForScope(u.getRole())))
                .filter(u -> notifyDirId == null
                        || (u.getDirectionId() != null && u.getDirectionId().equals(notifyDirId)))
                .forEach(u -> notificationService.notifyCloseReview(
                        u.getId(), ticketRef, requestedByUsername, true, t.getId()));
    }

    private Ticket scopedTicket(Long id, String actorUsername) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount viewer = directionScopeService.requireUser(actorUsername);
        if (PlannerTicketVisibility.isHiddenFromOperationalViews(t, Instant.now())) {
            throw new IllegalArgumentException("notFound");
        }
        if (!directionScopeService.canAccessTicket(viewer, t)) {
            throw new IllegalArgumentException("notFound");
        }
        return t;
    }

    private void ensureActorCreatedTicket(Ticket t, String actorUsername) {
        UserAccount actor = directionScopeService.requireUser(actorUsername);
        if (t.getCreatedBy() == null || !t.getCreatedBy().equals(actor.getId())) {
            throw new IllegalArgumentException("notFound");
        }
    }

    private void applyAuditFields(Ticket t, String actorUsername) {
        t.setUpdatedAt(Instant.now());
        if (actorUsername == null || actorUsername.isBlank()) {
            return;
        }
        users.findByUsernameIgnoreCase(actorUsername).map(UserAccount::getId).ifPresent(t::setUpdatedBy);
    }

    private static TicketLine toLine(Ticket t, Map<Long, String> userNames, Map<Long, List<Long>> assigneeIdsByTicket) {
        String number = ticketNumber(t);
        String title = t.getTitle() == null ? "" : t.getTitle();
        String status = t.getStatus() == null ? "" : t.getStatus().toUpperCase(Locale.ROOT);
        String type = t.getTicketType() == null ? "" : t.getTicketType().toUpperCase(Locale.ROOT);
        String prio = t.getPriority() == null ? "" : t.getPriority().toUpperCase(Locale.ROOT);
        String agent = assigneeIdsByTicket.getOrDefault(t.getId(), List.of()).stream()
                .map(uid -> userNames.getOrDefault(uid, ""))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        if (agent.isBlank() && t.getAssignedTo() != null) {
            agent = userNames.getOrDefault(t.getAssignedTo(), "");
        }
        String createdAt = DisplayDateFormatter.formatInstant(t.getCreatedAt());
        return new TicketLine(t.getId(), number, title, status, type, prio, agent, createdAt);
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private void notifyDirectionAndEscalatorOnClose(Ticket t, String actorUsername) {
        if (t == null) {
            return;
        }
        String ref = ticketNumber(t);
        String handler = safe(actorUsername);
        notificationService.notifyDirectionStakeholders(
                notificationDirectionId(t),
                "Ticket cloture",
                "Le ticket " + ref + " est cloture par " + handler + ".",
                "TICKET_MOVEMENT",
                "TICKET",
                t.getId(),
                ref);
        notificationService.notifyLatestExternalEscalatorOnTicketEvent(
                t.getId(),
                "Mise a jour ticket escalade",
                "Le ticket " + ref + " est cloture par " + handler + ".",
                ref,
                "ESCALATION");
    }

    private static Long notificationDirectionId(Ticket t) {
        if (t == null) {
            return null;
        }
        if (t.getExternalEscalationTargetDirectionId() != null) {
            return t.getExternalEscalationTargetDirectionId();
        }
        if (t.getHandlingDirectionId() != null) {
            return t.getHandlingDirectionId();
        }
        return t.getReporterDirectionId();
    }

    private Long firstAssignedUserId(Long ticketId) {
        return ticketAssignments.findByTicketId(ticketId).stream()
                .map(TicketAssignment::getUserId)
                .findFirst()
                .orElse(null);
    }

    private static String formatTs(Instant v) {
        return DisplayDateFormatter.formatInstant(v);
    }

    private static String slaCssClass(TicketSlaCalculator.SlaUiStatus status) {
        return switch (status) {
            case OK -> "ticket-fx-sla-ok";
            case WARNING -> "ticket-fx-sla-warn";
            case BREACH -> "ticket-fx-sla-breach";
            case CLOSED_OK -> "ticket-fx-sla-closed-ok";
            case CLOSED_BREACH -> "ticket-fx-sla-closed-breach";
            case NA -> "ticket-fx-sla-na";
        };
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return Path.of(path).getFileName().toString();
    }

    private static String joinedFileNames(String paths) {
        if (paths == null || paths.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(paths.split(";;"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(TicketManagementService::fileName)
                .collect(Collectors.joining(", "));
    }

    private List<TicketGenealogyRow> buildGenealogy(Ticket t, Map<Long, String> userNames) {
        List<TicketGenealogyRow> fromEvents = ticketEvents.findByTicketIdOrderByCreatedAtAsc(t.getId()).stream()
                .map(e -> new TicketGenealogyRow(
                        eventLabel(e.getEventType()),
                        e.getActorUserId() == null ? "" : userNames.getOrDefault(e.getActorUserId(), ""),
                        e.getTargetUserId() == null ? "" : userNames.getOrDefault(e.getTargetUserId(), ""),
                        safe(e.getNote()),
                        formatTs(e.getCreatedAt())))
                .toList();
        if (!fromEvents.isEmpty()) {
            return fromEvents;
        }
        return List.of(new TicketGenealogyRow(
                "Created",
                t.getCreatedBy() == null ? "" : userNames.getOrDefault(t.getCreatedBy(), ""),
                "",
                "Ticket created",
                formatTs(t.getCreatedAt())));
    }

    private static String eventLabel(String v) {
        if (v == null || v.isBlank()) {
            return "Event";
        }
        return switch (v.trim().toUpperCase(Locale.ROOT)) {
            case "CREATED" -> "Created";
            case "ASSIGNED" -> "Assigned";
            case "ESCALATED" -> "Escalated";
            case "CLOSED" -> "Closed";
            case "UPDATED" -> "Updated";
            case "COMMENTED" -> "Commented";
            case "TASK_CREATED" -> "Task created";
            case "MERGED" -> "Merged";
            case "PDF_EXPORTED" -> "PDF exported";
            case "REOPENED" -> "Reopened";
            default -> v;
        };
    }

    private static String pdfAttachmentFilename(String ticketRef, Long ticketId) {
        String base =
                ticketRef == null || ticketRef.isBlank()
                        ? ("ticket-" + (ticketId == null ? "unknown" : ticketId))
                        : ticketRef.trim();
        String lower = base.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            base = base.substring(0, base.length() - 4);
        }
        return sanitizeFileBasename(base) + ".pdf";
    }

    /** Safe single-segment filename for {@code Content-Disposition} (Windows / generic). */
    private static String sanitizeFileBasename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ticket";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 32
                    || c == '"'
                    || c == '<'
                    || c == '>'
                    || c == ':'
                    || c == '\\'
                    || c == '/'
                    || c == '|'
                    || c == '?'
                    || c == '*') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "ticket" : out;
    }

    private Long resolveActorId(String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return null;
        }
        return users.findByUsernameIgnoreCase(actorUsername).map(UserAccount::getId).orElse(null);
    }

    private static void validateCommentAttachment(MultipartFile f) {
        String name = stripPath(f.getOriginalFilename() == null ? "" : f.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_COMMENT_EXT.contains(ext)) {
            throw new IllegalArgumentException("attachmentType");
        }
    }

    private static String stripPath(String name) {
        String s = name.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static String normalizePriority(String p) {
        if (p == null || p.isBlank()) {
            return "MEDIUM";
        }
        String v = p.trim().toUpperCase(Locale.ROOT);
        return Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(v) ? v : "MEDIUM";
    }

    private String storeTaskAttachments(Long ticketId, List<MultipartFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        try {
            Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("task-attachments").resolve(String.valueOf(ticketId));
            Files.createDirectories(dir);
            String out = "";
            for (MultipartFile attachment : attachments) {
                if (attachment == null || attachment.isEmpty()) {
                    continue;
                }
                validateTaskAttachment(attachment);
                String safe = stripPath(attachment.getOriginalFilename() == null ? "attachment.bin" : attachment.getOriginalFilename());
                Path dest = dir.resolve(System.currentTimeMillis() + "_" + safe);
                Files.copy(attachment.getInputStream(), dest);
                out = out.isBlank() ? dest.toString() : (out + ";;" + dest);
            }
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    private static void validateTaskAttachment(MultipartFile f) {
        String name = stripPath(f.getOriginalFilename() == null ? "" : f.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_TASK_EXT.contains(ext)) {
            throw new IllegalArgumentException("attachmentType");
        }
    }

    private static String buildBarcodePayload(TicketDetail d) {
        StringBuilder sb = new StringBuilder(480);
        sb.append("SYSCO|")
                .append("ref=").append(blankDash(d.ticketNumber())).append('|')
                .append("title=").append(compactForBarcode(d.title(), 80)).append('|')
                .append("status=").append(blankDash(d.status())).append('|')
                .append("priority=").append(blankDash(d.priority())).append('|')
                .append("type=").append(blankDash(d.type())).append('|')
                .append("department=").append(blankDash(String.valueOf(d.departmentId()))).append('|')
                .append("createdBy=").append(compactForBarcode(d.createdBy(), 36)).append('|')
                .append("createdAt=").append(compactForBarcode(d.createdAt(), 32)).append('|')
                .append("updatedAt=").append(compactForBarcode(d.updatedAt(), 32)).append('|')
                .append("assigned=").append(compactForBarcode(d.assignedAgent(), 48)).append('|')
                .append("desc=").append(compactForBarcode(d.description(), 220));
        return pdfText(sb.toString());
    }

    private static String compactForBarcode(String value, int maxLen) {
        String v = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('|', '/').trim();
        if (v.length() <= maxLen) {
            return v;
        }
        return v.substring(0, Math.max(0, maxLen - 1)) + ".";
    }

    private static BufferedImage generateBarcodeImage(String payload, int width, int height) throws IOException {
        Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new Code128Writer().encode(payload, BarcodeFormat.CODE_128, width, height, hints);
        BufferedImage barcode = MatrixToImageWriter.toBufferedImage(matrix);
        BufferedImage withBackground = new BufferedImage(width, height + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = withBackground.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, withBackground.getWidth(), withBackground.getHeight());
        g.drawImage(barcode, 0, 0, null);
        g.dispose();
        return withBackground;
    }

    private static void writeLine(PDPageContentStream cs, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(pdfText(text));
        cs.endText();
    }

    private static List<String> wrap(String s, int max) {
        String text = s == null ? "" : s.replace("\r", "");
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw;
            while (line.length() > max) {
                out.add(line.substring(0, max));
                line = line.substring(max);
            }
            out.add(line);
        }
        if (out.isEmpty()) {
            out.add("-");
        }
        return out;
    }

    private static String blankDash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    private static String durationLabel(Instant start, Instant close) {
        if (start == null || close == null || close.isBefore(start)) {
            return "-";
        }
        long minutes = java.time.Duration.between(start, close).toMinutes();
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        if (days > 0) return days + "d:" + hours + "H " + mins + " min";
        if (hours > 0) return hours + "H" + (mins > 0 ? ":" + mins + " min" : "");
        return mins + " min";
    }

    private static String pdfText(String v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch >= 32 && ch <= 255) {
                sb.append(ch);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String ticketNumber(Ticket t) {
        if (t.getTicketNumber() != null && !t.getTicketNumber().isBlank()) {
            return t.getTicketNumber().trim();
        }
        int year = Year.now().getValue();
        if (t.getCreatedAt() != null) {
            year = LocalDateTime.ofInstant(t.getCreatedAt(), ZoneId.systemDefault()).getYear();
        }
        return "TCK-" + year + "-" + String.format("%05d", t.getId());
    }

    private static boolean matches(TicketLine t, String q) {
        return contains(t.ticketNumber(), q)
                || contains(t.title(), q)
                || contains(t.status(), q)
                || contains(t.agent(), q);
    }

    private static boolean contains(String v, String q) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(q);
    }

    public record TicketManagementPage(
            List<TicketLine> rows,
            String status,
            String agent,
            String q,
            List<String> agentOptions,
            List<TaskMgmtLine> taskRows,
            String taskStatus,
            String taskAgent,
            String taskQ,
            List<String> taskAgentOptions,
            boolean viewerMayEscalateTasks) {}

    public record TaskMgmtLine(
            Long id,
            String title,
            String assignee,
            String ticketRef,
            Long ticketId,
            String status,
            String dueAt,
            String priority,
            boolean active,
            boolean canEscalate) {}

    /** Inspecteurs / cadres proposés pour une demande de clôture ciblée (multi-assignés). */
    public record ClosureSeniorPick(long id, String label, Long sousDirectionId) {}

    public record PendingClosureTicketRow(
            long id,
            String ticketNumber,
            String title,
            String closeRequestedByUsername,
            String closeReviewRoleKey) {}

    public record TicketLine(
            Long id,
            String ticketNumber,
            String title,
            String status,
            String type,
            String priority,
            String agent,
            String createdAt) {}

    public record TicketDetail(
            Long id,
            String ticketNumber,
            String title,
            String description,
            String status,
            String priority,
            String type,
            Long departmentId,
            String assignedAgent,
            String createdBy,
            String updatedBy,
            String createdAt,
            String updatedAt,
            List<String> assignedAgents,
            boolean showPluralAgentsRow,
            List<TicketTaskRow> tasks,
            List<TaskAgentOption> taskAgentOptions,
            List<TaskSousDirectionOption> taskSousDirectionOptions,
            Long defaultTaskSousDirectionId,
            String taskAssigneesBySousDirectionJson,
            boolean taskAssignmentSousDirectionPicker,
            List<LinkedCourierRow> linkedCouriers,
            List<TicketCommentRow> comments,
            List<TicketGenealogyRow> genealogy,
            boolean closeRequested,
            String closeRequestedByUsername,
            String closeReviewRoleLabel,
            boolean closureReviewUsesTaskCreators,
            String closureReviewerNames,
            boolean viewerMayFinalizeClosure,
            boolean closureSeniorPoolReview,
            String closureAiBriefingText,
            boolean closureAiBriefingLocalFallback,
            Long mergedIntoTicketId,
            String mergedIntoTicketRef,
            String slaStatus,
            String slaDueFormatted,
            String slaResolvedFormatted,
            String slaCssClass) {}

    public record MergeTicketPickPage(
            long absorbedTicketId, String absorbedTicketNumber, String absorbedTitle, List<MergeSurvivorOption> survivors) {}

    public record MergeSurvivorOption(long id, String ticketNumber, String title) {}

    public record LinkedCourierRow(Long id, String refCode, String title, String status, String createdAt) {}
    public record TicketTaskRow(
            Long id,
            String title,
            String assignee,
            String status,
            String dueAt,
            String startedAt,
            String closedAt,
            String duration,
            String priority) {}
    public record TaskSousDirectionOption(Long id, String name) {}

    public record TaskAgentOption(Long id, String label) {}
    public record TicketCommentRow(Long id, String author, String text, String attachmentPath, String attachmentName, String createdAt) {
        public List<String> attachmentPaths() {
            if (attachmentPath == null || attachmentPath.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(attachmentPath.split(";;"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }
    public record TicketGenealogyRow(String title, String actor, String target, String note, String when) {}
}
