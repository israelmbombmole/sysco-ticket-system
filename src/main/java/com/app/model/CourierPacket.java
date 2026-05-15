package com.app.model;

import javafx.beans.property.*;

/** Physical courier (internal) packet tracked through directions until resolved. */
public class CourierPacket {
    public static final String ST_REGISTERED = "REGISTERED";
    /** Enregistré par le courrier, pas encore routé vers une direction. */
    public static final String ST_AWAITING_DIRECTION = "AWAITING_DIRECTION";
    public static final String ST_DIRECTED = "DIRECTED";
    public static final String ST_SOUS_ASSIGNED = "SOUS_ASSIGNED";
    public static final String ST_IN_PROGRESS = "IN_PROGRESS";
    public static final String ST_RESOLVED = "RESOLVED";

    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty refCode = new SimpleStringProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final IntegerProperty targetDirectionId = new SimpleIntegerProperty(0);
    private final IntegerProperty targetSousDirectionId = new SimpleIntegerProperty(0);
    private final StringProperty targetDirectionName = new SimpleStringProperty();
    private final StringProperty targetSousDirectionName = new SimpleStringProperty();
    private final IntegerProperty createdBy = new SimpleIntegerProperty();
    private final StringProperty createdAt = new SimpleStringProperty();
    private final IntegerProperty assignedSousDirId = new SimpleIntegerProperty(0);
    private final IntegerProperty assignedInspId = new SimpleIntegerProperty(0);
    private final IntegerProperty assignedCtrlId = new SimpleIntegerProperty(0);
    private final IntegerProperty assignedVerifId = new SimpleIntegerProperty(0);
    private final StringProperty resolvedAt = new SimpleStringProperty();
    private final StringProperty sender = new SimpleStringProperty();
    private final StringProperty priority = new SimpleStringProperty("MEDIUM");
    private final StringProperty registrationDate = new SimpleStringProperty();
    private final StringProperty attachmentPath = new SimpleStringProperty();
    private final StringProperty createdByName = new SimpleStringProperty();
    private final StringProperty assignSousDirName = new SimpleStringProperty();
    private final StringProperty assignInspName = new SimpleStringProperty();
    private final StringProperty assignCtrlName = new SimpleStringProperty();
    private final StringProperty assignVerifName = new SimpleStringProperty();

    public int getId() { return id.get(); }
    public void setId(int v) { id.set(v); }
    public IntegerProperty idProperty() { return id; }
    public String getRefCode() { return refCode.get(); }
    public void setRefCode(String v) { refCode.set(v); }
    public StringProperty refCodeProperty() { return refCode; }
    public String getTitle() { return title.get(); }
    public void setTitle(String v) { title.set(v); }
    public StringProperty titleProperty() { return title; }
    public String getDescription() { return description.get(); }
    public void setDescription(String v) { description.set(v); }
    public String getStatus() { return status.get(); }
    public void setStatus(String v) { status.set(v); }
    public StringProperty statusProperty() { return status; }
    public Integer getTargetDirectionId() { int v = targetDirectionId.get(); return v == 0 ? null : v; }
    public void setTargetDirectionId(Integer v) { targetDirectionId.set(v == null ? 0 : v); }
    public Integer getTargetSousDirectionId() { int v = targetSousDirectionId.get(); return v == 0 ? null : v; }
    public void setTargetSousDirectionId(Integer v) { targetSousDirectionId.set(v == null ? 0 : v); }
    public String getTargetDirectionName() { return targetDirectionName.get(); }
    public void setTargetDirectionName(String v) { targetDirectionName.set(v != null ? v : ""); }
    public String getTargetSousDirectionName() { return targetSousDirectionName.get(); }
    public void setTargetSousDirectionName(String v) { targetSousDirectionName.set(v != null ? v : ""); }
    public int getCreatedBy() { return createdBy.get(); }
    public void setCreatedBy(int v) { createdBy.set(v); }
    public String getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(String v) { createdAt.set(v != null ? v : ""); }
    public void setAssignSousDirecteurId(Integer v) { assignedSousDirId.set(v == null ? 0 : v); }
    public void setAssignInspecteurId(Integer v) { assignedInspId.set(v == null ? 0 : v); }
    public void setAssignControleurId(Integer v) { assignedCtrlId.set(v == null ? 0 : v); }
    public void setAssignVerificateurId(Integer v) { assignedVerifId.set(v == null ? 0 : v); }
    public Integer getAssignSousDirecteurId() { int v = assignedSousDirId.get(); return v == 0 ? null : v; }
    public Integer getAssignInspecteurId() { int v = assignedInspId.get(); return v == 0 ? null : v; }
    public Integer getAssignControleurId() { int v = assignedCtrlId.get(); return v == 0 ? null : v; }
    public Integer getAssignVerificateurId() { int v = assignedVerifId.get(); return v == 0 ? null : v; }
    public String getCreatedByName() { return createdByName.get(); }
    public void setCreatedByName(String v) { createdByName.set(v != null ? v : ""); }
    public String getAssignSousDirName() { return assignSousDirName.get(); }
    public void setAssignSousDirName(String v) { assignSousDirName.set(v != null ? v : ""); }
    public String getAssignInspName() { return assignInspName.get(); }
    public void setAssignInspName(String v) { assignInspName.set(v != null ? v : ""); }
    public String getAssignCtrlName() { return assignCtrlName.get(); }
    public void setAssignCtrlName(String v) { assignCtrlName.set(v != null ? v : ""); }
    public String getAssignVerifName() { return assignVerifName.get(); }
    public void setAssignVerifName(String v) { assignVerifName.set(v != null ? v : ""); }
    public String getResolvedAt() { return resolvedAt.get(); }
    public void setResolvedAt(String v) { resolvedAt.set(v != null ? v : ""); }

    public String getSender() { return sender.get(); }
    public void setSender(String v) { sender.set(v != null ? v : ""); }
    public StringProperty senderProperty() { return sender; }
    public String getPriority() { return priority.get(); }
    public void setPriority(String v) { priority.set(v != null && !v.isBlank() ? v : "MEDIUM"); }
    public String getRegistrationDate() { return registrationDate.get(); }
    public void setRegistrationDate(String v) { registrationDate.set(v != null ? v : ""); }
    public String getAttachmentPath() { return attachmentPath.get(); }
    public void setAttachmentPath(String v) { attachmentPath.set(v != null ? v : ""); }

    private int secretaireMayRouteSousFlag;
    private Integer linkedTicketId;
    /** Comma-separated labels from {@code courier_packet_extra_directions} (list query only). */
    private String extraDirectionNames = "";

    public boolean isSecretaireMayRouteSous() {
        return secretaireMayRouteSousFlag == 1;
    }

    public void setSecretaireMayRouteSousFromDb(int v) {
        this.secretaireMayRouteSousFlag = v;
    }

    public Integer getLinkedTicketId() {
        return linkedTicketId;
    }

    public void setLinkedTicketId(Integer linkedTicketId) {
        this.linkedTicketId = linkedTicketId;
    }

    public String getExtraDirectionNames() {
        return extraDirectionNames != null ? extraDirectionNames : "";
    }

    public void setExtraDirectionNames(String extraDirectionNames) {
        this.extraDirectionNames = extraDirectionNames != null ? extraDirectionNames : "";
    }
}
