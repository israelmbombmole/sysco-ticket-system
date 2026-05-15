package com.app.controller;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.util.AppUiStyles;
import com.app.util.I18n;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class UserDashboardController {
@FXML private Label lblTotalTickets;
@FXML private Label lblOpenTickets;
@FXML private Label lblInProgressTickets;
@FXML private Label lblClosedTickets;
@FXML private PieChart adminPieChart;
@FXML private Button btnChat;

@FXML
public void initialize() {
    loadStats();
}

private void loadStats() {

    int userId = Session.getUserId();

    int total;
    int open;
    int inProgress;
    int closed;

    // Only system ADMIN (role in DB) sees org-wide totals; directors and others are direction-scoped.
    if (TicketDAO.hasOrgWideTicketAccess()) {
        total = TicketDAO.countAllTickets();
        open = TicketDAO.countByStatus("OPEN");
        inProgress = TicketDAO.countByStatus("IN_PROGRESS");
        closed = TicketDAO.countClosedTickets();
    } else {
        // Other roles get personalized dashboard metrics for their own workload.
        total = TicketDAO.countTicketsByUserAssignments(userId);
        open = TicketDAO.countAssignedTicketsByStatus(userId, "ASSIGNED")
                + TicketDAO.countAssignedTicketsByStatus(userId, "ESCALATED");
        inProgress = TicketDAO.countAssignedTicketsByStatus(userId, "IN_PROGRESS");
        closed = TicketDAO.countAssignedTicketsByStatus(userId, "CLOSED");
    }

    lblTotalTickets.setText(String.valueOf(total));
    lblOpenTickets.setText(String.valueOf(open));
    if (lblInProgressTickets != null) {
        lblInProgressTickets.setText(String.valueOf(inProgress));
    }
    lblClosedTickets.setText(String.valueOf(closed));
   
    adminPieChart.getData().clear();

    adminPieChart.getData().addAll(
            new PieChart.Data(I18n.t("dashboardChart.open", "Open"), open),
            new PieChart.Data(I18n.t("dashboardChart.inProgress", "In Progress"), inProgress),
            new PieChart.Data(I18n.t("dashboardChart.closed", "Closed"), closed)
    );
}



@FXML
private void openChat() {

    try {

        FXMLLoader loader =
                new FXMLLoader(getClass().getResource("/view/ChatPopup.fxml"));

        Scene scene = new Scene(loader.load());
        AppUiStyles.applyToScene(scene);

        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle(I18n.t("chat", "Chat"));
        stage.setWidth(450);
        stage.setHeight(600);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


}
