package com.app.util;

import javafx.scene.control.Alert;

public class NotificationPopup {

    public static void show(String title, String message) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        alert.setTitle(title);
        alert.setHeaderText(I18n.t("notif.popup.unreadHeader", "You have unread messages"));
        alert.setContentText(message);

        alert.show();
    }
}