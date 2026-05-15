package com.app.controller;

import com.app.dao.AutomationDAO;
import com.app.dao.TicketDAO;
import com.app.model.MonthlyReportSummary;
import com.app.model.ScheduledJob;
import com.app.model.User;
import com.app.util.I18n;
import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

public class JobSchedulerController {
    @FXML private TextField txtJobTitle;
    @FXML private TextArea txtJobDescription;
    @FXML private DatePicker dpDueDate;
    @FXML private TextField txtDueTime;
    @FXML private TextField txtReminderMinutes;
    @FXML private ListView<User> listAssignees;
    @FXML private ComboBox<String> cmbRecurrence;
    @FXML private DatePicker dpReportFrom;
    @FXML private DatePicker dpReportTo;

    @FXML private TableView<ScheduledJob> tableJobs;
    @FXML private TableColumn<ScheduledJob, String> colJobTitle;
    @FXML private TableColumn<ScheduledJob, String> colJobDueAt;
    @FXML private TableColumn<ScheduledJob, String> colJobAssignee;
    @FXML private TableColumn<ScheduledJob, String> colJobRecurrence;
    @FXML private TableColumn<ScheduledJob, String> colJobStatus;
    @FXML private TableColumn<ScheduledJob, Void> colJobAction;

    @FXML private TableView<MonthlyReportSummary> tableReports;
    @FXML private TableColumn<MonthlyReportSummary, String> colReportMonth;
    @FXML private TableColumn<MonthlyReportSummary, String> colReportGeneratedAt;
    @FXML private TableColumn<MonthlyReportSummary, String> colReportTotals;
    @FXML private TableColumn<MonthlyReportSummary, String> colReportPath;
    @FXML private TableColumn<MonthlyReportSummary, Void> colReportAction;

    @FXML
    public void initialize() {
        if (cmbRecurrence != null) {
            cmbRecurrence.getItems().setAll("ONCE", "MONTHLY");
            cmbRecurrence.setValue("ONCE");
        }
        if (txtDueTime != null) txtDueTime.setText("09:00");
        if (txtReminderMinutes != null) {
            txtReminderMinutes.setText("60");
            txtReminderMinutes.setTooltip(new Tooltip(I18n.t("jobReminderTooltip",
                    "Each assignee is notified this many minutes before the due date and time (not after saving the job).")));
        }

        if (listAssignees != null) {
            ObservableList<User> users = TicketDAO.getTicketReassignCandidatesForCurrentUser();
            listAssignees.setItems(users);
            listAssignees.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listAssignees.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(User item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getUsername() + " - " + item.getRole());
                }
            });
            listAssignees.setTooltip(new Tooltip(I18n.t("jobAssigneesHint",
                    "Hold Ctrl (or Cmd) while clicking to select several assignees.")));
        }

        setupJobsTable();
        setupReportsTable();
        loadJobs();
        loadReports();

        java.time.YearMonth prev = java.time.YearMonth.now().minusMonths(1);
        if (dpReportFrom != null) {
            dpReportFrom.setValue(prev.atDay(1));
        }
        if (dpReportTo != null) {
            dpReportTo.setValue(prev.atEndOfMonth());
        }
    }

    private void setupJobsTable() {
        if (tableJobs == null) return;
        tableJobs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colJobTitle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitle()));
        colJobDueAt.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDueAt()));
        colJobAssignee.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getAssigneeUsername() == null ? "-" : c.getValue().getAssigneeUsername()
        ));
        colJobRecurrence.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRecurrence()));
        colJobStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive()
                ? I18n.t("active", "Active")
                : I18n.t("inactive", "Inactive")));
        colJobAction.setCellFactory(param -> new TableCell<>() {
            private final Button toggle = new Button();
            private final Button btnDelete = new Button(I18n.t("button.delete", "Delete"));
            private final HBox box = new HBox(8, toggle, btnDelete);
            {
                toggle.setOnAction(e -> {
                    ScheduledJob j = getTableView().getItems().get(getIndex());
                    try {
                        AutomationDAO.setScheduledJobActive(j.getId(), !j.isActive());
                        loadJobs();
                    } catch (RuntimeException ex) {
                        showWarn(ex.getMessage());
                    }
                });
                btnDelete.setOnAction(e -> {
                    ScheduledJob j = getTableView().getItems().get(getIndex());
                    if (j == null) return;
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setHeaderText(null);
                    confirm.setContentText(I18n.t("jobDeleteConfirm", "Delete this scheduled task?"));
                    if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                        return;
                    }
                    try {
                        AutomationDAO.deleteScheduledJob(j.getId());
                        loadJobs();
                    } catch (RuntimeException ex) {
                        showWarn(ex.getMessage());
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
                ScheduledJob j = (ScheduledJob) getTableRow().getItem();
                toggle.setText(j.isActive() ? I18n.t("deactivate", "Deactivate") : I18n.t("activate", "Activate"));
                setGraphic(box);
            }
        });
    }

    private void setupReportsTable() {
        if (tableReports == null) return;
        tableReports.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colReportMonth.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMonthKey()));
        colReportGeneratedAt.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getGeneratedAt()));
        colReportTotals.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTotalTickets() + " (" +
                        I18n.t("open", "Open") + ":" + c.getValue().getOpenTickets() + ", " +
                        I18n.t("inProgress", "In Progress") + ":" + c.getValue().getInProgressTickets() + ", " +
                        I18n.t("closed", "Closed") + ":" + c.getValue().getClosedTickets() + ")"
        ));
        colReportPath.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFilePath()));
        colReportAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnSaveAs = new Button(I18n.t("saveAs", "Save As"));
            private final Button btnOpen = new Button(I18n.t("button.open", "Open"));
            private final Button btnDelete = new Button(I18n.t("button.delete", "Delete"));
            private final HBox box = new HBox(6, btnOpen, btnSaveAs, btnDelete);
            {
                btnOpen.setOnAction(e -> {
                    MonthlyReportSummary r = getTableView().getItems().get(getIndex());
                    openReport(r);
                });
                btnSaveAs.setOnAction(e -> {
                    MonthlyReportSummary r = getTableView().getItems().get(getIndex());
                    saveReportToPc(r);
                });
                btnDelete.setOnAction(e -> {
                    MonthlyReportSummary r = getTableView().getItems().get(getIndex());
                    deleteReport(r);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(box);
            }
        });
    }

    @FXML
    private void handleSaveJob() {
        String title = txtJobTitle == null ? "" : txtJobTitle.getText().trim();
        if (title.isBlank()) {
            showWarn(I18n.t("jobTitleRequired", "Job title is required."));
            return;
        }
        if (dpDueDate == null || dpDueDate.getValue() == null) {
            showWarn(I18n.t("dueDateRequired", "Due date is required."));
            return;
        }
        List<User> selectedAssignees = new ArrayList<>();
        if (listAssignees != null) {
            selectedAssignees.addAll(listAssignees.getSelectionModel().getSelectedItems());
        }
        if (selectedAssignees.isEmpty()) {
            showWarn(I18n.t("assigneesRequired", "Select at least one assignee (Ctrl+click for multiple)."));
            return;
        }
        String hhmm = txtDueTime == null || txtDueTime.getText() == null || txtDueTime.getText().isBlank()
                ? "09:00"
                : txtDueTime.getText().trim();
        LocalTime time;
        try {
            time = LocalTime.parse(hhmm.length() == 5 ? hhmm + ":00" : hhmm);
        } catch (Exception e) {
            try {
                time = LocalTime.parse(hhmm + ":00");
            } catch (Exception ex) {
                showWarn(I18n.t("invalidTimeFormat", "Invalid time format. Use HH:mm."));
                return;
            }
        }
        int remind = 60;
        try {
            remind = Integer.parseInt(txtReminderMinutes.getText().trim());
        } catch (Exception ignored) {
        }
        String recurrence = cmbRecurrence == null || cmbRecurrence.getValue() == null
                ? "ONCE"
                : cmbRecurrence.getValue().trim().toUpperCase();
        LocalDateTime due = LocalDateTime.of(dpDueDate.getValue(), time);
        String desc = txtJobDescription == null ? "" : txtJobDescription.getText();
        List<Integer> assigneeIds = selectedAssignees.stream().map(User::getId).distinct().toList();
        try {
            AutomationDAO.createScheduledJob(title, desc, due, remind, assigneeIds, recurrence);
            txtJobTitle.clear();
            txtJobDescription.clear();
            if (listAssignees != null) {
                listAssignees.getSelectionModel().clearSelection();
            }
            loadJobs();
        } catch (RuntimeException ex) {
            showWarn(ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadJobs();
        loadReports();
    }

    @FXML
    private void handleGenerateReportForPeriod() {
        if (dpReportFrom == null || dpReportTo == null
                || dpReportFrom.getValue() == null || dpReportTo.getValue() == null) {
            showWarn(I18n.t("reportPeriodRequired", "Please select start and end dates for the report period."));
            return;
        }
        LocalDate from = dpReportFrom.getValue();
        LocalDate to = dpReportTo.getValue();
        if (to.isBefore(from)) {
            showWarn(I18n.t("reportPeriodInvalid", "End date must be on or after start date."));
            return;
        }
        try {
            AutomationDAO.generateOperationalReport(from, to, true);
            loadReports();
            String periodKey = from + "_au_" + to;
            MonthlyReportSummary generated = AutomationDAO.getMonthlyReportByMonthKey(periodKey);
            if (generated != null) {
                saveReportToPc(generated);
            } else {
                showWarn(I18n.t("reportNotFoundAfterGenerate", "Report generated but could not be loaded."));
            }
        } catch (Exception e) {
            showWarn(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    private void loadJobs() {
        if (tableJobs == null) return;
        tableJobs.setItems(AutomationDAO.getAllScheduledJobs());
    }

    private void loadReports() {
        if (tableReports == null) return;
        tableReports.setItems(AutomationDAO.getMonthlyReports());
    }

    private void showWarn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(null);
        a.setContentText(msg == null ? I18n.t("warning", "Warning") : msg);
        a.showAndWait();
    }

    private void saveReportToPc(MonthlyReportSummary report) {
        File src = resolveReportFile(report);
        if (src == null) {
            showWarn(I18n.t("reportPathMissing", "Report file path is missing."));
            return;
        }
        try {
            if (!src.exists()) {
                showWarn(I18n.t("reportSourceMissing", "Report file does not exist on server path."));
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("saveMonthlyReport", "Save Monthly Report"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word Document (*.docx)", "*.docx"));
            String suggestedName = "rapport-exploitation-" + report.getMonthKey().replace(":", "-") + ".docx";
            chooser.setInitialFileName(suggestedName);
            File dest = chooser.showSaveDialog(tableReports.getScene().getWindow());
            if (dest == null) return;
            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setHeaderText(null);
            ok.setContentText(I18n.t("reportSavedSuccess", "Report saved successfully.") + "\n" + dest.getAbsolutePath());
            ok.showAndWait();
        } catch (Exception e) {
            showWarn(I18n.t("reportSaveFailed", "Failed to save report: ") + e.getMessage());
        }
    }

    private void openReport(MonthlyReportSummary report) {
        if (report == null) return;
        try {
            File file = resolveReportFile(report);
            if ((file == null || !file.exists()) && tryRebuildReport(report)) {
                loadReports();
                MonthlyReportSummary fresh = AutomationDAO.getMonthlyReportByMonthKey(report.getMonthKey());
                file = resolveReportFile(fresh);
            }
            if (file == null || !file.exists()) {
                showWarn(I18n.t("reportSourceMissing", "Report file does not exist on server path."));
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                showWarn(I18n.t("reportOpenNotSupported", "Open is not supported on this system."));
                return;
            }
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            showWarn(I18n.t("reportOpenFailed", "Failed to open report: ") + e.getMessage());
        }
    }

    private void deleteReport(MonthlyReportSummary report) {
        if (report == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText(null);
        confirm.setContentText(I18n.t("reportDeleteConfirm", "Delete this generated report?"));
        if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        try {
            AutomationDAO.deleteMonthlyReport(report.getId());
            loadReports();
        } catch (RuntimeException ex) {
            showWarn(ex.getMessage());
        }
    }

    private File resolveReportFile(MonthlyReportSummary report) {
        if (report == null || report.getMonthKey() == null || report.getMonthKey().isBlank()) {
            return null;
        }
        String path = report.getFilePath();
        if (path != null && !path.isBlank()) {
            File fromDb = new File(path);
            if (fromDb.exists()) return fromDb;
        }
        String key = report.getMonthKey().replace(":", "-");
        File localNew = new File("reports", "rapport-exploitation-" + key + ".docx");
        if (localNew.exists()) return localNew;
        File localLegacy = new File("reports", "monthly-report-" + report.getMonthKey() + ".docx");
        if (localLegacy.exists()) return localLegacy;
        return null;
    }

    private boolean tryRebuildReport(MonthlyReportSummary report) {
        try {
            if (report == null || report.getMonthKey() == null || report.getMonthKey().isBlank()) return false;
            String key = report.getMonthKey();
            AutomationDAO.deleteMonthlyReport(report.getId());
            if (key.contains("_au_")) {
                String[] parts = key.split("_au_");
                if (parts.length == 2) {
                    LocalDate s = LocalDate.parse(parts[0]);
                    LocalDate e = LocalDate.parse(parts[1]);
                    AutomationDAO.generateOperationalReport(s, e, true);
                    return true;
                }
            }
            YearMonth month = YearMonth.parse(key);
            AutomationDAO.generateMonthlyReport(month);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
