package com.app.ui;

import com.app.dao.TicketDAO;
import com.app.dao.UserActionDAO;
import com.app.model.DataEntry;
import com.app.session.LoggedUser;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;

public class EditEntryController {

    @FXML private DatePicker dpDateEnreg;
    @FXML private TextField txtExpediteur;
    @FXML private TextArea txtObjet;
    @FXML private TextField txtCotation;
    @FXML private DatePicker dpDateCotation;
    @FXML private ComboBox<String> cbSousDirection;

    private DataEntry selectedEntry;
    private Runnable onSaveCallback;

    // ===============================
    // INITIALIZE
    // ===============================
    @FXML
    public void initialize() {
        cbSousDirection.getItems().addAll(
                "SDMA",
                "SDRM",
                "SDSYD"
        );
    }

    // ===============================
    // RECEIVE DATA FROM PARENT
    // ===============================
    public void setData(DataEntry entry, Runnable onSaveCallback) {

        this.selectedEntry = entry;
        this.onSaveCallback = onSaveCallback;

        dpDateEnreg.setValue(LocalDate.parse(entry.getDateEnregistrement()));
        txtExpediteur.setText(entry.getExpediteur());
        txtObjet.setText(entry.getObjet());
        txtCotation.setText(entry.getCotation());
        dpDateCotation.setValue(LocalDate.parse(entry.getDateCotation()));
        cbSousDirection.setValue(entry.getSousDirection());
    }

    // ===============================
    // EXIT BUTTON (CLOSE POPUP)
    // ===============================
    @FXML
    private void handleExit() {

        Stage stage = (Stage) txtCotation.getScene().getWindow();
        stage.close();
    }

    // ===============================
    // SAVE
    // ===============================
    @FXML
    private void handleSave() {

        TicketDAO.updateFinalizedDataEntry(
                selectedEntry,
                dpDateEnreg.getValue() == null ? null : dpDateEnreg.getValue().toString(),
                txtExpediteur.getText(),
                txtObjet.getText(),
                txtCotation.getText(),
                dpDateCotation.getValue() == null ? null : dpDateCotation.getValue().toString(),
                cbSousDirection.getValue()
        );

        if (onSaveCallback != null) {
            onSaveCallback.run();
        }

        UserActionDAO.log(
                LoggedUser.getUsername(),
                "Edited: " + txtObjet.getText()
        );

        Stage stage = (Stage) txtCotation.getScene().getWindow();
        stage.close();
    }
}
