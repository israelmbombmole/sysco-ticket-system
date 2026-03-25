package com.app.controller;

import com.app.dao.DataShareAuditDAO;
import com.app.model.DataShareAudit;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;

import java.util.ArrayList;
import java.util.List;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;





public class DataShareAuditController {

    @FXML private TableView<DataShareAudit> tableAudit;

    @FXML private TableColumn<DataShareAudit,String> colFile;
    @FXML private TableColumn<DataShareAudit,String> colSharedBy;
    @FXML private TableColumn<DataShareAudit,String> colRecipient;
    @FXML private TableColumn<DataShareAudit,String> colAction;
    @FXML private TableColumn<DataShareAudit,String> colDate;
    @FXML private TableColumn<DataShareAudit,String> colTime;

    @FXML private TextField txtSearch;

    


    
    private List<DataShareAudit> auditList = new ArrayList<>();


  @FXML
public void initialize() {

    tableAudit.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    colFile.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getFileName()));

    colSharedBy.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getSharedBy()));

    colRecipient.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getRecipient()));

    colAction.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getAction()));

    colDate.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getDate()));

    colTime.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getTime()));

    // LOAD DATA
    loadAudit();

    // ======================================
    // LIVE SEARCH
    // ======================================

    ObservableList<DataShareAudit> masterData =
            FXCollections.observableArrayList(auditList);

    FilteredList<DataShareAudit> filteredData =
            new FilteredList<>(masterData, p -> true);

    txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {

        filteredData.setPredicate(audit -> {

            if (newVal == null || newVal.isEmpty()) {
                return true;
            }

            String search = newVal.toLowerCase();

            return audit.getFileName().toLowerCase().contains(search)
                    || audit.getSharedBy().toLowerCase().contains(search)
                    || audit.getRecipient().toLowerCase().contains(search)
                    || audit.getAction().toLowerCase().contains(search);
        });
    });

    SortedList<DataShareAudit> sortedData =
            new SortedList<>(filteredData);

    sortedData.comparatorProperty().bind(tableAudit.comparatorProperty());

    tableAudit.setItems(sortedData);
}

    // ============================
    // LOAD AUDIT DATA
    // ============================
    @FXML
   

private void loadAudit() {

    auditList = DataShareAuditDAO.getAll();
}
    
    
    

}