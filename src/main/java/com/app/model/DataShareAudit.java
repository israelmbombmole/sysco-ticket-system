package com.app.model;

public class DataShareAudit {

    private int id;
    private int fileId;

    private String fileName;

    private int sharedById;
    private String sharedBy;

    private int recipientId;
    private String recipient;

    private String action;
    private String date;
    private String time;

    public DataShareAudit(int id,
                          int fileId,
                          String fileName,
                          int sharedById,
                          String sharedBy,
                          int recipientId,
                          String recipient,
                          String action,
                          String date,
                          String time) {

        this.id = id;
        this.fileId = fileId;
        this.fileName = fileName;
        this.sharedById = sharedById;
        this.sharedBy = sharedBy;
        this.recipientId = recipientId;
        this.recipient = recipient;
        this.action = action;
        this.date = date;
        this.time = time;
    }

    public int getId() {
        return id;
    }

    public int getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public int getSharedById() {
        return sharedById;
    }

    public String getSharedBy() {
        return sharedBy;
    }

    public int getRecipientId() {
        return recipientId;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getAction() {
        return action;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }
}