package com.app.controller;

import com.app.dao.UserDAO;
import com.app.util.I18n;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.text.MessageFormat;

/**
 * Issues a 5-character one-time password (24h). User then signs in with that code and is forced to set a new password.
 */
public class ForgotPasswordController {

    @FXML
    private TextField txtLoginId;
    @FXML
    private Label lblMessage;

    @FXML
    public void initialize() {
        if (lblMessage != null) {
            lblMessage.setText("");
        }
    }

    @FXML
    private void handleGenerate() {
        lblMessage.setText("");
        String id = txtLoginId.getText() != null ? txtLoginId.getText().trim() : "";
        if (id.isEmpty()) {
            lblMessage.setText(I18n.t("err.enterLoginId", "Enter your username or matricule."));
            return;
        }
        String plain = UserDAO.issueOrRegeneratePasswordOtp(id);
        if (plain == null) {
            lblMessage.setText(I18n.t("err.forgotPasswordNoUser", "No active account found for this username or matricule."));
            return;
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(I18n.t("otpGeneratedTitle", "One-time password"));
        a.setHeaderText(null);
        String template = I18n.t("otpGeneratedBody",
                "Your 5-character one-time password is:\n\n{0}\n\nIt is valid for 24 hours. Sign in with this code, then you will be asked to set a new password.\n\nDo not share this code.");
        a.setContentText(MessageFormat.format(template, plain));
        a.showAndWait();
        close();
    }

    @FXML
    private void handleClose() {
        close();
    }

    private void close() {
        if (txtLoginId != null && txtLoginId.getScene() != null) {
            Stage s = (Stage) txtLoginId.getScene().getWindow();
            s.close();
        }
    }
}
