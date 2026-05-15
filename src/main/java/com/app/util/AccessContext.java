package com.app.util;

import com.app.auth.Session;

/**
 * Distinguishes the global super administrator ({@code admin} / full system) from
 * per-direction {@code ADMIN} accounts that are scoped to {@code users.direction_id}.
 */
public final class AccessContext {

    private AccessContext() {
    }

    private static final String BUILTIN_SUPER_USERNAME = "admin";

    public static boolean isBuiltInSuperAdminUsername(String username) {
        return username != null && BUILTIN_SUPER_USERNAME.equalsIgnoreCase(username.trim());
    }

    /**
     * Full org-wide data and configuration (tickets, dashboards, courrier stats, user lists, etc.).
     * Only the built-in {@code admin} user with role {@code ADMIN} is unrestricted.
     */
    public static boolean isSystemSuperAdmin() {
        return isBuiltInSuperAdminUsername(Session.getUsername())
                && Session.getRole() != null
                && "ADMIN".equalsIgnoreCase(Session.getRole().trim());
    }

    /** Role {@code ADMIN} but not the global super account — limited to their {@link Session#getDirectionId()}. */
    public static boolean isDirectionAdmin() {
        if (Session.getRole() == null) {
            return false;
        }
        if (!"ADMIN".equalsIgnoreCase(Session.getRole().trim())) {
            return false;
        }
        return !isSystemSuperAdmin();
    }
}
