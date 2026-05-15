package com.app.controller;

import com.app.auth.Session;
import com.app.dao.UserAbsenceDAO;
import com.app.dao.UserDAO;
import com.app.model.User;
import com.app.model.UserAbsence;
import com.app.util.AgentAbsenceCalendarDialog;
import com.app.util.I18n;
import com.app.util.MissionOverviewDialog;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;

public class LeaveManagementController {

    @FXML private DatePicker dpAsOf;
    @FXML private Label lblSummary;
    @FXML private TableView<User> tblAvailable;
    @FXML private TableColumn<User, String> colAvailUser;
    @FXML private TableColumn<User, String> colAvailRole;
    @FXML private TableView<UserAbsence> tblOnLeave;
    @FXML private TableColumn<UserAbsence, String> colOffUser;
    @FXML private TableColumn<UserAbsence, String> colOffType;
    @FXML private TableColumn<UserAbsence, String> colOffFrom;
    @FXML private TableColumn<UserAbsence, String> colOffTo;
    @FXML private TableColumn<UserAbsence, String> colOffNotes;
    @FXML private ComboBox<User> cmbUser;
    @FXML private ComboBox<String> cmbType;
    @FXML private DatePicker dpStart;
    @FXML private DatePicker dpEnd;
    @FXML private TextField txtNotes;
    @FXML private TextField txtLeaveSearch;
    @FXML private TableView<UserAbsence> tblRecords;
    @FXML private TableColumn<UserAbsence, String> colRecUser;
    @FXML private TableColumn<UserAbsence, String> colRecType;
    @FXML private TableColumn<UserAbsence, String> colRecFrom;
    @FXML private TableColumn<UserAbsence, String> colRecTo;
    @FXML private TableColumn<UserAbsence, String> colRecNotes;

    private final ObservableList<UserAbsence> recordRows = FXCollections.observableArrayList();
    private final ObservableList<User> availMaster = FXCollections.observableArrayList();
    private final ObservableList<UserAbsence> offMaster = FXCollections.observableArrayList();
    private FilteredList<User> availFiltered;
    private FilteredList<UserAbsence> offFiltered;
    private FilteredList<UserAbsence> recordFiltered;

    @FXML
    public void initialize() {
        colAvailUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colAvailRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        colOffUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colOffFrom.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        colOffTo.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        colOffNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));
        colOffType.setCellValueFactory(new PropertyValueFactory<>("absenceType"));
        colOffType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                setText(empty || type == null ? null : absenceTypeLabel(type));
            }
        });

        colRecUser.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRecFrom.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        colRecTo.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        colRecNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));
        colRecType.setCellValueFactory(new PropertyValueFactory<>("absenceType"));
        colRecType.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                setText(empty || type == null ? null : absenceTypeLabel(type));
            }
        });

        tblAvailable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblOnLeave.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblRecords.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        tblOnLeave.setTooltip(new Tooltip(I18n.t("leaveCalendarDoubleClickHint",
                "Double-click a row to open this agent's calendar.")));
        tblOnLeave.setRowFactory(tv -> {
            TableRow<UserAbsence> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    UserAbsence a = row.getItem();
                    if (a != null && a.getUserId() > 0) {
                        String name = a.getUsername() != null ? a.getUsername() : "";
                        AgentAbsenceCalendarDialog.show(
                                tblOnLeave.getScene().getWindow(),
                                name,
                                a.getUserId());
                    }
                }
            });
            return row;
        });

        tblRecords.setTooltip(new Tooltip(I18n.t("leaveMissionRecordsDoubleClickHint",
                "Double-click to open mission details when linked to a field mission; otherwise opens the calendar.")));
        tblRecords.setRowFactory(tv -> {
            TableRow<UserAbsence> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    UserAbsence a = row.getItem();
                    if (a == null || a.getUserId() <= 0) {
                        return;
                    }
                    if (UserAbsence.TYPE_MISSION.equalsIgnoreCase(a.getAbsenceType())
                            && a.getMissionId() != null && a.getMissionId() > 0) {
                        MissionOverviewDialog.show(tblRecords.getScene().getWindow(), a.getMissionId());
                    } else {
                        String name = a.getUsername() != null ? a.getUsername() : "";
                        AgentAbsenceCalendarDialog.show(
                                tblRecords.getScene().getWindow(),
                                name,
                                a.getUserId());
                    }
                }
            });
            return row;
        });

        recordFiltered = new FilteredList<>(recordRows, a -> true);
        tblRecords.setItems(recordFiltered);
        tblRecords.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);

        availFiltered = new FilteredList<>(availMaster, u -> true);
        offFiltered = new FilteredList<>(offMaster, a -> true);
        tblAvailable.setItems(availFiltered);
        tblOnLeave.setItems(offFiltered);

        if (txtLeaveSearch != null) {
            txtLeaveSearch.textProperty().addListener((o, a, b) -> applyLeaveSearchFilter());
        }

        cmbType.getItems().setAll(
                UserAbsence.TYPE_LEAVE,
                UserAbsence.TYPE_HOLIDAY,
                UserAbsence.TYPE_ABSENCE);
        cmbType.setValue(UserAbsence.TYPE_LEAVE);
        cmbType.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : absenceTypeLabel(item));
            }
        });
        cmbType.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : absenceTypeLabel(item));
            }
        });

        wireUserCombo();

        if (dpAsOf != null) {
            dpAsOf.setValue(LocalDate.now());
            dpAsOf.valueProperty().addListener((o, a, b) -> {
                if (b != null) {
                    reloadSummary(b);
                }
            });
        }

        handleRefresh();
    }

    private void applyLeaveSearchFilter() {
        String raw = txtLeaveSearch != null && txtLeaveSearch.getText() != null
                ? txtLeaveSearch.getText().trim().toLowerCase()
                : "";
        if (raw.isEmpty()) {
            if (availFiltered != null) {
                availFiltered.setPredicate(u -> true);
            }
            if (offFiltered != null) {
                offFiltered.setPredicate(a -> true);
            }
            if (recordFiltered != null) {
                recordFiltered.setPredicate(a -> true);
            }
            return;
        }
        final String q = raw;
        if (availFiltered != null) {
            availFiltered.setPredicate(u -> u != null && userMatchesSearch(u, q));
        }
        if (offFiltered != null) {
            offFiltered.setPredicate(a -> a != null && absenceMatchesSearch(a, q));
        }
        if (recordFiltered != null) {
            recordFiltered.setPredicate(a -> a != null && absenceMatchesSearch(a, q));
        }
    }

    private static boolean userMatchesSearch(User u, String q) {
        return containsLower(u.getUsername(), q) || containsLower(u.getRole(), q);
    }

    private boolean absenceMatchesSearch(UserAbsence a, String q) {
        return containsLower(a.getUsername(), q)
                || containsLower(a.getAbsenceType(), q)
                || containsLower(absenceTypeLabel(a.getAbsenceType()), q)
                || containsLower(a.getStartDate(), q)
                || containsLower(a.getEndDate(), q)
                || containsLower(a.getNotes(), q);
    }

    private static boolean containsLower(String s, String q) {
        return s != null && !s.isBlank() && s.toLowerCase().contains(q);
    }

    private static String absenceTypeLabel(String code) {
        if (code == null) {
            return "";
        }
        if (UserAbsence.TYPE_HOLIDAY.equalsIgnoreCase(code)) {
            return I18n.t("leaveType.holiday", "Holiday");
        }
        if (UserAbsence.TYPE_ABSENCE.equalsIgnoreCase(code)) {
            return I18n.t("leaveType.absence", "Absence");
        }
        if (UserAbsence.TYPE_MISSION.equalsIgnoreCase(code)) {
            return I18n.t("leaveType.mission", "Mission");
        }
        return I18n.t("leaveType.leave", "Leave");
    }

    private void wireUserCombo() {
        cmbUser.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getUsername() + " — " + item.getRole());
            }
        });
        cmbUser.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getUsername());
            }
        });
    }

    @FXML
    private void handleRefresh() {
        reloadUserCombo();
        reloadRecords();
        LocalDate d = dpAsOf != null && dpAsOf.getValue() != null ? dpAsOf.getValue() : LocalDate.now();
        reloadSummary(d);
    }

    private void reloadUserCombo() {
        List<User> all = UserDAO.getAllUsers();
        ObservableList<User> active = FXCollections.observableArrayList();
        for (User u : all) {
            if (u != null && u.isActive()) {
                active.add(u);
            }
        }
        cmbUser.setItems(active);
        if (!active.isEmpty() && cmbUser.getValue() == null) {
            cmbUser.getSelectionModel().selectFirst();
        }
    }

    private void reloadRecords() {
        recordRows.setAll(UserAbsenceDAO.listAllRecent(500));
    }

    private void reloadSummary(LocalDate asOf) {
        Set<Integer> absentIds = new HashSet<>(UserAbsenceDAO.userIdsAbsentOn(asOf));
        absentIds.addAll(UserAbsenceDAO.userIdsWithUpcomingMissions(asOf, UserAbsence.UPCOMING_MISSION_EXCLUDE_DAYS));
        List<User> all = UserDAO.getAllUsers();
        ObservableList<User> avail = FXCollections.observableArrayList();
        for (User u : all) {
            if (u == null || !u.isActive()) {
                continue;
            }
            if (!absentIds.contains(u.getId())) {
                avail.add(u);
            }
        }
        availMaster.setAll(avail);

        List<UserAbsence> off = UserAbsenceDAO.listOverlappingCongeVacancesOnly(asOf);
        offMaster.setAll(off);

        applyLeaveSearchFilter();

        if (lblSummary != null) {
            lblSummary.setText(MessageFormat.format(
                    I18n.t("leaveSummaryCounts", "{0} available, {1} on leave or holiday for this date."),
                    availMaster.size(), offMaster.size()));
        }
    }

    @FXML
    private void handleAdd() {
        User u = cmbUser.getSelectionModel().getSelectedItem();
        if (u == null || u.getId() <= 0) {
            alert(Alert.AlertType.WARNING, I18n.t("leaveSelectUser", "Select a user."));
            return;
        }
        LocalDate start = dpStart.getValue();
        LocalDate end = dpEnd.getValue();
        if (start == null || end == null) {
            alert(Alert.AlertType.WARNING, I18n.t("leaveDatesRequired", "Choose start and end dates."));
            return;
        }
        if (end.isBefore(start)) {
            alert(Alert.AlertType.WARNING, I18n.t("leaveInvalidRange", "End date must be on or after start date."));
            return;
        }
        String notes = txtNotes.getText() != null ? txtNotes.getText().trim() : "";
        if (notes.isEmpty()) {
            alert(Alert.AlertType.WARNING, I18n.t("leaveNotesRequired", "Remarks are required."));
            return;
        }
        String type = cmbType.getValue() != null ? cmbType.getValue() : UserAbsence.TYPE_LEAVE;
        UserAbsence a = new UserAbsence();
        a.setUserId(u.getId());
        a.setStartDate(start.toString());
        a.setEndDate(end.toString());
        a.setAbsenceType(type);
        a.setNotes(notes);
        a.setCreatedBy(Session.getUserId());

        int id = UserAbsenceDAO.insert(a);
        if (id <= 0) {
            alert(Alert.AlertType.ERROR, I18n.t("leaveSaveFailed", "Could not save absence."));
            return;
        }
        alert(Alert.AlertType.INFORMATION, I18n.t("leaveSaved", "Absence recorded."));
        txtNotes.clear();
        handleRefresh();
    }

    @FXML
    private void handleDeleteSelected() {
        UserAbsence sel = tblRecords.getSelectionModel().getSelectedItem();
        if (sel == null) {
            return;
        }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t("leaveConfirmDeleteMission", "Delete this mission assignment record?"),
                ButtonType.OK, ButtonType.CANCEL);
        c.setHeaderText(null);
        Optional<ButtonType> r = c.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            if (UserAbsenceDAO.delete(sel.getId())) {
                handleRefresh();
            } else {
                alert(Alert.AlertType.ERROR, I18n.t("leaveDeleteFailed", "Could not delete."));
            }
        }
    }

    private void alert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
