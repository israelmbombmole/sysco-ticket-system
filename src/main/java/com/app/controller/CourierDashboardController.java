package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierTrackingDAO;
import com.app.model.Ticket;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import com.app.util.TicketUtil;

public class CourierDashboardController {

    @FXML private Label lblTotalCreated;
    @FXML private Label lblOpenCount;
    @FXML private Label lblAssignedCount;
    @FXML private Label lblInProgressCount;
    @FXML private Label lblClosedCount;

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TableView<Ticket> tableCourierTickets;
    @FXML private TableColumn<Ticket, String> colTicketRef;
    @FXML private TableColumn<Ticket, String> colTitle;
    @FXML private TableColumn<Ticket, String> colDirection;
    @FXML private TableColumn<Ticket, String> colAssignedAgent;
    @FXML private TableColumn<Ticket, String> colAssignedRole;
    @FXML private TableColumn<Ticket, String> colStatus;
    @FXML private TableColumn<Ticket, String> colUpdatedAt;

    private final ObservableList<Ticket> masterTickets = FXCollections.observableArrayList();
    private FilteredList<Ticket> filteredTickets;

    @FXML
    public void initialize() {
        colTicketRef.setCellValueFactory(cell ->
                new SimpleStringProperty(TicketUtil.formatTicketRef(cell.getValue().getId())));
        colTitle.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getTitle()));
        colDirection.setCellValueFactory(cell -> new SimpleStringProperty(defaultValue(cell.getValue().getDepartmentName())));
        colAssignedAgent.setCellValueFactory(cell -> new SimpleStringProperty(defaultValue(cell.getValue().getAssignedToName())));
        colAssignedRole.setCellValueFactory(cell -> new SimpleStringProperty(defaultValue(cell.getValue().getCurrentOwnerRole())));
        colStatus.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
        colUpdatedAt.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCreatedAt()));

        cmbStatus.setItems(FXCollections.observableArrayList(
                "ALL", "OPEN", "ASSIGNED", "IN_PROGRESS", "ESCALATED", "MERGED", "CLOSED"
        ));
        cmbStatus.getSelectionModel().selectFirst();

        loadData();
        setupFilters();
    }

    private void loadData() {
        int courierId = Session.getUserId();

        masterTickets.setAll(CourierTrackingDAO.getTicketsCreatedByCourier(courierId));
        filteredTickets = new FilteredList<>(masterTickets, t -> true);

        SortedList<Ticket> sorted = new SortedList<>(filteredTickets);
        sorted.comparatorProperty().bind(tableCourierTickets.comparatorProperty());
        tableCourierTickets.setItems(sorted);

        lblTotalCreated.setText(String.valueOf(masterTickets.size()));
        lblOpenCount.setText(String.valueOf(CourierTrackingDAO.countByStatus(courierId, "OPEN")));
        lblAssignedCount.setText(String.valueOf(CourierTrackingDAO.countByStatus(courierId, "ASSIGNED")));
        lblInProgressCount.setText(String.valueOf(CourierTrackingDAO.countByStatus(courierId, "IN_PROGRESS")));
        lblClosedCount.setText(String.valueOf(CourierTrackingDAO.countByStatus(courierId, "CLOSED")));
    }

    private void setupFilters() {
        txtSearch.textProperty().addListener((obs, o, n) -> applyFilter());
        cmbStatus.setOnAction(e -> applyFilter());
    }

    private void applyFilter() {
        String keyword = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        String status = cmbStatus.getValue();

        filteredTickets.setPredicate(t -> {
            boolean statusOk = "ALL".equalsIgnoreCase(status)
                    || (t.getStatus() != null && t.getStatus().equalsIgnoreCase(status));

            if (!statusOk) return false;
            if (keyword.isEmpty()) return true;

            String ref = TicketUtil.formatTicketRef(t.getId()).toLowerCase();
            String title = t.getTitle() == null ? "" : t.getTitle().toLowerCase();
            String direction = t.getDepartmentName() == null ? "" : t.getDepartmentName().toLowerCase();
            String assignee = t.getAssignedToName() == null ? "" : t.getAssignedToName().toLowerCase();
            String role = t.getCurrentOwnerRole() == null ? "" : t.getCurrentOwnerRole().toLowerCase();

            return ref.contains(keyword)
                    || title.contains(keyword)
                    || direction.contains(keyword)
                    || assignee.contains(keyword)
                    || role.contains(keyword);
        });
    }

    @FXML
    private void handleRefresh() {
        loadData();
        applyFilter();
    }

    private String defaultValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
