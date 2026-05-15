package com.app.model;

public class FieldMissionAttachment {

    private int id;
    private int missionId;
    private String fileName;
    private String filePath;

    public FieldMissionAttachment() {}

    public FieldMissionAttachment(int id, int missionId, String fileName, String filePath) {
        this.id = id;
        this.missionId = missionId;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMissionId() {
        return missionId;
    }

    public void setMissionId(int missionId) {
        this.missionId = missionId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public String toString() {
        return fileName != null ? fileName : "";
    }
}
