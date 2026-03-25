package com.app.model;

public class Attachment {

    private int id;
    private int ticketId;
    private Integer taskId; // ✅ IMPORTANT (for tasks)
    private String fileName;
    private String filePath;
    private String fileType;

    // ✅ EMPTY CONSTRUCTOR (REQUIRED)
    public Attachment() {
    }

    // ✅ FULL CONSTRUCTOR (OPTIONAL)
    public Attachment(int id, int ticketId, Integer taskId,
                      String fileName, String filePath, String fileType) {
        this.id = id;
        this.ticketId = ticketId;
        this.taskId = taskId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileType = fileType;
    }

    // ================= GETTERS =================

    public int getId() { return id; }

    public int getTicketId() { return ticketId; }

    public Integer getTaskId() { return taskId; }

    public String getFileName() { return fileName; }

    public String getFilePath() { return filePath; }

    public String getFileType() { return fileType; }

    // ================= SETTERS =================

    public void setId(int id) { this.id = id; }

    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public void setTaskId(Integer taskId) { this.taskId = taskId; }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public void setFilePath(String filePath) { this.filePath = filePath; }

    public void setFileType(String fileType) { this.fileType = fileType; }

    // ✅ IMPORTANT (for ListView display)
    @Override
    public String toString() {
        return fileName;
    }
}