package com.sysco.web.web.dto;

import java.time.Instant;

public record CourierRow(
        Long id, String refCode, String title, String sender, String status, String priority, Instant createdAt) {}
