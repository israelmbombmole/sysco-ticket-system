package com.app.model;

import java.time.LocalDateTime;

public class TicketAssignment {

    private int id;
    private int ticketId;
    private int agentId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;
    private Integer durationMinutes;
    private String agentName;   // NEW

    // ✅ Constructor WITHOUT agentName (used by findByUser)
    public TicketAssignment(int id,
                            int ticketId,
                            int agentId,
                            String status,
                            LocalDateTime startedAt,
                            LocalDateTime closedAt,
                            Integer durationMinutes) {

        this.id = id;
        this.ticketId = ticketId;
        this.agentId = agentId;
        this.status = status;
        this.startedAt = startedAt;
        this.closedAt = closedAt;
        this.durationMinutes = durationMinutes;
    }

    // ✅ Constructor WITH agentName (used by findAll)
    public TicketAssignment(int id,
                            int ticketId,
                            int agentId,
                            String status,
                            LocalDateTime startedAt,
                            LocalDateTime closedAt,
                            Integer durationMinutes,
                            String agentName) {

        this(id, ticketId, agentId, status, startedAt, closedAt, durationMinutes);
        this.agentName = agentName;
    }

    // ================= GETTERS =================

    public int getId() { return id; }
    public int getTicketId() { return ticketId; }
    public int getAgentId() { return agentId; }
    public String getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getAgentName() { return agentName; }
}
