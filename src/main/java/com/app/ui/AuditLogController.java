package com.app.ui;

import com.app.dao.AuditLogDAO;
import com.app.model.AuditLog;
import com.app.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AuditLogController {

    @FXML private TableView<AuditLog> tableAudit;
    @FXML private TableColumn<AuditLog, String> colUser;
    @FXML private TableColumn<AuditLog, String> colAction;
    @FXML private TableColumn<AuditLog, String> colEntity;
    
    @FXML private TableColumn<AuditLog, String> colDetails;
    @FXML private TableColumn<AuditLog, String> colTime;

    @FXML private ComboBox<String> cmbUser;
   
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TextField txtKeyword;

    private ObservableList<AuditLog> masterData;

    @FXML
    public void initialize() {
tableAudit.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        colUser.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().getUsername()));

        colAction.setCellValueFactory(d ->
                new SimpleStringProperty(I18n.t("audit.action." + d.getValue().getAction(), d.getValue().getAction())));

        colEntity.setCellValueFactory(d ->
                new SimpleStringProperty(I18n.t("audit.entity." + d.getValue().getEntity(), d.getValue().getEntity())));

       

        colDetails.setCellValueFactory(d ->
                new SimpleStringProperty(translateAuditDetails(d.getValue().getDetails())));

        colTime.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getCreatedAt() == null ?
                                "" : d.getValue().getCreatedAt().format(formatter)));

        loadData();
        loadFilters();
    }

    private void loadData() {
        masterData = AuditLogDAO.findAll();
        tableAudit.setItems(masterData);
    }

    private void loadFilters() {

    cmbUser.getItems().clear();

    // Add default option
    cmbUser.getItems().add(I18n.t("all", "All"));

    // Load distinct users from DB
    cmbUser.getItems().addAll(AuditLogDAO.getDistinctUsers());

    // Select "All" by default
    cmbUser.getSelectionModel().selectFirst();
}

    @FXML
private void handleFilter() {

    String user = cmbUser.getValue();
    LocalDate from = dpFrom.getValue();
    LocalDate to = dpTo.getValue();
    String keyword = txtKeyword.getText();

    if (I18n.t("all", "All").equals(user)) {
        user = null;
    }

    tableAudit.setItems(
            AuditLogDAO.advancedSearch(
                    user,
                    from,
                    to,
                    keyword
            )
    );
}

    @FXML
    private void handleReset() {
        dpFrom.setValue(null);
        dpTo.setValue(null);
        txtKeyword.clear();
        cmbUser.getSelectionModel().selectFirst();
        //cmbAction.getSelectionModel().selectFirst();
       // cmbEntity.getSelectionModel().selectFirst();
        loadData();
    }

    @FXML
    private void handleRefresh() {
        loadData();
    }

    @FXML
    private void handleExport() {

        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV", "*.csv")
        );

        File file = fc.showSaveDialog(tableAudit.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(file)) {

            pw.println("User,Action,Entity,ID,Details,Date");

            for (AuditLog log : tableAudit.getItems()) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        log.getUsername(),
                        log.getAction(),
                        log.getEntity(),
                        log.getEntityId(),
                        translateAuditDetails(log.getDetails()).replace("\"","'"),
                        log.getCreatedAt());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleClose() {
        ((Stage) tableAudit.getScene().getWindow()).close();
    }

    private String translateAuditDetails(String details) {
        if (details == null || details.isBlank()) {
            return "";
        }
        if ("User logged in".equalsIgnoreCase(details.trim())) {
            return I18n.t("audit.userLoggedIn", "User logged in");
        }
        return details;
    }
}