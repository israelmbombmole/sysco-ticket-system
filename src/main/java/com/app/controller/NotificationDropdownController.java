package com.app.controller;

import com.app.auth.Session;
import com.app.dao.NotificationDAO;
import com.app.model.Notification;
import com.app.util.TimeUtil;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class NotificationDropdownController {

    @FXML
    private ListView<Notification> notificationList;

    @FXML
    public void initialize() {
        
        notificationList.setOnMouseClicked(event -> {

    Notification notif =
        notificationList.getSelectionModel().getSelectedItem();

    if (notif == null) return;
    System.out.println("Clicked notification: " + notif.getType());

    openNotificationAction(notif);

});
        
        

        List<Notification> list =
NotificationDAO.getAll(Session.getUserId());

        notificationList.setItems(
                FXCollections.observableArrayList(list)
        );

        notificationList.setCellFactory(param -> new ListCell<Notification>() {

            private VBox container = new VBox();
            private Label lblTitle = new Label();
            private Label lblMessage = new Label();
            private Label lblTime = new Label();

            {
                lblTitle.setStyle("-fx-font-weight:bold; -fx-font-size:13;");
                lblMessage.setStyle("-fx-text-fill:#444;");
                lblTime.setStyle("-fx-text-fill:gray; -fx-font-size:11;");

                container.setSpacing(3);
                container.getChildren().addAll(lblTitle, lblMessage, lblTime);
            }

            @Override
            protected void updateItem(Notification n, boolean empty) {

                super.updateItem(n, empty);

                if (empty || n == null) {
                    setGraphic(null);
                    return;
                }

                String icon;

                switch (n.getType()) {

                    case "DATASHARE":
                        icon = "📁 ";
                        break;

                    case "TICKET_ASSIGNED":
                        icon = "🎫 ";
                        break;

                    case "TICKET_ESCALATED":
                        icon = "⚠ ";
                        break;

                    case "CHAT":
                        icon = "💬 ";
                        break;

                    default:
                        icon = "🔔 ";
                }

                lblTitle.setText(icon + n.getTitle());
                lblMessage.setText(n.getMessage());

                String timeAgo =
                        TimeUtil.timeAgo(n.getCreatedAt());

                lblTime.setText(timeAgo);

                setGraphic(container);
            }
        });
        
        
       
    }
    
private void openNotificationAction(Notification notif) {

    String type = notif.getType();

    try {

        // 🔥 CLOSE DROPDOWN
        Stage notificationStage =
                (Stage) notificationList.getScene().getWindow();
        notificationStage.close();

        // ===============================
        // 🔥 ROUTING
        // ===============================
        if ("DATASHARE".equalsIgnoreCase(type)) {

            MainController.loadPage("DataShare.fxml", null);

        } 
        else if ("MESSAGE".equalsIgnoreCase(type)) {

            // 🔥 DIRECTLY OPEN CHAT (NO INSTANCE)
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/ChatPopup.fxml")
            );

            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Chat");
            stage.setScene(new Scene(root));
            stage.setWidth(450);
            stage.setHeight(600);
            stage.centerOnScreen();
            stage.show();

        }

        // ===============================
        // 🔥 MARK AS READ
        // ===============================
        NotificationDAO.markRead(notif.getId());

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
    
}