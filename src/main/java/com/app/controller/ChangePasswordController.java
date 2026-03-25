package com.app.controller;

import com.app.dao.UserDAO;
import com.app.model.User;
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

        // VALIDATION
        if (newPass == null || newPass.isEmpty()) {
            lblMessage.setText("Enter new password");
            return;
        }

        if (!newPass.equals(confirm)) {
            lblMessage.setText("Passwords do not match");
            return;
        }

        if (newPass.length() < 6) {
            lblMessage.setText("Password must be at least 6 characters");
            return;
        }

        // 🔥 UPDATE PASSWORD
        UserDAO.updatePassword(user.getId(), newPass);

        lblMessage.setText("Password updated successfully");

        // CLOSE WINDOW
        Stage stage = (Stage) txtNewPassword.getScene().getWindow();
        stage.close();
    }
}