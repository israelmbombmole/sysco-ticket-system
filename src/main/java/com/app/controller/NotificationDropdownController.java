package com.app.controller;

import com.app.auth.Session;
import com.app.dao.NotificationDAO;
import com.app.dao.TicketDAO;
import com.app.dao.TicketTaskDAO;
import com.app.model.Notification;
import com.app.model.Ticket;
import com.app.model.TicketTask;
import com.app.ui.DataShareController;
import com.app.util.AppUiStyles;
import com.app.util.DataShareManagementOtpService;
import com.app.util.AccessContext;
import com.app.util.I18n;
import com.app.util.LanguageManager;
import com.app.util.TimeUtil;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.MessageFormat;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationDropdownController {

    @FXML
    private ListView<Notification> notificationList;

    @FXML
    public void initialize() {
        
        notificationList.setOnMouseClicked(event -> {
            Notification notif = notificationList.getSelectionModel().getSelectedItem();
            if (notif == null) {
                return;
            }
            showFullNotificationDialog(notif);
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

                lblMessage.setWrapText(true);
                lblMessage.setMaxWidth(300);

                container.setSpacing(3);
                container.setStyle("-fx-padding:8; -fx-background-radius:8;");
                container.setMaxWidth(320);
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
                    case "TICKET_REASSIGNED":
                    case "TICKET_ESCALATED":
                    case "TICKET_STARTED":
                    case "TICKET_COMPLETED":
                        icon = "🎫 ";
                        break;

                    case "TICKET_TASK_ASSIGNED":
                    case "TASK_STARTED":
                    case "TASK_COMPLETED":
                        icon = "🧩 ";
                        break;

                    case "CHAT":
                    case "CHAT_MESSAGE":
                    case "MESSAGE":
                        icon = "💬 ";
                        break;

                    case "DATASHARE_MGMT_OTP_REQUEST":
                    case "DATASHARE_MGMT_OTP":
                    case "DATASHARE_MGMT_OTP_ADMIN":
                        icon = "🔐 ";
                        break;

                    case "COURIER":
                        icon = "📨 ";
                        break;

                    default:
                        icon = "🔔 ";
                }

                lblTitle.setText(icon + localizeNotificationTitle(n));
                lblMessage.setText(localizeNotificationMessage(n));

                String timeAgo =
                        TimeUtil.timeAgo(n.getCreatedAt());

                lblTime.setText(timeAgo);

                boolean unread = n.getIsRead() == 0;
                if (unread) {
                    // unread: light highlight + stronger title
                    container.setStyle(
                            "-fx-padding:8; " +
                            "-fx-background-radius:8; " +
                            "-fx-background-color:#eaf3ff; " +
                            "-fx-border-color:#bcd7ff; " +
                            "-fx-border-radius:8;"
                    );
                    lblTitle.setStyle("-fx-font-weight:bold; -fx-font-size:13;");
                } else {
                    // read: normal background
                    container.setStyle(
                            "-fx-padding:8; " +
                            "-fx-background-radius:8; " +
                            "-fx-background-color:transparent; " +
                            "-fx-border-color:transparent;"
                    );
                    lblTitle.setStyle("-fx-font-weight:normal; -fx-font-size:13;");
                }

                setGraphic(container);
            }
        });
        
        
       
    }

    private void showFullNotificationDialog(Notification notif) {
        if (notif == null) {
            return;
        }
        String title = localizeNotificationTitle(notif);
        String fullBody = localizeNotificationMessage(notif);

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(I18n.t("notif.readFullTitle", "Notification"));
        a.setHeaderText(title);

        TextArea textArea = new TextArea(fullBody);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(8);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setStyle("-fx-font-size: 12;");

        VBox box = new VBox(6, textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        a.getDialogPane().setContent(box);
        a.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        a.getDialogPane().setPrefWidth(500);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.setResizable(true);
        a.showAndWait();
    }

private void openNotificationAction(Notification notif) {

    String type = notif.getType();
    String targetType = notif.getTargetType();
    Integer targetId = notif.getTargetId();

    try {

        // 🔥 CLOSE DROPDOWN
        Stage notificationStage =
                (Stage) notificationList.getScene().getWindow();
        notificationStage.close();

        // ===============================
        // 🔥 ROUTING
        // ===============================
        if ("DATASHARE_MGMT_OTP_REQUEST".equalsIgnoreCase(type)) {
            handleDataShareManagementOtpRequest(notif);
        } else if ("DATASHARE_MGMT_OTP_ADMIN".equalsIgnoreCase(type)) {
            MainController.loadPage("DataShareManagement.fxml", null);
        } else if ("DATASHARE_MGMT_OTP".equalsIgnoreCase(type)) {
            MainController.loadPage("DataShare.fxml", null);
        } else
        if ("CHAT_USER".equalsIgnoreCase(targetType) && targetId != null) {
            openChatAndFocusUser(targetId);
        } else if ("TICKET".equalsIgnoreCase(targetType) && targetId != null) {
            openTicketDetails(targetId);
        } else if ("TASK".equalsIgnoreCase(targetType) && targetId != null) {
            openTaskDetails(targetId);
        } else if ("DATASHARE_FILE".equalsIgnoreCase(targetType) && targetId != null) {
            openDataShareAndFocusFile(targetId);
        } else if ("DATASHARE".equalsIgnoreCase(type)) {
            MainController.loadPage("DataShare.fxml", null);
        } else if ("CLOSE_REQUEST".equalsIgnoreCase(targetType) && targetId != null) {
            MainController.loadPage("MyWork.fxml", controller -> {
                if (controller instanceof MyWorkController c) {
                    c.focusCloseRequest(targetId);
                }
            });
        } else if ("COURIER".equalsIgnoreCase(type) && "COURIER_PACKET".equalsIgnoreCase(targetType) && targetId != null) {
            MainController.loadPage("CourierDetails.fxml", c -> {
                if (c instanceof CourierDetailsController d) {
                    d.setPacketId(targetId);
                }
            });
        } else if ("MISSION".equalsIgnoreCase(targetType) && targetId != null) {
            MainController.loadPage("missions.fxml", controller -> {
                if (controller instanceof MissionController mc) {
                    mc.focusMission(targetId);
                }
            });
        } else if ("MESSAGE".equalsIgnoreCase(type)
                || "CHAT".equalsIgnoreCase(type)
                || "CHAT_MESSAGE".equalsIgnoreCase(type)) {
            openChatAndFocusUser(targetId != null ? targetId : -1);
        } else if ("TICKET_ASSIGNED".equalsIgnoreCase(type)
                || "TICKET_TASK_ASSIGNED".equalsIgnoreCase(type)
                || "TICKET_CLOSE_REQUEST".equalsIgnoreCase(type)) {
            MainController.loadPage("MyWork.fxml", null);
        } else if ("TICKET_STARTED".equalsIgnoreCase(type)
                || "TICKET_COMPLETED".equalsIgnoreCase(type)
                || "TASK_STARTED".equalsIgnoreCase(type)
                || "TASK_COMPLETED".equalsIgnoreCase(type)
                || "TICKET_REASSIGNED".equalsIgnoreCase(type)
                || "TICKET_ESCALATED".equalsIgnoreCase(type)
                || "EXTERNAL_ESCALATION_APPROVAL".equalsIgnoreCase(type)
                || "TICKET_AUTO_CREATED".equalsIgnoreCase(type)
                || "TICKET_CLOSED".equalsIgnoreCase(type)
                || "TICKET_CLOSE_REQUEST_REJECTED".equalsIgnoreCase(type)
                || "JOB_REMINDER".equalsIgnoreCase(type)) {
            // JOB_REMINDER: assignees go to tickets, not the job scheduler (same hub as auto-created ticket).
            MainController.loadPage("TicketMonitoring.fxml", null);
        }

        // ===============================
        // 🔥 MARK AS READ
        // ===============================
        NotificationDAO.markRead(notif.getId());

    } catch (Exception e) {
        e.printStackTrace();
    }
}

/** Distinguishes legacy admin rows that used type DATASHARE_MGMT_OTP from user OTP-issued rows. */
private static String titleForDataShareMgmtOtpNotification(Notification n) {
    String msg = n.getMessage() == null ? "" : n.getMessage().trim();
    if (msg.matches("^OTP generated for user ID \\d+\\.$")) {
        return I18n.t("notif.title.otpGeneratedAudit", "OTP recorded");
    }
    return I18n.t("notif.title.otpIssued", "OTP Issued");
}

private String localizeNotificationTitle(Notification n) {
    String type = n.getType() == null ? "" : n.getType().trim().toUpperCase();
    return switch (type) {
        case "DATASHARE" -> I18n.t("notif.title.newFileShared", "New File Shared");
        case "TASK_STARTED" -> I18n.t("notif.title.taskStarted", "Task Started");
        case "TASK_COMPLETED" -> I18n.t("notif.title.taskCompleted", "Task Completed");
        case "TICKET_STARTED" -> I18n.t("notif.title.ticketStarted", "Ticket Started");
        case "TICKET_COMPLETED" -> I18n.t("notif.title.ticketCompleted", "Ticket Completed");
        case "TICKET_ASSIGNED" -> I18n.t("notif.title.ticketAssigned", "Ticket Assigned");
        case "TICKET_REASSIGNED" -> I18n.t("notif.title.ticketReassigned", "Ticket Reassigned");
        case "TICKET_ESCALATED" -> I18n.t("notif.title.ticketEscalated", "Ticket Escalated");
        case "TICKET_CLOSE_REQUEST" -> I18n.t("notif.title.closeRequest", "Close Request");
        case "TICKET_CLOSE_REQUEST_REJECTED" -> I18n.t("notif.title.closeRequestRejected", "Close Request Rejected");
        case "TICKET_CLOSED" -> I18n.t("notif.title.closeRequestApproved", "Close Request Approved");
        case "JOB_REMINDER" -> I18n.t("notif.title.jobReminder", "Job Reminder");
        case "TICKET_AUTO_CREATED" -> I18n.t("notif.title.scheduledJobTicket", "Scheduled Job Ticket");
        case "TICKET_TASK_ASSIGNED" -> I18n.t("notif.title.newTask", "New Task");
        case "CHAT_MESSAGE" -> I18n.t("notif.title.newMessage", "New Message");
        case "MESSAGE" -> I18n.t("notif.title.newMessage", "New Message");
        case "DATASHARE_MGMT_OTP_REQUEST" -> I18n.t("notif.title.otpRequest", "OTP Request");
        case "DATASHARE_MGMT_OTP" -> titleForDataShareMgmtOtpNotification(n);
        case "DATASHARE_MGMT_OTP_ADMIN" -> I18n.t("notif.title.otpGeneratedAudit", "OTP recorded");
        case "EXTERNAL_ESCALATION_APPROVAL" -> I18n.t("notif.title.externalEscalationApproval", "External Escalation Approval");
        default -> n.getTitle();
    };
}

private static String notifFmt(String key, String fallback, Object... args) {
    return MessageFormat.format(I18n.t(key, fallback), args);
}

private String localizeNotificationMessage(Notification n) {
    String type = n.getType() == null ? "" : n.getType().trim().toUpperCase();
    String message = n.getMessage() == null ? "" : n.getMessage();

    if ("TASK_STARTED".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) started task \"(.+?)\" for ticket (.+)$").matcher(message);
        if (m.find()) {
            return m.group(1) + " " + I18n.t("notif.msg.taskStarted", "started task")
                    + " \"" + m.group(2) + "\" " + I18n.t("notif.msg.forTicket", "for ticket") + " " + m.group(3);
        }
    }
    if ("DATASHARE".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) shared \"(.+?)\" with you\\.(?: OTP: (\\d{6}))?$").matcher(message);
        if (m.find()) {
            String base = m.group(1) + " " + I18n.t("notif.msg.sharedFile", "shared")
                    + " \"" + m.group(2) + "\" " + I18n.t("notif.msg.withYou", "with you") + ".";
            String otp = m.group(3);
            if (otp != null && !otp.isBlank()) {
                base += " " + I18n.t("otpLabel", "OTP") + ": " + otp;
            }
            return base;
        }
        Matcher mOtpText = Pattern.compile("^(.+?) shared \"(.+?)\" with you\\. OTP: (.*)$").matcher(message);
        if (mOtpText.find()) {
            String base = mOtpText.group(1) + " " + I18n.t("notif.msg.sharedFile", "shared")
                    + " \"" + mOtpText.group(2) + "\" " + I18n.t("notif.msg.withYou", "with you") + ".";
            String otp = mOtpText.group(3);
            if (otp != null && !otp.isBlank()) {
                base += " " + I18n.t("otpLabel", "OTP") + ": " + otp;
            }
            return base;
        }
    }
    if ("DATASHARE_MGMT_OTP_REQUEST".equals(type)) {
        Matcher mReq = Pattern.compile("^(.+?) requested OTP for File Share Management access\\.$").matcher(message);
        if (mReq.find()) {
            return notifFmt("notif.msg.otpMgmtRequestBody",
                    "{0} requested an OTP for File Share Management access.",
                    mReq.group(1));
        }
        return message;
    }
    if ("DATASHARE_MGMT_OTP".equals(type)) {
        Matcher mIssued = Pattern.compile(
                "^(.+?) generated OTP for File Share Management access\\. OTP: (\\d+) \\(session (\\d+) min\\)$")
                .matcher(message);
        if (mIssued.find()) {
            return notifFmt("notif.msg.otpMgmtIssuedBody",
                    "{0} generated an OTP for File Share Management access. OTP: {1} (session {2} min)",
                    mIssued.group(1), mIssued.group(2), mIssued.group(3));
        }
        Matcher mAdmin = Pattern.compile("^OTP generated for user ID (\\d+)\\.$").matcher(message);
        if (mAdmin.find()) {
            return notifFmt("notif.msg.otpMgmtAdminAckBody",
                    "OTP generated for user ID {0}.",
                    mAdmin.group(1));
        }
        return message;
    }
    if ("DATASHARE_MGMT_OTP_ADMIN".equals(type)) {
        Matcher mAdmin = Pattern.compile("^OTP generated for user ID (\\d+)\\.$").matcher(message);
        if (mAdmin.find()) {
            return notifFmt("notif.msg.otpMgmtAdminAckBody",
                    "OTP generated for user ID {0}.",
                    mAdmin.group(1));
        }
        return message;
    }
    if ("TASK_COMPLETED".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) completed task \"(.+?)\" for ticket (.+)$").matcher(message);
        if (m.find()) {
            return m.group(1) + " " + I18n.t("notif.msg.taskCompleted", "completed task")
                    + " \"" + m.group(2) + "\" " + I18n.t("notif.msg.forTicket", "for ticket") + " " + m.group(3);
        }
    }
    if ("TICKET_STARTED".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) started working on ticket (.+)$").matcher(message);
        if (m.find()) {
            return m.group(1) + " " + I18n.t("notif.msg.ticketStarted", "started working on ticket") + " " + m.group(2);
        }
    }
    if ("TICKET_COMPLETED".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) completed ticket (.+)$").matcher(message);
        if (m.find()) {
            return m.group(1) + " " + I18n.t("notif.msg.ticketCompleted", "completed ticket") + " " + m.group(2);
        }
    }
    if ("TICKET_ASSIGNED".equals(type)) {
        Matcher m = Pattern.compile("^You have been assigned ticket (.+)$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.youAssignedTicket", "You have been assigned ticket {0}", m.group(1));
        }
    }
    if ("TICKET_REASSIGNED".equals(type)) {
        Matcher m = Pattern.compile("^Ticket (.+?) has been reassigned to you$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.ticketReassignedBody", "Ticket {0} has been reassigned to you", m.group(1));
        }
    }
    if ("TICKET_ESCALATED".equals(type)) {
        Matcher m = Pattern.compile("^Ticket (.+?) has been escalated to you$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.ticketEscalatedBody", "Ticket {0} has been escalated to you", m.group(1));
        }
    }
    if ("TICKET_TASK_ASSIGNED".equals(type)) {
        Matcher m1 = Pattern.compile("^You received a task for ticket (.+)$").matcher(message);
        if (m1.find()) {
            return notifFmt("notif.msg.taskReceivedForTicket", "You received a task for ticket {0}", m1.group(1));
        }
        Matcher m2 = Pattern.compile("^(.+?) assigned you task \"(.+?)\" for ticket (.+)$").matcher(message);
        if (m2.find()) {
            return notifFmt("notif.msg.taskAssignedYou", "{0} assigned you task \"{1}\" for ticket {2}",
                    m2.group(1), m2.group(2), m2.group(3));
        }
    }
    if ("JOB_REMINDER".equals(type)) {
        Matcher m = Pattern.compile("^Scheduled job \"(.+?)\" is due at (.+)$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.jobReminderDue", "Scheduled job \"{0}\" is due at {1}", m.group(1), m.group(2));
        }
    }
    if ("TICKET_AUTO_CREATED".equals(type)) {
        Matcher m = Pattern.compile("^A scheduled job is due\\. Ticket (.+?) was created\\.$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.scheduledJobTicketCreated", "A scheduled job is due. Ticket {0} was created.", m.group(1));
        }
    }
    if ("CHAT_MESSAGE".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) sent you a message$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.chatMessageSent", "{0} sent you a message", m.group(1));
        }
    }
    if ("MESSAGE".equals(type)) {
        Matcher m = Pattern.compile("^New message from (.+)$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.newMessageFrom", "New message from {0}", m.group(1));
        }
    }
    if ("TICKET_CLOSE_REQUEST".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) requested close approval for ticket (.+?)\\. (.+)$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.closeRequestBody",
                    "{0} requested close approval for ticket {1}. {2}",
                    m.group(1), m.group(2), m.group(3));
        }
    }
    if ("TICKET_CLOSE_REQUEST_REJECTED".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) rejected close request for (.+)$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.closeRequestRejectedBody", "{0} rejected close request for {1}", m.group(1), m.group(2));
        }
    }
    if ("TICKET_CLOSED".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) approved and closed ticket (.+)$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.closeRequestApprovedBody", "{0} approved and closed ticket {1}", m.group(1), m.group(2));
        }
    }
    if ("EXTERNAL_ESCALATION_APPROVAL".equals(type)) {
        Matcher m = Pattern.compile("^(.+?) requested external escalation approval for (.+)$").matcher(message);
        if (m.find()) {
            return notifFmt("notif.msg.externalEscalationBody",
                    "{0} requested external escalation approval for {1}",
                    m.group(1), m.group(2));
        }
    }

    return message;
}

private void handleDataShareManagementOtpRequest(Notification notif) {
    if (!AccessContext.isSystemSuperAdmin()) {
        Alert deny = new Alert(Alert.AlertType.WARNING);
        deny.setHeaderText(null);
        deny.setContentText(I18n.t("err.onlyAdminCanGenerateOtp",
                "Only the global administrator can generate OTP for this request."));
        deny.showAndWait();
        return;
    }

    Integer requesterId = notif.getTargetId();
    if (requesterId == null || requesterId <= 0) {
        return;
    }

    String requesterName = notif.getTargetRef() == null || notif.getTargetRef().isBlank()
            ? ("User ID " + requesterId)
            : notif.getTargetRef();

    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
    confirm.setTitle(I18n.t("otpGenerateTitle", "Generate OTP"));
    confirm.setHeaderText(I18n.t("otpGenerateHeader", "Generate OTP for File Share Management"));
    confirm.setContentText(I18n.t("otpGeneratePrompt", "Generate OTP for ") + requesterName + "?");
    ButtonType yesBtn = new ButtonType(I18n.t("confirm", "Confirm"), ButtonBar.ButtonData.OK_DONE);
    ButtonType cancelBtn = new ButtonType(I18n.t("cancel", "Cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
    confirm.getButtonTypes().setAll(yesBtn, cancelBtn);

    if (confirm.showAndWait().orElse(cancelBtn) != yesBtn) {
        return;
    }

    String otp = DataShareManagementOtpService.issueOtpForUser(
            requesterId,
            Session.getUserId(),
            Session.getUsername()
    );

    Alert info = new Alert(Alert.AlertType.INFORMATION);
    info.setHeaderText(null);
    info.setContentText(I18n.t("otpGeneratedForUser", "OTP generated for user")
            + " " + requesterName + ": " + otp);
    info.showAndWait();
}

private void openChatAndFocusUser(int userId) throws Exception {
    FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/view/ChatPopup.fxml"),
            LanguageManager.getBundle()
    );
    Parent root = loader.load();
    ChatPopupController controller = loader.getController();
    if (controller != null && userId > 0) {
        controller.openConversationWithUserId(userId);
    }

    Stage stage = new Stage();
    stage.setTitle(I18n.t("chat", "Chat"));
    Scene chatScene = new Scene(root);
    AppUiStyles.applyToScene(chatScene);
    stage.setScene(chatScene);
    stage.setWidth(450);
    stage.setHeight(600);
    stage.centerOnScreen();
    stage.show();
}

private void openTicketDetails(int ticketId) {
    Ticket t = TicketDAO.getTicketById(ticketId);
    if (t == null) {
        MainController.loadPage("TicketMonitoring.fxml", null);
        return;
    }
    MainController.loadPage("TicketDetails.fxml", controller -> {
        if (controller instanceof TicketDetailsController c) {
            c.setTicket(t);
        }
    });
}

private void openTaskDetails(int taskId) {
    TicketTask task = TicketTaskDAO.getTaskById(taskId);
    if (task == null) {
        MainController.loadPage("MyWork.fxml", null);
        return;
    }
    MainController.loadPage("TaskDetails.fxml", controller -> {
        if (controller instanceof TaskDetailsController c) {
            c.setTask(task);
        }
    });
}

private void openDataShareAndFocusFile(int fileId) {
    MainController.loadPage("DataShare.fxml", controller -> {
        if (controller instanceof DataShareController c) {
            c.focusFileById(fileId);
        }
    });
}
    
    
    
}