package com.app.model;

import java.time.LocalDateTime;

public class AuditLog {

    private String username;
    private String action;
    private String entity;
    private Integer entityId;
    private String details;
    private LocalDateTime createdAt;

    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getEntity() { return entity; }
    public Integer getEntityId() { return entityId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUsername(String username) { this.username = username; }
    public void setAction(String action) { this.action = action; }
    public void setEntity(String entity) { this.entity = entity; }
    public void setEntityId(Integer entityId) { this.entityId = entityId; }
    public void setDetails(String details) { this.details = details; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}