package com.app.controller;

import com.app.model.Ticket;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public class TicketViewController {

    @FXML private Label lblTicket;
    @FXML private Label lblTitle;
    @FXML private Label lblStatus;
    @FXML private Label lblType;
    @FXML private Label lblPriority;
    @FXML private Label lblAgent;
    @FXML private Label lblCreated;
    @FXML private TextArea txtDescription;

    public void setTicket(Ticket ticket) {

        lblTicket.setText("TCK-" + ticket.getId());
        lblTitle.setText(ticket.getTitle());
        lblStatus.setText(ticket.getStatus());
        lblType.setText(ticket.getTicketType());
        lblPriority.setText(ticket.getPriority());
        lblAgent.setText(ticket.getAssignedToName());
        lblCreated.setText(ticket.getCreatedAt());

        txtDescription.setText(ticket.getDescription());
    }
}