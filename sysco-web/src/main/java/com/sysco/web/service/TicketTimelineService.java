package com.sysco.web.service;

import com.sysco.web.domain.TicketEvent;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.TicketEventRepository;
import com.sysco.web.repo.UserAccountRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketTimelineService {

    private final TicketEventRepository ticketEvents;
    private final UserAccountRepository users;
    private final NotificationService notifications;

    @Transactional
    public void log(String eventType, Long ticketId, String actorUsername, Long targetUserId, String note) {
        log(eventType, ticketId, actorUsername, targetUserId, note, true);
    }

    /**
     * @param notifyMovement when {@code false}, the event is stored but no in-app notification is sent (e.g. planificateur
     *     auto-ticket or JOB_DUE where the assignee already gets a dedicated scheduled-job notification).
     */
    @Transactional
    public void log(
            String eventType,
            Long ticketId,
            String actorUsername,
            Long targetUserId,
            String note,
            boolean notifyMovement) {
        if (ticketId == null || eventType == null || eventType.isBlank()) {
            return;
        }
        TicketEvent event = new TicketEvent();
        event.setTicketId(ticketId);
        event.setEventType(eventType.trim().toUpperCase(java.util.Locale.ROOT));
        event.setActorUserId(resolveUserId(actorUsername));
        event.setTargetUserId(targetUserId);
        event.setNote(note == null ? "" : note.trim());
        event.setCreatedAt(Instant.now());
        ticketEvents.save(event);
        if (notifyMovement) {
            notifications.notifyTicketMovement(ticketId, event.getEventType(), event.getNote());
        }
    }

    private Long resolveUserId(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return users.findByUsernameIgnoreCase(username).map(UserAccount::getId).orElse(null);
    }
}
