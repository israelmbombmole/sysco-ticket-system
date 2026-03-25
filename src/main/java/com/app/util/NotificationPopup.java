package com.app.util;

import javafx.scene.control.Alert;

public class NotificationPopup {

    public static void show(String title,String message){

        Alert alert=new Alert(Alert.AlertType.INFORMATION);

        alert.setTitle(title);
        alert.setHeaderText("You have unread messages");
        alert.setContentText(message);

        alert.show();
    }
}