package com.app.controller;

import com.app.dao.TicketDAO;
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
@FXML private Label lblClosedTickets;
@FXML private PieChart adminPieChart;
@FXML private Button btnChat;

@FXML
public void initialize() {
    loadStats();
}

private void loadStats() {

    int total = TicketDAO.countAllTickets();
    int open = TicketDAO.countByStatus("OPEN");
    int inProgress = TicketDAO.countByStatus("IN_PROGRESS");
    int closed = TicketDAO.countClosedTickets();

    lblTotalTickets.setText(String.valueOf(total));
    lblOpenTickets.setText(String.valueOf(open)); // treat in progress as open
    lblClosedTickets.setText(String.valueOf(closed));
   
    adminPieChart.getData().clear();

    adminPieChart.getData().addAll(
    new PieChart.Data("Open", open),
    new PieChart.Data("In Progress", inProgress),
    new PieChart.Data("Closed", closed)
);
}



@FXML
private void openChat() {

    try {

        FXMLLoader loader =
                new FXMLLoader(getClass().getResource("/view/ChatPopup.fxml"));

        Scene scene = new Scene(loader.load());

        Stage stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Chat");
        stage.setWidth(450);
        stage.setHeight(600);
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


}
