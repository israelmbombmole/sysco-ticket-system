package com.app.controller;

import com.app.auth.Session;
import com.app.dao.DirectionDAO;
import com.app.dao.MissionDAO;
import com.app.dao.SousDirectionDAO;
import com.app.dao.UserDAO;
import com.app.model.Direction;
import com.app.model.FieldMission;
import com.app.model.SousDirection;
import com.app.service.MissionNotificationService;
import com.app.model.FieldMissionAttachment;
import com.app.model.User;
import com.app.util.AccessContext;
import com.app.util.I18n;
import com.app.util.MissionOrdreDialog;
import com.app.util.MissionOverviewDialog;
import com.app.util.RoleFlowUtil;

import java.awt.Desktop;
import java.io.File;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class MissionController {

    @FXML private ComboBox<String> cmbFilterStatus;
    @FXML private TextField txtSearch;
    @FXML private DatePicker dpFilterFrom;
    @FXML private DatePicker dpFilterTo;

    @FXML private TableView<FieldMission> tableMissions;
    @FXML private TableColumn<FieldMission, String> colCode;
    @FXML private TableColumn<FieldMission, String> colTitle;
    @FXML private TableColumn<FieldMission, String> colSite;
    @FXML private TableColumn<FieldMission, String> colStart;
    @FXML private TableColumn<FieldMission, String> colEnd;
    @FXML private TableColumn<FieldMission, String> colStatus;
    @FXML private TableColumn<FieldMission, String> colLead;

    @FXML private TextField txtCode;
    @FXML private TextField txtTitle;
    @FXML private TextField txtSite;
    @FXML private DatePicker dpStart;
    @FXML private DatePicker dpEnd;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private ComboBox<User> cmbLead;

    @FXML private TextArea txtDescription;
    @FXML private TextArea txtObjectives;
    @FXML private ComboBox<Direction> cmbParticipantDirection;
    @FXML private ComboBox<SousDirection> cmbParticipantSousDirection;
    @FXML private TextField txtParticipantSearch;
    @FXML private ListView<User> listParticipants;
    @FXML private TextArea txtReport;
    @FXML private Label lblReportSubmitted;
    @FXML private Label lblOrderSummary;

    @FXML private ListView<FieldMissionAttachment> listAttachments;
    @FXML private ListView<FieldMissionAttachment> listDetailAttachments;
    @FXML private TabPane tabMissionViews;

    @FXML private Button btnMissionNew;
    @FXML private Button btnMissionSave;
    @FXML private Button btnMissionDelete;
    @FXML private Button btnReportSave;
    @FXML private Button btnMarkReportSubmitted;
    @FXML private Button btnAddAttachment;
    @FXML private Button btnRemoveAttachment;
    @FXML private Button btnAddDetailAttachment;
    @FXML private Button btnRemoveDetailAttachment;

    private final ObservableList<FieldMission> missionRows = FXCollections.observableArrayList();
    private final ObservableList<User> allUsers = FXCollections.observableArrayList();
    private FilteredList<User> participantFiltered;
    private final LinkedHashSet<Integer> selectedParticipantIds = new LinkedHashSet<>();
    private boolean participantSelectionSync;
    private int editingId;

    @FXML
    public void initialize() {
        editingId = 0;
        if (cmbFilterStatus != null) {
            cmbFilterStatus.getItems().setAll(
                    "ALL",
                    FieldMission.STATUS_PLANNED,
                    FieldMission.STATUS_IN_PROGRESS,
                    FieldMission.STATUS_REPORTED
            );
            cmbFilterStatus.setValue("ALL");
        }
        if (cmbStatus != null) {
            cmbStatus.getItems().setAll(
                    FieldMission.STATUS_PLANNED,
                    FieldMission.STATUS_IN_PROGRESS,
                    FieldMission.STATUS_REPORTED
            );
            cmbStatus.setValue(FieldMission.STATUS_PLANNED);
        }

        wireMissionStatusDisplay();

        colCode.setCellValueFactory(new PropertyValueFactory<>("missionCode"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colSite.setCellValueFactory(new PropertyValueFactory<>("siteLocation"));
        colStart.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        colEnd.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        colStatus.setCellValueFactory(cd -> {
            FieldMission row = cd.getValue();
            String st = row != null ? row.getStatus() : null;
            return new ReadOnlyStringWrapper(st != null ? st : "");
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty || status == null || status.isBlank() ? null : missionStatusLabel(status));
            }
        });
        colLead.setCellValueFactory(new PropertyValueFactory<>("leadUsername"));

        tableMissions.setItems(missionRows);
        tableMissions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableMissions.setMaxWidth(Double.MAX_VALUE);
        tableMissions.setTooltip(new Tooltip(I18n.t("missionDoubleClickHint",
                "Double-click: summary (duration, participants, calendar) and details on the right.")));
        tableMissions.setRowFactory(tv -> {
            TableRow<FieldMission> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    FieldMission fm = row.getItem();
                    MissionOverviewDialog.show(row.getScene().getWindow(), fm.getId());
                    loadMissionIntoForm(fm.getId());
                    if (tabMissionViews != null) {
                        tabMissionViews.getSelectionModel().select(0);
                    }
                }
            });
            return row;
        });
        tableMissions.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                loadMissionIntoForm(n.getId());
            } else {
                clearFormPartial();
            }
        });

        listParticipants.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listParticipants.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<User>) c -> listParticipants.refresh());
        // Bold red list text; light selection bar so red stays readable.
        listParticipants.setStyle(
                "-fx-selection-bar: #ffcdd2; "
                        + "-fx-selection-bar-non-focused: #ffe0e0; "
                        + "-fx-selection-bar-text: #b71c1c;");
        listParticipants.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item.getUsername() + " — " + Objects.toString(item.getRole(), ""));
                styleParticipantListCell(this);
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                if (getItem() == null || isEmpty()) {
                    return;
                }
                styleParticipantListCell(this);
            }
        });

        participantFiltered = new FilteredList<>(allUsers, u -> true);
        listParticipants.setItems(participantFiltered);
        wireParticipantSelectionTracking();
        wireParticipantFilters();

        cmbLead.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item.getId() <= 0) {
                    setText(I18n.t("mission.leadNone", "—"));
                } else {
                    setText(item.getUsername() + " — " + Objects.toString(item.getRole(), ""));
                }
            }
        });
        cmbLead.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item.getId() <= 0) {
                    setText(I18n.t("mission.leadNone", "—"));
                } else {
                    setText(item.getUsername());
                }
            }
        });

        listAttachments.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FieldMissionAttachment item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFileName());
            }
        });
        if (listDetailAttachments != null) {
            listDetailAttachments.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(FieldMissionAttachment item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getFileName());
                }
            });
        }

        reloadUsers();
        handleRefresh();
        refreshMissionFormEditability();
    }

    /** Participant list: all rows red and bold (selected rows use slightly darker red on pink bar). */
    private static void styleParticipantListCell(ListCell<User> cell) {
        if (cell.isSelected()) {
            cell.setStyle("-fx-font-weight: bold; -fx-text-fill: #b71c1c;");
            return;
        }
        cell.setStyle("-fx-font-weight: bold; -fx-text-fill: #d32f2f;");
    }

    /** Open Missions and select a row (e.g. from notification). */
    public void focusMission(int missionId) {
        Platform.runLater(() -> Platform.runLater(() -> {
            if (tabMissionViews != null) {
                tabMissionViews.getSelectionModel().select(0);
            }
            loadTable();
            selectMissionInTable(missionId);
            loadMissionIntoForm(missionId);
        }));
    }

    private void reloadUsers() {
        allUsers.setAll(UserDAO.getAllUsersForMissionParticipantPicker());
        User none = new User(0, "—", "", true, "");
        ObservableList<User> leadItems = FXCollections.observableArrayList(none);
        leadItems.addAll(allUsers);
        cmbLead.setItems(leadItems);
        cmbLead.setValue(none);

        if (participantFiltered != null) {
            applyParticipantFilterPredicate();
            syncParticipantSelectionFromSetToListView();
        }
    }

    private void wireParticipantSelectionTracking() {
        listParticipants.getSelectionModel().getSelectedItems().addListener(
                (ListChangeListener<User>) c -> {
                    if (participantSelectionSync) {
                        return;
                    }
                    if (listParticipants.getItems() != participantFiltered) {
                        return;
                    }
                    while (c.next()) {
                        if (c.wasAdded()) {
                            for (User u : c.getAddedSubList()) {
                                if (u != null && u.getId() > 0) {
                                    selectedParticipantIds.add(u.getId());
                                }
                            }
                        }
                        if (c.wasRemoved()) {
                            for (User u : c.getRemoved()) {
                                if (u != null) {
                                    selectedParticipantIds.remove(u.getId());
                                }
                            }
                        }
                    }
                });
    }

    /**
     * When a direction is chosen, only sous-directions linked in {@code direction_sous_direction_map}
     * (plus legacy fallback in DAO) appear. With "All directions", every sous-direction is listed.
     */
    private void refreshParticipantSousDirectionCombo() {
        if (cmbParticipantSousDirection == null || cmbParticipantDirection == null) {
            return;
        }
        int prevSousId = cmbParticipantSousDirection.getValue() != null
                ? cmbParticipantSousDirection.getValue().getId()
                : 0;
        SousDirection allS = new SousDirection(0, I18n.t("mission.filterAllSousDirections", "All sub-departments"));
        ObservableList<SousDirection> items = FXCollections.observableArrayList(allS);
        Direction d = cmbParticipantDirection.getValue();
        if (d == null || d.getId() <= 0) {
            items.addAll(SousDirectionDAO.findAll());
        } else {
            items.addAll(DirectionDAO.getSousDirectionsForDirection(d.getId()));
        }
        cmbParticipantSousDirection.setItems(items);
        SousDirection pick = items.stream()
                .filter(x -> x.getId() == prevSousId)
                .findFirst()
                .orElse(allS);
        cmbParticipantSousDirection.setValue(pick);
    }

    private void wireParticipantFilters() {
        if (cmbParticipantDirection == null || cmbParticipantSousDirection == null) {
            return;
        }
        Direction allD = new Direction(0, I18n.t("mission.filterAllDirections", "All directions"));
        ObservableList<Direction> dirs = FXCollections.observableArrayList(allD);
        dirs.addAll(DirectionDAO.getAllDirections());
        cmbParticipantDirection.setItems(dirs);
        cmbParticipantDirection.setValue(allD);
        cmbParticipantDirection.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Direction item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        cmbParticipantDirection.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Direction item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        cmbParticipantSousDirection.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SousDirection item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        cmbParticipantSousDirection.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(SousDirection item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        refreshParticipantSousDirectionCombo();

        Runnable apply = () -> {
            applyParticipantFilterPredicate();
            syncParticipantSelectionFromSetToListView();
        };
        cmbParticipantDirection.valueProperty().addListener((o, a, b) -> refreshParticipantSousDirectionCombo());
        cmbParticipantSousDirection.valueProperty().addListener((o, a, b) -> apply.run());
        if (txtParticipantSearch != null) {
            txtParticipantSearch.textProperty().addListener((o, a, b) -> apply.run());
        }
        apply.run();
    }

    private void applyParticipantFilterPredicate() {
        if (participantFiltered == null) {
            return;
        }
        Direction d = cmbParticipantDirection != null ? cmbParticipantDirection.getValue() : null;
        SousDirection sd = cmbParticipantSousDirection != null ? cmbParticipantSousDirection.getValue() : null;
        String q = txtParticipantSearch != null && txtParticipantSearch.getText() != null
                ? txtParticipantSearch.getText().trim().toLowerCase()
                : "";
        participantFiltered.setPredicate(u -> {
            if (u == null) {
                return false;
            }
            if (d != null && d.getId() > 0) {
                if (u.getDirectionId() == null || u.getDirectionId() != d.getId()) {
                    return false;
                }
            }
            if (sd != null && sd.getId() > 0) {
                if (u.getSousDirectionId() == null || u.getSousDirectionId() != sd.getId()) {
                    return false;
                }
            }
            if (!q.isEmpty()) {
                boolean match = participantSearchContains(u, q);
                if (!match) {
                    return false;
                }
            }
            return true;
        });
    }

    private static boolean participantSearchContains(User u, String q) {
        return containsLower(u.getUsername(), q)
                || containsLower(u.getRole(), q)
                || containsLower(u.getDirectionName(), q)
                || containsLower(u.getSousDirectionName(), q);
    }

    private static boolean containsLower(String s, String q) {
        return s != null && !s.isBlank() && s.toLowerCase().contains(q);
    }

    private void syncParticipantSelectionFromSetToListView() {
        if (participantFiltered == null || listParticipants.getItems() != participantFiltered) {
            return;
        }
        participantSelectionSync = true;
        try {
            listParticipants.getSelectionModel().clearSelection();
            for (User u : participantFiltered) {
                if (selectedParticipantIds.contains(u.getId())) {
                    listParticipants.getSelectionModel().select(u);
                }
            }
        } finally {
            participantSelectionSync = false;
        }
    }

    private boolean participantListUsesFilteredPicker() {
        return listParticipants.getItems() == participantFiltered;
    }

    /**
     * Managers: searchable/filterable roster; selection is tracked in {@link #selectedParticipantIds} so it
     * survives filters. Others: only users stored as mission participants (not the lead unless also participant).
     */
    private void applyParticipantListAndSelection(FieldMission m, boolean mayManage) {
        if (mayManage) {
            listParticipants.setItems(participantFiltered);
            selectedParticipantIds.clear();
            if (m.getParticipantUserIds() != null) {
                selectedParticipantIds.addAll(m.getParticipantUserIds());
            }
            applyParticipantFilterPredicate();
            syncParticipantSelectionFromSetToListView();
        } else {
            ObservableList<User> subset = FXCollections.observableArrayList();
            if (m.getParticipantUserIds() != null) {
                for (int pid : m.getParticipantUserIds()) {
                    User u = allUsers.stream().filter(x -> x.getId() == pid).findFirst().orElse(null);
                    if (u == null) {
                        u = UserDAO.findById(pid);
                    }
                    if (u != null) {
                        boolean dup = false;
                        for (User x : subset) {
                            if (x.getId() == u.getId()) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) {
                            subset.add(u);
                        }
                    }
                }
            }
            listParticipants.setItems(subset);
            listParticipants.getSelectionModel().clearSelection();
            for (User u : subset) {
                listParticipants.getSelectionModel().select(u);
            }
        }
        listParticipants.refresh();
    }

    /** After clearing the mission form, restore participant list to full roster (creators) or empty (others). */
    private void resetParticipantsListWhenNoMissionSelected() {
        selectedParticipantIds.clear();
        if (mayCreateMission()) {
            listParticipants.setItems(participantFiltered);
            applyParticipantFilterPredicate();
            syncParticipantSelectionFromSetToListView();
        } else {
            listParticipants.setItems(FXCollections.observableArrayList());
            listParticipants.getSelectionModel().clearSelection();
        }
        listParticipants.refresh();
    }

    @FXML
    private void handleApplyFilters() {
        loadTable();
    }

    @FXML
    private void handleRefresh() {
        reloadUsers();
        loadTable();
        if (editingId > 0) {
            loadMissionIntoForm(editingId);
        }
    }


    /** Create new missions: ADMIN, DIRECTEUR, SOUS-DIRECTEUR, or INSPECTEUR only. */
    private static boolean mayCreateMission() {
        String r = Session.getRole();
        if (r == null || r.isBlank()) {
            return false;
        }
        r = r.trim();
        return "ADMIN".equalsIgnoreCase(r)
                || RoleFlowUtil.DIRECTEUR.equalsIgnoreCase(r)
                || RoleFlowUtil.SOUS_DIRECTEUR.equalsIgnoreCase(r)
                || RoleFlowUtil.INSPECTEUR.equalsIgnoreCase(r);
    }

    /**
     * Mission + ordre + pièces « détail » : créateur de la mission ou administrateur.
     * Compte rendu + pièces du rapport : auteur désigné (première sauvegarde) ou admin ; avant désignation, créateur ou responsable.
     */
    private void refreshMissionFormEditability() {
        int uid = Session.getUserId();
        boolean admin = editingId > 0 && MissionDAO.missionFullAdminAccess(editingId);
        boolean canCreate = mayCreateMission();
        boolean hasMission = editingId > 0;
        boolean mayManage = hasMission && MissionDAO.userMayManageMission(editingId, uid, admin);
        boolean mayEditReport = hasMission && MissionDAO.userMayEditMissionReport(editingId, uid, admin);
        boolean mayMarkSubmitted = hasMission && MissionDAO.userMayMarkReportSubmitted(editingId, uid, admin);
        boolean missionFormEditable = mayManage || (!hasMission && canCreate);
        boolean reportEditable = mayEditReport || (!hasMission && canCreate);

        if (txtTitle != null) {
            txtTitle.setEditable(missionFormEditable);
        }
        if (txtSite != null) {
            txtSite.setEditable(missionFormEditable);
        }
        if (dpStart != null) {
            dpStart.setDisable(!missionFormEditable);
        }
        if (dpEnd != null) {
            dpEnd.setDisable(!missionFormEditable);
        }
        if (cmbStatus != null) {
            cmbStatus.setDisable(!missionFormEditable);
        }
        if (cmbLead != null) {
            cmbLead.setDisable(!missionFormEditable);
        }
        if (txtDescription != null) {
            txtDescription.setEditable(missionFormEditable);
        }
        if (txtObjectives != null) {
            txtObjectives.setEditable(missionFormEditable);
        }
        if (listParticipants != null) {
            listParticipants.setDisable(!missionFormEditable);
        }
        if (cmbParticipantDirection != null) {
            cmbParticipantDirection.setDisable(!missionFormEditable);
        }
        if (cmbParticipantSousDirection != null) {
            cmbParticipantSousDirection.setDisable(!missionFormEditable);
        }
        if (txtParticipantSearch != null) {
            txtParticipantSearch.setDisable(!missionFormEditable);
        }
        if (txtReport != null) {
            txtReport.setEditable(reportEditable);
        }

        if (btnMissionNew != null) {
            btnMissionNew.setDisable(!canCreate);
            btnMissionNew.setVisible(canCreate);
            btnMissionNew.setManaged(canCreate);
        }
        if (btnMissionSave != null) {
            btnMissionSave.setDisable(!missionFormEditable);
        }
        if (btnMissionDelete != null) {
            btnMissionDelete.setDisable(!hasMission || !mayManage);
        }
        if (btnReportSave != null) {
            btnReportSave.setDisable(!reportEditable);
        }
        if (btnMarkReportSubmitted != null) {
            btnMarkReportSubmitted.setDisable(!mayMarkSubmitted);
        }
        if (btnAddAttachment != null) {
            btnAddAttachment.setDisable(!mayEditReport);
        }
        if (btnRemoveAttachment != null) {
            btnRemoveAttachment.setDisable(!mayEditReport);
        }
        if (btnAddDetailAttachment != null) {
            btnAddDetailAttachment.setDisable(!(hasMission && mayManage));
        }
        if (btnRemoveDetailAttachment != null) {
            btnRemoveDetailAttachment.setDisable(!(hasMission && mayManage));
        }
    }

    private void loadTable() {
        String st = cmbFilterStatus.getValue();
        String search = txtSearch.getText();
        LocalDate from = dpFilterFrom.getValue();
        LocalDate to = dpFilterTo.getValue();
        List<FieldMission> list = MissionDAO.listAll(
                st, search, from, to,
                AccessContext.isSystemSuperAdmin(),
                AccessContext.isDirectionAdmin() ? Session.getDirectionId() : null,
                Session.getUserId());
        missionRows.setAll(list);
    }

    private void loadMissionIntoForm(int id) {
        editingId = id;
        if (!MissionDAO.userMayViewMission(id, Session.getUserId(), MissionDAO.missionFullAdminAccess(id))) {
            tableMissions.getSelectionModel().clearSelection();
            clearFormPartial();
            refreshMissionFormEditability();
            return;
        }
        FieldMission m = MissionDAO.findById(id);
        if (m == null) {
            clearFormPartial();
            refreshMissionFormEditability();
            return;
        }
        MissionDAO.loadParticipants(m);

        boolean mayManage = MissionDAO.userMayManageMission(id, Session.getUserId(), MissionDAO.missionFullAdminAccess(id));

        txtCode.setText(m.getMissionCode() != null ? m.getMissionCode() : "");
        txtTitle.setText(m.getTitle() != null ? m.getTitle() : "");
        txtSite.setText(m.getSiteLocation() != null ? m.getSiteLocation() : "");
        dpStart.setValue(parseDate(m.getStartDate()));
        dpEnd.setValue(parseDate(m.getEndDate()));
        applyStatusToCombo(cmbStatus, m.getStatus());

        if (!cmbLead.getItems().isEmpty()) {
            if (m.getLeadUserId() != null) {
                User match = allUsers.stream().filter(u -> u.getId() == m.getLeadUserId()).findFirst().orElse(null);
                if (match != null) {
                    cmbLead.setValue(match);
                } else {
                    cmbLead.setValue(cmbLead.getItems().get(0));
                }
            } else {
                cmbLead.setValue(cmbLead.getItems().get(0));
            }
        }

        txtDescription.setText(m.getDescription() != null ? m.getDescription() : "");
        txtObjectives.setText(m.getObjectives() != null ? m.getObjectives() : "");
        refreshOrderSummaryLabel(m);
        txtReport.setText(m.getReportText() != null ? m.getReportText() : "");
        lblReportSubmitted.setText(m.getReportSubmittedAt() != null ? m.getReportSubmittedAt() : "—");

        applyParticipantListAndSelection(m, mayManage);

        listAttachments.setItems(FXCollections.observableArrayList(MissionDAO.listAttachments(id)));
        if (listDetailAttachments != null) {
            listDetailAttachments.setItems(FXCollections.observableArrayList(MissionDAO.listDetailAttachments(id)));
        }
        refreshMissionFormEditability();
    }

    private void wireMissionStatusDisplay() {
        javafx.util.Callback<ListView<String>, ListCell<String>> filterCellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : missionFilterOrStatusLabel(item));
            }
        };
        cmbFilterStatus.setCellFactory(filterCellFactory);
        cmbFilterStatus.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : missionFilterOrStatusLabel(item));
            }
        });

        javafx.util.Callback<ListView<String>, ListCell<String>> detailCellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : missionStatusLabel(item));
            }
        };
        cmbStatus.setCellFactory(detailCellFactory);
        cmbStatus.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : missionStatusLabel(item));
            }
        });
    }

    private static String missionFilterOrStatusLabel(String code) {
        if (code != null && "ALL".equalsIgnoreCase(code)) {
            return I18n.t("mission.status.all", "All");
        }
        return missionStatusLabel(code);
    }

    /** Display label for stored codes PLANNED / IN_PROGRESS / REPORTED (EN/FR from bundle). */
    public static String missionStatusLabel(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return switch (code.trim()) {
            case FieldMission.STATUS_PLANNED ->
                    I18n.t("mission.status.planned", "Planned");
            case FieldMission.STATUS_IN_PROGRESS ->
                    I18n.t("mission.status.inProgress", "In progress");
            case FieldMission.STATUS_REPORTED ->
                    I18n.t("mission.status.reported", "Report submitted");
            default -> code;
        };
    }

    /**
     * ComboBox only accepts values that exist in its items list; DB values that are blank or unknown
     * otherwise cause IllegalArgumentException when selecting a row.
     */
    private static void applyStatusToCombo(ComboBox<String> combo, String statusFromDb) {
        if (combo == null || combo.getItems().isEmpty()) {
            return;
        }
        String fallback = FieldMission.STATUS_PLANNED;
        if (statusFromDb == null || statusFromDb.isBlank()) {
            combo.setValue(fallback);
            return;
        }
        String s = statusFromDb.trim();
        if (combo.getItems().contains(s)) {
            combo.setValue(s);
        } else {
            combo.setValue(fallback);
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    @FXML
    private void handleNew() {
        tableMissions.getSelectionModel().clearSelection();
        editingId = 0;
        clearFormPartial();
        if (tabMissionViews != null) {
            tabMissionViews.getSelectionModel().select(0);
        }
        refreshMissionFormEditability();
    }

    private void clearFormPartial() {
        editingId = 0;
        txtCode.setText("");
        txtTitle.clear();
        txtSite.clear();
        dpStart.setValue(null);
        dpEnd.setValue(null);
        cmbStatus.setValue(FieldMission.STATUS_PLANNED);
        if (!cmbLead.getItems().isEmpty()) {
            cmbLead.setValue(cmbLead.getItems().get(0));
        }
        txtDescription.clear();
        txtObjectives.clear();
        refreshOrderSummaryLabel(null);
        txtReport.clear();
        lblReportSubmitted.setText("—");
        resetParticipantsListWhenNoMissionSelected();
        listAttachments.getItems().clear();
        if (listDetailAttachments != null) {
            listDetailAttachments.getItems().clear();
        }
        refreshMissionFormEditability();
    }

    @FXML
    private void handleSave() {
        int uid = Session.getUserId();
        boolean admin = MissionDAO.missionFullAdminAccess(editingId);
        if (editingId > 0) {
            boolean mayManage = MissionDAO.userMayManageMission(editingId, uid, admin);
            boolean mayEditReport = MissionDAO.userMayEditMissionReport(editingId, uid, admin);
            if (!mayManage && mayEditReport) {
                if (!MissionDAO.updateMissionReportOnly(editingId, txtReport.getText())) {
                    alert(Alert.AlertType.ERROR, I18n.t("mission.saveFailed", "Could not save mission."));
                    return;
                }
                loadTable();
                loadMissionIntoForm(editingId);
                alert(Alert.AlertType.INFORMATION, I18n.t("mission.reportSaved", "Mission report saved."));
                return;
            }
            if (!mayManage) {
                alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionReport",
                        "You cannot edit the mission report for this mission."));
                return;
            }
        } else if (!mayCreateMission()) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionCreate",
                    "You do not have permission to create missions."));
            return;
        }

        String title = txtTitle.getText() != null ? txtTitle.getText().trim() : "";
        if (title.isEmpty()) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.titleRequired", "Please enter a mission title."));
            return;
        }

        Window owner = txtTitle.getScene() != null ? txtTitle.getScene().getWindow() : null;
        if (owner == null && tableMissions.getScene() != null) {
            owner = tableMissions.getScene().getWindow();
        }
        if (owner == null) {
            return;
        }
        Optional<MissionOrdreDialog.Result> ordreOpt = MissionOrdreDialog.show(owner, editingId);
        if (ordreOpt.isEmpty()) {
            return;
        }
        MissionOrdreDialog.Result ordre = ordreOpt.get();

        FieldMission m = new FieldMission();
        m.setId(editingId);
        m.setTitle(title);
        m.setSiteLocation(txtSite.getText());
        m.setStartDate(dpStart.getValue() != null ? dpStart.getValue().toString() : null);
        m.setEndDate(dpEnd.getValue() != null ? dpEnd.getValue().toString() : null);
        m.setDescription(txtDescription.getText());
        m.setObjectives(txtObjectives.getText());
        m.setStatus(cmbStatus.getValue() != null ? cmbStatus.getValue() : FieldMission.STATUS_PLANNED);
        String reportForSave = txtReport.getText();
        if (editingId > 0 && !MissionDAO.userMayEditMissionReport(editingId, uid, admin)) {
            FieldMission ex = MissionDAO.findById(editingId);
            if (ex != null) {
                reportForSave = ex.getReportText();
            }
        }
        m.setReportText(reportForSave);
        m.setOrderReference(ordre.orderReference());
        m.setOrderIssueDate(ordre.orderIssueDate());
        m.setOrderIssuedBy(ordre.orderIssuedBy());
        m.setOrderBody(ordre.orderBody());

        User leadSel = cmbLead.getValue();
        if (leadSel != null && leadSel.getId() > 0) {
            m.setLeadUserId(leadSel.getId());
        } else {
            m.setLeadUserId(null);
        }

        String submittedLbl = lblReportSubmitted.getText();
        if (submittedLbl != null && !submittedLbl.isBlank() && !"—".equals(submittedLbl)) {
            m.setReportSubmittedAt(submittedLbl);
        } else if (editingId > 0) {
            FieldMission existing = MissionDAO.findById(editingId);
            m.setReportSubmittedAt(existing != null ? existing.getReportSubmittedAt() : null);
        } else {
            m.setReportSubmittedAt(null);
        }

        List<Integer> pids = participantListUsesFilteredPicker()
                ? new ArrayList<>(selectedParticipantIds)
                : listParticipants.getSelectionModel().getSelectedItems().stream()
                        .map(User::getId)
                        .collect(Collectors.toList());

        Integer oldLead = null;
        List<Integer> oldPids = new ArrayList<>();
        if (editingId > 0) {
            FieldMission previous = MissionDAO.findById(editingId);
            if (previous != null) {
                oldLead = previous.getLeadUserId();
                MissionDAO.loadParticipants(previous);
                oldPids = new ArrayList<>(previous.getParticipantUserIds());
            }
        }

        if (editingId <= 0) {
            int newId = MissionDAO.insert(m, pids);
            if (newId <= 0) {
                alert(Alert.AlertType.ERROR, I18n.t("mission.saveFailed", "Could not save mission."));
                return;
            }
            FieldMission saved = MissionDAO.findById(newId);
            String code = saved != null && saved.getMissionCode() != null ? saved.getMissionCode() : "";
            MissionNotificationService.notifyMissionCreated(newId, code, title, m.getLeadUserId(), pids);
            for (File f : ordre.pendingOrderAttachmentFiles()) {
                MissionDAO.addOrderAttachment(newId, f);
            }
            loadTable();
            handleNew();
            String successMsg = code.isBlank()
                    ? I18n.t("mission.createdSuccess", "Mission created successfully.")
                    : MessageFormat.format(
                            I18n.t("mission.createdSuccessDetail", "Mission {0} was created successfully."),
                            code);
            alert(Alert.AlertType.INFORMATION, successMsg);
        } else {
            if (!MissionDAO.update(m, pids)) {
                alert(Alert.AlertType.ERROR, I18n.t("mission.saveFailed", "Could not save mission."));
                return;
            }
            FieldMission saved = MissionDAO.findById(editingId);
            String code = saved != null && saved.getMissionCode() != null ? saved.getMissionCode() : "";
            MissionNotificationService.notifyMissionUpdated(
                    editingId, code, title, m.getLeadUserId(), pids, oldLead, oldPids);
            loadTable();
            selectMissionInTable(editingId);
            loadMissionIntoForm(editingId);
        }
    }

    private void refreshOrderSummaryLabel(FieldMission m) {
        if (lblOrderSummary == null) {
            return;
        }
        if (m == null) {
            lblOrderSummary.setText("—");
            return;
        }
        String ref = m.getOrderReference() != null ? m.getOrderReference().trim() : "";
        String issuedBy = m.getOrderIssuedBy() != null ? m.getOrderIssuedBy().trim() : "";
        LocalDate od = parseDate(m.getOrderIssueDate());
        String dateStr = od != null ? od.toString()
                : (m.getOrderIssueDate() != null ? m.getOrderIssueDate().trim() : "");
        boolean noDate = od == null && (m.getOrderIssueDate() == null || m.getOrderIssueDate().isBlank());
        boolean noBody = m.getOrderBody() == null || m.getOrderBody().isBlank();
        if (ref.isEmpty() && noDate && issuedBy.isEmpty() && noBody) {
            lblOrderSummary.setText(I18n.t("missionOrderSummaryNone", "No mission order recorded yet."));
            return;
        }
        lblOrderSummary.setText(MessageFormat.format(
                I18n.t("missionOrderSummaryPattern", "{0} · {1} · {2}"),
                ref.isEmpty() ? "—" : ref,
                dateStr.isEmpty() ? "—" : dateStr,
                issuedBy.isEmpty() ? "—" : issuedBy));
    }

    private void selectMissionInTable(int id) {
        for (FieldMission row : missionRows) {
            if (row.getId() == id) {
                tableMissions.getSelectionModel().select(row);
                tableMissions.scrollTo(row);
                return;
            }
        }
    }

    @FXML
    private void handleDelete() {
        if (editingId <= 0) {
            return;
        }
        if (!MissionDAO.userMayManageMission(editingId, Session.getUserId(), MissionDAO.missionFullAdminAccess(editingId))) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionAct",
                    "Only the mission creator or an administrator can change mission details, the official mission order, attachments, or mark the report as submitted."));
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("mission.confirmDelete", "Delete this mission and its attachments?"),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            if (MissionDAO.delete(editingId)) {
                handleNew();
                loadTable();
            } else {
                alert(Alert.AlertType.ERROR, I18n.t("mission.deleteFailed", "Could not delete mission."));
            }
        }
    }

    @FXML
    private void handleMarkReportSubmitted() {
        if (editingId <= 0) {
            alert(Alert.AlertType.INFORMATION, I18n.t("mission.saveFirstForReport", "Save the mission first, then mark the report as submitted."));
            return;
        }
        if (!MissionDAO.userMayMarkReportSubmitted(editingId, Session.getUserId(), MissionDAO.missionFullAdminAccess(editingId))) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionMarkReport",
                    "Only the report author (or an administrator) can mark the report as submitted."));
            return;
        }
        MissionDAO.setReportSubmittedNow(editingId);
        loadMissionIntoForm(editingId);
        loadTable();
    }

    @FXML
    private void handleAddAttachment() {
        if (editingId <= 0) {
            alert(Alert.AlertType.INFORMATION, I18n.t("mission.saveFirstForAttachment", "Save the mission before adding attachments."));
            return;
        }
        if (!MissionDAO.userMayEditMissionReport(editingId, Session.getUserId(), MissionDAO.missionFullAdminAccess(editingId))) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionReportEdit",
                    "Only the person who wrote the report (or an administrator) can add or remove report attachments."));
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.t("mission.chooseFile", "Choose file"));
        File f = ch.showOpenDialog(txtTitle.getScene().getWindow());
        if (f != null && MissionDAO.addAttachment(editingId, f)) {
            listAttachments.setItems(FXCollections.observableArrayList(MissionDAO.listAttachments(editingId)));
        }
    }

    @FXML
    private void handleOpenAttachment() {
        FieldMissionAttachment a = listAttachments.getSelectionModel().getSelectedItem();
        if (a == null) {
            return;
        }
        File f = new File(a.getFilePath());
        if (!f.isFile()) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.fileMissing", "File not found on disk."));
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(f);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRemoveAttachment() {
        FieldMissionAttachment a = listAttachments.getSelectionModel().getSelectedItem();
        if (a == null || editingId <= 0) {
            return;
        }
        if (!MissionDAO.userMayEditMissionReport(editingId, Session.getUserId(), MissionDAO.missionFullAdminAccess(editingId))) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionReportEdit",
                    "Only the person who wrote the report (or an administrator) can add or remove report attachments."));
            return;
        }
        MissionDAO.deleteAttachment(a.getId());
        listAttachments.setItems(FXCollections.observableArrayList(MissionDAO.listAttachments(editingId)));
    }

    @FXML
    private void handleAddDetailAttachment() {
        if (editingId <= 0) {
            alert(Alert.AlertType.INFORMATION, I18n.t("mission.saveFirstForAttachment", "Save the mission before adding attachments."));
            return;
        }
        if (!MissionDAO.userMayManageMission(editingId, Session.getUserId(), MissionDAO.missionFullAdminAccess(editingId))) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionAct",
                    "Only the mission creator or an administrator can change mission details, the official mission order, or mission attachments."));
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.t("mission.chooseFile", "Choose file"));
        File f = ch.showOpenDialog(txtTitle.getScene().getWindow());
        if (f != null && MissionDAO.addDetailAttachment(editingId, f) && listDetailAttachments != null) {
            listDetailAttachments.setItems(FXCollections.observableArrayList(MissionDAO.listDetailAttachments(editingId)));
        }
    }

    @FXML
    private void handleOpenDetailAttachment() {
        if (listDetailAttachments == null) {
            return;
        }
        FieldMissionAttachment a = listDetailAttachments.getSelectionModel().getSelectedItem();
        if (a == null) {
            return;
        }
        File file = new File(a.getFilePath());
        if (!file.isFile()) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.fileMissing", "File not found on disk."));
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRemoveDetailAttachment() {
        if (listDetailAttachments == null) {
            return;
        }
        FieldMissionAttachment a = listDetailAttachments.getSelectionModel().getSelectedItem();
        if (a == null || editingId <= 0) {
            return;
        }
        if (!MissionDAO.userMayManageMission(editingId, Session.getUserId(), MissionDAO.missionFullAdminAccess(editingId))) {
            alert(Alert.AlertType.WARNING, I18n.t("mission.noPermissionAct",
                    "Only the mission creator or an administrator can change mission details, the official mission order, or mission attachments."));
            return;
        }
        MissionDAO.deleteDetailAttachment(a.getId());
        listDetailAttachments.setItems(FXCollections.observableArrayList(MissionDAO.listDetailAttachments(editingId)));
    }

    private void alert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
