package com.app.model;

import java.util.ArrayList;
import java.util.List;

/** Field / official mission (Option A): site, dates, objectives, participants, status, report. */
public class FieldMission {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_REPORTED = "REPORTED";

    private int id;
    private String missionCode;
    private String title;
    private String siteLocation;
    private String startDate;
    private String endDate;
    /** Free-form mission description (distinct from objectives and from compte rendu). */
    private String description;
    private String objectives;
    private String status = STATUS_PLANNED;
    private String reportText;
    private String reportSubmittedAt;
    /** User who owns the compte rendu; first save assigns this. */
    private Integer reportAuthorId;
    private Integer leadUserId;
    private String leadUsername;
    private int createdBy;
    private String createdByUsername;
    private String createdAt;
    private String updatedAt;

    /** Official mission order: reference number, issue date, signatory, body text. */
    private String orderReference;
    private String orderIssueDate;
    private String orderIssuedBy;
    private String orderBody;

    private final List<Integer> participantUserIds = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getMissionCode() {
        return missionCode;
    }

    public void setMissionCode(String missionCode) {
        this.missionCode = missionCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSiteLocation() {
        return siteLocation;
    }

    public void setSiteLocation(String siteLocation) {
        this.siteLocation = siteLocation;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getObjectives() {
        return objectives;
    }

    public void setObjectives(String objectives) {
        this.objectives = objectives;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReportText() {
        return reportText;
    }

    public void setReportText(String reportText) {
        this.reportText = reportText;
    }

    public String getReportSubmittedAt() {
        return reportSubmittedAt;
    }

    public void setReportSubmittedAt(String reportSubmittedAt) {
        this.reportSubmittedAt = reportSubmittedAt;
    }

    public Integer getReportAuthorId() {
        return reportAuthorId;
    }

    public void setReportAuthorId(Integer reportAuthorId) {
        this.reportAuthorId = reportAuthorId;
    }

    public Integer getLeadUserId() {
        return leadUserId;
    }

    public void setLeadUserId(Integer leadUserId) {
        this.leadUserId = leadUserId;
    }

    public String getLeadUsername() {
        return leadUsername;
    }

    public void setLeadUsername(String leadUsername) {
        this.leadUsername = leadUsername;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getOrderReference() {
        return orderReference;
    }

    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    public String getOrderIssueDate() {
        return orderIssueDate;
    }

    public void setOrderIssueDate(String orderIssueDate) {
        this.orderIssueDate = orderIssueDate;
    }

    public String getOrderIssuedBy() {
        return orderIssuedBy;
    }

    public void setOrderIssuedBy(String orderIssuedBy) {
        this.orderIssuedBy = orderIssuedBy;
    }

    public String getOrderBody() {
        return orderBody;
    }

    public void setOrderBody(String orderBody) {
        this.orderBody = orderBody;
    }

    public List<Integer> getParticipantUserIds() {
        return participantUserIds;
    }
}
