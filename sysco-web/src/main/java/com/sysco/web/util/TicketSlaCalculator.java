package com.sysco.web.util;

import com.sysco.web.domain.Ticket;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * Priority-based resolution SLA from ticket {@link Ticket#getCreatedAt()}: typical IT-style targets
 * (critical fastest, low slowest). Uses calendar hours (not business hours).
 */
public final class TicketSlaCalculator {

    private static final Set<String> TERMINAL =
            Set.of("CLOSED", "RESOLVED", "MERGED");

    private TicketSlaCalculator() {}

    public enum SlaUiStatus {
        NA,
        OK,
        WARNING,
        BREACH,
        CLOSED_OK,
        CLOSED_BREACH
    }

    /** Labels shown via {@code ticketMgmt.sla.*} message keys (suffix matches name lower-case). */
    public record SlaPresentation(SlaUiStatus status, String dueFormatted, String resolvedFormatted) {}

    public static Duration targetResolution(String priority) {
        String p = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        return switch (p) {
            case "CRITICAL" -> Duration.ofHours(4);
            case "HIGH" -> Duration.ofHours(8);
            case "MEDIUM" -> Duration.ofHours(24);
            case "LOW" -> Duration.ofHours(72);
            default -> Duration.ofHours(48);
        };
    }

    public static Instant deadline(Ticket t) {
        if (t == null || t.getCreatedAt() == null) {
            return null;
        }
        return t.getCreatedAt().plus(targetResolution(t.getPriority()));
    }

    public static boolean isTerminalTicket(Ticket t) {
        if (t == null || t.getStatus() == null) {
            return false;
        }
        return TERMINAL.contains(t.getStatus().trim().toUpperCase(Locale.ROOT));
    }

    /** Open / in-flight ticket whose SLA deadline is already passed. */
    public static boolean isActiveBreached(Ticket t) {
        if (t == null || isTerminalTicket(t) || t.getCreatedAt() == null) {
            return false;
        }
        Instant d = deadline(t);
        if (d == null) {
            return false;
        }
        return Instant.now().isAfter(d);
    }

    /**
     * Non-terminal ticket with a known creation time — used as SLA denominator on dashboards.
     */
    public static boolean countsAsActiveSlaTicket(Ticket t) {
        return t != null && !isTerminalTicket(t) && t.getCreatedAt() != null;
    }

    private static Instant effectiveResolvedInstant(Ticket t) {
        if (t.getClosedAt() != null) {
            return t.getClosedAt();
        }
        return t.getUpdatedAt();
    }

    /** Presentation row for ticket detail UI. */
    public static SlaPresentation present(Ticket t) {
        if (t == null || t.getCreatedAt() == null) {
            return new SlaPresentation(SlaUiStatus.NA, "", "");
        }
        Instant due = deadline(t);
        String dueFmt = DisplayDateFormatter.formatInstant(due);

        if (isTerminalTicket(t)) {
            Instant resolved = effectiveResolvedInstant(t);
            String resFmt = resolved != null ? DisplayDateFormatter.formatInstant(resolved) : "";
            if (resolved == null || due == null) {
                return new SlaPresentation(SlaUiStatus.NA, dueFmt, resFmt);
            }
            boolean met = !resolved.isAfter(due);
            return new SlaPresentation(met ? SlaUiStatus.CLOSED_OK : SlaUiStatus.CLOSED_BREACH, dueFmt, resFmt);
        }

        Instant now = Instant.now();
        if (now.isAfter(due)) {
            return new SlaPresentation(SlaUiStatus.BREACH, dueFmt, "");
        }
        Duration window = Duration.between(t.getCreatedAt(), due);
        long warnNanos = window.toNanos() / 4;
        Instant warnFrom = due.minusNanos(Math.max(1L, warnNanos));
        if (!now.isBefore(warnFrom)) {
            return new SlaPresentation(SlaUiStatus.WARNING, dueFmt, "");
        }
        return new SlaPresentation(SlaUiStatus.OK, dueFmt, "");
    }
}
