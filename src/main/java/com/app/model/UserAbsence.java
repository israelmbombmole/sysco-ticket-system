package com.app.model;

/** Record of a user holiday or leave period (excluded from ticket assignment while active). */
public class UserAbsence {

    public static final String TYPE_LEAVE = "LEAVE";
    public static final String TYPE_HOLIDAY = "HOLIDAY";
    /** Administrative absence: listed in history only; does not appear in the congé/vacances snapshot or block ticket assignment. */
    public static final String TYPE_ABSENCE = "ABSENCE";
    /** Auto-created when a user is on a field mission (synced from {@code field_missions}). */
    public static final String TYPE_MISSION = "MISSION";
    /** Value stored in {@code notes} for mission-linked absence rows. */
    public static final String NOTE_MISSION = "mission";
    /**
     * For the leave dashboard only: users with a MISSION starting within this many days after
     * {@code État au} are excluded from "Disponibles" even before the mission start date.
     */
    public static final int UPCOMING_MISSION_EXCLUDE_DAYS = 30;

    private int id;
    private int userId;
    private String username;
    private String startDate;
    private String endDate;
    private String absenceType = TYPE_LEAVE;
    private String notes;
    private Integer createdBy;
    private String createdAt;
    private Integer missionId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getAbsenceType() {
        return absenceType;
    }

    public void setAbsenceType(String absenceType) {
        this.absenceType = absenceType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getMissionId() {
        return missionId;
    }

    public void setMissionId(Integer missionId) {
        this.missionId = missionId;
    }
}
