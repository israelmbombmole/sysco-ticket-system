package com.app.model;

public class Message {

    private int id;
    private int senderId;
    private int receiverId;
    private String content;
    private String timestamp;
    private int isRead;

    public Message(int id, int senderId, int receiverId,
                   String content, String timestamp, int isRead) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    // Constructor for sending
    public Message(int senderId, int receiverId, String content) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
    }

    // Getters
    public int getId() { return id; }
    public int getSenderId() { return senderId; }
    public int getReceiverId() { return receiverId; }
    public String getContent() { return content; }
    public String getTimestamp() { return timestamp; }
    public int getIsRead() { return isRead; }
    public String getMessage() {
    return content;
}
}