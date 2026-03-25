package com.app.controller;

import com.app.dao.TicketDAO;
import com.app.model.Ticket;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class InternalTicketEditController {

    @FXML private TextField txtTitle;
    @FXML private TextArea txtDescription;
    @FXML private ComboBox<String> cmbPriority;
    @FXML private Label message;

    private Ticket ticket;

    @FXML
    public void initialize() {

        cmbPriority.setItems(FXCollections.observableArrayList(
                "LOW",
                "MEDIUM",
                "HIGH",
                "CRITICAL"
        ));
    }

    public void setTicket(Ticket ticket) {

        this.ticket = ticket;

        txtTitle.setText(ticket.getTitle());
        txtDescription.setText(ticket.getDescription());
        cmbPriority.setValue(ticket.getPriority());
    }

    @FXML
    private void handleUpdate() {

        if (ticket == null) return;

        try {

            String title = txtTitle.getText().trim();
            String description = txtDescription.getText().trim();
            String priority = cmbPriority.getValue();

            if (priority == null) priority = "MEDIUM";

            TicketDAO.updateTicket(
                    ticket.getId(),
                    title,
                    description,
                    priority
            );

            message.setText("Ticket updated successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            message.setText("Error updating ticket.");
        }
    }

    @FXML
    private void handleCancel() {

        Stage stage = (Stage) txtTitle.getScene().getWindow();
        stage.close();
    }
}