package com.app.controller;

import com.app.auth.Session;
import com.app.dao.AttachmentDAO;
import com.app.dao.EscalationDAO;
import com.app.dao.NotificationDAO;
import com.app.dao.TicketTaskDAO;
import com.app.model.Attachment;
import com.app.model.Escalation;
import com.app.model.TicketEvent;
import com.app.model.TicketTask;
import com.app.util.I18n;
import com.app.util.TimelineEventDescriptionLocalizer;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;



import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

public class TaskDetailsController {
    private static final DateTimeFormatter ASSIGNED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DB_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter UI_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private Label lblTitle;
    @FXML private Label lblAssigned;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private Slider sliderProgress;
    @FXML private Label lblProgress;
    @FXML private TextArea txtDescription;
    @FXML private VBox timelineContainer;
    @FXML private TextField txtNewComment;

    @FXML private ListView<Attachment> attachmentList;
    @FXML private StackPane previewContainer;
    @FXML private Label lblPreviewPlaceholder;
    @FXML private VBox escalationBox;
    @FXML private Label lblNoEscalation;
    @FXML private TableView<TicketTask> tableTasks;
    @FXML private VBox taskContainer;

    @FXML private TableColumn<TicketTask, String> colTitle;
    @FXML private TableColumn<TicketTask, String> colAssigned;
    @FXML private TableColumn<TicketTask, String> colAssignedAt;
    @FXML private TableColumn<TicketTask, String> colStatus;
    
    

    private TicketTask task;
    private int taskId;
   


    
    
    
    
    // ✅ FIX: THIS WAS MISSING (YOUR ERROR)
    private Attachment selectedAttachment;

    // ============================
    // INIT
    // ============================
    @FXML
public void initialize() {

    // ============================
    // STATUS
    // ============================
    cmbStatus.getItems().addAll("PENDING", "IN_PROGRESS", "COMPLETED");
    cmbStatus.setCellFactory(cb -> new ListCell<>() {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : I18n.status(item));
        }
    });
    cmbStatus.setButtonCell(new ListCell<>() {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : I18n.status(item));
        }
    });

    // ============================
    // ATTACHMENT CLICK (PREVIEW)
    // ============================
    attachmentList.setOnMouseClicked(e -> {

        selectedAttachment =
                attachmentList.getSelectionModel().getSelectedItem();

        if (selectedAttachment != null) {
            previewFile(selectedAttachment.getFilePath());
        }
    });

    // ============================
    // 🔴 ROW COLOR (ESCALATION)
    // ============================
    tableTasks.setRowFactory(tv -> new TableRow<>() {
        @Override
        protected void updateItem(TicketTask task, boolean empty) {
            super.updateItem(task, empty);

            if (task == null || empty) {
                setStyle("");
            } else if (task.isEscalated()) {
                setStyle("-fx-background-color:#fee2e2;");
            } else {
                setStyle("");
            }
        }
    });

    colTitle.setCellValueFactory(c ->
    new SimpleStringProperty(c.getValue().getTitle())
);

colAssigned.setCellValueFactory(c -> {
    TicketTask t = c.getValue();
    String assignee = t.getAssignedToName();
    if (assignee == null || assignee.isBlank() || "-".equals(assignee)) {
        assignee = t.getCreatedByName();
    }
    return new SimpleStringProperty(assignee);
});

colAssignedAt.setCellValueFactory(c -> {
    TicketTask t = c.getValue();
    return new SimpleStringProperty(formatAssignedAt(t.getCreatedAt(), t.getCreatedByName()));
});

colStatus.setCellValueFactory(c ->
    new SimpleStringProperty(c.getValue().getStatus())
);

colStatus.setCellFactory(column -> new TableCell<>() {
    @Override
    protected void updateItem(String status, boolean empty) {
        super.updateItem(status, empty);
        if (empty || status == null) {
            setText(null);
            setStyle("");
            return;
        }

        setText(I18n.status(status));
        switch (status) {
            case "PENDING" ->
                setStyle("-fx-font-weight:bold; -fx-alignment:CENTER; -fx-text-fill:#b45309;");
            case "IN_PROGRESS" ->
                setStyle("-fx-font-weight:bold; -fx-alignment:CENTER; -fx-text-fill:#1d4ed8;");
            case "COMPLETED" ->
                setStyle("-fx-font-weight:bold; -fx-alignment:CENTER; -fx-text-fill:#15803d;");
            default ->
                setStyle("-fx-font-weight:bold; -fx-alignment:CENTER;");
        }
    }
});

tableTasks.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
tableTasks.setFixedCellSize(34);
tableTasks.setPlaceholder(new Label(I18n.t("noTasksAvailable", "No tasks available")));
    
    
    
    
    // ============================
    // PROGRESS AUTO UPDATE
    // ============================
    sliderProgress.valueProperty().addListener((obs, oldVal, newVal) -> {

        int progress = newVal.intValue();
        lblProgress.setText(progress + "%");

        if (progress == 0) {
            cmbStatus.setValue("PENDING");
        } else if (progress < 100) {
            cmbStatus.setValue("IN_PROGRESS");
        } else {
            cmbStatus.setValue("COMPLETED");
        }
    });

    // ============================
    // DESCRIPTION READ-ONLY
    // ============================
    txtDescription.setEditable(false);
}

    // ============================
    // SET TASK
    // ============================
    public void setTask(TicketTask task) {

    if (task == null) return;

    this.task = task;

    lblTitle.setText(task.getTitle());

    String assigned = TicketTaskDAO.getAssignedUsersNames(task.getId());

if (assigned == null || assigned.isEmpty()) {
    assigned = task.getAssignedToName(); // fallback
}

lblAssigned.setText(I18n.t("assignedTo", "Assigned To") + ": " + (assigned != null ? assigned : "N/A"));

    txtDescription.setText(
            task.getDescription() != null ? task.getDescription() : ""
    );

    cmbStatus.setValue(task.getStatus());

    switch (task.getStatus()) {
        case "PENDING" -> sliderProgress.setValue(0);
        case "IN_PROGRESS" -> sliderProgress.setValue(50);
        case "COMPLETED" -> sliderProgress.setValue(100);
        default -> sliderProgress.setValue(0);
    }

    lblProgress.setText((int) sliderProgress.getValue() + "%");

    // 🔥 LOAD EVERYTHING
    loadTimeline();
    loadAttachments();
    int ticketIdForTasks = task.getTicketId();
    if (ticketIdForTasks <= 0) {
        TicketTask fresh = TicketTaskDAO.getTaskById(task.getId());
        if (fresh != null) {
            ticketIdForTasks = fresh.getTicketId();
        }
    }
    loadTasks(ticketIdForTasks);   // ← IMPORTANT
    loadEscalations(task.getId());   // ← IMPORTANT
}

    

    // ============================
    // PREVIEW
    // ============================
    private void previewFile(String path) {

        previewContainer.getChildren().clear();

        File file = new File(path);

        if (!file.exists()) {
            previewContainer.getChildren().add(new Label(I18n.t("fileNotFound", "File not found")));
            return;
        }

        String name = file.getName().toLowerCase();

        try {

            // IMAGE
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {

                javafx.scene.image.Image img =
                        new javafx.scene.image.Image(file.toURI().toString());

                javafx.scene.image.ImageView view =
                        new javafx.scene.image.ImageView(img);

                view.setFitWidth(300);
                view.setPreserveRatio(true);

                previewContainer.getChildren().add(view);
            }

            // TEXT
            else if (name.endsWith(".txt") || name.endsWith(".log")) {

                String content = Files.readString(file.toPath());

                TextArea text = new TextArea(content);
                text.setWrapText(true);
                text.setEditable(false);

                previewContainer.getChildren().add(text);
            }

            // OTHER
            else {

                VBox box = new VBox(10);
                box.setStyle("-fx-alignment:center;");

                Label label = new Label(I18n.t("previewNotAvailable", "Preview not available"));

                Button openBtn = new Button(I18n.t("openFile", "Open File"));
                openBtn.setOnAction(ev -> handleOpenAttachment());

                box.getChildren().addAll(label, openBtn);

                previewContainer.getChildren().add(box);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================
    // OPEN
    // ============================
    @FXML
    private void handleOpenAttachment() {

        if (selectedAttachment == null) {
            showWarning(I18n.t("noFileSelected", "No file selected"));
            return;
        }

        try {
            File file = new File(selectedAttachment.getFilePath());

            if (!file.exists()) {
                showWarning(I18n.t("fileNotFound", "File not found"));
                return;
            }

            Desktop.getDesktop().open(file);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================
    // DOWNLOAD
    // ============================
    @FXML
private void handleDownloadAttachment() {

    if (selectedAttachment == null) {
        showWarning(I18n.t("noFileSelected", "No file selected"));
        return;
    }

    File source = new File(selectedAttachment.getFilePath());

    if (!source.exists()) {
        showWarning(I18n.t("fileNotFound", "File not found"));
        return;
    }

    String originalName = source.getName();

    // ✅ Extract extension
    String extension = "";
    int dotIndex = originalName.lastIndexOf(".");
    if (dotIndex > 0) {
        extension = originalName.substring(dotIndex);
    }

    FileChooser chooser = new FileChooser();

    // ✅ Set default name WITH extension
    chooser.setInitialFileName(originalName);

    // ✅ Add filter (VERY IMPORTANT)
    if (!extension.isEmpty()) {
        chooser.getExtensionFilters().addAll(
    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg"),
    new FileChooser.ExtensionFilter("PDF", "*.pdf"),
    new FileChooser.ExtensionFilter("All files", "*.*")
);
    }

    File dest = chooser.showSaveDialog(null);

    if (dest == null) return;

    try {

        // ✅ FORCE EXTENSION IF USER REMOVED IT
        if (!dest.getName().contains(".") && !extension.isEmpty()) {
            dest = new File(dest.getAbsolutePath() + extension);
        }

        Files.copy(
                source.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );

        showInfo(I18n.t("fileDownloadedSuccess", "File downloaded successfully"));

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    // ============================
    // COMMENTS
    // ============================
    @FXML
    private void handleAddComment() {

        if (task == null) return;

        String comment = txtNewComment.getText().trim();
        if (comment.isEmpty()) return;

        TicketTaskDAO.logTaskEvent(task.getId(), "COMMENT", comment);

        txtNewComment.clear();
        loadTimeline();
    }

    // ============================
    // TIMELINE
    // ============================
    private void loadTimeline() {

        timelineContainer.getChildren().clear();

        var events = TicketTaskDAO.getTaskEvents(task.getId());

        for (var ev : events) {

            VBox card = new VBox(5);
            card.setStyle("-fx-background-color:#f3f4f6; -fx-padding:10; -fx-background-radius:6;");

            Label user = new Label(ev.getUsername());
            user.setStyle("-fx-font-weight:bold;");

            Label text = new Label(TimelineEventDescriptionLocalizer.localizeTaskTimelineDescription(
                    ev.getType(), ev.getDescription()));
            Label time = new Label(formatToSystemTime(ev.getCreatedAt()));
            time.setStyle("-fx-font-size:10px; -fx-text-fill:gray;");

            card.getChildren().addAll(user, text, time);

            timelineContainer.getChildren().add(card);
        }
    }

    /**
     * Task event timestamps are stored as UTC text in SQLite; convert to local system time for UI.
     */
    private String formatToSystemTime(String dbTimeText) {
        if (dbTimeText == null || dbTimeText.isBlank()) {
            return "";
        }
        try {
            LocalDateTime utc = LocalDateTime.parse(dbTimeText.trim(), DB_TIME);
            return utc.atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(UI_TIME);
        } catch (Exception ignored) {
            return dbTimeText;
        }
    }

    private String formatAssignedAt(LocalDateTime assignedAt, String assignedByName) {
        if (assignedAt == null) {
            return "-";
        }
        String by = (assignedByName == null || assignedByName.isBlank()) ? "-" : assignedByName;
        return assignedAt.format(ASSIGNED_AT_FORMATTER) + " (" + by + ")";
    }

    @FXML
    private void handleTrackTask() {
        if (task == null) return;
        List<TicketEvent> events = TicketTaskDAO.getTaskTrackingEvents(task.getId());
        String title = I18n.t("trackTaskTitle", "Task Tracking") + " - " + task.getTitle();
        showTrackingDialog(title, events);
    }

    private void showTrackingDialog(String title, List<TicketEvent> events) {
        TableView<TrackingRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefSize(820, 400);

        TableColumn<TrackingRow, String> colTime = new TableColumn<>(I18n.t("track.time", "Time"));
        colTime.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().time()));

        TableColumn<TrackingRow, String> colBy = new TableColumn<>(I18n.t("track.actionBy", "Action By"));
        colBy.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().actionBy()));

        TableColumn<TrackingRow, String> colAction = new TableColumn<>(I18n.t("track.action", "Action"));
        colAction.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().action()));

        TableColumn<TrackingRow, String> colTarget = new TableColumn<>(I18n.t("track.targetUser", "Target User"));
        colTarget.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().targetUser()));

        table.getColumns().setAll(colTime, colBy, colAction, colTarget);

        if (events != null) {
            for (TicketEvent event : events) {
                String when = event.getCreatedAt() == null ? "-" : formatToSystemTime(event.getCreatedAt());
                String who = (event.getUsername() == null || event.getUsername().isBlank()) ? "-" : event.getUsername();
                String rawDescription = event.getDescription() == null ? "-" : event.getDescription();
                String what = I18n.t("track.action." + event.getType(), rawDescription);
                String target = extractTargetUser(rawDescription);
                table.getItems().add(new TrackingRow(when, who, what, target));
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.t("trackTask", "Track Task"));
        alert.setHeaderText(title);
        alert.getDialogPane().setContent(table);
        if (events == null || events.isEmpty()) {
            alert.setContentText(I18n.t("track.noEvents", "No tracking events found."));
        }
        alert.showAndWait();
    }

    private String extractTargetUser(String description) {
        if (description == null || description.isBlank()) return "-";
        String[] markers = {
                "assigned to ",
                "Assigned ticket to ",
                "Reassigned ticket to ",
                "Escalated ticket to ",
                "reassigned to ",
                " and assigned to "
        };
        for (String marker : markers) {
            int idx = description.indexOf(marker);
            if (idx >= 0) {
                String value = description.substring(idx + marker.length()).trim();
                return value.isBlank() ? "-" : value;
            }
        }
        return "-";
    }

    private record TrackingRow(String time, String actionBy, String action, String targetUser) {}

    @FXML
    private void handleExportPDF() {
        if (task == null) return;
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("exportTaskPdf", "Export Task PDF"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            chooser.setInitialFileName("TSK-" + task.getId() + ".pdf");

            File file = chooser.showSaveDialog(lblTitle.getScene().getWindow());
            if (file == null) return;

            PdfWriter writer = new PdfWriter(file.getAbsolutePath());
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph(I18n.t("taskDetails", "Task Details"))
                    .setBold()
                    .setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(" "));

            Table info = new Table(UnitValue.createPercentArray(new float[]{2, 5})).useAllAvailableWidth();
            addInfoRow(info, I18n.t("title", "Title"), lblTitle.getText());
            addInfoRow(info, I18n.t("assignedTo", "Assigned To"), lblAssigned.getText());
            addInfoRow(info, I18n.t("status", "Status"), cmbStatus.getValue());
            addInfoRow(info, I18n.t("progress", "Progress"), lblProgress.getText());
            document.add(info);

            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("description", "Description")).setBold());
            document.add(new Paragraph(safe(txtDescription.getText())));

            List<Attachment> attachments = AttachmentDAO.getTaskAttachments(task.getId());
            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("attachments", "Attachments")).setBold());
            Table attachmentTable = new Table(UnitValue.createPercentArray(new float[]{3, 5})).useAllAvailableWidth();
            attachmentTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("fileName", "File Name")).setBold()));
            attachmentTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("description", "Path")).setBold()));
            if (attachments != null && !attachments.isEmpty()) {
                for (Attachment a : attachments) {
                    attachmentTable.addCell(safe(a.getFileName()));
                    attachmentTable.addCell(safe(a.getFilePath()));
                }
            } else {
                attachmentTable.addCell(new Cell(1, 2).add(new Paragraph("-")));
            }
            document.add(attachmentTable);

            List<TicketTask> siblingTasks = TicketTaskDAO.getTasksByTicket(task.getTicketId());
            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("tasks", "Tasks")).setBold());
            Table taskTable = new Table(UnitValue.createPercentArray(new float[]{3, 2, 3, 2})).useAllAvailableWidth();
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("title", "Title")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("assignedTo", "Assigned To")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("assignedAt", "Assigned At")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("status", "Status")).setBold()));
            if (siblingTasks != null && !siblingTasks.isEmpty()) {
                for (TicketTask t : siblingTasks) {
                    taskTable.addCell(safe(t.getTitle()));
                    taskTable.addCell(safe(t.getAssignedToName()));
                    taskTable.addCell(formatAssignedAt(t.getCreatedAt(), t.getCreatedByName()));
                    taskTable.addCell(I18n.status(safe(t.getStatus())));
                }
            } else {
                taskTable.addCell(new Cell(1, 4).add(new Paragraph(I18n.t("noTasksAvailable", "No tasks available"))));
            }
            document.add(taskTable);

            List<Escalation> escalations = EscalationDAO.getByTask(task.getId());
            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("escalations", "Escalations")).setBold());
            Table escTable = new Table(UnitValue.createPercentArray(new float[]{2, 2, 4, 2})).useAllAvailableWidth();
            escTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("dateTime", "Date & Time")).setBold()));
            escTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("from", "From")).setBold()));
            escTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("details", "Details")).setBold()));
            escTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("to", "To")).setBold()));
            if (escalations != null && !escalations.isEmpty()) {
                for (Escalation e : escalations) {
                    escTable.addCell(safe(e.getCreatedAt()));
                    escTable.addCell(safe(e.getFromUserName()));
                    escTable.addCell(I18n.t("escalatedFrom", "Escalated from") + " "
                            + safe(e.getFromUserName()) + " -> " + safe(e.getToUserName()));
                    escTable.addCell(safe(e.getToUserName()));
                }
            } else {
                escTable.addCell(new Cell(1, 4).add(new Paragraph(I18n.t("noEscalation", "No escalation"))));
            }
            document.add(escTable);

            List<TicketEvent> events = TicketTaskDAO.getTaskEvents(task.getId());
            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("activityComments", "Activity / Comments")).setBold());
            Table eventTable = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 5})).useAllAvailableWidth();
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("dateTime", "Date & Time")).setBold()));
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("user", "User")).setBold()));
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("action", "Action")).setBold()));
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("details", "Details")).setBold()));
            if (events != null && !events.isEmpty()) {
                for (TicketEvent e : events) {
                    eventTable.addCell(formatToSystemTime(e.getCreatedAt()));
                    eventTable.addCell(safe(e.getUsername()));
                    eventTable.addCell(I18n.t("event." + safe(e.getType()), safe(e.getType())));
                    eventTable.addCell(safe(TimelineEventDescriptionLocalizer.localizeTaskTimelineDescription(
                            e.getType(), e.getDescription())));
                }
            } else {
                eventTable.addCell(new Cell(1, 4).add(new Paragraph("-")));
            }
            document.add(eventTable);

            document.close();
        } catch (Exception e) {
            showWarning(e.getMessage());
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static void addInfoRow(Table table, String key, String value) {
        table.addCell(new Cell().add(new Paragraph(safe(key)).setBold()));
        table.addCell(new Cell().add(new Paragraph(safe(value))));
    }

    // ============================
    // ALERTS
    // ============================
    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    
    
    private void loadEscalations(int taskId) {

    escalationBox.getChildren().clear();

    List<Escalation> list = EscalationDAO.getByTask(taskId);

    if (list.isEmpty()) {
        lblNoEscalation.setText(I18n.t("noEscalation", "No escalation"));
        escalationBox.getChildren().add(lblNoEscalation);
        return;
    }

    for (Escalation e : list) {

        Label lbl = new Label(
                I18n.t("escalatedFrom", "Escalated from") + " " + e.getFromUserName() +
                " → " + e.getToUserName() +
                " (" + e.getCreatedAt() + ")"
        );

        lbl.setStyle("""
            -fx-background-color:#f97316;
            -fx-text-fill:white;
            -fx-padding:6 10;
            -fx-background-radius:5;
        """);

        escalationBox.getChildren().add(lbl);
    }
}
    
    private void loadTasks(int ticketId) {
    int effectiveTicketId = ticketId;
    if (effectiveTicketId <= 0 && task != null) {
        effectiveTicketId = task.getTicketId();
    }
    if (effectiveTicketId <= 0 && task != null) {
        TicketTask fresh = TicketTaskDAO.getTaskById(task.getId());
        if (fresh != null) {
            effectiveTicketId = fresh.getTicketId();
        }
    }

    List<TicketTask> tasks = effectiveTicketId > 0
            ? TicketTaskDAO.getTasksByTicket(effectiveTicketId)
            : new ArrayList<>();

    // Show only peer tasks on the same ticket for this detail view.
    int currentUserId = Session.getUserId();
    if (currentUserId > 0) {
        tasks.removeIf(t ->
                t == null
                        || t.getAssignedTo() == currentUserId
                        || isSyntheticAssignmentTask(t.getTitle()));
    } else {
        tasks.removeIf(t -> t == null || isSyntheticAssignmentTask(t.getTitle()));
    }

    // Fallback only when ticket linkage is missing.
    if (tasks.isEmpty() && task != null && effectiveTicketId <= 0) {
        tasks.add(task);
    }

    tableTasks.getItems().setAll(tasks);
}

private static boolean isSyntheticAssignmentTask(String title) {
    if (title == null) {
        return false;
    }
    return "task assignment".equalsIgnoreCase(title.trim());
}
    
    
     
  private void loadAttachments() {

    attachmentList.getItems().clear();

    List<Attachment> attachments =
            AttachmentDAO.getTaskAttachments(task.getId());

    if (attachments != null) {
        attachmentList.getItems().addAll(attachments);
    }
}
    
  public void setTaskId(int taskId) {
    this.taskId = taskId;
    loadTask();
}

private void loadTask() {
    System.out.println("Loading task: " + taskId);

    // TODO: load task details from DAO
}  
    
private void loadComments() {
    // optional for now
}

@FXML
private void handleBack() {
    System.out.println("BACK CLICKED"); // 🔥 DEBUG
    MainController.loadPage("MyWork.fxml", null);
}
    
}