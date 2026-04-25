package com.app.controller;

import com.app.auth.Session;
import com.app.dao.AttachmentDAO;
import com.app.dao.TicketDAO;
import com.app.service.ExcelService;
import com.app.model.Department;
import com.app.model.Ticket;
import com.app.model.User;
import com.app.util.LanguageManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class UserExcelEntryController {

    @FXML private DatePicker dateEnreg;
    @FXML private TextField expediteur;
    @FXML private TextArea objet;
    @FXML private ComboBox<String> cmbPriority;

    @FXML private ListView<String> attachmentList;
    @FXML private Label message;
    @FXML private Label lblSaveStatus;
    @FXML private Button btnSave;
    @FXML private ProgressBar progressSave;

    private List<File> selectedFiles = new ArrayList<>();
    private Ticket editingTicket = null;

    // =========================
    // INITIALIZE
    // =========================
    @FXML
    public void initialize() {

        // ✅ AUTO SYSTEM DATE
        dateEnreg.setValue(LocalDate.now());
        dateEnreg.setDisable(true);

        cmbPriority.getItems().addAll(
                "LOW", "MEDIUM", "HIGH", "CRITICAL"
        );
        cmbPriority.setValue("MEDIUM");
    }

    // =========================
    // FILE BROWSE
    // =========================
    @FXML
    private void handleBrowseFiles() {

        FileChooser chooser = new FileChooser();

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Allowed Files",
                        "*.png","*.jpg","*.jpeg",
                        "*.pdf","*.docx","*.xlsx","*.txt"
                )
        );

        List<File> files =
                chooser.showOpenMultipleDialog(btnSave.getScene().getWindow());

        if (files != null) {

            selectedFiles.addAll(files);

            attachmentList.getItems().clear();

            for (File f : selectedFiles) {
                attachmentList.getItems().add(f.getName());
            }
        }
    }

    // =========================
    // SAVE
    // =========================
    @FXML
    private void handleSave() {

        lblSaveStatus.setText("⏳ " + LanguageManager.getBundle().getString("userExcelSaving"));
        progressSave.setVisible(true);
        lblSaveStatus.setVisible(true);
        btnSave.setDisable(true);

        new Thread(() -> {

            try {

                // ✅ VALIDATION
                if (expediteur.getText().isEmpty() ||
                        objet.getText().isEmpty()) {

                    Platform.runLater(() -> {
                        message.setText(LanguageManager.getBundle().getString("errFillRequiredFields"));
                        progressSave.setVisible(false);
                        btnSave.setDisable(false);
                    });
                    return;
                }

                Platform.runLater(() -> progressSave.setProgress(0.2));

                String title = expediteur.getText().trim();
                String description = objet.getText().trim();
                String priority = cmbPriority.getValue();

                if (title.isEmpty()) {
                    title = "External Request";
                }

                // =========================
                // CREATE TICKET (NO ASSIGN)
                // =========================
                Department department = new Department();
                department.setId(1); // default

                Platform.runLater(() -> {
                    lblSaveStatus.setText("⏳ " + LanguageManager.getBundle().getString("userExcelCreatingTicket"));
                    progressSave.setProgress(0.4);
                });

                int ticketId;

                if (editingTicket != null) {

                    TicketDAO.updateTicket(
                            editingTicket.getId(),
                            title,
                            description,
                            priority
                    );

                    ticketId = editingTicket.getId();

                } else {

                    List<User> users = new ArrayList<>();

User currentUser = new User();
currentUser.setId(Session.getUserId());
currentUser.setUsername(Session.getUsername());

users.add(currentUser);

ticketId = TicketDAO.createTicket(
        title,
        description,
        priority,
        department,
        null,
        null,
        "INTERNAL",
        users   // ✅ FIX
);
                }

                if (ticketId == -1) {
                    throw new RuntimeException(LanguageManager.getBundle().getString("errTicketCreationFailed"));
                }

                Platform.runLater(() -> progressSave.setProgress(0.6));

                // =========================
                // SAVE ATTACHMENTS
                // =========================
                if (!selectedFiles.isEmpty()) {

                    File uploadDir = new File("uploads");
                    if (!uploadDir.exists()) uploadDir.mkdirs();

                    for (File file : selectedFiles) {

                        String fileName = System.currentTimeMillis() + "_" + file.getName();
                        File dest = new File(uploadDir, fileName);

                        Files.copy(
                                file.toPath(),
                                dest.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                        );

                        AttachmentDAO.addAttachment(
                                ticketId,
                                file.getName(),
                                dest.getAbsolutePath(),
                                Files.probeContentType(dest.toPath())
                        );
                    }
                }

                Platform.runLater(() -> progressSave.setProgress(1.0));

                // =========================
                // CLEAR FORM
                // =========================
                Platform.runLater(() -> {

                    expediteur.clear();
                    objet.clear();
                    cmbPriority.setValue("MEDIUM");

                    selectedFiles.clear();
                    attachmentList.getItems().clear();

                    lblSaveStatus.setText("✔ " + LanguageManager.getBundle().getString("userExcelSaved"));
                    lblSaveStatus.setStyle("-fx-text-fill:green;");

                    progressSave.setVisible(false);
                    btnSave.setDisable(false);
                });

            } catch (Exception e) {

                e.printStackTrace();

                Platform.runLater(() -> {
                    message.setText(LanguageManager.getBundle().getString("dialogError") + ": " + e.getMessage());
                    progressSave.setVisible(false);
                    btnSave.setDisable(false);
                });
            }

        }).start();
    }

    // =========================
    // REMOVE FILE
    // =========================
    @FXML
    private void handleRemoveFile() {

        int index = attachmentList.getSelectionModel().getSelectedIndex();

        if (index >= 0) {
            selectedFiles.remove(index);
            attachmentList.getItems().remove(index);
        }
    }

    // =========================
    // LOAD FOR EDIT
    // =========================
    public void loadTicket(Ticket ticket) {

        if (ticket == null) return;

        editingTicket = ticket;

        expediteur.setText(ticket.getTitle());
        objet.setText(ticket.getDescription());

        if (ticket.getPriority() != null) {
            cmbPriority.setValue(ticket.getPriority());
        }

        if (ticket.getCreatedAt() != null) {
            dateEnreg.setValue(
                    LocalDate.parse(ticket.getCreatedAt().substring(0, 10))
            );
        }

        // Load attachments
        selectedFiles.clear();
        attachmentList.getItems().clear();

        List<String> files = ticket.getAttachments();

        if (files != null) {
            for (String file : files) {
                File f = new File(file);
                attachmentList.getItems().add(f.getName());
                selectedFiles.add(f);
            }
        }
    }
}