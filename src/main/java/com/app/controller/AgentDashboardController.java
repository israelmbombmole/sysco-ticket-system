package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierPacketDAO;
import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.Ticket;
import com.app.util.DashboardDetailDialog;
import com.app.util.I18n;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dashboard for SOUS-DIRECTEUR (agent dashboard). Assigned tasks are managed in Mon travail ({@link MyWorkController}).
 */
public class AgentDashboardController {

    @FXML
    private Label lblTotal;
    @FXML
    private Label lblOpen;
    @FXML
    private Label lblClosed;
    @FXML
    private PieChart pieChart;
    @FXML
    private Label lblInProgress;
    @FXML
    private Label lblMerged;
    @FXML
    private LineChart<String, Number> performanceChart;
    @FXML
    private Label lblSlaBreaches;
    @FXML
    private Label lblEscalations;
    @FXML
    private Label lblSlaCompliance;
    @FXML
    private Label lblCourierSTotal;
    @FXML
    private Label lblCourierSOpen;
    @FXML
    private Label lblCourierSPipe;
    @FXML
    private Label lblCourierSResolved;

    @FXML private VBox cardAgentTotal;
    @FXML private VBox cardAgentOpen;
    @FXML private VBox cardAgentInProgress;
    @FXML private VBox cardAgentMerged;
    @FXML private VBox cardAgentClosed;
    @FXML private VBox cardAgentSlaBreaches;
    @FXML private VBox cardAgentEscalations;
    @FXML private VBox cardAgentSlaCompliance;
    @FXML private VBox cardAgentCourierTotal;
    @FXML private VBox cardAgentCourierOpen;
    @FXML private VBox cardAgentCourierPipe;
    @FXML private VBox cardAgentCourierResolved;

    private volatile boolean dashboardCardHandlersInstalled;

    @FXML
    public void initialize() {

        int agentId = Session.getUserId();

        int total = TicketDAO.countTicketsByUserAssignments(agentId);

        int open =
                TicketDAO.countAssignedTicketsByStatus(agentId, "ASSIGNED")
                        + TicketDAO.countAssignedTicketsByStatus(agentId, "ESCALATED");

        int inProgress =
                TicketDAO.countAssignedTicketsByStatus(agentId, "IN_PROGRESS");

        int merged =
                TicketDAO.countAssignedTicketsByStatus(agentId, "MERGED");

        int closed =
                TicketDAO.countAssignedTicketsByStatus(agentId, "CLOSED");

        lblTotal.setText(String.valueOf(total));
        lblOpen.setText(String.valueOf(open));
        lblInProgress.setText(String.valueOf(inProgress));
        lblMerged.setText(String.valueOf(merged));
        lblClosed.setText(String.valueOf(closed));

        int breaches = TicketDAO.countAgentSlaBreaches(agentId);
        lblSlaBreaches.setText(String.valueOf(breaches));

        lblEscalations.setText(String.valueOf(TicketDAO.countEscalations()));
        lblSlaCompliance.setText("—");

        pieChart.getData().addAll(
                new PieChart.Data(I18n.t("dashboardChart.open", "Open"), open),
                new PieChart.Data(I18n.t("dashboardChart.inProgress", "In Progress"), inProgress),
                new PieChart.Data(I18n.t("dashboardChart.merged", "Merged"), merged),
                new PieChart.Data(I18n.t("dashboardChart.closed", "Closed"), closed)
        );

        performanceChart.setTitle(I18n.t("dashboardChart.ticketsClosedPerDay", "Tickets closed per day"));
        loadPerformanceChart();
        loadCourierBlock();
        attachAgentDashboardClicksWhenSceneReady(agentId);
    }

    private void attachAgentDashboardClicksWhenSceneReady(int agentId) {
        javafx.scene.Node anchor = lblTotal != null ? lblTotal : cardAgentTotal;
        if (anchor == null) {
            return;
        }
        Runnable install = () -> {
            if (dashboardCardHandlersInstalled) {
                return;
            }
            if (agentDashboardWindow() == null) {
                return;
            }
            dashboardCardHandlersInstalled = true;
            wireAgentDashboardCards(agentId);
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

    private Window agentDashboardWindow() {
        return lblTotal != null && lblTotal.getScene() != null ? lblTotal.getScene().getWindow() : null;
    }

    private Integer agentSousDirectionId() {
        Integer[] ds = UserDAO.getDirectionSousForUser(Session.getUserId());
        return ds != null && ds.length > 1 ? ds[1] : null;
    }

    private void wireCard(VBox card, Runnable action) {
        if (card == null || action == null) {
            return;
        }
        card.setCursor(Cursor.HAND);
        Tooltip.install(card, new Tooltip(I18n.t("dashboardCardClickHint", "Click to view details")));
        card.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> action.run());
    }

    private void wireAgentDashboardCards(int agentId) {
        Window w = agentDashboardWindow();
        if (w == null) {
            return;
        }

        wireCard(cardAgentTotal, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("total"), TicketDAO.listTicketsForUserAssignmentsDashboard(agentId)));

        wireCard(cardAgentOpen, () -> {
            List<Ticket> rows = new ArrayList<>(TicketDAO.listAssignedTicketsByStatusForDashboardAgent(agentId, "ASSIGNED"));
            rows.addAll(TicketDAO.listAssignedTicketsByStatusForDashboardAgent(agentId, "ESCALATED"));
            DashboardDetailDialog.showTickets(w, I18n.t("open"), rows);
        });

        wireCard(cardAgentInProgress, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("inProgress"),
                TicketDAO.listAssignedTicketsByStatusForDashboardAgent(agentId, "IN_PROGRESS")));

        wireCard(cardAgentMerged, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("MergeTicket"),
                TicketDAO.listAssignedTicketsByStatusForDashboardAgent(agentId, "MERGED")));

        wireCard(cardAgentClosed, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("closed"),
                TicketDAO.listAssignedTicketsByStatusForDashboardAgent(agentId, "CLOSED")));

        wireCard(cardAgentSlaBreaches, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("SLABreaches"), TicketDAO.listAgentSlaBreachesDashboard(agentId)));

        wireCard(cardAgentEscalations, () -> DashboardDetailDialog.showTickets(w,
                I18n.t("escalations"), TicketDAO.listDashboardTicketsWithEscalations()));

        wireCard(cardAgentSlaCompliance, () -> DashboardDetailDialog.showMessage(w,
                I18n.t("slaCompliance"),
                I18n.t("dashboardAgentSlaComplianceHint",
                        "SLA compliance rate for your scope is summarized on the administrator dashboard.")));

        Integer sous = agentSousDirectionId();
        wireCard(cardAgentCourierTotal, () -> {
            if (sous != null && sous > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashSousTotal"),
                        CourierPacketDAO.listPacketsSousDashboardBucket(sous, "total"));
            }
        });
        wireCard(cardAgentCourierOpen, () -> {
            if (sous != null && sous > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashSousOpen"),
                        CourierPacketDAO.listPacketsSousDashboardBucket(sous, "open"));
            }
        });
        wireCard(cardAgentCourierPipe, () -> {
            if (sous != null && sous > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("courierDashSousPipeline"),
                        CourierPacketDAO.listPacketsSousDashboardBucket(sous, "pipeline"));
            }
        });
        wireCard(cardAgentCourierResolved, () -> {
            if (sous != null && sous > 0) {
                DashboardDetailDialog.showCourierPackets(w,
                        I18n.t("resolved"),
                        CourierPacketDAO.listPacketsSousDashboardBucket(sous, "resolved"));
            }
        });

        applyAgentDashboardTileFallbackStyles();
    }

    private static final String AGENT_DASH_TILE_FALLBACK_STYLE =
            "-fx-background-color:#ffffff;"
                    + "-fx-border-color:#64748b;"
                    + "-fx-border-width:2px;"
                    + "-fx-border-radius:8px;"
                    + "-fx-background-radius:8px;"
                    + "-fx-padding:14px 16px;"
                    + "-fx-min-height:104px;"
                    + "-fx-min-width:188px;"
                    + "-fx-effect:dropshadow(gaussian,rgba(15,23,42,0.14),14,0.22,0,4);";

    private void applyAgentDashboardTileFallbackStyles() {
        VBox[] tiles = {
                cardAgentTotal, cardAgentOpen, cardAgentInProgress, cardAgentMerged, cardAgentClosed,
                cardAgentSlaBreaches, cardAgentEscalations, cardAgentSlaCompliance,
                cardAgentCourierTotal, cardAgentCourierOpen, cardAgentCourierPipe, cardAgentCourierResolved
        };
        for (VBox v : tiles) {
            if (v != null) {
                v.setStyle(AGENT_DASH_TILE_FALLBACK_STYLE);
            }
        }
    }

    private void loadPerformanceChart() {

        int agentId = Session.getUserId();

        Map<String, Integer> stats =
                TicketDAO.getAgentDailyPerformance(agentId);

        XYChart.Series<String, Number> series =
                new XYChart.Series<>();

        series.setName(I18n.t("dashboardChart.seriesClosedTickets", "Closed tickets"));

        for (String date : stats.keySet()) {
            series.getData().add(
                    new XYChart.Data<>(date, stats.get(date))
            );
        }

        performanceChart.getData().clear();
        performanceChart.getData().add(series);
    }

    private void loadCourierBlock() {
        if (lblCourierSTotal == null) {
            return;
        }
        Integer[] ds = UserDAO.getDirectionSousForUser(Session.getUserId());
        Integer sous = ds != null && ds.length > 1 ? ds[1] : null;
        if (sous == null || sous <= 0) {
            lblCourierSTotal.setText("0");
            lblCourierSOpen.setText("0");
            lblCourierSPipe.setText("0");
            lblCourierSResolved.setText("0");
            return;
        }
        CourierPacketDAO.HomeCourierStats st = CourierPacketDAO.statsForSous(sous);
        lblCourierSTotal.setText(String.valueOf(st.total()));
        lblCourierSOpen.setText(String.valueOf(st.notResolved()));
        lblCourierSPipe.setText(String.valueOf(st.directedAwaitingSous()));
        lblCourierSResolved.setText(String.valueOf(st.resolved()));
    }
}
