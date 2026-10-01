package com.sysco.web.navigation;

/** One permitted sidebar destination. {@code tourIndex} matches the guided-tour step order. */
public record SidebarLink(String path, String messageKey, boolean active, int tourIndex) {}
