package com.sysco.web.service;

import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.TicketAssignment;
import com.sysco.web.domain.TaskComment;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.TaskCommentRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.util.DisplayDateFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MyWorkService {

    private static final Set<String> ALLOWED_TASK_COMMENT_EXT =
            Set.of("png", "jpg", "jpeg", "pdf", "docx", "xlsx", "txt");

    private static final Set<String> SENIOR_ESCALATION_ROLES = Set.of("INSPECTEUR", "SOUS-DIRECTEUR", "DIRECTEUR");
    private static final Set<String> LOW_TIER_ESCALATOR_ROLES =
            Set.of("VERIFICATEUR-ASSISTANT", "VERIFICATEUR", "CONTROLEUR");
    private static final Set<String> CROSS_DIRECTION_RECEIVER_ROLES =
            Set.of("INSPECTEUR", "SOUS-DIRECTEUR", "DIRECTEUR");
    private static final Set<String> SENIOR_SAME_DIRECTION_RECEIVER_ROLES = Set.of(
            "VERIFICATEUR-ASSISTANT",
            "VERIFICATEUR",
            "CONTROLEUR",
            "INSPECTEUR",
            "SOUS-DIRECTEUR",
            "DIRECTEUR",
            "SECRETAIRE");

    /** Same sous-direction escalation targets for contrôleurs / vérificateurs (operational roles only). */
    private static final Set<String> SAME_SOUS_DIRECTION_ESCALATION_ROLES = Set.of(
            "VERIFICATEUR-ASSISTANT",
            "VERIFICATEUR",
            "CONTROLEUR",
            "INSPECTEUR",
            "SOUS-DIRECTEUR",
            "DIRECTEUR",
            "SECRETAIRE");

    private static final Set<String> EXTERNAL_ESCALATION_INBOX_ROLES =
            Set.of("SECRETAIRE", "SOUS-DIRECTEUR", "DIRECTEUR", "INSPECTEUR");

    private final TicketRepository tickets;
    private final TicketAssignmentRepository ticketAssignments;
    private final AutomatedJobRepository jobs;
    private final UserAccountRepository users;
    private final MissionService missions;
    private final TicketTimelineService ticketTimeline;
    private final NotificationService notificationService;
    private final TaskCommentRepository taskComments;
    private final TicketManagementService ticketManagementService;
    private final TicketMonitoringService ticketMonitoringService;
    private final DirectionScopeService directionScopeService;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;

    public MyWorkPage page(String username) {
        if (username == null || username.isBlank()) {
            return new MyWorkPage(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        UserAccount u = users.findByUsernameIgnoreCase(username).orElse(null);
        if (u == null) {
            return new MyWorkPage(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        Map<Long, String> sousDirectionNames =
                sousDirections.findAll().stream()
                        .collect(Collectors.toMap(SousDirection::getId, SousDirection::getName, (a, b) -> a));
        Map<Long, String> directionNames =
                directions.findAll().stream()
                        .collect(Collectors.toMap(Direction::getId, d -> safe(d.getName()), (a, b) -> a));

        Instant now = Instant.now();
        Long actorDirId = u.getDirectionId();
        List<DirectionPickRow> externalDirectionChoices =
                directions.findAll().stream()
                        .filter(d -> actorDirId == null || !actorDirId.equals(d.getId()))
                        .sorted(Comparator.comparing(Direction::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(d -> new DirectionPickRow(d.getId(), safe(d.getName())))
                        .toList();

        java.util.Set<Long> myAssignedTicketIds = ticketAssignments.findByUserId(u.getId()).stream()
                .map(TicketAssignment::getTicketId)
                .collect(java.util.stream.Collectors.toSet());
        List<MyTicketRow> myTickets = new ArrayList<>();
        java.util.LinkedHashSet<Long> ticketIdsSeen = new java.util.LinkedHashSet<>();
        RankPartition sameDir = escalationSamePartitioned(u);
        RankPartition otherDir = escalationOtherPartitioned(u);

        List<AutomatedJob> myJobsForUser = jobs.findByAssigneeUserId(u.getId());

        // Ticket-linked open tasks belong in Mes tâches; hide the same ticket from Mes tickets to avoid duplicates.
        // (Do not wait for due_at — assignees must see work immediately; due instant still gates actions elsewhere.)
        Set<Long> ticketIdsPreferringTaskUi = new LinkedHashSet<>();
        for (AutomatedJob j : myJobsForUser) {
            Long tid = j.getTicketId();
            if (tid == null) {
                continue;
            }
            if (isOpenWorkTask(j)) {
                Ticket linked = tickets.findById(tid).orElse(null);
                if (linked != null && ticketHasMultipleCollaborativeAssignees(linked)) {
                    ticketIdsPreferringTaskUi.add(tid);
                }
            }
        }

        List<Ticket> primary =
                tickets.findAll().stream()
                        .filter(t -> !isTicketMergedAway(t))
                        .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                        .filter(t -> u.getId().equals(t.getAssignedTo()) || myAssignedTicketIds.contains(t.getId()))
                        .filter(t -> !ticketIdsPreferringTaskUi.contains(t.getId()))
                        .sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
        for (Ticket t : primary) {
            myTickets.add(toMyTicketRow(t, u, sameDir, otherDir, sousDirectionNames));
            ticketIdsSeen.add(t.getId());
        }
        String viewerRole = RoleKeys.normalizeForScope(u.getRole());
        if (isSeniorEscalationViewer(viewerRole)) {
            tickets.findAll().stream()
                    .filter(ticket -> !isTicketMergedAway(ticket))
                    .filter(ticket -> !PlannerTicketVisibility.isHiddenFromOperationalViews(ticket, now))
                    .filter(ticket -> "ESCALATED".equalsIgnoreCase(safe(ticket.getStatus())))
                    .filter(ticket -> !(ticket.getExternalEscalationTargetDirectionId() != null
                            && ticket.getAssignedTo() == null))
                    .filter(ticket -> directionScopeService.canAccessTicket(u, ticket))
                    .filter(ticket -> !ticketIdsSeen.contains(ticket.getId()))
                    .sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .forEach(ticket -> myTickets.add(toMyTicketRow(ticket, u, sameDir, otherDir, sousDirectionNames)));
        }

        // All assigned jobs (standalone + ticket-linked); visibility does not depend on due_at.
        List<MyTaskRow> myTasks = myJobsForUser.stream()
                .filter(j -> !isPlannerLinkedJobDeferred(j, now))
                .sorted(Comparator.comparing(AutomatedJob::getId).reversed())
                .map(j -> new MyTaskRow(
                        j.getId(),
                        safe(j.getJobTitle()),
                        safe(j.getStatus()).isBlank() ? ((j.getActive() != null && j.getActive() == 1) ? "OPEN" : "CLOSED") : safe(j.getStatus()),
                        safe(j.getDueAt()),
                        safe(j.getPriority()),
                        splitAttachmentNames(j.getAttachmentPaths()),
                        formatInstant(j.getStartedAt()),
                        formatInstant(j.getClosedAt()),
                        durationLabel(j.getStartedAt(), j.getClosedAt()),
                        j.getTicketId()))
                .toList();

        List<MissionService.MissionRow> myMissions = missions.missionsForUser(username);

        List<MyEscalationRow> escalations = buildExternalEscalationInbox(u, directionNames);

        List<TicketManagementService.PendingClosureTicketRow> closureReviews =
                ticketManagementService.pendingClosureReviewsFor(username);

        return new MyWorkPage(
                myTickets, myMissions, myTasks, externalDirectionChoices, escalations, closureReviews);
    }

    private List<MyEscalationRow> buildExternalEscalationInbox(UserAccount viewer, Map<Long, String> dirNames) {
        String role = RoleKeys.normalizeForScope(viewer.getRole());
        if (!EXTERNAL_ESCALATION_INBOX_ROLES.contains(role)) {
            return List.of();
        }
        Long dirId = viewer.getDirectionId();
        if (dirId == null) {
            return List.of();
        }
        return tickets.findExternalEscalationInbox(dirId).stream()
                .map(t -> new MyEscalationRow(
                        t.getId(),
                        ticketNumber(t),
                        dirNames.getOrDefault(t.getExternalEscalationSourceDirectionId(), "—"),
                        dirNames.getOrDefault(t.getExternalEscalationTargetDirectionId(), "—"),
                        "—",
                        t.getAssignedTo() == null
                                ? "—"
                                : users.findById(t.getAssignedTo()).map(UserAccount::getUsername).orElse("—"),
                        safe(t.getStatus())))
                .toList();
    }

    @Transactional
    public void closeTicket(long id, String actor, boolean canManageAll) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureMayActOnTicket(t, actor, canManageAll, true);
        List<AutomatedJob> ticketTasks = jobs.findByTicketId(t.getId());
        long openTasks =
                ticketTasks.stream().filter(AutomatedJobProcessingService::isActiveOpenTask).count();
        if (openTasks > 0) {
            throw new IllegalStateException("tasksPending");
        }
        UserAccount actorUser = users.findByUsernameIgnoreCase(actor).orElseThrow(() -> new IllegalStateException("notAllowed"));
        if (!canManageAll) {
            if (ticketManagementService.mayCloseDirectlyWithoutReview(t.getId(), actorUser.getId())) {
                ticketManagementService.close(t.getId(), actor);
                return;
            }
            ticketManagementService.requestTicketClosure(id, actor);
            throw new IllegalStateException("closeRequested");
        }
        ticketManagementService.close(t.getId(), actor);
    }

    @Transactional
    public void startTicket(long id, String actor, boolean canManageAll) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureAllowed(t.getId(), actor, canManageAll);
        t.setStatus("IN_PROGRESS");
        if (t.getStartedAt() == null) {
            t.setStartedAt(Instant.now());
        }
        t.setUpdatedAt(Instant.now());
        setUpdatedByActor(t, actor);
        tickets.save(t);
        ticketTimeline.log("IN_PROGRESS", id, actor, null, "Ticket d\u00e9marr\u00e9 depuis Mon travail");
        if (t.getCreatedBy() != null) {
            notificationService.notifyTicketCreatorInProgress(
                    t.getCreatedBy(), ticketNumber(t), safe(actor), t.getId());
        }
        String ref = ticketNumber(t);
        Long notifyDirectionId = notificationDirectionId(t);
        String handler = safe(actor);
        notificationService.notifyDirectionStakeholders(
                notifyDirectionId,
                "Ticket demarre",
                "Le ticket " + ref + " est demarre par " + handler + ".",
                "TICKET_MOVEMENT",
                "TICKET",
                t.getId(),
                ref);
        notificationService.notifyLatestExternalEscalatorOnTicketEvent(
                t.getId(),
                "Mise a jour ticket escalade",
                "Le ticket " + ref + " est demarre par " + handler + ".",
                ref,
                "ESCALATION");
    }

    @Transactional(readOnly = true)
    public TicketManagementService.MergeTicketPickPage mergePick(long absorbedTicketId, String actor, boolean canManageAll) {
        return ticketManagementService.mergeSurvivorChoices(absorbedTicketId, actor, canManageAll);
    }

    @Transactional
    public long mergeExecute(long absorbedTicketId, long survivorTicketId, String actor, boolean canManageAll) {
        return ticketManagementService.mergeTicketsAbsorbIntoSurvivor(absorbedTicketId, survivorTicketId, actor, canManageAll);
    }

    /**
     * Même graphe d'escalade / réassignation que « Mon travail », pour réutiliser l'UI dans « Gestion des tickets ».
     */
    @Transactional(readOnly = true)
    public TicketEscalationMenu ticketEscalationMenu(String username) {
        if (username == null || username.isBlank()) {
            return TicketEscalationMenu.empty();
        }
        UserAccount u = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (u == null) {
            return TicketEscalationMenu.empty();
        }
        Map<Long, String> sousDirectionNames =
                sousDirections.findAll().stream()
                        .collect(Collectors.toMap(SousDirection::getId, SousDirection::getName, (a, b) -> a));
        Long actorDirId = u.getDirectionId();
        List<DirectionPickRow> externalDirectionChoices =
                directions.findAll().stream()
                        .filter(d -> actorDirId == null || !actorDirId.equals(d.getId()))
                        .sorted(Comparator.comparing(Direction::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(d -> new DirectionPickRow(d.getId(), safe(d.getName())))
                        .toList();
        RankPartition sameDir = escalationSamePartitioned(u);
        RankPartition otherDir = escalationOtherPartitioned(u);
        List<SousDirectionPickRow> escSd = buildSousDirectionPicks(sameDir.escalateChoices(), sousDirectionNames);
        List<SousDirectionPickRow> reSd = buildSousDirectionPicks(sameDir.reassignChoices(), sousDirectionNames);
        return new TicketEscalationMenu(
                externalDirectionChoices,
                sameDir.escalateChoices(),
                sameDir.reassignChoices(),
                otherDir.escalateChoices(),
                otherDir.reassignChoices(),
                escSd,
                reSd,
                escSd.size() > 1,
                reSd.size() > 1);
    }

    @Transactional
    public boolean escalateTicket(
            long id, Long targetUserId, String actor, boolean canManageAll, boolean crossDirection, boolean reassign) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureMayActOnTicket(t, actor, canManageAll, true);
        UserAccount actorUser = users.findByUsernameIgnoreCase(actor).orElseThrow(() -> new IllegalStateException("notAllowed"));
        if (targetUserId == null) {
            throw new IllegalArgumentException("target");
        }
        UserAccount target = users.findById(targetUserId).orElseThrow(() -> new IllegalArgumentException("target"));
        if (!canManageAll) {
            if (reassign) {
                if (!isReassignTargetAllowed(actorUser, target, crossDirection)) {
                    throw new IllegalStateException("badReassignTarget");
                }
            } else if (!isEscalateTargetAllowed(actorUser, target, crossDirection)) {
                throw new IllegalStateException("badEscalationTarget");
            }
        }
        return applyInternalEscalationOrReassign(t, id, actor, targetUserId, target, reassign);
    }

    /**
     * Escalade / réassignation depuis « Gestion des tickets » : périmètre direction (comme la liste), pas besoin d'être
     * assigné au ticket.
     */
    @Transactional
    public void escalateTicketFromTicketManagement(
            long id,
            Long targetUserId,
            String actor,
            boolean crossDirection,
            boolean reassign,
            boolean relaxTargetChecks) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount actorUser = users.findByUsernameIgnoreCase(actor).orElseThrow(() -> new IllegalStateException("notAllowed"));
        if (!directionScopeService.canAccessTicket(actorUser, t)) {
            throw new IllegalStateException("notAllowed");
        }
        ensureTicketOpenForEscalationActions(t);
        if (targetUserId == null) {
            throw new IllegalArgumentException("target");
        }
        UserAccount target = users.findById(targetUserId).orElseThrow(() -> new IllegalArgumentException("target"));
        if (!relaxTargetChecks) {
            if (reassign) {
                if (!isReassignTargetAllowed(actorUser, target, crossDirection)) {
                    throw new IllegalStateException("badReassignTarget");
                }
            } else if (!isEscalateTargetAllowed(actorUser, target, crossDirection)) {
                throw new IllegalStateException("badEscalationTarget");
            }
        }
        applyInternalEscalationOrReassign(t, id, actor, targetUserId, target, reassign);
    }

    @Transactional
    public void escalateTicketExternal(long id, long targetDirectionId, String actor, boolean canManageAll) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureMayActOnTicket(t, actor, canManageAll, true);
        UserAccount actorUser = users.findByUsernameIgnoreCase(actor).orElseThrow(() -> new IllegalStateException("notAllowed"));
        applyExternalEscalation(t, id, targetDirectionId, actor, actorUser, canManageAll);
    }

    @Transactional
    public void escalateTicketExternalFromTicketManagement(
            long id, long targetDirectionId, String actor, boolean relaxDirectionChecks) {
        Ticket t = tickets.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount actorUser = users.findByUsernameIgnoreCase(actor).orElseThrow(() -> new IllegalStateException("notAllowed"));
        if (!directionScopeService.canAccessTicket(actorUser, t)) {
            throw new IllegalStateException("notAllowed");
        }
        ensureTicketOpenForEscalationActions(t);
        applyExternalEscalation(t, id, targetDirectionId, actor, actorUser, relaxDirectionChecks);
    }

    private boolean applyInternalEscalationOrReassign(
            Ticket t, long id, String actor, long targetUserId, UserAccount target, boolean reassign) {
        t.setExternalEscalationSourceDirectionId(null);
        t.setExternalEscalationTargetDirectionId(null);
        t.setAssignedTo(targetUserId);

        boolean reopenedForReassign =
                reassign && ticketEligibleForReopenOnReassign(safe(t.getStatus()));
        if (reopenedForReassign) {
            t.setStatus("ASSIGNED");
            t.setClosedAt(null);
            t.setClosedBy(null);
        }

        ticketAssignments.deleteByTicketId(t.getId());
        Instant assignedAt = Instant.now();
        if (ticketAssignments.existsByTicketIdAndUserId(t.getId(), targetUserId)) {
            ticketAssignments.findByTicketId(t.getId()).stream()
                    .filter(a -> Objects.equals(a.getUserId(), targetUserId))
                    .findFirst()
                    .ifPresent(
                            a -> {
                                a.setAssignedAt(assignedAt);
                                ticketAssignments.save(a);
                            });
        } else {
            TicketAssignment ta = new TicketAssignment();
            ta.setTicketId(t.getId());
            ta.setUserId(targetUserId);
            ta.setAssignedAt(assignedAt);
            ticketAssignments.save(ta);
        }

        t.setUpdatedAt(Instant.now());
        setUpdatedByActor(t, actor);
        if (reassign) {
            tickets.save(t);
            String assignNote =
                    reopenedForReassign
                            ? ("Ticket rouvert et r\u00e9assign\u00e9 \u00e0 " + target.getUsername())
                            : ("Ticket r\u00e9assign\u00e9 \u00e0 " + target.getUsername());
            ticketTimeline.log("UPDATED", id, actor, targetUserId, assignNote);
            notificationService.notifyTicketReassignedToYou(
                    targetUserId, ticketNumber(t), safe(actor), t.getId());
            return reopenedForReassign;
        } else {
            t.setStatus("ESCALATED");
            tickets.save(t);
            ticketTimeline.log(
                    "ESCALATED", id, actor, targetUserId, "Ticket escalad\u00e9 vers " + target.getUsername());
            return false;
        }
    }

    private static boolean ticketEligibleForReopenOnReassign(String statusUpperOrMixed) {
        String s = safe(statusUpperOrMixed).toUpperCase(Locale.ROOT);
        return "CLOSED".equals(s) || "RESOLVED".equals(s);
    }

    private void applyExternalEscalation(
            Ticket t, long id, long targetDirectionId, String actor, UserAccount actorUser, boolean relaxDirectionChecks) {
        Long srcDir = actorUser.getDirectionId();
        if (!relaxDirectionChecks) {
            if (srcDir == null) {
                throw new IllegalStateException("externalEscalationBadActor");
            }
            if (srcDir.longValue() == targetDirectionId) {
                throw new IllegalStateException("externalEscalationBadDirection");
            }
        } else if (srcDir != null && srcDir.longValue() == targetDirectionId) {
            throw new IllegalStateException("externalEscalationBadDirection");
        }
        Direction targetDir =
                directions.findById(targetDirectionId).orElseThrow(() -> new IllegalArgumentException("badDirection"));
        t.setAssignedTo(null);
        ticketAssignments.deleteByTicketId(t.getId());
        t.setStatus("ESCALATED");
        t.setExternalEscalationSourceDirectionId(srcDir);
        t.setExternalEscalationTargetDirectionId(targetDir.getId());
        t.setUpdatedAt(Instant.now());
        setUpdatedByActor(t, actor);
        tickets.save(t);
        ticketTimeline.log(
                "EXTERNAL_ESCALATION",
                id,
                actor,
                null,
                "Ticket escalad\u00e9 vers la direction " + safe(targetDir.getName()));
        notifyExternalEscalationPool(targetDirectionId, t, actor);
    }

    private static void ensureTicketOpenForEscalationActions(Ticket t) {
        String s = safe(t.getStatus()).toUpperCase(Locale.ROOT);
        if ("CLOSED".equals(s) || "RESOLVED".equals(s) || "MERGED".equals(s)) {
            throw new IllegalStateException("alreadyClosed");
        }
    }

    private void notifyExternalEscalationPool(Long targetDirectionId, Ticket t, String actorUsername) {
        String ref = ticketNumber(t);
        String msg = "Escalade externe — " + ref + " (depuis " + safe(actorUsername) + ")";
        notificationService.notifyDirectionStakeholders(
                targetDirectionId, "Escalade externe", msg, "ESCALATION", "TICKET", t.getId(), ref);
    }

    @Transactional
    public void closeTask(long id, String actor, boolean canManageAll) {
        AutomatedJob j = jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureTaskAllowed(j.getAssigneeUserId(), actor, canManageAll);
        ensureScheduledInstantReached(j, canManageAll);
        j.setActive(0);
        j.setStatus("CLOSED");
        j.setClosedAt(Instant.now());
        jobs.save(j);
        if (j.getCreatedBy() != null) {
            notificationService.notifyTaskClosedForCreator(
                    j.getCreatedBy(), j.getJobTitle(), safe(actor), j.getId());
        }
        notifyTaskLifecycleStakeholders(j, actor, true);
    }

    @Transactional
    public void startTask(long id, String actor, boolean canManageAll) {
        AutomatedJob j = jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureTaskAllowed(j.getAssigneeUserId(), actor, canManageAll);
        ensureScheduledInstantReached(j, canManageAll);
        j.setActive(1);
        j.setStatus("IN_PROGRESS");
        if (j.getStartedAt() == null) {
            j.setStartedAt(Instant.now());
        }
        jobs.save(j);
        if (j.getCreatedBy() != null) {
            notificationService.notifyTaskStartedForCreator(
                    j.getCreatedBy(), j.getJobTitle(), safe(actor), j.getId());
        }
        notifyTaskLifecycleStakeholders(j, actor, false);
    }

    @Transactional(readOnly = true)
    public TaskDetail taskDetail(long id, String actor, boolean canManageAll) {
        return taskDetail(id, actor, canManageAll, false);
    }

    @Transactional(readOnly = true)
    public TaskDetail taskDetail(long id, String actor, boolean canManageAll, boolean monitoringReader) {
        AutomatedJob j = jobs.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureTaskVisible(j, actor, canManageAll, monitoringReader);
        Map<Long, String> usernames = users.findAll().stream().collect(java.util.stream.Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (a, b) -> a));
        Ticket ticket = j.getTicketId() == null ? null : tickets.findById(j.getTicketId()).orElse(null);
        LinkedHashSet<String> workerNames = new LinkedHashSet<>();
        if (ticket != null) {
            ticketAssignments.findByTicketId(ticket.getId()).stream()
                    .map(TicketAssignment::getUserId)
                    .map(uid -> usernames.getOrDefault(uid, ""))
                    .filter(v -> v != null && !v.isBlank())
                    .forEach(workerNames::add);
            if (ticket.getAssignedTo() != null) {
                String nm = usernames.getOrDefault(ticket.getAssignedTo(), "");
                if (!nm.isBlank()) {
                    workerNames.add(nm);
                }
            }
        }
        List<String> workers = workerNames.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        List<TaskCommentRow> comments = taskComments.findByTaskIdOrderByCreatedAtDesc(j.getId()).stream()
                .map(c -> new TaskCommentRow(
                        c.getId(),
                        c.getAuthorUserId() == null ? "" : usernames.getOrDefault(c.getAuthorUserId(), ""),
                        safe(c.getCommentText()),
                        c.getAttachmentPath(),
                        formatInstant(c.getCreatedAt())))
                .toList();
        String linkedTicketNumber = ticket == null ? "" : ticketNumber(ticket);
        return new TaskDetail(
                j.getId(),
                safe(j.getJobTitle()),
                safe(j.getJobDescription()),
                safe(j.getStatus()),
                safe(j.getDueAt()),
                safe(j.getPriority()),
                splitAttachmentNames(j.getAttachmentPaths()),
                formatInstant(j.getStartedAt()),
                formatInstant(j.getClosedAt()),
                durationLabel(j.getStartedAt(), j.getClosedAt()),
                j.getTicketId(),
                linkedTicketNumber,
                j.getAssigneeUserId() == null ? "" : usernames.getOrDefault(j.getAssigneeUserId(), ""),
                workers,
                comments);
    }

    @Transactional
    public void addTaskComment(
            long taskId,
            String comment,
            List<MultipartFile> attachments,
            String actor,
            boolean canManageAll,
            String uploadsDirectory)
            throws IOException {
        AutomatedJob j = jobs.findById(taskId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureTaskVisible(j, actor, canManageAll, false);
        String text = comment == null ? "" : comment.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("commentRequired");
        }
        String attachmentPath = "";
        if (attachments != null && !attachments.isEmpty()) {
            Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("task-comments").resolve(String.valueOf(taskId));
            Files.createDirectories(dir);
            for (MultipartFile attachment : attachments) {
                if (attachment == null || attachment.isEmpty()) {
                    continue;
                }
                validateTaskCommentAttachment(attachment);
                String fn =
                        stripPathForUpload(attachment.getOriginalFilename() == null ? "attachment.bin" : attachment.getOriginalFilename());
                Path dest = dir.resolve(System.currentTimeMillis() + "_" + fn);
                Files.copy(attachment.getInputStream(), dest);
                attachmentPath = attachmentPath.isBlank() ? dest.toString() : (attachmentPath + ";;" + dest);
            }
        }
        TaskComment c = new TaskComment();
        c.setTaskId(j.getId());
        c.setAuthorUserId(users.findByUsernameIgnoreCase(actor).map(UserAccount::getId).orElse(null));
        c.setCommentText(text);
        c.setAttachmentPath(attachmentPath);
        c.setCreatedAt(Instant.now());
        taskComments.save(c);
    }

    @Transactional(readOnly = true)
    public Optional<Path> resolveTaskCommentAttachment(long commentId, int idx, String actor, boolean canManageAll, String uploadsDirectory) {
        return resolveTaskCommentAttachment(commentId, idx, actor, canManageAll, uploadsDirectory, false);
    }

    public Optional<Path> resolveTaskCommentAttachment(
            long commentId, int idx, String actor, boolean canManageAll, String uploadsDirectory, boolean monitoringReader) {
        TaskComment comment = taskComments.findById(commentId).orElse(null);
        if (comment == null) {
            return Optional.empty();
        }
        AutomatedJob j = jobs.findById(comment.getTaskId()).orElse(null);
        if (j == null) {
            return Optional.empty();
        }
        ensureTaskVisible(j, actor, canManageAll, monitoringReader);
        List<String> paths =
                comment.getAttachmentPath() == null || comment.getAttachmentPath().isBlank()
                        ? List.of()
                        : Arrays.stream(comment.getAttachmentPath().split(";;"))
                                .map(String::trim)
                                .filter(s -> !s.isBlank())
                                .toList();
        if (idx < 0 || idx >= paths.size()) {
            return Optional.empty();
        }
        Path base = Path.of(uploadsDirectory).toAbsolutePath().normalize();
        Path file = Path.of(paths.get(idx)).toAbsolutePath().normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private static void validateTaskCommentAttachment(MultipartFile f) {
        String name = stripPathForUpload(f.getOriginalFilename() == null ? "" : f.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_TASK_COMMENT_EXT.contains(ext)) {
            throw new IllegalArgumentException("attachmentType");
        }
    }

    private static String stripPathForUpload(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String s = name.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    @Transactional(readOnly = true)
    public String rawTaskAttachmentPaths(long taskId, String actor, boolean canManageAll) {
        return rawTaskAttachmentPaths(taskId, actor, canManageAll, false);
    }

    @Transactional(readOnly = true)
    public String rawTaskAttachmentPaths(long taskId, String actor, boolean canManageAll, boolean monitoringReader) {
        AutomatedJob j = jobs.findById(taskId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureTaskVisible(j, actor, canManageAll, monitoringReader);
        return j.getAttachmentPaths();
    }

    private void setUpdatedByActor(Ticket t, String actor) {
        if (actor == null || actor.isBlank()) {
            return;
        }
        users.findByUsernameIgnoreCase(actor).map(UserAccount::getId).ifPresent(t::setUpdatedBy);
    }

    private static String ticketNumber(Ticket t) {
        return (t.getTicketNumber() == null || t.getTicketNumber().isBlank()) ? "TCK-" + t.getId() : t.getTicketNumber();
    }

    private static String safe(String v) {
        return v == null ? "" : v;
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

    private void notifyTaskLifecycleStakeholders(AutomatedJob job, String actor, boolean closed) {
        if (job == null || job.getTicketId() == null) {
            return;
        }
        Ticket ticket = tickets.findById(job.getTicketId()).orElse(null);
        if (ticket == null) {
            return;
        }
        String ref = ticketNumber(ticket);
        String taskLabel = safe(job.getJobTitle()).isBlank() ? ("Tache #" + job.getId()) : safe(job.getJobTitle());
        String handler = safe(actor);
        String action = closed ? "cloturee" : "demarree";
        notificationService.notifyDirectionStakeholders(
                notificationDirectionId(ticket),
                closed ? "Tache cloturee" : "Tache demarree",
                "La tache \"" + taskLabel + "\" du ticket " + ref + " est " + action + " par " + handler + ".",
                "TASK",
                "TICKET",
                ticket.getId(),
                ref);
        notificationService.notifyLatestExternalEscalatorOnTicketEvent(
                ticket.getId(),
                "Mise a jour ticket escalade",
                "Le ticket " + ref + " (" + taskLabel + ") est " + action + " par " + handler + ".",
                ref,
                "ESCALATION");
    }

    private static String formatInstant(Instant instant) {
        return DisplayDateFormatter.formatInstant(instant);
    }

    private static String durationLabel(Instant start, Instant close) {
        if (start == null || close == null || close.isBefore(start)) {
            return "-";
        }
        long minutes = java.time.Duration.between(start, close).toMinutes();
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        if (days > 0) {
            return days + "d:" + hours + "H " + mins + " min";
        }
        if (hours > 0) {
            return hours + "H" + (mins > 0 ? ":" + mins + " min" : "");
        }
        return mins + " min";
    }

    private MyTicketRow toMyTicketRow(
            Ticket t,
            UserAccount viewer,
            RankPartition sameDir,
            RankPartition otherDir,
            Map<Long, String> sousDirectionNames) {
        List<SousDirectionPickRow> escSd = buildSousDirectionPicks(sameDir.escalateChoices(), sousDirectionNames);
        List<SousDirectionPickRow> reSd = buildSousDirectionPicks(sameDir.reassignChoices(), sousDirectionNames);
        boolean multiEscSd = escSd.size() > 1;
        boolean multiReSd = reSd.size() > 1;
        String st = safe(t.getStatus()).toUpperCase(Locale.ROOT);
        long openTicketTasks =
                jobs.findByTicketId(t.getId()).stream().filter(AutomatedJobProcessingService::isActiveOpenTask).count();
        boolean hasOpenTicketTasks = openTicketTasks > 0;
        boolean directTicketCloseAvailable =
                !hasOpenTicketTasks
                        && !"CLOSED".equals(st)
                        && !"CLOSE_REQUESTED".equals(st)
                        && !"MERGED".equals(st)
                        && ticketManagementService.mayCloseDirectlyWithoutReview(t.getId(), viewer.getId());
        boolean closureEscalationAvailable =
                !hasOpenTicketTasks
                        && !"CLOSED".equals(st)
                        && !"CLOSE_REQUESTED".equals(st)
                        && !"MERGED".equals(st)
                        && !directTicketCloseAvailable;
        boolean multiAssignCollaboration = ticketHasMultipleCollaborativeAssignees(t);
        List<EscalationChoice> closureSeniorChoices = List.of();
        if (closureEscalationAvailable && multiAssignCollaboration) {
            closureSeniorChoices =
                    ticketManagementService.listSeniorClosureDelegates(t.getId(), viewer.getUsername()).stream()
                            .map(p -> new EscalationChoice(p.id(), p.label(), p.sousDirectionId()))
                            .toList();
        }
        boolean reassignmentReopensTicket =
                ("CLOSED".equals(st) || "RESOLVED".equals(st)) && !"MERGED".equals(st);
        return new MyTicketRow(
                t.getId(),
                ticketNumber(t),
                safe(t.getTitle()),
                st,
                safe(t.getPriority()).toUpperCase(Locale.ROOT),
                formatInstant(t.getStartedAt()),
                formatInstant(t.getClosedAt()),
                durationLabel(t.getStartedAt(), t.getClosedAt()),
                escSd,
                reSd,
                multiEscSd,
                multiReSd,
                sameDir.escalateChoices(),
                sameDir.reassignChoices(),
                otherDir.escalateChoices(),
                otherDir.reassignChoices(),
                hasOpenTicketTasks,
                directTicketCloseAvailable,
                closureEscalationAvailable,
                multiAssignCollaboration,
                closureSeniorChoices,
                reassignmentReopensTicket);
    }

    private static List<SousDirectionPickRow> buildSousDirectionPicks(
            List<EscalationChoice> choices, Map<Long, String> sousDirectionNames) {
        LinkedHashMap<String, SousDirectionPickRow> map = new LinkedHashMap<>();
        for (EscalationChoice c : choices) {
            if (c.sousDirectionId() == null) {
                map.putIfAbsent("_", new SousDirectionPickRow(null, ""));
            } else {
                String key = String.valueOf(c.sousDirectionId());
                String label = sousDirectionNames.getOrDefault(c.sousDirectionId(), key);
                map.putIfAbsent(key, new SousDirectionPickRow(c.sousDirectionId(), label));
            }
        }
        return List.copyOf(map.values());
    }

    private static boolean isTicketMergedAway(Ticket t) {
        if (t.getMergedIntoTicketId() != null) {
            return true;
        }
        return "MERGED".equalsIgnoreCase(safe(t.getStatus()));
    }

    private static boolean isOpenWorkTask(AutomatedJob j) {
        return AutomatedJobProcessingService.isActiveOpenTask(j);
    }

    private static boolean isJobDueInstantReached(AutomatedJob j) {
        return AutomatedJobProcessingService.isDueInstantReached(j);
    }

    private static void ensureScheduledInstantReached(AutomatedJob j, boolean canManageAll) {
        if (canManageAll) {
            return;
        }
        if (j.getTicketId() != null) {
            return;
        }
        if (!isJobDueInstantReached(j)) {
            throw new IllegalStateException("notAllowed");
        }
    }

    private boolean actorHasOpenTaskOnTicket(Long ticketId, Long actorUserId) {
        return jobs.findByTicketIdAndAssigneeUserId(ticketId, actorUserId).stream().anyMatch(j -> isOpenWorkTask(j));
    }

    private static boolean isSeniorEscalationViewer(String normalizedRole) {
        return SENIOR_ESCALATION_ROLES.contains(normalizedRole);
    }

    /** Numeric rank for hierarchy: larger = more senior (escalation goes to strictly higher rank). */
    private static int roleRank(String normalizedRole) {
        if (normalizedRole == null || normalizedRole.isBlank()) {
            return 0;
        }
        return switch (normalizedRole) {
            case "VERIFICATEUR-ASSISTANT", "COURIER", "COURRIER" -> 10;
            case "VERIFICATEUR" -> 20;
            case "CONTROLEUR" -> 30;
            case "SECRETAIRE" -> 35;
            case "INSPECTEUR" -> 40;
            case "SOUS-DIRECTEUR" -> 50;
            case "DIRECTEUR" -> 60;
            case "ADMIN", "SUPER_ADMIN" -> 1000;
            default -> 15;
        };
    }

    private RankPartition partitionByRank(UserAccount actor, List<UserAccount> candidates) {
        String actorRole = RoleKeys.normalizeForScope(actor.getRole());
        int ar = roleRank(actorRole);
        List<EscalationChoice> escalate = new ArrayList<>();
        List<EscalationChoice> reassign = new ArrayList<>();
        for (UserAccount u : candidates) {
            String tr = RoleKeys.normalizeForScope(u.getRole());
            EscalationChoice c =
                    new EscalationChoice(u.getId(), u.getUsername() + " — " + tr, u.getSousDirectionId());
            if (roleRank(tr) > ar) {
                escalate.add(c);
            } else {
                reassign.add(c);
            }
        }
        Comparator<EscalationChoice> cmp = Comparator.comparing(EscalationChoice::label, String.CASE_INSENSITIVE_ORDER);
        escalate.sort(cmp);
        reassign.sort(cmp);
        return new RankPartition(escalate, reassign);
    }

    private RankPartition escalationSamePartitioned(UserAccount actor) {
        String actorRole = RoleKeys.normalizeForScope(actor.getRole());
        if (LOW_TIER_ESCALATOR_ROLES.contains(actorRole)) {
            return partitionByRank(actor, collectLowTierSameDirectionCandidates(actor));
        }
        if (SENIOR_ESCALATION_ROLES.contains(actorRole)) {
            return partitionByRank(actor, collectSeniorSameDirectionCandidates(actor));
        }
        if ("SECRETAIRE".equals(actorRole)) {
            return partitionByRank(actor, collectSecretaireSameDirectionCandidates(actor));
        }
        return new RankPartition(List.of(), List.of());
    }

    private RankPartition escalationOtherPartitioned(UserAccount actor) {
        String actorRole = RoleKeys.normalizeForScope(actor.getRole());
        if (SENIOR_ESCALATION_ROLES.contains(actorRole) || "SECRETAIRE".equals(actorRole)) {
            return partitionByRank(actor, collectSeniorOtherDirectionCandidates(actor));
        }
        return new RankPartition(List.of(), List.of());
    }

    /** Contrôleur / vérificateur: même sous-direction ou inspecteurs de la même direction. */
    private List<UserAccount> collectLowTierSameDirectionCandidates(UserAccount actor) {
        Long sd = actor.getSousDirectionId();
        Long dir = actor.getDirectionId();
        LinkedHashMap<Long, UserAccount> byId = new LinkedHashMap<>();
        for (UserAccount u : users.findAll()) {
            if (!u.isActiveBool() || u.getId().equals(actor.getId())) {
                continue;
            }
            String tr = RoleKeys.normalizeForScope(u.getRole());
            boolean sameSd =
                    sd != null
                            && sd.equals(u.getSousDirectionId())
                            && SAME_SOUS_DIRECTION_ESCALATION_ROLES.contains(tr);
            boolean inspectorSameDir = "INSPECTEUR".equals(tr) && dir != null && dir.equals(u.getDirectionId());
            if (!sameSd && !inspectorSameDir) {
                continue;
            }
            byId.putIfAbsent(u.getId(), u);
        }
        return List.copyOf(byId.values());
    }

    private List<UserAccount> collectSeniorSameDirectionCandidates(UserAccount actor) {
        Long dir = actor.getDirectionId();
        if (dir == null) {
            return List.of();
        }
        return users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> !u.getId().equals(actor.getId()))
                .filter(u -> dir.equals(u.getDirectionId()))
                .filter(u -> SENIOR_SAME_DIRECTION_RECEIVER_ROLES.contains(RoleKeys.normalizeForScope(u.getRole())))
                .toList();
    }

    private List<UserAccount> collectSecretaireSameDirectionCandidates(UserAccount actor) {
        Long dir = actor.getDirectionId();
        if (dir == null) {
            return List.of();
        }
        Set<String> targetRoles =
                Set.of("SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR", "VERIFICATEUR", "VERIFICATEUR-ASSISTANT", "DIRECTEUR");
        return users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> !u.getId().equals(actor.getId()))
                .filter(u -> dir.equals(u.getDirectionId()))
                .filter(u -> targetRoles.contains(RoleKeys.normalizeForScope(u.getRole())))
                .toList();
    }

    private List<UserAccount> collectSeniorOtherDirectionCandidates(UserAccount actor) {
        Long dir = actor.getDirectionId();
        if (dir == null) {
            return List.of();
        }
        return users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> !u.getId().equals(actor.getId()))
                .filter(u -> u.getDirectionId() != null && !dir.equals(u.getDirectionId()))
                .filter(u -> CROSS_DIRECTION_RECEIVER_ROLES.contains(RoleKeys.normalizeForScope(u.getRole())))
                .toList();
    }

    /** {@code assigned_to} + {@code ticket_assignments} — collaboration means ≥ 2 distinct users on the ticket row. */
    private boolean ticketHasMultipleCollaborativeAssignees(Ticket t) {
        if (t == null) {
            return false;
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
        return ids.size() >= 2;
    }

    private static boolean choiceListContains(List<EscalationChoice> choices, Long userId) {
        return choices.stream().anyMatch(c -> c.id().equals(userId));
    }

    private boolean isEscalateTargetAllowed(UserAccount actor, UserAccount target, boolean crossDirection) {
        RankPartition p = crossDirection ? escalationOtherPartitioned(actor) : escalationSamePartitioned(actor);
        return choiceListContains(p.escalateChoices(), target.getId());
    }

    private boolean isReassignTargetAllowed(UserAccount actor, UserAccount target, boolean crossDirection) {
        RankPartition p = crossDirection ? escalationOtherPartitioned(actor) : escalationSamePartitioned(actor);
        return choiceListContains(p.reassignChoices(), target.getId());
    }

    /**
     * Assignee / multi-assign row only (e.g. Démarrer).
     */
    private void ensureAllowed(Long ticketId, String actor, boolean canManageAll) {
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureMayActOnTicket(t, actor, canManageAll, false);
    }

    /**
     * @param allowSeniorOnEscalated when true, INSPECTEUR / SOUS-DIRECTEUR / DIRECTEUR may act on ESCALATED tickets
     *                                 visible in their direction scope (same pool as “Mes tickets” for seniors).
     */
    private boolean isPlannerLinkedJobDeferred(AutomatedJob job, Instant now) {
        Long tid = job.getTicketId();
        if (tid == null) {
            return false;
        }
        return tickets.findById(tid).map(ticket -> PlannerTicketVisibility.isHiddenFromOperationalViews(ticket, now)).orElse(false);
    }

    private void ensureMayActOnTicket(Ticket t, String actor, boolean canManageAll, boolean allowSeniorOnEscalated) {
        if (PlannerTicketVisibility.isHiddenFromOperationalViews(t, Instant.now())) {
            throw new IllegalStateException("notAllowed");
        }
        if (canManageAll) {
            return;
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalStateException("notAllowed");
        }
        Long actorId = users.findByUsernameIgnoreCase(actor).map(UserAccount::getId).orElse(null);
        if (actorId == null) {
            throw new IllegalStateException("notAllowed");
        }
        if (actorId.equals(t.getAssignedTo()) || ticketAssignments.existsByTicketIdAndUserId(t.getId(), actorId)) {
            return;
        }
        if (actorHasOpenTaskOnTicket(t.getId(), actorId)) {
            return;
        }
        if (allowSeniorOnEscalated) {
            UserAccount u = users.findById(actorId).orElseThrow(() -> new IllegalStateException("notAllowed"));
            String role = RoleKeys.normalizeForScope(u.getRole());
            if (isSeniorEscalationViewer(role)
                    && "ESCALATED".equalsIgnoreCase(safe(t.getStatus()))
                    && directionScopeService.canAccessTicket(u, t)) {
                return;
            }
        }
        throw new IllegalStateException("notAllowed");
    }

    public record MyWorkPage(
            List<MyTicketRow> myTickets,
            List<MissionService.MissionRow> myMissions,
            List<MyTaskRow> myTasks,
            List<DirectionPickRow> externalEscalationDirections,
            List<MyEscalationRow> escalations,
            List<TicketManagementService.PendingClosureTicketRow> closureReviews) {}

    /**
     * Données pour le menu Escalader / Réassigner (Mon travail + Gestion des tickets).
     */
    public record TicketEscalationMenu(
            List<DirectionPickRow> externalEscalationDirections,
            List<EscalationChoice> escalateChoicesSameDirection,
            List<EscalationChoice> reassignChoicesSameDirection,
            List<EscalationChoice> escalateChoicesOtherDirection,
            List<EscalationChoice> reassignChoicesOtherDirection,
            List<SousDirectionPickRow> escalateInternalSousDirections,
            List<SousDirectionPickRow> reassignInternalSousDirections,
            boolean multiSousDirectionEscalateInternal,
            boolean multiSousDirectionReassignInternal) {
        public static TicketEscalationMenu empty() {
            return new TicketEscalationMenu(
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false, false);
        }
    }

    private record RankPartition(List<EscalationChoice> escalateChoices, List<EscalationChoice> reassignChoices) {}

    public record MyTicketRow(
            long id,
            String ticketNumber,
            String title,
            String status,
            String priority,
            String startAt,
            String closeAt,
            String duration,
            List<SousDirectionPickRow> escalateInternalSousDirections,
            List<SousDirectionPickRow> reassignInternalSousDirections,
            boolean multiSousDirectionEscalateInternal,
            boolean multiSousDirectionReassignInternal,
            List<EscalationChoice> escalateChoicesSameDirection,
            List<EscalationChoice> reassignChoicesSameDirection,
            List<EscalationChoice> escalateChoicesOtherDirection,
            List<EscalationChoice> reassignChoicesOtherDirection,
            boolean hasOpenTicketTasks,
            boolean directTicketCloseAvailable,
            boolean closureEscalationAvailable,
            boolean multiAssignCollaboration,
            List<EscalationChoice> closureSeniorChoices,
            boolean reassignmentReopensTicket) {}

    public record DirectionPickRow(long id, String label) {}

    public record SousDirectionPickRow(Long id, String label) {}
    public record MyTaskRow(
            long id,
            String title,
            String status,
            String dueAt,
            String priority,
            List<AttachmentRef> attachments,
            String startAt,
            String closeAt,
            String duration,
            Long ticketId) {}
    public record MyEscalationRow(long id, String ticketNumber, String fromDirection, String toDirection, String sousDirection, String approver, String status) {}
    public record EscalationChoice(Long id, String label, Long sousDirectionId) {}
    public record AttachmentRef(int idx, String name) {}
    public record TaskCommentRow(Long id, String author, String text, String attachmentPath, String createdAt) {
        public List<String> attachmentPaths() {
            if (attachmentPath == null || attachmentPath.isBlank()) {
                return List.of();
            }
            return Arrays.stream(attachmentPath.split(";;"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
    }

    public record TaskDetail(
            Long id,
            String title,
            String description,
            String status,
            String dueAt,
            String priority,
            List<AttachmentRef> attachments,
            String startAt,
            String closeAt,
            String duration,
            Long ticketId,
            String linkedTicketNumber,
            String assignee,
            List<String> workers,
            List<TaskCommentRow> comments) {}

    private static List<AttachmentRef> splitAttachmentNames(String paths) {
        if (paths == null || paths.isBlank()) {
            return List.of();
        }
        java.util.ArrayList<AttachmentRef> out = new java.util.ArrayList<>();
        String[] arr = paths.split(";;");
        for (int i = 0; i < arr.length; i++) {
            String raw = arr[i].trim();
            if (raw.isBlank()) {
                continue;
            }
            String name;
            try {
                name = java.nio.file.Path.of(raw).getFileName().toString();
            } catch (Exception e) {
                name = raw;
            }
            out.add(new AttachmentRef(i, name));
        }
        return out;
    }

    private void ensureTaskAllowed(Long ownerUserId, String actor, boolean canManageAll) {
        if (canManageAll) {
            return;
        }
        Long actorId = users.findByUsernameIgnoreCase(actor).map(UserAccount::getId).orElse(null);
        if (actorId == null || ownerUserId == null || !ownerUserId.equals(actorId)) {
            throw new IllegalStateException("notAllowed");
        }
    }

    private void ensureTaskVisible(AutomatedJob job, String actor, boolean canManageAll, boolean monitoringReader) {
        if (canManageAll) {
            return;
        }
        Long actorId = users.findByUsernameIgnoreCase(actor).map(UserAccount::getId).orElse(null);
        if (actorId == null) {
            throw new IllegalStateException("notAllowed");
        }
        if (job.getAssigneeUserId() != null && job.getAssigneeUserId().equals(actorId)) {
            return;
        }
        if (job.getTicketId() != null && ticketAssignments.existsByTicketIdAndUserId(job.getTicketId(), actorId)) {
            return;
        }
        if (monitoringReader && ticketMonitoringService.canViewerSeeTaskInMonitoring(actor, job.getId())) {
            return;
        }
        throw new IllegalStateException("notAllowed");
    }
}
