package com.app.controller;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.Ticket;
import com.app.model.User;
import javafx.collections.FXCollections;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

public class ExternalTicketManagementController {


@FXML private TableView<Ticket> tableTickets;
@FXML private TableColumn<Ticket, Integer> colId;
@FXML private TableColumn<Ticket, String> colTitle;
@FXML private TableColumn<Ticket, String> colPriority;
@FXML private TableColumn<Ticket, String> colDepartment;
@FXML private TableColumn<Ticket, String> colStatus;
@FXML private TextField txtTitle;
@FXML private TextArea txtDescription;
@FXML private ComboBox<String> cmbPriority;

// ✅ Only ONE ComboBox
@FXML private ComboBox<User> cmbAgents;

@FXML
public void initialize() {

    colId.setCellValueFactory(new PropertyValueFactory<>("id"));
    colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
    colPriority.setCellValueFactory(new PropertyValueFactory<>("priority"));
    colDepartment.setCellValueFactory(new PropertyValueFactory<>("departmentName"));
    colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

    loadTickets();
    loadAgents();

    // show username in ComboBox
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

private void loadTickets() {
    tableTickets.setItems(TicketDAO.getExternalTickets());
}

private void loadAgents() {
    cmbAgents.setItems(
    FXCollections.observableArrayList(UserDAO.getAllAgents())
);
}

@FXML
private void handleAssign() {

    Ticket selected = tableTickets.getSelectionModel().getSelectedItem();
    User agent = cmbAgents.getValue();

    if (selected == null || agent == null) {
        showAlert("Select ticket and agent.");
        return;
    }

    TicketDAO.assignTicket(selected.getId(), agent.getId());
    loadTickets();
}

@FXML
private void handleEscalate() {

    Ticket selected = tableTickets.getSelectionModel().getSelectedItem();
    User agent = cmbAgents.getValue();

    if (selected == null) {
        showAlert("Select ticket first.");
        return;
    }

    if (agent == null) {
        showAlert("Select agent to escalate to.");
        return;
    }

    int toAgent = agent.getId();
    int fromAgent = Session.getUserId();

    TicketDAO.escalateTicket(
        selected.getId(),
        fromAgent,
        toAgent,
        "HIGH",
        "Escalated by admin"
);

    loadTickets();
}

private void showAlert(String msg) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setContentText(msg);
    alert.showAndWait();
}


private Ticket ticket;

public void setTicket(Ticket ticket) {

    this.ticket = ticket;

    if (ticket != null) {

        txtTitle.setText(ticket.getTitle());
        txtDescription.setText(ticket.getDescription());

        if (ticket.getPriority() != null) {
            cmbPriority.setValue(ticket.getPriority());
        }

    }
}


}
