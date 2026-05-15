package com.app.model;

public class ExternalEscalation {
    private int id;
    private int ticketId;
    private String ticketRef;
    private String fromDirection;
    private String toDirection;
    private int toDirectionId;
    private String toSousDirection;
    private int toSousDirectionId;
    private String escalatedBy;
    private Integer requestedApproverId;
    private String requestedApproverName;
    private String status;
    private String createdAt;
    private Integer assignedTo;
    private String assignedToName;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }
    public String getTicketRef() { return ticketRef; }
    public void setTicketRef(String ticketRef) { this.ticketRef = ticketRef; }
    public String getFromDirection() { return fromDirection; }
    public void setFromDirection(String fromDirection) { this.fromDirection = fromDirection; }
    public String getToDirection() { return toDirection; }
    public void setToDirection(String toDirection) { this.toDirection = toDirection; }
    public int getToDirectionId() { return toDirectionId; }
    public void setToDirectionId(int toDirectionId) { this.toDirectionId = toDirectionId; }
    public String getToSousDirection() { return toSousDirection; }
    public void setToSousDirection(String toSousDirection) { this.toSousDirection = toSousDirection; }
    public int getToSousDirectionId() { return toSousDirectionId; }
    public void setToSousDirectionId(int toSousDirectionId) { this.toSousDirectionId = toSousDirectionId; }
    public String getEscalatedBy() { return escalatedBy; }
    public void setEscalatedBy(String escalatedBy) { this.escalatedBy = escalatedBy; }
    public Integer getRequestedApproverId() { return requestedApproverId; }
    public void setRequestedApproverId(Integer requestedApproverId) { this.requestedApproverId = requestedApproverId; }
    public String getRequestedApproverName() { return requestedApproverName; }
    public void setRequestedApproverName(String requestedApproverName) { this.requestedApproverName = requestedApproverName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Integer getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Integer assignedTo) { this.assignedTo = assignedTo; }
    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }
}
