package com.app.controller;

import com.app.dao.UserDAO;
import com.app.model.User;
import com.app.util.LanguageManager;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class ChangePasswordController {

    @FXML
    private PasswordField txtNewPassword;

    @FXML
    private PasswordField txtConfirmPassword;

    @FXML
    private Label lblMessage;

    private User user;

    // 🔥 RECEIVE USER FROM LOGIN
    public void setUser(User user) {
        this.user = user;
    }

    // =========================
    // 🔐 HANDLE SAVE
    // =========================
    @FXML
    private void handleSave() {

        String newPass = txtNewPassword.getText();
        String confirm = txtConfirmPassword.getText();

        ResourceBundle b = LanguageManager.getBundle();

        // VALIDATION
        if (newPass == null || newPass.isEmpty()) {
            lblMessage.setText(b.getString("loginPasswordEnter"));
            return;
        }

        if (!newPass.equals(confirm)) {
            lblMessage.setText(b.getString("loginPasswordMismatch"));
            return;
        }

        if (newPass.length() < 6) {
            lblMessage.setText(b.getString("loginPasswordTooShort"));
            return;
        }

        // 🔥 UPDATE PASSWORD
        UserDAO.updatePassword(user.getId(), newPass);

        lblMessage.setText(b.getString("loginPasswordUpdated"));

        // CLOSE WINDOW
        Stage stage = (Stage) txtNewPassword.getScene().getWindow();
        stage.close();
    }
}