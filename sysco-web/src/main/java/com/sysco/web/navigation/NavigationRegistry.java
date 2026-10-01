package com.sysco.web.navigation;

import java.util.List;

public final class NavigationRegistry {

    private NavigationRegistry() {}

    /**
     * Five visible modules. Extra screens live inside a module and appear when that module is opened.
     * {@link #mainNav()} stays the flat order used by the guided tour.
     */
    public static List<NavGroup> groups() {
        return List.of(
                new NavGroup("dashboard", "nav.dashboard", List.of(new NavItem("/app", "nav.dashboard"))),
                new NavGroup(
                        "tickets",
                        "nav.group.tickets",
                        List.of(
                                new NavItem("/app/create-ticket", "nav.createTicket"),
                                new NavItem("/app/ticket-monitoring", "nav.ticketMonitoring"),
                                new NavItem("/app/ticket-management", "nav.ticketManagement"),
                                new NavItem("/app/my-activity", "nav.myActivity"),
                                new NavItem("/app/my-work", "nav.myWork"))),
                new NavGroup(
                        "courier",
                        "nav.group.courier",
                        List.of(
                                new NavItem("/app/courier", "nav.courier"),
                                new NavItem("/app/courier-management", "nav.courierManagement"))),
                new NavGroup(
                        "data",
                        "nav.group.data",
                        List.of(
                                new NavItem("/app/data-entry", "nav.dataEntry"),
                                new NavItem("/app/data-management", "nav.dataManagement"),
                                new NavItem("/app/data-share", "nav.dataShare"),
                                new NavItem("/app/file-share-management", "nav.fileShareManagement"),
                                new NavItem("/app/file-share-audit", "nav.fileShareAudit"))),
                new NavGroup(
                        "operations",
                        "nav.group.operations",
                        List.of(
                                new NavItem("/app/agenda", "nav.agenda"),
                                new NavItem("/app/missions", "nav.missions"),
                                new NavItem("/app/my-shift", "nav.myShift"),
                                new NavItem("/app/job-scheduler", "nav.jobScheduler"),
                                new NavItem("/app/user-management", "nav.userManagement"),
                                new NavItem("/app/login-audit", "nav.loginAudit"))));
    }

    public static List<NavItem> mainNav() {
        return groups().stream().flatMap(group -> group.items().stream()).toList();
    }

    /** True when {@code currentPath} is this menu entry or a page under it. {@code /app} matches only the dashboard. */
    public static boolean matchesPath(String currentPath, String itemPath) {
        if (currentPath == null || itemPath == null || itemPath.isBlank()) {
            return false;
        }
        if (currentPath.equals(itemPath)) {
            return true;
        }
        if ("/app".equals(itemPath)) {
            return false;
        }
        return currentPath.startsWith(itemPath + "/");
    }
}
