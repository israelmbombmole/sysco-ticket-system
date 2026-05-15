package com.app.model;

/**
 * One row in Mon travail — missions where the user is lead or participant.
 */
public final class MyWorkMissionRow {

    private final FieldMission mission;
    /** LEAD, PARTICIPANT, or BOTH */
    private final String roleKind;
    private final String participantsSummary;

    public MyWorkMissionRow(FieldMission mission, String roleKind, String participantsSummary) {
        this.mission = mission;
        this.roleKind = roleKind;
        this.participantsSummary = participantsSummary;
    }

    public FieldMission getMission() {
        return mission;
    }

    public String getRoleKind() {
        return roleKind;
    }

    public String getParticipantsSummary() {
        return participantsSummary;
    }
}
