package com.app.ui;

import com.app.dao.UserActionDAO;
import com.app.dao.TicketDAO;
import com.app.model.DataEntry;
import com.app.session.LoggedUser;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DataManagementController {

    @FXML private TableView<DataEntry> tableData;

    @FXML private TableColumn<DataEntry, String> colDateEnregistrement;
    @FXML private TableColumn<DataEntry, String> colExpediteur;
    @FXML private TableColumn<DataEntry, String> colObjet;
    @FXML private TableColumn<DataEntry, String> colCotation;
    @FXML private TableColumn<DataEntry, String> colDateCotation;
    @FXML private TableColumn<DataEntry, String> colSousDirection;

    @FXML private TextField txtCotation;
    @FXML private TextField txtExpediteur;
    @FXML private DatePicker datePicker;

    // ===============================
    // INITIALIZE
    // ===============================
    @FXML
    public void initialize() {
tableData.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colDateEnregistrement.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDateEnregistrement()));

        colExpediteur.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getExpediteur()));

        colObjet.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getObjet()));

        colCotation.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getCotation()));

        colDateCotation.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDateCotation()));

        colSousDirection.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getSousDirection()));

        loadAll();
    }

    // ===============================
    // LOAD ALL FROM FINALIZED TICKETS
    // ===============================
    private void loadAll() {
        tableData.setItems(TicketDAO.getFinalizedDataEntries());
    }

    // ===============================
    // SEARCH
    // ===============================
    @FXML
    private void handleSearch() {

        ObservableList<DataEntry> allData = TicketDAO.getFinalizedDataEntries();
        ObservableList<DataEntry> filtered = FXCollections.observableArrayList();

        String cotation = txtCotation.getText() == null ? "" : txtCotation.getText().toLowerCase();
        String expediteur = txtExpediteur.getText() == null ? "" : txtExpediteur.getText().toLowerCase();
        String date = datePicker.getValue() == null ? "" : datePicker.getValue().toString();

        for (DataEntry entry : allData) {

            boolean match = true;

            if (!cotation.isBlank()) {
                match &= entry.getCotation().toLowerCase().contains(cotation);
            }

            if (!expediteur.isBlank()) {
                match &= entry.getExpediteur().toLowerCase().contains(expediteur);
            }

            if (!date.isBlank()) {
                match &= entry.getDateEnregistrement().equals(date);
            }

            if (match) {
                filtered.add(entry);
            }
        }

        tableData.setItems(filtered);
    }

    // ===============================
    // RESET
    // ===============================
    @FXML
    private void handleReset() {
        txtCotation.clear();
        txtExpediteur.clear();
        datePicker.setValue(null);
        loadAll();
    }

    // ===============================
    // DELETE (FROM TABLE ONLY FOR NOW)
    // ===============================
    @FXML
private void handleDelete() {

    DataEntry selected = tableData.getSelectionModel().getSelectedItem();
    if (selected == null) return;

    Alert confirm = new Alert(
            Alert.AlertType.CONFIRMATION,
            com.app.util.LanguageManager.getBundle().getString("dataMgmtDeleteConfirm"),
            ButtonType.YES, ButtonType.NO
    );

    UserActionDAO.log( LoggedUser.getUsername(), "Deleted: " + selected.getObjet() );


    
    confirm.showAndWait().ifPresent(btn -> {
        if (btn == ButtonType.YES) {

            TicketDAO.clearFinalizedDataEntry(selected);
            loadAll();
        }
    });
}


@FXML
private void handleExit() {

    try {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/login.fxml"),
                com.app.util.LanguageManager.getBundle()
        );

        Parent root = loader.load();

        Stage stage = (Stage) tableData.getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageLogin"));
        stage.centerOnScreen();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


    // ===============================
    // EDIT (TO IMPLEMENT NEXT)
    // ===============================
    @FXML
private void handleEdit() {

    DataEntry selected = tableData.getSelectionModel().getSelectedItem();
    if (selected == null) return;

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/edit_entry.fxml"),
                com.app.util.LanguageManager.getBundle()
        );

        Parent root = loader.load();

        EditEntryController controller = loader.getController();
        controller.setData(selected, this::loadAll);

        Stage stage = new Stage();
        stage.setTitle(com.app.util.LanguageManager.getBundle().getString("stageEditEntry"));
        stage.setScene(new Scene(root));
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

}
