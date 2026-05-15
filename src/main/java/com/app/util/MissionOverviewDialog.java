package com.app.util;

import com.app.auth.Session;
import com.app.dao.MissionDAO;
import com.app.model.FieldMission;
import com.app.model.User;
import com.app.dao.UserDAO;

import java.text.MessageFormat;
import java.time.LocalDate;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Leave-style snapshot: mission duration, participants, and mission-period calendar.
 */
public final class MissionOverviewDialog {

    private MissionOverviewDialog() {}

    public static void show(Window owner, int missionId) {
        boolean admin = "ADMIN".equalsIgnoreCase(Session.getRole());
        if (!MissionDAO.userMayViewMission(missionId, Session.getUserId(), admin)) {
            return;
        }
        FieldMission m = MissionDAO.findById(missionId);
        if (m == null) {
            return;
        }
        MissionDAO.loadParticipants(m);

        LocalDate start = parseIsoDate(m.getStartDate());
        LocalDate end = parseIsoDate(m.getEndDate());

        ObservableList<User> participantRows = FXCollections.observableArrayList();
        for (int uid : m.getParticipantUserIds()) {
            User u = UserDAO.findById(uid);
            if (u != null) {
                participantRows.add(u);
            }
        }

        ObservableList<MissionPeriodRow> periodRows = FXCollections.observableArrayList();
        periodRows.add(new MissionPeriodRow(
                I18n.t("leaveType.mission", "Mission"),
                formatDate(m.getStartDate()),
                formatDate(m.getEndDate()),
                m.getTitle() != null ? m.getTitle() : ""));

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(MessageFormat.format(
                I18n.t("missionOverviewWindowTitle", "Mission — {0}"),
                m.getMissionCode() != null ? m.getMissionCode() : ""));

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color:#f5f7fb;");

        Label lblHead = new Label(m.getTitle() != null ? m.getTitle() : "");
        lblHead.setStyle("-fx-font-size:17px; -fx-font-weight:bold;");
        lblHead.setWrapText(true);

        String sub = MessageFormat.format("{0} · {1}",
                m.getMissionCode() != null ? m.getMissionCode() : "—",
                m.getSiteLocation() != null && !m.getSiteLocation().isBlank()
                        ? m.getSiteLocation()
                        : I18n.t("missionOverviewNoSite", "No site"));
        Label lblSub = new Label(sub);
        lblSub.setStyle("-fx-text-fill:#374151;");

        String lead = m.getLeadUsername() != null ? m.getLeadUsername() : "—";
        String status = missionStatusLabelForOverview(m.getStatus());
        String durationLine = MessageFormat.format(
                I18n.t("missionOverviewSummaryLine", "{0} — {1} · {2} · {3}"),
                I18n.t("missionLead", "Lead") + ": " + lead,
                I18n.t("status", "Status") + ": " + status,
                I18n.t("missionStart", "Start") + ": " + formatDate(m.getStartDate()),
                I18n.t("missionEnd", "End") + ": " + formatDate(m.getEndDate()));
        Label lblSummary = new Label(durationLine);
        lblSummary.setWrapText(true);
        lblSummary.setStyle("-fx-text-fill:#374151;");

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnMissionOrder = new Button(I18n.t("missionViewMissionOrder", "View mission order"));
        btnMissionOrder.setOnAction(e -> MissionOrdreDialog.showViewOnly(stage, missionId));
        Button btnClose = new Button(I18n.t("close", "Close"));
        btnClose.setOnAction(e -> stage.close());
        topBar.getChildren().addAll(lblHead, spacer, btnMissionOrder, btnClose);
        VBox.setMargin(lblSub, new Insets(-8, 0, 0, 0));

        TableView<User> tblParticipants = new TableView<>(participantRows);
        tblParticipants.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblParticipants.setPrefHeight(220);
        TableColumn<User, String> colPu = new TableColumn<>(I18n.t("username", "Username"));
        colPu.setCellValueFactory(new PropertyValueFactory<>("username"));
        TableColumn<User, String> colPr = new TableColumn<>(I18n.t("role", "Role"));
        colPr.setCellValueFactory(new PropertyValueFactory<>("role"));
        tblParticipants.getColumns().setAll(colPu, colPr);

        VBox leftBox = new VBox(8, new Label(I18n.t("missionOverviewParticipants", "Participants")), tblParticipants);
        VBox.setVgrow(tblParticipants, Priority.ALWAYS);

        TableView<MissionPeriodRow> tblPeriod = new TableView<>(periodRows);
        tblPeriod.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblPeriod.setPrefHeight(220);
        TableColumn<MissionPeriodRow, String> colT = new TableColumn<>(I18n.t("leaveType", "Type"));
        colT.setCellValueFactory(new PropertyValueFactory<>("typeLabel"));
        TableColumn<MissionPeriodRow, String> colF = new TableColumn<>(I18n.t("leaveDateFrom", "From"));
        colF.setCellValueFactory(new PropertyValueFactory<>("dateFrom"));
        TableColumn<MissionPeriodRow, String> colTo = new TableColumn<>(I18n.t("leaveDateTo", "To"));
        colTo.setCellValueFactory(new PropertyValueFactory<>("dateTo"));
        TableColumn<MissionPeriodRow, String> colN = new TableColumn<>(I18n.t("missionOverviewRemarksCol", "Detail"));
        colN.setCellValueFactory(new PropertyValueFactory<>("remarks"));
        tblPeriod.getColumns().setAll(colT, colF, colTo, colN);

        VBox rightBox = new VBox(8,
                new Label(I18n.t("missionOverviewMissionPeriod", "Mission period")),
                tblPeriod);
        VBox.setVgrow(tblPeriod, Priority.ALWAYS);

        SplitPane split = new SplitPane(leftBox, rightBox);
        split.setDividerPositions(0.5);
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox calendarCard = new VBox(10);
        calendarCard.setStyle("-fx-background-color:white; -fx-padding:12; -fx-background-radius:8;");
        Label lblCal = new Label(I18n.t("missionOverviewCalendarSection", "Calendar (mission period)"));
        lblCal.setStyle("-fx-font-weight:bold;");
        VBox calNode = AgentAbsenceCalendarDialog.createMissionPeriodCalendarNode(start, end);
        ScrollPane calScroll = new ScrollPane(calNode);
        calScroll.setFitToWidth(true);
        calScroll.setPrefHeight(280);
        calendarCard.getChildren().addAll(lblCal, calScroll);

        root.getChildren().addAll(topBar, lblSub, lblSummary, split, calendarCard);
        VBox.setVgrow(split, Priority.ALWAYS);

        Scene scene = new Scene(root, 880, 640);
        AppUiStyles.applyToScene(scene);
        stage.setScene(scene);
        stage.show();
    }

    private static String formatDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "—";
        }
        String t = raw.trim();
        return t.length() >= 10 ? t.substring(0, 10) : t;
    }

    private static LocalDate parseIsoDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String t = raw.trim();
            return LocalDate.parse(t.length() >= 10 ? t.substring(0, 10) : t);
        } catch (Exception e) {
            return null;
        }
    }

    private static String missionStatusLabelForOverview(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return switch (code.trim()) {
            case FieldMission.STATUS_PLANNED -> I18n.t("mission.status.planned", "Planned");
            case FieldMission.STATUS_IN_PROGRESS -> I18n.t("mission.status.inProgress", "In progress");
            case FieldMission.STATUS_REPORTED -> I18n.t("mission.status.reported", "Report submitted");
            default -> code;
        };
    }

    /** Table row: mission as a single "period" line (like leave list). */
    public static class MissionPeriodRow {
        private final String typeLabel;
        private final String dateFrom;
        private final String dateTo;
        private final String remarks;

        public MissionPeriodRow(String typeLabel, String dateFrom, String dateTo, String remarks) {
            this.typeLabel = typeLabel;
            this.dateFrom = dateFrom;
            this.dateTo = dateTo;
            this.remarks = remarks;
        }

        public String getTypeLabel() {
            return typeLabel;
        }

        public String getDateFrom() {
            return dateFrom;
        }

        public String getDateTo() {
            return dateTo;
        }

        public String getRemarks() {
            return remarks;
        }
    }

}
