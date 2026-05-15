package com.app.ui;

import com.app.auth.Session;
import com.app.controller.MainController;
import com.app.dao.DataShareDAO;
import com.app.dao.NotificationDAO;
import com.app.model.DataShareFile;
import com.app.model.Notification;
import com.app.util.AppUiStyles;
import com.app.util.DataShareManagementOtpService;
import com.app.util.I18n;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.text.MessageFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DataShareManagementController {

    @FXML private TableView<DataShareFile> tableFiles;

    @FXML private TableColumn<DataShareFile,String> colFileName;
    @FXML private TableColumn<DataShareFile,String> colSharedBy;
    @FXML private TableColumn<DataShareFile,String> colRole;
    @FXML private TableColumn<DataShareFile,String> colDate;
    @FXML private TableColumn<DataShareFile,String> colTime;

    @FXML private TextField txtSearch;
    @FXML private HBox adminSessionSettingsBox;
    @FXML private ComboBox<Integer> cmbSessionMinutes;
    @FXML private VBox otpRequestsPanel;
    @FXML private TableView<Notification> tableOtpRequests;
    @FXML private TextField txtSearchOtpRequests;

    private List<DataShareFile> allFiles = new ArrayList<>();
    private List<Notification> allOtpRequestsCache = new ArrayList<>();


    @FXML
    public void initialize(){

        boolean isAdmin = "ADMIN".equalsIgnoreCase(Session.getRole());
        boolean allowed = isAdmin || DataShareManagementOtpService.hasActiveAccess(Session.getUserId());
        if (!allowed) {
            new Alert(Alert.AlertType.ERROR,
                    I18n.t("err.unauthorizedFileShareManagement", "Unauthorized access."),
                    ButtonType.OK).showAndWait();
            tableFiles.setVisible(false);
            tableFiles.setManaged(false);
            if (adminSessionSettingsBox != null) {
                adminSessionSettingsBox.setVisible(false);
                adminSessionSettingsBox.setManaged(false);
            }
            if (otpRequestsPanel != null) {
                otpRequestsPanel.setVisible(false);
                otpRequestsPanel.setManaged(false);
            }
            return;
        }

        if (adminSessionSettingsBox != null && cmbSessionMinutes != null) {
            if (isAdmin) {
                adminSessionSettingsBox.setVisible(true);
                adminSessionSettingsBox.setManaged(true);
                ObservableList<Integer> mins = FXCollections.observableArrayList();
                for (int i = DataShareManagementOtpService.MIN_SESSION_MINUTES;
                     i <= DataShareManagementOtpService.MAX_SESSION_MINUTES; i++) {
                    mins.add(i);
                }
                cmbSessionMinutes.setItems(mins);
                cmbSessionMinutes.setValue(DataShareManagementOtpService.getSessionDurationMinutes());
            } else {
                adminSessionSettingsBox.setVisible(false);
                adminSessionSettingsBox.setManaged(false);
            }
        }

        if (isAdmin && otpRequestsPanel != null && tableOtpRequests != null) {
            otpRequestsPanel.setVisible(true);
            otpRequestsPanel.setManaged(true);
            setupOtpRequestsTable();
            if (txtSearchOtpRequests != null) {
                txtSearchOtpRequests.textProperty().addListener((o, oldV, newV) -> applyOtpRequestFilter());
            }
            loadOtpRequests();
        } else if (otpRequestsPanel != null) {
            otpRequestsPanel.setVisible(false);
            otpRequestsPanel.setManaged(false);
        }

        tableFiles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colFileName.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFileName()));

        colSharedBy.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getSharedBy()));

        colRole.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getRole()));

        colDate.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getDate()));

        colTime.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getTime()));

        loadFiles();

        txtSearch.textProperty().addListener((obs,oldVal,newVal)->{
            filterFiles(newVal);
        });

        addPreviewColumn();
        addDownloadColumn();
        addDeleteColumn();
    }

    @FXML
    private void handleSaveSessionMinutes() {
        if (!"ADMIN".equalsIgnoreCase(Session.getRole()) || cmbSessionMinutes == null) {
            return;
        }
        Integer v = cmbSessionMinutes.getValue();
        if (v == null) {
            return;
        }
        DataShareManagementOtpService.setSessionDurationMinutes(v);
        Alert ok = new Alert(Alert.AlertType.INFORMATION,
                I18n.t("fileShareMgmtOtpSessionSaved", "Session duration saved. New OTP logins will use this length (max 20 minutes)."),
                ButtonType.OK);
        ok.setHeaderText(null);
        ok.showAndWait();
    }

    @FXML
    private void handleRefreshOtpRequests() {
        loadOtpRequests();
    }

    private void setupOtpRequestsTable() {
        tableOtpRequests.getColumns().clear();
        tableOtpRequests.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Notification, String> colBy = new TableColumn<>(
                I18n.t("fileShareMgmtOtpRequestedBy", "Requested by"));
        colBy.setCellValueFactory(cd -> {
            Notification n = cd.getValue();
            String name = n.getTargetRef();
            if (name == null || name.isBlank()) {
                name = n.getTargetId() != null ? ("ID " + n.getTargetId()) : "";
            }
            return new javafx.beans.property.SimpleStringProperty(name);
        });

        TableColumn<Notification, String> colWhen = new TableColumn<>(
                I18n.t("fileShareMgmtOtpRequestedAt", "Requested at"));
        colWhen.setCellValueFactory(cd -> {
            String at = cd.getValue().getCreatedAt();
            return new javafx.beans.property.SimpleStringProperty(at == null ? "" : at);
        });

        TableColumn<Notification, String> colStatus = new TableColumn<>(
                I18n.t("fileShareMgmtOtpStatus", "Status"));
        colStatus.setCellValueFactory(cd -> {
            boolean pending = cd.getValue().getIsRead() == 0;
            String label = pending
                    ? I18n.t("fileShareMgmtOtpStatusPending", "Pending")
                    : I18n.t("fileShareMgmtOtpStatusHandled", "Handled");
            return new javafx.beans.property.SimpleStringProperty(label);
        });

        TableColumn<Notification, Void> colAct = new TableColumn<>(
                I18n.t("fileShareMgmtOtpActions", "Actions"));
        colAct.setCellFactory(param -> new TableCell<>() {
            private final Button btnGen = new Button(I18n.t("button.generateOtpMgmt", "Generate OTP"));
            private final Button btnDel = new Button(I18n.t("button.delete", "Delete"));
            private final HBox box = new HBox(8, btnGen, btnDel);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnDel.getStyleClass().add("delete-btn");
                btnGen.setOnAction(e -> {
                    Notification n = getTableView().getItems().get(getIndex());
                    generateOtpFromRequest(n);
                });
                btnDel.setOnAction(e -> {
                    Notification n = getTableView().getItems().get(getIndex());
                    deleteOtpRequestRow(n);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tableOtpRequests.getColumns().addAll(colBy, colWhen, colStatus, colAct);
    }

    private void loadOtpRequests() {
        if (!"ADMIN".equalsIgnoreCase(Session.getRole()) || tableOtpRequests == null) {
            return;
        }
        allOtpRequestsCache = NotificationDAO.getOtpManagementRequestsForAdmin(Session.getUserId(), 200);
        applyOtpRequestFilter();
    }

    private void applyOtpRequestFilter() {
        if (tableOtpRequests == null) {
            return;
        }
        String q = txtSearchOtpRequests != null ? txtSearchOtpRequests.getText() : "";
        if (q == null || q.isBlank()) {
            tableOtpRequests.setItems(FXCollections.observableArrayList(allOtpRequestsCache));
            return;
        }
        String kw = q.toLowerCase().trim();
        List<Notification> filtered = new ArrayList<>();
        for (Notification n : allOtpRequestsCache) {
            if (otpRequestMatchesKeyword(n, kw)) {
                filtered.add(n);
            }
        }
        tableOtpRequests.setItems(FXCollections.observableArrayList(filtered));
    }

    private boolean otpRequestMatchesKeyword(Notification n, String kw) {
        if (n == null || kw.isEmpty()) {
            return true;
        }
        String ref = n.getTargetRef() != null ? n.getTargetRef().toLowerCase() : "";
        String msg = n.getMessage() != null ? n.getMessage().toLowerCase() : "";
        String at = n.getCreatedAt() != null ? n.getCreatedAt().toLowerCase() : "";
        String idPart = n.getTargetId() != null ? String.valueOf(n.getTargetId()) : "";
        String pending = I18n.t("fileShareMgmtOtpStatusPending", "Pending").toLowerCase();
        String handled = I18n.t("fileShareMgmtOtpStatusHandled", "Handled").toLowerCase();
        return ref.contains(kw)
                || msg.contains(kw)
                || at.contains(kw)
                || idPart.contains(kw)
                || pending.contains(kw)
                || handled.contains(kw);
    }

    private void deleteOtpRequestRow(Notification notif) {
        if (!"ADMIN".equalsIgnoreCase(Session.getRole()) || notif == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(I18n.t("fileShareMgmtOtpDeleteTitle", "Delete request"));
        confirm.setHeaderText(null);
        confirm.setContentText(I18n.t("fileShareMgmtOtpDeleteConfirm",
                "Remove this OTP access request from your list?"));
        ButtonType yesBtn = new ButtonType(I18n.t("confirm", "Confirm"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType(I18n.t("cancel", "Cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(yesBtn, cancelBtn);

        if (confirm.showAndWait().orElse(cancelBtn) != yesBtn) {
            return;
        }

        boolean ok = NotificationDAO.deleteOtpManagementRequestForAdmin(notif.getId(), Session.getUserId());
        if (!ok) {
            new Alert(Alert.AlertType.WARNING,
                    I18n.t("fileShareMgmtOtpDeleteFailed", "Could not delete this request."),
                    ButtonType.OK).showAndWait();
            return;
        }

        allOtpRequestsCache.removeIf(n -> n.getId() == notif.getId());
        applyOtpRequestFilter();
        MainController.refreshNotificationBadgesNow();
    }

    private void generateOtpFromRequest(Notification notif) {
        if (!"ADMIN".equalsIgnoreCase(Session.getRole())) {
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

        MainController.refreshNotificationBadgesNow();
        loadOtpRequests();

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setHeaderText(null);
        info.setContentText(I18n.t("otpGeneratedForUser", "OTP generated for user")
                + " " + requesterName + ": " + otp);
        info.showAndWait();
    }


    // ================= LOAD FILES =================
    private void loadFiles(){

        allFiles = DataShareDAO.getAllSharedFiles();

        tableFiles.setItems(
                FXCollections.observableArrayList(allFiles)
        );
    }


    // ================= SEARCH =================
    private void filterFiles(String search){

        if(search == null || search.isEmpty()){
            tableFiles.setItems(FXCollections.observableArrayList(allFiles));
            return;
        }

        String keyword = search.toLowerCase();

        List<DataShareFile> filtered = new ArrayList<>();

        for(DataShareFile file : allFiles){

            if(file.getFileName().toLowerCase().contains(keyword)
            || file.getSharedBy().toLowerCase().contains(keyword)
            || file.getRole().toLowerCase().contains(keyword)
            || file.getDate().toLowerCase().contains(keyword)){

                filtered.add(file);
            }
        }

        tableFiles.setItems(
                FXCollections.observableArrayList(filtered)
        );
    }


    // ================= PREVIEW =================
    private void addPreviewColumn(){
         

    TableColumn<DataShareFile,Void> colPreview = new TableColumn<>(I18n.t("button.preview", "Preview"));

    colPreview.setCellFactory(param -> new TableCell<>(){

        private final Button btn = new Button(I18n.t("button.preview", "Preview"));

        {
            btn.getStyleClass().add("preview-btn");

            btn.setOnAction(e -> {

                DataShareFile file = getTableView().getItems().get(getIndex());

                Alert loading = new Alert(Alert.AlertType.INFORMATION);
                loading.setHeaderText(null);
                loading.setContentText(I18n.t("fileShareMgmt.previewLoading",
                        "Please wait while the file is loading..."));
                loading.show();

                new Thread(() -> {

                    try{

                        File f = new File(file.getFilePath());

                        if(!f.exists()){
                            javafx.application.Platform.runLater(() -> {
                                loading.close();
                                showAlert(MessageFormat.format(
                                        I18n.t("fileShareMgmt.fileNotFound", "File not found:\n{0}"),
                                        file.getFilePath()));
                            });
                            return;
                        }

                        if(!java.awt.Desktop.isDesktopSupported()){
                            javafx.application.Platform.runLater(() -> {
                                loading.close();
                                showAlert(I18n.t("fileShareMgmt.previewUnsupported",
                                        "Preview is not supported on this system."));
                            });
                            return;
                        }

                        java.awt.Desktop.getDesktop().open(f);

                        javafx.application.Platform.runLater(loading::close);

                    }catch(Exception ex){

                        javafx.application.Platform.runLater(() -> {
                            loading.close();
                            showAlert(I18n.t("fileShareMgmt.previewOpenFailed",
                                    "Cannot preview this file. Make sure a program is installed to open this file type."));
                        });

                        ex.printStackTrace();
                    }

                }).start();

            });
        }

        @Override
        protected void updateItem(Void item, boolean empty){
            super.updateItem(item, empty);
            setGraphic(empty ? null : btn);
        }
    });

    tableFiles.getColumns().add(colPreview);
}


    // ================= DOWNLOAD =================
    // ================= DOWNLOAD =================
private void addDownloadColumn(){

    TableColumn<DataShareFile,Void> colDownload = new TableColumn<>(I18n.t("download", "Download").trim());

    colDownload.setCellFactory(param -> new TableCell<>(){

        private final Button btn = new Button(I18n.t("download", "Download").trim());

        {
            btn.getStyleClass().add("download-btn");

            btn.setOnAction(e -> {

                DataShareFile file = getTableView().getItems().get(getIndex());

                try{

                    File sourceFile = new File(file.getFilePath());

                    if(!sourceFile.exists()){
                        showAlert(MessageFormat.format(
                                I18n.t("fileShareMgmt.fileNotFoundServer", "File not found on server:\n{0}"),
                                file.getFilePath()));
                        return;
                    }

                    FileChooser chooser = new FileChooser();
                    chooser.setTitle(I18n.t("fileShareMgmt.saveDialogTitle", "Save File"));

                    // IMPORTANT → keep original filename with extension
                    chooser.setInitialFileName(sourceFile.getName());

                    chooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter(I18n.t("fileType.pdf", "PDF files"), "*.pdf"),
                            new FileChooser.ExtensionFilter(I18n.t("fileType.images", "Images"), "*.png","*.jpg","*.jpeg"),
                            new FileChooser.ExtensionFilter(I18n.t("fileType.excel", "Excel files"), "*.xlsx","*.xls","*.xlsm"),
                            new FileChooser.ExtensionFilter(I18n.t("fileType.all", "All files"), "*.*")
                    );

                    File destination = chooser.showSaveDialog(tableFiles.getScene().getWindow());

                    if(destination != null){

                        Files.copy(
                                sourceFile.toPath(),
                                destination.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                        );

                        showAlert(I18n.t("fileShareMgmt.downloadSuccess", "File downloaded successfully."));

                    }

                }catch(Exception ex){
                    ex.printStackTrace();
                    showAlert(I18n.t("fileShareMgmt.downloadFailed", "Download failed."));
                }

            });
        }

        @Override
        protected void updateItem(Void item, boolean empty){
            super.updateItem(item, empty);
            setGraphic(empty ? null : btn);
        }
    });

    tableFiles.getColumns().add(colDownload);
}


    // ================= DELETE =================
    private void addDeleteColumn(){

        TableColumn<DataShareFile,Void> colDelete = new TableColumn<>(I18n.t("delete", "Delete"));

        colDelete.setCellFactory(param -> new TableCell<>(){

            private final Button btn = new Button(I18n.t("delete", "Delete"));

            {
                btn.getStyleClass().add("delete-btn");

                btn.setOnAction(e->{

                    DataShareFile file = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            I18n.t("fileShareMgmt.confirmDelete", "Delete this file?"),
                            ButtonType.YES,
                            ButtonType.NO
                    );

                    confirm.showAndWait();

                    if(confirm.getResult() == ButtonType.YES){

                        DataShareDAO.deleteFile(file.getId());

                        loadFiles();
                    }

                });
            }

            @Override
            protected void updateItem(Void item, boolean empty){
                super.updateItem(item,empty);
                setGraphic(empty ? null : btn);
            }
        });

        tableFiles.getColumns().add(colDelete);
    }


    private void showAlert(String message){

        new Alert(Alert.AlertType.INFORMATION,
                message,
                ButtonType.OK).showAndWait();
    }
    
    private Stage showLoadingDialog() {

    Label title = new Label(I18n.t("dialog.title.message", "Message"));
    title.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");

    Label msg = new Label(I18n.t("fileShareMgmt.previewLoading",
            "Please wait while the file is loading..."));
    msg.setStyle("-fx-font-size:13px;");

    ProgressBar progress = new ProgressBar();
    progress.setPrefWidth(300);
    progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

    VBox layout = new VBox(15, title, msg, progress);
    layout.setStyle(
        "-fx-padding:20;" +
        "-fx-background-color:white;" +
        "-fx-border-color:#cfd8dc;" +
        "-fx-border-radius:6;" +
        "-fx-background-radius:6;"
    );

    layout.setAlignment(Pos.CENTER);

    Scene scene = new Scene(layout);
    AppUiStyles.applyToScene(scene);

    Stage dialog = new Stage();
    dialog.setScene(scene);
    dialog.setTitle(I18n.t("dialog.title.loading", "Loading"));
    dialog.setWidth(380);
    dialog.setHeight(150);
    dialog.setResizable(false);

    return dialog;
}
    
    
    
}