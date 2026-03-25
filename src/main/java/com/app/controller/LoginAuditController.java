package com.app.controller;

import com.app.dao.AuditLogDAO;
import com.app.model.AuditLog;
import com.app.model.LoginAudit;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.format.DateTimeFormatter;
import javafx.scene.control.cell.PropertyValueFactory;

public class LoginAuditController {

    @FXML private TableView<AuditLog> loginAuditTable;
    @FXML private TableColumn<LoginAudit, String> colUsername;
    @FXML private TableColumn<LoginAudit, String> colLoginTime;

    @FXML
    public void initialize() {

        loginAuditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    colUsername.prefWidthProperty().bind(loginAuditTable.widthProperty().multiply(0.4));
    colLoginTime.prefWidthProperty().bind(loginAuditTable.widthProperty().multiply(0.6));

    colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
    colLoginTime.setCellValueFactory(new PropertyValueFactory<>("loginTime"));

   
    
        colUsername.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getUsername())
        );

        colLoginTime.setCellValueFactory(data -> {

            if (data.getValue().getLoginTime() == null)
                return new SimpleStringProperty("");

            return new SimpleStringProperty(
                    data.getValue().getLoginTime()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
        });

        ObservableList<AuditLog>audits = AuditLogDAO.findAll();
        loginAuditTable.setItems(audits);
    }
}