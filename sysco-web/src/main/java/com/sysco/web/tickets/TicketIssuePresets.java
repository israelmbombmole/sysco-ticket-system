package com.sysco.web.tickets;

import java.util.List;
import java.util.Set;

/** Issue-type presets for the Créer un ticket wizard (Jira-style issue types). */
public final class TicketIssuePresets {

    public static final String OTHER = "__OTHER__";

    /** Ordered keys matching {@code messages[_fr].properties} {@code createTicket.issue.<code>}. */
    public static final List<String> ORDERED_CODES = List.of(
            OTHER,
            "ACCOUNTS_ACCESS",
            "PASSWORD_RESET",
            "EMAIL_CALENDAR",
            "NETWORK_WIFI",
            "NETWORK_VPN",
            "PRINT_SCAN",
            "HARDWARE_PC",
            "HARDWARE_PHONE",
            "SOFTWARE_INSTALL",
            "SOFTWARE_BUG",
            "DATA_SHARE_ACCESS",
            "PORTAL_WEB",
            "SECURITY_INCIDENT",
            "TRAINING_REQUEST",
            "FACILITY_OFFICE");

    private static final Set<String> ALLOWED = Set.copyOf(ORDERED_CODES);

    private TicketIssuePresets() {}

    public static boolean isAllowed(String code) {
        return code != null && ALLOWED.contains(code.trim());
    }
}
