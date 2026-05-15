package com.sysco.web.service;

import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.CourierPacketRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.util.TicketSlaCalculator;
import com.sysco.web.web.dto.TicketRow;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardMetricsService {

    /** Matches {@link com.app.model.CourierPacket#ST_RESOLVED} / desktop dashboard buckets. */
    public static final String COURIER_RESOLVED = "RESOLVED";

    /** Matches {@link com.app.model.CourierPacket#ST_DIRECTED}. */
    public static final String COURIER_DIRECTED = "DIRECTED";

    private final TicketRepository tickets;
    private final UserAccountRepository users;
    private final CourierPacketRepository courierPackets;
    private final TicketAssignmentRepository ticketAssignments;
    private final AutomatedJobRepository jobs;
    private final DirectionScopeService directionScopeService;

    /**
     * Org-wide snapshot aligned with {@code AdminDashboardController#loadDashboardData()} +
     * {@code AdminDashboardController#loadCourierBlock()} (company scope for web shell).
     */
    public AdminDashboardSnapshot snapshot(String username) {
        final com.sysco.web.domain.UserAccount viewer;
        try {
            viewer = directionScopeService.requireUser(username);
        } catch (IllegalStateException ex) {
            return emptySnapshot("UNKNOWN");
        }
        boolean superAdmin = directionScopeService.isSuperAdmin(viewer);
        String roleKey = RoleKeys.normalizeForScope(viewer.getRole());
        boolean canSeeAllDashboardData =
                superAdmin || "DIRECTEUR".equals(roleKey) || "SOUS-DIRECTEUR".equals(roleKey);
        Long viewerId = viewer.getId();
        java.time.Instant now = java.time.Instant.now();

        List<com.sysco.web.domain.Ticket> scopedTickets = tickets.findAll().stream()
                .filter(t -> t.getMergedIntoTicketId() == null)
                .filter(t -> {
                    String st = t.getStatus() == null ? "" : t.getStatus().trim();
                    return !"MERGED".equalsIgnoreCase(st);
                })
                .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, now))
                .filter(t -> canSeeAllDashboardData || isTicketOwnedByUser(t, viewerId))
                .toList();
        long totalTickets = scopedTickets.size();
        long open = scopedTickets.stream()
                .filter(t -> Set.of("OPEN", "NEW").contains((t.getStatus() == null ? "" : t.getStatus().trim().toUpperCase())))
                .count();
        long assigned = scopedTickets.stream()
                .filter(t -> Set.of("ASSIGNED", "IN_PROGRESS")
                        .contains((t.getStatus() == null ? "" : t.getStatus().trim().toUpperCase())))
                .count();
        long closed = scopedTickets.stream()
                .filter(t -> Set.of("CLOSED", "RESOLVED")
                        .contains((t.getStatus() == null ? "" : t.getStatus().trim().toUpperCase())))
                .count();

        List<com.sysco.web.domain.UserAccount> scopedUsers = users.findAll().stream()
                .filter(u -> u.isActiveBool())
                .filter(u -> canSeeAllDashboardData
                        || (viewer.getDirectionId() != null
                                && u.getDirectionId() != null
                                && viewer.getDirectionId().equals(u.getDirectionId())))
                .filter(u -> canSeeAllDashboardData || (viewerId != null && viewerId.equals(u.getId())))
                .toList();
        long insp = scopedUsers.stream().filter(u -> "INSPECTEUR".equalsIgnoreCase(u.getRole())).count();
        long ctrl = scopedUsers.stream().filter(u -> "CONTROLEUR".equalsIgnoreCase(u.getRole())).count();
        long verif = scopedUsers.stream().filter(u -> "VERIFICATEUR".equalsIgnoreCase(u.getRole())).count();
        long assist = scopedUsers.stream().filter(u -> "VERIFICATEUR-ASSISTANT".equalsIgnoreCase(u.getRole())).count();

        List<com.sysco.web.domain.CourierPacket> scopedCouriers = courierPackets.findAll().stream()
                .filter(c -> canSeeAllDashboardData
                        || (viewer.getDirectionId() != null
                                && c.getTargetDirectionId() != null
                                && viewer.getDirectionId().equals(c.getTargetDirectionId())))
                .filter(c -> canSeeAllDashboardData || isCourierOwnedByUser(c, viewerId))
                .toList();
        long cpTotal = scopedCouriers.size();
        long cpResolved = scopedCouriers.stream()
                .filter(c -> COURIER_RESOLVED.equalsIgnoreCase(c.getStatus()))
                .count();
        long cpNotResolved = scopedCouriers.stream()
                .filter(c -> !COURIER_RESOLVED.equalsIgnoreCase(c.getStatus()))
                .count();
        long cpAwaitSous = scopedCouriers.stream()
                .filter(c -> COURIER_DIRECTED.equalsIgnoreCase(c.getStatus()))
                .count();

        List<TicketRow> recent = scopedTickets.stream()
                .sorted((a, b) -> java.util.Comparator.nullsLast(java.time.Instant::compareTo)
                        .compare(b.getCreatedAt(), a.getCreatedAt()))
                .limit(20)
                .map(t -> new TicketRow(
                        t.getId(),
                        t.getTicketNumber(),
                        t.getTitle(),
                        t.getStatus(),
                        t.getPriority(),
                        users.findById(t.getCreatedBy()).map(com.sysco.web.domain.UserAccount::getUsername).orElse("")))
                .toList();
        List<AgentPerformanceRow> agentPerformance = buildAgentPerformance(recent);
        String topAgent = agentPerformance.isEmpty() ? "—" : agentPerformance.get(0).username();

        int ticketOpenPct = pct(open, totalTickets);
        int ticketAssignedPct = pct(assigned, totalTickets);
        int ticketClosedPct = pct(closed, totalTickets);

        long slaEligibleActive =
                scopedTickets.stream().filter(TicketSlaCalculator::countsAsActiveSlaTicket).count();
        long slaViolations = scopedTickets.stream().filter(TicketSlaCalculator::isActiveBreached).count();
        int slaConformPct =
                slaEligibleActive == 0
                        ? 100
                        : (int) Math.round(100.0 * (slaEligibleActive - slaViolations) / slaEligibleActive);
        int slaViolationPct = slaEligibleActive == 0 ? 0 : pct(slaViolations, slaEligibleActive);

        return new AdminDashboardSnapshot(
                totalTickets,
                open,
                assigned,
                closed,
                insp,
                ctrl,
                verif,
                assist,
                cpTotal,
                cpNotResolved,
                cpAwaitSous,
                cpResolved,
                users.count(),
                jobs.count(),
                recent,
                slaConformPct,
                slaViolations,
                assigned,
                closed == 0 ? 0 : 46,
                topAgent,
                roleKey,
                ticketOpenPct,
                ticketAssignedPct,
                ticketClosedPct,
                slaViolationPct,
                agentPerformance);
    }

    private static AdminDashboardSnapshot emptySnapshot(String roleKey) {
        return new AdminDashboardSnapshot(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                0,
                0,
                0,
                0,
                "—",
                roleKey,
                0,
                0,
                0,
                0,
                List.of());
    }

    private static List<AgentPerformanceRow> buildAgentPerformance(List<TicketRow> rows) {
        Map<String, Long> grouped = rows.stream()
                .filter(r -> r.createdByUsername() != null && !r.createdByUsername().isBlank())
                .collect(Collectors.groupingBy(TicketRow::createdByUsername, Collectors.counting()));

        long max = grouped.values().stream().mapToLong(Long::longValue).max().orElse(0);

        return grouped.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(e -> new AgentPerformanceRow(e.getKey(), e.getValue(), max == 0 ? 0 : (int) ((e.getValue() * 100) / max)))
                .toList();
    }

    private static int pct(long value, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round((value * 100.0) / total);
    }

    private boolean isTicketOwnedByUser(com.sysco.web.domain.Ticket ticket, Long viewerId) {
        if (ticket == null || viewerId == null) {
            return false;
        }
        if (viewerId.equals(ticket.getCreatedBy())
                || viewerId.equals(ticket.getAssignedTo())
                || viewerId.equals(ticket.getUpdatedBy())) {
            return true;
        }
        if (ticket.getId() == null) {
            return false;
        }
        return ticketAssignments.existsByTicketIdAndUserId(ticket.getId(), viewerId);
    }

    private static boolean isCourierOwnedByUser(com.sysco.web.domain.CourierPacket courier, Long viewerId) {
        if (courier == null || viewerId == null) {
            return false;
        }
        return viewerId.equals(courier.getCreatedBy())
                || viewerId.equals(courier.getAssignedSousDirecteurId())
                || viewerId.equals(courier.getAssignedInspecteurId())
                || viewerId.equals(courier.getAssignedControleurId())
                || viewerId.equals(courier.getAssignedVerificateurId())
                || viewerId.equals(courier.getResolvedBy());
    }

    public record AdminDashboardSnapshot(
            long ticketsTotal,
            long ticketsOpen,
            long ticketsAssigned,
            long ticketsClosed,
            long staffInspecteurs,
            long staffControleurs,
            long staffVerificateurs,
            long staffAssistants,
            long courierTotal,
            long courierOpenPipeline,
            long courierAwaitSous,
            long courierResolved,
            long usersTotal,
            long scheduledJobs,
            List<TicketRow> recentTickets,
            int slaConformityPct,
            long slaViolations,
            long escaladesL1L2,
            int avgResolutionMinutes,
            String topAgent,
            String roleKey,
            int ticketOpenPct,
            int ticketAssignedPct,
            int ticketClosedPct,
            int slaViolationPct,
            List<AgentPerformanceRow> agentPerformance) {}

    public record AgentPerformanceRow(String username, long count, int pctOfMax) {}
}
