package com.sysco.web.service;

import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.repo.CourierPacketRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MyActivityService {

    private final UserAccountRepository users;
    private final TicketRepository tickets;
    private final TicketAssignmentRepository ticketAssignments;
    private final CourierPacketRepository couriers;
    private final DataShareService dataShareService;

    public MyActivityPage page(String username, String ticketQ, String fileQ, boolean allowTicketSelfEdit) {
        if (username == null || username.isBlank()) {
            return new MyActivityPage(List.of(), List.of(), List.of(), ticketQ == null ? "" : ticketQ, fileQ == null ? "" : fileQ);
        }
        var userOpt = users.findByUsernameIgnoreCase(username);
        if (userOpt.isEmpty()) {
            return new MyActivityPage(List.of(), List.of(), List.of(), ticketQ == null ? "" : ticketQ, fileQ == null ? "" : fileQ);
        }
        var user = userOpt.get();
        String tq = ticketQ == null ? "" : ticketQ.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        List<MyTicketRow> myTickets = tickets.loadRecentTicketRowsByCreator(user.getId(), PageRequest.of(0, 50)).stream()
                .filter(t -> tq.isBlank()
                        || contains(t.ticketNumber(), tq)
                        || contains(t.title(), tq)
                        || contains(t.status(), tq))
                .map(t -> {
                    var full = tickets.findById(t.id()).orElse(null);
                    if (full != null && PlannerTicketVisibility.isHiddenFromOperationalViews(full, now)) {
                        return null;
                    }
                    boolean creator =
                            full != null
                                    && full.getCreatedBy() != null
                                    && full.getCreatedBy().equals(user.getId());
                    String st = full == null || full.getStatus() == null ? "" : full.getStatus().trim();
                    boolean open = "OPEN".equalsIgnoreCase(st);
                    boolean unassigned =
                            full != null
                                    && full.getAssignedTo() == null
                                    && ticketAssignments.findByTicketId(t.id()).isEmpty();
                    boolean mergedInto = full != null && full.getMergedIntoTicketId() != null;
                    boolean blockedEdit = blockedEditStatus(st);
                    boolean canEdit = creator && allowTicketSelfEdit && !blockedEdit;
                    boolean canDelete = creator && allowTicketSelfEdit && open && unassigned;
                    boolean canMerge =
                            creator
                                    && allowTicketSelfEdit
                                    && !open
                                    && !mergedInto
                                    && mergeEligibleForCreatorMerge(st);
                    return new MyTicketRow(t.id(), t.ticketNumber(), t.title(), t.status(), canEdit, canDelete, canMerge);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        List<MyCourierRow> myCouriers = couriers.findTop50ByCreatedByOrderByCreatedAtDesc(user.getId()).stream()
                .map(c -> {
                    String st = c.getStatus() == null ? "" : c.getStatus().trim();
                    boolean canEdit = isCourierEditableStatus(st);
                    boolean canDelete = canEdit;
                    return new MyCourierRow(
                            c.getId(),
                            c.getRefCode() == null ? "" : c.getRefCode(),
                            c.getTitle() == null ? "" : c.getTitle(),
                            st,
                            c.getCreatedAt() == null ? "" : com.sysco.web.util.DisplayDateFormatter.formatInstant(c.getCreatedAt()),
                            canEdit,
                            canDelete);
                })
                .toList();
        var myFiles = dataShareService.sharedByUser(username, fileQ);
        return new MyActivityPage(myTickets, myCouriers, myFiles, ticketQ == null ? "" : ticketQ, fileQ == null ? "" : fileQ);
    }

    @Transactional
    public void deleteOwnUnassignedTicket(String username, Long ticketId) {
        var actor = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("auth"));
        var ticket = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        if (ticket.getCreatedBy() == null || !ticket.getCreatedBy().equals(actor.getId())) {
            throw new IllegalStateException("notAllowed");
        }
        if (ticket.getAssignedTo() != null || !ticketAssignments.findByTicketId(ticketId).isEmpty()) {
            throw new IllegalStateException("alreadyAssigned");
        }
        String st = ticket.getStatus() == null ? "" : ticket.getStatus().trim();
        if (!"OPEN".equalsIgnoreCase(st)) {
            throw new IllegalStateException("notAllowed");
        }
        tickets.delete(ticket);
    }

    public void deleteOwnSharedFile(Long fileId, String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("auth");
        }
        dataShareService.deleteSenderShare(fileId, username);
    }

    public String regenerateSharedFileOtp(Long fileId, String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("auth");
        }
        return dataShareService.regenerateOtpForSender(fileId, username);
    }

    public void replaceSharedFile(Long fileId, String username, MultipartFile file) throws IOException {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("auth");
        }
        dataShareService.replaceSharedFile(fileId, username, file);
    }

    public void updateSharedFileVisibility(
            Long fileId, String username, Integer visibilityMinutes, String visibleUntilEndRaw) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("auth");
        }
        LocalDateTime visibleUntilEnd = parseVisibleUntilEnd(visibleUntilEndRaw);
        dataShareService.updateVisibility(fileId, username, visibilityMinutes, visibleUntilEnd);
    }

    private static LocalDateTime parseVisibleUntilEnd(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("badDateTime");
        }
    }

    private static boolean contains(String v, String q) {
        return v != null && v.toLowerCase(Locale.ROOT).contains(q);
    }

    private static boolean blockedEditStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return "MERGED".equals(s)
                || "CLOSED".equals(s)
                || "CLOSE_REQUESTED".equals(s)
                || "RESOLVED".equals(s);
    }

    /** Aligns with {@code TicketManagementService.ensureTicketEligibleForMerge}: not already merged away; OPEN uses delete instead. */
    private static boolean mergeEligibleForCreatorMerge(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return !"MERGED".equals(s);
    }

    private static boolean isCourierEditableStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return "AWAITING_DIRECTION".equals(s) || "REGISTERED".equals(s);
    }

    public record MyActivityPage(
            List<MyTicketRow> myTickets,
            List<MyCourierRow> myCouriers,
            List<DataShareService.SharedFileRow> mySharedFiles,
            String ticketQ,
            String fileQ) {}

    public record MyTicketRow(
            Long id,
            String ticketNumber,
            String title,
            String status,
            boolean canEdit,
            boolean canDelete,
            boolean canMerge) {}

    public record MyCourierRow(
            Long id,
            String refCode,
            String title,
            String status,
            String createdAt,
            boolean canEdit,
            boolean canDelete) {}
}
