package com.sysco.web.security;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.TicketAssignment;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.UserAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DirectionScopeService {

    private final UserAccountRepository users;
    private final TicketAssignmentRepository ticketAssignments;
    private final DirectionRepository directions;

    public UserAccount requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("auth");
        }
        return users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalStateException("auth"));
    }

    public boolean isSuperAdmin(UserAccount user) {
        return user != null && "SUPER_ADMIN".equalsIgnoreCase(RoleKeys.normalizeForScope(user.getRole()));
    }

    public boolean canAccessUser(UserAccount viewer, Long userId) {
        if (viewer == null || userId == null) {
            return false;
        }
        if (isSuperAdmin(viewer)) {
            return true;
        }
        if (viewer.getId() != null && viewer.getId().equals(userId)) {
            return true;
        }
        UserAccount target = users.findById(userId).orElse(null);
        if (target == null) {
            return false;
        }
        if (viewer.getDirectionId() != null
                && target.getDirectionId() != null
                && viewer.getDirectionId().equals(target.getDirectionId())) {
            return true;
        }
        if (isDirectionLeadership(viewer)) {
            Long vsd = effectiveSousDirectionId(viewer);
            Long tsd = effectiveSousDirectionId(target);
            return vsd != null && vsd.equals(tsd);
        }
        return false;
    }

    public boolean canAccessTicket(UserAccount viewer, Ticket ticket) {
        if (viewer == null || ticket == null) {
            return false;
        }
        if (isSuperAdmin(viewer)) {
            return true;
        }
        if (canAccessUser(viewer, ticket.getCreatedBy())) {
            return true;
        }
        if (canAccessUser(viewer, ticket.getAssignedTo())) {
            return true;
        }
        boolean assignedToAccessibleUser = ticketAssignments.findByTicketId(ticket.getId()).stream()
                .anyMatch(ta -> canAccessUser(viewer, ta.getUserId()));
        if (assignedToAccessibleUser) {
            return true;
        }
        if (ticket.getExternalEscalationTargetDirectionId() != null
                && viewer.getDirectionId() != null
                && ticket.getExternalEscalationTargetDirectionId().equals(viewer.getDirectionId())
                && "ESCALATED".equalsIgnoreCase(trim(ticket.getStatus()))) {
            String role = RoleKeys.normalizeForScope(viewer.getRole());
            if ("SECRETAIRE".equals(role)
                    || "SOUS-DIRECTEUR".equals(role)
                    || "DIRECTEUR".equals(role)) {
                return true;
            }
        }
        if (canAccessUser(viewer, ticket.getUpdatedBy())) {
            return true;
        }
        if (isDirectionLeadership(viewer)) {
            Long vsd = effectiveSousDirectionId(viewer);
            if (vsd != null && ticketTouchesSousDirection(ticket, vsd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shared organisational sous-direction: either stored on the user row or derived from their handling direction row.
     */
    public Long effectiveSousDirectionId(UserAccount u) {
        if (u == null) {
            return null;
        }
        if (u.getSousDirectionId() != null) {
            return u.getSousDirectionId();
        }
        if (u.getDirectionId() == null) {
            return null;
        }
        return directions.findById(u.getDirectionId()).map(Direction::getSousDirectionId).orElse(null);
    }

    private boolean ticketTouchesSousDirection(Ticket ticket, Long sousDirectionId) {
        if (ticket == null || sousDirectionId == null) {
            return false;
        }
        if (ticket.getReporterSousDirectionId() != null
                && sousDirectionId.equals(ticket.getReporterSousDirectionId())) {
            return true;
        }
        if (ticket.getHandlingDirectionId() != null) {
            Long hsd =
                    directions
                            .findById(ticket.getHandlingDirectionId())
                            .map(Direction::getSousDirectionId)
                            .orElse(null);
            if (sousDirectionId.equals(hsd)) {
                return true;
            }
        }
        if (ticket.getCreatedBy() != null) {
            UserAccount c = users.findById(ticket.getCreatedBy()).orElse(null);
            if (c != null && sousDirectionId.equals(effectiveSousDirectionId(c))) {
                return true;
            }
        }
        if (ticket.getAssignedTo() != null) {
            UserAccount a = users.findById(ticket.getAssignedTo()).orElse(null);
            if (a != null && sousDirectionId.equals(effectiveSousDirectionId(a))) {
                return true;
            }
        }
        return ticketAssignments.findByTicketId(ticket.getId()).stream()
                        .map(TicketAssignment::getUserId)
                        .map(users::findById)
                        .flatMap(Optional::stream)
                        .anyMatch(u -> sousDirectionId.equals(effectiveSousDirectionId(u)));
    }

    private static boolean isDirectionLeadership(UserAccount viewer) {
        if (viewer == null || viewer.getRole() == null) {
            return false;
        }
        String r = RoleKeys.normalizeForScope(viewer.getRole());
        return "SOUS-DIRECTEUR".equals(r) || "DIRECTEUR".equals(r);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
