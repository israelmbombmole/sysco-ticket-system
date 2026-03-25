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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.layout.Region;

public class UserManagementController {

    @FXML private TableView<User> tableUsers;
    @FXML private TableColumn<User, Integer> colId;
    @FXML private TableColumn<User, String> colUsername;
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
    colUsername.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.25));
    colRole.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.2));
    colActive.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.15));
    colSousDirection.prefWidthProperty().bind(tableUsers.widthProperty().multiply(0.3));

    colId.setCellValueFactory(data ->
            new SimpleIntegerProperty(data.getValue().getId()).asObject());

    colUsername.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getUsername()));

    colRole.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getRole()));

    colActive.setCellValueFactory(data ->
            new SimpleBooleanProperty(data.getValue().isActive()));

    colSousDirection.setCellValueFactory(data -> {
        String sd = data.getValue().getSousDirectionName();
        String dir = data.getValue().getDirectionName();

        return new SimpleStringProperty(
                (dir != null && !dir.isBlank()) ? sd + " - " + dir : sd
        );
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
        showInfo("You are not authorized to create users");
        return;
    }

    // ✅ OPEN SAME FORM
    openUserForm(null);
}

@FXML
private void handleDelete() {

    User user = tableUsers.getSelectionModel().getSelectedItem();

    if (user == null) {
        showInfo("Please select a user");
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
                "Please select a user first.",
                ButtonType.OK).showAndWait();
        return null;
    }

    return selectedUser;
}
    
   @FXML
private void handleEdit() {

    User selectedUser = tableUsers.getSelectionModel().getSelectedItem();

    if (selectedUser == null) {
        showInfo("Please select a user");
        return;
    }

    // 🔥 OPEN IN EDIT MODE
    openUserForm(selectedUser);
}


private void openUserForm(User user) {
    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/edit-user.fxml")
        );

        Parent root = loader.load(); // ✅ NEW INSTANCE every time

        EditUserController controller = loader.getController();

        if (user != null) {
            controller.setUser(user);
        } else {
            controller.initCreateMode();
        }

        Stage stage = new Stage();
        stage.setTitle(user == null ? "Create User" : "Edit User");

        // ✅ FIXED SIZE (for scroll)
        stage.setScene(new Scene(root, 500, 600));

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

        Locale locale = new Locale("fr"); // or "en"

ResourceBundle bundle = ResourceBundle.getBundle("lang.messages", locale);

FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/edit-user.fxml"),
        bundle
);

Parent root = loader.load();

        EditUserController controller = loader.getController();
        controller.setUser(user);

        Stage stage = new Stage();
        stage.setTitle("Edit User");
        stage.setScene(new Scene(root));
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
    showMessage("Veuillez sélectionner un utilisateur");
    return;
}

// 🔥 BLOCK ONLY SELF ACTION
if (selectedUser.getId() == Session.getUserId()) {
    showMessage("Vous ne pouvez pas désactiver votre propre compte.");
    return;
}

// 🔥 ALWAYS ALLOW ADMIN TO UNLOCK LOCKED USERS
UserDAO.toggleActive(selectedUser.getId());

loadUsers(); // refresh table

showMessage("L'utilisateur a été mis à jour avec succès");
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
        showInfo("Veuillez sélectionner un utilisateur");
        return;
    }

    String currentRole = Session.getRole();
    String targetRole = targetUser.getRole();

    // 🔥 BLOCK if trying to modify equal or higher role
    if (!canManage(currentRole, targetRole)) {
        showInfo("Vous ne pouvez pas modifier un utilisateur ayant un rôle égal ou supérieur.");
        return;
    }
    
    

    ChoiceDialog<String> dialog = new ChoiceDialog<>(
            targetRole,
            "DIRECTEUR",
            "SOUS-DIRECTEUR",
            "INSPECTEUR",
            "CONTROLEUR",
            "VERIFICATEUR",
            "VERIFICATEUR-ASSISTANT"
    );

    dialog.setTitle("Change Role");
    dialog.setHeaderText("Change role for " + targetUser.getUsername());

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
        case "VERIFICATEUR-ASSISTANT" -> 1;
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
        dialog.setTitle("Reset Password");

        ButtonType resetBtn =
                new ButtonType("Reset", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes()
                .addAll(resetBtn, ButtonType.CANCEL);

        PasswordField pwd = new PasswordField();
        pwd.setPromptText("New password");

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

            showInfo("Password reset successfully");
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
            Locale locale = new Locale("fr"); // or "en"

ResourceBundle bundle =
        ResourceBundle.getBundle("lang.messages", locale);

FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/audit-log.fxml"),
        bundle
);
            Scene scene = new Scene(loader.load(), 900, 550);

            Stage stage = new Stage();
            stage.setTitle("Audit Logs");
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

        private final Button btn = new Button("Delete");

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
    alert.setTitle("Delete User");
    alert.setHeaderText("Are you sure?");
    alert.setContentText("Delete user: " + user.getUsername());

    Optional<ButtonType> result = alert.showAndWait();

    if (result.isPresent() && result.get() == ButtonType.OK) {

        if (!verifyAdminPassword()) {
            showInfo("❌ Incorrect admin password");
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

            showInfo("Utilisateur supprimé de la liste");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
    
    private boolean verifyAdminPassword() {

    Dialog<String> dialog = new Dialog<>();
    dialog.setTitle("Admin Verification");
    dialog.setHeaderText("Enter admin password to confirm deletion");

    ButtonType confirmBtn = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(confirmBtn, ButtonType.CANCEL);

    PasswordField passwordField = new PasswordField();
    passwordField.setPromptText("Admin password");

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
