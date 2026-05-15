package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierPacketDAO;
import com.app.model.CourierPacket;
import com.app.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.stream.Collectors;

public class CourierHomeDashboardController {

    @FXML private Label lblTotal;
    @FXML private Label lblOpen;
    @FXML private Label lblPipeline;
    @FXML private Label lblResolved;
    @FXML private TableView<CourierPacket> table;
    @FXML private TableColumn<CourierPacket, String> colRef;
    @FXML private TableColumn<CourierPacket, String> colTitle;
    @FXML private TableColumn<CourierPacket, String> colSt;
    @FXML private TableColumn<CourierPacket, String> colDir;

    @FXML
    public void initialize() {
        colRef.setCellValueFactory(new PropertyValueFactory<>("refCode"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDir.setCellValueFactory(new PropertyValueFactory<>("targetDirectionName"));
        colSt.setCellValueFactory(c -> new SimpleStringProperty(translateSt(c.getValue().getStatus())));
        refresh();
    }

    private String translateSt(String s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case CourierPacket.ST_AWAITING_DIRECTION -> I18n.t("courierStAwaitingDir", "Not routed to a direction");
            case CourierPacket.ST_REGISTERED -> I18n.t("courierStRegistered", "Registered");
            case CourierPacket.ST_DIRECTED -> I18n.t("courierStDirected", "Directed");
            case CourierPacket.ST_SOUS_ASSIGNED -> I18n.t("courierStSous", "Sous-direction set");
            case CourierPacket.ST_IN_PROGRESS -> I18n.t("courierStProgress", "In progress");
            case CourierPacket.ST_RESOLVED -> I18n.t("courierStResolved", "Resolved");
            default -> s;
        };
    }

    private void refresh() {
        int uid = Session.getUserId();
        CourierPacketDAO.HomeCourierStats s = CourierPacketDAO.statsCreatedByUser(uid);
        lblTotal.setText(String.valueOf(s.total()));
        lblOpen.setText(String.valueOf(s.notResolved()));
        lblPipeline.setText(String.valueOf(s.directedAwaitingSous()));
        lblResolved.setText(String.valueOf(s.resolved()));
        List<CourierPacket> recent = CourierPacketDAO.listForScope("COURIER", null, null, null, null)
                .stream()
                .filter(p -> p.getCreatedBy() == uid)
                .limit(12)
                .collect(Collectors.toList());
        table.getItems().setAll(recent);
    }
}
