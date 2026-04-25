package com.app.controller;

import com.app.auth.Session;
import com.app.dao.MessageDAO;
import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.Ticket;
import com.app.util.LanguageManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.ResourceBundle;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class AdminDashboardController {

    // ================= GLOBAL STATS =================
    @FXML private Label lblTotalTickets;
    @FXML private Label lblOpenTickets;
    @FXML private Label lblAssignedTickets;
    @FXML private Label lblClosedTickets;
    @FXML private Label lblTotalAgents;
    

    // ================= SLA / ANALYTICS =================
    @FXML private Label lblSlaCompliance;
    @FXML private Label lblSlaBreaches;
    @FXML private Label lblEscalations;
    @FXML private Label lblAvgResolution;
    @FXML private Label lblTopAgent;

    @FXML private PieChart ticketChart;
    @FXML private PieChart slaChart;
    @FXML private BarChart<String, Number> agentPerformanceChart;

    // ================= RECENT TABLE =================
    @FXML private TableView<Ticket> recentTicketsTable;
    @FXML private TableColumn<Ticket, Integer> colId;
    @FXML private TableColumn<Ticket, String> colTitle;
    @FXML private TableColumn<Ticket, String> colStatus;
    @FXML private TableColumn<Ticket, String> colCreated;
    //@FXML private TableView<Ticket> tableTickets;

    @FXML
public void initialize() {

    if (recentTicketsTable != null) {
        recentTicketsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));

        colStatus.setCellValueFactory(cell -> {
            String status = cell.getValue().getStatus();
            ResourceBundle bundle = LanguageManager.getBundle();
            try {
                return new SimpleStringProperty(bundle.getString("status." + status));
            } catch (Exception e) {
                return new SimpleStringProperty(status);
            }
        });

        colCreated.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    }

    // 🔥 ADD THESE LINES (CRITICAL)
    loadDashboardData();
    loadAnalytics();
    loadMyTickets();
}

    // ================= DASHBOARD DATA =================

   private void loadDashboardData() {

    int total = TicketDAO.countAll();
    int open = TicketDAO.countByStatus("OPEN");

    // 🔥 FIXED HERE
    int assigned = TicketDAO.countByStatus("ASSIGNED");

    int closed = TicketDAO.countByStatus("CLOSED");
    int agents = UserDAO.countAgents();

    lblTotalTickets.setText(String.valueOf(total));
    lblOpenTickets.setText(String.valueOf(open));
    lblAssignedTickets.setText(String.valueOf(assigned));
    lblClosedTickets.setText(String.valueOf(closed));
    lblTotalAgents.setText(String.valueOf(agents));

    // Ticket Distribution Chart
    ObservableList<PieChart.Data> pieData =
            FXCollections.observableArrayList(
                    new PieChart.Data("Open", open),
                    new PieChart.Data("In Progress", assigned), // 🔥 label fixed
                    new PieChart.Data("Closed", closed)
            );

    ticketChart.setData(pieData);

    // Recent Tickets
    recentTicketsTable.setItems(
            FXCollections.observableArrayList(TicketDAO.findLast5())
    );
}

    // ================= ANALYTICS =================

    private void loadAnalytics() {

    int total = TicketDAO.countAll();
    int breaches = TicketDAO.countSlaBreaches();
    int escalations = TicketDAO.countEscalations();
    double avgRes = TicketDAO.averageResolution();

    // Calculate compliance safely
    double compliance = total == 0
            ? 0
            : ((double) (total - breaches) / total) * 100;

    lblSlaCompliance.setText(String.format("%.2f", compliance) + "%");
    lblSlaBreaches.setText(String.valueOf(breaches));
    lblEscalations.setText(String.valueOf(escalations));
    lblAvgResolution.setText(String.valueOf((int) avgRes));

    // Top agent from performance map
    var performance = TicketDAO.getUserPerformance();
    String topAgent = performance.isEmpty()
            ? "N/A"
            : performance.entrySet()
                         .stream()
                         .max(java.util.Map.Entry.comparingByValue())
                         .get()
                         .getKey();

    lblTopAgent.setText(topAgent);

    // SLA Chart
    ObservableList<PieChart.Data> slaData =
            FXCollections.observableArrayList(
                    new PieChart.Data("Compliant", total - breaches),
                    new PieChart.Data("Breached", breaches)
            );

    slaChart.setData(slaData);

    // Agent Performance Chart
    loadAgentPerformance();
}

    private void loadAgentPerformance() {

    XYChart.Series<String, Number> series = new XYChart.Series<>();
    series.setName("Closed Tickets");

    var performanceData = TicketDAO.getUserPerformance();

    for (var entry : performanceData.entrySet()) {
        series.getData().add(
                new XYChart.Data<>(entry.getKey(), entry.getValue())
        );
    }

    agentPerformanceChart.getData().clear();
    agentPerformanceChart.getData().add(series);
}

    
    public static class AgentRow {

    private final SimpleStringProperty agent;
    private final SimpleIntegerProperty total;

    public AgentRow(String agent, int total) {
        this.agent = new SimpleStringProperty(agent);
        this.total = new SimpleIntegerProperty(total);
    }

    public String getAgent() {
        return agent.get();
    }

    public int getTotal() {
        return total.get();
    }
}
    
    
    // ================= MESSAGES =================

    private void checkNewMessages() {

        int userId = Session.getUserId();
        List<Integer> senders =
                MessageDAO.getUsersWithUnreadMessages(userId);

        if (!senders.isEmpty()) {

            StringBuilder message = new StringBuilder();

            for (Integer senderId : senders) {
                String username =
                        UserDAO.findById(senderId).getUsername();

                message.append("• New message from ")
                        .append(username)
                        .append("\n");
            }

            java.util.ResourceBundle b = com.app.util.LanguageManager.getBundle();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(b.getString("adminNewMessages"));
            alert.setHeaderText(b.getString("adminUnreadMessages"));
            alert.setContentText(message.toString());
            alert.show();
        }
    }
    
    @FXML
private void openTicketManagement() {

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TicketManagement.fxml"),
                com.app.util.LanguageManager.getBundle());

        Parent root = loader.load();

        Stage stage = new Stage();
        stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageTicketManagement"));
        stage.setScene(new Scene(root));
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }

}


private void loadMyTickets() {

    int userId = Session.getUserId();

    ObservableList<Ticket> myTickets =
            TicketDAO.getWorkQueue(userId);

    if (recentTicketsTable != null) {
        recentTicketsTable.setItems(myTickets);
    }
}




}