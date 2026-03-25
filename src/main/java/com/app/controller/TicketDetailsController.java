package com.app.controller;

import com.app.auth.Session;
import com.app.dao.AttachmentDAO;
import com.app.dao.TicketDAO;
import com.app.dao.TicketTaskDAO;
import com.app.model.Attachment;
import com.app.model.Ticket;
import com.app.model.TicketEvent;
import com.app.model.TicketTask;
import com.app.model.User;

import com.app.util.SecurityUtil;
import com.app.util.TicketUtil;

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
import java.util.List;
import java.awt.Desktop;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.embed.swing.SwingFXUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;
import com.itextpdf.io.image.ImageDataFactory;


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

        lblCreatedBy.setText("Créé par : " + ticket.getCreatedBy());
        lblUpdatedBy.setText("Dernière mise à jour : " + ticket.getUpdatedBy());

        // PRIORITY BADGES
        String priority = ticket.getPriority();

        if (priority != null) {

            lblPriority.setText(priority);

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
        lblStatus.setText(status);

        String agent = TicketDAO.getAssignedAgent(ticket.getId());
        String assignedUsers = TicketDAO.getAssignedUsersNames(ticket.getId());

if (assignedUsers == null || assignedUsers.isEmpty()) {
    lblAssigned.setText("Unassigned");
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
            lblResolution.setText(ticket.getResolutionMinutes() + " minutes");

        txtDescription.setText(ticket.getDescription());

        // SLA STATUS
        double usage = TicketDAO.getSlaUsagePercent(ticket.getId());

        if(usage < 80){
            lblSla.setText("SLA OK");
            lblSla.setStyle("-fx-text-fill:#22c55e;");
        }
        else if(usage < 100){
            lblSla.setText("SLA WARNING");
            lblSla.setStyle("-fx-text-fill:#f59e0b;");
        }
        else{
            lblSla.setText("SLA BREACHED");
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

        Label title = new Label(event.getUsername() + " • " + event.getType());
        title.setStyle("-fx-font-weight:bold; -fx-font-size:13px;");

        Label description = new Label(event.getDescription());
        description.setWrapText(true);

        Label time = new Label(event.getCreatedAt());
        time.setStyle("-fx-font-size:11px; -fx-text-fill:#6b7280;");

        content.getChildren().addAll(title, description, time);

        row.getChildren().addAll(icon, content);

        timelineContainer.getChildren().add(row);
    }

    private String getIconForType(String type) {

        return switch (type) {
            case "CREATED" -> "🟢";
            case "ASSIGNED" -> "🔵";
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
            showMessage("You are not allowed to comment on this ticket");
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
            chooser.setTitle("Export Ticket PDF");

            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

            String ref = lblReference.getText().replace("REF:", "").trim();
            chooser.setInitialFileName(ref + ".pdf");

            File file = chooser.showSaveDialog(lblTitle.getScene().getWindow());
            if (file == null) return;

            PdfWriter writer = new PdfWriter(file.getAbsolutePath());
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("SYSCO TICKET REPORT")
                    .setBold()
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("\n"));

            document.add(new Paragraph("Reference: " + lblReference.getText()));
            document.add(new Paragraph("Title: " + lblTitle.getText()));
            document.add(new Paragraph("Priority: " + lblPriority.getText()));
            document.add(new Paragraph("Agent: " + lblAssigned.getText()));
            document.add(new Paragraph("Started: " + lblStarted.getText()));
            document.add(new Paragraph("Closed: " + lblClosed.getText()));

            document.add(new Paragraph("\nDESCRIPTION").setBold());
            document.add(new Paragraph(txtDescription.getText()));

            WritableImage timelineImage =
                    timelineContainer.snapshot(new SnapshotParameters(), null);

            File tempImage = File.createTempFile("timeline", ".png");

            ImageIO.write(
                    SwingFXUtils.fromFXImage(timelineImage, null),
                    "png",
                    tempImage
            );

            com.itextpdf.layout.element.Image pdfImage =
                    new com.itextpdf.layout.element.Image(
                            ImageDataFactory.create(tempImage.getAbsolutePath()));

            pdfImage.setAutoScale(true);

            document.add(pdfImage);

            document.close();
            tempImage.delete();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    
    public void initTaskTree(int ticketId) {

    List<TicketTask> tasks = TicketTaskDAO.getTasksByTicket(ticketId);

    System.out.println("TASK COUNT: " + tasks.size());

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

        return new SimpleStringProperty(
                d == null ? "-" : d + " min"
        );
    });

    // =====================
    // TREE
    // =====================
    TreeItem<TicketTask> root = TicketDAO.buildTaskTree(tasks);

    taskTree.setRoot(root);
    taskTree.setShowRoot(false);

    // =====================
    // BASIC COLUMNS
    // =====================
    colTask.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getValue().getTitle()));

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

            setText(status);

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
                getClass().getResource("/view/TaskDetails.fxml")
        );

        Parent root = loader.load();

        TaskDetailsController controller = loader.getController();
        controller.setTask(task);

        Stage stage = new Stage();
        stage.setTitle("Task Details - " + task.getTitle());
        stage.setScene(new Scene(root));
        stage.setMaximized(true);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
    
    private void showTaskActionPopup(TicketTask task) {

    ChoiceDialog<String> dialog = new ChoiceDialog<>("START",
            "START", "COMPLETE", "REASSIGN");

    dialog.setTitle("Task Action");
    dialog.setHeaderText("Task: " + task.getTitle());
    dialog.setContentText("Choose action:");

    dialog.showAndWait().ifPresent(action -> {

        switch (action) {

            case "START" -> {
                TicketTaskDAO.updateStatus(task.getId(), "IN_PROGRESS");
            }

            case "COMPLETE" -> {
                TicketTaskDAO.updateStatus(task.getId(), "COMPLETED");
            }

            case "REASSIGN" -> {
                // optional (we add later)
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
                getClass().getResource("/view/AddTaskPopup.fxml")
        );

        Parent root = loader.load();

        AddTaskPopupController controller = loader.getController();

        // ✅ FIX: LOAD USERS BASED ON ROLE (NOT ASSIGNED USERS)
        ObservableList<User> assignableUsers =
                javafx.collections.FXCollections.observableArrayList(
                        com.app.dao.UserDAO.getAssignableUsers()
                );

        controller.initData(currentTicketId, assignableUsers);

        Stage stage = new Stage();
        stage.setTitle("Add Task");
        stage.setScene(new Scene(root));
        stage.showAndWait();

        // 🔥 refresh after closing popup
        initTaskTree(currentTicketId);

    } catch (Exception e) {
        e.printStackTrace();
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