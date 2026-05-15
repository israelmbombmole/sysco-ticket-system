package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierPacketDAO;
import com.app.dao.DirectionDAO;
import com.app.dao.UserDAO;
import com.app.model.CourierPacket;
import com.app.model.Direction;
import com.app.model.User;
import com.app.util.AccessContext;
import com.app.util.I18n;
import com.app.util.RoleKeyUtil;
import com.app.util.TicketUtil;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Courier management: list in scope plus edit, redirect (including resolved),
 * assign additional directions, and delete when permitted.
 */
public class CourierManagementController {

    @FXML private ComboBox<Direction> cmbFilterDirection;
    @FXML private ComboBox<String> cmbFilterStatus;
    @FXML private TableView<CourierPacket> table;
    @FXML private Button btnEdit;
    @FXML private Button btnRedirect;
    @FXML private Button btnExtraDirs;
    @FXML private Button btnDelete;
    @FXML private TableColumn<CourierPacket, String> colRef;
    @FXML private TableColumn<CourierPacket, String> colTitle;
    @FXML private TableColumn<CourierPacket, String> colSender;
    @FXML private TableColumn<CourierPacket, String> colPri;
    @FXML private TableColumn<CourierPacket, String> colStatus;
    @FXML private TableColumn<CourierPacket, String> colDir;
    @FXML private TableColumn<CourierPacket, String> colExtraDirs;
    @FXML private TableColumn<CourierPacket, String> colSous;
    @FXML private TableColumn<CourierPacket, String> colTicket;
    @FXML private TableColumn<CourierPacket, String> colCreated;

    private String viewRole;
    private String roleKey;
    private Integer myDirectionId;
    private Integer mySousId;

    @FXML
    private void initialize() {
        viewRole = Session.getRole() == null ? "" : Session.getRole();
        roleKey = RoleKeyUtil.normalizeForScope(viewRole);
        Integer[] ds = UserDAO.getDirectionSousForUser(Session.getUserId());
        myDirectionId = ds[0] != null ? ds[0] : Session.getDirectionId();
        mySousId = ds[1];
        User uRow = UserDAO.findById(Session.getUserId());
        if (uRow != null) {
            if (uRow.getDirectionId() != null) {
                myDirectionId = uRow.getDirectionId();
            }
            if (uRow.getSousDirectionId() != null) {
                mySousId = uRow.getSousDirectionId();
            }
        }

        cmbFilterStatus.setItems(FXCollections.observableArrayList("ALL", "OPEN", "RESOLVED"));
        cmbFilterStatus.getSelectionModel().selectFirst();
        cmbFilterStatus.setConverter(new StringConverter<>() {
            @Override
            public String toString(String c) {
                if (c == null) {
                    return "";
                }
                return switch (c) {
                    case "ALL" -> I18n.t("all", "All");
                    case "OPEN" -> I18n.t("courierFilterOpen", "Open");
                    case "RESOLVED" -> I18n.t("resolved", "Resolved");
                    default -> c;
                };
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        });

        colRef.setCellValueFactory(new PropertyValueFactory<>("refCode"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colSender.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getSender())));
        colPri.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getPriority())));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(translateStatus(c.getValue().getStatus())));
        colDir.setCellValueFactory(new PropertyValueFactory<>("targetDirectionName"));
        colExtraDirs.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getExtraDirectionNames())));
        colSous.setCellValueFactory(new PropertyValueFactory<>("targetSousDirectionName"));
        colTicket.setCellValueFactory(c -> {
            Integer tid = c.getValue().getLinkedTicketId();
            return new SimpleStringProperty(tid != null ? TicketUtil.formatTicketRef(tid) : "");
        });
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getCreatedAt())));

        cmbFilterDirection.setConverter(new StringConverter<>() {
            @Override
            public String toString(Direction d) {
                return d == null ? "" : d.getName();
            }

            @Override
            public Direction fromString(String s) {
                return null;
            }
        });
        setupFilterDirectionCombo();

        table.setRowFactory(tv -> {
            TableRow<CourierPacket> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (row.isEmpty() || ev.getClickCount() != 2) {
                    return;
                }
                CourierPacket p = row.getItem();
                if (p != null) {
                    openCourierDetails(p.getId());
                }
            });
            return row;
        });

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> updateActionButtons());

        cmbFilterDirection.setOnAction(e -> refresh());
        cmbFilterStatus.setOnAction(e -> refresh());
        refresh();
    }

    private Map<Integer, Direction> directionIndex() {
        Map<Integer, Direction> map = new HashMap<>();
        for (Direction d : DirectionDAO.getAllDirections()) {
            map.put(d.getId(), d);
        }
        return map;
    }

    private String directionName(int directionId, Map<Integer, Direction> ix) {
        Direction d = ix.get(directionId);
        return d != null ? d.getName() : ("#" + directionId);
    }

    private void updateActionButtons() {
        CourierPacket p = table.getSelectionModel().getSelectedItem();
        String listRole = RoleKeyUtil.listRoleKey(viewRole);
        boolean adm = p != null && CourierPacketDAO.mayAdministerCourier(p, listRole, myDirectionId, mySousId);
        if (btnEdit != null) {
            btnEdit.setDisable(!adm);
        }
        if (btnRedirect != null) {
            btnRedirect.setDisable(!adm);
        }
        if (btnExtraDirs != null) {
            btnExtraDirs.setDisable(!adm);
        }
        if (btnDelete != null) {
            btnDelete.setDisable(!adm);
        }
    }

    private void setupFilterDirectionCombo() {
        if (cmbFilterDirection == null) {
            return;
        }
        String rk = roleKey == null ? "" : roleKey;
        boolean scopeToMyDirection = myDirectionId != null && myDirectionId > 0
                && ("DIRECTEUR".equals(rk)
                || "SECRETAIRE".equals(rk)
                || ("ADMIN".equals(rk) && !AccessContext.isSystemSuperAdmin()));
        ObservableList<Direction> dirs = DirectionDAO.getAllDirections();
        Direction allDir = new Direction(-1, I18n.t("allDirections", "All directions"));
        if (scopeToMyDirection) {
            java.util.List<Integer> cl = myDirectionId != null && myDirectionId > 0
                    ? DirectionDAO.getDirectionIdCluster(myDirectionId)
                    : List.of();
            ObservableList<Direction> scoped = FXCollections.observableArrayList();
            scoped.add(allDir);
            for (Direction d : dirs) {
                if (cl.contains(d.getId())) {
                    scoped.add(d);
                }
            }
            cmbFilterDirection.setItems(scoped);
        } else {
            cmbFilterDirection.setItems(dirs);
            cmbFilterDirection.getItems().add(0, allDir);
        }
        cmbFilterDirection.getSelectionModel().selectFirst();
    }

    @FXML
    private void refresh() {
        String st = cmbFilterStatus.getSelectionModel().getSelectedItem();
        String f = null;
        if ("OPEN".equals(st)) {
            f = "OPEN";
        } else if ("RESOLVED".equals(st)) {
            f = CourierPacket.ST_RESOLVED;
        }
        Direction dsel = cmbFilterDirection.getSelectionModel().getSelectedItem();
        Integer fd = dsel != null && dsel.getId() > 0 ? dsel.getId() : null;
        String listRole = RoleKeyUtil.listRoleKey(viewRole);
        List<CourierPacket> rows = CourierPacketDAO.listForScope(listRole, myDirectionId, mySousId, fd, f);
        table.setItems(FXCollections.observableArrayList(rows));
        if (rows.isEmpty()
                && ("SECRETAIRE".equals(listRole) || "DIRECTEUR".equals(listRole))
                && (myDirectionId == null || myDirectionId <= 0)) {
            table.setPlaceholder(
                    new Label(I18n.t("courierTableNoDirectionOnUser",
                            "No direction is set on this user account. In User management, assign a « Direction » so courriers for that service appear here."))
            );
        } else if (rows.isEmpty()
                && myDirectionId != null
                && ("DIRECTEUR".equals(listRole)
                        || ("ADMIN".equals(listRole) && !AccessContext.isSystemSuperAdmin())
                        || "SECRETAIRE".equals(listRole))) {
            table.setPlaceholder(
                    new Label(I18n.t("courierTableEmptyDirecteur",
                            "No courrier for this direction. If you expect items here, check that the courrier was sent to the same direction as in your user profile (or a matching duplicate)."))
            );
        } else {
            table.setPlaceholder(new Label(I18n.t("noTableContent", "No content in table")));
        }
        updateActionButtons();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String translateStatus(String s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case CourierPacket.ST_AWAITING_DIRECTION -> I18n.t("courierStAwaitingDir", "Not routed to a direction");
            case CourierPacket.ST_REGISTERED -> I18n.t("courierStRegistered", "Registered");
            case CourierPacket.ST_DIRECTED -> I18n.t("courierStDirected", "Directed");
            case CourierPacket.ST_SOUS_ASSIGNED -> I18n.t("courierStSous", "Sous-direction set");
            case CourierPacket.ST_IN_PROGRESS -> I18n.t("courierStProgress", "In progress");
            case CourierPacket.ST_RESOLVED -> I18n.t("courierStResolved", "Resolved");
            default -> s;
        };
    }

    private void openCourierDetails(int packetId) {
        MainController.loadPage("CourierDetails.fxml", c -> {
            if (c instanceof CourierDetailsController d) {
                d.setPacketId(packetId);
            }
        });
    }

    @FXML
    private void handleEditSelected() {
        CourierPacket row = table.getSelectionModel().getSelectedItem();
        String listRole = RoleKeyUtil.listRoleKey(viewRole);
        if (row == null || !CourierPacketDAO.mayAdministerCourier(row, listRole, myDirectionId, mySousId)) {
            return;
        }
        CourierPacket p = CourierPacketDAO.getById(row.getId());
        if (p == null) {
            return;
        }

        DialogPane pane = new DialogPane();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField txtTitle = new TextField(p.getTitle());
        TextArea txtDesc = new TextArea(p.getDescription());
        txtDesc.setPrefRowCount(4);
        txtDesc.setWrapText(true);
        TextField txtSender = new TextField(p.getSender());
        ComboBox<String> cmbPri = new ComboBox<>(FXCollections.observableArrayList("LOW", "MEDIUM", "HIGH", "CRITICAL"));
        String pr = p.getPriority() != null ? p.getPriority().trim() : "MEDIUM";
        if (!cmbPri.getItems().contains(pr)) {
            cmbPri.getItems().add(pr);
        }
        cmbPri.getSelectionModel().select(pr);
        TextField txtReg = new TextField(p.getRegistrationDate());
        TextField txtAttach = new TextField(p.getAttachmentPath());

        int r = 0;
        grid.addRow(r++, new Label(I18n.t("title", "Title")), txtTitle);
        grid.addRow(r++, new Label(I18n.t("description", "Description")), txtDesc);
        grid.addRow(r++, new Label(I18n.t("courierFieldSender", "Sender")), txtSender);
        grid.addRow(r++, new Label(I18n.t("priority", "Priority")), cmbPri);
        grid.addRow(r++, new Label(I18n.t("courierFieldRegDate", "Registered on")), txtReg);
        grid.addRow(r++, new Label(I18n.t("courierMgmtAttachmentPath", "Attachment path")), txtAttach);

        pane.setContent(grid);
        pane.getButtonTypes().setAll(new ButtonType(I18n.t("save", "Save"), ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);

        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle(I18n.t("courierMgmtEditTitle", "Edit courier"));
        dlg.setDialogPane(pane);

        dlg.setResultConverter(btn -> btn != null && btn.getButtonData() == ButtonBar.ButtonData.OK_DONE);

        Optional<Boolean> res = dlg.showAndWait();
        if (!res.orElse(false)) {
            return;
        }
        try {
            CourierPacketDAO.adminUpdatePacket(
                    p.getId(),
                    txtTitle.getText(),
                    txtDesc.getText(),
                    txtSender.getText(),
                    cmbPri.getSelectionModel().getSelectedItem(),
                    txtReg.getText(),
                    txtAttach.getText());
            alert(Alert.AlertType.INFORMATION, I18n.t("courierMgmtSaved", "Saved."));
            refresh();
        } catch (SQLException | IllegalArgumentException ex) {
            alert(Alert.AlertType.ERROR,
                    ex.getLocalizedMessage() != null ? ex.getLocalizedMessage()
                            : I18n.t("courierMgmtSaveFail", "Could not save."));
        }
    }

    @FXML
    private void handleRedirectSelected() {
        CourierPacket row = table.getSelectionModel().getSelectedItem();
        String listRole = RoleKeyUtil.listRoleKey(viewRole);
        if (row == null || !CourierPacketDAO.mayAdministerCourier(row, listRole, myDirectionId, mySousId)) {
            return;
        }

        DialogPane pane = new DialogPane();
        ComboBox<Direction> cmbDir = new ComboBox<>(DirectionDAO.getAllDirections());
        Direction current = directionIndex().get(row.getTargetDirectionId() != null ? row.getTargetDirectionId() : -1);
        if (current != null) {
            for (Direction d : cmbDir.getItems()) {
                if (d.getId() == current.getId()) {
                    cmbDir.getSelectionModel().select(d);
                    break;
                }
            }
        }
        CheckBox chkReopen = new CheckBox(I18n.t(
                "courierMgmtRedirectReopen",
                "If resolved: reopen workflow (direction set; resolution cleared)."));
        chkReopen.setWrapText(true);
        chkReopen.setSelected(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label(I18n.t("direction", "Direction")), cmbDir);
        grid.addRow(1, chkReopen);

        pane.setContent(grid);
        pane.getButtonTypes().setAll(new ButtonType(I18n.t("apply", "Apply"), ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);

        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle(I18n.t("courierMgmtRedirectTitle", "Redirect primary direction"));
        dlg.setDialogPane(pane);
        dlg.setResultConverter(btn -> btn != null && btn.getButtonData() == ButtonBar.ButtonData.OK_DONE);

        Optional<Boolean> res = dlg.showAndWait();
        if (!res.orElse(false)) {
            return;
        }
        Direction sel = cmbDir.getSelectionModel().getSelectedItem();
        if (sel == null || sel.getId() <= 0) {
            alert(Alert.AlertType.WARNING, I18n.t("courierMgmtSelectDirection", "Select a direction."));
            return;
        }
        try {
            CourierPacketDAO.adminRedirectPrimary(row.getId(), sel.getId(), Session.getUserId(), chkReopen.isSelected());
            alert(Alert.AlertType.INFORMATION, I18n.t("courierMgmtRedirectDone", "Primary direction updated."));
            refresh();
        } catch (SQLException ex) {
            alert(Alert.AlertType.ERROR,
                    ex.getLocalizedMessage() != null ? ex.getLocalizedMessage()
                            : I18n.t("courierMgmtRedirectFail", "Could not redirect."));
        }
    }

    @FXML
    private void handleExtraDirsSelected() {
        CourierPacket row = table.getSelectionModel().getSelectedItem();
        String listRole = RoleKeyUtil.listRoleKey(viewRole);
        if (row == null || !CourierPacketDAO.mayAdministerCourier(row, listRole, myDirectionId, mySousId)) {
            return;
        }
        int packetId = row.getId();

        DialogPane pane = new DialogPane();
        Map<Integer, Direction> ix = directionIndex();

        ObservableList<Integer> extras = FXCollections.observableArrayList(CourierPacketDAO.listExtraDirectionIds(packetId));
        ListView<Integer> lst = new ListView<>(extras);
        lst.setPrefHeight(180);
        lst.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Integer directionId, boolean empty) {
                super.updateItem(directionId, empty);
                if (empty || directionId == null) {
                    setText(null);
                } else {
                    setText(directionName(directionId, ix));
                }
            }
        });

        ComboBox<Direction> cmbAdd = new ComboBox<>();
        Runnable refillAddCombo = () -> {
            ObservableList<Direction> opts = FXCollections.observableArrayList();
            Integer prim = CourierPacketDAO.getById(packetId).getTargetDirectionId();
            Set<Integer> blocked = new HashSet<>(extras);
            if (prim != null) {
                blocked.add(prim);
            }
            for (Direction d : DirectionDAO.getAllDirections()) {
                if (!blocked.contains(d.getId())) {
                    opts.add(d);
                }
            }
            cmbAdd.setItems(opts);
            if (!opts.isEmpty()) {
                cmbAdd.getSelectionModel().selectFirst();
            }
        };
        refillAddCombo.run();

        Button btnRm = new Button(I18n.t("remove", "Remove"));
        btnRm.setOnAction(ev -> {
            Integer id = lst.getSelectionModel().getSelectedItem();
            if (id == null) {
                return;
            }
            try {
                CourierPacketDAO.removeExtraDirection(packetId, id, Session.getUserId());
                extras.remove(id);
                refillAddCombo.run();
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR,
                        ex.getLocalizedMessage() != null ? ex.getLocalizedMessage()
                                : I18n.t("courierMgmtExtraFail", "Could not update."));
            }
        });

        Button btnAdd = new Button(I18n.t("courierMgmtAddExtra", "Add direction"));
        btnAdd.setStyle("-fx-font-weight:bold;");
        btnAdd.setOnAction(ev -> {
            Direction d = cmbAdd.getSelectionModel().getSelectedItem();
            if (d == null || d.getId() <= 0) {
                return;
            }
            try {
                CourierPacketDAO.addExtraDirection(packetId, d.getId(), Session.getUserId());
                extras.add(d.getId());
                extras.sort(Integer::compareTo);
                refillAddCombo.run();
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR,
                        ex.getLocalizedMessage() != null ? ex.getLocalizedMessage()
                                : I18n.t("courierMgmtExtraFail", "Could not update."));
            } catch (IllegalStateException ex) {
                alert(Alert.AlertType.WARNING,
                        I18n.t("courierMgmtExtraDup", "That direction is already primary or duplicate."));
            }
        });

        Label lblHint = new Label(I18n.t("courierMgmtExtraDirsHint",
                "Same courier remains one record; listed directions see it in their courier list (in addition to the primary direction)."));
        lblHint.setWrapText(true);
        lblHint.setMaxWidth(420);
        HBox addRow = new HBox(10, cmbAdd, btnAdd);
        VBox vbox = new VBox(12);
        vbox.setPadding(new Insets(16));
        vbox.getChildren().addAll(lblHint, lst, addRow, btnRm);

        pane.setContent(vbox);
        pane.getButtonTypes().setAll(new ButtonType(I18n.t("close", "Close"), ButtonBar.ButtonData.CANCEL_CLOSE));

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(I18n.t("courierMgmtExtraDirsTitle", "Additional directions"));
        dlg.setDialogPane(pane);
        dlg.showAndWait();
        refresh();
    }

    @FXML
    private void handleDeleteSelected() {
        CourierPacket row = table.getSelectionModel().getSelectedItem();
        String listRole = RoleKeyUtil.listRoleKey(viewRole);
        if (row == null || !CourierPacketDAO.mayAdministerCourier(row, listRole, myDirectionId, mySousId)) {
            return;
        }
        Alert c = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.t(
                        "courierMgmtConfirmDelete",
                        "Permanently delete this courier record and its history?"),
                ButtonType.OK, ButtonType.CANCEL);
        c.setTitle(I18n.t("confirm", "Confirm"));
        Optional<ButtonType> r = c.showAndWait();
        if (!r.isPresent() || r.get() != ButtonType.OK) {
            return;
        }
        if (CourierPacketDAO.adminDeletePacket(row.getId())) {
            alert(Alert.AlertType.INFORMATION, I18n.t("courierMgmtDeleted", "Deleted."));
            refresh();
        } else {
            alert(Alert.AlertType.ERROR, I18n.t("courierMgmtDeleteFail", "Could not delete."));
        }
    }

    private void alert(Alert.AlertType type, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
