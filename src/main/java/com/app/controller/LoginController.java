package com.app.controller;

import com.app.auth.Session;
import com.app.dao.UserDAO;
import com.app.dao.UserPermissionDAO;
import com.app.model.User;
import com.app.service.AuditService;
import com.app.session.LoggedUser;
import java.util.Set;
import com.app.util.AppUiStyles;
import com.app.util.I18n;
import com.app.util.LanguageManager;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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

    String loginId = txtUsername.getText() != null ? txtUsername.getText().trim() : "";
    String password = txtPassword.getText() != null ? txtPassword.getText().trim() : "";

    // ======================
    // VALIDATION
    // ======================
    if (loginId.isEmpty() || password.isEmpty()) {
        lblMessage.setText(I18n.t("err.enterLoginIdPassword",
                "Please enter your username/matricule and password"));
        return;
    }

    progressBar.setVisible(true);
    progressBar.setProgress(-1);

    Task<User> loginTask = new Task<>() {

        @Override
        protected User call() throws Exception {
            Thread.sleep(300);
            return UserDAO.login(loginId, password); // username OR matricule
        }
    };

    loginTask.setOnSucceeded(event -> {

        progressBar.setVisible(false);

        User user = loginTask.getValue();

        if (user == null) {
            lblMessage.setText(I18n.t("err.invalidCredentials", "Invalid username/matricule or password"));
            return;
        }

        // 🔥 FORCE PASSWORD CHANGE (expired policy or one-time password)
        if (user.isMustChangePassword()) {
            if (user.isPasswordResetOtpUsed()) {
                lblMessage.setText(I18n.t("mustSetNewPasswordAfterOtp", "Please choose a new password to continue."));
            } else {
                lblMessage.setText(I18n.t("err.mustChangePassword", "You must change your password"));
            }
            AuditService.log(
                    user.getUsername(),
                    "LOGIN",
                    "AUTH",
                    user.getId(),
                    user.isPasswordResetOtpUsed()
                            ? I18n.t("audit.loginWithOtp", "Successful login with one-time password; password change required")
                            : I18n.t("audit.loginMustChange", "User logged in; password change required")
            );
            openChangePasswordScreen(user);
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

        // ADMIN accounts always get full access.
        // For DIRECTEUR (and other roles), permissions are controlled strictly by the user form.
        if ("ADMIN".equalsIgnoreCase(role)) {

            Set<String> allPerms = Set.of(
                "DASHBOARD",
                "DATA_ENTRY",
                "DATA_MANAGEMENT",
                "DATASHARE",
                "MY_ACTIVITY",
                "MY_WORK",
                "TICKET_MONITORING",
                "TICKET_MANAGEMENT",
                "FILE_SHARE_MANAGEMENT",
                "USER_MANAGEMENT",
                "LOGIN_AUDIT",
                "FILE_SHARE_AUDIT",
                "CREATE_TICKET",
                "MISSIONS",
                "LEAVE_MANAGEMENT",
                "PHYSICAL_COURIER",
                "MY_SHIFT"
            );

            Session.setPermissions(allPerms);

        } else {
            Session.setPermissions(UserPermissionDAO.getPermissions(user.getId()));
        }

        // ======================
        // AUDIT
        // ======================
        AuditService.log(
                Session.getUsername(),
                "LOGIN",
                "SYSTEM",
                null,
                I18n.t("audit.userLoggedIn", "User logged in")
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
            AppUiStyles.applyToScene(scene);

            Stage stage = (Stage) txtUsername.getScene().getWindow();

            stage.setScene(scene);
            stage.setTitle(I18n.t("appTitle", "Ticket System") + " - " + user.getUsername());
            try {
                stage.getIcons().add(new Image(
                        "file:/C:/Users/Israe/.cursor/projects/c-sqlite-javafx-audit-system/assets/c__Users_Israe_AppData_Roaming_Cursor_User_workspaceStorage_7305834def579094771d21ca3ae571c5_images_Gemini_Generated_Image_rqnoiarqnoiarqno-a954d1ba-756d-40e2-a967-e7455c751e2e.png"
                ));
            } catch (Exception ignored) {}
            stage.setMaximized(true);
            stage.centerOnScreen();

        } catch (Exception e) {
            e.printStackTrace();
            lblMessage.setText(I18n.t("err.systemLoadFailed", "Unable to load the system"));
        }

    });

    loginTask.setOnFailed(event -> {

        progressBar.setVisible(false);

        Throwable ex = loginTask.getException();

        if (ex != null) {
            lblMessage.setText(ex.getMessage()); // 🔥 SHOW LOCK MESSAGE
        } else {
            lblMessage.setText(I18n.t("err.login", "Login error"));
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
        AppUiStyles.applyToScene(scene);

        // 🔥 pass user to controller
        ChangePasswordController controller = loader.getController();
        controller.setUser(user);

        Stage stage = new Stage();
        stage.setTitle(I18n.t("changePassword", "Change Password"));
        try {
            stage.getIcons().add(new Image(
                    "file:/C:/Users/Israe/.cursor/projects/c-sqlite-javafx-audit-system/assets/c__Users_Israe_AppData_Roaming_Cursor_User_workspaceStorage_7305834def579094771d21ca3ae571c5_images_Gemini_Generated_Image_rqnoiarqnoiarqno-a954d1ba-756d-40e2-a967-e7455c751e2e.png"
            ));
        } catch (Exception ignored) {}
        stage.setScene(scene);
        stage.initOwner(txtUsername.getScene().getWindow());
        stage.show();

    } catch (Exception e) {
        e.printStackTrace();
        lblMessage.setText(I18n.t("err.changePasswordScreen", "Unable to open change password screen"));
    }
}

    @FXML
    private void openForgotPassword() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/forgot_password.fxml"),
                    LanguageManager.getBundle()
            );
            Scene scene = new Scene(loader.load());
            AppUiStyles.applyToScene(scene);
            Stage stage = new Stage();
            stage.setTitle(I18n.t("forgotPasswordTitle", "Forgot password"));
            try {
                stage.getIcons().add(new Image(
                        "file:/C:/Users/Israe/.cursor/projects/c-sqlite-javafx-audit-system/assets/c__Users_Israe_AppData_Roaming_Cursor_User_workspaceStorage_7305834def579094771d21ca3ae571c5_images_Gemini_Generated_Image_rqnoiarqnoiarqno-a954d1ba-756d-40e2-a967-e7455c751e2e.png"
                ));
            } catch (Exception ignored) {
            }
            stage.setScene(scene);
            if (txtUsername.getScene() != null && txtUsername.getScene().getWindow() != null) {
                stage.initOwner(txtUsername.getScene().getWindow());
            }
            stage.setResizable(false);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            lblMessage.setText(I18n.t("err.forgotPasswordOpen", "Could not open password recovery."));
        }
    }
}