package com.app.controller;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.model.Ticket;
import com.app.util.TicketUtil;
import java.util.Optional;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import com.app.controller.InternalTicketEditController;
import com.app.controller.ExternalTicketManagementController;
import com.app.util.LanguageManager;
import com.app.util.SecurityUtil;
import com.app.util.TicketEditorRouter;
import java.util.ResourceBundle;
import java.util.Set;

public class TicketManagementController {

    @FXML private TableView<Ticket> tableTickets;

    @FXML private TableColumn<Ticket,String> colTicketId;
    @FXML private TableColumn<Ticket,String> colTitle;
    @FXML private TableColumn<Ticket,String> colStatus;
    @FXML private TableColumn<Ticket,String> colType;
    @FXML private TableColumn<Ticket,String> colPriority;
    @FXML private TableColumn<Ticket,String> colAgent;
    @FXML private TableColumn<Ticket,String> colCreated;

    @FXML private TextField txtSearch;

    
    @FXML private ComboBox<String> cmbStatus;
    @FXML private ComboBox<String> cmbAgent;
    
    @FXML private TableColumn<Ticket, Void> colEscalate;
    @FXML private TableColumn<Ticket, Void> colView;
    @FXML private TableColumn<Ticket, Void> colEdit; 
    @FXML private TableColumn<Ticket, Void> colClose;
    @FXML private TableColumn<Ticket, Void> colDelete;
    
    @FXML private ProgressIndicator progressEscalation;
    @FXML private Label lblEscalationStatus;
    

    
    private ObservableList<Ticket> masterTickets = FXCollections.observableArrayList();
    private FilteredList<Ticket> filteredTickets;
   

    // =====================================================
    // INITIALIZE
    // =====================================================

    @FXML
    public void initialize() {
        
        // load tickets
masterTickets.setAll(TicketDAO.getAllTickets());

filteredTickets = new FilteredList<>(masterTickets, p -> true);

// live search
txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {

    filteredTickets.setPredicate(ticket -> {

    if(newVal == null || newVal.isEmpty()){
        return true;
    }

    String keyword = newVal.toLowerCase();

    String ref = TicketUtil.formatTicketRef(ticket.getId()).toLowerCase();
    String title = ticket.getTitle() != null ? ticket.getTitle().toLowerCase() : "";
    String status = ticket.getStatus() != null ? ticket.getStatus().toLowerCase() : "";
    String type = ticket.getTicketType() != null
        ? ticket.getTicketType().toLowerCase()
        : "";
    String priority = ticket.getPriority() != null ? ticket.getPriority().toLowerCase() : "";
    String agent = ticket.getAssignedToName() != null ? ticket.getAssignedToName().toLowerCase() : "";
    String created = ticket.getCreatedAt() != null ? ticket.getCreatedAt().toLowerCase() : "";

    return ref.contains(keyword)
        || title.contains(keyword)
        || status.contains(keyword)
        || type.contains(keyword)
        || priority.contains(keyword)
        || agent.contains(keyword)
        || created.contains(keyword);
});
});

SortedList<Ticket> sorted = new SortedList<>(filteredTickets);
sorted.comparatorProperty().bind(tableTickets.comparatorProperty());

tableTickets.setItems(sorted);
        
        
        
        tableTickets.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        colTicketId.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        TicketUtil.formatTicketRef(cell.getValue().getId())
                ));

        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(column -> new TableCell<>() {

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

            case "ASSIGNED":
                setStyle("-fx-background-color:#3498db; -fx-text-fill:white;");
                break;

            case "IN_PROGRESS":
                setStyle("-fx-background-color:#f39c12; -fx-text-fill:white;");
                break;

            case "ESCALATED":
                setStyle("-fx-background-color:#e74c3c; -fx-text-fill:white;");
                break;

            case "CLOSED":
                setStyle("-fx-background-color:#2ecc71; -fx-text-fill:white;");
                break;

            default:
                setStyle("");
        }
    }
});
        
        
        
        
        
        
        colType.setCellValueFactory(cellData -> {

    Ticket t = cellData.getValue();

    String type = t.getType();
    String sender = t.getCreatedByName();   // who created the ticket
    String dept = t.getDepartmentName();    // department / sous direction

    StringBuilder value = new StringBuilder();

    if(type != null && !type.isBlank())
        value.append(type);

    if(sender != null && !sender.isBlank())
        value.append(" | ").append(sender);

    if(dept != null && !dept.isBlank())
        value.append(" | ").append(dept);

    return new SimpleStringProperty(value.toString());
});
        
        
        
        
        colPriority.setCellValueFactory(new PropertyValueFactory<>("priority"));
        colAgent.setCellValueFactory(new PropertyValueFactory<>("assignedToName"));
        colCreated.setCellValueFactory(cell ->
        new SimpleStringProperty(cell.getValue().getCreatedAt())
);
        
        colEscalate.setCellFactory(param -> new TableCell<>() {

    private final Button btn = new Button("Escalate");

    {
        btn.getStyleClass().add("btn-escalate");

        btn.setOnAction(event -> {

    Ticket ticket = getTableView().getItems().get(getIndex());

    ChoiceDialog<String> dialog =
            new ChoiceDialog<>(null, TicketDAO.getAgents());

    dialog.setTitle("Escalate Ticket");
    dialog.setHeaderText("Select Agent to Escalate");
    dialog.setContentText("Agent:");

    dialog.showAndWait().ifPresent(agentName -> {

        int agentId = TicketDAO.getAgentIdByUsername(agentName);

// 🔴 CHECK IF TICKET IS ALREADY ASSIGNED TO THIS AGENT
if (ticket.getAssignedTo() != null && ticket.getAssignedTo() == agentId) {

    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Escalation Not Allowed");
    alert.setHeaderText("Ticket already escalated");
    alert.setContentText(
            "Ticket " + TicketUtil.formatTicketRef(ticket.getId())
            + " is already assigned to " + agentName + "."
    );
    alert.showAndWait();

    return;
}
        

        // 🔹 SHOW MESSAGE
        lblEscalationStatus.setText(
                "⏳ Escalation en cours... " +
                TicketUtil.formatTicketRef(ticket.getId())
        );

        progressEscalation.setVisible(true);
        lblEscalationStatus.setVisible(true);

        // 🔹 RUN ESCALATION IN BACKGROUND
        new Thread(() -> {

            TicketDAO.escalateTicket(
                    ticket.getId(),
                    Session.getUserId(),
                    agentId,
                    "HIGH",
                    "Escalated by admin"
            );

            javafx.application.Platform.runLater(() -> {

                loadTickets();

                progressEscalation.setVisible(false);
                lblEscalationStatus.setVisible(false);

            });

        }).start();

    });

});
    }

    @Override
    protected void updateItem(Void item, boolean empty) {

        super.updateItem(item, empty);

        if (empty) {
            setGraphic(null);
            return;
        }

        Ticket ticket = getTableView().getItems().get(getIndex());

        String status = ticket.getStatus();

        boolean allowed =
        status.equals("ASSIGNED")
     || status.equals("IN_PROGRESS")
     || status.equals("CLOSED");

        btn.setDisable(!allowed);

        setGraphic(btn);
    }
});
        
        
        
        
        colView.setCellValueFactory(param -> null);
        colClose.setCellValueFactory(param -> null);
        colDelete.setCellValueFactory(param -> null);
        

        loadTickets();
         loadFilters();
        
         
         
         
         
        
    

    // ===============================
    // 🔥 ROLE ACCESS CONTROL
    // ===============================
    String role = Session.getRole();

    if (!(role.equalsIgnoreCase("ADMIN")
            || role.equalsIgnoreCase("DIRECTEUR")
            || role.equalsIgnoreCase("SOUS-DIRECTEUR")
            || role.equalsIgnoreCase("INSPECTEUR")
            || role.equalsIgnoreCase("CONTROLEUR"))) {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Access Denied");
        alert.setHeaderText(null);
        alert.setContentText("You are not allowed to access Ticket Management.");
        alert.showAndWait();

        return; // 🔥 STOP loading
    }

    // ===============================
    // 🔥 NORMAL INITIALIZATION
    // ===============================
    setupTable();
    loadTickets();
    setupFilters();

        
        colView.setCellFactory(param -> new TableCell<>() {

    private final Button btn = new Button("View");

    {
        btn.getStyleClass().add("btn-view");

        btn.setOnAction(event -> {

            Ticket ticket = getTableView().getItems().get(getIndex());

            if (ticket != null) {
                openTicket(ticket);
            }

        });
    }

    @Override
    protected void updateItem(Void item, boolean empty) {

        super.updateItem(item, empty);

        if (empty) {
            setGraphic(null);
        } else {
            setGraphic(btn);
        }
    }
});
        
        
     colEdit.setCellFactory(param -> new TableCell<>() {

    private final Button btn = new Button("Edit");

    {
        btn.getStyleClass().add("btn-edit");

        btn.setOnAction(event -> {

            Ticket ticket = getTableView().getItems().get(getIndex());

            if (ticket != null) {
                openEditTicket(ticket);
            }

        });
    }

    @Override
    protected void updateItem(Void item, boolean empty) {

        super.updateItem(item, empty);

        if (empty) {
            setGraphic(null);
            return;
        }

        Ticket ticket = getTableView().getItems().get(getIndex());

        // Disable editing if ticket is CLOSED
        if ("CLOSED".equalsIgnoreCase(ticket.getStatus())) {
            btn.setDisable(true);
        } else {
            btn.setDisable(false);
        }

        setGraphic(btn);
    }
});   
        
        
        
        colClose.setCellFactory(param -> new TableCell<>() {

    private final Button btn = new Button("Close");

    {
        btn.getStyleClass().add("btn-close");

        btn.setOnAction(event -> {

            Ticket ticket = getTableView().getItems().get(getIndex());

            // ============================
            // CONFIRMATION DIALOG
            // ============================
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Close Ticket");
            alert.setHeaderText("Confirm Ticket Closure");
            alert.setContentText(
                    "Are you sure you want to close ticket "
                    + TicketUtil.formatTicketRef(ticket.getId()) + " ?"
            );

            ButtonType yesBtn = new ButtonType("Yes");
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(yesBtn, cancelBtn);

            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == yesBtn) {

                try {

                    TicketDAO.closeTicket(ticket.getId());

                    loadTickets();

                } catch (Exception e) {

                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Error");
                    error.setHeaderText("Cannot close ticket");
                    error.setContentText(e.getMessage());
                    error.showAndWait();
                }
            }

        });
    }

    @Override
    protected void updateItem(Void item, boolean empty) {

        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);

    }
});  
        
        
        colDelete.setCellFactory(param -> new TableCell<>() {

    private final Button btn = new Button("Delete");

    {
        btn.getStyleClass().add("btn-delete");

        btn.setOnAction(event -> {

            Ticket ticket = getTableView().getItems().get(getIndex());

            // ==============================
            // CONFIRMATION DIALOG
            // ==============================
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Ticket");
            alert.setHeaderText("Confirm Deletion");
            alert.setContentText(
                    "Are you sure you want to delete ticket "
                    + TicketUtil.formatTicketRef(ticket.getId()) + " ?"
            );

            ButtonType yesBtn = new ButtonType("Yes");
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(yesBtn, cancelBtn);

            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == yesBtn) {

                TicketDAO.deleteTicket(ticket.getId());

                loadTickets();
            }

        });
    }

    @Override
    protected void updateItem(Void item, boolean empty) {

        super.updateItem(item, empty);
        setGraphic(empty ? null : btn);

    }
});
     
        
    }

    
    
    private void loadFilters() {

    cmbStatus.getItems().add("All");
    cmbStatus.getItems().add("OPEN");
    cmbStatus.getItems().add("ASSIGNED");
    cmbStatus.getItems().add("ESCALATED");
    cmbStatus.getItems().add("IN_PROGRESS");
    cmbStatus.getItems().add("MERGED");
    cmbStatus.getItems().add("CLOSED");

    cmbStatus.getSelectionModel().selectFirst();

    cmbAgent.getItems().add("All");
    cmbAgent.getItems().addAll(TicketDAO.getDistinctAgents());

    cmbAgent.getSelectionModel().selectFirst();
}
    
    
    
    
    
    
    
    private void openTicket(Ticket ticket) {

    try {

        FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/TicketDetails.fxml"),
        ResourceBundle.getBundle("lang.messages")
);

Parent root = loader.load();

        TicketDetailsController controller = loader.getController();
        controller.setTicket(ticket);

        Stage stage = new Stage();
        stage.setTitle("Ticket Details - TCK-" + ticket.getId());

        Scene scene = new Scene(root);
        stage.setScene(scene);

        // same behavior as agent dashboard
        stage.setMaximized(true);

        stage.centerOnScreen();
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
        showError("Unable to open ticket view.");
    }
}
    
    
    
    
    // =====================================================
    // LOAD TICKETS
    // =====================================================

    @FXML
public void loadTickets() {

    masterTickets.setAll(TicketDAO.getAllTickets());

    // 🔥 force refresh of filtered list
    if (filteredTickets != null) {
        filteredTickets.setPredicate(filteredTickets.getPredicate());
    }

    tableTickets.refresh();
}

    // =====================================================
    // SEARCH
    // =====================================================

    @FXML
private void handleSearch() {

    String keyword = txtSearch.getText();

    if (keyword == null || keyword.isEmpty()) {
        filteredTickets.setPredicate(p -> true);
        return;
    }

    String lower = keyword.toLowerCase();

    filteredTickets.setPredicate(ticket -> {

        String ref = TicketUtil.formatTicketRef(ticket.getId()).toLowerCase();
        String title = ticket.getTitle() != null ? ticket.getTitle().toLowerCase() : "";
        String status = ticket.getStatus() != null ? ticket.getStatus().toLowerCase() : "";
        String type = ticket.getTicketType() != null ? ticket.getTicketType().toLowerCase() : "";
        String priority = ticket.getPriority() != null ? ticket.getPriority().toLowerCase() : "";
        String agent = ticket.getAssignedToName() != null ? ticket.getAssignedToName().toLowerCase() : "";

        return ref.contains(lower)
                || title.contains(lower)
                || status.contains(lower)
                || type.contains(lower)
                || priority.contains(lower)
                || agent.contains(lower);
    });
}

    // =====================================================
    // FILTER
    // =====================================================

    @FXML
private void handleFilter() {

    String status = cmbStatus.getValue();
    String agent = cmbAgent.getValue();

    filteredTickets.setPredicate(ticket -> {

        boolean matchesStatus = true;
        boolean matchesAgent = true;

        if (status != null && !"All".equals(status)) {
            matchesStatus = status.equalsIgnoreCase(ticket.getStatus());
        }

        if (agent != null && !"All".equals(agent)) {
            matchesAgent = agent.equalsIgnoreCase(ticket.getAssignedToName());
        }

        return matchesStatus && matchesAgent;
    });
}

    // =====================================================
    // VIEW
    // =====================================================

    @FXML
private void handleEdit() {

    Ticket ticket = tableTickets.getSelectionModel().getSelectedItem();

    if (ticket == null) {
        showAlert("Please select a ticket.");
        return;
    }

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/ticket_create.fxml"),
                ResourceBundle.getBundle("lang.messages")
        );

        Parent root = loader.load();

        TicketCreateController controller = loader.getController();

        // load the ticket into the form (edit mode)
        controller.loadTicket(ticket);

        Stage stage = new Stage();
        stage.setTitle("Edit External Ticket");
        stage.setScene(new Scene(root));
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    // =====================================================
    // REASSIGN
    // =====================================================

    @FXML
    private void handleReassign() {

        Ticket ticket = tableTickets.getSelectionModel().getSelectedItem();

        if (ticket == null) {
            showAlert("Please select a ticket.");
            return;
        }

        System.out.println("Reassign ticket: " + ticket.getId());
        // Later we open agent selection dialog
    }

    // =====================================================
    // MERGE
    // =====================================================

    @FXML
private void handleMerge() {

    ObservableList<Ticket> selectedTickets =
            tableTickets.getSelectionModel().getSelectedItems();

    if (selectedTickets.size() != 2) {
        showAlert("Please select TWO tickets to merge.");
        return;
    }

    Ticket mainTicket = selectedTickets.get(0);
    Ticket secondaryTicket = selectedTickets.get(1);

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Merge Tickets");
    confirm.setHeaderText(
            "Merge " +
            TicketUtil.formatTicketRef(secondaryTicket.getId()) +
            " into " +
            TicketUtil.formatTicketRef(mainTicket.getId())
    );

    confirm.setContentText("The second ticket will be closed as MERGED.");

    if (confirm.showAndWait().get() == ButtonType.OK) {

        TicketDAO.mergeTickets(mainTicket.getId(), secondaryTicket.getId());

        loadTickets();

        showAlert("Tickets merged successfully.");
    }
}

    // =====================================================
    // CLOSE
    // =====================================================

    @FXML
private void handleClose() {

    Ticket ticket = tableTickets.getSelectionModel().getSelectedItem();

    if (ticket == null) {
        showError("Select a ticket first.");
        return;
    }
    if (!SecurityUtil.canAccessTicket(ticket.getId())) {
    showMessage("You cannot close this ticket");
    return;
}
    

    try {

        TicketDAO.closeTicket(ticket.getId());

        loadTickets();

    } catch (RuntimeException ex) {

        showError(ex.getMessage());

    }
}

    // =====================================================
    // DELETE
    // =====================================================

    @FXML
    private void handleDelete() {

        Ticket ticket = tableTickets.getSelectionModel().getSelectedItem();

        if (ticket == null) {
            showAlert("Please select a ticket.");
            return;
        }

        TicketDAO.deleteTicket(ticket.getId());

        loadTickets();
    }
    
    @FXML
private void handleMergeTickets() {

    ObservableList<Ticket> selected = tableTickets.getSelectionModel().getSelectedItems();

    if (selected.size() != 2) {
        showAlert("Please select exactly TWO tickets to merge.");
        return;
    }

    Ticket mainTicket = selected.get(0);
    Ticket secondaryTicket = selected.get(1);

    if (mainTicket.getId() == secondaryTicket.getId()) {
        showAlert("Cannot merge the same ticket.");
        return;
    }

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Merge Tickets");
    confirm.setHeaderText("Merge Ticket "
            + TicketUtil.formatTicketRef(secondaryTicket.getId())
            + " into "
            + TicketUtil.formatTicketRef(mainTicket.getId()));

    confirm.setContentText("The secondary ticket will be closed as MERGED.");

    if (confirm.showAndWait().get() == ButtonType.OK) {

        TicketDAO.mergeTickets(mainTicket.getId(), secondaryTicket.getId());

        loadTickets(); // refresh table

        showAlert("Tickets merged successfully.");
    }
}
    
    
    private void showMessage(String msg) {

    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setHeaderText(null);
    alert.setContentText(msg);
    alert.showAndWait();
}
    

    // =====================================================
    // ALERT
    // =====================================================

    private void showAlert(String message) {

    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();

}
    
    private void showError(String message) {

    Alert alert = new Alert(Alert.AlertType.ERROR);
    alert.setTitle("Error");
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.showAndWait();
}
    
   private void openEditTicket(Ticket ticket) {

    try {

        Ticket fullTicket = TicketDAO.getTicketById(ticket.getId());
        System.out.println("Ticket Type from DB = " + fullTicket.getTicketType());

        if (fullTicket == null) {
            showError("Unable to load ticket.");
            return;
        }

        String ticketType = fullTicket.getTicketType();

        FXMLLoader loader;
        Parent root;

        // =========================
        // EXTERNAL TICKET
        // =========================
        if ("EXTERNAL".equalsIgnoreCase(ticketType)) {

            loader = new FXMLLoader(
                    getClass().getResource("/view/external_ticket_edit.fxml"),
                    LanguageManager.getBundle()
            );

            root = loader.load();

            ExternalTicketEditController controller = loader.getController();
            controller.loadTicket(fullTicket);

        }

        // =========================
        // INTERNAL TICKET
        // =========================
        
else {

    loader = new FXMLLoader(
            getClass().getResource("/view/user_excel_entry.fxml"),
            LanguageManager.getBundle()
    );

    root = loader.load();

    UserExcelEntryController controller = loader.getController();
    controller.loadTicket(fullTicket);

}

        Stage stage = new Stage();
        stage.setTitle("Edit Ticket - " + TicketUtil.formatTicketRef(fullTicket.getId()));
        stage.setScene(new Scene(root));
        stage.setMaximized(true);
        stage.show();

    } catch (Exception e) {

        e.printStackTrace();
        showError("Unable to open edit window.");

    }
}
   
   
   private void setupTable() {

    // ===============================
    // 🔥 LOAD DATA
    // ===============================
    masterTickets = FXCollections.observableArrayList(TicketDAO.getAllTickets());

    filteredTickets = new FilteredList<>(masterTickets, p -> true);

    SortedList<Ticket> sorted = new SortedList<>(filteredTickets);

    sorted.comparatorProperty().bind(tableTickets.comparatorProperty());

    tableTickets.setItems(sorted);

    // ===============================
    // 🔥 COLUMN MAPPING
    // ===============================
    colType.setCellValueFactory(new PropertyValueFactory<>("ticketType"));
    colPriority.setCellValueFactory(new PropertyValueFactory<>("priority"));
    colAgent.setCellValueFactory(new PropertyValueFactory<>("assignedToName"));
    colCreated.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

    // ===============================
    // 🔥 OPTIONAL: STYLE PRIORITY
    // ===============================
    colPriority.setCellFactory(col -> new TableCell<>() {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setStyle("");
            } else {
                setText(item);

                switch (item.toUpperCase()) {
                    case "HIGH" -> setStyle("-fx-background-color:#ef4444; -fx-text-fill:white;");
                    case "MEDIUM" -> setStyle("-fx-background-color:#f59e0b; -fx-text-fill:white;");
                    case "LOW" -> setStyle("-fx-background-color:#22c55e; -fx-text-fill:white;");
                    default -> setStyle("");
                }
            }
        }
    });
}
   
  private void setupFilters() {

    // ===============================
    // STATUS FILTER
    // ===============================
    cmbStatus.getItems().addAll(
            "All",
            "OPEN",
            "ASSIGNED",
            "IN_PROGRESS",
            "WAITING_ON_TASKS",
            "ESCALATED",
            "MERGED",
            "CLOSED"
    );
    cmbStatus.setValue("All");

    // ===============================
    // AGENT FILTER
    // ===============================
    cmbAgent.getItems().add("All");

    Set<String> agents = masterTickets.stream()
            .map(Ticket::getAssignedToName)
            .filter(a -> a != null && !a.isBlank())
            .collect(java.util.stream.Collectors.toSet());

    cmbAgent.getItems().addAll(agents);

    cmbAgent.setValue("All");

    // ===============================
    // 🔥 LIVE FILTER (NO BUTTON)
    // ===============================
    cmbStatus.setOnAction(e -> applyFilters());
    cmbAgent.setOnAction(e -> applyFilters());

    // ===============================
    // 🔥 LIVE SEARCH
    // ===============================
    txtSearch.textProperty().addListener((obs, oldVal, newVal) -> applySearch());
}
  
  
  private void applyFilters() {

    String status = cmbStatus.getValue();
    String agent = cmbAgent.getValue();

    filteredTickets.setPredicate(ticket -> {

        boolean matchesStatus = true;
        boolean matchesAgent = true;

        if (status != null && !"All".equals(status)) {
            matchesStatus = status.equalsIgnoreCase(ticket.getStatus());
        }

        if (agent != null && !"All".equals(agent)) {
            matchesAgent = agent.equalsIgnoreCase(ticket.getAssignedToName());
        }

        return matchesStatus && matchesAgent;
    });
}
   
   private void applySearch() {

    String keyword = txtSearch.getText();

    if (keyword == null || keyword.isEmpty()) {
        filteredTickets.setPredicate(p -> true);
        return;
    }

    String lower = keyword.toLowerCase();

    filteredTickets.setPredicate(ticket -> {

        return (ticket.getTitle() != null && ticket.getTitle().toLowerCase().contains(lower))
                || (ticket.getStatus() != null && ticket.getStatus().toLowerCase().contains(lower))
                || (ticket.getPriority() != null && ticket.getPriority().toLowerCase().contains(lower))
                || (ticket.getAssignedToName() != null && ticket.getAssignedToName().toLowerCase().contains(lower));
    });
}
   
    
    
}