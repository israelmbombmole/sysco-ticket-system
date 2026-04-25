package com.app.controller;

import com.app.dao.DepartmentDAO;
import com.app.dao.TicketDAO;
import com.app.model.Department;
import com.app.model.Ticket;
import com.app.util.LanguageManager;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TicketCreateController {

    @FXML private TextField txtTitle;
    @FXML private TextArea txtDescription;
    @FXML private ComboBox<String> cmbPriority;
    @FXML private ComboBox<Department> cmbDepartment;

    @FXML private Button btnCreate;
    @FXML private Label lblTitle;

    // ✅ ATTACHMENTS
    @FXML private ListView<String> attachmentList;

    private Ticket editingTicket;

    // MULTIPLE FILES
    private List<File> selectedFiles = new ArrayList<>();


    // =====================================
    // INITIALIZE
    // =====================================
    @FXML
    public void initialize() {

        cmbPriority.setItems(FXCollections.observableArrayList(
                "LOW", "MEDIUM", "HIGH", "CRITICAL"
        ));
        cmbPriority.getSelectionModel().select("LOW");

        cmbDepartment.setItems(
                FXCollections.observableArrayList(
                        DepartmentDAO.getAllDepartments()
                )
        );
    }


    // =====================================
    // ADD ATTACHMENTS
    // =====================================
    @FXML
    private void handleBrowseFiles() {

        FileChooser chooser = new FileChooser();

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Allowed Files",
                        "*.png", "*.jpg", "*.jpeg",
                        "*.pdf", "*.docx", "*.xlsx", "*.txt"
                )
        );

        List<File> files = chooser.showOpenMultipleDialog(txtTitle.getScene().getWindow());

        if (files != null) {

            selectedFiles.addAll(files);

            attachmentList.getItems().clear();

            for (File f : selectedFiles) {
                attachmentList.getItems().add(f.getName());
            }
        }
    }


    // =====================================
    // REMOVE ATTACHMENT
    // =====================================
    @FXML
    private void handleRemoveFile() {

        int index = attachmentList.getSelectionModel().getSelectedIndex();

        if (index >= 0) {

            selectedFiles.remove(index);
            attachmentList.getItems().remove(index);
        }
    }


    // =====================================
    // CREATE OR UPDATE TICKET
    // =====================================
    @FXML
    private void handleCreate() {

        String title = txtTitle.getText();
        String description = txtDescription.getText();
        String priority = cmbPriority.getValue();
        Department department = cmbDepartment.getValue();

        if (title == null || title.isEmpty()
                || description == null || description.isEmpty()
                || department == null) {

            showAlert(LanguageManager.getBundle().getString("errMissingFields"));
            return;
        }

        try {

            if (editingTicket != null) {

                // 🔄 UPDATE
                TicketDAO.updateTicket(
                        editingTicket.getId(),
                        title,
                        description,
                        priority,
                        department.getId(),
                        selectedFiles
                );

                System.out.println("✅ Ticket updated");

            } else {

                String ticketType = "EXTERNAL";

                // ✅ NO USER ASSIGNMENT HERE
                TicketDAO.createTicket(
                        title,
                        description,
                        priority,
                        department,
                        null, // no assigned user
                        selectedFiles,
                        ticketType,
                        null  // 🔥 no users
                );

                showAlert(LanguageManager.getBundle().getString("infoTicketCreated"));
            }

            handleClear();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(LanguageManager.getBundle().getString("errSavingTicket"));
        }
    }


    // =====================================
    // LOAD EXISTING TICKET (EDIT MODE)
    // =====================================
    public void loadTicket(Ticket ticket) {

        editingTicket = ticket;

        lblTitle.setText(LanguageManager.getBundle().getString("externalEditTitle"));
        btnCreate.setText(LanguageManager.getBundle().getString("externalUpdateButton"));

        txtTitle.setText(ticket.getTitle());
        txtDescription.setText(ticket.getDescription());

        if (ticket.getPriority() != null) {
            cmbPriority.setValue(ticket.getPriority());
        }

        if (ticket.getDepartmentId() != null) {

            for (Department d : cmbDepartment.getItems()) {
                if (d.getId() == ticket.getDepartmentId()) {
                    cmbDepartment.setValue(d);
                    break;
                }
            }
        }

        attachmentList.getItems().clear();

        if (ticket.getAttachments() != null) {
            attachmentList.getItems().addAll(ticket.getAttachments());
        }
    }


    // =====================================
    // CLEAR FORM
    // =====================================
    @FXML
    private void handleClear() {

        txtTitle.clear();
        txtDescription.clear();

        cmbPriority.getSelectionModel().select("LOW");
        cmbDepartment.getSelectionModel().clearSelection();

        attachmentList.getItems().clear();
        selectedFiles.clear();
    }


    // =====================================
    // ALERT
    // =====================================
    private void showAlert(String msg) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}