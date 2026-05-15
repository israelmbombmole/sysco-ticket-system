package com.app.controller;

import com.app.auth.Session;
import com.app.dao.MyShiftDAO;
import com.app.dao.UserDAO;
import com.app.model.MyShiftRow;
import com.app.model.User;
import com.app.util.AppUiStyles;
import com.app.util.I18n;
import com.app.util.LanguageManager;
import com.app.util.MyShiftAttendanceLabels;
import com.app.util.MyShiftCsv;
import com.app.util.MyShiftHeroClock;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MyShiftSousController {

    @FXML
    private Label lblClock;
    @FXML
    private Label lblContextDate;
    @FXML
    private Label lblSous;
    @FXML
    private Button btnPlanMonth;
    @FXML
    private TableView<MyShiftRow> tblPresent;
    @FXML
    private TableColumn<MyShiftRow, String> colPNames;
    @FXML
    private TableColumn<MyShiftRow, String> colPMat;
    @FXML
    private TableColumn<MyShiftRow, String> colPFonction;
    @FXML
    private TableColumn<MyShiftRow, String> colPIn;
    @FXML
    private TableColumn<MyShiftRow, String> colPSignature;
    @FXML
    private TableColumn<MyShiftRow, String> colPOut;
    @FXML
    private TableColumn<MyShiftRow, String> colPLoc;
    @FXML
    private Label lblOnline;
    @FXML
    private DatePicker dpFrom;
    @FXML
    private DatePicker dpTo;
    @FXML
    private TableView<MyShiftRow> tblReport;
    @FXML
    private TableColumn<MyShiftRow, String> colRNames;
    @FXML
    private TableColumn<MyShiftRow, String> colRMat;
    @FXML
    private TableColumn<MyShiftRow, String> colRFonction;
    @FXML
    private TableColumn<MyShiftRow, String> colRIn;
    @FXML
    private TableColumn<MyShiftRow, String> colRSignature;
    @FXML
    private TableColumn<MyShiftRow, String> colROut;
    @FXML
    private TableColumn<MyShiftRow, String> colRLoc;

    private Integer sousId;

    private Timeline myshiftClock;
    private Timeline presentTicker;

    @FXML
    private void initialize() {
        myshiftClock = MyShiftHeroClock.install(lblClock);
        if (lblContextDate != null) {
            DateTimeFormatter dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(LanguageManager.getLocale());
            lblContextDate.setText(dayFmt.format(LocalDate.now()));
            lblContextDate.sceneProperty().addListener((o, oldS, newS) -> {
                if (oldS != null && newS == null && myshiftClock != null) {
                    myshiftClock.stop();
                    myshiftClock = null;
                }
            });
        }
        User u = UserDAO.findById(Session.getUserId());
        sousId = u != null ? u.getSousDirectionId() : null;
        if (lblSous != null) {
            if (sousId == null) {
                String a = I18n.t("myshiftSousNoSd", "No sub-direction is assigned to your account. Oversight is not available.");
                String b = I18n.t("myshiftSousPlanMonthDisabled", "Month planning is disabled until a sub-direction is assigned to your account.");
                lblSous.setText(a + "\n\n" + b);
                lblSous.setVisible(true);
                lblSous.setManaged(true);
            } else {
                lblSous.setText("");
                lblSous.setVisible(false);
                lblSous.setManaged(false);
            }
        }
        if (btnPlanMonth != null) {
            boolean noSd = sousId == null;
            btnPlanMonth.setDisable(noSd);
            btnPlanMonth.setTooltip(
                    noSd
                            ? new Tooltip(
                            I18n.t(
                                    "myshiftSousPlanMonthDisabled",
                                    "Month planning is disabled until a sub-direction is assigned to your account."))
                            : null);
        }
        colPNames.setCellValueFactory(new PropertyValueFactory<>("namesAndPostnoms"));
        colPMat.setCellValueFactory(new PropertyValueFactory<>("matricule"));
        colPFonction.setCellValueFactory(new PropertyValueFactory<>("fonction"));
        colPIn.setCellValueFactory(new PropertyValueFactory<>("signInTime"));
        colPSignature.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                MyShiftAttendanceLabels.signature(c.getValue())));
        colPOut.setCellValueFactory(new PropertyValueFactory<>("signOutTime"));
        colPLoc.setCellValueFactory(new PropertyValueFactory<>("location"));
        colRNames.setCellValueFactory(new PropertyValueFactory<>("namesAndPostnoms"));
        colRMat.setCellValueFactory(new PropertyValueFactory<>("matricule"));
        colRFonction.setCellValueFactory(new PropertyValueFactory<>("fonction"));
        colRIn.setCellValueFactory(new PropertyValueFactory<>("signInTime"));
        colRSignature.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                MyShiftAttendanceLabels.signature(c.getValue())));
        colROut.setCellValueFactory(new PropertyValueFactory<>("signOutTime"));
        colRLoc.setCellValueFactory(new PropertyValueFactory<>("location"));
        LocalDate today = LocalDate.now();
        if (dpFrom != null) {
            dpFrom.setValue(today.withDayOfMonth(1));
        }
        if (dpTo != null) {
            dpTo.setValue(today);
        }
        if (tblPresent != null) {
            Label pl = new Label(I18n.t("myshiftTableEmptyPresent", "No one is on shift right now."));
            pl.setWrapText(true);
            tblPresent.setPlaceholder(pl);
        }
        if (tblReport != null) {
            Label rl = new Label(I18n.t("myshiftTableEmptyReport", "No rows. Choose dates and load the report."));
            rl.setWrapText(true);
            tblReport.setPlaceholder(rl);
        }
        if (tblPresent != null) {
            tblPresent.sceneProperty().addListener((o, oldS, newS) -> {
                if (oldS != null && newS == null) {
                    if (presentTicker != null) {
                        presentTicker.stop();
                        presentTicker = null;
                    }
                } else if (newS != null) {
                    startPresentTicker();
                }
            });
        }
        onRefreshPresent();
    }

    private void startPresentTicker() {
        if (tblPresent == null || sousId == null) {
            return;
        }
        if (presentTicker != null) {
            return;
        }
        presentTicker = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (tblPresent == null) {
                return;
            }
            tblPresent.refresh();
            updateOnlineCount();
        }));
        presentTicker.setCycleCount(Timeline.INDEFINITE);
        presentTicker.play();
    }

    private void updateOnlineCount() {
        if (lblOnline == null) {
            return;
        }
        int n = tblPresent != null && tblPresent.getItems() != null ? tblPresent.getItems().size() : 0;
        String msg = I18n.t("myshiftOnlineCount", "Online: {0}");
        if (msg != null) {
            lblOnline.setText(msg.replace("{0}", String.valueOf(n)));
        } else {
            lblOnline.setText("Online: " + n);
        }
    }

    @FXML
    private void onRefreshPresent() {
        if (tblPresent == null) {
            return;
        }
        if (sousId == null) {
            tblPresent.getItems().clear();
            return;
        }
        String day = LocalDate.now().toString();
        List<MyShiftRow> rows = MyShiftDAO.listPresentToday(day, null, sousId);
        tblPresent.getItems().setAll(rows);
    }

    @FXML
    private void onOpenPlanMonth() {
        if (sousId == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/myshift_plan_month.fxml"),
                    LanguageManager.getBundle()
            );
            Parent root = loader.load();
            MyShiftPlanMonthController c = loader.getController();
            Stage s = new Stage();
            c.setDirectionScope(Session.getDirectionId(), sousId);
            c.setStage(s);
            Scene sc = new Scene(root);
            AppUiStyles.applyToScene(sc);
            s.setScene(sc);
            s.setTitle(I18n.t("myshiftPlanMonthWindowTitle", "MyShift — month planning"));
            s.initModality(Modality.WINDOW_MODAL);
            if (tblPresent != null && tblPresent.getScene() != null) {
                s.initOwner(tblPresent.getScene().getWindow());
            } else if (lblSous != null && lblSous.getScene() != null) {
                s.initOwner(lblSous.getScene().getWindow());
            }
            s.show();
        } catch (Exception e) {
            if (e.getMessage() != null) {
                new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
            } else {
                new Alert(Alert.AlertType.ERROR, I18n.t("err.error", "Error")).showAndWait();
            }
        }
    }

    @FXML
    private void onLoadReport() {
        if (tblReport == null || dpFrom == null || dpTo == null) {
            return;
        }
        if (sousId == null) {
            tblReport.getItems().clear();
            return;
        }
        LocalDate a = dpFrom.getValue();
        LocalDate b = dpTo.getValue();
        if (a == null || b == null) {
            new Alert(Alert.AlertType.WARNING, I18n.t("myshiftNeedDates", "Select start and end dates.")).showAndWait();
            return;
        }
        if (a.isAfter(b)) {
            new Alert(Alert.AlertType.WARNING, I18n.t("myshiftDateOrder", "Start date must be on or before end date."))
                    .showAndWait();
            return;
        }
        List<MyShiftRow> rows = MyShiftDAO.listRangeReport(
                a.toString(),
                b.toString(),
                null,
                sousId);
        tblReport.getItems().setAll(rows);
    }

    @FXML
    private void onExportCsv() {
        if (tblReport == null || tblReport.getItems().isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, I18n.t("myshiftNoRowsToExport", "No rows to export. Load a report first."))
                    .showAndWait();
            return;
        }
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.t("myshiftExportDialog", "Export report"));
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        ch.setInitialFileName("myshift-sous-report.csv");
        File f = ch.showSaveDialog(tblReport.getScene().getWindow());
        if (f == null) {
            return;
        }
        try {
            MyShiftCsv.writeUtf8Bom(tblReport.getItems(), f);
            new Alert(Alert.AlertType.INFORMATION, I18n.t("myshiftExportDone", "File saved.")).showAndWait();
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage() != null ? e.getMessage() : "Error").showAndWait();
        }
    }
}
