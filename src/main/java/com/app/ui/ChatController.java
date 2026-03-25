package com.app.ui;

import com.app.auth.Session;
import com.app.dao.ChatDAO;
import com.app.dao.UserDAO;
import com.app.dao.UserStatusDAO;
import com.app.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import com.app.dao.ChatDAO;
import java.util.List;


public class ChatController {

    @FXML private VBox usersContainer;
    @FXML private ListView<String> chatListView;
    @FXML private TextField txtMessage;

    private User selectedUser;

    @FXML
    public void initialize() {

        loadUsers();
    }

    private void loadUsers() {

        List<User> users = UserDAO.getAllUsers();

        usersContainer.getChildren().clear();

        for (User user : users) {

            if (user.getId() == Session.getUserId()) continue;

            Label label = new Label(user.toString());

            label.setOnMouseClicked(e -> {
                selectedUser = user;
                loadConversation();
            });

            usersContainer.getChildren().add(label);
        }
    }

    private void loadConversation() {

        if (selectedUser == null) return;

        List<String> messages =
                ChatDAO.getConversation(selectedUser.getId());

        chatListView.getItems().setAll(messages);
    }

    @FXML
    private void handleSend() {

        if (selectedUser == null) return;

        if (txtMessage.getText().isBlank()) return;

        ChatDAO.sendMessage(
                selectedUser.getId(),
                txtMessage.getText().trim()
        );

        txtMessage.clear();
        loadConversation();
    }
}