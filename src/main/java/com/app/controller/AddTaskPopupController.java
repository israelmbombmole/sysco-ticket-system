package com.app.controller;

import com.app.auth.Session;
import com.app.dao.AttachmentDAO;
import com.app.dao.TicketDAO;
import com.app.dao.TicketTaskDAO;
import com.app.model.User;
import com.app.util.BusinessRuleException;
import com.app.util.I18n;
import com.app.util.RoleFlowUtil;
import com.app.util.SecurityUtil;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class AddTaskPopupController {

    @FXML private TextField txtTaskTitle;
    @FXML private TextField txtAgentSearch;
    @FXML private ComboBox<User> cmbAgents;
    @FXML private TextArea txtDescription;
    @FXML private ListView<File> attachmentList;
    @FXML private VBox attachmentFrame;

    private int ticketId;
    private ObservableList<User> agents;
    private FilteredList<User> filteredAgents;
    /** Full assignee list for coverage / multi-assign finalization (may differ from combo when sequential). */
    private List<User> coverageAssignees;
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
        attachmentList.setPlaceholder(new Label(I18n.t("noFilesSelected", "No files selected")));
    }

    // ============================
    // INIT DATA
    // ============================
    public void initData(int ticketId, ObservableList<User> comboAgents) {
        initData(ticketId, comboAgents, null);
    }

    /**
     * @param coverageForMultiAssign if non-null, used for "every assignee has a task" and
     *                               {@link TicketDAO#assignTicketToMultipleUsers}; otherwise loaded from DB.
     */
    public void initData(int ticketId, ObservableList<User> comboAgents, List<User> coverageForMultiAssign) {
        this.ticketId = ticketId;
        this.agents = comboAgents;

        if (coverageForMultiAssign != null && !coverageForMultiAssign.isEmpty()) {
            this.coverageAssignees = new ArrayList<>(coverageForMultiAssign);
        } else {
            List<User> ordered = TicketDAO.getActiveAssignedUsersOrdered(ticketId);
            this.coverageAssignees = ordered.isEmpty() ? null : new ArrayList<>(ordered);
        }

        filteredAgents = new FilteredList<>(comboAgents, u -> true);
        cmbAgents.setItems(filteredAgents);

        if (!comboAgents.isEmpty()) {
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

        txtAgentSearch.clear();
        txtAgentSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredAgents.setPredicate(user -> {
                if (user == null) return false;
                if (q.isEmpty()) return true;
                String username = user.getUsername() == null ? "" : user.getUsername().toLowerCase();
                String role = user.getRole() == null ? "" : user.getRole().toLowerCase();
                return username.contains(q) || role.contains(q);
            });

            if (!filteredAgents.isEmpty() && !filteredAgents.contains(cmbAgents.getValue())) {
                cmbAgents.getSelectionModel().selectFirst();
            } else if (filteredAgents.isEmpty()) {
                cmbAgents.getSelectionModel().clearSelection();
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
        showError(I18n.t("err.selectTaskAndAgent", "Please enter task and select agent."));
        return;
    }

    // ============================
    // ✅ STEP 2: SAFE ROLE HANDLING
    // ============================
    String currentRole = Session.getRole();
    String targetRole = selectedAgent.getRole();

    if (currentRole == null || targetRole == null) {
        showError(I18n.t("err.roleConfiguration", "Role configuration error. Contact admin."));
        return;
    }

    if (!canAssignTo(currentRole, targetRole)) {
        showError(I18n.t("err.assignHigherRole", "You cannot assign a task to a higher role."));
        return;
    }

    // ============================
    // ✅ STEP 3: SECURITY CHECK
    // ============================
    boolean canManageByRole = RoleFlowUtil.canSeeUnassignedTicketsForAssignment(currentRole);
    if (!canManageByRole && !SecurityUtil.canAccessTicket(ticketId)) {
        showError(I18n.t("err.notAllowedAddTasks", "You are not allowed to add tasks"));
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
            showError(I18n.t("err.failedCreateTask", "Failed to create task"));
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
        // ✅ CHECK COVERAGE (full assignee list, not the sequential combo alone)
        // ============================
        List<User> coverageList =
                (coverageAssignees != null && !coverageAssignees.isEmpty())
                        ? coverageAssignees
                        : new ArrayList<>(agents);

        boolean allCovered =
                TicketTaskDAO.allAgentsHaveTasks(ticketId, coverageList);

        if (allCovered) {

            List<Integer> ids = new ArrayList<>();
            for (User u : coverageList) {
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
            // Remove the user that just got a task, so the next assignee appears automatically.
            agents.removeIf(u -> u != null && u.getId() == selectedAgent.getId());
            if (!agents.isEmpty()) {
                cmbAgents.getSelectionModel().selectFirst();
            } else {
                cmbAgents.getSelectionModel().clearSelection();
            }

            selectedFiles.clear();
            attachmentList.getItems().clear();

            attachmentFrame.setVisible(true);
            attachmentFrame.setManaged(true);
        }

    } catch (BusinessRuleException e) {
        showError(e.getMessage());
    } catch (Exception e) {
        e.printStackTrace();
        showError(I18n.t("err.errorSavingTask", "Error saving task"));
    }
}
    
    
    
    // ============================
    // BROWSE FILES
    // ============================
    @FXML
    private void handleBrowseFiles() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("selectAttachments", "Select Attachments"));

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
        alert.setTitle(I18n.t("error", "Error"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private int getRoleLevel(String role) {

    if (role == null) return -1; 

    return switch (role.toUpperCase()) {
        case "ADMIN" -> 99;
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
    if ("ADMIN".equalsIgnoreCase(currentRole)) {
        return true;
    }
    return getRoleLevel(currentRole) >= getRoleLevel(targetRole);
}
    
    
    
}