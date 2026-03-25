package com.app.controller;

import com.app.auth.Session;
import com.app.dao.AttachmentDAO;
import com.app.dao.TicketDAO;
import com.app.dao.TicketTaskDAO;
import com.app.model.User;
import com.app.util.SecurityUtil;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class AddTaskPopupController {

    @FXML private TextField txtTaskTitle;
    @FXML private ComboBox<User> cmbAgents;
    @FXML private TextArea txtDescription;
    @FXML private ListView<File> attachmentList;
    @FXML private VBox attachmentFrame;

    private int ticketId;
    private ObservableList<User> agents;
    private List<File> selectedFiles = new ArrayList<>();

    // ============================
    // INITIALIZE
    // ============================
    @FXML
    public void initialize() {

        // ✅ Show file name only
        attachmentList.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                setText(empty || file == null ? null : file.getName());
            }
        });

        // ✅ Double-click → open file
        attachmentList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {

                File file = attachmentList.getSelectionModel().getSelectedItem();

                if (file != null) {
                    try {
                        Desktop.getDesktop().open(file);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        
        
        // hide attachment frame initially
        attachmentFrame.setVisible(true);
        attachmentFrame.setManaged(true);
        attachmentList.setPlaceholder(new Label("No files selected"));
    }

    // ============================
    // INIT DATA
    // ============================
    public void initData(int ticketId, ObservableList<User> agents) {
        this.ticketId = ticketId;
        this.agents = agents;

        cmbAgents.setItems(agents);

        if (!agents.isEmpty()) {
            cmbAgents.getSelectionModel().selectFirst();
        }

        cmbAgents.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.getUsername());
            }
        });

        cmbAgents.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.getUsername());
            }
        });
    }

    // ============================
    // SAVE TASK
    // ============================
    @FXML
private void handleSave() {

    String taskTitle = txtTaskTitle.getText().trim();
    String description = txtDescription.getText().trim();
    User selectedAgent = cmbAgents.getValue();

    // ============================
    // ✅ STEP 1: BASIC VALIDATION FIRST
    // ============================
    if (taskTitle.isEmpty() || selectedAgent == null) {
        showError("Please enter task and select agent.");
        return;
    }

    // ============================
    // ✅ STEP 2: SAFE ROLE HANDLING
    // ============================
    String currentRole = Session.getRole();
    String targetRole = selectedAgent.getRole();

    if (currentRole == null || targetRole == null) {
        showError("Role configuration error. Contact admin.");
        return;
    }

    if (!canAssignTo(currentRole, targetRole)) {
        showError("You cannot assign a task to a higher role.");
        return;
    }

    // ============================
    // ✅ STEP 3: SECURITY CHECK
    // ============================
    if (!SecurityUtil.canAccessTicket(ticketId)) {
        showError("You are not allowed to add tasks");
        return;
    }

    try {

        // ============================
        // ✅ CREATE TASK
        // ============================
        int taskId = TicketTaskDAO.createTaskAndReturnId(
                ticketId,
                taskTitle,
                selectedAgent.getId(),
                description
        );

        if (taskId == -1) {
            showError("Failed to create task");
            return;
        }

        // ============================
        // ✅ SAVE ATTACHMENTS
        // ============================
        for (File file : selectedFiles) {

            File dest = new File("uploads/" + file.getName());

            Files.copy(
                    file.toPath(),
                    dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            AttachmentDAO.addTaskAttachment(
                    taskId,
                    file.getName(),
                    dest.getAbsolutePath()
            );
        }

        // ============================
        // ✅ CHECK COVERAGE
        // ============================
        boolean allCovered =
                TicketTaskDAO.allAgentsHaveTasks(ticketId, agents);

        if (allCovered) {

            List<Integer> ids = new ArrayList<>();
            for (User u : agents) {
                ids.add(u.getId());
            }

            TicketDAO.assignTicketToMultipleUsers(
                    ticketId,
                    ids,
                    "Task Assignment",
                    "Tasks assigned via popup"
            );

            close();

        } else {

            txtTaskTitle.clear();
            txtDescription.clear();
            cmbAgents.getSelectionModel().clearSelection();

            selectedFiles.clear();
            attachmentList.getItems().clear();

            attachmentFrame.setVisible(true);
            attachmentFrame.setManaged(true);
        }

    } catch (Exception e) {
        e.printStackTrace();
        showError("Error saving task");
    }
}
    
    
    
    // ============================
    // BROWSE FILES
    // ============================
    @FXML
    private void handleBrowseFiles() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Attachments");

        List<File> files = chooser.showOpenMultipleDialog(null);

        if (files != null && !files.isEmpty()) {

            selectedFiles.addAll(files);
            attachmentList.getItems().setAll(selectedFiles);

            attachmentFrame.setVisible(true);
            attachmentFrame.setManaged(true);
        }
    }

    // ============================
    // REMOVE FILE
    // ============================
    @FXML
    private void handleRemoveFile() {

        File selected = attachmentList.getSelectionModel().getSelectedItem();

        if (selected != null) {
            selectedFiles.remove(selected);
            attachmentList.getItems().setAll(selectedFiles);
        }

       
    }

    // ============================
    // CANCEL
    // ============================
    @FXML
    private void handleCancel() {
        close();
    }

    private void close() {
        Stage stage = (Stage) txtTaskTitle.getScene().getWindow();
        stage.close();
    }

    // ============================
    // ERROR
    // ============================
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private int getRoleLevel(String role) {

    if (role == null) return -1; 

    return switch (role.toUpperCase()) {
        case "DIRECTEUR" -> 6;
        case "SOUS-DIRECTEUR" -> 5;
        case "INSPECTEUR" -> 4;
        case "CONTROLEUR" -> 3;
        case "VERIFICATEUR" -> 2;
        case "VERIFICATEUR-ASSISTANT" -> 1;
        default -> 0;
    };
}

private boolean canAssignTo(String currentRole, String targetRole) {
    return getRoleLevel(currentRole) >= getRoleLevel(targetRole);
}
    
    
    
}