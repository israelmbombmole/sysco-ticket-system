package com.app.controller;

import com.app.dao.AttachmentDAO;
import com.app.dao.DepartmentDAO;
import com.app.model.Department;
import com.app.model.Ticket;
import javafx.scene.control.ComboBox;
import com.app.model.Department;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.stage.Stage;

public class ExternalTicketEditController {

    @FXML private TextField txtTitle;
    @FXML private TextArea txtDescription;
   
    
    @FXML private ListView<String> attachmentList;
    @FXML
private ComboBox<String> cmbPriority;

@FXML
private ComboBox<Department> cmbDepartment;
@FXML private Button btnUpdate;



    private Ticket editingTicket;
    private List<File> selectedFiles = new ArrayList<>();
    
    
    
    @FXML
public void initialize() {

    // Priority values
    cmbPriority.setItems(FXCollections.observableArrayList(
            "LOW",
            "MEDIUM",
            "HIGH",
            "CRITICAL"
    ));

    // Load departments
    cmbDepartment.setItems(
            FXCollections.observableArrayList(
                    DepartmentDAO.getAllDepartments()
            )
    );
}
    
    
    

    public void loadTicket(Ticket ticket) {

        editingTicket = ticket;
        selectedFiles.clear();

        txtTitle.setText(ticket.getTitle());
        txtDescription.setText(ticket.getDescription());

        if (ticket.getPriority() != null) {
            cmbPriority.setValue(ticket.getPriority());
        }

        // Load attachments
        try {

            List<String> files =
                    AttachmentDAO.getAttachmentsByTicket(ticket.getId());

            attachmentList.getItems().clear();

            for (String path : files) {

                File f = new File(path);

                if (f.exists()) {
                    attachmentList.getItems().add(f.getName());
                    selectedFiles.add(f);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBrowseFiles() {

        FileChooser chooser = new FileChooser();

        List<File> files = chooser.showOpenMultipleDialog(null);

        if (files != null) {

            selectedFiles.addAll(files);

            for (File f : files) {
                attachmentList.getItems().add(f.getName());
            }
        }
    }

    @FXML
    private void handleRemoveFile() {

        int index = attachmentList.getSelectionModel().getSelectedIndex();

        if (index >= 0) {

            attachmentList.getItems().remove(index);
            selectedFiles.remove(index);
        }
    }

   @FXML
private void handleUpdate() {

    if (editingTicket == null) return;

    try {

        String title = txtTitle.getText();
        String description = txtDescription.getText();
        String priority = cmbPriority.getValue();

        Department department = cmbDepartment.getValue();

        if (department == null) {
            new Alert(Alert.AlertType.ERROR, "Please select a department").show();
            return;
        }

        com.app.dao.TicketDAO.updateTicket(
                editingTicket.getId(),
                title,
                description,
                priority,
                department.getId(),
                selectedFiles
        );

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText("Ticket updated successfully.");
        alert.showAndWait();

        Stage stage = (Stage) btnUpdate.getScene().getWindow();
        stage.close();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    @FXML
    private void handleClear() {

        txtTitle.clear();
        txtDescription.clear();
        attachmentList.getItems().clear();
        selectedFiles.clear();
    }
}