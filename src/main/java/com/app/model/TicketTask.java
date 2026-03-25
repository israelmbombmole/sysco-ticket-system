package com.app.model;

import java.time.LocalDateTime;

public class TicketTask {

    private int id;
    private int ticketId;
    private Integer parentAssignmentId;
    private String title;
    private String description;
    private int assignedTo;
    private String assignedToName;
    private String status;
    private LocalDateTime createdAt;
    private Integer parentTaskId;
    private int assignedBy;
    private String ticketTitle;

    // ✅ TIME FIELDS (KEEP AS LocalDateTime)
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;
    private Integer resolutionMinutes;
    private Integer durationMinutes;

    private String attachmentPath;
    private int createdBy;
    private String createdByName;
    private boolean escalated;
 

    // =====================
    // BASIC GETTERS/SETTERS
    // =====================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public Integer getParentAssignmentId() { return parentAssignmentId; }
    public void setParentAssignmentId(Integer parentAssignmentId) { this.parentAssignmentId = parentAssignmentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getAssignedTo() { return assignedTo; }
    public void setAssignedTo(int assignedTo) { this.assignedTo = assignedTo; }

    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(Integer parentTaskId) { this.parentTaskId = parentTaskId; }

    public int getAssignedBy() { return assignedBy; }
    public void setAssignedBy(int assignedBy) { this.assignedBy = assignedBy; }

    public String getTicketTitle() { return ticketTitle; }
    public void setTicketTitle(String ticketTitle) { this.ticketTitle = ticketTitle; }

    // =====================
    // TIME FIELDS (FIXED)
    // =====================

    public LocalDateTime getStartedAt() { return startedAt; }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    // ✅ OVERLOAD (THIS FIXES YOUR ERROR)
    public void setStartedAt(String startedAt) {
        if (startedAt != null) {
            this.startedAt = LocalDateTime.parse(startedAt.replace(" ", "T"));
        }
    }

    public LocalDateTime getClosedAt() { return closedAt; }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    // ✅ OVERLOAD
    public void setClosedAt(String closedAt) {
        if (closedAt != null) {
            this.closedAt = LocalDateTime.parse(closedAt.replace(" ", "T"));
        }
    }

    public Integer getResolutionMinutes() { return resolutionMinutes; }
    public void setResolutionMinutes(Integer resolutionMinutes) { this.resolutionMinutes = resolutionMinutes; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    // =====================
    // OTHER FIELDS
    // =====================

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public boolean isEscalated() { return escalated; }
    public void setEscalated(boolean escalated) { this.escalated = escalated; }
    
    
}