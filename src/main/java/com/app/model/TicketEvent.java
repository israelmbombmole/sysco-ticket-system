package com.app.model;

public class TicketEvent {

    private String username;
    private String type;
    private String description;
    private String createdAt;
    private String imagePath;

    public TicketEvent() {}

    public TicketEvent(String username, String type, String description, String createdAt, String imagePath) {
        this.username = username;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
        this.imagePath = imagePath;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}