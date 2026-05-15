package com.app.controller;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.dao.FileDAO;
import com.app.dao.MessageDAO;
import com.app.util.I18n;

import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ExternalDashboardController {

    @FXML
    private Label lblTickets;

    @FXML
    private Label lblFiles;

    @FXML
    private Label lblMessages;

    @FXML
    private PieChart ticketChart;


    @FXML
    public void initialize() {

        loadStats();
        loadChart();
    }


    private void loadStats() {

        int userId = Session.getUserId();

        int ticketCount = TicketDAO.countTicketsByUser(userId);
        int fileCount = FileDAO.countFilesForUser(userId);
        int messageCount = MessageDAO.countMessagesForUser(userId);

        lblTickets.setText(String.valueOf(ticketCount));
        lblFiles.setText(String.valueOf(fileCount));
        lblMessages.setText(String.valueOf(messageCount));
    }


    private void loadChart() {

        int userId = Session.getUserId();

        int open = TicketDAO.countTicketsByStatus(userId, "OPEN");
        int progress = TicketDAO.countTicketsByStatus(userId, "IN_PROGRESS");
        int closed = TicketDAO.countTicketsByStatus(userId, "CLOSED");

        ObservableList<PieChart.Data> data = FXCollections.observableArrayList(
                new PieChart.Data(I18n.t("dashboardChart.open", "Open"), open),
                new PieChart.Data(I18n.t("dashboardChart.inProgress", "In Progress"), progress),
                new PieChart.Data(I18n.t("dashboardChart.closed", "Closed"), closed)
        );

        ticketChart.setData(data);
    }
}