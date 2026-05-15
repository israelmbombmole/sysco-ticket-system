package com.app.util;

import com.app.dao.DirectionDAO;
import com.app.model.CourierPacket;
import com.app.model.Ticket;
import com.app.model.User;
import com.app.util.AppUiStyles;
import com.app.util.I18n;

import javafx.beans.property.SimpleStringProperty;

import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Read-only drill-down tables for dashboard statistic cards.
 */
public final class DashboardDetailDialog {

    private DashboardDetailDialog() {}

    public static void showTickets(Window owner, String title, List<Ticket> rows) {
        TableView<Ticket> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Ticket, Integer> colId = new TableColumn<>(I18n.t("ID", "ID"));
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<Ticket, String> colTitle = new TableColumn<>(I18n.t("Title", "Title"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<Ticket, String> colStatus = new TableColumn<>(I18n.t("Status", "Status"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        TableColumn<Ticket, String> colPri = new TableColumn<>(I18n.t("priority", "Priority"));
        colPri.setCellValueFactory(new PropertyValueFactory<>("priority"));

        TableColumn<Ticket, String> colAgent = new TableColumn<>(I18n.t("assignedTo"));
        colAgent.setCellValueFactory(cd -> {
            Ticket t = cd.getValue();
            String n = t != null ? t.getAssignedToName() : null;
            if (n != null && !n.isBlank()) {
                return new javafx.beans.property.SimpleStringProperty(n);
            }
            return new javafx.beans.property.SimpleStringProperty("—");
        });

        TableColumn<Ticket, String> colCreated = new TableColumn<>(I18n.t("Created", "Created"));
        colCreated.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        table.getColumns().setAll(colId, colTitle, colStatus, colPri, colAgent, colCreated);

        Label hint = new Label(java.text.MessageFormat.format(
                I18n.t("dashboardDetailRowCap", "Showing {0} row(s)."),
                String.valueOf(rows.size())));
        hint.setStyle("-fx-text-fill:#64748b;");

        open(owner, title, table, hint);
    }

    public static void showUsers(Window owner, String title, List<User> rows) {
        TableView<User> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<User, String> colUser = new TableColumn<>(I18n.t("username", "Username"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("username"));

        TableColumn<User, String> colRole = new TableColumn<>(I18n.t("role", "Role"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));

        TableColumn<User, String> colDir = new TableColumn<>(I18n.t("directionColumn", "Direction"));
        colDir.setCellValueFactory(cd -> {
            User u = cd.getValue();
            if (u == null) {
                return new SimpleStringProperty("—");
            }
            String name = u.getDirectionName();
            if (name != null && !name.isBlank()) {
                return new SimpleStringProperty(name.trim());
            }
            Integer id = u.getDirectionId();
            if (id != null && id > 0) {
                var dir = DirectionDAO.getById(id);
                return new SimpleStringProperty(dir != null ? dir.getName() : "—");
            }
            return new SimpleStringProperty("—");
        });

        table.getColumns().setAll(colUser, colRole, colDir);

        Label hint = new Label(java.text.MessageFormat.format(
                I18n.t("dashboardDetailRowCap", "Showing {0} row(s)."),
                String.valueOf(rows.size())));
        hint.setStyle("-fx-text-fill:#64748b;");

        open(owner, title, table, hint);
    }

    public static void showCourierPackets(Window owner, String title, List<CourierPacket> rows) {
        TableView<CourierPacket> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<CourierPacket, String> colRef = new TableColumn<>(I18n.t("ref", "Ref"));
        colRef.setCellValueFactory(new PropertyValueFactory<>("refCode"));

        TableColumn<CourierPacket, String> colTitle = new TableColumn<>(I18n.t("Title", "Title"));
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<CourierPacket, String> colSt = new TableColumn<>(I18n.t("status", "Status"));
        colSt.setCellValueFactory(new PropertyValueFactory<>("status"));

        TableColumn<CourierPacket, String> colDir = new TableColumn<>(I18n.t("direction", "Direction"));
        colDir.setCellValueFactory(new PropertyValueFactory<>("targetDirectionName"));

        TableColumn<CourierPacket, String> colSd = new TableColumn<>(I18n.t("sousDirection", "Sub-direction"));
        colSd.setCellValueFactory(new PropertyValueFactory<>("targetSousDirectionName"));

        table.getColumns().setAll(colRef, colTitle, colSt, colDir, colSd);

        Label hint = new Label(java.text.MessageFormat.format(
                I18n.t("dashboardDetailRowCap", "Showing {0} row(s)."),
                String.valueOf(rows.size())));
        hint.setStyle("-fx-text-fill:#64748b;");

        open(owner, title, table, hint);
    }

    public static void showMessage(Window owner, String title, String message) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle(title);

        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setStyle("-fx-padding:12;");

        Button btnClose = new Button(I18n.t("close", "Close"));
        btnClose.setOnAction(e -> stage.close());

        HBox bottom = new HBox(btnClose);
        bottom.setPadding(new Insets(8, 14, 14, 14));

        BorderPane root = new BorderPane();
        root.setCenter(msg);
        BorderPane.setMargin(msg, new Insets(14));
        root.setBottom(bottom);

        Scene scene = new Scene(root, 560, 220);
        AppUiStyles.applyToScene(scene);
        stage.setScene(scene);
        stage.show();
    }

    private static void open(Window owner, String title, TableView<?> table, Label topOrHint) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.setTitle(title);

        Button btnClose = new Button(I18n.t("close", "Close"));
        btnClose.setOnAction(e -> stage.close());
        HBox bottom = new HBox(btnClose);
        bottom.setPadding(new Insets(8, 0, 0, 0));

        VBox topBox = new VBox(6);
        topBox.getChildren().add(topOrHint);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setTop(topBox);
        BorderPane.setMargin(table, new Insets(8, 0, 0, 0));
        root.setCenter(table);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 920, 520);
        AppUiStyles.applyToScene(scene);
        stage.setScene(scene);
        stage.show();
    }
}
