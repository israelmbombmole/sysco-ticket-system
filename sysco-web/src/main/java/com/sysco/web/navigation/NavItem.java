package com.sysco.web.navigation;

/** Sidebar entry mirroring JavaFX MainLayout menu order (labels via messages_*.properties). */
public record NavItem(String path, String messageKey) {}
