package com.app.controller;

import com.app.auth.Session;
import com.app.dao.UserDAO;
import com.app.dao.UserPermissionDAO;
import com.app.model.User;
import com.app.service.AuditService;
import com.app.session.LoggedUser;
import com.app.util.LanguageManager;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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
        lblMessage.setText(LanguageManager.getBundle().getString("loginErrorEmpty"));
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
            lblMessage.setText(LanguageManager.getBundle().getString("loginErrorInvalid"));
            return;
        }

        // 🔥 FORCE PASSWORD CHANGE
        if (user.isMustChangePassword()) {
            lblMessage.setText(LanguageManager.getBundle().getString("loginErrorMustChangePassword"));
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
        if ("COURRIER".equalsIgnoreCase(role)) {
            java.util.Set<String> perms = new java.util.HashSet<>(Session.getPermissions());
            perms.add("DASHBOARD");
            perms.add("CREATE_TICKET");
            perms.add("TICKET_MONITORING");
            Session.setPermissions(perms);
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
                    LanguageManager.getBundle()
            );

            Scene scene = new Scene(loader.load());

            Stage stage = (Stage) txtUsername.getScene().getWindow();

            stage.setScene(scene);
            stage.setTitle(LanguageManager.getBundle().getString("stageTicketSystem") + " - " + user.getUsername());
            stage.setMaximized(true);
            stage.centerOnScreen();

        } catch (Exception e) {
            e.printStackTrace();
            lblMessage.setText(LanguageManager.getBundle().getString("loginErrorLoad"));
        }

    });

    loginTask.setOnFailed(event -> {

        progressBar.setVisible(false);

        Throwable ex = loginTask.getException();

        if (ex != null) {
            lblMessage.setText(ex.getMessage()); // 🔥 SHOW LOCK MESSAGE
        } else {
            lblMessage.setText(LanguageManager.getBundle().getString("loginErrorGeneric"));
        }
    });

    new Thread(loginTask).start();
}



private void openChangePasswordScreen(User user) {

    try {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/change-password.fxml"),
                LanguageManager.getBundle()
        );

        Scene scene = new Scene(loader.load());

        // 🔥 pass user to controller
        ChangePasswordController controller = loader.getController();
        controller.setUser(user);

        Stage stage = new Stage();
        stage.setTitle(LanguageManager.getBundle().getString("loginChangePasswordTitle"));
        stage.setScene(scene);
        stage.initOwner(txtUsername.getScene().getWindow());
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
        lblMessage.setText(LanguageManager.getBundle().getString("loginChangePasswordOpenError"));
    }
}

    @FXML
    private void handleEnglish() {
        switchLanguage(Locale.ENGLISH);
    }

    @FXML
    private void handleFrench() {
        switchLanguage(Locale.FRENCH);
    }

    private void switchLanguage(Locale locale) {
        LanguageManager.setLocale(locale);
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/login.fxml"),
                    LanguageManager.getBundle()
            );
            Parent root = loader.load();
            Stage stage = (Stage) txtUsername.getScene().getWindow();
            stage.getScene().setRoot(root);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}