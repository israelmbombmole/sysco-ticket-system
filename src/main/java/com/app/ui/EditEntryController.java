package com.app.ui;

import com.app.dao.UserActionDAO;
import com.app.model.DataEntry;
import com.app.service.ExcelService;
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
                "Développement et Maintenance des Applications",
                "Réseaux, Télécommunications et Maintenance Hardware",
                "Sydonia"
        );
    }

    // ===============================
    // RECEIVE DATA FROM PARENT
    // ===============================
    public void setData(DataEntry entry, Runnable onSaveCallback) {

        this.selectedEntry = entry;
        this.onSaveCallback = onSaveCallback;

        dpDateEnreg.setValue(parseDateOrNull(entry.getDateEnregistrement()));
        txtExpediteur.setText(entry.getExpediteur());
        txtObjet.setText(entry.getObjet());
        txtCotation.setText(entry.getCotation());
        dpDateCotation.setValue(parseDateOrNull(entry.getDateCotation()));
        String sd = entry.getSousDirection();
        if (sd == null || sd.isBlank() || "N/A".equalsIgnoreCase(sd.trim())) {
            cbSousDirection.setValue(null);
        } else {
            cbSousDirection.setValue(sd);
        }
    }

    private static LocalDate parseDateOrNull(String raw) {
        if (raw == null || raw.isBlank() || "N/A".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
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

        String dEnr = dpDateEnreg.getValue() != null ? dpDateEnreg.getValue().toString() : "N/A";
        String dCot = dpDateCotation.getValue() != null ? dpDateCotation.getValue().toString() : "N/A";
        String sous = cbSousDirection.getValue();
        if (sous == null || sous.isBlank()) {
            sous = "N/A";
        }

        ExcelService.update(
                selectedEntry,
                dEnr,
                txtExpediteur.getText(),
                txtObjet.getText(),
                txtCotation.getText(),
                dCot,
                sous
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
