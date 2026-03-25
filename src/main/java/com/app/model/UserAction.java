package com.app.model;

import java.time.LocalDateTime;

public class UserAction {

    private String username;
    private String action;
    private LocalDateTime createdAt;

    public UserAction(String username, String action, LocalDateTime createdAt) {
        this.username = username;
        this.action = action;
        this.createdAt = createdAt;
    }

    public String getUsername() { return username; }
    public String getAction() { return action; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
