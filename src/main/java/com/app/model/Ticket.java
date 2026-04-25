package com.app.model;

import java.time.LocalDateTime;
import java.util.List;

public class Ticket {

    private int id;
    private String title;
    private String description;
    private String status;
    private String createdAt;
    private String assignedToName;
    private String departmentName;
    private String ticketType;
    private String attachmentName;
    private String attachmentPath;
    private String attachmentType;
    private List<String> attachments;
    
    

    private Integer slaHours;
    private Integer mergedInto;
    private String mergeNote;
    private String ticketNumber;

    private String createdByName;
    private String createdByRole;
    private String createdBySousDirection;
    
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;

    private Integer resolutionMinutes;
    private String object;
    
    private String priority;
    private Integer assignedTo;
    private Integer departmentId;
    private Integer directionId;
    private Integer sousDirectionId;
    
    private String createdBy;
    private String updatedBy;
    private String updatedAt;
    
    // ======================
    // GETTERS & SETTERS
    // ======================

    
    

public String getObject() {
    return object;
}

public void setObject(String object) {
    this.object = object;
}
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // 🔥 FIXED CREATED AT
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Integer getResolutionMinutes() {

    if (startedAt == null || closedAt == null) {
        return null;
    }

    
    
    return (int) java.time.Duration
            .between(startedAt, closedAt)
            .toMinutes();
}


public void setResolutionMinutes(Integer resolutionMinutes) {
    this.resolutionMinutes = resolutionMinutes;
}
    
    
    public String getCreatedBy() {
    return createdBy == null ? "System" : createdBy;
}

public String getUpdatedBy() {
    return updatedBy == null ? "System" : updatedBy;
}

public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
}

public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
}
    
    // ===============================
// PRIORITY
// ===============================

public String getPriority() {
    return priority;
}

public void setPriority(String priority) {
    this.priority = priority;
}

// ===============================
// ASSIGNED TO
// ===============================

public Integer getAssignedTo() {
    return assignedTo;
}

public void setAssignedTo(Integer assignedTo) {
    this.assignedTo = assignedTo;
}
    
    public String getAssignedToName() {
    return assignedToName;
}

public void setAssignedToName(String assignedToName) {
    this.assignedToName = assignedToName;
}
   

private Integer createdById;

public Integer getCreatedById() {
    return createdById;
}

public void setCreatedById(Integer createdById) {
    this.createdById = createdById;
}
   


// ===============================
// SLA HOURS
// ===============================
public Integer getSlaHours() {
    return slaHours;
}

public void setSlaHours(Integer slaHours) {
    this.slaHours = slaHours;
}

// ===============================
// MERGED INTO
// ===============================
public Integer getMergedInto() {
    return mergedInto;
}

public void setMergedInto(Integer mergedInto) {
    this.mergedInto = mergedInto;
}

// ===============================
// MERGE NOTE
// ===============================
public String getMergeNote() {
    return mergeNote;
}

public void setMergeNote(String mergeNote) {
    this.mergeNote = mergeNote;
}

private boolean slaBreached;

public boolean isSlaBreached() {
    return slaBreached;
}

public void setSlaBreached(boolean slaBreached) {
    this.slaBreached = slaBreached;
}

private String registrationDate;
private String senderName;
private String subject;
private String cotation;
private String cotationDate;

public String getDepartmentName() {
    return departmentName;
}

public void setDepartmentName(String departmentName) {
    this.departmentName = departmentName;
}

public String getRegistrationDate() {
    return registrationDate;
}

public void setRegistrationDate(String registrationDate) {
    this.registrationDate = registrationDate;
}

public String getSenderName() {
    return senderName;
}

public void setSenderName(String senderName) {
    this.senderName = senderName;
}

public String getSubject() {
    return subject;
}

public void setSubject(String subject) {
    this.subject = subject;
}

public String getCotation() {
    return cotation;
}

public void setCotation(String cotation) {
    this.cotation = cotation;
}

public String getCotationDate() {
    return cotationDate;
}

public void setCotationDate(String cotationDate) {
    this.cotationDate = cotationDate;
}


public Integer getDepartmentId() {
    return departmentId;
}

public void setDepartmentId(Integer departmentId) {
    this.departmentId = departmentId;
}

public Integer getDirectionId() {
    return directionId;
}

public void setDirectionId(Integer directionId) {
    this.directionId = directionId;
}

public Integer getSousDirectionId() {
    return sousDirectionId;
}

public void setSousDirectionId(Integer sousDirectionId) {
    this.sousDirectionId = sousDirectionId;
}


public String getTicketType() {
    return ticketType;
}

public void setTicketType(String ticketType) {
    this.ticketType = ticketType;
}

// ===============================
// TICKET REFERENCE
// ===============================

public String getReference() {

    if (id == 0) {
        return "";
    }

    return "TCK-" + String.format("%03d", id);
}


public String getCreatedByName() {
    return createdByName;
}

public String getCreatedBySousDirection() {
    return createdBySousDirection;
}

public String getCreatedByRole() {
    return createdByRole;
}


public String getTicketTypeDisplay() {

    if (ticketType == null) {
        return "";
    }

    String user = createdByName != null ? createdByName : "";
    String direction = createdBySousDirection != null ? createdBySousDirection : "";

    return ticketType + " - " + user + " - " + direction;
}
public List<String> getAttachments() {
    return attachments;
}

public void setAttachments(List<String> attachments) {
    this.attachments = attachments;
}



public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
}

public void setCreatedByRole(String createdByRole) {
    this.createdByRole = createdByRole;
}

public void setCreatedBySousDirection(String createdBySousDirection) {
    this.createdBySousDirection = createdBySousDirection;
}


public String getAttachmentName() { return attachmentName; }
public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

public String getAttachmentPath() { return attachmentPath; }
public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

public String getAttachmentType() { return attachmentType; }
public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }
    
public String getType() {
    return ticketType;
}


private String subDirection;


public String getSubDirection() {
    return subDirection;
}

public void setSubDirection(String subDirection) {
    this.subDirection = subDirection;
}

private String sender;
private String department;
private String currentOwnerRole;
private String routingStage;
private Integer ageHours;


public String getSender() {
    return sender;
}

public void setSender(String sender) {
    this.sender = sender;
}

public String getDepartment() {
    return department;
}

public void setDepartment(String department) {
    this.department = department;
}

public String getCurrentOwnerRole() {
    return currentOwnerRole;
}

public void setCurrentOwnerRole(String currentOwnerRole) {
    this.currentOwnerRole = currentOwnerRole;
}

public String getRoutingStage() {
    return routingStage;
}

public void setRoutingStage(String routingStage) {
    this.routingStage = routingStage;
}

private String smartAging;

public String getSmartAging() {
    return smartAging;
}

public void setSmartAging(String smartAging) {
    this.smartAging = smartAging;
}

public Integer getAgeHours() {
    return ageHours;
}

public void setAgeHours(Integer ageHours) {
    this.ageHours = ageHours;
}

public String getTicketNumber() {
    return "TCK-" + id;
}

public void setTicketNumber(String ticketNumber) {
    this.ticketNumber = ticketNumber;
}

public String getUpdatedAt() {
    return updatedAt;
}

public void setUpdatedAt(String updatedAt) {
    this.updatedAt = updatedAt;
}


}