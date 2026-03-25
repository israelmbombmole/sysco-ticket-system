package com.app.controller;

import com.app.dao.AttachmentDAO;
import com.app.dao.EscalationDAO;
import com.app.dao.NotificationDAO;
import com.app.dao.TicketTaskDAO;
import com.app.model.Attachment;
import com.app.model.Escalation;
import com.app.model.TicketTask;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

public class TaskDetailsController {

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

    @FXML private TableColumn<TicketTask, String> colTitle;
    @FXML private TableColumn<TicketTask, String> colAssigned;
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

    // ============================
    // ⚠ ESCALATION COLUMN (SAFE)
    // ============================

    // ❗ Avoid adding duplicate column on reload
    boolean columnExists = tableTasks.getColumns().stream()
            .anyMatch(c -> "⚠".equals(c.getText()));

    if (!columnExists) {

        TableColumn<TicketTask, String> colEscalation = new TableColumn<>("⚠");

        colEscalation.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().isEscalated() ? "⚠" : ""
                )
        );

        colEscalation.setPrefWidth(60); // nice size

        tableTasks.getColumns().add(0, colEscalation); // add as FIRST column
    }

    colTitle.setCellValueFactory(c ->
    new SimpleStringProperty(c.getValue().getTitle())
);

colAssigned.setCellValueFactory(c ->
    new SimpleStringProperty(c.getValue().getAssignedToName())
);

colStatus.setCellValueFactory(c ->
    new SimpleStringProperty(c.getValue().getStatus())
);
    
    
    
    
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

lblAssigned.setText("Assigned to: " + (assigned != null ? assigned : "N/A"));

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
    loadTasks(task.getTicketId());   // ← IMPORTANT
    loadEscalations(task.getId());   // ← IMPORTANT
}

    

    // ============================
    // PREVIEW
    // ============================
    private void previewFile(String path) {

        previewContainer.getChildren().clear();

        File file = new File(path);

        if (!file.exists()) {
            previewContainer.getChildren().add(new Label("File not found"));
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

                Label label = new Label("Preview not available");

                Button openBtn = new Button("Open File");
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
            showWarning("No file selected");
            return;
        }

        try {
            File file = new File(selectedAttachment.getFilePath());

            if (!file.exists()) {
                showWarning("File not found");
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
        showWarning("No file selected");
        return;
    }

    File source = new File(selectedAttachment.getFilePath());

    if (!source.exists()) {
        showWarning("File not found");
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

        showInfo("File downloaded successfully");

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

            Label text = new Label(ev.getDescription());
            Label time = new Label(ev.getCreatedAt());
            time.setStyle("-fx-font-size:10px; -fx-text-fill:gray;");

            card.getChildren().addAll(user, text, time);

            timelineContainer.getChildren().add(card);
        }
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
        lblNoEscalation.setText("No escalation");
        escalationBox.getChildren().add(lblNoEscalation);
        return;
    }

    for (Escalation e : list) {

        Label lbl = new Label(
                "⚠ Escalated from " + e.getFromUserName() +
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

    List<TicketTask> tasks = TicketTaskDAO.getTasksByTicket(ticketId);

    tableTasks.getItems().setAll(tasks);
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