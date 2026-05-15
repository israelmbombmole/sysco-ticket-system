package com.app.ui;

import com.app.auth.Session;
import com.app.dao.DataShareAuditDAO;
import com.app.dao.DataShareDAO;
import com.app.dao.UserDAO;
import com.app.model.DataShareFile;
import com.app.model.User;
import com.app.util.AppUiStyles;
import com.app.util.I18n;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DataShareController {

    @FXML private TextField txtSelectedFile;
    @FXML private VBox recipientsContainer;
    @FXML private TableView<DataShareFile> tableInbox;
    @FXML private TableColumn<DataShareFile,String> colFileName;
    @FXML private TableColumn<DataShareFile,String> colSharedBy;
    @FXML private TableColumn<DataShareFile,String> colRole;
    @FXML private TableColumn<DataShareFile,String> colDate;
    @FXML private TableColumn<DataShareFile,String> colTime;
    @FXML private DatePicker dateExpiration;
    @FXML private TextField txtSearchRecipients;
    @FXML private TextField txtSearchFiles;
    

    private File selectedFile;

    private List<CheckBox> recipientCheckboxes = new ArrayList<>();
    private List<DataShareFile> inboxFiles = new ArrayList<>();
    
    
    @FXML
private ListView<String> fileList;

private List<File> selectedFiles = new ArrayList<>();

    @FXML
    public void initialize(){
        

        tableInbox.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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

        loadRecipients();
        loadInbox();

        // ================= SEARCH RECIPIENTS =================
        txtSearchRecipients.textProperty().addListener((obs, oldVal, newVal) -> {
            filterRecipients(newVal);
        });

        // ================= SEARCH FILES =================
        txtSearchFiles.textProperty().addListener((obs, oldVal, newVal) -> {
            filterFiles(newVal);
        });

        // ================= DOWNLOAD BUTTON =================
        TableColumn<DataShareFile,Void> colDownload = new TableColumn<>(I18n.t("download", "Download"));

        colDownload.setCellFactory(param -> new TableCell<>(){

            private final Button btn = new Button(I18n.t("download", "Download"));

            {
                btn.getStyleClass().add("download-btn");

               btn.setOnAction(event -> {

    DataShareFile file = getTableView().getItems().get(getIndex());
    if (!ensureOtpAccess(file)) return;

    try {

        File source = new File(file.getFilePath());
        String realFileName = source.getName();

        // detect extension
        String extension = "";
        int dot = realFileName.lastIndexOf(".");
        if (dot > 0) {
            extension = realFileName.substring(dot + 1).toLowerCase();
        }

        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(realFileName);

        // ================= FILE TYPE FILTER =================
        switch (extension) {

            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(
                                "Images (*.png, *.jpg, *.jpeg, *.gif)",
                                "*.png", "*.jpg", "*.jpeg", "*.gif"
                        )
                );
                break;

            case "pdf":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(
                                "PDF Files (*.pdf)",
                                "*.pdf"
                        )
                );
                break;

            case "doc":
            case "docx":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(
                                "Word Documents (*.doc, *.docx)",
                                "*.doc", "*.docx"
                        )
                );
                break;

            case "xls":
            case "xlsx":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(
                                "Excel Files (*.xls, *.xlsx)",
                                "*.xls", "*.xlsx"
                        )
                );
                break;

            case "txt":
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(
                                "Text Files (*.txt)",
                                "*.txt"
                        )
                );
                break;

            default:
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(
                                "All Files (*.*)",
                                "*.*"
                        )
                );
        }

        File destination = chooser.showSaveDialog(tableInbox.getScene().getWindow());

        if (destination != null) {

            Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            DataShareAuditDAO.log(
                    file.getId(),
                    file.getFileName(),
                    Session.getUserId(),
                    Session.getUsername(),
                    Session.getUserId(),
                    Session.getUsername(),
                    "DOWNLOADED"
            );

            showAlert(I18n.t("fileDownloadedSuccess", "File downloaded successfully"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

});
            }

            @Override
            protected void updateItem(Void item, boolean empty){
                super.updateItem(item,empty);
                setGraphic(empty ? null : btn);
            }
        });

        // ================= PREVIEW BUTTON =================
        TableColumn<DataShareFile,Void> colPreview = new TableColumn<>(I18n.t("button.preview", "Preview"));

        colPreview.setCellFactory(param -> new TableCell<>(){

            private final Button btn = new Button(I18n.t("button.preview", "Preview"));

            {
                btn.getStyleClass().add("preview-btn");

                btn.setOnAction(event -> {

                    DataShareFile file = getTableView().getItems().get(getIndex());
                    if (!ensureOtpAccess(file)) return;

                    try{

                        File f = new File(file.getFilePath());

                        if(!f.exists()){
                            showAlert(I18n.t("fileNotFound", "File not found"));
                            return;
                        }

                        java.awt.Desktop.getDesktop().open(f);

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty){
                super.updateItem(item,empty);
                setGraphic(empty ? null : btn);
            }
        });

        tableInbox.getColumns().add(colPreview);
        tableInbox.getColumns().add(colDownload);
    }

    // ================= LOAD RECIPIENTS =================
    private void loadRecipients(){

        List<User> users = UserDAO.getAllUsers();

        recipientsContainer.getChildren().clear();
        recipientCheckboxes.clear();

        for(User user : users){

            if(user.getId() == Session.getUserId()) continue;

            CheckBox cb = new CheckBox(user.getUsername()+" - "+user.getRole());
            cb.setUserData(user);

            recipientsContainer.getChildren().add(cb);
            recipientCheckboxes.add(cb);
        }
    }

    // ================= FILTER RECIPIENTS =================
    private void filterRecipients(String search){

        String keyword = search.toLowerCase();

        for(CheckBox cb : recipientCheckboxes){

            String text = cb.getText().toLowerCase();

            boolean visible = text.contains(keyword);

            cb.setVisible(visible);
            cb.setManaged(visible);
        }
    }

    // ================= FILE BROWSER =================
  @FXML
private void handleBrowse() {

    FileChooser chooser = new FileChooser();

    List<File> files = chooser.showOpenMultipleDialog(null);

    if (files != null) {

        for (File file : files) {
            if (file == null || !file.exists()) {
                continue;
            }
            if (file.length() > DataShareDAO.MAX_SHARE_FILE_SIZE_BYTES) {
                showAlert(I18n.t("err.fileTooLarge", "Selected file exceeds 15 MB limit.")
                        + " (" + file.getName() + ")");
                continue;
            }

            selectedFiles.add(file);

            // DISPLAY FILE NAME IN UI
            fileList.getItems().add(file.getName());

        }
    }
}

    // ================= SHARE FILE =================
    @FXML
private void handleShare(){

    if(selectedFiles == null || selectedFiles.isEmpty()){
        showAlert(I18n.t("err.selectFileFirst", "Please select a file"));
        return;
    }

    List<User> selectedUsers = new ArrayList<>();

    for(CheckBox cb : recipientCheckboxes){
        if(cb.isSelected()){
            selectedUsers.add((User) cb.getUserData());
        }
    }

    if(selectedUsers.isEmpty()){
        showAlert(I18n.t("err.selectAtLeastOneRecipient", "Please select at least one recipient."));
        return;
    }

    for (File file : selectedFiles) {
        if (file == null || !file.exists()) {
            showAlert(I18n.t("err.selectedFileMissing", "One selected file does not exist anymore."));
            return;
        }
        if (file.length() > DataShareDAO.MAX_SHARE_FILE_SIZE_BYTES) {
            showAlert(I18n.t("err.fileTooLarge", "Selected file exceeds 15 MB limit.")
                    + " (" + file.getName() + ")");
            return;
        }
    }

    final String expiration =
            dateExpiration.getValue() != null
            ? dateExpiration.getValue().toString()
            : null;

    Stage loading = showLoadingDialog();
    loading.show();

    new Thread(() -> {

        try{

            Platform.runLater(() -> progressBar.setProgress(0.1));

            // SHARE ALL FILES
            for(File file : selectedFiles){

                DataShareDAO.shareFile(
                        file.getName(),
                        file.getAbsolutePath(),
                        selectedUsers,
                        expiration
                );

            }

            Platform.runLater(() -> {

                progressBar.setProgress(1.0);

                loading.close();

                showAlert(I18n.t("filesSharedSuccess", "File(s) shared successfully"));

                fileList.getItems().clear();
                selectedFiles.clear();

                recipientCheckboxes.forEach(cb -> cb.setSelected(false));

                loadInbox();
            });

        }catch(Exception ex){

            Platform.runLater(() -> {
                loading.close();
                showAlert(I18n.t("err.shareFile", "Error while sharing file."));
            });

            ex.printStackTrace();
        }

    }).start();
}


@FXML
private void handleRemoveFile() {

    int index = fileList.getSelectionModel().getSelectedIndex();

    if (index >= 0) {

        fileList.getItems().remove(index);
        selectedFiles.remove(index);

    }
}

@FXML
private void handleDownload(){

    DataShareFile file = tableInbox.getSelectionModel().getSelectedItem();

    if(file == null){
        showAlert(I18n.t("err.selectFileToDownload", "Please select a file to download."));
        return;
    }

    if (!ensureOtpAccess(file)) return;

    try{

        File source = new File(file.getFilePath());

        String fileName = file.getFileName();

        // ADD EXTENSION IF MISSING
        if(!fileName.contains(".")){

            String ext = "";

            int dot = source.getName().lastIndexOf(".");
            if(dot > 0){
                ext = source.getName().substring(dot);
            }

            fileName = fileName + ext;
        }

        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(fileName);

        File destination = chooser.showSaveDialog(tableInbox.getScene().getWindow());

        if(destination != null){

            Files.copy(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

    }catch(Exception e){
        e.printStackTrace();
        showAlert(I18n.t("err.downloadFile", "Error while downloading file."));
    }
}


    // ================= LOAD INBOX =================
    private void loadInbox(){

        inboxFiles = DataShareDAO.getInboxFiles(Session.getUserId());

        tableInbox.setItems(
                FXCollections.observableArrayList(inboxFiles)
        );
    }

    public void focusFileById(int fileId) {
        if (inboxFiles == null || inboxFiles.isEmpty()) {
            loadInbox();
        }
        DataShareFile target = null;
        for (DataShareFile f : inboxFiles) {
            if (f.getId() == fileId) {
                target = f;
                break;
            }
        }
        if (target != null) {
            tableInbox.getSelectionModel().select(target);
            tableInbox.scrollTo(target);
        }
    }

    // ================= FILTER FILES =================
    private void filterFiles(String search){

        if(search == null || search.isEmpty()){
            tableInbox.setItems(
                    FXCollections.observableArrayList(inboxFiles)
            );
            return;
        }

        String keyword = search.toLowerCase();

        List<DataShareFile> filtered = new ArrayList<>();

        for(DataShareFile file : inboxFiles){

            if(file.getFileName().toLowerCase().contains(keyword)
            || file.getSharedBy().toLowerCase().contains(keyword)
            || file.getRole().toLowerCase().contains(keyword)
            || file.getDate().toLowerCase().contains(keyword)){

                filtered.add(file);
            }
        }

        tableInbox.setItems(
                FXCollections.observableArrayList(filtered)
        );
    }

    private void showAlert(String message){

        new Alert(Alert.AlertType.INFORMATION,
                message,
                ButtonType.OK).showAndWait();
    }
    
   

private ProgressBar progressBar;

private Stage showLoadingDialog(){

    Label title = new Label(I18n.t("loading", "Loading"));
    title.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");

    Label msg = new Label(I18n.t("shareInProgress", "Please wait while the file is being shared..."));
    msg.setStyle("-fx-font-size:13px;");

    progressBar = new ProgressBar(0);
    progressBar.setPrefWidth(320);

    VBox layout = new VBox(15, title, msg, progressBar);
    layout.setAlignment(Pos.CENTER);
    layout.setStyle("-fx-padding:20; -fx-background-color:white;");

    Scene scene = new Scene(layout);
    AppUiStyles.applyToScene(scene);

    Stage dialog = new Stage();
    dialog.setTitle(I18n.t("loading", "Loading"));
    dialog.setScene(scene);
    dialog.setWidth(380);
    dialog.setHeight(160);
    dialog.setResizable(false);

    return dialog;
}

private boolean ensureOtpAccess(DataShareFile file) {
    if (file == null) return false;
    int recipientId = Session.getUserId();

    if (DataShareDAO.isOtpVerifiedForRecipient(file.getId(), recipientId)) {
        return true;
    }

    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle(I18n.t("otpRequiredTitle", "OTP Required"));
    dialog.setHeaderText(I18n.t("otpRequiredHeader", "Enter the OTP sent with the share notification"));
    dialog.setContentText(I18n.t("otpLabel", "OTP") + ":");

    String entered = dialog.showAndWait().orElse("").trim();
    if (entered.isBlank()) {
        showAlert(I18n.t("err.otpRequired", "OTP is required to access this file."));
        return false;
    }

    boolean valid = DataShareDAO.verifyOtpForRecipient(file.getId(), recipientId, entered);
    if (!valid) {
        showAlert(I18n.t("err.invalidOrExpiredOtp", "Invalid or expired OTP."));
        return false;
    }

    return true;
}
    
    
}