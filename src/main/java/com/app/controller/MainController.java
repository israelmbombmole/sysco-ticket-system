package com.app.controller;

import com.app.auth.Session;
import com.app.dao.NotificationDAO;
import com.app.model.Notification;
import com.app.model.TicketTask;
import com.app.service.SLAMonitorService;
import com.app.util.LanguageManager;
import com.app.util.NotificationPopup;

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
import javafx.scene.control.ScrollPane;



public class MainController {

private static MainController instance;


    @FXML private BorderPane rootPane;
    @FXML private VBox sidebar;
    @FXML private StackPane contentArea;
    @FXML private Label lblUser;
    @FXML private Button btnChat;

    @FXML private Button btnUserManagement;
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
    @FXML private Button btnFileShareManagement;
    @FXML private Button btnMyWork;
    @FXML private TableView<TicketTask> tableTasks;
    
    



    

    private double mouseX;
    private double mouseY;
    private String role;
    private Timeline notificationPolling;
    private int lastUnreadCount = -1;
    
    
    

    @FXML
public void initialize() throws Exception {
     instance = this;
     applyPermissions();
     
     if (tableTasks != null) {
    setupTaskDoubleClick();
}
    
    SLAMonitorService.checkAllTickets();
    startNotificationPolling();

    // Apply automatic translation
   
    String username = Session.getUsername() != null ? Session.getUsername() : "Unknown";
    role = normalizeRole(Session.getRole());

    //applyRoleVisibility();
    updateTexts(username);
    refreshNotificationWidgets(true);

    // Listen for language change (LIVE UPDATE)
    LanguageManager.localeProperty().addListener((obs, oldVal, newVal) -> {
        updateTexts(username);
        refreshNotificationWidgets(true);
        loadDashboard();
    });

    // Responsive sidebar
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

    if (!normalizeRole(Session.getRole()).equals("DIRECTEUR")) {
    btnFileShareManagement.setVisible(false);
    btnFileShareManagement.setManaged(false);
}

    makeDraggable(btnChat);
}


private void applyPermissions() {

    Set<String> perms = Session.getPermissions();

    if (perms == null) {
        System.out.println("⚠️ No permissions found");
        return;
    }

    control(btnDashboard, perms.contains("DASHBOARD"));
    control(btnDataEntry, perms.contains("DATA_ENTRY"));
    control(btnDataManagement, perms.contains("DATA_MANAGEMENT"));
    control(btnDataShare, perms.contains("DATASHARE"));
    control(btnMyActivity, perms.contains("MY_ACTIVITY"));
    control(btnTicketMonitoring, perms.contains("TICKET_MONITORING"));
    control(btnTicketManagement, perms.contains("TICKET_MANAGEMENT"));
    control(btnFileShareManagement, perms.contains("FILE_SHARE_MANAGEMENT"));
    control(btnUserManagement, perms.contains("USER_MANAGEMENT"));
    control(btnLoginAudit, perms.contains("LOGIN_AUDIT"));
    control(btnFileShareAudit, perms.contains("FILE_SHARE_AUDIT"));
    control(btnCreateTicket, perms.contains("CREATE_TICKET"));
    control(btnMyWork, true);
    
    
    
    
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

    btnTicketMonitoring.setText(bundle.getString("ticketMonitoring"));
    btnTicketManagement.setText(bundle.getString("ticketManagement"));

    btnFileShareManagement.setText(bundle.getString("fileShareManagement"));

    btnUserManagement.setText(bundle.getString("userManagement"));
    btnLoginAudit.setText(bundle.getString("loginAudit"));
    btnFileShareAudit.setText(bundle.getString("fileShareAudit"));

    btnCreateTicket.setText(bundle.getString("createTicket"));

    btnLogout.setText(bundle.getString("logout"));
    
    
    
    
}

    @FXML
private void openCreateTicket() {
    loadPage("ticket_create.fxml");
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
        if (!Session.getPermissions().contains("TICKET_MANAGEMENT")) {

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Access Denied");
            alert.setHeaderText(null);
            alert.setContentText("You do not have permission to access Ticket Management.");
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

     try {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/DataShareManagement.fxml"),
                LanguageManager.getBundle()
        );

        Parent view = loader.load();

        // Load the screen into the center of the main layout
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
            stage.setScene(new Scene(root));
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

    switch (role) {

        case "DIRECTEUR":
            loadPage("admin_dashboard.fxml");
            break;

        case "SOUS-DIRECTEUR":
        case "INSPECTEUR":
            loadPage("agent_dashboard.fxml");
            break;

        case "CONTROLEUR":
        case "VERIFICATEUR":
        case "VERIFICATEUR-ASSISTANT":
            loadPage("user_dashboard_home.fxml");
            break;
        case "COURRIER":
            loadPage("courier_dashboard.fxml");
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
    btn.setVisible(false);
    btn.setManaged(false);
}

private void hideAll() {

    hide(btnDashboard);
    hide(btnDataEntry);
    hide(btnDataManagement);
    hide(btnDataShare);
    hide(btnMyActivity);
    hide(btnTicketMonitoring);
    hide(btnTicketManagement);
    hide(btnFileShareManagement);
    hide(btnUserManagement);
    hide(btnLoginAudit);
    hide(btnFileShareAudit);
    hide(btnCreateTicket);
}

private void showAll() {

    show(btnDashboard);
    show(btnDataEntry);
    show(btnDataManagement);
    show(btnDataShare);
    show(btnMyActivity);
    show(btnTicketMonitoring);
    show(btnTicketManagement);
    show(btnFileShareManagement);
    show(btnUserManagement);
    show(btnLoginAudit);
    show(btnFileShareAudit);
    show(btnCreateTicket);
}
   
   
    // ================================
    // LOGOUT WITH TRANSLATION
    // ================================
    @FXML
private void handleLogout(ActionEvent event) {

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle("Logout");
    confirm.setHeaderText("Confirm Logout");
    confirm.setContentText("Are you sure you want to logout?");

    Optional<ButtonType> result = confirm.showAndWait();

    if (result.isPresent() && result.get() == ButtonType.OK) {

        try {

            // clear session
            Session.clear();

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/login.fxml")
            );

            Parent loginRoot = loader.load();

            Scene loginScene = new Scene(loginRoot, 900, 550);

            Stage stage = (Stage) btnLogout.getScene().getWindow();

            stage.setScene(loginScene);
            stage.setTitle("Login");
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

        new KeyFrame(Duration.seconds(10), e -> {

            refreshNotificationWidgets(false);

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



private void refreshNotificationWidgets(boolean forceRefresh) {
    int unread = NotificationDAO.getUnreadCount(Session.getUserId());
    if (!forceRefresh && unread == lastUnreadCount) {
        return;
    }

    String dataShareLabel = LanguageManager.getBundle().getString("dataShare");
    if (unread > 0) {
        btnDataShare.setText(dataShareLabel + "  🔔 " + unread);
        badgeNotifications.setText(String.valueOf(unread));
        badgeNotifications.setVisible(true);
    } else {
        btnDataShare.setText(dataShareLabel);
        badgeNotifications.setVisible(false);
    }

    lastUnreadCount = unread;
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
        stage.setTitle("Notifications");

        Scene scene = new Scene(root);

        stage.setScene(scene);
        stage.setWidth(350);
        stage.setHeight(350);

        stage.initOwner(btnNotifications.getScene().getWindow());

        stage.show();

        // ⭐ mark notifications as read
        NotificationDAO.markAllRead(Session.getUserId());

        // ⭐ refresh badges
        refreshNotificationWidgets(true);

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

        // ✅ CORRECT NAVIGATION
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
        });

    } catch (Exception e) {
        e.printStackTrace();
    }
}

private void setCenterContent(Parent view) {
    rootPane.setCenter(adaptViewForViewport(view));
}

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