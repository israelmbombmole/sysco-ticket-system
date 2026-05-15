package com.app.controller;

import com.app.auth.Session;
import com.app.dao.AttachmentDAO;
import com.app.dao.AutomationDAO;
import com.app.dao.TicketDAO;
import com.app.dao.TicketTaskDAO;
import com.app.model.Attachment;
import com.app.model.Ticket;
import com.app.model.TicketEvent;
import com.app.model.TicketTask;
import com.app.model.User;

import com.app.util.AppUiStyles;
import com.app.util.I18n;
import com.app.util.TimelineEventDescriptionLocalizer;
import com.app.util.LanguageManager;
import com.app.util.SecurityUtil;
import com.app.util.TicketUtil;
import com.app.util.TimeUtil;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.FileChooser;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Node;
import javafx.scene.control.TableView;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.awt.Desktop;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.TextAlignment;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;


import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class TicketDetailsController {

    // ================= LABELS =================

    @FXML private Label lblTitle;
    @FXML private Label lblPriority;
    @FXML private Label lblStatus;
    @FXML private Label lblAssigned;
    @FXML private Label lblStarted;
    @FXML private Label lblClosed;
    @FXML private Label lblResolution;
    @FXML private Label lblReference;
    @FXML private Label lblCreatedBy;
    @FXML private Label lblUpdatedBy;
    @FXML private Label lblSla;

    // ================= TEXT =================

    @FXML private TextArea txtDescription;
    @FXML private TextField txtNewComment;

    // ================= CONTAINERS =================

    @FXML private VBox timelineContainer;
    @FXML private VBox ticketContainer;

    // ================= ATTACHMENTS =================

    @FXML private VBox attachmentFrame;
    @FXML private ListView<Attachment> attachmentList;
    @FXML private ImageView attachmentImage;
    
    @FXML private TreeTableView<TicketTask> taskTree;
    

@FXML private TreeTableColumn<TicketTask, String> colTask;

@FXML private TreeTableColumn<TicketTask, String> colUser;

@FXML private TreeTableColumn<TicketTask, String> colStatus;

@FXML private Button btnComment;
@FXML private Button btnAddTask;
@FXML private Button btnClose;

@FXML private Label lblOwner;


@FXML private TreeTableColumn<TicketTask, String> colStart;
@FXML private TreeTableColumn<TicketTask, String> colClose;
@FXML private TreeTableColumn<TicketTask, String> colDuration;

  

    private Ticket ticket;
    private int currentTicketId;
    private ObservableList<User> assignedAgentsList;
    private int ticketId;
    
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("dd MMM yyyy  HH:mm");

    // =====================================================
    // INITIALIZE
    // =====================================================

    @FXML
    public void initialize() {
        
        attachmentList.setCellFactory(list -> new ListCell<>() {

            @Override
            protected void updateItem(Attachment att, boolean empty) {

                super.updateItem(att, empty);

                if (empty || att == null) {
                    setGraphic(null);
                    return;
                }

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);

                Node preview;

                String name = att.getFileName().toLowerCase();

                if (name.endsWith(".png") ||
                    name.endsWith(".jpg") ||
                    name.endsWith(".jpeg") ||
                    name.endsWith(".gif")) {

                    ImageView thumb = new ImageView(
                            new Image("file:" + att.getFilePath()));

                    thumb.setFitWidth(60);
                    thumb.setPreserveRatio(true);

                    preview = thumb;

                } else {

                    preview = new Label("📄");
                }

                Label fileName = new Label(att.getFileName());

                Button downloadBtn = new Button("⬇");

                downloadBtn.setStyle(
                        "-fx-background-color:#22c55e;" +
                        "-fx-text-fill:white;" +
                        "-fx-font-weight:bold;" +
                        "-fx-background-radius:6;"
                );

                downloadBtn.setOnMouseEntered(e ->
                        downloadBtn.setStyle("-fx-background-color:#16a34a; -fx-text-fill:white;"));

                downloadBtn.setOnMouseExited(e ->
                        downloadBtn.setStyle("-fx-background-color:#22c55e; -fx-text-fill:white;"));

                downloadBtn.setOnAction(e -> downloadAttachment(att));

                row.getChildren().addAll(preview, fileName, downloadBtn);

                setGraphic(row);
            }
        });

        attachmentList.setOnMouseClicked(event -> {

            Attachment att = attachmentList.getSelectionModel().getSelectedItem();
            if (att == null) return;

            File file = new File(att.getFilePath());
            if (!file.exists()) return;

            String name = file.getName().toLowerCase();

            if (name.endsWith(".png") ||
                name.endsWith(".jpg") ||
                name.endsWith(".jpeg") ||
                name.endsWith(".gif")) {

                attachmentImage.setVisible(true);
                attachmentImage.setManaged(true);

                attachmentImage.setImage(
                        new Image("file:" + file.getAbsolutePath()));

            } else {

                try {
                    Desktop.getDesktop().open(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // =====================================================
    // SET TICKET
    // =====================================================

    public void setTicket(Ticket ticket) {

    this.ticket = TicketDAO.getTicketById(ticket.getId());
    this.currentTicketId = this.ticket.getId();

    // load assigned users
    this.assignedAgentsList = TicketDAO.getAssignedUsers(currentTicketId);

    loadTicketData();
    loadTimeline();
    loadAttachments();

    initTaskTree(currentTicketId);
}

    // =====================================================
    // LOAD TICKET DATA
    // =====================================================

    private void loadTicketData() {

        if (ticket == null) return;

        lblReference.setText("REF: " + TicketUtil.formatTicketRef(ticket.getId()));
        lblTitle.setText(ticket.getTitle() == null ? "-" : ticket.getTitle());

        lblCreatedBy.setText(I18n.t("createdBy", "Created by") + ": " + ticket.getCreatedBy());
        lblUpdatedBy.setText(I18n.t("lastUpdatedBy", "Last updated by") + ": " + ticket.getUpdatedBy());

        // PRIORITY BADGES
        String priority = ticket.getPriority();

        if (priority != null) {

            lblPriority.setText(I18n.t("priority." + priority.toUpperCase(), priority));

            switch(priority.toUpperCase()) {

                case "LOW" ->
                        lblPriority.setStyle("-fx-background-color:#22c55e; -fx-text-fill:white; -fx-padding:4 10; -fx-background-radius:6;");

                case "MEDIUM" ->
                        lblPriority.setStyle("-fx-background-color:#f59e0b; -fx-text-fill:white; -fx-padding:4 10; -fx-background-radius:6;");

                case "HIGH" ->
                        lblPriority.setStyle("-fx-background-color:#ef4444; -fx-text-fill:white; -fx-padding:4 10; -fx-background-radius:6;");
            }
        }

        String status = ticket.getStatus() == null ? "OPEN" : ticket.getStatus().toUpperCase();
        lblStatus.setText(I18n.status(status));

        String agent = TicketDAO.getAssignedAgent(ticket.getId());
        String assignedUsers = TicketDAO.getAssignedUsersNames(ticket.getId());

if (assignedUsers == null || assignedUsers.isEmpty()) {
    lblAssigned.setText(I18n.t("unassigned", "Unassigned"));
} else {
    lblAssigned.setText(assignedUsers);
}

        if (ticket.getStartedAt() != null) {
    lblStarted.setText(ticket.getStartedAt().format(FORMATTER));
}



if (ticket.getClosedAt() != null) {
    lblClosed.setText(ticket.getClosedAt().format(FORMATTER));
}

        if (ticket.getResolutionMinutes() != null)
            lblResolution.setText(TimeUtil.formatDurationMinutes(ticket.getResolutionMinutes()));

        txtDescription.setText(ticket.getDescription());

        // SLA STATUS
        double usage = TicketDAO.getSlaUsagePercent(ticket.getId());

        if(usage < 80){
            lblSla.setText(I18n.t("slaOk", "SLA OK"));
            lblSla.setStyle("-fx-text-fill:#22c55e;");
        }
        else if(usage < 100){
            lblSla.setText(I18n.t("slaWarning", "SLA WARNING"));
            lblSla.setStyle("-fx-text-fill:#f59e0b;");
        }
        else{
            lblSla.setText(I18n.t("slaBreached", "SLA BREACHED"));
            lblSla.setStyle("-fx-text-fill:#ef4444;");
        }
        
         
        
    }

    

   

    // =====================================================
    // DOWNLOAD ATTACHMENT
    // =====================================================

    private void downloadAttachment(Attachment att) {

    try {

        File source = new File(att.getFilePath());

        if (!source.exists()) {
            System.out.println("Attachment file not found.");
            return;
        }

        String fileName = source.getName();
        String extension = "";

        int dot = fileName.lastIndexOf(".");
        if (dot > 0) {
            extension = fileName.substring(dot + 1).toLowerCase();
        }

        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(fileName);

        switch (extension) {

            case "pdf":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
                break;

            case "doc":
            case "docx":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Word Files (*.doc, *.docx)", "*.doc", "*.docx"));
                break;

            case "xls":
            case "xlsx":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Excel Files (*.xls, *.xlsx)", "*.xls", "*.xlsx"));
                break;

            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg, *.gif)", "*.png", "*.jpg", "*.jpeg", "*.gif"));
                break;

            case "txt":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"));
                break;

            default:
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));
        }

        File dest = chooser.showSaveDialog(lblTitle.getScene().getWindow());

        if (dest != null) {

            Files.copy(
                    source.toPath(),
                    dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println("Attachment downloaded successfully.");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    // =====================================================
    // TIMELINE
    // =====================================================

    private void loadTimeline() {

        timelineContainer.getChildren().clear();

        List<TicketEvent> events = TicketDAO.getEvents(ticket.getId());

        for (TicketEvent event : events) {
            addStyledTimelineItem(event);
        }
    }

    private void addStyledTimelineItem(TicketEvent event) {

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(getIconForType(event.getType()));
        icon.setStyle("-fx-font-size:16px;");

        VBox content = new VBox(3);

        Label title = new Label(event.getUsername() + " • " + I18n.t("event." + event.getType(), event.getType()));
        title.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");

        Label description = new Label(
                TimelineEventDescriptionLocalizer.localizeTicketTimelineDescription(
                        event.getType(), event.getDescription()));
        description.setWrapText(true);

        Label time = new Label(event.getCreatedAt());
        time.setStyle("-fx-font-size:11px; -fx-text-fill:#6b7280;");

        content.getChildren().addAll(title, description, time);

        row.getChildren().addAll(icon, content);

        timelineContainer.getChildren().add(row);
    }

    @FXML
    private void handleTrackTicket() {
        if (ticket == null) return;
        List<TicketEvent> events = TicketDAO.getTicketTrackingEvents(ticket.getId());
        String title = I18n.t("trackTicketTitle", "Ticket Tracking") + " - " + TicketUtil.formatTicketRef(ticket.getId());
        showTrackingDialog(title, events);
    }

    private void showTrackingDialog(String title, List<TicketEvent> events) {
        TableView<TrackingRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefSize(860, 430);

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
                String when = event.getCreatedAt() == null ? "-" : event.getCreatedAt();
                String who = (event.getUsername() == null || event.getUsername().isBlank()) ? "-" : event.getUsername();
                String rawDescription = event.getDescription() == null ? "-" : event.getDescription();
                String what = I18n.t("track.action." + event.getType(), rawDescription);
                String target = extractTargetUser(rawDescription);
                table.getItems().add(new TrackingRow(when, who, what, target));
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.t("trackTicket", "Track Ticket"));
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

    private String getIconForType(String type) {

        return switch (type) {
            case "CREATED" -> "🟢";
            case "ASSIGNED" -> "🔵";
            case "TICKET_ASSIGNED" -> "🔵";
            case "TICKET_REASSIGNED" -> "🔁";
            case "TICKET_ESCALATED" -> "⏫";
            case "TICKET_MERGED" -> "🔀";
            case "STARTED" -> "🟡";
            case "COMMENT" -> "💬";
            case "MERGED" -> "🔀";
            case "CLOSED" -> "🔴";
            default -> "•";
        };
    }

    // =====================================================
    // COMMENT
    // =====================================================

    @FXML
    private void handleAddComment() {

        if (!SecurityUtil.canAccessTicket(ticket.getId())) {
            showMessage(I18n.t("err.notAllowedCommentTicket", "You are not allowed to comment on this ticket"));
            return;
        }
        String comment = txtNewComment.getText().trim();
        if (comment.isEmpty()) return;
        
        TicketDAO.addComment(
    ticket.getId(),
    Session.getUserId(),
    comment,
    null
);

        txtNewComment.clear();
        loadTimeline();
    }

    // =====================================================
    // EXPORT PDF
    // =====================================================

    @FXML
    private void handleExportPDF() {

        try {

            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("exportTicketPdf", "Export Ticket PDF"));

            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

            String ref = lblReference.getText().replace("REF:", "").trim();
            chooser.setInitialFileName(ref + ".pdf");

            File file = chooser.showSaveDialog(lblTitle.getScene().getWindow());
            if (file == null) return;

            PdfWriter writer = new PdfWriter(file.getAbsolutePath());
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph(I18n.t("ticketDetails", "Ticket Details"))
                    .setBold()
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(" "));

            Table info = new Table(UnitValue.createPercentArray(new float[]{2, 5})).useAllAvailableWidth();
            addInfoRow(info, I18n.t("reference", "Reference"), lblReference.getText());
            addInfoRow(info, I18n.t("title", "Title"), lblTitle.getText());
            addInfoRow(info, I18n.t("status", "Status"), lblStatus.getText());
            addInfoRow(info, I18n.t("priority", "Priority"), lblPriority.getText());
            addInfoRow(info, I18n.t("assignedTo", "Assigned To"), lblAssigned.getText());
            addInfoRow(info, I18n.t("startedAt", "Started At"), lblStarted.getText());
            addInfoRow(info, I18n.t("closedAt", "Closed At"), lblClosed.getText());
            addInfoRow(info, I18n.t("duration", "Duration"), lblResolution.getText());
            addInfoRow(info, I18n.t("sla", "SLA"), lblSla.getText());
            addInfoRow(info, I18n.t("createdBy", "Created by"), lblCreatedBy.getText());
            addInfoRow(info, I18n.t("lastUpdatedBy", "Last updated by"), lblUpdatedBy.getText());
            document.add(info);

            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("description", "Description")).setBold());
            document.add(new Paragraph(safe(txtDescription.getText())));

            List<TicketTask> tasks = TicketTaskDAO.getTasksByTicket(ticket.getId());
            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("tasks", "Tasks")).setBold());
            Table taskTable = new Table(UnitValue.createPercentArray(new float[]{3, 2, 2, 2, 2, 2})).useAllAvailableWidth();
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("title", "Title")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("assignedTo", "Assigned To")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("status", "Status")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("start", "Start")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("close", "Close")).setBold()));
            taskTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("duration", "Duration")).setBold()));
            if (tasks != null && !tasks.isEmpty()) {
                for (TicketTask t : tasks) {
                    taskTable.addCell(safe(t.getTitle()));
                    taskTable.addCell(safe(t.getAssignedToName()));
                    taskTable.addCell(I18n.status(safe(t.getStatus())));
                    taskTable.addCell(t.getStartedAt() == null ? "-" : t.getStartedAt().format(TABLE_DATETIME_FORMAT));
                    taskTable.addCell(t.getClosedAt() == null ? "-" : t.getClosedAt().format(TABLE_DATETIME_FORMAT));
                    taskTable.addCell(TimeUtil.formatDurationMinutes(t.getDurationMinutes()));
                }
            } else {
                taskTable.addCell(new Cell(1, 6).add(new Paragraph(I18n.t("noTasksAvailable", "No tasks available"))));
            }
            document.add(taskTable);

            List<Attachment> ticketAttachments = AttachmentDAO.getAttachments(ticket.getId());
            List<Attachment> taskAttachments = AttachmentDAO.getAllTaskAttachmentsByTicket(ticket.getId());
            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("attachments", "Attachments")).setBold());
            Table attachmentTable = new Table(UnitValue.createPercentArray(new float[]{3, 5})).useAllAvailableWidth();
            attachmentTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("fileName", "File Name")).setBold()));
            attachmentTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("description", "Path")).setBold()));
            int count = 0;
            if (ticketAttachments != null) {
                for (Attachment a : ticketAttachments) {
                    attachmentTable.addCell(safe(a.getFileName()));
                    attachmentTable.addCell(safe(a.getFilePath()));
                    count++;
                }
            }
            if (taskAttachments != null) {
                for (Attachment a : taskAttachments) {
                    attachmentTable.addCell(safe(a.getFileName()));
                    attachmentTable.addCell(safe(a.getFilePath()));
                    count++;
                }
            }
            if (count == 0) {
                attachmentTable.addCell(new Cell(1, 2).add(new Paragraph("-")));
            }
            document.add(attachmentTable);

            List<TicketEvent> events = TicketDAO.getEvents(ticket.getId());
            document.add(new Paragraph(" "));
            document.add(new Paragraph(I18n.t("activityComments", "Activity / Comments")).setBold());
            Table eventTable = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 5})).useAllAvailableWidth();
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("dateTime", "Date & Time")).setBold()));
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("user", "User")).setBold()));
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("action", "Action")).setBold()));
            eventTable.addHeaderCell(new Cell().add(new Paragraph(I18n.t("details", "Details")).setBold()));
            if (events != null && !events.isEmpty()) {
                for (TicketEvent e : events) {
                    eventTable.addCell(safe(e.getCreatedAt()));
                    eventTable.addCell(safe(e.getUsername()));
                    eventTable.addCell(I18n.t("event." + safe(e.getType()), safe(e.getType())));
                    eventTable.addCell(safe(TimelineEventDescriptionLocalizer.localizeTicketTimelineDescription(
                            e.getType(), e.getDescription())));
                }
            } else {
                eventTable.addCell(new Cell(1, 4).add(new Paragraph("-")));
            }
            document.add(eventTable);

            document.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final DateTimeFormatter TABLE_DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static void addInfoRow(Table table, String key, String value) {
        table.addCell(new Cell().add(new Paragraph(safe(key)).setBold()));
        table.addCell(new Cell().add(new Paragraph(safe(value))));
    }
    
    
    
    public void initTaskTree(int ticketId) {

    int resolvedTicketId = ticketId > 0
            ? ticketId
            : (currentTicketId > 0 ? currentTicketId : (ticket != null ? ticket.getId() : 0));

    List<TicketTask> tasks = resolvedTicketId > 0
            ? TicketTaskDAO.getTasksByTicket(resolvedTicketId)
            : new ArrayList<>();

    System.out.println("TASK COUNT: " + tasks.size() + " (ticketId=" + resolvedTicketId + ")");

    for (TicketTask t : tasks) {
        System.out.println("TASK: " + t.getTitle());
    }

    taskTree.setRowFactory(tv -> {
        TreeTableRow<TicketTask> row = new TreeTableRow<>();

        row.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !row.isEmpty()) {
                TicketTask task = row.getItem();
                openTaskDetails(task);
            }
        });

        return row;
    });

    DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // =====================
    // START
    // =====================
    colStart.setCellValueFactory(param -> {
        var t = param.getValue().getValue();
        return new SimpleStringProperty(
                t.getStartedAt() != null ? t.getStartedAt().format(formatter) : "-"
        );
    });

    // =====================
    // CLOSE
    // =====================
    colClose.setCellValueFactory(param -> {
        var t = param.getValue().getValue();
        return new SimpleStringProperty(
                t.getClosedAt() != null ? t.getClosedAt().format(formatter) : "-"
        );
    });

    // =====================
    // ✅ DURATION (FIXED)
    // =====================
    colDuration.setCellValueFactory(param -> {

        TicketTask t = param.getValue().getValue();

        Integer d = t.getDurationMinutes(); // ✅ CORRECT FIELD

        return new SimpleStringProperty(TimeUtil.formatDurationMinutes(d));
    });

    // =====================
    // TREE (flat list for ticket details)
    // =====================
    TreeItem<TicketTask> root = new TreeItem<>();
    root.setExpanded(true);
    for (TicketTask task : tasks) {
        root.getChildren().add(new TreeItem<>(task));
    }
    taskTree.setRoot(root);
    taskTree.setShowRoot(false);

    // =====================
    // BASIC COLUMNS
    // =====================
    colTask.setCellValueFactory(param -> {
        TicketTask t = param.getValue().getValue();
        String title = t != null ? t.getTitle() : null;
        if (title == null || title.isBlank()) {
            int taskId = t != null ? t.getId() : 0;
            title = taskId > 0 ? "Task #" + taskId : "-";
        }
        return new SimpleStringProperty(title);
    });

    colUser.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getValue().getAssignedToName()));

    colStatus.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getValue().getStatus()));

    // =====================
    // COLOR LOGIC
    // =====================
    colStatus.setCellFactory(column -> new TreeTableCell<>() {
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
                case "PENDING" -> setStyle("-fx-text-fill: orange;");
                case "IN_PROGRESS" -> setStyle("-fx-text-fill: blue;");
                case "COMPLETED" -> setStyle("-fx-text-fill: green;");
                default -> setStyle("");
            }
        }
    });
}
    
    
    
    private void openTaskDetails(TicketTask task) {

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TaskDetails.fxml"),
                LanguageManager.getBundle()
        );

        Parent root = loader.load();

        TaskDetailsController controller = loader.getController();
        controller.setTask(task);

        Stage stage = new Stage();
        stage.setTitle(I18n.t("taskDetails", "Task Details") + " - " + task.getTitle());
        Scene taskScene = new Scene(root);
        AppUiStyles.applyToScene(taskScene);
        stage.setScene(taskScene);
        stage.setMaximized(true);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
    
    private void showTaskActionPopup(TicketTask task) {

    ChoiceDialog<String> dialog = new ChoiceDialog<>("START",
            "START", "COMPLETE", "REASSIGN");

    dialog.setTitle(I18n.t("taskAction", "Task Action"));
    dialog.setHeaderText(I18n.t("task", "Task") + ": " + task.getTitle());
    dialog.setContentText(I18n.t("chooseAction", "Choose action:"));

    dialog.showAndWait().ifPresent(action -> {

        switch (action) {

            case "START" -> TicketTaskDAO.startTask(task.getId());

            case "COMPLETE" -> TicketTaskDAO.completeTask(task.getId());

            case "REASSIGN" -> {
                System.out.println("Reassign not implemented yet");
            }
        }

        // 🔥 refresh
        initTaskTree(ticket.getId());
    });
}
    
   
    
    
    
    
    // =====================================================
    // LOAD ATTACHMENTS
    // =====================================================
    
    private void loadAttachments() {

    attachmentList.getItems().clear();

    // 🔹 Ticket attachments
    List<Attachment> ticketAttachments =
            AttachmentDAO.getAttachments(ticket.getId());

    // 🔹 Task attachments
    List<Attachment> taskAttachments =
            AttachmentDAO.getAllTaskAttachmentsByTicket(ticket.getId());

    if ((ticketAttachments == null || ticketAttachments.isEmpty()) &&
        (taskAttachments == null || taskAttachments.isEmpty())) {

        attachmentFrame.setVisible(false);
        attachmentFrame.setManaged(false);
        return;
    }

    attachmentFrame.setVisible(true);
    attachmentFrame.setManaged(true);

    if (ticketAttachments != null)
        attachmentList.getItems().addAll(ticketAttachments);

    if (taskAttachments != null)
        attachmentList.getItems().addAll(taskAttachments);
}
    
   @FXML
private void handleAddTask() {

    try {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/AddTaskPopup.fxml"),
                LanguageManager.getBundle()
        );

        Parent root = loader.load();

        AddTaskPopupController controller = loader.getController();

        ObservableList<User> combo =
                com.app.dao.TicketDAO.getUsersInTicketDirectionForTasks(currentTicketId);

        if (combo.isEmpty()) {
            showMessage("No available users found in this ticket direction.");
            return;
        }

        controller.initData(currentTicketId, combo, null);

        Stage stage = new Stage();
        stage.setTitle(I18n.t("addTask", "Add Task"));
        Scene addTaskScene = new Scene(root);
        AppUiStyles.applyToScene(addTaskScene);
        stage.setScene(addTaskScene);
        stage.showAndWait();

        // 🔥 refresh after closing popup
        initTaskTree(currentTicketId);

    } catch (Exception e) {
        e.printStackTrace();
    }
}

@FXML
private void handleCreateJobFromTicket() {
    if (ticket == null) return;
    try {
        ObservableList<User> users = TicketDAO.getTicketReassignCandidatesForCurrentUser();
        if (users == null || users.isEmpty()) {
            showMessage(I18n.t("err.noAssignableUsers", "No eligible users found for reassignment."));
            return;
        }

        ChoiceDialog<User> assigneeDialog = new ChoiceDialog<>(users.get(0), users);
        assigneeDialog.setTitle(I18n.t("createJobFromTicket", "Create Job From Ticket"));
        assigneeDialog.setHeaderText(I18n.t("assignee", "Assignee"));
        assigneeDialog.setContentText(I18n.t("selectUser", "Select User"));
        assigneeDialog.showAndWait().ifPresent(assignee -> {
            DatePicker dp = new DatePicker(java.time.LocalDate.now().plusDays(1));
            Dialog<java.time.LocalDate> dateDialog = new Dialog<>();
            dateDialog.setTitle(I18n.t("createJobFromTicket", "Create Job From Ticket"));
            dateDialog.setHeaderText(I18n.t("dueDate", "Due Date"));
            dateDialog.getDialogPane().setContent(dp);
            dateDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dateDialog.setResultConverter(btn -> btn == ButtonType.OK ? dp.getValue() : null);
            java.time.LocalDate dueDate = dateDialog.showAndWait().orElse(null);
            if (dueDate == null) return;

            TextInputDialog timeDialog = new TextInputDialog("09:00");
            timeDialog.setTitle(I18n.t("createJobFromTicket", "Create Job From Ticket"));
            timeDialog.setHeaderText(I18n.t("dueTime", "Due Time"));
            String hhmm = timeDialog.showAndWait().orElse("09:00").trim();
            LocalTime dueTime;
            try {
                dueTime = LocalTime.parse(hhmm.length() == 5 ? hhmm + ":00" : hhmm);
            } catch (Exception ex) {
                showMessage(I18n.t("invalidTimeFormat", "Invalid time format. Use HH:mm."));
                return;
            }

            TextInputDialog reminderDialog = new TextInputDialog("60");
            reminderDialog.setTitle(I18n.t("createJobFromTicket", "Create Job From Ticket"));
            reminderDialog.setHeaderText(I18n.t("reminderMinutes", "Reminder (minutes before)"));
            int parsedReminderMinutes = 60;
            try {
                parsedReminderMinutes = Integer.parseInt(reminderDialog.showAndWait().orElse("60").trim());
            } catch (Exception ignored) {}
            final int reminderMinutes = parsedReminderMinutes;

            ChoiceDialog<String> recurrenceDialog = new ChoiceDialog<>("ONCE", "ONCE", "MONTHLY");
            recurrenceDialog.setTitle(I18n.t("createJobFromTicket", "Create Job From Ticket"));
            recurrenceDialog.setHeaderText(I18n.t("recurrence", "Recurrence"));
            recurrenceDialog.showAndWait().ifPresent(recurrence -> {
                LocalDateTime dueAt = LocalDateTime.of(dueDate, dueTime);
                String jobTitle = "[From " + TicketUtil.formatTicketRef(ticket.getId()) + "] "
                        + (ticket.getTitle() == null ? "Scheduled follow-up" : ticket.getTitle());
                String jobDescription = ticket.getDescription() == null ? "" : ticket.getDescription();
                try {
                    AutomationDAO.createScheduledJob(
                            jobTitle,
                            jobDescription,
                            dueAt,
                            reminderMinutes,
                            assignee.getId(),
                            recurrence
                    );
                    showMessage(I18n.t("jobCreatedFromTicket", "Scheduled job created from ticket."));
                } catch (RuntimeException ex) {
                    showMessage(ex.getMessage());
                }
            });
        });
    } catch (Exception e) {
        showMessage(e.getMessage());
    }
}
    
  
private void loadTaskTree(int ticketId) {

    List<TicketTask> tasks = TicketTaskDAO.getTasksByTicket(ticketId);

    TreeItem<TicketTask> root = new TreeItem<>();
    root.setExpanded(true);

    for (TicketTask t : tasks) {
        root.getChildren().add(new TreeItem<>(t));
    }

    taskTree.setRoot(root);

    System.out.println("🌳 Tasks loaded: " + tasks.size());
}

private void showMessage(String msg) {

    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setHeaderText(null);
    alert.setContentText(msg);
    alert.showAndWait();
}
   public void setTicketId(int ticketId) {

    this.ticketId = ticketId;

    this.ticket = TicketDAO.getTicketById(ticketId); // ✅ LOAD DATA

    if (this.ticket == null) {
        System.out.println("❌ Ticket not found: " + ticketId);
        return;
    }

    this.currentTicketId = this.ticket.getId();
    this.assignedAgentsList = TicketDAO.getAssignedUsers(currentTicketId);

    loadTicketData();     // ✅ populate UI
    loadTimeline();       // ✅ events
    loadAttachments();    // ✅ files
    initTaskTree(currentTicketId); // ✅ tasks
}

private void loadTicket() {
    System.out.println("Loading ticket: " + ticketId);

    // your existing logic
}
    
}