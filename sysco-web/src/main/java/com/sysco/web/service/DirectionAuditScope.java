package com.sysco.web.service;

/**
 * SUPER_ADMIN ({@code unrestricted}) sees all audit rows; otherwise limit to the viewer's {@link #directionId()}
 * (users in the same direction).
 */
public record DirectionAuditScope(boolean unrestricted, Long directionId) {}
