package com.app.ui;

import com.app.auth.Session;
import com.app.dao.DirectionDAO;
import com.app.dao.SousDirectionDAO;
import com.app.dao.UserDAO;
import com.app.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.app.dao.TicketDAO;
import com.app.dao.UserPermissionDAO;
import com.app.model.Direction;
import com.app.model.SousDirection;
import com.app.security.RoleUtil;
import static com.app.security.RoleUtil.canManage;
import com.app.service.AuditService;
import com.app.util.AccessContext;
import com.app.util.AppUiStyles;
import com.app.util.I18n;
import com.app.util.LanguageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.layout.Region;

public class UserManagementController {

    @FXML private TableView<User> tableUsers;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colMatricule;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, Boolean> colActive;
    @FXML private TextField txtSearch;
    @FXML private TableColumn<User, String> colSousDirection;
    @FXML private Button editButton;
    @FXML private Button enableDisableButton;
    @FXML private Button changeRoleButton;
    @FXML private Button resetPasswordButton;
    @FXML private TableColumn<User, Void> colDelete;
    
    
    
    
    



    private final ObservableList<User> users = FXCollections.observableArrayList();

    // =========================
    // INIT
    // =========================
    @FXML
public void initialize() {
    
    addDeleteButton();

    
    // ===============================
    // 🔥 SELECTION
    // ===============================
    BooleanBinding noSelection = tableUsers.getSelectionModel()
            .selectedItemProperty().isNull();

    // ===============================
    // 🔥 ROLE-BASED EDIT CONTROL
    // ===============================
    BooleanBinding disableEdit = tableUsers.getSelectionModel()
        .selectedItemProperty().isNull()
        .or(Bindings.createBooleanBinding(() -> {

            User selected = tableUsers.getSelectionModel().getSelectedItem();

            if (selected == null) return true;

            if (AccessContext.isBuiltInSuperAdminUsername(selected.getUsername())
                    && !AccessContext.isSystemSuperAdmin()) {
                return true;
            }

            int current = getRoleLevel(Session.getRole());
            int target = getRoleLevel(selected.getRole());

            // ONLY block if trying to edit HIGHER role
            return current < target;

        }, tableUsers.getSelectionModel().selectedItemProperty()));

    // ===============================
    // 🔥 BUTTON BINDINGS
    // ===============================
    editButton.disableProperty().bind(disableEdit);
    changeRoleButton.disableProperty().bind(disableEdit);

    // ===============================
    // 🔥 ENABLE / DISABLE BUTTON (with self-protection)
    // ===============================
    BooleanBinding disableEnableBtn = disableEdit.or(
            Bindings.createBooleanBinding(() -> {

                User u = tableUsers.getSelectionModel().getSelectedItem();

                return u != null && u.getId() == Session.getUserId();

            }, tableUsers.getSelectionModel().selectedItemProperty())
    );

    enableDisableButton.disableProperty().bind(disableEnableBtn);

    // ===============================
    // 🔥 RESET PASSWORD RULE
    // ===============================
    BooleanBinding disableReset = tableUsers.getSelectionModel()
        .selectedItemProperty().isNull()
        .or(Bindings.createBooleanBinding(() -> {

            User selected = tableUsers.getSelectionModel().getSelectedItem();

            if (selected == null) return true;

            int current = getRoleLevel(Session.getRole());
            int target = getRoleLevel(selected.getRole());

            return current < target;

        }, tableUsers.getSelectionModel().selectedItemProperty()));

    
    
    // ===============================
    // 🔥 TABLE CONFIG
    // ===============================
    tableUsers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    colId.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.1));
    colUsername.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.18));
    colMatricule.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.16));
    colRole.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.16));
    colActive.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.10));
    colSousDirection.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.28));
    colDelete.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.12));

    colId.setCellValueFactory(data ->
            new SimpleIntegerProperty(data.getValue().getId()).asObject());

    colUsername.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getUsername()));
    
    colMatricule.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getMatricule() == null ? "" : data.getValue().getMatricule()));

    colRole.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getRole()));

    colActive.setCellValueFactory(data ->
            new SimpleBooleanProperty(data.getValue().isActive()));

    colSousDirection.setCellValueFactory(data -> {
        String sd = data.getValue().getSousDirectionName();
        String dir = data.getValue().getDirectionName();
        boolean hasDir = dir != null && !dir.isBlank();
        boolean hasSd = sd != null && !sd.isBlank();
        if (hasDir && hasSd) {
            return new SimpleStringProperty(dir + " — " + sd);
        }
        if (hasDir) {
            return new SimpleStringProperty(dir);
        }
        if (hasSd) {
            return new SimpleStringProperty(sd);
        }
        return new SimpleStringProperty("");
    });
    
    tableUsers.setRowFactory(tv -> {
    TableRow<User> row = new TableRow<>();

    row.setOnMouseClicked(event -> {
        if (!row.isEmpty()) {
            tableUsers.getSelectionModel().select(row.getItem());
        }
    });

    return row;
});
    
    
    

    // ===============================
    // 🔥 LOAD DATA
    // ===============================
    tableUsers.setItems(users);
    loadUsers();
}



    private void loadUsers() {
users.clear();

users.addAll(
    UserDAO.getAllUsers()
        .stream()
        .filter(u -> !u.getHidden()) // ✅ only visible users
        .toList()
);
}



    private User selected() {
        return tableUsers.getSelectionModel().getSelectedItem();
    }
    
    

    // =========================
    // CREATE USER
    // =========================
   @FXML
private void handleCreate() {

    // 🔒 role check
    if (RoleUtil.getLevel(normalizeRole(Session.getRole())) < 5) {
        showInfo(I18n.t("err.notAuthorizedCreateUsers", "You are not authorized to create users"));
        return;
    }

    // ✅ OPEN SAME FORM
    openUserForm(null);
}

@FXML
private void handleDelete() {

    User user = tableUsers.getSelectionModel().getSelectedItem();

    if (user == null) {
        showInfo(I18n.t("pleaseSelectUser", "Please select a user"));
        return;
    }

    confirmDelete(user);
}





private String normalizeRole(String role) {

    if (role == null) return "VERIFICATEUR";

    role = role.toUpperCase();

    if (role.equals("ADMIN")) return "DIRECTEUR";
    if (role.equals("USER")) return "VERIFICATEUR";
    if (role.equals("AGENT")) return "CONTROLEUR";

    return role;
}

    private User getSelectedUserOrShowError() {

    User selectedUser = tableUsers.getSelectionModel().getSelectedItem();

    if (selectedUser == null) {
        new Alert(Alert.AlertType.WARNING,
                I18n.t("pleaseSelectUserFirst", "Please select a user first."),
                ButtonType.OK).showAndWait();
        return null;
    }

    return selectedUser;
}
    
   @FXML
private void handleEdit() {

    User selectedUser = tableUsers.getSelectionModel().getSelectedItem();

    if (selectedUser == null) {
        showInfo(I18n.t("pleaseSelectUser", "Please select a user"));
        return;
    }

    // 🔥 OPEN IN EDIT MODE
    openUserForm(selectedUser);
}


private void openUserForm(User user) {
    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/edit-user.fxml"),
                LanguageManager.getBundle()
        );

        Parent root = loader.load(); // ✅ NEW INSTANCE every time

        EditUserController controller = loader.getController();

        if (user != null) {
            controller.setUser(user);
        } else {
            controller.initCreateMode();
        }

        Stage stage = new Stage();
        stage.setTitle(user == null
                ? I18n.t("createUser", "Create User")
                : I18n.t("editUser", "Edit User"));

        Scene editScene = new Scene(root, 500, 600);
        AppUiStyles.applyToScene(editScene);
        stage.setScene(editScene);

        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(tableUsers.getScene().getWindow());

        stage.showAndWait();

        refreshTable();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


    private void openEditPopup(User user) {
    try {

FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/edit-user.fxml"),
        LanguageManager.getBundle()
);

Parent root = loader.load();

        EditUserController controller = loader.getController();
        controller.setUser(user);

        Stage stage = new Stage();
        stage.setTitle(I18n.t("editUser", "Edit User"));
        Scene editScene2 = new Scene(root);
        AppUiStyles.applyToScene(editScene2);
        stage.setScene(editScene2);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(tableUsers.getScene().getWindow());
        stage.showAndWait();

        refreshTable();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


    
    // =========================
    // TOGGLE ACTIVE
    // =========================
    @FXML
private void handleToggleActive() {

   User selectedUser = tableUsers.getSelectionModel().getSelectedItem();

if (selectedUser == null) {
    showMessage(I18n.t("pleaseSelectUser", "Please select a user"));
    return;
}

// 🔥 BLOCK ONLY SELF ACTION
if (selectedUser.getId() == Session.getUserId()) {
    showMessage(I18n.t("err.cannotDisableSelf", "You cannot disable your own account."));
    return;
}

// 🔥 ALWAYS ALLOW ADMIN TO UNLOCK LOCKED USERS
UserDAO.toggleActive(selectedUser.getId());

loadUsers(); // refresh table

showMessage(I18n.t("userUpdatedSuccess", "User updated successfully"));
}


private void showMessage(String message) {

    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Message");
    alert.setHeaderText(null);
    alert.setContentText(message);

    alert.showAndWait();
}


    // =========================
    // CHANGE ROLE
    // =========================
    @FXML
private void handleChangeRole() {

    User targetUser = tableUsers.getSelectionModel().getSelectedItem();

    if (targetUser == null) {
        showInfo(I18n.t("pleaseSelectUser", "Please select a user"));
        return;
    }

    String currentRole = Session.getRole();
    String targetRole = targetUser.getRole();

    // 🔥 BLOCK if trying to modify equal or higher role
    if (!canManage(currentRole, targetRole)) {
        showInfo(I18n.t("err.cannotModifyEqualHigherRole", "You cannot modify a user with an equal or higher role."));
        return;
    }
    
    

    ChoiceDialog<String> dialog = new ChoiceDialog<>(
            targetRole,
            "DIRECTEUR",
            "SOUS-DIRECTEUR",
            "INSPECTEUR",
            "CONTROLEUR",
            "VERIFICATEUR",
            "VERIFICATEUR-ASSISTANT",
            "COURIER",
            "SECRETAIRE"
    );

    dialog.setTitle(I18n.t("changeRole", "Change Role"));
    dialog.setHeaderText(I18n.t("changeRoleFor", "Change role for") + " " + targetUser.getUsername());

    dialog.showAndWait().ifPresent(newRole -> {

        UserDAO.updateRole(targetUser.getId(), newRole);

        AuditService.log(
                Session.getUsername(),
                "ROLE_CHANGE",
                "USER",
                targetUser.getId(),
                "Changed role of " + targetUser.getUsername() + " -> " + newRole
        );

        refreshTable();
    });
}

private int getRoleLevel(String role) {

    if (role == null) return 0;

    role = role.toUpperCase();

    return switch (role) {
        case "ADMIN", "DIRECTEUR" -> 6;
        case "SOUS-DIRECTEUR" -> 5;
        case "INSPECTEUR" -> 4;
        case "CONTROLEUR", "AGENT" -> 3;
        case "VERIFICATEUR", "USER" -> 2;
        case "VERIFICATEUR-ASSISTANT", "SECRETAIRE", "COURIER" -> 1;
        default -> 0;
    };
}




private boolean canManage(String currentRole, String targetRole) {
    return getRoleLevel(currentRole) > getRoleLevel(targetRole);
}


    // =========================
    // RESET PASSWORD
    // =========================
    @FXML
    private void handleResetPassword() {

        User u = selected();
        if (u == null) return;

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("resetPassword", "Reset Password"));

        ButtonType resetBtn =
                new ButtonType(I18n.t("reset", "Reset"), ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes()
                .addAll(resetBtn, ButtonType.CANCEL);

        PasswordField pwd = new PasswordField();
        pwd.setPromptText(I18n.t("newPassword", "New password"));

        dialog.getDialogPane().setContent(pwd);
        dialog.setResultConverter(btn -> btn == resetBtn ? pwd.getText() : null);

        dialog.showAndWait().ifPresent(newPwd -> {

            if (newPwd.isBlank()) return;

            UserDAO.updatePassword(u.getId(), newPwd);

            AuditService.log(
        Session.getUsername(),
        "PASSWORD_RESET",
        "USER",
        u.getId(),
        "Reset password for user: " + u.getUsername()
);

            showInfo(I18n.t("passwordResetSuccess", "Password reset successfully"));
        });
    }

    // =========================
    // SEARCH
    // =========================
    @FXML
    private void handleSearch() {

        String q = txtSearch.getText().toLowerCase();

        if (q.isBlank()) {
            refreshTable();
            return;
        }

        tableUsers.setItems(users.filtered(u ->
                u.getUsername().toLowerCase().contains(q) ||
                (u.getMatricule() != null && u.getMatricule().toLowerCase().contains(q)) ||
                u.getRole().toLowerCase().contains(q)
        ));
    }

    
    
    private int getSousDirectionId(String name) {

    String sql = "SELECT id FROM sous_directions WHERE name = ?";

    try (Connection c = com.app.util.DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, name);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    throw new RuntimeException("Sous Direction not found: " + name);
}


    // =========================
    // HELPERS
    // =========================
    private void refreshTable() {

    users.clear();
    users.addAll(UserDAO.getAllUsers());
    tableUsers.setItems(users);

}

   
    @FXML
    private void openAuditLogs() {
        try {
FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/audit-log.fxml"),
        LanguageManager.getBundle()
);
            Scene scene = new Scene(loader.load(), 900, 550);
            AppUiStyles.applyToScene(scene);

            Stage stage = new Stage();
            stage.setTitle(I18n.t("auditLogs", "Audit Logs"));
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(tableUsers.getScene().getWindow());
            stage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showInfo(String msg) {
        Alert alert =
                new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.showAndWait();
    }
    
     private void addDeleteButton() {

    colDelete.setCellFactory(param -> new TableCell<>() {

        private final Button btn = new Button(I18n.t("delete", "Delete"));

        {
            btn.setStyle("-fx-background-color:#e74c3c; -fx-text-fill:white;");

            btn.setOnAction(event -> {

                User user = getTableView().getItems().get(getIndex());

                confirmDelete(user);
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setGraphic(null);
            } else {
                setGraphic(btn);
            }
        }
    });
}
    
    private void confirmDelete(User user) {

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle(I18n.t("deleteUser", "Delete User"));
    alert.setHeaderText(I18n.t("areYouSure", "Are you sure?"));
    alert.setContentText(I18n.t("deleteUserLabel", "Delete user:") + " " + user.getUsername());

    Optional<ButtonType> result = alert.showAndWait();

    if (result.isPresent() && result.get() == ButtonType.OK) {

        if (!verifyAdminPassword()) {
            showInfo(I18n.t("err.incorrectAdminPassword", "Incorrect admin password"));
            return;
        }

        try {

            // ✅ SOFT DELETE (deactivate)
            UserDAO.toggleActive(user.getId());

            // ✅ REMOVE ONLY THIS USER FROM UI
            tableUsers.getItems().remove(user);

            AuditService.log(
                    Session.getUsername(),
                    "SOFT_DELETE",
                    "USER",
                    user.getId(),
                    "User hidden from UI (deactivated): " + user.getUsername()
            );

            showInfo(I18n.t("userRemovedFromList", "User removed from list"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
    
    private boolean verifyAdminPassword() {

    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle(I18n.t("adminVerification", "Admin Verification"));
    dialog.setHeaderText(I18n.t("enterAdminPasswordConfirmDeletion", "Enter admin password to confirm deletion"));

    ButtonType confirmBtn = new ButtonType(I18n.t("confirm", "Confirm"), ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(confirmBtn, ButtonType.CANCEL);

    PasswordField passwordField = new PasswordField();
    passwordField.setPromptText(I18n.t("adminPassword", "Admin password"));

    dialog.getDialogPane().setContent(passwordField);

    dialog.setResultConverter(btn -> {
        if (btn == confirmBtn) {
            return passwordField.getText();
        }
        return null;
    });

    Optional<String> result = dialog.showAndWait();

    if (result.isEmpty()) return false;

    String enteredPassword = result.get();

    // 🔥 VERIFY AGAINST DATABASE
    return UserDAO.verifyPassword(Session.getUserId(), enteredPassword);
}
    
    
    
    
}
