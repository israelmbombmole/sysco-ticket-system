        package com.app.ui;

        import com.app.auth.Session;
        import com.app.dao.DirectionDAO;
        import com.app.dao.SousDirectionDAO;
        import com.app.dao.UserDAO;
import com.app.dao.UserPermissionDAO;
import com.app.util.AccessContext;
import com.app.util.DashboardPermissions;
import com.app.util.I18n;
import com.app.util.ModuleAccess;
import com.app.util.RoleDefaultPermissions;
import com.app.util.AttendanceSignatureUtil;
import com.app.util.DB;
        import com.app.model.Direction;
        import com.app.model.SousDirection;
        import com.app.model.User;
        import com.app.service.AuditService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

        public class EditUserController {

            @FXML private TextField txtUsername;
            @FXML private TextField txtMatricule;
            @FXML private TextField txtAttendanceSignature;
            @FXML private TextField txtEmail;
            @FXML private TextField txtCustomSousDirection;

            @FXML private ComboBox<String> cmbRole;
            @FXML private ComboBox<SousDirection> cmbSousDirection;
            @FXML private ComboBox<Direction> cmbDirection;

            @FXML private CheckBox chkActive;

            @FXML private Label lblEmail;
            @FXML private Label lblDirection;
            @FXML private Label lblCustomSousDirection;

            @FXML private Button btnCreate;
            @FXML private Button btnEdit;
            @FXML private Button btnDisable;
            @FXML private Button btnChangeRole;
            @FXML private Button btnResetPassword;

            @FXML private PasswordField txtPassword;
            @FXML private Label lblPassword;

            @FXML private VBox permissionsModuleBox;

            private User user;
            private boolean editMode = false;
            /** Permissions as last loaded / last saved baseline for dashboard row when role changes. */
            private Set<String> cachedPermissions = new HashSet<>();
            private Label lblDashboardModuleTitle;
            private CheckBox chkDashboardRead;
            private CheckBox chkDashboardWrite;
            private List<PermissionRowDefinition> permissionRows = new ArrayList<>();

            private static final Map<String, List<String>> DIRECTION_SOUS_CATALOG = new LinkedHashMap<>();

            /**
             * One logical module row: optional {@code baseKey} for dashboard (resolved from role at save time).
             */
            private static final class PermissionRowDefinition {
                final String baseKey;
                final CheckBox readBox;
                final CheckBox writeBox;
                final boolean superAdminOnly;

                PermissionRowDefinition(String baseKey, CheckBox readBox, CheckBox writeBox, boolean superAdminOnly) {
                    this.baseKey = baseKey;
                    this.readBox = readBox;
                    this.writeBox = writeBox;
                    this.superAdminOnly = superAdminOnly;
                }
            }

            static {
                DIRECTION_SOUS_CATALOG.put("Direction de la Réglementation et de la Facilitation",
                        List.of("Réglementation", "Facilitation"));
                DIRECTION_SOUS_CATALOG.put("Direction de la Lutte contre la Fraude",
                        List.of("Liaison et Renseignements", "Stratégies et Planification", "Audit a posteriori"));
                DIRECTION_SOUS_CATALOG.put("Direction du Tarif et des Règles d’Origine",
                        List.of("Tarif", "Règles d’origine"));
                DIRECTION_SOUS_CATALOG.put("Direction de la Valeur",
                        List.of("Évaluation", "Recours et valeurs de base"));
                DIRECTION_SOUS_CATALOG.put("Direction des Autres Produits d’Accises",
                        List.of("Alcools, Boissons alcooliques et Limonades", "Tabacs et autres Produits d’Accises"));
                DIRECTION_SOUS_CATALOG.put("Direction des Huiles Minérales",
                        List.of("Producteurs", "Distributeurs"));
                DIRECTION_SOUS_CATALOG.put("Direction des Recettes du Trésor",
                        List.of("Recettes de Douanes", "Recettes des Accises", "Budget et Recettes Connexes"));
                DIRECTION_SOUS_CATALOG.put("Direction des Ressources Humaines",
                        List.of("Recrutement et Formation", "Administration", "Œuvres Sociales", "Relations Publiques et Protocole"));
                DIRECTION_SOUS_CATALOG.put("Direction des Équipements et de la Logistique",
                        List.of("Gestion du Patrimoine", "Imprimerie et Approvisionnements"));
                DIRECTION_SOUS_CATALOG.put("Direction des Statistiques, Documentation et Études Économiques",
                        List.of("Statistiques et Études Économiques", "Documentation"));
                DIRECTION_SOUS_CATALOG.put("Direction des Affaires Juridiques et Contentieuses",
                        List.of("Affaires Contentieuses", "Affaires Juridiques"));
                DIRECTION_SOUS_CATALOG.put(DirectionDAO.CANONICAL_DIRECTION_SYSTEMES_TI,
                        List.of("Développement et Maintenance des Applications",
                                "Réseaux, Télécommunications et Maintenance Hardware",
                                "Sydonia"));
                DIRECTION_SOUS_CATALOG.put("Direction de l’Audit Interne", List.of());
                DIRECTION_SOUS_CATALOG.put("Direction des Finances Internes",
                        List.of("Comptabilité et Trésorerie", "Budget Interne"));
                DIRECTION_SOUS_CATALOG.put("Direction des Réformes et Modernisation", List.of());
                DIRECTION_SOUS_CATALOG.put("Bureau de Coordination", List.of());
            }

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
                "COURIER",
                "SECRETAIRE"
                );
                if (AccessContext.isSystemSuperAdmin()) {
                    cmbRole.getItems().add(0, "ADMIN");
                }

                txtEmail.setVisible(false);
                txtEmail.setManaged(false);
                lblEmail.setVisible(false);

                loadDirectionsFromCatalog();

                txtCustomSousDirection.setVisible(false);
                txtCustomSousDirection.setManaged(false);
                lblCustomSousDirection.setVisible(false);
                lblCustomSousDirection.setManaged(false);

                cmbRole.setOnAction(e -> {
                    if (!editMode && cmbRole.getValue() != null) {
                        cachedPermissions = new HashSet<>(RoleDefaultPermissions.defaultKeysForRole(cmbRole.getValue()));
                    }
                    updateRoleFields();
                    applyPermissionSelectionFromCache();
                });
                cmbDirection.setOnAction(e -> refreshSousDirectionsForDepartment());
                cmbSousDirection.setOnAction(e -> updateCustomSousDirectionVisibility());
                cmbDirection.setDisable(shouldRestrictToOwnDirection());
                buildPermissionGrid();
                refreshSousDirectionsForDepartment();
                updateRoleFields();
            }

            public void initCreateMode() {
            this.editMode = false;

            cmbRole.getSelectionModel().selectFirst();
            if (!cmbDirection.getItems().isEmpty()) {
                cmbDirection.getSelectionModel().selectFirst();
            }
            if (txtAttendanceSignature != null) {
                txtAttendanceSignature.clear();
            }
            txtPassword.setVisible(true);
            lblPassword.setVisible(true);
            chkActive.setSelected(true);
            refreshSousDirectionsForDepartment();
            updateRoleFields();
            cachedPermissions = new HashSet<>(RoleDefaultPermissions.defaultKeysForRole(cmbRole.getValue()));
            applyPermissionSelectionFromCache();

        }


            // =========================
            // ROLE LOGIC
            // =========================
            private void updateRoleFields() {

                String role = cmbRole.getValue();

                boolean showEmail =
                        role != null && DashboardPermissions.isEmailRequiredForRole(role);

                txtEmail.setVisible(showEmail);
                txtEmail.setManaged(showEmail);
                lblEmail.setVisible(showEmail);
                if (showEmail) {
                    lblEmail.setText(I18n.t("email", "Email") + " *");
                }

                if (lblDashboardModuleTitle != null && role != null) {
                    lblDashboardModuleTitle.setText(DashboardPermissions.checkboxLabelForRole(role));
                }

                updateSousDirectionState(role);
                updateDirectionFieldHint(role);
            }

            /** Director & secretary: direction is the organizational scope (courrier, tickets, notifications). */
            private void updateDirectionFieldHint(String role) {
                boolean need = role != null
                        && ("DIRECTEUR".equalsIgnoreCase(role) || "SECRETAIRE".equalsIgnoreCase(role));
                String base = I18n.t("direction", "Direction");
                if (lblDirection != null) {
                    if (need) {
                        lblDirection.setText(base + I18n.t("fieldRequiredMark", " *"));
                    } else {
                        lblDirection.setText(base);
                    }
                }
                if (cmbDirection != null) {
                    if (need) {
                        cmbDirection.setTooltip(new Tooltip(
                                I18n.t("directionHintDirSec",
                                        "Required. This account only sees courriers, tickets, and items for the selected direction. Set it in line with the user’s real service.")));
                    } else {
                        cmbDirection.setTooltip(null);
                    }
                }
            }

            // =========================
            // DEPARTMENT / SOUS-DIRECTION LOGIC
            // =========================
            private void refreshSousDirectionsForDepartment() {
                Direction direction = cmbDirection.getValue();
                if (direction == null) {
                    cmbSousDirection.setItems(FXCollections.observableArrayList());
                    updateSousDirectionState(cmbRole.getValue());
                    return;
                }

                LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
                for (String n : sousNamesForDbDirection(direction.getName())) {
                    if (n != null && !n.isBlank()) {
                        seen.putIfAbsent(n.trim(), true);
                    }
                }
                if (seen.isEmpty() && direction.getId() > 0) {
                    for (SousDirection s : DirectionDAO.getSousDirectionsForDirectionCluster(direction.getId())) {
                        if (s.getName() != null && !s.getName().isBlank()) {
                            seen.putIfAbsent(s.getName().trim(), true);
                        }
                    }
                }
                List<String> names = new ArrayList<>(seen.keySet());
                names.add("AUTRE");

                ObservableList<SousDirection> items = FXCollections.observableArrayList();
                for (String name : names) {
                    items.add(new SousDirection(name));
                }
                cmbSousDirection.setItems(items);
                if (!cmbSousDirection.getItems().isEmpty()) {
                    cmbSousDirection.getSelectionModel().selectFirst();
                } else {
                    cmbSousDirection.getSelectionModel().clearSelection();
                }
                updateSousDirectionState(cmbRole.getValue());
            }

            private void updateCustomSousDirectionVisibility() {
                SousDirection sd = cmbSousDirection.getValue();
                boolean isAutre = !cmbSousDirection.isDisabled()
                        && sd != null
                        && "AUTRE".equalsIgnoreCase(sd.getName());

                txtCustomSousDirection.setVisible(isAutre);
                txtCustomSousDirection.setManaged(isAutre);
                lblCustomSousDirection.setVisible(isAutre);
                lblCustomSousDirection.setManaged(isAutre);

                if (!isAutre) {
                    txtCustomSousDirection.clear();
                }
            }

            private void updateSousDirectionState(String role) {
                // When creating a user, directeur and secrétaire are scoped by direction only — no sous-direction.
                boolean offForCreateDirOrSec = !editMode && role != null
                        && ("DIRECTEUR".equalsIgnoreCase(role) || "SECRETAIRE".equalsIgnoreCase(role));
                cmbSousDirection.setDisable(offForCreateDirOrSec);
                if (offForCreateDirOrSec) {
                    cmbSousDirection.getSelectionModel().clearSelection();
                }
                updateCustomSousDirectionVisibility();
            }

            private List<String> sousNamesForDbDirection(String nameFromDatabase) {
                if (nameFromDatabase == null || nameFromDatabase.isBlank()) {
                    return List.of();
                }
                List<String> s = DIRECTION_SOUS_CATALOG.get(nameFromDatabase);
                if (s != null && !s.isEmpty()) {
                    return s;
                }
                String base = DirectionDAO.baseDirectionLabel(nameFromDatabase);
                String want = DirectionDAO.directionClusterKey(nameFromDatabase);
                for (String key : DIRECTION_SOUS_CATALOG.keySet()) {
                    if (nameFromDatabase.equalsIgnoreCase(key)
                            || DirectionDAO.baseDirectionLabel(key).equalsIgnoreCase(base)
                            || (!want.isEmpty() && want.equals(DirectionDAO.directionClusterKey(key)))) {
                        return DIRECTION_SOUS_CATALOG.get(key);
                    }
                }
                return List.of();
            }

            private void loadDirectionsFromCatalog() {
                ObservableList<Direction> directions = FXCollections.observableArrayList();
                boolean restrict = shouldRestrictToOwnDirection();
                Integer currentDirectionId = restrict ? getCurrentUserDirectionId() : null;

                for (String name : DIRECTION_SOUS_CATALOG.keySet()) {
                    int id = DirectionDAO.getOrCreateDirectionId(name);
                    Direction fromDb = DirectionDAO.getById(id);
                    if (fromDb == null) {
                        fromDb = new Direction(id, name);
                    }
                    if (!restrict || (currentDirectionId != null && currentDirectionId == fromDb.getId())) {
                        directions.add(fromDb);
                    }
                }
                cmbDirection.setItems(directions);
                if (!directions.isEmpty()) {
                    cmbDirection.getSelectionModel().selectFirst();
                }
            }

            // =========================
            // LOAD USER
            // =========================
           public void setUser(User user) {

    this.user = user;
    this.editMode = true;

    if (user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole())
            && cmbRole.getItems().stream().noneMatch("ADMIN"::equalsIgnoreCase)) {
        cmbRole.getItems().add(0, "ADMIN");
    }

    txtUsername.setText(user.getUsername());
    txtMatricule.setText(user.getMatricule());
    User sigRow = UserDAO.findById(user.getId());
    if (txtAttendanceSignature != null) {
        txtAttendanceSignature.setText(
                sigRow != null && sigRow.getAttendanceSignature() != null ? sigRow.getAttendanceSignature() : "");
    }
    cmbRole.setValue(user.getRole());
    chkActive.setSelected(user.isActive());

    txtEmail.setText(user.getEmail());

    txtPassword.setVisible(false);
    lblPassword.setVisible(false);

    if (user.getDirectionId() != null) {
        for (Direction d : cmbDirection.getItems()) {
            if (d.getId() == user.getDirectionId()) {
                cmbDirection.getSelectionModel().select(d);
                break;
            }
        }
    }
    refreshSousDirectionsForDepartment();

    if (user.getSousDirectionName() != null) {
        boolean existsInCombo = cmbSousDirection.getItems().stream()
                .anyMatch(sd -> user.getSousDirectionName().equalsIgnoreCase(sd.getName()));
        if (existsInCombo) {
            for (SousDirection sd : cmbSousDirection.getItems()) {
                if (user.getSousDirectionName().equalsIgnoreCase(sd.getName())) {
                    cmbSousDirection.getSelectionModel().select(sd);
                    break;
                }
            }
        } else {
            SousDirection autre = SousDirectionDAO.getByName("AUTRE");
            if (autre != null) {
                for (SousDirection sd : cmbSousDirection.getItems()) {
                    if ("AUTRE".equalsIgnoreCase(sd.getName())) {
                        cmbSousDirection.getSelectionModel().select(sd);
                        break;
                    }
                }
                txtCustomSousDirection.setText(user.getSousDirectionName());
            }
        }
    }

    updateRoleFields();
    updateCustomSousDirectionVisibility();

    // 🔥 ADD THIS LINE
    loadPermissions(user.getId());
}

            // =========================
            // SAVE USER
            // =========================
            @FXML
private void handleSave() {

    String username = safeTrim(txtUsername != null ? txtUsername.getText() : null);
    String matricule = safeTrim(txtMatricule != null ? txtMatricule.getText() : null);
    String attendancePreferred = safeTrim(txtAttendanceSignature != null ? txtAttendanceSignature.getText() : null);
    String role = cmbRole.getValue();
    String email = safeTrim(txtEmail != null ? txtEmail.getText() : null);

    // ===============================
    // 🔒 VALIDATION
    // ===============================
    if (username.isEmpty()) {
        showError(I18n.t("err.usernameEmpty", "Username cannot be empty."));
        return;
    }
    if (matricule.isEmpty()) {
        showError(I18n.t("err.matriculeEmpty", "Matricule cannot be empty."));
        return;
    }

    if (role == null) {
        showError(I18n.t("err.selectRole", "Please select a role."));
        return;
    }

    if ("ADMIN".equalsIgnoreCase(role) && !AccessContext.isSystemSuperAdmin()) {
        showError(I18n.t("err.adminRoleSuperOnly", "Only the global super administrator can assign the ADMIN role."));
        return;
    }
    if (!editMode && AccessContext.isBuiltInSuperAdminUsername(username) && !AccessContext.isSystemSuperAdmin()) {
        showError(I18n.t("err.reservedAdminUsername", "The username is reserved for the system administrator."));
        return;
    }

    // Directeur / secrétaire / global admin: sous-direction is optional (edit); create dir/sec: no sous.
    boolean sousOptionalForRole = "DIRECTEUR".equalsIgnoreCase(role)
            || "SECRETAIRE".equalsIgnoreCase(role)
            || "ADMIN".equalsIgnoreCase(role);
    SousDirection sd = cmbSousDirection.getValue();
    boolean createAsDirOrSec = !editMode && role != null
            && ("DIRECTEUR".equalsIgnoreCase(role) || "SECRETAIRE".equalsIgnoreCase(role));

    // 🔐 PASSWORD (CREATE ONLY)
    String password = txtPassword.getText();

    if (!editMode && (password == null || password.isBlank())) {
        showError(I18n.t("err.passwordRequired", "Password is required."));
        return;
    }

    Direction dir = cmbDirection.getValue();
    if (dir == null) {
        if (role != null
                && ("DIRECTEUR".equalsIgnoreCase(role) || "SECRETAIRE".equalsIgnoreCase(role))) {
            showError(I18n.t("err.directionRequiredForDirSec",
                    "You must select a direction for a director or secretary. It defines which courriers and tickets this user can act on."));
        } else {
            showError(I18n.t("err.selectDirection", "Please select a Direction."));
        }
        return;
    }
    Integer directionId = dir.getId();
    if (shouldRestrictToOwnDirection()) {
        Integer ownDirectionId = getCurrentUserDirectionId();
        if (ownDirectionId == null || ownDirectionId <= 0) {
            showError(I18n.t("err.selectDirection", "Please select a Direction."));
            return;
        }
        directionId = ownDirectionId;
    }

    Integer sdId = null;
    if (createAsDirOrSec) {
        sdId = null;
    } else if (sousOptionalForRole) {
        if (sd != null) {
            if ("AUTRE".equalsIgnoreCase(sd.getName())) {
                String customSous = txtCustomSousDirection.getText() != null
                        ? txtCustomSousDirection.getText().trim()
                        : "";
                if (customSous.isEmpty()) {
                    SousDirection autreRow = SousDirectionDAO.getByName("AUTRE");
                    sdId = autreRow != null ? autreRow.getId() : SousDirectionDAO.getOrCreateSousDirectionId("AUTRE");
                } else {
                    sdId = SousDirectionDAO.getOrCreateSousDirectionId(customSous);
                }
            } else {
                sdId = SousDirectionDAO.getOrCreateSousDirectionId(sd.getName());
            }
        }
    } else {
        if (sd == null) {
            showError(I18n.t("err.selectSousDirection", "Please select a Sous Direction."));
            return;
        }
        if ("AUTRE".equalsIgnoreCase(sd.getName())) {
            String customSous = txtCustomSousDirection.getText() != null
                    ? txtCustomSousDirection.getText().trim()
                    : "";
            if (customSous.isEmpty()) {
                showError(I18n.t("err.manualSousDirectionRequired", "Please enter a manual Sous Direction for AUTRE."));
                return;
            }
            sdId = SousDirectionDAO.getOrCreateSousDirectionId(customSous);
        } else {
            sdId = SousDirectionDAO.getOrCreateSousDirectionId(sd.getName());
        }
    }

    if (DashboardPermissions.isEmailRequiredForRole(role)) {
        if (email.isEmpty() || !isPlausibleEmail(email)) {
            showError(I18n.t("err.emailRequiredForRole", "Email is required for DIRECTEUR, SOUS-DIRECTEUR, and INSPECTEUR."));
            return;
        }
    }

    try {

        User targetUser;

        // ===============================
        // 🟢 CREATE
        // ===============================
        if (!editMode) {

            // check duplicate username
            if (UserDAO.findByUsername(username) != null) {
                showError(I18n.t("err.usernameExists", "Username already exists."));
                return;
            }
            if (UserDAO.existsByMatricule(matricule, null)) {
                showError(I18n.t("err.matriculeExists", "Matricule already exists."));
                return;
            }

            UserDAO.createUser(
                    username,
                    matricule,
                    password, // ✅ FIXED (was default123)
                    role,
                    email,
                    sdId,
                    directionId,
                    attendancePreferred.isEmpty() ? null : attendancePreferred
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
            if (UserDAO.existsByMatricule(matricule, user.getId())) {
                showError(I18n.t("err.matriculeExists", "Matricule already exists."));
                return;
            }
            String attSig;
            if (attendancePreferred.isEmpty()) {
                attSig = AttendanceSignatureUtil.generateForUser(user.getId(), matricule);
            } else {
                String c = AttendanceSignatureUtil.clamp(attendancePreferred);
                attSig = c != null
                        ? c
                        : AttendanceSignatureUtil.generateForUser(user.getId(), matricule);
            }
            UserDAO.updateUser(
                    user.getId(),
                    username,
                    matricule,
                    role,
                    email,
                    chkActive.isSelected(),
                    sdId,
                    directionId,
                    attSig
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

        appendRwPermissionsForSave(permissions, role);

        if (targetUser != null) {
            UserPermissionDAO.savePermissions(targetUser.getId(), permissions);
        }

        // ===============================
        // ✅ CLOSE
        // ===============================
        closeWindow();

    } catch (Exception e) {
        e.printStackTrace();
        showError(I18n.t("err.savingUser", "Something went wrong while saving user."));
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

    if (chkDashboardRead != null) {
        chkDashboardRead.setSelected(true);
    }
    if (chkDashboardWrite != null) {
        chkDashboardWrite.setSelected(true);
    }
    for (PermissionRowDefinition row : permissionRows) {
        if (!row.superAdminOnly || AccessContext.isSystemSuperAdmin()) {
            row.readBox.setSelected(true);
            row.writeBox.setSelected(true);
        }
    }
}


// =========================
// HELPERS
// =========================

    private PermissionRowDefinition findRowByBase(String baseKey) {
        if (baseKey == null) {
            return null;
        }
        for (PermissionRowDefinition row : permissionRows) {
            if (baseKey.equals(row.baseKey)) {
                return row;
            }
        }
        return null;
    }

    private void bindReadWrite(CheckBox read, CheckBox write) {
        write.selectedProperty().addListener((obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) {
                read.setSelected(true);
            }
        });
        read.selectedProperty().addListener((obs, oldV, newV) -> {
            if (!Boolean.TRUE.equals(newV)) {
                write.setSelected(false);
            }
        });
    }

    private void buildPermissionGrid() {
        if (permissionsModuleBox == null) {
            return;
        }
        permissionsModuleBox.getChildren().clear();
        permissionRows.clear();

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 8, 0));

        ColumnConstraints colModule = new ColumnConstraints();
        colModule.setHgrow(Priority.ALWAYS);
        ColumnConstraints colR = new ColumnConstraints();
        colR.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints colW = new ColumnConstraints();
        colW.setMinWidth(Region.USE_PREF_SIZE);
        grid.getColumnConstraints().addAll(colModule, colR, colW);

        Label hEmpty = new Label("");
        Label hRead = new Label(I18n.t("permissionRead", "Read"));
        Label hWrite = new Label(I18n.t("permissionWrite", "Write"));
        GridPane.setConstraints(hEmpty, 0, 0);
        GridPane.setConstraints(hRead, 1, 0);
        GridPane.setConstraints(hWrite, 2, 0);
        grid.getChildren().addAll(hEmpty, hRead, hWrite);

        lblDashboardModuleTitle = new Label(I18n.t("dashboard", "Dashboard"));
        chkDashboardRead = new CheckBox();
        chkDashboardWrite = new CheckBox();
        bindReadWrite(chkDashboardRead, chkDashboardWrite);

        Label dashTitle = lblDashboardModuleTitle;
        GridPane.setConstraints(dashTitle, 0, 1);
        GridPane.setConstraints(chkDashboardRead, 1, 1);
        GridPane.setConstraints(chkDashboardWrite, 2, 1);
        dashTitle.setWrapText(true);
        dashTitle.setMaxWidth(340);
        grid.getChildren().addAll(dashTitle, chkDashboardRead, chkDashboardWrite);

        List<String[]> defs = Arrays.asList(
                new String[] {"dataEntry", "Data entry", "DATA_ENTRY", "f"},
                new String[] {"dataManagement", "Data management", "DATA_MANAGEMENT", "f"},
                new String[] {"dataShare", "Data share", "DATASHARE", "f"},
                new String[] {"myActivity", "My activity", "MY_ACTIVITY", "f"},
                new String[] {"myWork", "My work", "MY_WORK", "f"},
                new String[] {"ticketMonitoring", "Ticket monitoring", "TICKET_MONITORING", "f"},
                new String[] {"ticketManagement", "Ticket management", "TICKET_MANAGEMENT", "f"},
                new String[] {"fileShareManagement", "File share management", "FILE_SHARE_MANAGEMENT", "f"},
                new String[] {"userManagement", "User management", "USER_MANAGEMENT", "f"},
                new String[] {"leaveManagement", "Leave", "LEAVE_MANAGEMENT", "f"},
                new String[] {"loginAudit", "Login audit", "LOGIN_AUDIT", "f"},
                new String[] {"fileShareAudit", "File share audit", "FILE_SHARE_AUDIT", "f"},
                new String[] {"createTicket", "Create ticket", "CREATE_TICKET", "f"},
                new String[] {"jobScheduler", "Job scheduler", "JOB_SCHEDULER", "t"},
                new String[] {"missions", "Missions", "MISSIONS", "f"},
                new String[] {"physicalCourierModule", "Physical courier module", "PHYSICAL_COURIER", "f"},
                new String[] {"myshiftPermission", "MyShift", "MY_SHIFT", "f"}
        );

        int gridRow = 2;
        for (String[] def : defs) {
            boolean superOnly = "t".equals(def[3]);
            Label title = new Label(I18n.t(def[0], def[1]));
            String baseK = def[2];
            title.setWrapText(true);
            title.setMaxWidth(340);
            CheckBox rb = new CheckBox();
            CheckBox wb = new CheckBox();
            bindReadWrite(rb, wb);

            PermissionRowDefinition row = new PermissionRowDefinition(baseK, rb, wb, superOnly);
            permissionRows.add(row);

            GridPane.setConstraints(title, 0, gridRow);
            GridPane.setConstraints(rb, 1, gridRow);
            GridPane.setConstraints(wb, 2, gridRow);

            grid.getChildren().addAll(title, rb, wb);
            if (superOnly) {
                rb.setManaged(AccessContext.isSystemSuperAdmin());
                rb.setVisible(AccessContext.isSystemSuperAdmin());
                wb.setManaged(AccessContext.isSystemSuperAdmin());
                wb.setVisible(AccessContext.isSystemSuperAdmin());
                title.setManaged(AccessContext.isSystemSuperAdmin());
                title.setVisible(AccessContext.isSystemSuperAdmin());
                if (!AccessContext.isSystemSuperAdmin()) {
                    rb.setDisable(true);
                    wb.setDisable(true);
                }
            }
            gridRow++;
        }

        permissionsModuleBox.getChildren().add(grid);
        updateRoleFields();
    }

    private void appendRwPermissionsForSave(List<String> out, String role) {
        if (chkDashboardRead != null && chkDashboardWrite != null) {
            String dk = DashboardPermissions.keyForRole(role);
            if (chkDashboardRead.isSelected()) {
                out.add(ModuleAccess.readKey(dk));
            }
            if (chkDashboardWrite.isSelected()) {
                out.add(ModuleAccess.writeKey(dk));
            }
        }
        for (PermissionRowDefinition row : permissionRows) {
            if (row.superAdminOnly && !AccessContext.isSystemSuperAdmin()) {
                continue;
            }
            if (row.readBox.isSelected()) {
                out.add(ModuleAccess.readKey(row.baseKey));
            }
            if (row.writeBox.isSelected()) {
                out.add(ModuleAccess.writeKey(row.baseKey));
            }
        }
    }

    private void applyPermissionSelectionFromCache() {
        Set<String> perms = cachedPermissions;
        if (perms == null) {
            perms = new HashSet<>();
        }
        String r = cmbRole.getValue();

        if (chkDashboardRead != null && chkDashboardWrite != null) {
            String dk = r != null ? DashboardPermissions.keyForRole(r) : "VERIFICATEUR_DASHBOARD";
            boolean dashLegacy = perms.contains("DASHBOARD");
            chkDashboardRead.setSelected(dashLegacy || ModuleAccess.canRead(perms, dk));
            chkDashboardWrite.setSelected(dashLegacy || ModuleAccess.canWrite(perms, dk));
        }

        for (PermissionRowDefinition row : permissionRows) {
            if (row.superAdminOnly && !AccessContext.isSystemSuperAdmin()) {
                row.readBox.setSelected(false);
                row.writeBox.setSelected(false);
                continue;
            }
            String k = row.baseKey;
            boolean readSel;
            boolean writeSel;

            if ("MY_WORK".equals(k)) {
                readSel = ModuleAccess.canRead(perms, "MY_WORK")
                        || ModuleAccess.hasLegacyFull(perms, "MY_ACTIVITY");
                writeSel = ModuleAccess.canWrite(perms, "MY_WORK")
                        || ModuleAccess.hasLegacyFull(perms, "MY_ACTIVITY");
            } else if ("LEAVE_MANAGEMENT".equals(k)) {
                readSel = ModuleAccess.canRead(perms, "LEAVE_MANAGEMENT")
                        || ModuleAccess.canRead(perms, "USER_MANAGEMENT");
                writeSel = ModuleAccess.canWrite(perms, "LEAVE_MANAGEMENT")
                        || ModuleAccess.canWrite(perms, "USER_MANAGEMENT");
            } else {
                readSel = ModuleAccess.canRead(perms, k);
                writeSel = ModuleAccess.canWrite(perms, k);
            }

            row.readBox.setSelected(readSel);
            row.writeBox.setSelected(writeSel);
        }
    }

private void closeWindow() {
    Stage stage = (Stage) txtUsername.getScene().getWindow();
    stage.close();
}

private void showError(String msg) {
    new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
}

private boolean shouldRestrictToOwnDirection() {
    return !AccessContext.isSystemSuperAdmin();
}

private Integer getCurrentUserDirectionId() {
    String sql = "SELECT direction_id FROM users WHERE id = ?";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, Session.getUserId());
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            int directionId = rs.getInt("direction_id");
            return rs.wasNull() ? null : directionId;
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return null;
}
            
    private void loadPermissions(int userId) {
        Set<String> perms = UserPermissionDAO.getPermissions(userId);
        cachedPermissions = perms != null ? new HashSet<>(perms) : new HashSet<>();
        applyPermissionSelectionFromCache();
        String r = cmbRole.getValue();
        if (lblDashboardModuleTitle != null && r != null) {
            lblDashboardModuleTitle.setText(DashboardPermissions.checkboxLabelForRole(r));
        }
    }

    private static boolean isPlausibleEmail(String email) {
        String e = safeTrim(email);
        int at = e.indexOf('@');
        return at > 0 && at < e.length() - 1 && e.indexOf('.', at) > at;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
            
            
            
            
            
            
            
            
        }