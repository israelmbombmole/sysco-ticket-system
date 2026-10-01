package com.sysco.web.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NavigationRegistryTest {

    @Test
    void sidebarExposesFiveModules() {
        List<NavGroup> groups = NavigationRegistry.groups();
        assertEquals(5, groups.size());
        assertEquals(
                List.of("dashboard", "tickets", "courier", "data", "operations"),
                groups.stream().map(NavGroup::id).toList());
        assertEquals(1, groups.get(0).items().size());
        assertTrue(groups.stream().skip(1).allMatch(group -> group.items().size() > 1));
    }

    @Test
    void everyFlatEntryBelongsToExactlyOneModule() {
        List<String> flat = NavigationRegistry.mainNav().stream().map(NavItem::path).toList();
        Set<String> unique = new HashSet<>(flat);
        assertEquals(flat.size(), unique.size());
        assertEquals(19, flat.size());
        assertTrue(flat.contains("/app"));
        assertTrue(flat.contains("/app/job-scheduler"));
    }

    @Test
    void pathMatchKeepsDashboardFromSwallowingOtherPages() {
        assertTrue(NavigationRegistry.matchesPath("/app", "/app"));
        assertFalse(NavigationRegistry.matchesPath("/app/missions", "/app"));
        assertTrue(NavigationRegistry.matchesPath("/app/missions/M-2026-1", "/app/missions"));
        assertFalse(NavigationRegistry.matchesPath("/app/courier-management", "/app/courier"));
    }
}
