package com.sysco.web.navigation;

import java.util.List;

/** One top-level sidebar module. A single link stays a direct item; several links open on click. */
public record NavGroup(String id, String messageKey, List<NavItem> items) {}
