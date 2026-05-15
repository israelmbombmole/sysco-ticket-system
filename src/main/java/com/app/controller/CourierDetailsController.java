package com.app.controller;

import com.app.auth.Session;
import com.app.dao.CourierPacketDAO;
import com.app.dao.TicketDAO;
import com.app.dao.UserDAO;
import com.app.model.CourierJourneyLine;
import com.app.model.CourierPacket;
import com.app.model.Ticket;
import com.app.model.User;
import com.app.util.I18n;
import com.app.util.RoleKeyUtil;
import com.app.util.TicketUtil;
import java.awt.Desktop;
import java.io.File;
import java.util.Locale;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

/**
 * Read-only courrier detail view (double-click from the courrier list), aligned with ticket/task details pattern.
 */
public class CourierDetailsController {

    @FXML private Button btnBack;
    @FXML private Button btnOpenTicket;
    @FXML private Button btnOpenAttachment;
    @FXML private Label lblHeader;
    @FXML private Label lblStatus;
    @FXML private Label lblRef;
    @FXML private Label lblTitle;
    @FXML private Label lblMeta;
    @FXML private Label lblPriority;
    @FXML private Label lblDirection;
    @FXML private Label lblSous;
    @FXML private Label lblExtraDirections;
    @FXML private Label lblSender;
    @FXML private Label lblRegDate;
    @FXML private Label lblLinkedTicket;
    @FXML private Label lblAssignees;
    @FXML private TextArea txtDescription;
    @FXML private ListView<CourierJourneyLine> listJourney;

    private int packetId;
    private String viewRole;
    private Integer myDirectionId;
    private Integer mySousId;

    @FXML
    public void initialize() {
        viewRole = Session.getRole() == null ? "" : Session.getRole();
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
        if (listJourney != null) {
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
        }
    }

    /**
     * Open details for a courrier by id; reloads from the database.
     */
    public void setPacketId(int id) {
        this.packetId = id;
        CourierPacket p = CourierPacketDAO.getById(id);
        String listRole = computeListRole();
        if (p == null || !CourierPacketDAO.mayViewPacket(p, listRole, myDirectionId, mySousId)) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle(I18n.t("warning", "Warning"));
            a.setContentText(I18n.t("err.courierViewDenied", "You cannot open this courrier or it no longer exists."));
            a.showAndWait();
            MainController.loadPage("courier_portal.fxml", null);
            return;
        }
        populate(p);
    }

    private String computeListRole() {
        return RoleKeyUtil.listRoleKey(viewRole);
    }

    private void populate(CourierPacket p) {
        if (lblHeader != null) {
            lblHeader.setText(I18n.t("courierDetailsTitle", "Courrier details"));
        }
        lblStatus.setText(translateStatus(p.getStatus()));
        lblRef.setText(p.getRefCode() != null ? p.getRefCode() : "#" + p.getId());
        lblTitle.setText(p.getTitle() != null && !p.getTitle().isBlank() ? p.getTitle() : "—");
        String by = p.getCreatedByName() != null && !p.getCreatedByName().isBlank() ? p.getCreatedByName() : String.valueOf(p.getCreatedBy());
        String created = p.getCreatedAt() != null ? p.getCreatedAt() : "—";
        lblMeta.setText(I18n.t("createdBy", "Created by") + ": " + by + " · " + I18n.t("created", "Created") + ": " + created);
        applyPriorityStyle(p.getPriority());
        lblDirection.setText(nz(p.getTargetDirectionName()));
        lblSous.setText(nz(p.getTargetSousDirectionName()));
        if (lblExtraDirections != null) {
            String ex = p.getExtraDirectionNames();
            lblExtraDirections.setText(ex != null && !ex.isBlank() ? ex : "—");
        }
        lblSender.setText(nz(p.getSender()));
        lblRegDate.setText(nz(p.getRegistrationDate()));
        if (p.getLinkedTicketId() != null) {
            lblLinkedTicket.setText(TicketUtil.formatTicketRef(p.getLinkedTicketId()));
            if (btnOpenTicket != null) {
                btnOpenTicket.setVisible(true);
                btnOpenTicket.setManaged(true);
            }
        } else {
            lblLinkedTicket.setText("—");
            if (btnOpenTicket != null) {
                btnOpenTicket.setVisible(false);
                btnOpenTicket.setManaged(false);
            }
        }
        String path = p.getAttachmentPath();
        if (path != null && !path.isBlank()) {
            if (btnOpenAttachment != null) {
                btnOpenAttachment.setVisible(true);
                btnOpenAttachment.setManaged(true);
            }
        } else if (btnOpenAttachment != null) {
            btnOpenAttachment.setVisible(false);
            btnOpenAttachment.setManaged(false);
        }
        if (lblAssignees != null) {
            StringBuilder assign = new StringBuilder();
            appendAssignLine(assign, I18n.t("assignSousDir", "Sub-director"), p.getAssignSousDirName());
            appendAssignLine(assign, I18n.t("assignInspecteur", "Inspector"), p.getAssignInspName());
            appendAssignLine(assign, I18n.t("assignControleur", "Controller"), p.getAssignCtrlName());
            appendAssignLine(assign, I18n.t("assignVerificateur", "Verifier"), p.getAssignVerifName());
            lblAssignees.setText(assign.length() == 0 ? "—" : assign.toString().trim());
        }
        txtDescription.setText(p.getDescription() != null ? p.getDescription() : "");
        listJourney.setItems(CourierPacketDAO.loadJourney(p.getId()));
    }

    private void applyPriorityStyle(String pr) {
        if (pr == null) {
            pr = "MEDIUM";
        }
        lblPriority.setText(I18n.t("priority." + pr.toUpperCase(Locale.ROOT), pr));
        String base = "-fx-text-fill:white; -fx-padding:4 10; -fx-background-radius:6;";
        switch (pr.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> lblPriority.setStyle(
                    base + " -fx-background-color:#7f1d1d;");
            case "HIGH" -> lblPriority.setStyle(
                    base + " -fx-background-color:#ef4444;");
            case "LOW" -> lblPriority.setStyle(
                    base + " -fx-background-color:#22c55e;");
            case "MEDIUM" -> lblPriority.setStyle(
                    base + " -fx-background-color:#f59e0b;");
            default -> lblPriority.setStyle(
                    base + " -fx-background-color:#6b7280;");
        }
    }

    @FXML
    private void handleBack() {
        MainController.loadPage("courier_portal.fxml", null);
    }

    @FXML
    private void handleOpenLinkedTicket() {
        CourierPacket p = CourierPacketDAO.getById(packetId);
        if (p == null || p.getLinkedTicketId() == null) {
            return;
        }
        Ticket t = TicketDAO.getTicketById(p.getLinkedTicketId());
        if (t == null) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setContentText(I18n.t("err.courierLinkedTicketGone", "The linked ticket could not be found."));
            a.showAndWait();
            return;
        }
        MainController.loadPage("TicketDetails.fxml", c -> {
            if (c instanceof TicketDetailsController tc) {
                tc.setTicket(t);
            }
        });
    }

    @FXML
    private void handleOpenAttachment() {
        CourierPacket p = CourierPacketDAO.getById(packetId);
        if (p == null) {
            return;
        }
        String path = p.getAttachmentPath();
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            File f = new File(path);
            if (f.isFile() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(f);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void appendAssignLine(StringBuilder b, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        b.append(label).append(": ").append(value.trim()).append('\n');
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
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
}
