package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierPacketDAO;
import com.app.dao.MessageDAO;
import com.app.util.AccessContext;
import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.Ticket;
import com.app.util.AppUiStyles;
import com.app.util.DashboardDetailDialog;
import com.app.util.I18n;
import com.app.util.LanguageManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;

import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;


public class AdminDashboardController {

    // ================= GLOBAL STATS =================
    @FXML private Label lblTotalTickets;
    @FXML private Label lblOpenTickets;
    @FXML private Label lblAssignedTickets;
    @FXML private Label lblClosedTickets;
    @FXML private Label lblInspecteurCount;
    @FXML private Label lblControleurCount;
    @FXML private Label lblVerificateurCount;
    @FXML private Label lblAssistantCount;
    

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

    @FXML private Label lblCourierDTotal;
    @FXML private Label lblCourierDOpen;
    @FXML private Label lblCourierDAwaitSous;
    @FXML private Label lblCourierDResolved;

    @FXML private VBox cardDashTotal;
    @FXML private VBox cardDashOpen;
    @FXML private VBox cardDashAssigned;
    @FXML private VBox cardDashClosed;
    @FXML private VBox cardDashInsp;
    @FXML private VBox cardDashCtrl;
    @FXML private VBox cardDashVerif;
    @FXML private VBox cardDashAssist;
    @FXML private VBox cardDashSlaCompliance;
    @FXML private VBox cardDashSlaBreaches;
    @FXML private VBox cardDashEscalations;
    @FXML private VBox cardDashAvgResolution;
    @FXML private VBox cardDashTopAgent;
    @FXML private VBox cardCourierTotal;
    @FXML private VBox cardCourierOpen;
    @FXML private VBox cardCourierAwaitSous;
    @FXML private VBox cardCourierResolved;

    private volatile boolean dashboardCardHandlersInstalled;

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
    loadCourierBlock();
    attachAdminDashboardClicksWhenSceneReady();
}

    /**
     * {@code initialize()} runs during {@link FXMLLoader#load()} before this root is attached to the main window,
     * so {@link Label#getScene()} is still null — wiring must wait until the scene exists.
     */
    private void attachAdminDashboardClicksWhenSceneReady() {
        javafx.scene.Node anchor = lblTotalTickets != null ? lblTotalTickets : cardDashTotal;
        if (anchor == null) {
            return;
        }
        Runnable install = () -> {
            if (dashboardCardHandlersInstalled) {
                return;
            }
            if (dashboardWindow() == null) {
                return;
            }
            dashboardCardHandlersInstalled = true;
            wireDashboardCardClicks();
        };
        Platform.runLater(() -> {
            install.run();
            if (!dashboardCardHandlersInstalled) {
                anchor.sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        Platform.runLater(install);
                    }
                });
            }
        });
    }

    private Window dashboardWindow() {
        return lblTotalTickets != null && lblTotalTickets.getScene() != null
                ? lblTotalTickets.getScene().getWindow()
                : null;
    }

    private void wireCard(VBox card, Runnable action) {
        if (card == null || action == null) {
            return;
        }
        card.setCursor(Cursor.HAND);
        Tooltip.install(card, new Tooltip(I18n.t("dashboardCardClickHint", "Click to view details")));
        card.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> action.run());
    }

    private void wireDashboardCardClicks() {
        Window w = dashboardWindow();
        if (w == null) {
            return;
        }

        wireCard(cardDashTotal, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("TotalTickets"), TicketDAO.listDashboardTicketsAll()));
        wireCard(cardDashOpen, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("Open"), TicketDAO.listDashboardTicketsByStatus("OPEN")));
        wireCard(cardDashAssigned, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("Assigned"), TicketDAO.listDashboardTicketsByStatus("ASSIGNED")));
        wireCard(cardDashClosed, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("Closed"), TicketDAO.listDashboardTicketsByStatus("CLOSED")));

        wireCard(cardDashInsp, () -> DashboardDetailDialog.showUsers(w,
                I18n.t("inspecteurs"), UserDAO.listActiveUsersByRoleForDashboard("INSPECTEUR")));
        wireCard(cardDashCtrl, () -> DashboardDetailDialog.showUsers(w,
                I18n.t("controleurs"), UserDAO.listActiveUsersByRoleForDashboard("CONTROLEUR")));
        wireCard(cardDashVerif, () -> DashboardDetailDialog.showUsers(w,
                I18n.t("verificateurs"), UserDAO.listActiveUsersByRoleForDashboard("VERIFICATEUR")));
        wireCard(cardDashAssist, () -> DashboardDetailDialog.showUsers(w,
                I18n.t("assistants"), UserDAO.listActiveUsersByRoleForDashboard("VERIFICATEUR-ASSISTANT")));

        wireCard(cardDashSlaCompliance, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("slaCompliance"), TicketDAO.listDashboardClosedTicketsResolvedWithinSla()));
        wireCard(cardDashSlaBreaches, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("SLABreaches"), TicketDAO.listDashboardTicketsSlaBreaches()));
        wireCard(cardDashEscalations, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("escalations"), TicketDAO.listDashboardTicketsWithEscalations()));
        wireCard(cardDashAvgResolution, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("avgResolution"), TicketDAO.listDashboardClosedTicketsWithResolution()));
        wireCard(cardDashTopAgent, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("TopPerformingAgent"),
                TicketDAO.listDashboardClosedTicketsForAssigneeUsername(lblTopAgent.getText())));

        Integer[] dirs = UserDAO.getDirectionSousForUser(Session.getUserId());
        Integer dirId = AccessContext.isSystemSuperAdmin()
                ? null
                : (dirs != null && dirs.length > 0 ? dirs[0] : null);

        wireCard(cardCourierTotal, () -> {
            if (AccessContext.isSystemSuperAdmin()) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashInDirection"),
                        CourierPacketDAO.listPacketsCompanyDashboardBucket("total"));
            } else if (dirId != null && dirId > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashInDirection"),
                        CourierPacketDAO.listPacketsDirectionDashboardBucket(dirId, "total"));
            }
        });
        wireCard(cardCourierOpen, () -> {
            if (AccessContext.isSystemSuperAdmin()) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashOpenInDirection"),
                        CourierPacketDAO.listPacketsCompanyDashboardBucket("open"));
            } else if (dirId != null && dirId > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashOpenInDirection"),
                        CourierPacketDAO.listPacketsDirectionDashboardBucket(dirId, "open"));
            }
        });
        wireCard(cardCourierAwaitSous, () -> {
            if (AccessContext.isSystemSuperAdmin()) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashAwaitSousInDirection"),
                        CourierPacketDAO.listPacketsCompanyDashboardBucket("await_sous"));
            } else if (dirId != null && dirId > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashAwaitSousInDirection"),
                        CourierPacketDAO.listPacketsDirectionDashboardBucket(dirId, "await_sous"));
            }
        });
        wireCard(cardCourierResolved, () -> {
            if (AccessContext.isSystemSuperAdmin()) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("resolvedInDirection"),
                        CourierPacketDAO.listPacketsCompanyDashboardBucket("resolved"));
            } else if (dirId != null && dirId > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("resolvedInDirection"),
                        CourierPacketDAO.listPacketsDirectionDashboardBucket(dirId, "resolved"));
            }
        });

        applyDashboardTileFallbackStyles();
    }

    /** Inline styles so KPI tiles stay visible even when scene stylesheets/CSS gradients fail to apply (JavaFX 17). */
    private static final String DASHBOARD_TILE_FALLBACK_STYLE =
            "-fx-background-color:#ffffff;"
                    + "-fx-border-color:#64748b;"
                    + "-fx-border-width:2px;"
                    + "-fx-border-radius:8px;"
                    + "-fx-background-radius:8px;"
                    + "-fx-padding:14px 16px;"
                    + "-fx-min-height:104px;"
                    + "-fx-min-width:188px;"
                    + "-fx-effect:dropshadow(gaussian,rgba(15,23,42,0.14),14,0.22,0,4);";

    private void applyDashboardTileFallbackStyles() {
        VBox[] tiles = {
                cardDashTotal, cardDashOpen, cardDashAssigned, cardDashClosed,
                cardDashInsp, cardDashCtrl, cardDashVerif, cardDashAssist,
                cardDashSlaCompliance, cardDashSlaBreaches, cardDashEscalations,
                cardDashAvgResolution, cardDashTopAgent,
                cardCourierTotal, cardCourierOpen, cardCourierAwaitSous, cardCourierResolved
        };
        for (VBox v : tiles) {
            if (v != null) {
                v.setStyle(DASHBOARD_TILE_FALLBACK_STYLE);
            }
        }
    }

    private void loadCourierBlock() {
        if (lblCourierDTotal == null) {
            return;
        }
        CourierPacketDAO.HomeCourierStats st;
        if (AccessContext.isSystemSuperAdmin()) {
            st = CourierPacketDAO.statsCompany();
        } else {
            Integer[] ds = UserDAO.getDirectionSousForUser(Session.getUserId());
            Integer d = ds != null && ds.length > 0 ? ds[0] : null;
            st = d != null ? CourierPacketDAO.statsForDirection(d) : new CourierPacketDAO.HomeCourierStats(0, 0, 0, 0);
        }
        lblCourierDTotal.setText(String.valueOf(st.total()));
        lblCourierDOpen.setText(String.valueOf(st.notResolved()));
        lblCourierDAwaitSous.setText(String.valueOf(st.directedAwaitingSous()));
        lblCourierDResolved.setText(String.valueOf(st.resolved()));
    }

    // ================= DASHBOARD DATA =================

   private void loadDashboardData() {
    // Org-wide totals for role ADMIN (TicketDAO.hasOrgWideTicketAccess()); DIRECTEUR and others are scoped to their direction.

    int total = TicketDAO.countAll();
    int open = TicketDAO.countByStatus("OPEN");

    // 🔥 FIXED HERE
    int assigned = TicketDAO.countByStatus("ASSIGNED");

    int closed = TicketDAO.countByStatus("CLOSED");
    int inspecteurs = UserDAO.countActiveUsersByRoleForDashboard("INSPECTEUR");
    int controleurs = UserDAO.countActiveUsersByRoleForDashboard("CONTROLEUR");
    int verificateurs = UserDAO.countActiveUsersByRoleForDashboard("VERIFICATEUR");
    int assistants = UserDAO.countActiveUsersByRoleForDashboard("VERIFICATEUR-ASSISTANT");

    lblTotalTickets.setText(String.valueOf(total));
    lblOpenTickets.setText(String.valueOf(open));
    lblAssignedTickets.setText(String.valueOf(assigned));
    lblClosedTickets.setText(String.valueOf(closed));
    if (lblInspecteurCount != null) lblInspecteurCount.setText(String.valueOf(inspecteurs));
    if (lblControleurCount != null) lblControleurCount.setText(String.valueOf(controleurs));
    if (lblVerificateurCount != null) lblVerificateurCount.setText(String.valueOf(verificateurs));
    if (lblAssistantCount != null) lblAssistantCount.setText(String.valueOf(assistants));

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
                    new PieChart.Data(I18n.t("dashboardChart.compliant", "Compliant"), total - breaches),
                    new PieChart.Data(I18n.t("dashboardChart.breached", "Breached"), breaches)
            );

    slaChart.setData(slaData);

    // Agent Performance Chart
    loadAgentPerformance();
}

    private void loadAgentPerformance() {

    XYChart.Series<String, Number> series = new XYChart.Series<>();
    series.setName(I18n.t("dashboardChart.seriesClosedTickets", "Closed tickets"));

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

                message.append(MessageFormat.format(
                        I18n.t("notif.msg.bulletNewMessageFrom", "• New message from {0}\n"),
                        username));
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(I18n.t("notif.popup.newMessagesTitle", "New Messages"));
            alert.setHeaderText(I18n.t("notif.popup.unreadHeader", "You have unread messages"));
            alert.setContentText(message.toString());
            alert.show();
        }
    }
    
    @FXML
private void openTicketManagement() {

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TicketManagement.fxml"));

        Parent root = loader.load();

        Stage stage = new Stage();
        stage.setTitle(I18n.t("ticketManagement", "Ticket Management"));
        Scene tmScene = new Scene(root);
        AppUiStyles.applyToScene(tmScene);
        stage.setScene(tmScene);
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