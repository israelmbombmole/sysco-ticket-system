package com.sysco.web.navigation;

import java.util.List;

/** Sidebar module after permission filtering, ready for the template. */
public record SidebarGroup(String id, String messageKey, boolean expanded, List<SidebarLink> links) {}
