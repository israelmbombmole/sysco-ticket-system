package com.app.controller;

import com.app.auth.Session;
import com.app.dao.MissionDAO;
import com.app.dao.TicketCloseRequestDAO;
import com.app.dao.TicketDAO;
import com.app.dao.TicketTaskDAO;
import com.app.dao.UserDAO;
import com.app.dao.NotificationDAO;
import com.app.dao.DirectionDAO;
import com.app.model.FieldMission;
import com.app.model.MyWorkMissionRow;
import com.app.model.Ticket;
import com.app.model.TicketCloseRequest;
import com.app.model.ExternalEscalation;
import com.app.model.TicketTask;
import com.app.model.User;
import com.app.model.Direction;
import com.app.model.SousDirection;
import com.app.util.AppUiStyles;
import com.app.util.I18n;
import com.app.util.RoleFlowUtil;
import com.app.util.TimeUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MyWorkController {
    private static final DateTimeFormatter TABLE_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private Timeline autoRefreshTimeline;

    // ================= TABLES =================
    @FXML private TableView<Ticket> tableTickets;
    @FXML private TableView<MyWorkMissionRow> tableMyMissions;
    @FXML private TableView<TicketTask> tableTasks;

    // ================= TICKET COLUMNS =================
    @FXML private TableColumn<Ticket, Integer> colId;
    @FXML private TableColumn<Ticket, String> colTitle;
    @FXML private TableColumn<Ticket, String> colStatus;
    @FXML private TableColumn<Ticket, String> colPriority;
    @FXML private TableColumn<Ticket, String> colStart;
    @FXML private TableColumn<Ticket, String> colClose;
    @FXML private TableColumn<Ticket, String> colResolution;
    @FXML private TableColumn<Ticket, Void> colAction;

    @FXML private TableColumn<MyWorkMissionRow, String> colMissionCode;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionTitle;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionSite;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionLead;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionStart;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionEnd;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionStatus;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionMyRole;
    @FXML private TableColumn<MyWorkMissionRow, String> colMissionParticipants;
    @FXML private TableColumn<MyWorkMissionRow, Void> colMissionAction;

    // ================= TASK COLUMNS =================
    @FXML private TableColumn<TicketTask, Integer> colTaskId;
    @FXML private TableColumn<TicketTask, String> colTaskTitle;
    @FXML private TableColumn<TicketTask, String> colTaskStatus;
    @FXML private TableColumn<TicketTask, String> colTaskStart;
    @FXML private TableColumn<TicketTask, LocalDateTime> colTaskClose;
    @FXML private TableColumn<TicketTask, String> colTaskResolution;
    @FXML private TableColumn<TicketTask, Void> colTaskAction;
    @FXML private VBox closeRequestsSection;
    @FXML private TableView<TicketCloseRequest> tableCloseRequests;
    @FXML private TableColumn<TicketCloseRequest, String> colReqTicket;
    @FXML private TableColumn<TicketCloseRequest, String> colReqBy;
    @FXML private TableColumn<TicketCloseRequest, String> colReqReason;
    @FXML private TableColumn<TicketCloseRequest, String> colReqTime;
    @FXML private TableColumn<TicketCloseRequest, Void> colReqAction;
    @FXML private VBox externalEscalationsSection;
    @FXML private TableView<ExternalEscalation> tableExternalEscalations;
    @FXML private TableColumn<ExternalEscalation, String> colExtTicket;
    @FXML private TableColumn<ExternalEscalation, String> colExtFromDirection;
    @FXML private TableColumn<ExternalEscalation, String> colExtToDirection;
    @FXML private TableColumn<ExternalEscalation, String> colExtSousDirection;
    @FXML private TableColumn<ExternalEscalation, String> colExtRequestedApprover;
    @FXML private TableColumn<ExternalEscalation, String> colExtStatus;
    @FXML private TableColumn<ExternalEscalation, Void> colExtAction;

    // ================= INIT =================
    @FXML
    public void initialize() {

        setupTicketColumns();
        setupMyMissionColumns();
        setupTaskColumns();
        setupCloseRequestColumns();
        setupExternalEscalationColumns();

        applyMyWorkTablesFullWidth();
         
        loadTickets();
        loadMyMissions();
        loadTasks();
        loadCloseRequests();
        loadExternalEscalations();

        setupTaskDoubleClick();
        setupMyMissionsDoubleClick();
        startAutoRefresh();
    }

    /**
     * Keep column pref widths so each table can scroll horizontally on narrow screens (see MyWork.fxml
     * {@code ScrollPane} wrappers). Constrained resize would shrink columns and disable meaningful H-scroll.
     */
    private void applyMyWorkTablesFullWidth() {
        applyUnconstrainedColumns(tableTickets);
        applyUnconstrainedColumns(tableMyMissions);
        applyUnconstrainedColumns(tableTasks);
        applyUnconstrainedColumns(tableCloseRequests);
        applyUnconstrainedColumns(tableExternalEscalations);
    }

    private static void applyUnconstrainedColumns(TableView<?> tv) {
        if (tv == null) {
            return;
        }
        tv.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    // ================= LOAD =================

    private void loadTickets() {

    int userId = Session.getUserId();
    String role = Session.getRole();

    if ("ADMIN".equalsIgnoreCase(role)) {
        tableTickets.setItems(TicketDAO.getAssignedTickets());
    } else {
        tableTickets.setItems(TicketDAO.getAssignedTicketsByUser(userId));
    }

    addTicketButtons();
}

    private void loadTasks() {

        int userId = Session.getUserId();

        tableTasks.setItems(
                TicketTaskDAO.getTasksForAgent(userId)
        );

        addTaskButtons();
    }

    private void loadMyMissions() {
        if (tableMyMissions == null) {
            return;
        }
        int uid = Session.getUserId();
        List<FieldMission> list = MissionDAO.listForMyWork(uid);
        ObservableList<MyWorkMissionRow> rows = FXCollections.observableArrayList();
        for (FieldMission fm : list) {
            MissionDAO.loadParticipants(fm);
            boolean isLead = fm.getLeadUserId() != null && fm.getLeadUserId() == uid;
            boolean isPart = fm.getParticipantUserIds().contains(uid);
            String roleKind = isLead && isPart ? "BOTH" : isLead ? "LEAD" : "PARTICIPANT";
            String names = MissionDAO.getParticipantNamesCsv(fm.getId());
            rows.add(new MyWorkMissionRow(fm, roleKind, names));
        }
        tableMyMissions.setItems(rows);
    }

    private void setupMyMissionColumns() {
        if (colMissionCode == null) {
            return;
        }
        colMissionCode.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getMission().getMissionCode())));
        colMissionTitle.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getMission().getTitle())));
        colMissionSite.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getMission().getSiteLocation())));
        colMissionLead.setCellValueFactory(c -> {
            String u = c.getValue().getMission().getLeadUsername();
            return new SimpleStringProperty(u != null && !u.isBlank() ? u : "-");
        });
        colMissionStart.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getMission().getStartDate())));
        colMissionEnd.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getMission().getEndDate())));
        colMissionStatus.setCellValueFactory(c ->
                new SimpleStringProperty(safeStr(c.getValue().getMission().getStatus())));
        colMissionStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String st, boolean empty) {
                super.updateItem(st, empty);
                setText(empty || st == null || st.isBlank() ? null : MissionController.missionStatusLabel(st));
            }
        });
        colMissionMyRole.setCellValueFactory(c ->
                new SimpleStringProperty(myWorkMissionRoleLabel(c.getValue().getRoleKind())));
        colMissionParticipants.setCellValueFactory(c -> {
            String s = c.getValue().getParticipantsSummary();
            return new SimpleStringProperty(s != null && !s.isBlank() ? s : "-");
        });

        colMissionAction.setCellFactory(param -> new TableCell<>() {
            private final Button btn = new Button(I18n.t("myWork.mission.openDetails", "View mission"));

            {
                btn.setStyle(AppUiStyles.Gov.BTN_PRIMARY_11);
                btn.setMinWidth(100);
                btn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx < 0 || idx >= getTableView().getItems().size()) {
                        return;
                    }
                    MyWorkMissionRow row = getTableView().getItems().get(idx);
                    openMissionDetailsFromMyWork(row.getMission());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    private static String safeStr(String s) {
        return s != null ? s : "";
    }

    private static String myWorkMissionRoleLabel(String kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case "LEAD" -> I18n.t("myWork.mission.role.lead", "Responsible");
            case "PARTICIPANT" -> I18n.t("myWork.mission.role.participant", "Participant");
            case "BOTH" -> I18n.t("myWork.mission.role.both", "Responsible & participant");
            default -> kind;
        };
    }

    private void openMissionDetailsFromMyWork(FieldMission mission) {
        if (mission == null) {
            return;
        }
        MainController.loadPage("missions.fxml", controller -> {
            if (controller instanceof MissionController mc) {
                mc.focusMission(mission.getId());
            }
        });
    }

    private void setupMyMissionsDoubleClick() {
        if (tableMyMissions == null) {
            return;
        }
        tableMyMissions.setRowFactory(tv -> {
            TableRow<MyWorkMissionRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    openMissionDetailsFromMyWork(row.getItem().getMission());
                }
            });
            return row;
        });
    }

    // ================= COLUMN SETUP =================

    private void setupTicketColumns() {

        colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()).asObject());
        colTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitle()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : I18n.status(item));
            }
        });
        colPriority.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPriority()));
        colPriority.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : I18n.t("priority." + item.toUpperCase(), item));
            }
        });

        colStart.setCellValueFactory(c ->
                new SimpleStringProperty(formatDateTimeLabel(c.getValue().getStartedAt()))
        );

        colClose.setCellValueFactory(c ->
                new SimpleStringProperty(formatDateTimeLabel(c.getValue().getClosedAt()))
        );

        colResolution.setCellValueFactory(c -> {
            Integer minutes = c.getValue().getResolutionMinutes();
            return new SimpleStringProperty(formatMinutesLabel(minutes));
        });
    }

    /** Formats duration for ticket/task tables; null or non-positive shows "-". */
    private static String formatMinutesLabel(Integer minutes) {
        return TimeUtil.formatDurationMinutes(minutes);
    }

    private static String formatDateTimeLabel(LocalDateTime dt) {
        return dt == null ? "-" : dt.format(TABLE_DATETIME_FORMAT);
    }

    
    
    
    private void setupTaskColumns() {

    colTaskId.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().getId()).asObject());

    colTaskTitle.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getTitle()));

    colTaskStatus.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getStatus()));
    colTaskStatus.setCellFactory(col -> new TableCell<>() {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : I18n.status(item));
        }
    });

    // ================= START =================
    colTaskStart.setCellValueFactory(c ->
            new SimpleStringProperty(formatDateTimeLabel(c.getValue().getStartedAt()))
    );

    // ================= CLOSE (🔥 FIXED) =================
    colTaskClose.setCellValueFactory(c ->
            new SimpleObjectProperty<>(c.getValue().getClosedAt())
    );

    colTaskClose.setCellFactory(col -> new TableCell<>() {
        @Override
        protected void updateItem(LocalDateTime item, boolean empty) {
            super.updateItem(item, empty);

            setText(empty ? "-" : formatDateTimeLabel(item));
        }
    });

    // ================= DURATION =================
    colTaskResolution.setCellValueFactory(c ->
            new SimpleStringProperty(formatMinutesLabel(c.getValue().getDurationMinutes()))
    );
}

    // ================= BUTTONS =================

    private void addTicketButtons() {

    colAction.setPrefWidth(560); // ensure all action buttons are fully visible

    colAction.setCellFactory(param -> new TableCell<>() {

        private final Button view = new Button(I18n.t("button.view", "View"));
        private final Button start = new Button(I18n.t("start", "Start"));
        private final Button close = new Button(I18n.t("button.close", "Close"));
        private final Button reassign = new Button(I18n.t("reassign", "Reassign"));
        private final Button merge = new Button(I18n.t("mergeTicket", "Merge"));
        private final Button escalate = new Button(I18n.t("button.escalate", "Escalate"));

        {
            view.setStyle(AppUiStyles.Gov.BTN_PRIMARY_11);
            start.setStyle(AppUiStyles.Gov.BTN_WARN_11);
            close.setStyle(AppUiStyles.Gov.BTN_SUCCESS_11);
            reassign.setStyle(AppUiStyles.Gov.BTN_NEUTRAL_11);
            merge.setStyle(AppUiStyles.Gov.BTN_MERGE_11);
            escalate.setStyle(AppUiStyles.Gov.BTN_DANGER_11);

            // 🔥 FORCE WIDTH (VERY IMPORTANT)
            view.setMinWidth(60);
            start.setMinWidth(60);
            close.setMinWidth(95);
            reassign.setMinWidth(85);
            merge.setMinWidth(85);
            escalate.setMinWidth(85);

            // ================= VIEW =================
            view.setOnAction(e -> {
                Ticket t = getTableView().getItems().get(getIndex());
                openTicket(t);
            });

            // ================= START =================
            start.setOnAction(e -> {
    Ticket t = getTableView().getItems().get(getIndex());

    TicketDAO.startTicket(t.getId());

    // 🔥 FORCE FULL RELOAD
    tableTickets.getItems().clear();
    loadTickets();
});

            // ================= CLOSE =================
            close.setOnAction(e -> {
                Ticket t = getTableView().getItems().get(getIndex());
                String currentRole = Session.getRole() == null ? "" : Session.getRole().trim().toUpperCase();
                boolean isVerifier = "VERIFICATEUR".equals(currentRole) || "VERIFICATEUR-ASSISTANT".equals(currentRole);
                boolean hasTasks = TicketDAO.hasTasks(t.getId());
                try {
                    if (isVerifier && hasTasks) {
                        openCloseRequestDialog(t);
                    } else {
                        TicketDAO.closeTicket(t.getId());
                    }
                    tableTickets.getItems().clear();
                    loadTickets();
                    loadCloseRequests();
                } catch (RuntimeException ex) {
                    showWarning(ex.getMessage());
                }
            });

            // ================= ESCALATE =================
            escalate.setOnAction(e -> {
                Ticket t = getTableView().getItems().get(getIndex());
                escalateTicket(t);
            });

            // ================= REASSIGN =================
            reassign.setOnAction(e -> {
                Ticket t = getTableView().getItems().get(getIndex());
                reassignTicket(t);
            });

            // ================= MERGE =================
            merge.setOnAction(e -> {
                Ticket t = getTableView().getItems().get(getIndex());
                mergeTicketsIntoMaster(t);
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {

            if (empty) {
                setGraphic(null);
                return;
            }

            Ticket t = getTableView().getItems().get(getIndex());

            // 🔥 SMART ENABLE / DISABLE
            String status = t.getStatus() == null ? "" : t.getStatus().trim().toUpperCase();
            boolean canStartFromStatus =
                    "ASSIGNED".equals(status)
                    || "ESCALATED".equals(status);
            start.setDisable(!canStartFromStatus);
            boolean canCloseFromStatus =
                    "ASSIGNED".equals(status)
                    || "ESCALATED".equals(status)
                    || "IN_PROGRESS".equals(status)
                    || "WAITING_ON_TASKS".equals(status)
                    || "RESOLVED".equals(status);
            String currentRole = Session.getRole() == null ? "" : Session.getRole().trim().toUpperCase();
            boolean isVerifier = "VERIFICATEUR".equals(currentRole) || "VERIFICATEUR-ASSISTANT".equals(currentRole);
            boolean isAssignedToCurrentUser = t.getAssignedTo() != null && t.getAssignedTo() == Session.getUserId();
            boolean hasTasks = TicketDAO.hasTasks(t.getId());
            boolean useRequestMode = isVerifier && isAssignedToCurrentUser && hasTasks && !"CLOSED".equals(status);
            close.setText(useRequestMode
                    ? I18n.t("requestToClose", "Request to Close")
                    : I18n.t("button.close", "Close"));
            close.setDisable(!canCloseFromStatus || (isVerifier && !isAssignedToCurrentUser));
            boolean canReassign = !"CLOSED".equals(status)
                    && !"FERMÉ".equals(status)
                    && isAssignedToCurrentUser;
            reassign.setDisable(!canReassign);
            boolean canMerge = !"MERGED".equals(status)
                    && !"CLOSED".equals(status)
                    && !"FERMÉ".equals(status)
                    && isAssignedToCurrentUser;
            merge.setDisable(!canMerge);
            escalate.setDisable("CLOSED".equalsIgnoreCase(t.getStatus()));

            HBox box = new HBox(6, view, start, close, reassign, merge, escalate);
            box.setStyle("-fx-alignment:center;");

            setGraphic(box);
        }
    });
}

    private void reassignTicket(Ticket ticket) {
        if (ticket == null) return;
        ObservableList<User> users = TicketDAO.getTicketReassignCandidatesForCurrentUser();
        if (users.isEmpty()) {
            showWarning(I18n.t("err.noAssignableUsers", "No eligible users found for reassignment."));
            return;
        }
        ChoiceDialog<User> dialog = new ChoiceDialog<>(users.get(0), users);
        dialog.setTitle(I18n.t("reassign", "Reassign"));
        dialog.setHeaderText(I18n.t("selectUser", "Select user"));
        dialog.showAndWait().ifPresent(user -> {
            if (user.getId() == Session.getUserId()) {
                showWarning(I18n.t("err.cannotReassignToSelf", "You cannot reassign a ticket to yourself."));
                return;
            }
            try {
                TicketDAO.reassignTicket(ticket.getId(), user.getId());
                refresh();
            } catch (RuntimeException ex) {
                showWarning(ex.getMessage());
            }
        });
    }

    private void mergeTicketsIntoMaster(Ticket masterTicket) {
        if (masterTicket == null) return;
        ObservableList<Ticket> allMine = TicketDAO.getAssignedTicketsByUser(Session.getUserId());
        ObservableList<Ticket> candidates = FXCollections.observableArrayList();
        for (Ticket t : allMine) {
            if (t == null) continue;
            if (t.getId() == masterTicket.getId()) continue;
            String st = t.getStatus() == null ? "" : t.getStatus().trim().toUpperCase();
            if ("MERGED".equals(st)) continue;
            candidates.add(t);
        }
        if (candidates.isEmpty()) {
            showWarning(I18n.t("merge.noCandidates", "No ticket available to merge with this ticket."));
            return;
        }

        ListView<Ticket> listView = new ListView<>(candidates);
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Ticket item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(com.app.util.TicketUtil.formatTicketRef(item.getId()) + " - " + item.getTitle());
                }
            }
        });

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("mergeTicket", "Merge Ticket"));
        dialog.setHeaderText(I18n.t("merge.selectTickets", "Select one or more tickets to merge into")
                + " " + com.app.util.TicketUtil.formatTicketRef(masterTicket.getId()));
        dialog.getDialogPane().setContent(listView);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
            ObservableList<Ticket> selected = listView.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                showWarning(I18n.t("merge.selectAtLeastOne", "Select at least one ticket to merge."));
                return;
            }
            List<Integer> ids = selected.stream().map(Ticket::getId).toList();
            try {
                TicketDAO.mergeTicketsBulk(masterTicket.getId(), ids);
                refresh();
            } catch (RuntimeException ex) {
                showWarning(ex.getMessage());
            }
        }
    }

    
    private void openCloseRequestDialog(Ticket ticket) {
        ObservableList<User> candidates = TicketCloseRequestDAO.getCloseRequestCandidatesForCurrentUser();
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException(I18n.t("err.noHigherRoleForCloseRequest", "No higher-role user found for close request."));
        }

        ChoiceDialog<User> dialog = new ChoiceDialog<>(candidates.get(0), candidates);
        dialog.setTitle(I18n.t("requestToClose", "Request to Close"));
        dialog.setHeaderText(I18n.t("selectApproverForTicket", "Select approver for") + " " + ticket.getReference());
        dialog.setContentText(I18n.t("approver", "Approver") + ":");

        dialog.showAndWait().ifPresent(selected -> {
            TextInputDialog reasonDialog = new TextInputDialog(I18n.t("closeRequestDefaultReason", "Tasks completed. Requesting closure approval."));
            reasonDialog.setTitle(I18n.t("closeRequestReasonTitle", "Close Request Reason"));
            reasonDialog.setHeaderText(I18n.t("closeRequestReasonHeader", "Reason for close request"));
            reasonDialog.setContentText(I18n.t("reason", "Reason") + ":");

            String reason = reasonDialog.showAndWait().orElse(I18n.t("requestToClose", "Close request"));
            TicketDAO.requestCloseEscalation(ticket.getId(), selected.getId(), reason);
            showWarning(I18n.t("closeRequestSentTo", "Close request sent to ") + selected.getUsername() + ".");
        });
    }

    private void setupCloseRequestColumns() {
        if (tableCloseRequests == null) return;
        colReqTicket.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTicketRef()));
        colReqBy.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRequestedByName()));
        colReqReason.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReason()));
        colReqTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRequestedAt()));
        colReqAction.setCellFactory(param -> new TableCell<>() {
            private final Button approve = new Button(I18n.t("button.closeTicket", "Close Ticket"));
            private final Button reject = new Button(I18n.t("button.reject", "Reject"));

            {
                approve.setStyle(AppUiStyles.Gov.BTN_SUCCESS_11);
                reject.setStyle(AppUiStyles.Gov.BTN_DANGER_11);
                approve.setOnAction(e -> {
                    TicketCloseRequest req = getTableView().getItems().get(getIndex());
                    if (!confirmCloseRequest(req)) {
                        return;
                    }
                    try {
                        TicketCloseRequestDAO.approveAndClose(req.getId(), "Approved by " + Session.getUsername());
                        NotificationDAO.create(
                                req.getRequestedBy(),
                                "Close Request Approved",
                                Session.getUsername() + " approved and closed ticket " + req.getTicketRef(),
                                "TICKET_CLOSED",
                                "TICKET",
                                req.getTicketId(),
                                req.getTicketRef()
                        );
                        loadTickets();
                        loadCloseRequests();
                    } catch (RuntimeException ex) {
                        showWarning(ex.getMessage());
                    }
                });
                reject.setOnAction(e -> {
                    TicketCloseRequest req = getTableView().getItems().get(getIndex());
                    try {
                        TicketCloseRequestDAO.reject(req.getId(), "Rejected by " + Session.getUsername());
                        NotificationDAO.create(
                                req.getRequestedBy(),
                                "Close Request Rejected",
                                Session.getUsername() + " rejected close request for " + req.getTicketRef(),
                                "TICKET_CLOSE_REQUEST_REJECTED",
                                "TICKET",
                                req.getTicketId(),
                                req.getTicketRef()
                        );
                        loadCloseRequests();
                    } catch (RuntimeException ex) {
                        showWarning(ex.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                if (empty) {
                    setGraphic(null);
                    return;
                }
                setGraphic(new HBox(6, approve, reject));
            }
        });

        tableCloseRequests.setRowFactory(tv -> {
            TableRow<TicketCloseRequest> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    openCloseRequestTicket(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupExternalEscalationColumns() {
        if (tableExternalEscalations == null) return;

        colExtTicket.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTicketRef()));
        colExtFromDirection.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFromDirection()));
        colExtToDirection.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getToDirection()));
        colExtSousDirection.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getToSousDirection()));
        colExtRequestedApprover.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRequestedApproverName() == null ? "-" : c.getValue().getRequestedApproverName()
        ));
        colExtStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colExtStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : I18n.status(item));
            }
        });

        colExtAction.setCellFactory(param -> new TableCell<>() {
            private final Button action = new Button();
            {
                action.setStyle(AppUiStyles.Gov.BTN_PRIMARY_11);
                action.setOnAction(e -> {
                    ExternalEscalation esc = getTableView().getItems().get(getIndex());
                    String status = esc.getStatus() == null ? "" : esc.getStatus();
                    if ("PENDING_APPROVAL".equalsIgnoreCase(status)) {
                        approveExternalEscalation(esc);
                    } else {
                        assignExternalEscalation(esc);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                ExternalEscalation esc = (ExternalEscalation) getTableRow().getItem();
                String status = esc.getStatus() == null ? "" : esc.getStatus().toUpperCase();
                if ("PENDING_APPROVAL".equals(status)) {
                    action.setText(I18n.t("button.approve", "Approve"));
                    Integer approverId = esc.getRequestedApproverId();
                    boolean canApprove = "ADMIN".equalsIgnoreCase(Session.getRole())
                            || (approverId != null && approverId == Session.getUserId());
                    action.setDisable(!canApprove);
                } else {
                    action.setText(I18n.t("assign", "Assign"));
                    boolean canAssign = TicketDAO.canAssignExternalEscalations()
                            && ("PENDING".equals(status) || "ESCALATED".equals(status));
                    action.setDisable(!canAssign);
                }
                setGraphic(action);
            }
        });

        tableExternalEscalations.setRowFactory(tv -> {
            TableRow<ExternalEscalation> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    ExternalEscalation esc = row.getItem();
                    Ticket t = TicketDAO.getTicketById(esc.getTicketId());
                    if (t != null) openTicket(t);
                }
            });
            return row;
        });
    }

    private boolean confirmCloseRequest(TicketCloseRequest req) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(I18n.t("confirmClose", "Confirm Close"));
        confirm.setHeaderText(I18n.t("confirmCloseTicketHeader", "Close ticket") + " " + req.getTicketRef() + "?");
        confirm.setContentText(I18n.t("confirmCloseTicketMessage", "Do you want to close this ticket now?"));
        return confirm.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void openCloseRequestTicket(TicketCloseRequest req) {
        if (req == null) return;
        Ticket t = TicketDAO.getTicketById(req.getTicketId());
        if (t == null) {
            showWarning(I18n.t("err.ticketNotFound", "Ticket not found."));
            return;
        }
        openTicket(t);
    }

    private void loadCloseRequests() {
        if (tableCloseRequests == null || closeRequestsSection == null) return;
        String role = Session.getRole() == null ? "" : Session.getRole().trim().toUpperCase();
        boolean canApproveRequests = "DIRECTEUR".equals(role)
                || "SOUS-DIRECTEUR".equals(role)
                || "INSPECTEUR".equals(role)
                || "CONTROLEUR".equals(role);
        closeRequestsSection.setManaged(canApproveRequests);
        closeRequestsSection.setVisible(canApproveRequests);
        if (!canApproveRequests) {
            tableCloseRequests.getItems().clear();
            return;
        }
        tableCloseRequests.setItems(TicketCloseRequestDAO.getPendingForApprover(Session.getUserId()));
    }

    private void loadExternalEscalations() {
        if (tableExternalEscalations == null || externalEscalationsSection == null) return;
        boolean visible = TicketDAO.canViewExternalEscalations();
        externalEscalationsSection.setVisible(visible);
        externalEscalationsSection.setManaged(visible);
        if (!visible) {
            tableExternalEscalations.getItems().clear();
            return;
        }
        tableExternalEscalations.setItems(TicketDAO.getExternalEscalationsForCurrentUser());
    }

    private void approveExternalEscalation(ExternalEscalation escalation) {
        if (escalation == null) return;
        try {
            TicketDAO.approveExternalEscalationRequest(escalation.getId());
            loadExternalEscalations();
        } catch (RuntimeException ex) {
            showWarning(ex.getMessage());
        }
    }

    private void assignExternalEscalation(ExternalEscalation escalation) {
        if (escalation == null) return;
        try {
            ObservableList<User> users = UserDAO.getActiveUsersByDirectionAndSousDirection(
                    escalation.getToDirectionId(),
                    escalation.getToSousDirectionId()
            );
            if (users.isEmpty()) {
                showWarning(I18n.t("escalation.noAssignableInDirection",
                        "No active users available in target direction."));
                return;
            }
            ChoiceDialog<User> dialog = new ChoiceDialog<>(users.get(0), users);
            dialog.setTitle(I18n.t("assign", "Assign"));
            dialog.setHeaderText(I18n.t("escalation.selectTargetAssignee",
                    "Select user to handle this external escalation"));
            dialog.showAndWait().ifPresent(user -> {
                try {
                    TicketDAO.assignExternalEscalation(escalation.getId(), user.getId());
                    loadExternalEscalations();
                    loadTickets();
                } catch (RuntimeException ex) {
                    showWarning(ex.getMessage());
                }
            });
        } catch (Exception e) {
            showWarning(e.getMessage());
        }
    }

    public void focusCloseRequest(int requestId) {
        if (tableCloseRequests == null) return;
        loadCloseRequests();
        for (TicketCloseRequest req : tableCloseRequests.getItems()) {
            if (req.getId() == requestId) {
                tableCloseRequests.getSelectionModel().select(req);
                tableCloseRequests.scrollTo(req);
                break;
            }
        }
    }

    
    private void escalateTicket(Ticket ticket) {

    try {
        List<String> modes = FXCollections.observableArrayList(
                I18n.t("escalation.internal", "Internal"),
                I18n.t("escalation.external", "External")
        );
        ChoiceDialog<String> modeDialog = new ChoiceDialog<>(modes.get(0), modes);
        modeDialog.setTitle(I18n.t("escalateTicket", "Escalate Ticket"));
        modeDialog.setHeaderText(I18n.t("escalation.selectType", "Select escalation type"));

        Optional<String> selectedMode = modeDialog.showAndWait();
        if (selectedMode.isEmpty()) return;

        if (selectedMode.get().equals(I18n.t("escalation.internal", "Internal"))) {
            ObservableList<User> users = TicketDAO.getInternalEscalationCandidatesForCurrentUser();
            if (users.isEmpty()) {
                showWarning(I18n.t("escalation.noHigherUser", "No higher-role user available for internal escalation."));
                return;
            }
            ChoiceDialog<User> dialog = new ChoiceDialog<>(users.get(0), users);
            dialog.setTitle(I18n.t("escalateTicket", "Escalate Ticket"));
            dialog.setHeaderText(I18n.t("selectUserEscalateTo", "Select user to escalate to"));
            dialog.showAndWait().ifPresent(user -> {
                try {
                    TicketDAO.escalateTicketInternalHigherOnly(
                            ticket.getId(),
                            user.getId(),
                            "Escalated from MyWork (internal)"
                    );
                    refresh();
                } catch (RuntimeException ex) {
                    showWarning(ex.getMessage());
                }
            });
            return;
        }

        if (!TicketDAO.canCreateExternalEscalation()) {
            showWarning(I18n.t("escalation.externalNotAllowed",
                    "You are not allowed to create external escalations."));
            return;
        }

        List<Direction> directions = DirectionDAO.getAllDirections();
        if (directions.isEmpty()) {
            showWarning(I18n.t("escalation.noDirection", "No direction found."));
            return;
        }
        ChoiceDialog<Direction> directionDialog = new ChoiceDialog<>(directions.get(0), directions);
        directionDialog.setTitle(I18n.t("escalateTicket", "Escalate Ticket"));
        directionDialog.setHeaderText(I18n.t("escalation.selectDirection", "Select target direction"));
        Optional<Direction> selectedDirection = directionDialog.showAndWait();
        if (selectedDirection.isEmpty()) return;

        List<SousDirection> sousDirections = DirectionDAO.getSousDirectionsForDirection(selectedDirection.get().getId());
        if (sousDirections.isEmpty()) {
            showWarning(I18n.t("escalation.noSousDirection", "No sous-direction found."));
            return;
        }
        ChoiceDialog<SousDirection> sousDialog = new ChoiceDialog<>(sousDirections.get(0), sousDirections);
        sousDialog.setTitle(I18n.t("escalateTicket", "Escalate Ticket"));
        sousDialog.setHeaderText(I18n.t("escalation.selectSousDirection", "Select target sous-direction"));
        Optional<SousDirection> selectedSousDirection = sousDialog.showAndWait();
        if (selectedSousDirection.isEmpty()) return;

        try {
            String role = Session.getRole();
            if (RoleFlowUtil.requiresExternalEscalationApproval(role)) {
                ObservableList<User> approvers = TicketDAO.getExternalEscalationApproversForCurrentUser();
                if (approvers.isEmpty()) {
                    showWarning(I18n.t("escalation.noApprover", "No approver found for external escalation."));
                    return;
                }
                ChoiceDialog<User> approverDialog = new ChoiceDialog<>(approvers.get(0), approvers);
                approverDialog.setTitle(I18n.t("escalateTicket", "Escalate Ticket"));
                approverDialog.setHeaderText(I18n.t("escalation.selectApprover",
                        "Select approver for external escalation"));
                approverDialog.showAndWait().ifPresent(approver -> {
                    try {
                        TicketDAO.requestExternalEscalationApproval(
                                ticket.getId(),
                                selectedDirection.get().getId(),
                                selectedSousDirection.get().getId(),
                                approver.getId(),
                                "External escalation approval requested from MyWork"
                        );
                        showWarning(I18n.t("escalation.approvalRequested",
                                "External escalation approval request sent."));
                        refresh();
                    } catch (RuntimeException ex) {
                        showWarning(ex.getMessage());
                    }
                });
                return;
            }

            TicketDAO.escalateTicketExternally(
                    ticket.getId(),
                    selectedDirection.get().getId(),
                    selectedSousDirection.get().getId(),
                    "Escalated from MyWork (external)"
            );
            refresh();
        } catch (RuntimeException ex) {
            showWarning(ex.getMessage());
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
   private void addTaskButtons() {

    colTaskAction.setPrefWidth(220);

    colTaskAction.setCellFactory(param -> new TableCell<>() {

        private final Button start = new Button(I18n.t("start", "Start"));
        private final Button close = new Button(I18n.t("button.close", "Close"));
        private final Button reassign = new Button(I18n.t("reassign", "Reassign"));

        {
            start.setStyle(AppUiStyles.Gov.BTN_WARN_11);
            close.setStyle(AppUiStyles.Gov.BTN_SUCCESS_11);
            reassign.setStyle(AppUiStyles.Gov.BTN_NEUTRAL_11);

            start.setMinWidth(60);
            close.setMinWidth(60);
            reassign.setMinWidth(80);

            start.setOnAction(e -> {
                TicketTask t = getTableView().getItems().get(getIndex());
                TicketTaskDAO.startTask(t.getId());
                refresh();
            });

            close.setOnAction(e -> {
    TicketTask t = getTableView().getItems().get(getIndex());

    TicketTaskDAO.closeTask(t.getId()); // ✅ CORRECT METHOD

    refresh();
});
            reassign.setOnAction(e -> {
                TicketTask t = getTableView().getItems().get(getIndex());
                reassignTask(t);
            });
        }

        @Override
protected void updateItem(Void item, boolean empty) {

    if (empty || getTableRow() == null || getTableRow().getItem() == null) {
        setGraphic(null);
        return;
    }

    TicketTask t = (TicketTask) getTableRow().getItem();

    int currentUserId = Session.getUserId();
    boolean isMine = t.getAssignedTo() == currentUserId;

    String status = t.getStatus();
    if (status == null) status = "PENDING";

    // ✅ CORRECT LOGIC
    start.setDisable(!(isMine && status.equalsIgnoreCase("PENDING")));
    close.setDisable(!(isMine && status.equalsIgnoreCase("IN_PROGRESS")));
    reassign.setDisable(!isMine);

    HBox box = new HBox(6, start, close, reassign);
    box.setStyle("-fx-alignment:center;");

    setGraphic(box);
}
    });
}

    // ================= ACTIONS =================

  private void openTicket(Ticket t) {

    try {

        MainController.loadPage("TicketDetails.fxml", c -> {

            if (c instanceof TicketDetailsController controller) {
                controller.setTicket(t);
            }

        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}

  

    private void reassignTask(TicketTask task) {

        List<User> users = UserDAO.getAssignableUsers();

        if (users.isEmpty()) return;

        ChoiceDialog<User> dialog = new ChoiceDialog<>(users.get(0), users);
        dialog.setTitle(I18n.t("reassignTask", "Reassign Task"));
        dialog.setHeaderText(I18n.t("selectUser", "Select user"));

        dialog.showAndWait().ifPresent(user -> {
            TicketTaskDAO.reassignTask(task.getId(), user.getId());
            refresh();
        });
    }

    

    // ================= DOUBLE CLICK =================

    private void setupTaskDoubleClick() {

    tableTasks.setRowFactory(tv -> {
        TableRow<TicketTask> row = new TableRow<>();

        row.setOnMouseClicked(event -> {

            if (!row.isEmpty() && event.getClickCount() == 2) {

                TicketTask task = row.getItem();

                System.out.println("DOUBLE CLICK TASK → " + task.getId());

                openTaskDetails(task); // ✅ CORRECT
            }
        });

        return row;
    });
}
  
    private void openTaskDetails(TicketTask task) {

    System.out.println("DOUBLE CLICK TASK → " + task.getId());

    MainController.loadPage("TaskDetails.fxml", controller -> {

        if (controller instanceof TaskDetailsController tc) {
            tc.setTask(task);
        }

    });
}
    
    
    private void refresh() {
    tableTickets.getItems().clear();
    tableTasks.getItems().clear();

    loadTickets();
    loadMyMissions();
    loadTasks();
    loadCloseRequests();
    loadExternalEscalations();
}

    private void startAutoRefresh() {
        stopAutoRefresh();
        autoRefreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(5), e -> refreshSilently())
        );
        autoRefreshTimeline.setCycleCount(Animation.INDEFINITE);
        autoRefreshTimeline.play();
        if (tableTickets != null) {
            tableTickets.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene == null) {
                    stopAutoRefresh();
                }
            });
        }
    }

    private void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
        }
    }

    private void refreshSilently() {
        try {
            refresh();
        } catch (Exception ignored) {
            // Background refresh should not interrupt user actions.
        }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank()
                ? "Operation failed."
                : message);
        alert.showAndWait();
    }
    
   
    
 
    private void openReassignPopup(TicketTask task) {

    try {

        List<User> users = UserDAO.getAssignableUsers();

        if (users.isEmpty()) return;

        ChoiceDialog<User> dialog =
                new ChoiceDialog<>(users.get(0), users);

        dialog.setTitle(I18n.t("reassignTask", "Reassign Task"));
        dialog.setHeaderText(I18n.t("selectNewUser", "Select new user"));

        dialog.showAndWait().ifPresent(selected -> {

            TicketTaskDAO.reassignTask(task.getId(), selected.getId());

            refresh();
        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
    
    
}