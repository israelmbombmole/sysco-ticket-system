package com.app.model;

import java.time.LocalDateTime;

public class UserActivity {

    private String username;
    private String name;
    private String sousDirection;
    private LocalDateTime createdAt;

    public UserActivity(String username,
                    String name,
                    String sousDirection,
                    LocalDateTime createdAt) {
    this.username = username;
    this.name = name;
    this.sousDirection = sousDirection;
    this.createdAt = createdAt;
}


    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public String getSousDirection() {
        return sousDirection;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
