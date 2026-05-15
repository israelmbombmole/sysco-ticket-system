package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierPacketDAO;
import com.app.dao.DirectionDAO;
import com.app.dao.UserDAO;
import com.app.model.CourierJourneyLine;
import com.app.model.CourierPacket;
import com.app.model.Direction;
import com.app.model.SousDirection;
import com.app.model.User;
import com.app.util.AccessContext;
import com.app.util.TicketUtil;
import com.app.util.I18n;
import com.app.util.RoleKeyUtil;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

    /**
     * Physical courier: registration, direction routing, assignments, journey timeline, notifications and ticket link.
     */
public class CourierController {

    /** Roles that may register courrier and set the first direction (same scope as intake clerk + direction staff). */
    private boolean mayIntakeCourierMail() {
        return "COURIER".equals(roleKey)
                || "ADMIN".equals(roleKey)
                || "DIRECTEUR".equals(roleKey)
                || "SECRETAIRE".equals(roleKey);
    }

    @FXML private ComboBox<Direction> cmbFilterDirection;
    @FXML private ComboBox<String> cmbFilterStatus;
    @FXML private DatePicker dpRegDate;
    @FXML private TextField txtSender;
    @FXML private TextField txtObject;
    @FXML private TextArea txtNewDesc;
    @FXML private ComboBox<String> cmbPriority;
    @FXML private TextField txtAttachment;
    @FXML private TableView<CourierPacket> table;
    @FXML private TableColumn<CourierPacket, String> colRef;
    @FXML private TableColumn<CourierPacket, String> colTitle;
    @FXML private TableColumn<CourierPacket, String> colSender;
    @FXML private TableColumn<CourierPacket, String> colPri;
    @FXML private TableColumn<CourierPacket, String> colStatus;
    @FXML private TableColumn<CourierPacket, String> colDir;
    @FXML private TableColumn<CourierPacket, String> colSous;
    @FXML private TableColumn<CourierPacket, String> colTicket;
    @FXML private TableColumn<CourierPacket, String> colCreated;
    @FXML private ListView<CourierJourneyLine> listJourney;
    @FXML private ComboBox<Direction> cmbTargetDirection;
    @FXML private ComboBox<SousDirection> cmbTargetSous;
    @FXML private ComboBox<User> cmbAssignSousDir;
    @FXML private ComboBox<User> cmbAssignInsp;
    @FXML private ComboBox<User> cmbAssignCtrl;
    @FXML private ComboBox<User> cmbAssignVer;
    @FXML private Button btnSetDirection;
    @FXML private Button btnSetSous;
    @FXML private Button btnAssSousDir;
    @FXML private Button btnAssInsp;
    @FXML private Button btnAssCtrl;
    @FXML private Button btnAssVer;
    @FXML private Button btnResolve;
    @FXML private Button btnGrantSecret;
    @FXML private Button btnEditMyPacket;
    @FXML private TitledPane titledRegister;
    @FXML private Label lblDir;
    @FXML private Label lblSous;
    @FXML private Label lblSdir;
    @FXML private Label lblInsp;
    @FXML private Label lblCtrl;
    @FXML private Label lblVer;
    @FXML private HBox boxFilterBar;
    @FXML private Label lblSousHint;
    @FXML private ScrollPane scrollParcours;

    private String viewRole;
    /** Locale-safe, accent-stripped key matching DB roles: SECRETAIRE, SOUS-DIRECTEUR, etc. */
    private String roleKey;
    private Integer myDirectionId;
    private Integer mySousId;
    private File pendingAttachmentFile;
    private Integer editingPacketId;

    @FXML
    public void initialize() {
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
        editingPacketId = null;
        if (cmbPriority != null) {
            cmbPriority.setItems(FXCollections.observableArrayList("LOW", "MEDIUM", "HIGH", "CRITICAL"));
            cmbPriority.getSelectionModel().select("MEDIUM");
        }
        if (dpRegDate != null) {
            dpRegDate.setValue(LocalDate.now());
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
        if (colSender != null) {
            colSender.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getSender())));
        }
        if (colPri != null) {
            colPri.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getPriority())));
        }
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(translateStatus(c.getValue().getStatus())));
        colDir.setCellValueFactory(new PropertyValueFactory<>("targetDirectionName"));
        colSous.setCellValueFactory(new PropertyValueFactory<>("targetSousDirectionName"));
        if (colTicket != null) {
            colTicket.setCellValueFactory(
                    c -> {
                        Integer tid = c.getValue().getLinkedTicketId();
                        return new SimpleStringProperty(
                                tid != null ? TicketUtil.formatTicketRef(tid) : "");
                    });
        }
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getCreatedAt())));

        listJourney.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CourierJourneyLine line, boolean empty) {
                super.updateItem(line, empty);
                if (empty || line == null) {
                    setText(null);
                } else {
                    setText(line.getAtTime() + " — " + line.getText());
                }
            }
        });

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

        cmbTargetDirection.setItems(DirectionDAO.getAllDirections());
        cmbTargetSous.setItems(FXCollections.observableArrayList());
        cmbTargetDirection.setOnAction(e -> {
            Direction d = cmbTargetDirection.getSelectionModel().getSelectedItem();
            if (d != null) {
                cmbTargetSous.setItems(DirectionDAO.getSousDirectionsForDirection(d.getId()));
            } else {
                cmbTargetSous.setItems(FXCollections.observableArrayList());
            }
        });
        StringConverter<User> uconv = new StringConverter<>() {
            @Override
            public String toString(User u) {
                return u == null ? "" : u.getUsername() + " (" + u.getRole() + ")";
            }
            @Override
            public User fromString(String s) {
                return null;
            }
        };
        cmbAssignSousDir.setConverter(uconv);
        cmbAssignInsp.setConverter(uconv);
        cmbAssignCtrl.setConverter(uconv);
        cmbAssignVer.setConverter(uconv);

        table.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> onSelect(p));
        table.setRowFactory(tv -> {
            TableRow<CourierPacket> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (row.isEmpty() || event.getClickCount() != 2) {
                    return;
                }
                CourierPacket p = row.getItem();
                if (p != null) {
                    openCourierDetails(p.getId());
                }
            });
            return row;
        });
        cmbFilterDirection.setOnAction(e -> refresh());
        cmbFilterStatus.setOnAction(e -> refresh());

        boolean isCourier = "COURIER".equals(roleKey);
        // Direction head and secretary register mail too; ADMIN and dedicated COURIER role keep full intake.
        boolean showRegister = isCourier
                || "ADMIN".equals(roleKey)
                || "DIRECTEUR".equals(roleKey)
                || "SECRETAIRE".equals(roleKey);
        if (titledRegister != null) {
            titledRegister.setVisible(showRegister);
            titledRegister.setManaged(showRegister);
        }
        if (boxFilterBar != null) {
            boolean showBar = isCourier
                    || "SECRETAIRE".equals(roleKey)
                    || "DIRECTEUR".equals(roleKey)
                    || "ADMIN".equals(roleKey);
            boxFilterBar.setVisible(showBar);
            boxFilterBar.setManaged(showBar);
        }
        applyActionVisibility();
        refresh();
    }

    /**
     * Directeur and direction-scoped admin see the same courrier rows as each other for that direction;
     * limit the direction filter to "all in my scope" + my direction so the UI matches the query.
     */
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
            cmbFilterDirection.setDisable(false);
        } else {
            cmbFilterDirection.setItems(dirs);
            cmbFilterDirection.getItems().add(0, allDir);
            cmbFilterDirection.setDisable(false);
        }
        cmbFilterDirection.getSelectionModel().selectFirst();
    }

    @FXML
    private void onAttachBrowse() {
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.t("courierAttachment", "Attachment"));
        File f = ch.showOpenDialog(table.getScene() != null ? table.getScene().getWindow() : null);
        if (f != null) {
            pendingAttachmentFile = f;
            if (txtAttachment != null) {
                txtAttachment.setText(f.getAbsolutePath());
            }
        }
    }

    @FXML
    private void onAttachRemove() {
        pendingAttachmentFile = null;
        if (txtAttachment != null) {
            txtAttachment.clear();
        }
    }

    @FXML
    private void onGrantSecretDelegate() {
        CourierPacket p = table.getSelectionModel().getSelectedItem();
        if (p == null || p.getTargetDirectionId() == null) {
            return;
        }
        if (!"DIRECTEUR".equals(roleKey) && !"ADMIN".equals(roleKey)) {
            return;
        }
        if (p.getTargetSousDirectionId() != null) {
            return;
        }
        if (p.isSecretaireMayRouteSous()) {
            return;
        }
        if ("DIRECTEUR".equals(roleKey)
                && (myDirectionId == null || !isPacketInMyDirectionCluster(p))) {
            err("err.courierDir", I18n.t("err.courierNotYourDirection", "Not in your direction."));
            return;
        }
        try {
            CourierPacketDAO.setSecretaireMayRouteSous(p.getId(), p.getTargetDirectionId(), Session.getUserId());
            refresh();
            selectById(p.getId());
            MainController.refreshNotificationBadgesNow();
        } catch (Exception e) {
            e.printStackTrace();
            err("err.courierSave", e.getMessage() != null ? e.getMessage() : "Error");
        }
    }

    @FXML
    private void onEditMyPacket() {
        CourierPacket p = table.getSelectionModel().getSelectedItem();
        if (p == null || p.getCreatedBy() != Session.getUserId()) {
            return;
        }
        if (CourierPacket.ST_RESOLVED.equalsIgnoreCase(p.getStatus())) {
            return;
        }
        editingPacketId = p.getId();
        if (txtObject != null) {
            txtObject.setText(p.getTitle() != null ? p.getTitle() : "");
        }
        if (txtNewDesc != null) {
            txtNewDesc.setText(p.getDescription() != null ? p.getDescription() : "");
        }
        if (txtSender != null) {
            txtSender.setText(p.getSender() != null ? p.getSender() : "");
        }
        if (cmbPriority != null && p.getPriority() != null) {
            cmbPriority.getSelectionModel().select(p.getPriority());
        }
        if (dpRegDate != null) {
            try {
                if (p.getRegistrationDate() != null && p.getRegistrationDate().length() >= 10) {
                    dpRegDate.setValue(LocalDate.parse(p.getRegistrationDate().substring(0, 10)));
                } else {
                    dpRegDate.setValue(LocalDate.now());
                }
            } catch (Exception e) {
                dpRegDate.setValue(LocalDate.now());
            }
        }
        if (txtAttachment != null) {
            txtAttachment.setText(p.getAttachmentPath() != null ? p.getAttachmentPath() : "");
        }
        pendingAttachmentFile = null;
        if (titledRegister != null) {
            titledRegister.setExpanded(true);
        }
    }

    private void applyActionVisibility() {
        String r = roleKey == null ? "" : roleKey;
        boolean admin = "ADMIN".equals(r);
        boolean co = "COURIER".equals(r);
        boolean sec = "SECRETAIRE".equals(r);
        boolean dir = "DIRECTEUR".equals(r) || admin;
        boolean canSetInitialDir = co || admin;

        setVis(btnSetDirection, canSetInitialDir);
        setVis(lblDir, canSetInitialDir);
        setVis(cmbTargetDirection, canSetInitialDir);
        boolean canRouteSous = sec || dir;
        setVis(btnSetSous, canRouteSous);
        setVis(lblSous, canRouteSous);
        setVis(cmbTargetSous, canRouteSous);
        setVis(lblSdir, dir);
        setVis(cmbAssignSousDir, dir);
        setVis(btnAssSousDir, dir);
        // Inspecteur / contrôleur / vérificateur: only at direction (directeur); lower roles use ticket tracking.
        boolean showParcoursAgentAssign = "DIRECTEUR".equals(r) || admin;
        setVis(lblInsp, showParcoursAgentAssign);
        setVis(cmbAssignInsp, showParcoursAgentAssign);
        setVis(btnAssInsp, showParcoursAgentAssign);
        setVis(lblCtrl, showParcoursAgentAssign);
        setVis(cmbAssignCtrl, showParcoursAgentAssign);
        setVis(btnAssCtrl, showParcoursAgentAssign);
        setVis(lblVer, showParcoursAgentAssign);
        setVis(cmbAssignVer, showParcoursAgentAssign);
        setVis(btnAssVer, showParcoursAgentAssign);
        boolean showParcoursResolve = "DIRECTEUR".equals(r) || admin || "SECRETAIRE".equals(r);
        setVis(btnResolve, showParcoursResolve);
        applySousDirecteurDownUi(r);
    }

    /**
     * Sous-directeur and line agents: hide the full Parcours (bottom-right) and direction/sous/ticket columns
     * (covered in Suivi des tickets). COURIER keeps Parcours for first routing; coordination roles keep the full view.
     */
    private void applySousDirecteurDownUi(String r) {
        boolean lineAgent = Set.of(
                        "SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR", "VERIFICATEUR", "VERIFICATEUR-ASSISTANT")
                .contains(r);
        if (scrollParcours != null) {
            setVis(scrollParcours, !lineAgent);
            if (scrollParcours.getParent() instanceof SplitPane sp && !sp.getDividers().isEmpty()) {
                if (lineAgent) {
                    sp.getDividers().get(0).setPosition(1.0);
                } else {
                    sp.setDividerPositions(0.5);
                }
            }
        }
        setTableColumnVisible(colDir, !lineAgent);
        setTableColumnVisible(colSous, !lineAgent);
        setTableColumnVisible(colTicket, !lineAgent);
    }

    private static void setTableColumnVisible(TableColumn<CourierPacket, String> col, boolean v) {
        if (col == null) {
            return;
        }
        col.setVisible(v);
    }

    private void updateGrantAndEditVisibility(CourierPacket p) {
        if (p == null) {
            if (btnGrantSecret != null) {
                setVis(btnGrantSecret, false);
            }
            if (btnEditMyPacket != null) {
                setVis(btnEditMyPacket, false);
            }
            return;
        }
        String r = roleKey == null ? "" : roleKey;
        boolean isDir = "DIRECTEUR".equals(r) || "ADMIN".equals(r);
        boolean showGrant = isDir
                && CourierPacket.ST_DIRECTED.equals(p.getStatus())
                && p.getTargetSousDirectionId() == null
                && !p.isSecretaireMayRouteSous();
        if (myDirectionId != null && p.getTargetDirectionId() != null
                && !isPacketInMyDirectionCluster(p)) {
            showGrant = false;
        }
        if (btnGrantSecret != null) {
            setVis(btnGrantSecret, showGrant);
        }
        boolean canEdit = "COURIER".equals(r)
                && p.getCreatedBy() == Session.getUserId()
                && !CourierPacket.ST_RESOLVED.equals(p.getStatus());
        if (btnEditMyPacket != null) {
            setVis(btnEditMyPacket, canEdit);
        }
    }

    /** Secrétaire: hint + disable Appliquer until the directeur has delegated, or the directeur routes themself. */
    private void updateSousRouteControls(CourierPacket p) {
        if (lblSousHint != null) {
            lblSousHint.setVisible(false);
            lblSousHint.setManaged(false);
        }
        if (btnSetSous != null) {
            btnSetSous.setDisable(false);
        }
        if (p == null) {
            return;
        }
        String r = roleKey == null ? "" : roleKey;
        boolean sec = "SECRETAIRE".equals(r);
        boolean hasSousChoices = cmbTargetSous.getItems() != null && !cmbTargetSous.getItems().isEmpty();
        boolean needGrant = sec
                && CourierPacket.ST_DIRECTED.equals(p.getStatus())
                && p.getTargetSousDirectionId() == null
                && !p.isSecretaireMayRouteSous()
                && hasSousChoices;
        if (needGrant) {
            if (lblSousHint != null) {
                lblSousHint.setText(I18n.t("courierSousHintNeedGrant",
                        "The director must use « Authorize the secretary to assign a sub-direction », or set the sub-direction themself. You will be notified when authorization is given."));
                lblSousHint.setStyle("-fx-text-fill:#b45309; -fx-font-size:12px;");
                lblSousHint.setVisible(true);
                lblSousHint.setManaged(true);
            }
            if (btnSetSous != null) {
                btnSetSous.setDisable(true);
            }
        } else if (CourierPacket.ST_RESOLVED.equalsIgnoreCase(p.getStatus())) {
            if (btnSetSous != null) {
                btnSetSous.setDisable(true);
            }
        } else if (sec && p.getTargetDirectionId() != null
                && (myDirectionId == null || !isPacketInMyDirectionCluster(p))) {
            if (btnSetSous != null) {
                btnSetSous.setDisable(true);
            }
        } else {
            if (btnSetSous != null) {
                btnSetSous.setDisable(false);
            }
            if (lblSousHint != null) {
                boolean dirU = "DIRECTEUR".equals(r) || "ADMIN".equals(r);
                if (dirU
                        && CourierPacket.ST_DIRECTED.equals(p.getStatus())
                        && p.getTargetSousDirectionId() == null
                        && hasSousChoices) {
                    lblSousHint.setText(I18n.t("courierSousHintDirecteur",
                            "Set the sub-direction here, or use « Authorize the secretary to assign a sub-direction » if the secretary should do it. They are notified when you grant access."));
                    lblSousHint.setStyle("-fx-text-fill:#1d4ed8; -fx-font-size:12px;");
                    lblSousHint.setVisible(true);
                    lblSousHint.setManaged(true);
                }
            }
        }
    }

    private void setVis(javafx.scene.Node n, boolean v) {
        if (n == null) {
            return;
        }
        n.setVisible(v);
        n.setManaged(v);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String translateStatus(String s) {
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
    }

    private void onSelect(CourierPacket p) {
        updateGrantAndEditVisibility(p);
        if (p == null) {
            listJourney.getItems().clear();
            return;
        }
        listJourney.setItems(CourierPacketDAO.loadJourney(p.getId()));
        loadUserCombos(p);
        if (p.getTargetDirectionId() != null) {
            cmbTargetSous.setItems(DirectionDAO.getSousDirectionsForDirectionCluster(p.getTargetDirectionId()));
        } else {
            cmbTargetSous.setItems(FXCollections.observableArrayList());
        }
        cmbTargetDirection.getSelectionModel().select(
                cmbTargetDirection.getItems().stream()
                        .filter(d -> p.getTargetDirectionId() != null && d.getId() == p.getTargetDirectionId())
                        .findFirst()
                        .orElse(null)
        );
        if (p.getTargetSousDirectionId() != null) {
            cmbTargetSous.getItems().stream()
                    .filter(sd -> sd.getId() == p.getTargetSousDirectionId())
                    .findFirst()
                    .ifPresent(sd -> cmbTargetSous.getSelectionModel().select(sd));
        }
        updateSousRouteControls(p);
    }

    private void loadUserCombos(CourierPacket p) {
        Integer d = p.getTargetDirectionId();
        cmbAssignSousDir.getItems().clear();
        cmbAssignInsp.getItems().clear();
        cmbAssignCtrl.getItems().clear();
        cmbAssignVer.getItems().clear();
        if (d == null) {
            return;
        }
        String rk = roleKey == null ? "" : roleKey;
        if (!"DIRECTEUR".equals(rk) && !"ADMIN".equals(rk)) {
            return;
        }
        cmbAssignSousDir.setItems(UserDAO.findActiveByRoleInCourierScope("SOUS-DIRECTEUR", d));
        cmbAssignInsp.setItems(UserDAO.findActiveByRoleInCourierScope("INSPECTEUR", d));
        cmbAssignCtrl.setItems(UserDAO.findActiveByRoleInCourierScope("CONTROLEUR", d));
        cmbAssignVer.setItems(
                UserDAO.findActiveByRolesInCourierScope(d, "VERIFICATEUR", "VERIFICATEUR-ASSISTANT"));
    }

    @FXML
    private void register() {
        if (!mayIntakeCourierMail()) {
            return;
        }
        String t = txtObject != null && txtObject.getText() != null ? txtObject.getText().trim() : "";
        if (t.isEmpty()) {
            err("err.courierTitle", I18n.t("err.courierObjectRequired", "Object / title is required."));
            return;
        }
        String desc = txtNewDesc != null ? txtNewDesc.getText() : null;
        String sender = txtSender != null ? txtSender.getText() : null;
        String pr = cmbPriority != null && cmbPriority.getValue() != null ? cmbPriority.getValue() : "MEDIUM";
        String reg = "";
        if (dpRegDate != null && dpRegDate.getValue() != null) {
            reg = dpRegDate.getValue().toString();
        }
        try {
            if (editingPacketId != null) {
                String path = txtAttachment != null ? txtAttachment.getText() : null;
                if (pendingAttachmentFile != null) {
                    path = copyToCourierDir(editingPacketId, pendingAttachmentFile);
                }
                CourierPacketDAO.updateMyPacket(
                        editingPacketId, Session.getUserId(), t, desc, sender, pr, reg, path);
                clearForm();
                editingPacketId = null;
                refresh();
            } else {
                int id = CourierPacketDAO.createPacketFull(
                        t, desc, Session.getUserId(), sender, pr, reg, null);
                if (pendingAttachmentFile != null) {
                    String path = copyToCourierDir(id, pendingAttachmentFile);
                    CourierPacketDAO.updateMyPacket(
                            id, Session.getUserId(), t, desc, sender, pr, reg, path);
                }
                clearForm();
                refresh();
                selectById(id);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if ("not_allowed".equals(e.getMessage())) {
                err("err.courierNotYours", I18n.t("err.courierUpdateDenied", "Update not allowed."));
            } else {
                err("err.courierSave", "Could not save: " + e.getMessage());
            }
        }
    }

    private void clearForm() {
        if (txtObject != null) {
            txtObject.clear();
        }
        if (txtNewDesc != null) {
            txtNewDesc.clear();
        }
        if (txtSender != null) {
            txtSender.clear();
        }
        if (cmbPriority != null) {
            cmbPriority.getSelectionModel().select("MEDIUM");
        }
        if (dpRegDate != null) {
            dpRegDate.setValue(LocalDate.now());
        }
        onAttachRemove();
        editingPacketId = null;
    }

    private String copyToCourierDir(int packetId, File f) throws Exception {
        File base = new File(System.getProperty("sysco.data.dir", System.getProperty("user.dir")));
        File dir = new File(base, "uploads/courier/" + packetId);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new java.io.IOException("uploads");
        }
        String safe = f.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dest = Paths.get(dir.getAbsolutePath(), safe);
        Files.copy(f.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toAbsolutePath().toString();
    }

    @FXML
    private void onSetDirection() {
        CourierPacket p = table.getSelectionModel().getSelectedItem();
        if (p == null || cmbTargetDirection.getValue() == null) {
            return;
        }
        if (!mayIntakeCourierMail()) {
            return;
        }
        boolean stOk = Set.of(
                CourierPacket.ST_AWAITING_DIRECTION, CourierPacket.ST_REGISTERED).contains(p.getStatus());
        if (!stOk) {
            err("err.courierNotYours", I18n.t("err.courierRouteState", "This courrier is already routed."));
            return;
        }
        if (p.getCreatedBy() != Session.getUserId() && !"ADMIN".equals(roleKey)) {
            err("err.courierNotYours", I18n.t("err.courierYoursOnly",
                    "Only the courrier creator (or an administrator) can set the direction at this step."));
            return;
        }
        try {
            CourierPacketDAO.setDirection(p.getId(), cmbTargetDirection.getValue().getId(), Session.getUserId());
            refresh();
            selectById(p.getId());
            MainController.refreshNotificationBadgesNow();
        } catch (Exception e) {
            e.printStackTrace();
            err("err.courierDir", e.getMessage());
        }
    }

    @FXML
    private void onSetSous() {
        CourierPacket p = table.getSelectionModel().getSelectedItem();
        SousDirection sd = cmbTargetSous.getValue();
        if (p == null || sd == null) {
            return;
        }
        String vr = roleKey == null ? "" : roleKey;
        if (!Set.of("SECRETAIRE", "ADMIN", "DIRECTEUR").contains(vr)) {
            return;
        }
        if ("DIRECTEUR".equals(vr)
                && (myDirectionId == null || !isPacketInMyDirectionCluster(p))) {
            err("err.courierDir", I18n.t("err.courierNotYourDirection", "Not in your direction."));
            return;
        }
        if ("SECRETAIRE".equals(vr)
                && (myDirectionId == null || !isPacketInMyDirectionCluster(p))) {
            err("err.courierDir", I18n.t("err.courierNotYourDirection", "Not in your direction."));
            return;
        }
        try {
            boolean adminOverride = "ADMIN".equals(roleKey);
            // DIRECTEUR and ADMIN: no secrétaire grant required. SECRETAIRE: enforced in DAO.
            String roleForDao = "ADMIN".equals(vr) ? "ADMIN" : vr;
            CourierPacketDAO.setSousDirection(
                    p.getId(), sd.getId(), Session.getUserId(), roleForDao, adminOverride);
            refresh();
            selectById(p.getId());
            MainController.refreshNotificationBadgesNow();
        } catch (IllegalStateException e) {
            if ("secretaire_not_granted".equals(e.getMessage())) {
                err("err.courierSous", I18n.t("err.courierSousNeedsGrant", "The director must allow the secretary to route to a sub-direction first."));
            } else if ("sous_mismatch".equals(e.getMessage())) {
                err("err.courierSous", I18n.t("err.courierSousMismatch", "The sub-direction does not belong to this courrier’s direction."));
            } else if ("direction_first".equals(e.getMessage())) {
                err("err.courierSous", I18n.t("err.courierNeedDirection", "A direction must be set first."));
            } else {
                err("err.courierSous", e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            err("err.courierSous", e.getMessage() != null ? e.getMessage() : "Error");
        }
    }

    @FXML
    private void onAssignSousDir() {
        assignUser(cmbAssignSousDir, (pid, uid) -> CourierPacketDAO.assignSousDirecteur(pid, uid, Session.getUserId()));
    }

    @FXML
    private void onAssignInsp() {
        assignUser(cmbAssignInsp, (pid, uid) -> CourierPacketDAO.assignInspecteur(pid, uid, Session.getUserId()));
    }

    @FXML
    private void onAssignCtrl() {
        assignUser(cmbAssignCtrl, (pid, uid) -> CourierPacketDAO.assignControleur(pid, uid, Session.getUserId()));
    }

    @FXML
    private void onAssignVer() {
        assignUser(cmbAssignVer, (pid, uid) -> CourierPacketDAO.assignVerificateur(pid, uid, Session.getUserId()));
    }

    @FunctionalInterface
    private interface Assigner {
        void go(int packetId, int userId) throws Exception;
    }

    private void assignUser(ComboBox<User> cmb, Assigner a) {
        CourierPacket p = table.getSelectionModel().getSelectedItem();
        User u = cmb.getValue();
        if (p == null || u == null) {
            return;
        }
        try {
            a.go(p.getId(), u.getId());
            refresh();
            selectById(p.getId());
            MainController.refreshNotificationBadgesNow();
        } catch (Exception e) {
            e.printStackTrace();
            err("err.courierAssign", e.getMessage());
        }
    }

    @FXML
    private void onResolve() {
        CourierPacket p = table.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }
        try {
            CourierPacketDAO.resolve(p.getId(), Session.getUserId());
            refresh();
            selectById(p.getId());
            MainController.refreshNotificationBadgesNow();
        } catch (Exception e) {
            e.printStackTrace();
            err("err.courierResolve", e.getMessage());
        }
    }

    private void selectById(int id) {
        for (CourierPacket row : table.getItems()) {
            if (row.getId() == id) {
                table.getSelectionModel().select(row);
                onSelect(row);
                return;
            }
        }
    }

    private void openCourierDetails(int packetId) {
        MainController.loadPage("CourierDetails.fxml", c -> {
            if (c instanceof CourierDetailsController d) {
                d.setPacketId(packetId);
            }
        });
    }

    /** True if the packet's target direction is in the same label cluster as the current user's direction. */
    private boolean isPacketInMyDirectionCluster(CourierPacket p) {
        if (myDirectionId == null || p == null) {
            return false;
        }
        Integer tid = p.getTargetDirectionId();
        if (tid == null) {
            return false;
        }
        return DirectionDAO.getDirectionIdCluster(myDirectionId).contains(tid);
    }

    private void err(String key, String d) {
        Alert al = new Alert(Alert.AlertType.ERROR);
        al.setTitle(I18n.t("error", "Error"));
        al.setHeaderText(null);
        al.setContentText(I18n.t(key, d));
        al.showAndWait();
    }
}
