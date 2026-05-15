package com.app.controller;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.ExternalEscalation;
import com.app.model.Ticket;
import com.app.model.User;
import com.app.util.AppUiStyles;
import com.app.util.BusinessRuleException;
import com.app.util.LanguageManager;
import com.app.util.I18n;
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
import javafx.scene.layout.VBox;
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
    @FXML private TextField txtAgentSearch;

    @FXML private TextField txtSearch;

    private FilteredList<Ticket> filteredData;
    private ObservableList<User> masterAgents = FXCollections.observableArrayList();
    private FilteredList<User> filteredAgents;

    // ===== STATS TABLE =====
    @FXML private TableView<User> tableStats;
    @FXML private TableColumn<User, String> colAgentName;
    @FXML private TableColumn<User, Integer> colTotalTickets;

    @FXML private PieChart ticketPieChart;

    //@FXML private TableColumn<Ticket, Void> colEscalate;
    //@FXML private TableColumn<Ticket, Void> colDelete;
    @FXML private TableColumn<Ticket, String> colTicketIdAssign;
    @FXML private TableColumn<Ticket, String> colTicketIdAssigned;
    @FXML private VBox externalEscalationsSection;
    @FXML private TableView<ExternalEscalation> tableExternalEscalations;
    @FXML private TableColumn<ExternalEscalation, String> colExtTicket;
    @FXML private TableColumn<ExternalEscalation, String> colExtFromDirection;
    @FXML private TableColumn<ExternalEscalation, String> colExtToDirection;
    @FXML private TableColumn<ExternalEscalation, String> colExtSousDirection;
    @FXML private TableColumn<ExternalEscalation, String> colExtStatus;
    @FXML private TableColumn<ExternalEscalation, String> colExtAssignedTo;
    @FXML private TableColumn<ExternalEscalation, Void> colExtAction;

    
    
    @FXML
public void initialize() {

    
    //listAgents.setItems(UserDAO.getAllAgents());
    listAgents.setTooltip(new Tooltip("Hold CTRL to select multiple agents"));
    listAgents.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    tableTickets.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    tableAssignments.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    tableStats.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    if (tableExternalEscalations != null) {
        tableExternalEscalations.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    // ================= ASSIGN TABLE =================
    colTicketIdAssign.setCellValueFactory(cell ->
            new javafx.beans.property.SimpleStringProperty(
                    com.app.util.TicketUtil.formatTicketRef(cell.getValue().getId())
            )
    );

    colTitleAssign.setCellValueFactory(new PropertyValueFactory<>("title"));
    colStatusAssign.setCellValueFactory(new PropertyValueFactory<>("status"));
    colStatusAssign.setCellFactory(column -> new TableCell<>() {
        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            setText(empty || status == null ? null : I18n.status(status));
        }
    });


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

                setText(I18n.status(status));

                switch (status) {
                    case "ASSIGNED" ->
                        setStyle(AppUiStyles.Gov.STATUS_ASSIGNED);

                    case "IN_PROGRESS" ->
                        setStyle(AppUiStyles.Gov.STATUS_IN_PROGRESS);

                    case "CLOSED" ->
                        setStyle(AppUiStyles.Gov.STATUS_CLOSED);

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
loadAgents();
setupExternalEscalationColumns();
loadExternalEscalations();



setupLiveSearch();
addViewButtonColumn();
        
      
    }
    
    



    
    
    private void deleteTicket(Ticket ticket) {

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);

    confirm.setTitle(I18n.t("deleteTicket", "Delete Ticket"));
    confirm.setHeaderText(I18n.t("confirmDeleteTicket", "Are you sure you want to delete ticket #") + ticket.getId() + "?");

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
    loadExternalEscalations();
    loadStats();
    loadChart();
}

    private void setupExternalEscalationColumns() {
        if (tableExternalEscalations == null) return;

        colExtTicket.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTicketRef()));
        colExtFromDirection.setCellValueFactory(new PropertyValueFactory<>("fromDirection"));
        colExtToDirection.setCellValueFactory(new PropertyValueFactory<>("toDirection"));
        colExtSousDirection.setCellValueFactory(new PropertyValueFactory<>("toSousDirection"));
        colExtStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colExtStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    return;
                }
                setText(I18n.status(status));
            }
        });
        colExtAssignedTo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getAssignedToName() == null ? "-" : c.getValue().getAssignedToName()
        ));
        colExtAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnAction = new Button(I18n.t("assign", "Assign"));
            {
                btnAction.setStyle(AppUiStyles.Gov.BTN_TABLE_ACTION + "-fx-background-radius:4;");
                btnAction.setOnAction(event -> {
                    ExternalEscalation row = getTableView().getItems().get(getIndex());
                    if ("PENDING_APPROVAL".equalsIgnoreCase(row.getStatus())) {
                        approveExternalEscalation(row);
                    } else {
                        assignExternalEscalation(row);
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                ExternalEscalation row = (ExternalEscalation) getTableRow().getItem();
                String status = row.getStatus() == null ? "" : row.getStatus();
                if ("PENDING_APPROVAL".equalsIgnoreCase(status)) {
                    btnAction.setText(I18n.t("button.approve", "Approve"));
                    Integer requestedApprover = row.getRequestedApproverId();
                    boolean canApprove = "ADMIN".equalsIgnoreCase(Session.getRole())
                            || (requestedApprover != null && requestedApprover == Session.getUserId());
                    btnAction.setDisable(!canApprove);
                    setGraphic(btnAction);
                    return;
                }

                btnAction.setText(I18n.t("assign", "Assign"));
                boolean canAssign = TicketDAO.canAssignExternalEscalations()
                        && ("PENDING".equalsIgnoreCase(status) || "ESCALATED".equalsIgnoreCase(status));
                btnAction.setDisable(!canAssign);
                setGraphic(btnAction);
            }
        });
    }

    private void loadExternalEscalations() {
        if (tableExternalEscalations == null || externalEscalationsSection == null) return;
        boolean visible = TicketDAO.canViewExternalEscalations();
        externalEscalationsSection.setVisible(visible);
        externalEscalationsSection.setManaged(visible);
        if (!visible) {
            tableExternalEscalations.setItems(FXCollections.observableArrayList());
            return;
        }
        tableExternalEscalations.setItems(TicketDAO.getExternalEscalationsForCurrentUser());
    }

    private void assignExternalEscalation(ExternalEscalation escalation) {
        if (escalation == null) return;
        try {
            ObservableList<User> users = UserDAO.getActiveUsersByDirectionAndSousDirection(
                    escalation.getToDirectionId(),
                    escalation.getToSousDirectionId()
            );
            if (users.isEmpty()) {
                showError(I18n.t("escalation.noAssignableInDirection",
                        "No active users available in target direction."));
                return;
            }
            ChoiceDialog<User> dialog = new ChoiceDialog<>(users.get(0), users);
            dialog.setTitle(I18n.t("assign", "Assign"));
            dialog.setHeaderText(I18n.t("escalation.selectTargetAssignee", "Select user to handle this external escalation"));
            dialog.showAndWait().ifPresent(user -> {
                try {
                    TicketDAO.assignExternalEscalation(escalation.getId(), user.getId());
                    reloadAssignments();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void approveExternalEscalation(ExternalEscalation escalation) {
        if (escalation == null) return;
        try {
            TicketDAO.approveExternalEscalationRequest(escalation.getId());
            reloadAssignments();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }
    
    
    
    private void showEscalationDialog(Ticket ticket) {

    Dialog<User> dialog = new Dialog<>();
    dialog.setTitle(I18n.t("escalateTicket", "Escalate Ticket"));
    dialog.setHeaderText(I18n.t("selectAgentEscalateTicket", "Select agent to escalate ticket #") + ticket.getId());

    ButtonType escalateButtonType = new ButtonType(I18n.t("button.escalate", "Escalate"), ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(escalateButtonType, ButtonType.CANCEL);

    ComboBox<User> agentCombo = new ComboBox<>();
    agentCombo.setItems(UserDAO.findAllAgents());
    agentCombo.setPromptText(I18n.t("selectAgent", "Select agent"));

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
        alert.setContentText(I18n.t("ticketEscalatedTo", "Ticket escalated to ") + selectedAgent.getUsername());
        alert.showAndWait();

        reloadAssignments();
    });
}
    
    

    private void loadUnassignedTickets() {

    String role = com.app.auth.Session.getRole();

    if (RoleFlowUtil.canSeeUnassignedTicketsForAssignment(role)) {
        tableTickets.setItems(TicketDAO.getUnassignedTickets());
    } else {
        tableTickets.setItems(FXCollections.observableArrayList());
    }
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

        TableColumn<Ticket, Void> colView = new TableColumn<>(I18n.t("button.view", "View"));

        colView.setCellFactory(param -> new TableCell<>() {

            private final Button btn = new Button("👁 " + I18n.t("button.view", "View"));

            {
               btn.setStyle(
                       "-fx-background-color:#1e4d7a;"
                               + "-fx-text-fill:white;"
                               + "-fx-font-weight:normal;"
                               + "-fx-background-radius:4;"
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
            stage.setTitle(I18n.t("ticketDetails", "Ticket Details") + " - TCK-" + ticket.getId());
            Scene tdScene = new Scene(root);
            AppUiStyles.applyToScene(tdScene);
            stage.setScene(tdScene);

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

        ticketPieChart.setData(FXCollections.observableArrayList(
                new PieChart.Data(I18n.t("dashboardChart.open", "Open"), open),
                new PieChart.Data(I18n.t("dashboardChart.closed", "Closed"), closed)
        ));
    }
private void loadAgents() {

    String currentRole = Session.getRole();

    // 🔥 get allowed roles
    Set<String> allowedRoles =
            RoleFlowUtil.getAllowedTargetRoles(currentRole);

    // convert to list
    List<String> rolesList = new ArrayList<>(allowedRoles);

    // 🔥 get filtered users
    masterAgents = UserDAO.getUsersByRoles(rolesList);
    filteredAgents = new FilteredList<>(masterAgents, u -> true);
    listAgents.setItems(filteredAgents);

    if (txtAgentSearch != null) {
        txtAgentSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredAgents.setPredicate(u -> {
                if (u == null) return false;
                if (q.isEmpty()) return true;
                String username = u.getUsername() == null ? "" : u.getUsername().toLowerCase();
                String role = u.getRole() == null ? "" : u.getRole().toLowerCase();
                return username.contains(q) || role.contains(q);
            });
        });
    }

    listAgents.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
}
    

    @FXML
private void handleAssign() {

    Ticket selectedTicket = tableTickets.getSelectionModel().getSelectedItem();

    ObservableList<User> selectedAgents =
            listAgents.getSelectionModel().getSelectedItems();

    if (selectedTicket == null || selectedAgents.isEmpty()) {
        showError(I18n.t("err.selectTicketAndAgent", "Select ticket and at least one agent."));
        return;
    }

    try {

        // ===============================
        // ✅ SINGLE ASSIGNMENT
        // ===============================
        if (selectedAgents.size() == 1) {

            int userId = selectedAgents.get(0).getId();

            TicketDAO.assignTicket(selectedTicket.getId(), userId);

            reloadAssignments();
            return;
        }

        // ===============================
        // 🔥 MULTI ASSIGNMENT → POPUP
        // ===============================
        openTaskAssignmentPopup(selectedTicket, selectedAgents);

    } catch (BusinessRuleException e) {
        showError(e.getMessage());
    } catch (Exception e) {
        e.printStackTrace();
        showError(I18n.t("err.assignmentFailed", "Assignment failed: ") + e.getMessage());
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
                FXCollections.observableArrayList(agents),
                agents
        );
        

        Stage stage = new Stage();
        stage.setTitle(I18n.t("assignTasks", "Assign Tasks"));
        Scene taskScene = new Scene(root);
        AppUiStyles.applyToScene(taskScene);
        stage.setScene(taskScene);
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

    // ADMIN is unrestricted across the system.
    if ("ADMIN".equalsIgnoreCase(role)) {
        return true;
    }

    // DIRECTEUR / SOUS-DIRECTEUR must be restricted to their department.
    if ("DIRECTEUR".equalsIgnoreCase(role) || "SOUS-DIRECTEUR".equalsIgnoreCase(role)) {
        return TicketDAO.isTicketInCurrentUserDirection(ticketId);
    }

    return TicketDAO.isUserCurrentOwner(ticketId, userId);
}
   
    
    private void loadAssignments() {

    ObservableList<Ticket> list = TicketDAO.getAssignedTickets();

    System.out.println("Assignments size = " + list.size()); // DEBUG

    tableAssignments.setItems(list);
}
    
    
}