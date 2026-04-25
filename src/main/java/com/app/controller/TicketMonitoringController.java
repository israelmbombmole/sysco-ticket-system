package com.app.controller;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.Ticket;
import com.app.model.User;
import com.app.util.LanguageManager;
import com.app.util.RoleFlowUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class TicketMonitoringController {

    // ===== ASSIGN TABLE =====
    @FXML private TableView<Ticket> tableTickets;
    @FXML private TableColumn<Ticket, String> colTitleAssign;
    @FXML private TableColumn<Ticket, String> colStatusAssign;
    

   

    // ===== ASSIGNMENTS TABLE =====
    @FXML private TableView<Ticket> tableAssignments;
   
    @FXML private TableColumn<Ticket, String> colAgentAssigned;
    @FXML private TableColumn<Ticket, String> colStatusAssigned;
    @FXML private ListView<User> listAgents;
@FXML private ComboBox<String> cmbTargetRole;

    @FXML private TextField txtSearch;

    private FilteredList<Ticket> filteredData;

    // ===== STATS TABLE =====
    @FXML private TableView<User> tableStats;
    @FXML private TableColumn<User, String> colAgentName;
    @FXML private TableColumn<User, Integer> colTotalTickets;

    @FXML private PieChart ticketPieChart;

    //@FXML private TableColumn<Ticket, Void> colEscalate;
    //@FXML private TableColumn<Ticket, Void> colDelete;
    @FXML private TableColumn<Ticket, String> colTicketIdAssign;
    @FXML private TableColumn<Ticket, String> colTicketIdAssigned;

    
    
    @FXML
public void initialize() {

    
    //listAgents.setItems(UserDAO.getAllAgents());
    listAgents.setTooltip(new Tooltip(LanguageManager.getBundle().getString("monitoringTooltipMultiSelect")));
    listAgents.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    tableTickets.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    tableAssignments.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    tableStats.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    // ================= ASSIGN TABLE =================
    colTicketIdAssign.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                    com.app.util.TicketUtil.formatTicketRef(cell.getValue().getId())
            )
    );

    colTitleAssign.setCellValueFactory(new PropertyValueFactory<>("title"));
    colStatusAssign.setCellValueFactory(new PropertyValueFactory<>("status"));


    // ================= ASSIGNED TABLE =================
    colTicketIdAssigned.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                    com.app.util.TicketUtil.formatTicketRef(cell.getValue().getId())
            )
    );

    colAgentAssigned.setCellValueFactory(new PropertyValueFactory<>("assignedToName"));

    // 🔥 THIS LINE WAS MISSING
    colStatusAssigned.setCellValueFactory(new PropertyValueFactory<>("status"));

    colStatusAssigned.setCellFactory(column -> new TableCell<>() {
        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);

            if (empty || status == null) {
                setText(null);
                setStyle("");
            } else {

                setText(status);

                switch (status) {
                    case "ASSIGNED" ->
                        setStyle("-fx-background-color:#3b82f6; -fx-text-fill:white;");

                    case "IN_PROGRESS" ->
                        setStyle("-fx-background-color:#f59e0b; -fx-text-fill:white;");

                    case "CLOSED" ->
                        setStyle("-fx-background-color:#10b981; -fx-text-fill:white;");

                    default ->
                        setStyle("");
                }
            }
        }
    });




// ================= STATS =================
colAgentName.setCellValueFactory(new PropertyValueFactory<>("username"));
colTotalTickets.setCellValueFactory(new PropertyValueFactory<>("ticketCount"));


// ================= LOAD DATA =================
loadUnassignedTickets();
loadAssignedTickets();
loadStats();
loadChart();
initTargetRoleFilter();



setupLiveSearch();
addViewButtonColumn();
        
      
    }
    
    



    
    
    private void deleteTicket(Ticket ticket) {

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    java.util.ResourceBundle b = LanguageManager.getBundle();

    confirm.setTitle(b.getString("deleteTicketTitle"));
    confirm.setHeaderText(java.text.MessageFormat.format(
            b.getString("deleteTicketHeader"), ticket.getId()));

    confirm.showAndWait().ifPresent(response -> {

        if (response == ButtonType.OK) {

            TicketDAO.deleteTicket(ticket.getId());

            reloadAssignments();
        }
    });
}
    
    
    
    
    
    private void reloadAssignments() {

    loadUnassignedTickets();
    loadAssignedTickets();
    loadStats();
    loadChart();
}
    
    
    
    private void showEscalationDialog(Ticket ticket) {

    java.util.ResourceBundle b = LanguageManager.getBundle();
    Dialog<User> dialog = new Dialog<>();
    dialog.setTitle(b.getString("escalateTicketTitle"));
    dialog.setHeaderText(java.text.MessageFormat.format(
            b.getString("escalateTicketHeader"), ticket.getId()));

    ButtonType escalateButtonType = new ButtonType(
            b.getString("actionEscalate"), ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(escalateButtonType, ButtonType.CANCEL);

    ComboBox<User> agentCombo = new ComboBox<>();
    agentCombo.setItems(UserDAO.findAllAgents());
    agentCombo.setPromptText(b.getString("monitoringSelectAgent"));

    dialog.getDialogPane().setContent(agentCombo);

    dialog.setResultConverter(dialogButton -> {
        if (dialogButton == escalateButtonType) {
            return agentCombo.getValue();
        }
        return null;
    });

    dialog.showAndWait().ifPresent(selectedAgent -> {

        if (selectedAgent == null) {
            return;
        }

        // escalate ticket
        TicketDAO.escalateTicket(
        ticket.getId(),
        0,
        selectedAgent.getId(),
        "HIGH",
        "Escalated from monitoring dashboard"
);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(java.text.MessageFormat.format(
                LanguageManager.getBundle().getString("infoTicketEscalated"),
                selectedAgent.getUsername()));
        alert.showAndWait();

        reloadAssignments();
    });
}
    
    

    private void loadUnassignedTickets() {
    String role = Session.getRole();
    tableTickets.setItems(TicketDAO.getTicketsForMonitoringRole(role, Session.getUserId()));
}

   private void loadAssignedTickets() {

    int userId = Session.getUserId();

    filteredData = new FilteredList<>(
        FXCollections.observableArrayList(
            TicketDAO.getTicketsForUser(userId)
        ),
        p -> true
    );

    SortedList<Ticket> sortedData = new SortedList<>(filteredData);
    sortedData.comparatorProperty().bind(tableAssignments.comparatorProperty());

    tableAssignments.setItems(sortedData);
}

    private void setupLiveSearch() {

        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> {

            filteredData.setPredicate(ticket -> {

                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String filter = newValue.toLowerCase();

                if (String.valueOf(ticket.getId()).contains(filter)) {
                    return true;
                }

                if (ticket.getAssignedToName() != null &&
                        ticket.getAssignedToName().toLowerCase().contains(filter)) {
                    return true;
                }

                if (ticket.getStatus().toLowerCase().contains(filter)) {
                    return true;
                }

                return false;
            });
        });
    }

    private void addViewButtonColumn() {

        TableColumn<Ticket, Void> colView = new TableColumn<>("Voir");

        colView.setCellFactory(param -> new TableCell<>() {

            private final Button btn = new Button("👁 " + LanguageManager.getBundle().getString("courierViewButton"));

            {
               btn.setStyle(
    "-fx-background-color:#2563eb;" +
    "-fx-text-fill:white;" +
    "-fx-font-weight:bold;" +
    "-fx-background-radius:6;"
);
                btn.setOnAction(event -> {
                    Ticket ticket = getTableView().getItems().get(getIndex());
                    openTicketDetails(ticket);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tableAssignments.getColumns().add(colView);
    }

    private void openTicketDetails(Ticket ticket) {

        try {
            FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/TicketDetails.fxml"),
        LanguageManager.getBundle()
);

Parent root = loader.load();

            TicketDetailsController controller = loader.getController();
            controller.setTicket(ticket);

            Stage stage = new Stage();
            stage.setTitle(LanguageManager.getBundle().getString("stageTicketDetails")
                    + " - TCK-" + ticket.getId());
            stage.setScene(new Scene(root));

            stage.show();
            stage.setMaximized(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadStats() {

    var performance = TicketDAO.getUserPerformance();

    ObservableList<User> list = FXCollections.observableArrayList();

    for (var entry : performance.entrySet()) {

        User u = new User();
        u.setUsername(entry.getKey());
        u.setTicketCount(entry.getValue());

        list.add(u);
    }

    tableStats.setItems(list);
}

    private void loadChart() {

        int open = TicketDAO.countByStatus("OPEN");
        int closed = TicketDAO.countByStatus("CLOSED");

        java.util.ResourceBundle b = LanguageManager.getBundle();
        ticketPieChart.setData(FXCollections.observableArrayList(
                new PieChart.Data(b.getString("open"), open),
                new PieChart.Data(b.getString("closed"), closed)
        ));
    }
private void initTargetRoleFilter() {
    if (cmbTargetRole == null) {
        return;
    }

    cmbTargetRole.getItems().clear();
    Set<String> allowedRoles = RoleFlowUtil.getAllowedTargetRoles(Session.getRole());
    cmbTargetRole.getItems().addAll(allowedRoles);
    if (!cmbTargetRole.getItems().isEmpty()) {
        cmbTargetRole.getSelectionModel().selectFirst();
    }

    cmbTargetRole.setOnAction(e -> loadAgentsByTargetRole());
    loadAgentsByTargetRole();
}

private void loadAgentsByTargetRole() {
    if (cmbTargetRole == null) {
        return;
    }

    String selectedRole = cmbTargetRole.getValue();
    if (selectedRole == null || selectedRole.isBlank()) {
        listAgents.setItems(FXCollections.observableArrayList());
        return;
    }

    listAgents.setItems(UserDAO.getUsersByRoles(List.of(selectedRole)));
    listAgents.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
}

private boolean isDirectionCompatible(Ticket ticket, User agent) {
    if (ticket == null || agent == null) {
        return false;
    }

    String sessionRole = Session.getRole() == null ? "" : Session.getRole().toUpperCase();

    if ("SECRETAIRE".equals(sessionRole) && "SOUS-DIRECTEUR".equalsIgnoreCase(agent.getRole())) {
        Integer ticketSousDirectionId = ticket.getSousDirectionId();
        Integer agentSousDirectionId = agent.getSousDirectionId();
        if (ticketSousDirectionId == null || agentSousDirectionId == null) {
            return false;
        }
        return ticketSousDirectionId.equals(agentSousDirectionId);
    }

    Integer ticketDirectionId = ticket.getDepartmentId();
    if (ticketDirectionId == null || agent.getDirectionId() == null) {
        return false;
    }
    return ticketDirectionId.equals(agent.getDirectionId());
}
    

    @FXML
private void handleAssign() {

    Ticket selectedTicket = tableTickets.getSelectionModel().getSelectedItem();

    ObservableList<User> selectedAgents =
            listAgents.getSelectionModel().getSelectedItems();

    if (selectedTicket == null || selectedAgents.isEmpty()) {
        showError(LanguageManager.getBundle().getString("errMustSelectTicketAgent"));
        return;
    }

    try {

        // ===============================
        // ✅ SINGLE ASSIGNMENT
        // ===============================
        if (selectedAgents.size() == 1) {

            User selectedAgent = selectedAgents.get(0);
            if (!isDirectionCompatible(selectedTicket, selectedAgent)) {
                showError(LanguageManager.getBundle().getString("assignmentDirectionError"));
                return;
            }

            int userId = selectedAgent.getId();

            TicketDAO.assignTicket(selectedTicket.getId(), userId);

            reloadAssignments();
            return;
        }

        // ===============================
        // 🔥 MULTI ASSIGNMENT → POPUP
        // ===============================
        List<User> directionSafeAgents = selectedAgents
                .stream()
                .filter(agent -> isDirectionCompatible(selectedTicket, agent))
                .toList();
        if (directionSafeAgents.isEmpty()) {
            showError(LanguageManager.getBundle().getString("assignmentDirectionError"));
            return;
        }
        if (directionSafeAgents.size() != selectedAgents.size()) {
            showError(LanguageManager.getBundle().getString("assignmentDirectionError"));
        }
        openTaskAssignmentPopup(selectedTicket, directionSafeAgents);

    } catch (Exception e) {

        e.printStackTrace();
        showError(LanguageManager.getBundle().getString("errAssignmentFailed") + " " + e.getMessage());
    }
}


private void openTaskAssignmentPopup(Ticket ticket, List<User> agents) {

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/AddTaskPopup.fxml"),
                LanguageManager.getBundle()
        );

        Parent root = loader.load();

        AddTaskPopupController controller = loader.getController();

        // 🔥 PASS DATA
       controller.initData(
    ticket.getId(),
    FXCollections.observableArrayList(agents)
);
        

        Stage stage = new Stage();
        stage.setTitle(LanguageManager.getBundle().getString("stageAssignTasks"));
        stage.setScene(new Scene(root));
        stage.showAndWait();

        // AFTER popup closes → refresh UI
        reloadAssignments();

    } catch (Exception e) {
        e.printStackTrace();
    }
}



    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    
    private void loadMyTickets() {

    int userId = Session.getUserId();

    ObservableList<Ticket> myTickets =
            TicketDAO.getWorkQueue(userId);

    tableTickets.setItems(myTickets);
}
    
    public static boolean canAccessTicket(int ticketId) {

    int userId = Session.getUserId();
    String role = Session.getRole();

    // High level override
    if (RoleFlowUtil.isHighLevel(role)) {
        return true;
    }

    return TicketDAO.isUserCurrentOwner(ticketId, userId);
}
   
    
    private void loadAssignments() {

    ObservableList<Ticket> list = TicketDAO.getAssignedTickets();

    System.out.println("Assignments size = " + list.size()); // DEBUG

    tableAssignments.setItems(list);
}
    
    
}