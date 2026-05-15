package com.app.service;

import com.app.controller.MainController;
import com.app.dao.NotificationDAO;
import com.app.util.I18n;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Notifies users when they are assigned as mission lead or participant.
 */
public final class MissionNotificationService {

    private MissionNotificationService() {}

    public static void notifyMissionCreated(int missionId, String missionCode, String missionTitle,
            Integer leadUserId, List<Integer> participantUserIds) {
        String title = missionTitle != null ? missionTitle : "";
        String code = missionCode != null ? missionCode : "";

        if (leadUserId != null && leadUserId > 0) {
            NotificationDAO.create(
                    leadUserId,
                    I18n.t("notif.mission.leadTitle", "Mission — responsible"),
                    MessageFormat.format(
                            I18n.t("notif.mission.leadBody", "You are responsible for: {0}"),
                            title),
                    "MISSION",
                    "MISSION",
                    missionId,
                    code);
        }
        LinkedHashSet<Integer> participants = new LinkedHashSet<>(
                participantUserIds != null ? participantUserIds : List.of());
        for (Integer uid : participants) {
            if (uid == null || uid <= 0) {
                continue;
            }
            if (leadUserId != null && uid.equals(leadUserId)) {
                continue;
            }
            NotificationDAO.create(
                    uid,
                    I18n.t("notif.mission.participantTitle", "Mission — participant"),
                    MessageFormat.format(
                            I18n.t("notif.mission.participantBody", "You are a participant on: {0}"),
                            title),
                    "MISSION",
                    "MISSION",
                    missionId,
                    code);
        }
        MainController.refreshNotificationBadgesNow();
    }

    public static void notifyMissionUpdated(int missionId, String missionCode, String missionTitle,
            Integer newLeadUserId, List<Integer> newParticipantUserIds,
            Integer oldLeadUserId, List<Integer> oldParticipantUserIds) {
        String title = missionTitle != null ? missionTitle : "";
        String code = missionCode != null ? missionCode : "";
        Set<Integer> oldP = new HashSet<>(oldParticipantUserIds != null ? oldParticipantUserIds : List.of());

        boolean newLeadAssigned = newLeadUserId != null && newLeadUserId > 0
                && !Objects.equals(oldLeadUserId, newLeadUserId);
        if (newLeadAssigned) {
            NotificationDAO.create(
                    newLeadUserId,
                    I18n.t("notif.mission.leadTitle", "Mission — responsible"),
                    MessageFormat.format(
                            I18n.t("notif.mission.leadBody", "You are responsible for: {0}"),
                            title),
                    "MISSION",
                    "MISSION",
                    missionId,
                    code);
        }

        LinkedHashSet<Integer> newP = new LinkedHashSet<>(
                newParticipantUserIds != null ? newParticipantUserIds : List.of());
        for (Integer uid : newP) {
            if (uid == null || uid <= 0) {
                continue;
            }
            if (oldP.contains(uid)) {
                continue;
            }
            if (newLeadUserId != null && uid.equals(newLeadUserId)) {
                continue;
            }
            NotificationDAO.create(
                    uid,
                    I18n.t("notif.mission.participantTitle", "Mission — participant"),
                    MessageFormat.format(
                            I18n.t("notif.mission.participantBody", "You are a participant on: {0}"),
                            title),
                    "MISSION",
                    "MISSION",
                    missionId,
                    code);
        }
        MainController.refreshNotificationBadgesNow();
    }
}
