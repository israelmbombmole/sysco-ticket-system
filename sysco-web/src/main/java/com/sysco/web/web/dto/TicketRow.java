package com.sysco.web.web.dto;

public record TicketRow(
        Long id, String ticketNumber, String title, String status, String priority, String createdByUsername) {}
