        package com.app.ui;

        import com.app.auth.Session;
        import com.app.dao.DirectionDAO;
        import com.app.dao.SousDirectionDAO;
        import com.app.dao.UserDAO;
import com.app.dao.UserPermissionDAO;
        import com.app.model.Direction;
        import com.app.model.SousDirection;
        import com.app.model.User;
        import com.app.service.AuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

        import javafx.collections.FXCollections;
        import javafx.fxml.FXML;
        import javafx.scene.control.*;
        import javafx.stage.Stage;

        public class EditUserController {

            @FXML private TextField txtUsername;
            @FXML private TextField txtEmail;

            @FXML private ComboBox<String> cmbRole;
            @FXML private ComboBox<SousDirection> cmbSousDirection;
            @FXML private ComboBox<Direction> cmbDirection;

            @FXML private CheckBox chkActive;

            @FXML private Label lblEmail;
            @FXML private Label lblDirection;

            @FXML private Button btnCreate;
            @FXML private Button btnEdit;
            @FXML private Button btnDisable;
            @FXML private Button btnChangeRole;
            @FXML private Button btnResetPassword;

            @FXML private PasswordField txtPassword;
            @FXML private Label lblPassword;

            @FXML private CheckBox chkDashboard;
            @FXML private CheckBox chkDataEntry;
            @FXML private CheckBox chkDataManagement;
            @FXML private CheckBox chkDataShare;
            @FXML private CheckBox chkMyActivity;
            @FXML private CheckBox chkTicketMonitoring;
            @FXML private CheckBox chkTicketManagement;
            @FXML private CheckBox chkUserManagement;
            
            @FXML private CheckBox chkFileShareManagement;
            @FXML private CheckBox chkLoginAudit;
            @FXML private CheckBox chkFileShareAudit;
            @FXML private CheckBox chkCreateTicket;
            
            

            private User user;
            private boolean editMode = false;

            // =========================
            // INITIALIZE
            // =========================
        @FXML
        public void initialize() {

            cmbRole.getItems().addAll(
                "DIRECTEUR",
                "SOUS-DIRECTEUR",
                "INSPECTEUR",
                "CONTROLEUR",
                "VERIFICATEUR",
                "VERIFICATEUR-ASSISTANT",
                "COURRIER"
                );

                cmbSousDirection.setItems(SousDirectionDAO.getAllSousDirections());

                txtEmail.setVisible(false);
                txtEmail.setManaged(false);
                lblEmail.setVisible(false);

                cmbDirection.setVisible(false);
                cmbDirection.setManaged(false);
                lblDirection.setVisible(false);

                cmbRole.setOnAction(e -> updateRoleFields());

                cmbSousDirection.setOnAction(e -> updateDirectionVisibility());
            }

            public void initCreateMode() {
            this.editMode = false;

            cmbRole.getSelectionModel().selectFirst();
            cmbSousDirection.getSelectionModel().selectFirst();
            txtPassword.setVisible(true);
            lblPassword.setVisible(true);
            chkActive.setSelected(true);

        }


            // =========================
            // ROLE LOGIC
            // =========================
            private void updateRoleFields() {

                String role = cmbRole.getValue();

                boolean isAdmin = "ADMIN".equals(role);

                txtEmail.setVisible(isAdmin);
                txtEmail.setManaged(isAdmin);
                lblEmail.setVisible(isAdmin);
            }

            // =========================
            // DIRECTION LOGIC
            // =========================
            private void updateDirectionVisibility() {

                SousDirection sd = cmbSousDirection.getValue();

                boolean isAutre =
                        sd != null &&
                        "AUTRE".equalsIgnoreCase(sd.getName());

                cmbDirection.setVisible(isAutre);
                cmbDirection.setManaged(isAutre);
                lblDirection.setVisible(isAutre);

                if (isAutre) {

                    cmbDirection.setItems(
                            FXCollections.observableArrayList(
                                    DirectionDAO.getDirectionsBySousDirection(sd.getId())
                            )
                    );
                }
            }

            // =========================
            // LOAD USER
            // =========================
           public void setUser(User user) {

    this.user = user;
    this.editMode = true;

    txtUsername.setText(user.getUsername());
    cmbRole.setValue(user.getRole());
    chkActive.setSelected(user.isActive());

    txtEmail.setText(user.getEmail());

    txtPassword.setVisible(false);
    lblPassword.setVisible(false);

    cmbSousDirection.getSelectionModel().select(
            SousDirectionDAO.getByName(user.getSousDirectionName())
    );

    updateRoleFields();
    updateDirectionVisibility();

    if (user.getDirectionId() != null) {
        for (Direction d : cmbDirection.getItems()) {
            if (d.getId() == user.getDirectionId()) {
                cmbDirection.getSelectionModel().select(d);
                break;
            }
        }
    }

    // 🔥 ADD THIS LINE
    loadPermissions(user.getId());
}

            // =========================
            // SAVE USER
            // =========================
            @FXML
private void handleSave() {

    String username = txtUsername.getText().trim();
    String role = cmbRole.getValue();
    String email = txtEmail.getText().trim();

    // ===============================
    // 🔒 VALIDATION
    // ===============================
    java.util.ResourceBundle b = com.app.util.LanguageManager.getBundle();
    if (username.isEmpty()) {
        showError(b.getString("errUsernameEmpty"));
        return;
    }

    if (role == null) {
        showError(b.getString("errSelectRole"));
        return;
    }

    SousDirection sd = cmbSousDirection.getValue();

    if (sd == null) {
        showError(b.getString("errSelectSousDirection"));
        return;
    }

    // 🔐 PASSWORD (CREATE ONLY)
    String password = txtPassword.getText();

    if (!editMode && (password == null || password.isBlank())) {
        showError(b.getString("errPasswordRequired"));
        return;
    }

    int sdId = sd.getId();
    Integer directionId = null;

    if ("AUTRE".equalsIgnoreCase(sd.getName())) {

        Direction dir = cmbDirection.getValue();

        if (dir == null) {
            showError(b.getString("errSelectDirection"));
            return;
        }

        directionId = dir.getId();
    }

    try {

        User targetUser;

        // ===============================
        // 🟢 CREATE
        // ===============================
        if (!editMode) {

            // check duplicate username
            if (UserDAO.findByUsername(username) != null) {
                showError(b.getString("errUsernameExists"));
                return;
            }

            UserDAO.createUser(
                    username,
                    password, // ✅ FIXED (was default123)
                    role,
                    email,
                    sdId,
                    directionId
            );

            targetUser = UserDAO.findByUsername(username);

            AuditService.log(
                    Session.getUsername(),
                    "CREATE",
                    "USER",
                    null,
                    "Created user: " + username
            );

        } else {

            // ===============================
            // 🔵 UPDATE
            // ===============================
            UserDAO.updateUser(
                    user.getId(),
                    username,
                    role,
                    email,
                    chkActive.isSelected(),
                    sdId,
                    directionId
            );

            targetUser = user;

            AuditService.log(
                    Session.getUsername(),
                    "UPDATE",
                    "USER",
                    user.getId(),
                    "Updated user: " + user.getUsername()
            );
        }

        // ===============================
        // 🔐 PERMISSIONS
        // ===============================
        List<String> permissions = new ArrayList<>();

        if (chkDashboard.isSelected()) permissions.add("DASHBOARD");
        if (chkDataEntry.isSelected()) permissions.add("DATA_ENTRY");
        if (chkDataManagement.isSelected()) permissions.add("DATA_MANAGEMENT");
        if (chkDataShare.isSelected()) permissions.add("DATASHARE");
        if (chkMyActivity.isSelected()) permissions.add("MY_ACTIVITY");
        if (chkTicketMonitoring.isSelected()) permissions.add("TICKET_MONITORING");
        if (chkTicketManagement.isSelected()) permissions.add("TICKET_MANAGEMENT");
        if (chkUserManagement.isSelected()) permissions.add("USER_MANAGEMENT");
        if (chkFileShareManagement.isSelected()) permissions.add("FILE_SHARE_MANAGEMENT");
        if (chkLoginAudit.isSelected()) permissions.add("LOGIN_AUDIT");
        if (chkFileShareAudit.isSelected()) permissions.add("FILE_SHARE_AUDIT");
        if (chkCreateTicket.isSelected()) permissions.add("CREATE_TICKET");
        

        if (targetUser != null) {
            UserPermissionDAO.savePermissions(targetUser.getId(), permissions);
        }

        // ===============================
        // ✅ CLOSE
        // ===============================
        closeWindow();

    } catch (Exception e) {
        e.printStackTrace();
        showError(b.getString("errSavingUser"));
    }
}
            // =========================
            // CANCEL
            // =========================
@FXML
private void handleCancel() {
    closeWindow();
}



@FXML
private void handleSelectAll() {

    chkDashboard.setSelected(true);
    chkDataEntry.setSelected(true);
    chkDataManagement.setSelected(true);
    chkDataShare.setSelected(true);
    chkMyActivity.setSelected(true);
    chkTicketMonitoring.setSelected(true);
    chkTicketManagement.setSelected(true);
    chkFileShareManagement.setSelected(true);
    chkUserManagement.setSelected(true);
    chkLoginAudit.setSelected(true);
    chkFileShareAudit.setSelected(true);
    chkCreateTicket.setSelected(true);
}


// =========================
// HELPERS
// =========================
private void closeWindow() {
    Stage stage = (Stage) txtUsername.getScene().getWindow();
    stage.close();
}

private void showError(String msg) {
    new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
}
            
    private void loadPermissions(int userId) {

    // 🔥 Get permissions from DB
    Set<String> perms = UserPermissionDAO.getPermissions(userId);

    // 🔄 Reset all first
    chkDashboard.setSelected(false);
    chkDataEntry.setSelected(false);
    chkDataManagement.setSelected(false);
    chkDataShare.setSelected(false);
    chkMyActivity.setSelected(false);
    chkTicketMonitoring.setSelected(false);
    chkTicketManagement.setSelected(false);
    chkUserManagement.setSelected(false);

    if (perms == null) return;

    // ✅ Apply from DB
    chkDashboard.setSelected(perms.contains("DASHBOARD"));
    chkDataEntry.setSelected(perms.contains("DATA_ENTRY"));
    chkDataManagement.setSelected(perms.contains("DATA_MANAGEMENT"));
    chkDataShare.setSelected(perms.contains("DATASHARE"));
    chkMyActivity.setSelected(perms.contains("MY_ACTIVITY"));
    chkTicketMonitoring.setSelected(perms.contains("TICKET_MONITORING"));
    chkTicketManagement.setSelected(perms.contains("TICKET_MANAGEMENT"));
    chkUserManagement.setSelected(perms.contains("USER_MANAGEMENT"));
}        
            
            
            
            
            
            
            
            
        }