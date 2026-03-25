package com.app.controller;

import com.app.dao.TicketDAO;
import com.app.model.Ticket;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ComboBox;
import java.io.File;

public class EditTicketController {

    @FXML private TextField txtTitle;
    @FXML private TextArea txtDescription;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private ComboBox<String> cmbPriority;
    private File selectedFile;

    
    
    private int ticketId;

    public void loadTicket(int ticketId) {

    this.ticketId = ticketId;

    try {

        Ticket ticket = TicketDAO.getTicketById(ticketId);

        if (ticket == null) return;

        txtTitle.setText(ticket.getTitle());
        txtDescription.setText(ticket.getDescription());

        cmbStatus.setValue(ticket.getStatus());
        cmbPriority.setValue(ticket.getPriority());

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    

    @FXML
    private void handleSave() {

        try {

            TicketDAO.updateTicket(

                    ticketId,
                    txtTitle.getText(),
                    txtDescription.getText(),
                    cmbStatus.getValue(),
                    cmbPriority.getValue()

            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}