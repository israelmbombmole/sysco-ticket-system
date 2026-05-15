package com.sysco.web.security;

import com.sysco.web.domain.UserAccount;

/**
 * Role ordering for ticket reopen: only accounts strictly above the closing officer may reopen (plus global admins).
 * Aligns conceptually with escalation seniority in {@link com.sysco.web.service.MyWorkService} (higher = more senior).
 */
public final class TicketRoleRanks {

    private TicketRoleRanks() {}

    /**
     * Larger = more senior. {@link RoleKeys#normalizeForScope(String)} keys only.
     */
    public static int reopenHierarchyRank(String normalizedRole) {
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
            case "ADMIN" -> 1000;
            case "SUPER_ADMIN" -> 1100;
            default -> 15;
        };
    }

    /**
     * Whether {@code viewer} may reopen a ticket closed by {@code closer}. Unknown closer → only {@code SUPER_ADMIN} /
     * {@code ADMIN} (except ADMIN cannot override {@code SUPER_ADMIN} closure).
     */
    public static boolean mayReopenRelativeToCloser(UserAccount viewer, UserAccount closer) {
        if (viewer == null || !viewer.isActiveBool()) {
            return false;
        }
        String vr = RoleKeys.normalizeForScope(viewer.getRole());
        if ("SUPER_ADMIN".equals(vr)) {
            return true;
        }
        if ("ADMIN".equals(vr)) {
            if (closer == null) {
                return true;
            }
            return !"SUPER_ADMIN".equals(RoleKeys.normalizeForScope(closer.getRole()));
        }
        if (closer == null || !closer.isActiveBool()) {
            return false;
        }
        String cr = RoleKeys.normalizeForScope(closer.getRole());
        return reopenHierarchyRank(vr) > reopenHierarchyRank(cr);
    }

    /**
     * Whether {@code candidate}'s role is strictly senior to {@code closer}'s (same rule as reopen, excluding admin
     * shortcuts). Used to choose closure-notification recipients.
     */
    public static boolean strictlyMoreSeniorThanCloser(UserAccount candidate, UserAccount closer) {
        if (candidate == null || !candidate.isActiveBool() || closer == null || !closer.isActiveBool()) {
            return false;
        }
        String cand = RoleKeys.normalizeForScope(candidate.getRole());
        String cr = RoleKeys.normalizeForScope(closer.getRole());
        if ("SUPER_ADMIN".equals(cand)) {
            return !"SUPER_ADMIN".equals(cr);
        }
        if ("ADMIN".equals(cand)) {
            return !"SUPER_ADMIN".equals(cr) && !"ADMIN".equals(cr);
        }
        return reopenHierarchyRank(cand) > reopenHierarchyRank(cr);
    }
}
