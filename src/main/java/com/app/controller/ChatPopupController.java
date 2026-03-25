package com.app.controller;

import com.app.auth.Session;
import com.app.dao.MessageDAO;
import com.app.dao.UserDAO;
import com.app.model.Message;
import com.app.model.User;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.List;
import javafx.geometry.Insets;

public class ChatPopupController {

    @FXML private ListView<User> userList;
    @FXML private VBox messageContainer;
    @FXML private TextField txtMessage;
    @FXML private ScrollPane scrollPane;

    private int selectedUserId = -1;
    private int loggedUserId;
    private Timeline autoRefresh;
    private int lastLoadedMessageId = 0;
    private static final int POLLING_INTERVAL = 1;

    // =============================
    // INITIALIZE
    // =============================
    @FXML
    public void initialize() {

        userList.setCellFactory(listView -> new ListCell<>() {

    @Override
    protected void updateItem(User user, boolean empty) {
        super.updateItem(user, empty);

        if (empty || user == null) {
            setGraphic(null);
            return;
        }

        HBox root = new HBox();
        root.setSpacing(10);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(8, 12, 8, 12));

        // ===== Username Label =====
        Label nameLabel = new Label(user.getUsername());
        nameLabel.setStyle("-fx-font-size: 14px;");

        // ===== Check unread count =====
        int unreadCount =
                MessageDAO.getUnreadCount(
                        user.getId(),
                        Session.getUserId()
                );

        if (unreadCount > 0) {

            // Bold username
            nameLabel.setStyle("""
                -fx-font-size: 14px;
                -fx-font-weight: bold;
            """);

            // Red unread badge
            Label badge = new Label(String.valueOf(unreadCount));
            badge.setStyle("""
                -fx-background-color: #ef4444;
                -fx-text-fill: white;
                -fx-font-size: 11px;
                -fx-padding: 3 8 3 8;
                -fx-background-radius: 10;
            """);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            root.getChildren().addAll(nameLabel, spacer, badge);

        } else {

            root.getChildren().add(nameLabel);
        }

        setGraphic(root);
    }
});
        
        
        if (!Session.isLoggedIn()) return;

        loggedUserId = Session.getUserId();

        txtMessage.setOnAction(e -> handleSend());

        loadUsers();
        setupUserSelection();
        startPolling();
    }

    private void loadUsers() {
        List<User> users = UserDAO.findAll();
        for (User user : users) {
            if (user.getId() != loggedUserId) {
                userList.getItems().add(user);
            }
        }
    }

    private void setupUserSelection() {
        userList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, selectedUser) -> {
                    if (selectedUser != null) {

                        selectedUserId = selectedUser.getId();

                        MessageDAO.markAsRead(selectedUserId, loggedUserId);
                        loadConversation();
                        userList.refresh();
                    }
                }
        );
    }

    private void startPolling() {
        autoRefresh = new Timeline(
                new KeyFrame(Duration.seconds(POLLING_INTERVAL), e -> {
                    if (selectedUserId != -1) {
                        loadNewMessages();
                        userList.refresh();
                    }
                })
        );

        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    // =============================
    // LOAD CONVERSATION
    // =============================
    private void loadConversation() {

    messageContainer.getChildren().clear();
    lastLoadedMessageId = 0;

    List<Message> messages =
            MessageDAO.getConversation(loggedUserId, selectedUserId);

    for (Message msg : messages) {

        addMessageBubble(msg);

        lastLoadedMessageId = msg.getId();
    }

    scrollPane.setVvalue(1.0);
}

    private void loadNewMessages() {

    List<Message> newMessages =
            MessageDAO.getNewMessages(
                    loggedUserId,
                    selectedUserId,
                    lastLoadedMessageId
            );

    if (newMessages.isEmpty()) return;

    boolean atBottom = scrollPane.getVvalue() >= 0.9;

    for (Message msg : newMessages) {

        addMessageBubble(msg);

        lastLoadedMessageId = msg.getId();
    }

    if (atBottom) {
        scrollPane.setVvalue(1.0);
    }
}

    // =============================
    // SEND MESSAGE
    // =============================
    @FXML
private void handleSend() {

    if (selectedUserId == -1) return;

    String text = txtMessage.getText().trim();
    if (text.isEmpty()) return;

    Message msg = new Message(loggedUserId, selectedUserId, text);

    MessageDAO.sendMessage(msg);

    List<Message> newMessages =
            MessageDAO.getNewMessages(
                    loggedUserId,
                    selectedUserId,
                    lastLoadedMessageId
            );

    for (Message m : newMessages) {

        addMessageBubble(m);

        lastLoadedMessageId = m.getId();
    }

    txtMessage.clear();

    scrollPane.setVvalue(1.0);
}

    // =============================
    // MESSAGE BUBBLE
    // =============================
    private void addMessageBubble(Message msg) {

    boolean isSender = msg.getSenderId() == loggedUserId;

    // ===== Row Container =====
    HBox row = new HBox();
    row.setAlignment(isSender ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
    row.setPadding(new Insets(5, 10, 5, 10));

    // ===== Bubble Container =====
    VBox bubble = new VBox();
    bubble.setSpacing(5);
    bubble.setMaxWidth(420);
    bubble.setPadding(new Insets(10));
    bubble.setStyle("""
        -fx-background-radius: 18;
        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 6, 0, 0, 2);
    """);

    // ===== Message Text =====
    Label messageLabel = new Label(msg.getContent());
    messageLabel.setWrapText(true);
    messageLabel.setMaxWidth(380);
    messageLabel.setStyle("-fx-font-size: 14px;");

    // ===== Timestamp =====
    Label timeLabel = new Label(msg.getTimestamp());
    timeLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.75;");

    // ===== Bottom Row (time + status) =====
    HBox bottomRow = new HBox();
    bottomRow.setSpacing(6);
    bottomRow.setAlignment(Pos.CENTER_RIGHT);
    bottomRow.getChildren().add(timeLabel);

    if (isSender) {

        // Sender bubble style
        bubble.setStyle(bubble.getStyle() + """
            -fx-background-color: #2563eb;
        """);

        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: white;");

        // Read indicator
        Label status = new Label(msg.getIsRead() == 1 ? "✓✓" : "✓");
        status.setStyle("-fx-font-size: 10px; -fx-text-fill: white;");
        bottomRow.getChildren().add(status);

    } else {

        // Receiver bubble style
        bubble.setStyle(bubble.getStyle() + """
            -fx-background-color: #f1f5f9;
            -fx-border-color: #e2e8f0;
            -fx-border-radius: 18;
        """);

        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #111;");
    }

    bubble.getChildren().addAll(messageLabel, bottomRow);
    row.getChildren().add(bubble);
    messageContainer.getChildren().add(row);

    // ===== Smooth Fade Animation =====
    FadeTransition ft = new FadeTransition(Duration.millis(180), row);
    ft.setFromValue(0);
    ft.setToValue(1);
    ft.play();
}

    // =============================
    // STOP POLLING
    // =============================
    public void stopPolling() {
        if (autoRefresh != null) {
            autoRefresh.stop();
        }
    }
}