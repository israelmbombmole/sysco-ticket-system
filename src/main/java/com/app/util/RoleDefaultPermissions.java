package com.app.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pre-selected {@code user_permissions} rows when creating a user or changing role in create mode.
 * <p>
 * <b>Courier:</b> {@code PHYSICAL_COURIER} read+write is set for {@code DIRECTEUR}, {@code SECRETAIRE},
 * {@code SOUS-DIRECTEUR}, {@code COURIER}, and {@code ADMIN} so the sidebar shows “Courrier” / management.
 * {@code DIRECTEUR} also gets {@code USER_MANAGEMENT} so they can create {@code SECRETAIRE} accounts whose
 * defaults already include courier + leave. In the courier UI, only the director/admin can use
 * “authorize secretary to assign sous-direction”.
 */
public final class RoleDefaultPermissions {

    private RoleDefaultPermissions() {}

    public static Set<String> defaultKeysForRole(String role) {
        if (role == null || role.isBlank()) {
            return new HashSet<>();
        }
        String r = role.trim().toUpperCase(Locale.ROOT);
        return switch (r) {
            case "ADMIN" -> adminDefaults();
            case "DIRECTEUR" -> directeurDefaults();
            case "SOUS-DIRECTEUR" -> sousDirecteurDefaults();
            case "INSPECTEUR" -> inspecteurDefaults();
            case "CONTROLEUR" -> controleurDefaults();
            case "VERIFICATEUR" -> verificateurDefaults();
            case "VERIFICATEUR-ASSISTANT" -> verificateurAssistantDefaults();
            case "COURIER" -> courierDefaults();
            case "SECRETAIRE" -> secretaireDefaults();
            default -> verificateurDefaults();
        };
    }

    private static void rw(Set<String> out, String base) {
        out.add(ModuleAccess.readKey(base));
        out.add(ModuleAccess.writeKey(base));
    }

    private static void ro(Set<String> out, String base) {
        out.add(ModuleAccess.readKey(base));
    }

    private static void dash(Set<String> out, String role) {
        String k = DashboardPermissions.keyForRole(role);
        rw(out, k);
    }

    private static Set<String> adminDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "ADMIN");
        rw(s, "DATA_ENTRY");
        rw(s, "DATA_MANAGEMENT");
        rw(s, "DATASHARE");
        rw(s, "MY_ACTIVITY");
        rw(s, "MY_WORK");
        rw(s, "TICKET_MONITORING");
        rw(s, "TICKET_MANAGEMENT");
        rw(s, "FILE_SHARE_MANAGEMENT");
        rw(s, "USER_MANAGEMENT");
        rw(s, "LOGIN_AUDIT");
        rw(s, "FILE_SHARE_AUDIT");
        rw(s, "CREATE_TICKET");
        rw(s, "JOB_SCHEDULER");
        rw(s, "MISSIONS");
        rw(s, "LEAVE_MANAGEMENT");
        rw(s, "PHYSICAL_COURIER");
        rw(s, "MY_SHIFT");
        return s;
    }

    private static Set<String> directeurDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "DIRECTEUR");
        rw(s, "DATA_ENTRY");
        rw(s, "DATA_MANAGEMENT");
        rw(s, "DATASHARE");
        rw(s, "MY_ACTIVITY");
        rw(s, "MY_WORK");
        rw(s, "TICKET_MONITORING");
        rw(s, "TICKET_MANAGEMENT");
        rw(s, "FILE_SHARE_MANAGEMENT");
        rw(s, "USER_MANAGEMENT");
        rw(s, "LEAVE_MANAGEMENT");
        ro(s, "LOGIN_AUDIT");
        ro(s, "FILE_SHARE_AUDIT");
        rw(s, "CREATE_TICKET");
        rw(s, "MISSIONS");
        rw(s, "PHYSICAL_COURIER");
        ro(s, "MY_SHIFT");
        return s;
    }

    private static Set<String> sousDirecteurDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "SOUS-DIRECTEUR");
        rw(s, "DATA_ENTRY");
        rw(s, "DATA_MANAGEMENT");
        rw(s, "DATASHARE");
        rw(s, "MY_ACTIVITY");
        rw(s, "MY_WORK");
        rw(s, "TICKET_MONITORING");
        rw(s, "TICKET_MANAGEMENT");
        rw(s, "FILE_SHARE_MANAGEMENT");
        ro(s, "USER_MANAGEMENT");
        rw(s, "LEAVE_MANAGEMENT");
        ro(s, "LOGIN_AUDIT");
        ro(s, "FILE_SHARE_AUDIT");
        rw(s, "CREATE_TICKET");
        rw(s, "MISSIONS");
        rw(s, "PHYSICAL_COURIER");
        rw(s, "MY_SHIFT");
        return s;
    }

    private static Set<String> inspecteurDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "INSPECTEUR");
        rw(s, "MY_WORK");
        rw(s, "TICKET_MONITORING");
        rw(s, "TICKET_MANAGEMENT");
        rw(s, "CREATE_TICKET");
        rw(s, "DATASHARE");
        rw(s, "MY_ACTIVITY");
        rw(s, "MISSIONS");
        ro(s, "DATA_ENTRY");
        ro(s, "LEAVE_MANAGEMENT");
        ro(s, "FILE_SHARE_MANAGEMENT");
        ro(s, "MY_SHIFT");
        return s;
    }

    private static Set<String> controleurDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "CONTROLEUR");
        rw(s, "MY_WORK");
        rw(s, "TICKET_MONITORING");
        rw(s, "CREATE_TICKET");
        rw(s, "DATASHARE");
        rw(s, "MY_ACTIVITY");
        ro(s, "TICKET_MANAGEMENT");
        ro(s, "DATA_ENTRY");
        ro(s, "MISSIONS");
        return s;
    }

    private static Set<String> verificateurDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "VERIFICATEUR");
        rw(s, "MY_WORK");
        rw(s, "MY_ACTIVITY");
        rw(s, "DATASHARE");
        ro(s, "CREATE_TICKET");
        ro(s, "TICKET_MONITORING");
        ro(s, "DATA_ENTRY");
        return s;
    }

    private static Set<String> verificateurAssistantDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "VERIFICATEUR-ASSISTANT");
        ro(s, "MY_WORK");
        ro(s, "MY_ACTIVITY");
        ro(s, "DATASHARE");
        ro(s, "CREATE_TICKET");
        return s;
    }

    private static Set<String> courierDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "COURIER");
        rw(s, "PHYSICAL_COURIER");
        ro(s, "DATASHARE");
        return s;
    }

    private static Set<String> secretaireDefaults() {
        Set<String> s = new HashSet<>();
        dash(s, "SECRETAIRE");
        rw(s, "PHYSICAL_COURIER");
        rw(s, "CREATE_TICKET");
        rw(s, "LEAVE_MANAGEMENT");
        rw(s, "DATASHARE");
        rw(s, "TICKET_MANAGEMENT");
        ro(s, "DATA_ENTRY");
        ro(s, "FILE_SHARE_MANAGEMENT");
        return s;
    }
}
