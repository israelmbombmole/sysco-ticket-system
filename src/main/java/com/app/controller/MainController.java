package com.app.controller;

import com.app.MainApp;
import com.app.auth.Session;
import com.app.dao.NotificationDAO;
import com.app.model.Notification;
import com.app.model.TicketTask;
import com.app.service.AutomationSchedulerService;
import com.app.service.SLAMonitorService;
import com.app.util.AppUiStyles;
import com.app.util.DataShareManagementOtpService;
import com.app.util.DashboardPermissions;
import com.app.util.I18n;
import com.app.util.ModuleAccess;
import com.app.util.LanguageManager;
import com.app.util.RoleKeyUtil;
import com.app.util.NotificationPopup;
import static com.sun.source.util.DocTrees.instance;

import java.text.MessageFormat;
import java.util.List;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Button;


import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;



import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.util.Duration;



public class MainController {

private static MainController instance;


    @FXML private BorderPane rootPane;
    @FXML private VBox sidebar;
    @FXML private StackPane contentArea;
    @FXML private Label lblUser;
    @FXML private Button btnChat;

    @FXML private Button btnUserManagement;
    @FXML private Button btnLeaveManagement;
    @FXML private Button btnLoginAudit;
    @FXML private Button btnTicketMonitoring;
    @FXML private Button btnTicketManagement; 
    @FXML private Button btnDashboard;
    @FXML private Button btnLogout;
    @FXML private Button btnDataEntry;
    @FXML private Button btnDataManagement;
   
    @FXML private Button btnDataShare;
    @FXML private Button btnFileShareAudit;
    @FXML private Label badgeNotifications;
    @FXML private Button btnNotifications;
 
    @FXML private Button btnMyActivity;
    @FXML private Button btnCreateTicket;
    @FXML private Button btnJobScheduler;
    @FXML private Button btnMissions;
    @FXML private Button btnCouriers;
    @FXML private Button btnCourierManagement;
    @FXML private Button btnMyShift;
    @FXML private Button btnFileShareManagement;
    @FXML private Button btnMyWork;
    @FXML private TableView<TicketTask> tableTasks;
    
    



    

    private double mouseX;
    private double mouseY;
    private String role;
    private Timeline notificationPolling;
    private String currentPageFxml;
    
    
    

    @FXML
public void initialize() throws Exception {
     instance = this;
     applyPermissions();
     
     if (tableTasks != null) {
    setupTaskDoubleClick();
}
    
    // Create translation bundle
    ResourceBundle bundle = LanguageManager.getBundle();
    

    SLAMonitorService.checkAllTickets();
    AutomationSchedulerService.start();
    startNotificationPolling();
    refreshNotifications();
    updateNotificationBadges();
    startNotificationAutoRefresh();

    // Apply automatic translation
   
    String username = Session.getUsername() != null ? Session.getUsername() : "Unknown";
    role = normalizeRole(Session.getRole());

    //applyRoleVisibility();
    updateTexts(username);

    // Listen for language change (LIVE UPDATE)
    LanguageManager.localeProperty().addListener((obs, oldVal, newVal) -> {

        ResourceBundle newBundle = LanguageManager.getBundle();
       

        updateTexts(username);
        updateNotificationBadges();
        reloadCurrentCenterPage();
    });

    // Responsive sidebar (same as sysco-ticket-system-preview: ~20% of width, clamped 180–260)
    rootPane.widthProperty().addListener((obs, oldVal, newVal) -> {
        double targetWidth = Math.max(180, Math.min(260, newVal.doubleValue() * 0.2));
        sidebar.setPrefWidth(targetWidth);
    });
    sidebar.setPrefWidth(220);

    loadDashboard();

    btnChat.setOnMouseEntered(e -> {
        btnChat.setScaleX(1.1);
        btnChat.setScaleY(1.1);
    });

    btnChat.setOnMouseExited(e -> {
        btnChat.setScaleX(1);
        btnChat.setScaleY(1);
    });

    // Visibility for File Share Management is driven by FILE_SHARE_MANAGEMENT in applyPermissions().
    // Do not hide it here — that would override granted permissions for VERIFICATEUR and other roles.

    makeDraggable(btnChat);
}


private void applyPermissions() {

    String role = Session.getRole();
    Set<String> perms = Session.getPermissions();

    // ADMIN is unrestricted even if permission rows are missing/null.
    if ("ADMIN".equalsIgnoreCase(role)) {
        showAll();
        return;
    }

    if (perms == null) {
        System.out.println("⚠️ No permissions found — treating as empty");
        perms = java.util.Collections.emptySet();
    }

    control(btnDashboard, DashboardPermissions.hasDashboardAccess(perms, Session.getRole()));
    control(btnDataEntry, ModuleAccess.canRead(perms, "DATA_ENTRY"));
    control(btnDataManagement, ModuleAccess.canRead(perms, "DATA_MANAGEMENT"));
    control(btnDataShare, ModuleAccess.canRead(perms, "DATASHARE"));
    control(btnMyActivity, ModuleAccess.canRead(perms, "MY_ACTIVITY"));
    control(btnTicketMonitoring, ModuleAccess.canRead(perms, "TICKET_MONITORING"));
    control(btnTicketManagement, ModuleAccess.canRead(perms, "TICKET_MANAGEMENT"));
    control(btnFileShareManagement, ModuleAccess.canRead(perms, "FILE_SHARE_MANAGEMENT"));
    control(btnUserManagement, ModuleAccess.canRead(perms, "USER_MANAGEMENT"));
    control(btnLoginAudit, ModuleAccess.canRead(perms, "LOGIN_AUDIT"));
    control(btnFileShareAudit, ModuleAccess.canRead(perms, "FILE_SHARE_AUDIT"));
    control(btnCreateTicket, ModuleAccess.canRead(perms, "CREATE_TICKET"));
    control(btnJobScheduler, ModuleAccess.canRead(perms, "JOB_SCHEDULER"));
    control(btnMissions, ModuleAccess.canRead(perms, "MISSIONS"));
    control(btnLeaveManagement, ModuleAccess.canRead(perms, "LEAVE_MANAGEMENT")
            || ModuleAccess.canRead(perms, "USER_MANAGEMENT"));
    control(btnMyWork, ModuleAccess.canRead(perms, "MY_WORK")
            || ModuleAccess.canRead(perms, "MY_ACTIVITY"));
    control(btnCouriers, isCourierModuleVisible(role, perms));
    control(btnCourierManagement, isCourierManagementVisible(role));
    control(btnMyShift, isMyShiftModuleVisible(role, perms));
}

    private static boolean isCourierManagementVisible(String role) {
        if (role == null) {
            return false;
        }
        String r = RoleKeyUtil.normalizeForScope(role);
        if (r.isEmpty()) {
            return false;
        }
        return switch (r) {
            case "ADMIN", "DIRECTEUR", "SECRETAIRE", "SOUS-DIRECTEUR", "INSPECTEUR" -> true;
            default -> false;
        };
    }

    private static boolean isMyShiftModuleVisible(String role, Set<String> perms) {
        if (perms != null && ModuleAccess.canRead(perms, "MY_SHIFT")) {
            return true;
        }
        if (role == null) {
            return false;
        }
        String r = RoleKeyUtil.normalizeForScope(role);
        if (r.isEmpty()) {
            return false;
        }
        return switch (r) {
            case "ADMIN", "DIRECTEUR", "SOUS-DIRECTEUR" -> true;
            default -> false;
        };
    }

    private static boolean isCourierModuleVisible(String role, Set<String> perms) {
        if (perms != null && ModuleAccess.canRead(perms, "PHYSICAL_COURIER")) {
            return true;
        }
        if (role == null) {
            return false;
        }
        // toUpperCase() alone keeps accents (e.g. "Secrétaire" → "SECRÉTAIRE" ≠ "SECRETAIRE") and
        // French "Courrier" → "COURRIER" ≠ "COURIER" — use the same NFD normalisation as courrier scoping
        String r = RoleKeyUtil.normalizeForScope(role);
        if (r.isEmpty()) {
            return false;
        }
        return switch (r) {
            case "COURIER", "SECRETAIRE", "DIRECTEUR", "SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR",
                 "VERIFICATEUR", "VERIFICATEUR-ASSISTANT" -> true;
            default -> false;
        };
    }




    private String normalizeRole(String role) {

    if (role == null) return "VERIFICATEUR";

    role = role.toUpperCase();

    // 🔥 MAP OLD SYSTEM → NEW SYSTEM
    if (role.equals("ADMIN")) return "DIRECTEUR";
    if (role.equals("USER")) return "VERIFICATEUR";
    if (role.equals("AGENT")) return "CONTROLEUR";

    return role;
}
    
    // ================================
    // UPDATE TEXTS LIVE
    // ================================
    private void updateTexts(String username) {

    ResourceBundle bundle = LanguageManager.getBundle();

    lblUser.setText(bundle.getString("user") + ": " + username);

    btnDashboard.setText(bundle.getString("dashboard"));
    btnDataEntry.setText(bundle.getString("dataEntry"));
    btnDataManagement.setText(bundle.getString("dataManagement"));

    btnDataShare.setText(bundle.getString("dataShare"));
    btnMyActivity.setText(bundle.getString("myActivity"));
    btnMyWork.setText(bundle.getString("myWork"));

    btnTicketMonitoring.setText(bundle.getString("ticketMonitoring"));
    btnTicketManagement.setText(bundle.getString("ticketManagement"));

    btnFileShareManagement.setText(bundle.getString("fileShareManagement"));

    btnUserManagement.setText(bundle.getString("userManagement"));
    if (btnLeaveManagement != null) {
        btnLeaveManagement.setText(bundle.getString("leaveManagement"));
    }
    btnLoginAudit.setText(bundle.getString("loginAudit"));
    btnFileShareAudit.setText(bundle.getString("fileShareAudit"));

    btnCreateTicket.setText(bundle.getString("createTicket"));
    if (btnJobScheduler != null) {
        btnJobScheduler.setText(bundle.getString("jobScheduler"));
    }
    if (btnMissions != null) {
        btnMissions.setText(bundle.getString("missions"));
    }
    if (btnMyShift != null) {
        btnMyShift.setText(I18n.t("myshiftModule", "MyShift"));
    }
    if (btnCouriers != null) {
        btnCouriers.setText(bundle.getString("courierModule"));
    }
    if (btnCourierManagement != null) {
        btnCourierManagement.setText(I18n.t("courierManagementModule", "Courier management"));
    }

    btnLogout.setText(bundle.getString("logout"));
    
    
    
    
}

    @FXML
private void openCreateTicket() {
    loadPage("ticket_create.fxml");
}

@FXML
private void openJobScheduler() {
    if (!"ADMIN".equalsIgnoreCase(Session.getRole())
            && (Session.getPermissions() == null
            || !ModuleAccess.canRead(Session.getPermissions(), "JOB_SCHEDULER"))) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t("err.noPermissionJobScheduler",
                "You do not have permission to access Job Scheduler."));
        alert.showAndWait();
        return;
    }
    loadPage("job_scheduler.fxml");
}

@FXML
private void openLeaveManagement() {
    Set<String> p = Session.getPermissions();
    boolean mayLeave = p != null && (ModuleAccess.canRead(p, "LEAVE_MANAGEMENT")
            || ModuleAccess.canRead(p, "USER_MANAGEMENT"));
    if (!"ADMIN".equalsIgnoreCase(Session.getRole())
            && (p == null || !mayLeave)) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t("err.noPermissionLeaveManagement",
                "You do not have permission to manage leave and holidays."));
        alert.showAndWait();
        return;
    }
    loadPage("leave_management.fxml");
}

@FXML
private void openMissions() {
    if (!"ADMIN".equalsIgnoreCase(Session.getRole())
            && (Session.getPermissions() == null
            || !ModuleAccess.canRead(Session.getPermissions(), "MISSIONS"))) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t("err.noPermissionMissions",
                "You do not have permission to access Missions."));
        alert.showAndWait();
        return;
    }
    loadPage("missions.fxml");
}

@FXML
private void openCouriers() {
    if (!"ADMIN".equalsIgnoreCase(Session.getRole())
            && !isCourierModuleVisible(Session.getRole(), Session.getPermissions())) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t("err.noPermissionCourier", "You do not have access to the courier module."));
        alert.showAndWait();
        return;
    }
    loadPage("courier_portal.fxml");
}

@FXML
private void openCourierManagement() {
    if (!"ADMIN".equalsIgnoreCase(Session.getRole())
            && !isCourierManagementVisible(Session.getRole())) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t(
                "err.noPermissionCourierManagement",
                "You do not have access to courier management."));
        alert.showAndWait();
        return;
    }
    loadPage("courier_management.fxml");
}

@FXML
private void openMyShift() {
    String raw = Session.getRole();
    if (raw != null && "EXTERNAL".equalsIgnoreCase(raw)) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t("err.noPermissionMyShift", "You do not have access to MyShift."));
        alert.showAndWait();
        return;
    }
    if (!isMyShiftModuleVisible(raw, Session.getPermissions())) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t("err.noPermissionMyShift", "You do not have access to MyShift."));
        alert.showAndWait();
        return;
    }
    if ("SOUS-DIRECTEUR".equalsIgnoreCase(raw)) {
        loadPage("myshift_sous.fxml");
    } else if ("ADMIN".equalsIgnoreCase(raw) || "DIRECTEUR".equalsIgnoreCase(raw)) {
        loadPage("myshift_director.fxml");
    } else {
        loadPage("myshift_agent.fxml");
    }
}
    
@FXML
private void loadFileShareAudit() {
    loadPage("datashare_audit.fxml");
}

@FXML
private void loadMyWork() {
    loadPage("MyWork.fxml");
}


@FXML
private void openTicketManagement() {

    try {

        // 🔥 PERMISSION CHECK ONLY (NO ROLE)
        if (!"ADMIN".equalsIgnoreCase(Session.getRole())
                && !ModuleAccess.canRead(Session.getPermissions(), "TICKET_MANAGEMENT")) {

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(I18n.t("accessDenied", "Access Denied"));
            alert.setHeaderText(null);
            alert.setContentText(I18n.t("err.noPermissionTicketManagement", "You do not have permission to access Ticket Management."));
            alert.showAndWait();

            return;
        }

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TicketManagement.fxml"),
                LanguageManager.getBundle()
        );

        Parent view = loader.load();
        setCenterContent(view);

    } catch (Exception e) {
        e.printStackTrace();
    }
}


@FXML
private void handleDataShareManagement() {
    String currentRole = Session.getRole();
    boolean isAdmin = "ADMIN".equalsIgnoreCase(currentRole);

    if (!isAdmin && (Session.getPermissions() == null
            || !ModuleAccess.canRead(Session.getPermissions(), "FILE_SHARE_MANAGEMENT"))) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("accessDenied", "Access Denied"));
        alert.setHeaderText(null);
        alert.setContentText(I18n.t("err.noPermissionFileShareManagement",
                "You do not have permission to access File Share Management."));
        alert.showAndWait();
        return;
    }

    if (isAdmin || DataShareManagementOtpService.hasActiveAccess(Session.getUserId())) {
        openDataShareManagementPage();
        return;
    }

    ButtonType enterOtpBtn = new ButtonType(
            I18n.t("button.enterOtp", "Enter OTP"),
            ButtonBar.ButtonData.OK_DONE
    );
    ButtonType requestOtpBtn = new ButtonType(
            I18n.t("button.requestOtpAdmin", "Request OTP from admin"),
            ButtonBar.ButtonData.OTHER
    );
    ButtonType cancelBtn = new ButtonType(
            I18n.t("cancel", "Cancel"),
            ButtonBar.ButtonData.CANCEL_CLOSE
    );

    Alert gate = new Alert(Alert.AlertType.INFORMATION);
    gate.setTitle(I18n.t("otpPageRestrictedTitle", "OTP Required"));
    gate.setHeaderText(I18n.t("otpPageRestrictedHeader", "File Share Management is protected"));
    String sessionHint = MessageFormat.format(
            I18n.t("otpPageRestrictedSessionHint",
                    "After a valid OTP, access lasts up to {0} minutes (admin setting, max 20)."),
            DataShareManagementOtpService.getSessionDurationMinutes());
    gate.setContentText(I18n.t("otpPageRestrictedMessage",
            "Only admins can open this page directly. Authorized users must use an admin-issued OTP.")
            + "\n\n" + sessionHint);
    gate.getButtonTypes().setAll(enterOtpBtn, requestOtpBtn, cancelBtn);

    Optional<ButtonType> selected = gate.showAndWait();
    if (selected.isEmpty() || selected.get() == cancelBtn) {
        return;
    }

    if (selected.get() == requestOtpBtn) {
        DataShareManagementOtpService.requestOtpFromAdmin(Session.getUserId(), Session.getUsername());
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setHeaderText(null);
        info.setContentText(I18n.t("otpRequestSentToAdmin",
                "OTP request sent to admin. Please wait for approval notification."));
        info.showAndWait();
        return;
    }

    TextInputDialog otpDialog = new TextInputDialog();
    otpDialog.setTitle(I18n.t("otpRequiredTitle", "OTP Required"));
    otpDialog.setHeaderText(I18n.t("otpPageEnterHeader", "Enter OTP for File Share Management"));
    otpDialog.setContentText(I18n.t("otpLabel", "OTP") + ":");
    String entered = otpDialog.showAndWait().orElse("").trim();
    if (entered.isBlank()) {
        Alert warn = new Alert(Alert.AlertType.WARNING);
        warn.setHeaderText(null);
        warn.setContentText(I18n.t("err.otpRequired", "OTP is required to access this file."));
        warn.showAndWait();
        return;
    }

    boolean ok = DataShareManagementOtpService.verifyOtpAndGrant(Session.getUserId(), entered);
    if (!ok) {
        Alert warn = new Alert(Alert.AlertType.WARNING);
        warn.setHeaderText(null);
        warn.setContentText(I18n.t("err.invalidOrExpiredOtp", "Invalid or expired OTP."));
        warn.showAndWait();
        return;
    }

    openDataShareManagementPage();
}

private void openDataShareManagementPage() {
    try {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/DataShareManagement.fxml"),
                LanguageManager.getBundle()
        );
        Parent view = loader.load();
        setCenterContent(view);
    } catch (Exception e) {
        e.printStackTrace();
    }
}


@FXML
private void openExternalTicketManagement() {
    loadPage("external_ticket_management.fxml");
}
    // ================================
    // LANGUAGE SWITCH (LIVE)
    // ================================
    @FXML
    private void switchToEnglish() {
        LanguageManager.setLocale(Locale.ENGLISH);
    }

    @FXML
    private void switchToFrench() {
        LanguageManager.setLocale(Locale.FRENCH);
    }

    // ================================
    // OPEN CHAT
    // ================================
    @FXML
public void openChat()  {

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/ChatPopup.fxml"),
                    LanguageManager.getBundle()
            );

            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(LanguageManager.getBundle().getString("chat"));
            Scene chatScene = new Scene(root);
            AppUiStyles.applyToScene(chatScene);
            stage.setScene(chatScene);
            stage.setWidth(450);
            stage.setHeight(600);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================================
    // DRAG CHAT BUTTON
    // ================================
    private void makeDraggable(Button node) {

        node.setOnMousePressed(event -> {
            mouseX = event.getSceneX();
            mouseY = event.getSceneY();
        });

        node.setOnMouseDragged(event -> {
            double deltaX = event.getSceneX() - mouseX;
            double deltaY = event.getSceneY() - mouseY;

            node.setTranslateX(node.getTranslateX() + deltaX);
            node.setTranslateY(node.getTranslateY() + deltaY);

            mouseX = event.getSceneX();
            mouseY = event.getSceneY();
        });
    }

    // ================================
    // LOAD PAGE WITH LANGUAGE
    // ================================
    private void loadPage(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/" + fxml),
                    LanguageManager.getBundle()
            );
            
            

            Parent root = loader.load();
            setCenterContent(root);
            currentPageFxml = fxml;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    

    // ================================
    // NAVIGATION
    // ================================
   @FXML
private void loadDashboard() {

    String role = normalizeRole(Session.getRole());
    String rawRole = Session.getRole();

    if (!"ADMIN".equalsIgnoreCase(rawRole)
            && !"COURIER".equalsIgnoreCase(rawRole)
            && !"SECRETAIRE".equalsIgnoreCase(rawRole)
            && !DashboardPermissions.hasDashboardAccess(Session.getPermissions(), Session.getRole())) {
        return;
    }

    switch (role) {

        case "DIRECTEUR":
            loadPage("admin_dashboard.fxml");
            break;

        case "COURIER":
            loadPage("courier_home_dashboard.fxml");
            break;
        case "SECRETAIRE":
            loadPage("secretaire_home_dashboard.fxml");
            break;

        case "SOUS-DIRECTEUR":
            loadPage("agent_dashboard.fxml");
            break;

        case "INSPECTEUR":
            loadPage("external_dashboard.fxml");
            break;

        case "CONTROLEUR":
            loadPage("user_dashboard_home.fxml");
            break;

        case "VERIFICATEUR":
            loadPage("user_dashboard.fxml");
            break;

        case "VERIFICATEUR-ASSISTANT":
            loadPage("assistant_dashboard.fxml");
            break;

        default:
            loadPage("user_excel_entry.fxml");
    }
}

    @FXML private void loadUserManagement() { loadPage("user-management.fxml"); }
@FXML private void loadLoginAudit() { loadPage("audit-log.fxml"); }
@FXML private void loadTicketMonitoring() { loadPage("TicketMonitoring.fxml"); }
@FXML private void loadDataEntry() { loadPage("user_excel_entry.fxml"); }
@FXML private void loadDataManagement() { loadPage("data_management.fxml"); }

    // ================================
    // ROLE VISIBILITY
    // ================================
   private void applyRoleVisibility() {
       
       String role = normalizeRole(Session.getRole());

    // 🔥 DIRECTEUR (ADMIN) → FULL ACCESS
    if ("DIRECTEUR".equalsIgnoreCase(role)) {
        showAll();
        return;
    }

    // 🔥 DEFAULT → LIMITED ACCESS
    hideAll();

    show(btnDashboard);
    show(btnDataShare);
    show(btnMyActivity);
    show(btnCreateTicket);

    // ===============================
    // 🔥 ROLE FLAGS (NEW SYSTEM)
    // ===============================
    boolean isDirecteur = role.equals("DIRECTEUR");
    boolean isSousDirecteur = role.equals("SOUS-DIRECTEUR");
    boolean isInspecteur = role.equals("INSPECTEUR");
    boolean isControleur = role.equals("CONTROLEUR");
    boolean isVerificateur = role.equals("VERIFICATEUR");
    boolean isAssistant = role.equals("VERIFICATEUR-ASSISTANT");

    // ===============================
    // 🔥 DIRECTEUR → FULL ACCESS
    // ===============================
    
    
    
    if (isDirecteur) {
        showAll();
        return;
    }

    // ===============================
    // 🔥 DEFAULT → HIDE ALL FIRST
    // ===============================
    hideAll();

    // ===============================
    // 🔥 COMMON ACCESS (ALL ROLES)
    // ===============================
    show(btnDashboard);
    show(btnDataShare);
    show(btnMyActivity);

    // ===============================
    // 🔥 CREATE TICKET
    // ===============================
    show(btnCreateTicket);

    // ===============================
    // 🔥 DATA ACCESS
    // ===============================
    if (isSousDirecteur || isInspecteur || isControleur) {
        show(btnDataEntry);
        show(btnDataManagement);
    }

    // ===============================
    // 🔥 TICKET ACCESS
    // ===============================
    if (isSousDirecteur || isInspecteur || isControleur || isVerificateur) {
        show(btnTicketManagement);
    }

    
    
    
    if (isSousDirecteur || isInspecteur) {
        show(btnTicketMonitoring);
    }

    // ===============================
    // 🔥 FILE SHARE MANAGEMENT
    // ===============================
    if (isSousDirecteur || isInspecteur) {
        show(btnFileShareManagement);
    }

    // ===============================
    // 🔥 AUDIT ACCESS
    // ===============================
    if (isSousDirecteur) {
        show(btnLoginAudit);
        show(btnFileShareAudit);
    }

    // ===============================
    // 🔥 USER MANAGEMENT
    // ===============================
    if (isSousDirecteur) {
        show(btnUserManagement);
    }
}

   
   private void show(Button btn) {
    if (btn == null) {
        return;
    }
    btn.setVisible(true);
    btn.setManaged(true);
}
   
   private void control(Node node, boolean allowed) {

    if (node == null) {
        System.out.println("⚠️ Missing FXML binding!");
        return;
    }

    node.setVisible(allowed);
    node.setManaged(allowed);
}

private void hide(Button btn) {
    if (btn == null) {
        return;
    }
    btn.setVisible(false);
    btn.setManaged(false);
}

private void hideAll() {

    hide(btnDashboard);
    hide(btnDataEntry);
    hide(btnDataManagement);
    hide(btnDataShare);
    hide(btnMyActivity);
    hide(btnMyWork);
    hide(btnTicketMonitoring);
    hide(btnTicketManagement);
    hide(btnFileShareManagement);
    hide(btnUserManagement);
    hide(btnLeaveManagement);
    hide(btnLoginAudit);
    hide(btnFileShareAudit);
    hide(btnCreateTicket);
    hide(btnJobScheduler);
    hide(btnMissions);
    hide(btnCouriers);
    hide(btnCourierManagement);
    hide(btnMyShift);
}

private void showAll() {

    show(btnDashboard);
    show(btnDataEntry);
    show(btnDataManagement);
    show(btnDataShare);
    show(btnMyActivity);
    show(btnMyWork);
    show(btnTicketMonitoring);
    show(btnTicketManagement);
    show(btnFileShareManagement);
    show(btnUserManagement);
    show(btnLeaveManagement);
    show(btnLoginAudit);
    show(btnFileShareAudit);
    show(btnCreateTicket);
    show(btnJobScheduler);
    show(btnMissions);
    show(btnCouriers);
    show(btnCourierManagement);
    show(btnMyShift);
}
   
   
    // ================================
    // LOGOUT WITH TRANSLATION
    // ================================
    @FXML
private void handleLogout(ActionEvent event) {

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle(I18n.t("logout", "Logout"));
    confirm.setHeaderText(I18n.t("confirmLogout", "Confirm Logout"));
    confirm.setContentText(I18n.t("confirmLogoutMessage", "Are you sure you want to logout?"));

    Optional<ButtonType> result = confirm.showAndWait();

    if (result.isPresent() && result.get() == ButtonType.OK) {

        try {
            // Always return to French when opening login screen.
            LanguageManager.setLocale(Locale.FRENCH);

            // clear session
            Session.clear();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/login.fxml"),
                    LanguageManager.getBundle()
            );

            Parent loginRoot = loader.load();

            Scene loginScene = new Scene(loginRoot);
            AppUiStyles.applyToScene(loginScene);

            Stage stage = (Stage) btnLogout.getScene().getWindow();

            stage.setScene(loginScene);
            stage.setTitle(I18n.t("login", "Login"));
            MainApp.applyPreviewWindowSize(stage);
            stage.setMaximized(true);
            stage.centerOnScreen();
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
    



public void startNotificationPolling() {

    notificationPolling = new Timeline(

        new KeyFrame(Duration.seconds(5), e -> {

            // just refresh badge and dropdown
            updateNotificationBadges();
            refreshNotifications();

        })
    );

    notificationPolling.setCycleCount(Timeline.INDEFINITE);
    notificationPolling.play();
}



@FXML
private void openDataShare() {

    try {

        System.out.println("Opening DataShare...");

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/DataShare.fxml"),
                LanguageManager.getBundle()
        );

        Parent view = loader.load();
        setCenterContent(view);

    } catch (Exception e) {
        e.printStackTrace();
    }

}



private void updateNotificationBadges() {

    int unread = NotificationDAO.getUnreadCount(Session.getUserId());
    ResourceBundle bundle = LanguageManager.getBundle();
    String dataShareLabel = bundle.getString("dataShare");
    if (unread > 0) {
        btnDataShare.setText(dataShareLabel + " (" + unread + ")");
    } else {
        btnDataShare.setText(dataShareLabel);
    }
}

private void refreshNotifications() {

    int userId = Session.getUserId();

    int unread = NotificationDAO.getUnreadCount(userId);

    if (unread > 0) {
        badgeNotifications.setText(String.valueOf(unread));
        badgeNotifications.setVisible(true);
    } else {
        badgeNotifications.setVisible(false);
    }
}

/** Call after notification-creating actions so the bell badge updates without waiting for polling. */
public static void refreshNotificationBadgesNow() {
    if (instance == null) {
        return;
    }
    javafx.application.Platform.runLater(() -> {
        instance.refreshNotifications();
        instance.updateNotificationBadges();
    });
}

private void startNotificationAutoRefresh() {

    Timeline timeline = new Timeline(

            new KeyFrame(Duration.seconds(10), event -> {

                refreshNotifications();

            })

    );

    timeline.setCycleCount(Timeline.INDEFINITE);
    timeline.play();

}



@FXML
private void openNotifications() {

    try {

        FXMLLoader loader = new FXMLLoader(
        getClass().getResource("/view/notification_dropdown.fxml"),
        LanguageManager.getBundle()
);

        Parent root = loader.load();

        Stage stage = new Stage();
        stage.setTitle(I18n.t("notifications", "Notifications"));

        Scene scene = new Scene(root);
        AppUiStyles.applyToScene(scene);

        stage.setScene(scene);
        stage.setWidth(350);
        stage.setHeight(350);

        stage.initOwner(btnNotifications.getScene().getWindow());

        stage.show();

        // ⭐ refresh badges
        updateNotificationBadges();
        refreshNotifications();

    } catch (Exception e) {
        e.printStackTrace();
    }

}

public void openPageFromNotification(String fxml) {
    loadPage(fxml);
}

@FXML
private void openMyActivity() {

    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/UserActivity.fxml"),
                LanguageManager.getBundle()
        );

        Parent view = loader.load();
        setCenterContent(view);

    } catch (Exception e) {
        e.printStackTrace();
    }
}



private void openTaskDetails(TicketTask task, MouseEvent event) {
    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TaskDetails.fxml"),
                LanguageManager.getBundle()
        );

        Parent view = loader.load();

        TaskDetailsController controller = loader.getController();
        controller.setTask(task);
        setCenterContent(view);

    } catch (Exception e) {
        e.printStackTrace();
    }
}



private void openTaskDetails(TicketTask task) {
    try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/TaskDetails.fxml"),
                LanguageManager.getBundle()
        );

        Parent view = loader.load();

        TaskDetailsController controller = loader.getController();
        controller.setTask(task);

        // 🔥 CORRECT NAVIGATION
        MainController.loadPage("TaskDetails.fxml", c -> {
            if (c instanceof TaskDetailsController tc) {
                tc.setTask(task);
            }
        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}

private void setupTaskDoubleClick() {

    tableTasks.setRowFactory(tv -> {
        TableRow<TicketTask> row = new TableRow<>();

        row.setOnMouseClicked(event -> {

            if (!row.isEmpty() && event.getClickCount() == 2) {

                TicketTask task = row.getItem();

                openTaskDetails(task);
            }
        });

        return row;
    });
}





@FXML
private void handleBack() {
    MainController.loadPage("MyWork.fxml", null);
}

public static void loadPage(String fxml, java.util.function.Consumer<Object> consumer) {

    try {

        if (instance == null) {
            System.out.println("❌ MainController instance is NULL");
            return;
        }

        FXMLLoader loader = new FXMLLoader(
                MainController.class.getResource("/view/" + fxml),
                LanguageManager.getBundle()
        );

        Parent view = loader.load();
        Object controller = loader.getController();

        if (consumer != null && controller != null) {
            consumer.accept(controller);
        }

        if (instance.rootPane == null) {
            System.out.println("❌ rootPane is NULL");
            return;
        }

        javafx.application.Platform.runLater(() -> {
            instance.setCenterContent(view);
            instance.currentPageFxml = fxml;
        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}

private void reloadCurrentCenterPage() {
    if (currentPageFxml == null || currentPageFxml.isBlank()) {
        loadDashboard();
        return;
    }
    loadPage(currentPageFxml);
}

private void setCenterContent(Parent view) {
    rootPane.setCenter(adaptViewForViewport(view));
}

/**
 * Same behavior as {@code sysco-ticket-system-preview} MainController: center content is scrollable
 * and tracks viewport width for {@link Region} children.
 */
private Parent adaptViewForViewport(Parent view) {
    if (view instanceof ScrollPane scrollView) {
        scrollView.setFitToWidth(true);
        scrollView.setFitToHeight(false);
        scrollView.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scrollView;
    }

    ScrollPane wrapper = new ScrollPane(view);
    wrapper.setFitToWidth(true);
    wrapper.setFitToHeight(true);
    wrapper.setPannable(true);
    wrapper.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    wrapper.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    wrapper.setStyle("-fx-background-color:transparent; -fx-background:transparent;");

    if (view instanceof Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
        region.setMaxHeight(Double.MAX_VALUE);
        wrapper.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                region.setPrefWidth(newBounds.getWidth()));
    }

    return wrapper;
}

}