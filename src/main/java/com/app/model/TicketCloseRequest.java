package com.app.model;

public class TicketCloseRequest {
    private int id;
    private int ticketId;
    private String ticketRef;
    private int requestedBy;
    private String requestedByName;
    private int requestedTo;
    private String requestedToName;
    private String reason;
    private String status;
    private String requestedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getTicketRef() { return ticketRef; }
    public void setTicketRef(String ticketRef) { this.ticketRef = ticketRef; }

    public int getRequestedBy() { return requestedBy; }
    public void setRequestedBy(int requestedBy) { this.requestedBy = requestedBy; }

    public String getRequestedByName() { return requestedByName; }
    public void setRequestedByName(String requestedByName) { this.requestedByName = requestedByName; }

    public int getRequestedTo() { return requestedTo; }
    public void setRequestedTo(int requestedTo) { this.requestedTo = requestedTo; }

    public String getRequestedToName() { return requestedToName; }
    public void setRequestedToName(String requestedToName) { this.requestedToName = requestedToName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestedAt() { return requestedAt; }
    public void setRequestedAt(String requestedAt) { this.requestedAt = requestedAt; }
}
