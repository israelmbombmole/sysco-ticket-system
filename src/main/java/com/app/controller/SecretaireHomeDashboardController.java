package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierPacketDAO;
import com.app.dao.UserDAO;
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

public class SecretaireHomeDashboardController {

    @FXML private Label lblAwaitSous;
    @FXML private Label lblOpen;
    @FXML private Label lblTotal;
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
        Integer myDir = null;
        Integer mySous = null;
        Integer[] ds = UserDAO.getDirectionSousForUser(Session.getUserId());
        if (ds != null) {
            myDir = ds[0];
            mySous = ds[1];
        }
        if (myDir == null) {
            lblAwaitSous.setText(String.valueOf(CourierPacketDAO.countAwaitingSousCompany()));
            CourierPacketDAO.HomeCourierStats c0 = CourierPacketDAO.statsCompany();
            lblTotal.setText(String.valueOf(c0.total()));
            lblResolved.setText(String.valueOf(c0.resolved()));
            lblOpen.setText(String.valueOf(c0.notResolved()));
            List<CourierPacket> need0 = CourierPacketDAO.listForScope("SECRETAIRE", null, mySous, null, "OPEN")
                    .stream()
                    .filter(p -> p.getTargetDirectionId() != null
                            && p.getTargetSousDirectionId() == null
                            && !CourierPacket.ST_RESOLVED.equals(p.getStatus()))
                    .limit(15)
                    .collect(Collectors.toList());
            table.getItems().setAll(need0);
            return;
        }
        lblAwaitSous.setText(String.valueOf(CourierPacketDAO.countAwaitingSousForDirection(myDir)));
        CourierPacketDAO.HomeCourierStats c = CourierPacketDAO.statsForDirection(myDir);
        lblTotal.setText(String.valueOf(c.total()));
        lblResolved.setText(String.valueOf(c.resolved()));
        lblOpen.setText(String.valueOf(c.notResolved()));
        List<CourierPacket> need = CourierPacketDAO.listForScope("SECRETAIRE", myDir, mySous, null, "OPEN")
                .stream()
                .filter(p -> p.getTargetDirectionId() != null
                        && p.getTargetSousDirectionId() == null
                        && !CourierPacket.ST_RESOLVED.equals(p.getStatus()))
                .limit(15)
                .collect(Collectors.toList());
        table.getItems().setAll(need);
    }
}
