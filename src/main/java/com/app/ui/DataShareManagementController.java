package com.app.ui;

import com.app.auth.Session;
import com.app.dao.DataShareDAO;
import com.app.model.DataShareFile;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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

    private List<DataShareFile> allFiles = new ArrayList<>();


    @FXML
    public void initialize(){
        
         if(!Session.getRole().equals("ADMIN")){
        new Alert(Alert.AlertType.ERROR,
                "Unauthorized access.",
                ButtonType.OK).showAndWait();

        tableFiles.setVisible(false);
        return;
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
         

    TableColumn<DataShareFile,Void> colPreview = new TableColumn<>("Preview");

    colPreview.setCellFactory(param -> new TableCell<>(){

        private final Button btn = new Button("Preview");

        {
            btn.getStyleClass().add("preview-btn");

            btn.setOnAction(e -> {

                DataShareFile file = getTableView().getItems().get(getIndex());
                
                
                

                // ============================
                // LOADING MESSAGE
                // ============================
                Alert loading = new Alert(Alert.AlertType.INFORMATION);
                loading.setHeaderText(null);
                loading.setContentText("Please wait while the file is loading...");
                loading.show();

                new Thread(() -> {

                    try{

                        File f = new File(file.getFilePath());

                        if(!f.exists()){
                            javafx.application.Platform.runLater(() -> {
                                loading.close();
                                showAlert("File not found:\n" + file.getFilePath());
                            });
                            return;
                        }

                        if(!java.awt.Desktop.isDesktopSupported()){
                            javafx.application.Platform.runLater(() -> {
                                loading.close();
                                showAlert("Preview is not supported on this system.");
                            });
                            return;
                        }

                        java.awt.Desktop.getDesktop().open(f);

                        javafx.application.Platform.runLater(loading::close);

                    }catch(Exception ex){

                        javafx.application.Platform.runLater(() -> {
                            loading.close();
                            showAlert("Cannot preview this file.\nMake sure a program is installed to open this file type.");
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

    TableColumn<DataShareFile,Void> colDownload = new TableColumn<>("Download");

    colDownload.setCellFactory(param -> new TableCell<>(){

        private final Button btn = new Button("Download");

        {
            btn.getStyleClass().add("download-btn");

            btn.setOnAction(e -> {

                DataShareFile file = getTableView().getItems().get(getIndex());

                try{

                    File sourceFile = new File(file.getFilePath());

                    if(!sourceFile.exists()){
                        showAlert("File not found on server:\n" + file.getFilePath());
                        return;
                    }

                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Save File");

                    // IMPORTANT → keep original filename with extension
                    chooser.setInitialFileName(sourceFile.getName());

                    // File type filters (fixes empty "Save as type")
                    chooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                            new FileChooser.ExtensionFilter("Images", "*.png","*.jpg","*.jpeg"),
                            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx","*.xls","*.xlsm"),
                            new FileChooser.ExtensionFilter("All Files", "*.*")
                    );

                    File destination = chooser.showSaveDialog(tableFiles.getScene().getWindow());

                    if(destination != null){

                        Files.copy(
                                sourceFile.toPath(),
                                destination.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                        );

                        showAlert("File downloaded successfully.");

                    }

                }catch(Exception ex){
                    ex.printStackTrace();
                    showAlert("Download failed.");
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

        TableColumn<DataShareFile,Void> colDelete = new TableColumn<>("Delete");

        colDelete.setCellFactory(param -> new TableCell<>(){

            private final Button btn = new Button("Delete");

            {
                btn.getStyleClass().add("delete-btn");

                btn.setOnAction(e->{

                    DataShareFile file = getTableView().getItems().get(getIndex());

                    Alert confirm = new Alert(
                            Alert.AlertType.CONFIRMATION,
                            "Delete this file?",
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

    Label title = new Label("Message");
    title.setStyle("-fx-font-size:16px; -fx-font-weight:bold;");

    Label msg = new Label("Please wait while we are loading the file...");
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

    Stage dialog = new Stage();
    dialog.setScene(scene);
    dialog.setTitle("Loading");
    dialog.setWidth(380);
    dialog.setHeight(150);
    dialog.setResizable(false);

    return dialog;
}
    
    
    
}