package com.sysco.web.security;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Mirrors {@code MainController#applyPermissions()} and {@code ModuleAccess} /
 * {@code DashboardPermissions} from the JavaFX SYSCO client — drives which sidebar links appear.
 */
public final class WebSyscoPermissions {

    private static final String SUFFIX_READ = "_READ";
    private static final String SUFFIX_WRITE = "_WRITE";

    private WebSyscoPermissions() {}

    public static boolean canAccessNavPath(Authentication auth, String servletPath) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Set<String> perms = permissionStrings(auth);
        String role = resolveRole(auth);

        if ("ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role)) {
            return true;
        }

        return switch (servletPath) {
            case "/app" -> hasDashboardAccess(perms, role);
            case "/app/data-entry" -> canRead(perms, "DATA_ENTRY");
            /* Must match @PreAuthorize on CourierPortalController / CourierManagementController (no role bypass). */
            case "/app/courier", "/app/courier-management" -> canRead(perms, "PHYSICAL_COURIER");
            case "/app/data-management" -> canRead(perms, "DATA_MANAGEMENT");
            case "/app/data-share" -> canRead(perms, "DATASHARE");
            case "/app/my-activity" -> canRead(perms, "MY_ACTIVITY");
            case "/app/my-work" -> canRead(perms, "MY_WORK") || canRead(perms, "MY_ACTIVITY");
            case "/app/ticket-monitoring" -> canRead(perms, "TICKET_MONITORING");
            case "/app/ticket-management" -> canRead(perms, "TICKET_MANAGEMENT");
            case "/app/file-share-management" ->
                    "SUPER_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
            case "/app/user-management" -> canRead(perms, "USER_MANAGEMENT");
            case "/app/agenda", "/app/leave-management" ->
                    canRead(perms, "LEAVE_MANAGEMENT") || canRead(perms, "USER_MANAGEMENT");
            case "/app/login-audit" -> canRead(perms, "LOGIN_AUDIT");
            case "/app/file-share-audit" -> canRead(perms, "FILE_SHARE_AUDIT");
            case "/app/create-ticket" -> canRead(perms, "CREATE_TICKET");
            case "/app/job-scheduler" -> canRead(perms, "JOB_SCHEDULER");
            case "/app/missions" -> canRead(perms, "MISSIONS");
            case "/app/my-shift" -> isMyShiftModuleVisible(role, perms);
            case "/app/chat", "/app/notifications" -> true;
            default -> false;
        };
    }

    static Set<String> permissionStrings(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    public static String resolveRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .findFirst()
                .orElse("");
    }

    static boolean canRead(Set<String> perms, String base) {
        if (perms == null || base == null) {
            return false;
        }
        if (perms.contains(base)) {
            return true;
        }
        return perms.contains(base + SUFFIX_READ) || perms.contains(base + SUFFIX_WRITE);
    }

    static boolean hasDashboardAccess(Set<String> perms, String role) {
        if (perms != null && perms.contains("DASHBOARD")) {
            return true;
        }
        String k = dashboardKeyForRole(role);
        if (canRead(perms, k)) {
            return true;
        }
        /*
         * Many deployments only store fine-grained authorities (MY_WORK_READ, etc.) and omit explicit
         * INSPECTEUR_DASHBOARD_READ rows. Login still lands on /app (authenticated-only), but the sidebar
         * filtered navItems here — without a *_DASHBOARD permission the Dashboard link disappeared after the
         * first redirect. Allow the home dashboard for standard SYSCO roles when no explicit deny pattern exists.
         */
        return implicitDashboardNavByRole(role);
    }

    /** Sidebar Dashboard link: role implies dashboard module (matches desktop default visibility). */
    private static boolean implicitDashboardNavByRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String r = normalizeForScope(role);
        if (r.isEmpty()) {
            return false;
        }
        return switch (r) {
            case "ADMIN",
                    "SUPER_ADMIN",
                    "DIRECTEUR",
                    "SOUS-DIRECTEUR",
                    "INSPECTEUR",
                    "CONTROLEUR",
                    "VERIFICATEUR",
                    "VERIFICATEUR-ASSISTANT",
                    "SECRETAIRE",
                    "COURIER",
                    "COURRIER",
                    "USER",
                    "AGENT" -> true;
            default -> false;
        };
    }

    private static String normalizeRoleForDashboard(String role) {
        if (role == null || role.isBlank()) {
            return "VERIFICATEUR";
        }
        String r = role.toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(r)) {
            return "DIRECTEUR";
        }
        if ("USER".equals(r)) {
            return "VERIFICATEUR";
        }
        if ("AGENT".equals(r)) {
            return "CONTROLEUR";
        }
        return r;
    }

    private static String dashboardKeyForRole(String role) {
        String r = normalizeRoleForDashboard(role);
        return switch (r) {
            case "DIRECTEUR" -> "DIRECTEUR_DASHBOARD";
            case "SOUS-DIRECTEUR", "SOUS_DIRECTEUR" -> "SOUS_DIRECTEUR_DASHBOARD";
            case "INSPECTEUR" -> "INSPECTEUR_DASHBOARD";
            case "CONTROLEUR" -> "CONTROLEUR_DASHBOARD";
            case "VERIFICATEUR" -> "VERIFICATEUR_DASHBOARD";
            case "VERIFICATEUR-ASSISTANT", "VERIFICATEUR_ASSISTANT" -> "VERIFICATEUR_ASSISTANT_DASHBOARD";
            case "COURIER", "COURRIER" -> "COURIER_DASHBOARD";
            case "SECRETAIRE" -> "SECRETAIRE_DASHBOARD";
            default -> "VERIFICATEUR_DASHBOARD";
        };
    }

    private static boolean isMyShiftModuleVisible(String role, Set<String> perms) {
        if (perms != null && canRead(perms, "MY_SHIFT")) {
            return true;
        }
        if (role == null || role.isBlank()) {
            return false;
        }
        String r = normalizeForScope(role);
        if (r.isEmpty()) {
            return false;
        }
        return switch (r) {
            case "ADMIN", "DIRECTEUR", "SOUS-DIRECTEUR" -> true;
            default -> false;
        };
    }

    /** Same rules as {@code RoleKeyUtil#normalizeForScope} in the desktop app. */
    private static String normalizeForScope(String viewRole) {
        if (viewRole == null || viewRole.isBlank()) {
            return "";
        }
        String s = Normalizer.normalize(viewRole.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        s = s.toUpperCase(Locale.ROOT).replace('\u00A0', ' ').replace("\u00AD", "").trim();
        s = s.replace('’', '\'').replace('`', '\'');
        if (s.contains("VERIFICATEUR") && s.contains("ASSISTANT")) {
            return "VERIFICATEUR-ASSISTANT";
        }
        if (s.contains("SOUS") && s.contains("DIRECTEUR")) {
            return "SOUS-DIRECTEUR";
        }
        if ("COURRIER".equals(s)) {
            return "COURIER";
        }
        if ("DIRECTEUR".equals(s) || s.startsWith("DIRECTEUR ")) {
            return "DIRECTEUR";
        }
        if ("DIRECTRICE".equals(s) || s.startsWith("DIRECTRICE ")) {
            return "DIRECTEUR";
        }
        if ("SECRETAIRE".equals(s) || s.startsWith("SECRETAIRE ")) {
            return "SECRETAIRE";
        }
        return s;
    }
}
