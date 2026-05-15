package com.sysco.web.navigation;

import java.util.List;

public final class NavigationRegistry {

    private NavigationRegistry() {}

    public static List<NavItem> mainNav() {
        return List.of(
                new NavItem("/app", "nav.dashboard"),
                new NavItem("/app/data-entry", "nav.dataEntry"),
                new NavItem("/app/courier", "nav.courier"),
                new NavItem("/app/courier-management", "nav.courierManagement"),
                new NavItem("/app/data-management", "nav.dataManagement"),
                new NavItem("/app/data-share", "nav.dataShare"),
                new NavItem("/app/my-activity", "nav.myActivity"),
                new NavItem("/app/my-work", "nav.myWork"),
                new NavItem("/app/ticket-monitoring", "nav.ticketMonitoring"),
                new NavItem("/app/ticket-management", "nav.ticketManagement"),
                new NavItem("/app/file-share-management", "nav.fileShareManagement"),
                new NavItem("/app/user-management", "nav.userManagement"),
                new NavItem("/app/agenda", "nav.agenda"),
                new NavItem("/app/login-audit", "nav.loginAudit"),
                new NavItem("/app/file-share-audit", "nav.fileShareAudit"),
                new NavItem("/app/create-ticket", "nav.createTicket"),
                new NavItem("/app/job-scheduler", "nav.jobScheduler"),
                new NavItem("/app/missions", "nav.missions"),
                new NavItem("/app/my-shift", "nav.myShift"));
    }
}
