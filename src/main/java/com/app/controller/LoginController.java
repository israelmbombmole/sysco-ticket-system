package com.app.controller;

import com.app.auth.Session;
import com.app.dao.UserDAO;
import com.app.dao.UserPermissionDAO;
import com.app.model.User;
import com.app.service.AuditService;
import com.app.session.LoggedUser;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.concurrent.Task;

public class LoginController {

    @FXML
    private TextField txtUsername;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private Label lblMessage;

    @FXML
    private ProgressBar progressBar;
    

    @FXML
private void handleLogin() {

    lblMessage.setText("");

    String username = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
    String password = txtPassword.getText() != null ? txtPassword.getText().trim() : "";

    // ======================
    // VALIDATION
    // ======================
    if (username.isEmpty() || password.isEmpty()) {
        lblMessage.setText("Veuillez saisir votre nom d'utilisateur et votre mot de passe");
        return;
    }

    progressBar.setVisible(true);
    progressBar.setProgress(-1);

    Task<User> loginTask = new Task<>() {

        @Override
        protected User call() throws Exception {
            Thread.sleep(300);
            return UserDAO.login(username, password); // ✅ ONLY THIS
        }
    };

    loginTask.setOnSucceeded(event -> {

        progressBar.setVisible(false);

        User user = loginTask.getValue();

        if (user == null) {
            lblMessage.setText("Identifiants invalides");
            return;
        }

        // 🔥 FORCE PASSWORD CHANGE
        if (user.isMustChangePassword()) {
            lblMessage.setText("Vous devez changer votre mot de passe");
            openChangePasswordScreen(user); // 👉 you already have this
            return;
        }

        // ======================
        // STORE SESSION
        // ======================
        LoggedUser.setId(user.getId());
        LoggedUser.setUsername(user.getUsername());
        LoggedUser.setRole(user.getRole());

        Session.set(user);

        // ======================
        // PERMISSIONS
        // ======================
        String role = user.getRole();

        if ("ADMIN".equalsIgnoreCase(role)) {
            role = "DIRECTEUR";
        }

        if ("DIRECTEUR".equalsIgnoreCase(role)) {

            Set<String> allPerms = Set.of(
                "DASHBOARD",
                "DATA_ENTRY",
                "DATA_MANAGEMENT",
                "DATASHARE",
                "MY_ACTIVITY",
                "TICKET_MONITORING",
                "TICKET_MANAGEMENT",
                "FILE_SHARE_MANAGEMENT",
                "USER_MANAGEMENT",
                "LOGIN_AUDIT",
                "FILE_SHARE_AUDIT",
                "CREATE_TICKET"
            );

            Session.setPermissions(allPerms);

        } else {

            Session.setPermissions(
                UserPermissionDAO.getPermissions(user.getId())
            );
        }

        // ======================
        // AUDIT
        // ======================
        AuditService.log(
                Session.getUsername(),
                "LOGIN",
                "SYSTEM",
                null,
                "User logged in"
        );

        // ======================
        // OPEN DASHBOARD
        // ======================
        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/MainLayout.fxml"),
                    ResourceBundle.getBundle("lang.messages")
            );

            Scene scene = new Scene(loader.load());

            Stage stage = (Stage) txtUsername.getScene().getWindow();

            stage.setScene(scene);
            stage.setTitle("Ticket System - " + user.getUsername());
            stage.setMaximized(true);
            stage.centerOnScreen();

        } catch (Exception e) {
            e.printStackTrace();
            lblMessage.setText("Impossible de charger le système");
        }

    });

    loginTask.setOnFailed(event -> {

        progressBar.setVisible(false);

        Throwable ex = loginTask.getException();

        if (ex != null) {
            lblMessage.setText(ex.getMessage()); // 🔥 SHOW LOCK MESSAGE
        } else {
            lblMessage.setText("Erreur de connexion");
        }
    });

    new Thread(loginTask).start();
}



private void openChangePasswordScreen(User user) {

    try {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/change-password.fxml")
        );

        Scene scene = new Scene(loader.load());

        // 🔥 pass user to controller
        ChangePasswordController controller = loader.getController();
        controller.setUser(user);

        Stage stage = new Stage();
        stage.setTitle("Changer le mot de passe");
        stage.setScene(scene);
        stage.initOwner(txtUsername.getScene().getWindow());
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
        lblMessage.setText("Impossible d'ouvrir l'écran de changement de mot de passe");
    }
}

    
    
}