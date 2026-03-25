package com.app.model;

public class DataShareFile {

    private int id;
    private String fileName;
    private String filePath;
    private String sharedBy;
    private String role;
    private String date;
    private String time;

    private long fileSize;
    private int downloads;
    private String expirationDate;

    public DataShareFile(int id,
                         String fileName,
                         String filePath,
                         String sharedBy,
                         String role,
                         String date,
                         String time) {

        this.id = id;
        this.fileName = fileName;
        this.filePath = filePath;
        this.sharedBy = sharedBy;
        this.role = role;
        this.date = date;
        this.time = time;
    }

    // ================= GETTERS =================

    public int getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSharedBy() {
        return sharedBy;
    }

    public String getRole() {
        return role;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getDownloads() {
        return downloads;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    // ================= SETTERS =================

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public void setDownloads(int downloads) {
        this.downloads = downloads;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }
}