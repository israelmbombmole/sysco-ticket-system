package com.app.util;

import com.app.auth.Session;
import com.app.dao.MissionDAO;
import com.app.model.FieldMission;
import com.app.model.FieldMissionAttachment;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class MissionOrdreDialogController {

    @FXML private TextField txtOrderReference;
    @FXML private DatePicker dpOrderIssueDate;
    @FXML private TextField txtOrderIssuedBy;
    @FXML private TextArea txtOrderBody;

    @FXML private ListView<File> listPendingOrderFiles;
    @FXML private ListView<FieldMissionAttachment> listDbOrderAttachments;

    @FXML private Button btnAddOrderAttachment;
    @FXML private Button btnOpenOrderAttachment;
    @FXML private Button btnRemoveOrderAttachment;
    @FXML private Button btnCancel;
    @FXML private Button btnOk;

    private Stage stage;
    private int missionId;
    private final ObservableList<File> pendingFiles = FXCollections.observableArrayList();
    private Optional<MissionOrdreDialog.Result> outcome = Optional.empty();

    public void prepare(Stage dialogStage, int missionId) {
        prepare(dialogStage, missionId, false);
    }

    public void prepare(Stage dialogStage, int missionId, boolean viewOnly) {
        this.stage = dialogStage;
        this.missionId = missionId;

        boolean editExisting = missionId > 0;
        if (listPendingOrderFiles != null) {
            listPendingOrderFiles.setManaged(!editExisting);
            listPendingOrderFiles.setVisible(!editExisting);
            listPendingOrderFiles.setItems(pendingFiles);
            listPendingOrderFiles.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(File item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getName());
                }
            });
        }
        if (listDbOrderAttachments != null) {
            listDbOrderAttachments.setManaged(editExisting);
            listDbOrderAttachments.setVisible(editExisting);
            listDbOrderAttachments.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(FieldMissionAttachment item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.getFileName());
                }
            });
        }

        if (editExisting) {
            FieldMission m = MissionDAO.findById(missionId);
            if (m != null) {
                txtOrderReference.setText(m.getOrderReference() != null ? m.getOrderReference() : "");
                dpOrderIssueDate.setValue(parseLocalDate(m.getOrderIssueDate()));
                txtOrderIssuedBy.setText(m.getOrderIssuedBy() != null ? m.getOrderIssuedBy() : "");
                txtOrderBody.setText(m.getOrderBody() != null ? m.getOrderBody() : "");
            }
            if (listDbOrderAttachments != null) {
                listDbOrderAttachments.setItems(FXCollections.observableArrayList(
                        MissionDAO.listOrderAttachments(missionId)));
            }
        } else {
            txtOrderReference.clear();
            dpOrderIssueDate.setValue(null);
            txtOrderIssuedBy.clear();
            txtOrderBody.clear();
            pendingFiles.clear();
        }

        boolean mayEditByRole = missionId <= 0
                || MissionDAO.userMayManageMission(missionId, Session.getUserId(),
                        MissionDAO.missionFullAdminAccess(missionId));
        boolean mayEdit = !viewOnly && mayEditByRole;
        txtOrderReference.setEditable(mayEdit);
        dpOrderIssueDate.setDisable(!mayEdit);
        txtOrderIssuedBy.setEditable(mayEdit);
        txtOrderBody.setEditable(mayEdit);
        if (btnAddOrderAttachment != null) {
            btnAddOrderAttachment.setDisable(!mayEdit);
        }
        if (btnRemoveOrderAttachment != null) {
            btnRemoveOrderAttachment.setDisable(!mayEdit);
        }
        if (btnOk != null) {
            btnOk.setDisable(!mayEdit);
            btnOk.setVisible(mayEdit);
            btnOk.setManaged(mayEdit);
        }
        if (viewOnly && btnCancel != null) {
            btnCancel.setText(I18n.t("close", "Close"));
        } else if (btnCancel != null) {
            btnCancel.setText(I18n.t("cancel", "Cancel"));
        }
        if (!mayEdit && !viewOnly) {
            stage.setTitle(I18n.t("missionOrdreWindowTitleReadOnly", "Mission order (read only)"));
        }
    }

    public Optional<MissionOrdreDialog.Result> getOutcome() {
        return outcome;
    }

    @FXML
    private void handleAddOrderAttachment() {
        if (missionId > 0) {
            if (!MissionDAO.userMayManageMission(missionId, Session.getUserId(),
                    MissionDAO.missionFullAdminAccess(missionId))) {
                warn(I18n.t("mission.noPermissionAct",
                        "Only the mission creator or an administrator can change mission details, the official mission order, attachments, or mark the report as submitted."));
                return;
            }
            FileChooser ch = new FileChooser();
            ch.setTitle(I18n.t("mission.chooseFile", "Choose file"));
            File f = ch.showOpenDialog(stage);
            if (f != null && MissionDAO.addOrderAttachment(missionId, f) && listDbOrderAttachments != null) {
                listDbOrderAttachments.setItems(FXCollections.observableArrayList(
                        MissionDAO.listOrderAttachments(missionId)));
            }
        } else {
            FileChooser ch = new FileChooser();
            ch.setTitle(I18n.t("mission.chooseFile", "Choose file"));
            File f = ch.showOpenDialog(stage);
            if (f != null) {
                pendingFiles.add(f);
            }
        }
    }

    @FXML
    private void handleOpenOrderAttachment() {
        if (missionId > 0) {
            FieldMissionAttachment a = listDbOrderAttachments != null
                    ? listDbOrderAttachments.getSelectionModel().getSelectedItem()
                    : null;
            if (a == null) {
                return;
            }
            File file = new File(a.getFilePath());
            if (!file.isFile()) {
                warn(I18n.t("mission.fileMissing", "File not found on disk."));
                return;
            }
            openFile(file);
        } else {
            File f = listPendingOrderFiles != null
                    ? listPendingOrderFiles.getSelectionModel().getSelectedItem()
                    : null;
            if (f == null || !f.isFile()) {
                return;
            }
            openFile(f);
        }
    }

    @FXML
    private void handleRemoveOrderAttachment() {
        if (missionId > 0) {
            FieldMissionAttachment a = listDbOrderAttachments != null
                    ? listDbOrderAttachments.getSelectionModel().getSelectedItem()
                    : null;
            if (a == null) {
                return;
            }
            if (!MissionDAO.userMayManageMission(missionId, Session.getUserId(),
                    MissionDAO.missionFullAdminAccess(missionId))) {
                warn(I18n.t("mission.noPermissionAct",
                        "Only the mission creator or an administrator can change mission details, the official mission order, attachments, or mark the report as submitted."));
                return;
            }
            MissionDAO.deleteOrderAttachment(a.getId());
            if (listDbOrderAttachments != null) {
                listDbOrderAttachments.setItems(FXCollections.observableArrayList(
                        MissionDAO.listOrderAttachments(missionId)));
            }
        } else {
            File f = listPendingOrderFiles != null
                    ? listPendingOrderFiles.getSelectionModel().getSelectedItem()
                    : null;
            if (f != null) {
                pendingFiles.remove(f);
            }
        }
    }

    @FXML
    private void handleCancel() {
        outcome = Optional.empty();
        stage.close();
    }

    @FXML
    private void handleOk() {
        String ref = txtOrderReference.getText() != null ? txtOrderReference.getText().trim() : "";
        String issuedBy = txtOrderIssuedBy.getText() != null ? txtOrderIssuedBy.getText().trim() : "";
        String body = txtOrderBody.getText() != null ? txtOrderBody.getText().trim() : "";
        if (ref.isEmpty() || dpOrderIssueDate.getValue() == null || issuedBy.isEmpty() || body.isEmpty()) {
            warn(I18n.t("mission.ordreRequiredFields",
                    "Please fill reference, issue date, issued by, and order text."));
            return;
        }
        String issueDateIso = dpOrderIssueDate.getValue().toString();
        List<File> pending = missionId <= 0 ? new ArrayList<>(pendingFiles) : List.of();
        outcome = Optional.of(new MissionOrdreDialog.Result(
                ref,
                issueDateIso,
                issuedBy,
                body,
                pending));
        stage.close();
    }

    private static void openFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void warn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static java.time.LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }
}
