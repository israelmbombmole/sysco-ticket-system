package com.app.util;

import java.util.Set;

/**
 * Role-specific dashboard permission keys and helpers. Legacy {@code DASHBOARD} is still accepted.
 */
public final class DashboardPermissions {

    private DashboardPermissions() {}

    public static String normalizeRole(String role) {
        if (role == null) {
            return "VERIFICATEUR";
        }
        role = role.toUpperCase();
        if ("ADMIN".equals(role)) {
            return "DIRECTEUR";
        }
        if ("USER".equals(role)) {
            return "VERIFICATEUR";
        }
        if ("AGENT".equals(role)) {
            return "CONTROLEUR";
        }
        return role;
    }

    /** Stored permission key for the given role's dashboard access. */
    public static String keyForRole(String role) {
        String r = normalizeRole(role);
        return switch (r) {
            case "DIRECTEUR" -> "DIRECTEUR_DASHBOARD";
            case "SOUS-DIRECTEUR" -> "SOUS_DIRECTEUR_DASHBOARD";
            case "INSPECTEUR" -> "INSPECTEUR_DASHBOARD";
            case "CONTROLEUR" -> "CONTROLEUR_DASHBOARD";
            case "VERIFICATEUR" -> "VERIFICATEUR_DASHBOARD";
            case "VERIFICATEUR-ASSISTANT" -> "VERIFICATEUR_ASSISTANT_DASHBOARD";
            case "COURIER" -> "COURIER_DASHBOARD";
            case "SECRETAIRE" -> "SECRETAIRE_DASHBOARD";
            default -> "VERIFICATEUR_DASHBOARD";
        };
    }

    public static boolean hasDashboardAccess(Set<String> perms, String role) {
        if (perms == null) {
            return false;
        }
        if (perms.contains("DASHBOARD")) {
            return true;
        }
        String k = keyForRole(role);
        return ModuleAccess.canRead(perms, k);
    }

    /** Checkbox label in the user form (e.g. "DIRECTEUR Dashboard"). */
    public static String checkboxLabelForRole(String role) {
        String r = normalizeRole(role);
        String dashboardText;
        try {
            dashboardText = LanguageManager.getBundle().getString("dashboard");
        } catch (Exception e) {
            dashboardText = "Dashboard";
        }
        return r + " " + dashboardText;
    }

    /** Email is mandatory for these roles when creating or editing a profile. */
    public static boolean isEmailRequiredForRole(String role) {
        String r = normalizeRole(role);
        return "DIRECTEUR".equals(r) || "SOUS-DIRECTEUR".equals(r) || "INSPECTEUR".equals(r);
    }
}
