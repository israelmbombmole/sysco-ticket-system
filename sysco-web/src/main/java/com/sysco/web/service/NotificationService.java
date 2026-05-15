package com.sysco.web.service;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.NotificationItem;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.NotificationRepository;
import com.sysco.web.repo.TicketEventRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.util.DisplayDateFormatter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    /** All generated notification text uses French (product default for SYSCO web). */
    private static final Locale NOTIF_LOCALE = Locale.FRENCH;

    private static final DateTimeFormatter JOB_REMINDER_DUE_DISPLAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy \u00e0 HH:mm", Locale.FRENCH);
    private static final Set<String> DIRECTION_STAKEHOLDER_ROLES =
            Set.of("DIRECTEUR", "SOUS-DIRECTEUR", "INSPECTEUR");

    private static final Set<String> SOUS_DIRECTION_LEAD_ROLES = Set.of("DIRECTEUR", "SOUS-DIRECTEUR");

    private final NotificationRepository notifications;
    private final UserAccountRepository users;
    private final DirectionRepository directions;
    private final DirectionScopeService directionScopeService;
    private final TicketRepository tickets;
    private final TicketEventRepository ticketEvents;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageSource messageSource;

    private String tr(String code, Object... args) {
        return messageSource.getMessage(code, args, NOTIF_LOCALE);
    }

    @Transactional
    public void notifyTicketMovement(Long ticketId, String eventType, String note) {
        if (ticketId == null) {
            return;
        }
        Ticket t = tickets.findById(ticketId).orElse(null);
        if (t == null || t.getCreatedBy() == null) {
            return;
        }
        String ref = t.getTicketNumber() == null ? ("#" + t.getId()) : t.getTicketNumber().trim();
        String title = tr("notifications.movement.title", ref);
        String et = eventType == null ? "UPDATED" : eventType.trim().toUpperCase(Locale.ROOT);
        String eventLabel =
                messageSource.getMessage("notifications.timeline.event." + et, null, et, NOTIF_LOCALE);
        String notePart = note == null ? "" : note.trim();
        String message =
                notePart.isEmpty()
                        ? tr("notifications.movement.bodySimple", eventLabel)
                        : tr("notifications.movement.bodyWithNote", eventLabel, notePart);
        createAndPush(t.getCreatedBy(), title, message, "TICKET_MOVEMENT", "TICKET", t.getId(), ref);
    }

    @Transactional
    public void createAndPush(Long userId, String title, String message, String type, String targetType, Long targetId, String targetRef) {
        if (userId == null) {
            return;
        }
        NotificationItem n = new NotificationItem();
        n.setUserId(userId);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setTargetType(targetType);
        n.setTargetId(targetId);
        n.setTargetRef(targetRef);
        n.setIsRead(0);
        n.setCreatedAt(Instant.now());
        NotificationItem saved = notifications.save(n);
        String username = users.findById(userId).map(u -> u.getUsername()).orElse(null);
        if (username != null) {
            messagingTemplate.convertAndSendToUser(username, "/queue/notifications", toRow(saved));
        }
    }

    public void notifyTicketCreatorInProgress(Long createdBy, String ticketRef, String actor, Long ticketId) {
        if (createdBy == null) {
            return;
        }
        createAndPush(
                createdBy,
                tr("notifications.ticket.statusInProgress.title"),
                tr("notifications.ticket.statusInProgress.body", ticketRef, safe(actor)),
                "TICKET_MOVEMENT",
                "TICKET",
                ticketId,
                ticketRef);
    }

    public void notifyTicketReassignedToYou(Long targetUserId, String ticketRef, String actor, Long ticketId) {
        if (targetUserId == null) {
            return;
        }
        createAndPush(
                targetUserId,
                tr("notifications.ticket.reassigned.title"),
                tr("notifications.ticket.reassigned.body", ticketRef, safe(actor)),
                "TICKET_MOVEMENT",
                "TICKET",
                ticketId,
                ticketRef);
    }

    public void notifyTicketAssignedToYou(Long userId, String ticketRef, Long ticketId) {
        if (userId == null) {
            return;
        }
        createAndPush(
                userId,
                tr("notifications.ticket.assigned.title"),
                tr("notifications.ticket.assigned.body", ticketRef),
                "TICKET_MOVEMENT",
                "TICKET",
                ticketId,
                ticketRef);
    }

    public void notifyNewTaskOnTicket(Long assigneeId, String ticketRef, String jobTitle, Long ticketId) {
        if (assigneeId == null) {
            return;
        }
        createAndPush(
                assigneeId,
                tr("notifications.task.newAssigned.title"),
                tr("notifications.task.newAssigned.body", ticketRef, jobTitle == null ? "" : jobTitle),
                "TASK_CREATED",
                "TICKET",
                ticketId,
                ticketRef);
    }

    public void notifyTaskClosedForCreator(Long creatorId, String jobTitle, String actor, Long jobId) {
        if (creatorId == null) {
            return;
        }
        String title = jobTitle == null || jobTitle.isBlank() ? tr("notifications.job.unnamed") : jobTitle;
        createAndPush(
                creatorId,
                tr("notifications.task.status.title"),
                tr("notifications.task.status.closed", title, safe(actor)),
                "TASK",
                "TASK",
                jobId,
                String.valueOf(jobId));
    }

    public void notifyTaskStartedForCreator(Long creatorId, String jobTitle, String actor, Long jobId) {
        if (creatorId == null) {
            return;
        }
        String title = jobTitle == null || jobTitle.isBlank() ? tr("notifications.job.unnamed") : jobTitle;
        createAndPush(
                creatorId,
                tr("notifications.task.status.title"),
                tr("notifications.task.status.started", title, safe(actor)),
                "TASK",
                "TASK",
                jobId,
                String.valueOf(jobId));
    }

    public void notifyCloseReview(Long reviewerId, String ticketRef, String requestedBy, boolean escalatedTitle, Long ticketId) {
        if (reviewerId == null) {
            return;
        }
        String title =
                escalatedTitle
                        ? tr("notifications.ticket.closeReview.titleEscalated")
                        : tr("notifications.ticket.closeReview.title");
        createAndPush(
                reviewerId,
                title,
                tr("notifications.ticket.closeReview.body", safe(requestedBy), ticketRef),
                "TICKET_MOVEMENT",
                "TICKET",
                ticketId,
                ticketRef);
    }

    public void notifyExternalEscalationInbox(Long userId, String title, String message, Long ticketId, String ticketRef) {
        createAndPush(userId, title, message, "ESCALATION", "TICKET", ticketId, ticketRef);
    }

    public void notifyDirectionStakeholders(
            Long directionId,
            String title,
            String message,
            String type,
            String targetType,
            Long targetId,
            String targetRef) {
        if (directionId == null) {
            return;
        }
        Long handlingSousDirectionId =
                directions.findById(directionId).map(Direction::getSousDirectionId).orElse(null);

        Set<Long> notified = new LinkedHashSet<>();
        users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> directionId.equals(u.getDirectionId()))
                .filter(u -> DIRECTION_STAKEHOLDER_ROLES.contains(RoleKeys.normalizeForScope(u.getRole())))
                .map(UserAccount::getId)
                .distinct()
                .forEach(uid -> {
                    notified.add(uid);
                    createAndPush(uid, title, message, type, targetType, targetId, targetRef);
                });

        if (handlingSousDirectionId != null) {
            users.findAll().stream()
                    .filter(UserAccount::isActiveBool)
                    .filter(u -> SOUS_DIRECTION_LEAD_ROLES.contains(RoleKeys.normalizeForScope(u.getRole())))
                    .filter(u -> handlingSousDirectionId.equals(directionScopeService.effectiveSousDirectionId(u)))
                    .map(UserAccount::getId)
                    .distinct()
                    .filter(uid -> !notified.contains(uid))
                    .forEach(uid -> createAndPush(uid, title, message, type, targetType, targetId, targetRef));
        }
    }

    public void notifyLatestExternalEscalatorOnTicketEvent(
            Long ticketId, String title, String message, String ticketRef, String type) {
        if (ticketId == null) {
            return;
        }
        Long escalatorId = ticketEvents
                .findTopByTicketIdAndEventTypeOrderByCreatedAtDesc(ticketId, "EXTERNAL_ESCALATION")
                .map(com.sysco.web.domain.TicketEvent::getActorUserId)
                .orElse(null);
        if (escalatorId == null) {
            return;
        }
        createAndPush(escalatorId, title, message, type, "TICKET", ticketId, ticketRef);
    }

    public void notifyScheduledJobDue(
            Long assigneeId,
            String taskTitle,
            String ticketSuffix,
            Long ticketId,
            Long jobId,
            String targetRef,
            boolean ticketLinked) {
        if (assigneeId == null) {
            return;
        }
        String safeTitle = taskTitle == null || taskTitle.isBlank() ? tr("notifications.job.unnamed") : taskTitle;
        String suffix = ticketSuffix == null ? "" : ticketSuffix;
        createAndPush(
                assigneeId,
                tr("notifications.job.due.title"),
                tr("notifications.job.due.message", safeTitle, suffix),
                "TASK",
                ticketLinked ? "TICKET" : "JOB",
                ticketLinked ? ticketId : jobId,
                targetRef);
    }

    public void notifyScheduledJobReminder(
            Long assigneeId,
            String taskTitle,
            LocalDateTime dueLdt,
            String ticketSuffix,
            Long ticketId,
            Long jobId,
            String targetRef,
            boolean ticketLinked) {
        if (assigneeId == null) {
            return;
        }
        String safeTitle = taskTitle == null || taskTitle.isBlank() ? tr("notifications.job.unnamed") : taskTitle;
        String dueStr = dueLdt == null ? "" : JOB_REMINDER_DUE_DISPLAY.format(dueLdt);
        String suffix = ticketSuffix == null ? "" : ticketSuffix;
        createAndPush(
                assigneeId,
                tr("notifications.job.reminder.title"),
                tr("notifications.job.reminder.message", safeTitle, dueStr, suffix),
                "TASK",
                ticketLinked ? "TICKET" : "JOB",
                ticketLinked ? ticketId : jobId,
                targetRef);
    }

    public void notifyCourierArrivedInDirection(Long userId, String courierRef, String directionName, Long packetId, String ref) {
        if (userId == null) {
            return;
        }
        createAndPush(
                userId,
                tr("notifications.courier.arrived.title"),
                tr("notifications.courier.arrived.body", courierRef, directionName == null ? "" : directionName),
                "COURIER",
                "COURIER",
                packetId,
                ref);
    }

    /**
     * Notifies active {@code DIRECTEUR} and {@code SOUS-DIRECTEUR} tied to the direction (and its linked sous-direction),
     * excluding inspecteurs and secretaries — used when a courier packet is routed to a direction.
     */
    public void notifyCourierInboundToDirectionLeads(
            Long directionId, String courierRef, String directionDisplayName, Long packetId, String packetRef) {
        if (directionId == null) {
            return;
        }
        String ref = packetRef == null || packetRef.isBlank() ? ("#" + packetId) : packetRef.trim();
        String cpRef = courierRef == null || courierRef.isBlank() ? ref : courierRef.trim();
        String dirLabel = directionDisplayName == null ? "" : directionDisplayName.trim();
        for (Long uid : directeurEtSousDirecteurRecipientIds(directionId, null)) {
            notifyCourierArrivedInDirection(uid, cpRef, dirLabel, packetId, ref);
        }
    }

    /**
     * Notifies direction leads (and sous-directeurs scoped to the packet sous-direction when provided) that an internal
     * ticket was created from a courier workflow. Uses ticket target so the UI can open ticket management.
     */
    public void notifyCourierTicketCreatedForDirectionLeads(
            Long directionId,
            Long packetTargetSousDirectionId,
            Long ticketId,
            String ticketRef,
            String courierRef,
            Long excludeUserId) {
        if (directionId == null || ticketId == null) {
            return;
        }
        String ref = ticketRef == null || ticketRef.isBlank() ? ("#" + ticketId) : ticketRef.trim();
        String cpRef = courierRef == null ? "" : courierRef.trim();
        String title = tr("notifications.courier.ticketCreated.title");
        String message = tr("notifications.courier.ticketCreated.body", ref, cpRef);
        for (Long uid : directeurEtSousDirecteurRecipientIds(directionId, packetTargetSousDirectionId)) {
            if (excludeUserId != null && excludeUserId.equals(uid)) {
                continue;
            }
            createAndPush(uid, title, message, "TICKET_MOVEMENT", "TICKET", ticketId, ref);
        }
    }

    /** Active directeurs / sous-directeurs for the direction row plus leads tied to its linked sous-direction. */
    private Set<Long> directeurEtSousDirecteurRecipientIds(Long directionId, Long packetTargetSousDirectionId) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (directionId == null) {
            addSousDirecteursForSousDirection(packetTargetSousDirectionId, recipients);
            return recipients;
        }
        Long handlingSousDirectionId =
                directions.findById(directionId).map(Direction::getSousDirectionId).orElse(null);

        users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> directionId.equals(u.getDirectionId()))
                .filter(u -> directeurOrSousDirecteur(u))
                .map(UserAccount::getId)
                .filter(Objects::nonNull)
                .forEach(recipients::add);

        if (handlingSousDirectionId != null) {
            users.findAll().stream()
                    .filter(UserAccount::isActiveBool)
                    .filter(this::directeurOrSousDirecteur)
                    .filter(u -> handlingSousDirectionId.equals(directionScopeService.effectiveSousDirectionId(u)))
                    .map(UserAccount::getId)
                    .filter(Objects::nonNull)
                    .forEach(recipients::add);
        }

        addSousDirecteursForSousDirection(packetTargetSousDirectionId, recipients);
        return recipients;
    }

    private void addSousDirecteursForSousDirection(Long sousDirectionId, Set<Long> recipients) {
        if (sousDirectionId == null) {
            return;
        }
        users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> "SOUS-DIRECTEUR".equals(RoleKeys.normalizeForScope(u.getRole())))
                .filter(u -> sousDirectionId.equals(directionScopeService.effectiveSousDirectionId(u)))
                .map(UserAccount::getId)
                .filter(Objects::nonNull)
                .forEach(recipients::add);
    }

    private boolean directeurOrSousDirecteur(UserAccount u) {
        String rk = RoleKeys.normalizeForScope(u.getRole());
        return "DIRECTEUR".equals(rk) || "SOUS-DIRECTEUR".equals(rk);
    }

    /**
     * In-app notification to sous-directeur(s) of the reporter's sous-direction when that user logs a ticket (external or internal flow).
     */
    @Transactional
    public void notifyReporterSousDirecteursTicketLogged(
            Long reporterUserId,
            String reporterUsername,
            Long reporterSousDirectionId,
            Long ticketId,
            String ticketRef) {
        if (reporterUserId == null || reporterSousDirectionId == null || ticketId == null) {
            return;
        }
        List<UserAccount> recipients = users.findActiveSousDirecteursForSousDirection(reporterSousDirectionId);
        if (recipients.isEmpty()) {
            return;
        }
        String ref =
                ticketRef == null || ticketRef.isBlank()
                        ? ("#" + ticketId)
                        : ticketRef.trim();
        String displayName =
                reporterUsername == null || reporterUsername.isBlank()
                        ? ("user-" + reporterUserId)
                        : reporterUsername.trim();
        String title = tr("notifications.ticket.reporterSdLogged.title");
        String message = tr("notifications.ticket.reporterSdLogged.body", displayName, ref);
        for (UserAccount sd : recipients) {
            if (sd == null || sd.getId() == null || sd.getId().equals(reporterUserId)) {
                continue;
            }
            createAndPush(sd.getId(), title, message, "TICKET_MOVEMENT", "TICKET", ticketId, ref);
        }
    }

    /**
     * In-app notification to sous-directeur(s) of the given sous-direction when an agent punches arrival or departure (Ma garde).
     */
    @Transactional
    public void notifyAttendancePunchToSousDirecteurs(
            Long punchingUserId,
            String punchingUsername,
            boolean arrival,
            Long attendanceRecordId,
            Long agentSousDirectionId) {
        if (punchingUserId == null || agentSousDirectionId == null || attendanceRecordId == null) {
            return;
        }
        List<UserAccount> recipients =
                users.findActiveSousDirecteursForSousDirection(agentSousDirectionId);
        if (recipients.isEmpty()) {
            return;
        }
        String displayName =
                punchingUsername == null || punchingUsername.isBlank() ? ("user-" + punchingUserId) : punchingUsername.trim();
        String title =
                arrival
                        ? tr("notifications.attendance.arrival.title")
                        : tr("notifications.attendance.departure.title");
        String message =
                arrival
                        ? tr("notifications.attendance.arrival.body", displayName)
                        : tr("notifications.attendance.departure.body", displayName);
        String type = "MY_SHIFT_ATTENDANCE";
        String targetRef = "ATT-" + attendanceRecordId;
        for (UserAccount sd : recipients) {
            if (sd == null || sd.getId() == null || sd.getId().equals(punchingUserId)) {
                continue;
            }
            createAndPush(sd.getId(), title, message, type, "ATTENDANCE", attendanceRecordId, targetRef);
        }
    }

    @Transactional(readOnly = true)
    public NotificationView page(String username) {
        Long userId = users.findByUsernameIgnoreCase(username).map(u -> u.getId()).orElse(null);
        if (userId == null) {
            return new NotificationView(List.of(), 0);
        }
        List<NotificationRow> rows = notifications.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toRow)
                .toList();
        long unread = notifications.countByUserIdAndIsRead(userId, 0);
        return new NotificationView(rows, unread);
    }

    @Transactional
    public void markRead(Long id, String username) {
        Long userId = users.findByUsernameIgnoreCase(username).map(u -> u.getId()).orElse(null);
        if (userId == null) {
            return;
        }
        notifications.findById(id).ifPresent(n -> {
            if (userId.equals(n.getUserId())) {
                n.setIsRead(1);
                notifications.save(n);
            }
        });
    }

    @Transactional
    public void deleteNotification(Long id, String username) {
        Long userId = users.findByUsernameIgnoreCase(username).map(u -> u.getId()).orElse(null);
        if (userId == null) {
            return;
        }
        notifications
                .findById(id)
                .filter(n -> userId.equals(n.getUserId()))
                .ifPresent(notifications::delete);
    }

    private NotificationRow toRow(NotificationItem n) {
        String type = n.getType() == null ? "" : n.getType().trim().toUpperCase(Locale.ROOT);
        String iconClass =
                switch (type) {
                    case "TICKET_MOVEMENT" -> "notif-icon-ticket";
                    case "TASK_CREATED", "TASK" -> "notif-icon-task";
                    case "ESCALATION", "ESCALATED" -> "notif-icon-escalation";
                    case "CHAT" -> "notif-icon-chat";
                    case "DATASHARE_OTP", "DATASHARE_ACCESS" -> "notif-icon-share";
                    case "COURIER" -> "notif-icon-generic";
                    case "FILE_SHARE_MGMT_OTP" -> "notif-icon-share";
                    case "MY_SHIFT_ATTENDANCE" -> "notif-icon-generic";
                    default -> "notif-icon-generic";
                };
        String typeForKey = type.isEmpty() ? "default" : type;
        String typeLabel =
                messageSource.getMessage(
                        "notifications.type." + typeForKey,
                        null,
                        type.isEmpty() ? tr("notifications.type.default") : type,
                        NOTIF_LOCALE);
        String createdAt = formatInstant(n.getCreatedAt());
        String relative = relativeFromNow(n.getCreatedAt());
        return new NotificationRow(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                typeLabel,
                iconClass,
                n.getTargetType(),
                n.getTargetId(),
                n.getTargetRef(),
                n.getIsRead() != null && n.getIsRead() == 1,
                createdAt,
                relative);
    }

    private static String formatInstant(Instant createdAt) {
        return DisplayDateFormatter.formatInstant(createdAt);
    }

    private String relativeFromNow(Instant createdAt) {
        if (createdAt == null) {
            return "";
        }
        long sec = Math.max(1, java.time.Duration.between(createdAt, Instant.now()).getSeconds());
        if (sec < 60) {
            return tr("notifications.rel.seconds", sec);
        }
        long min = sec / 60;
        if (min < 60) {
            return tr("notifications.rel.minutes", min);
        }
        long hr = min / 60;
        if (hr < 24) {
            return tr("notifications.rel.hours", hr);
        }
        long day = hr / 24;
        return tr("notifications.rel.days", day);
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    public record NotificationView(List<NotificationRow> rows, long unreadCount) {}

    public record NotificationRow(
            Long id,
            String title,
            String message,
            String type,
            String typeLabel,
            String iconClass,
            String targetType,
            Long targetId,
            String targetRef,
            boolean read,
            String createdAt,
            String relativeTime) {}
}
