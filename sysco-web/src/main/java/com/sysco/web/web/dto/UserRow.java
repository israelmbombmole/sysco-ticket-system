package com.sysco.web.web.dto;

public record UserRow(Long id, String username, String role, String email, Long directionId, boolean active) {}
