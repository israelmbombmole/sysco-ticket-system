package com.app.controller;

import com.app.auth.Session;
import com.app.dao.MyShiftPlannedDAO;
import com.app.dao.UserDAO;
import com.app.model.User;
import com.app.util.I18n;
import java.time.LocalDate;
import java.time.YearMonth;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Replaces a full month of {@code myshift_planned} rows for one user using a 5/7 work-week template
 * (configurable JOUR/REPOS-style codes for DGDA or similar org rules).
 */
public class MyShiftPlanMonthController {

    @FXML
    private ComboBox<User> cmbUser;
    @FXML
    private DatePicker dpMonth;
    @FXML
    private Label lblResult;

    private Integer directionId;
    private Integer sousDirectionId;
    private Stage selfStage;

    public void setDirectionScope(Integer directionId, Integer sousDirectionId) {
        this.directionId = directionId;
        this.sousDirectionId = sousDirectionId;
        if (cmbUser != null) {
            cmbUser.getItems().setAll(UserDAO.listForShiftPlan(this.directionId, this.sousDirectionId));
        }
    }

    public void setStage(Stage s) {
        this.selfStage = s;
    }

    @FXML
    private void initialize() {
        if (dpMonth != null) {
            LocalDate d = LocalDate.now().withDayOfMonth(1);
            dpMonth.setValue(d);
        }
        if (cmbUser != null) {
            cmbUser.setCellFactory(l -> newListCell());
            cmbUser.setButtonCell(newListCell());
        }
    }

    private static ListCell<User> newListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                } else {
                    String m = u.getMatricule() != null && !u.getMatricule().isBlank()
                            ? u.getMatricule() + " — "
                            : "";
                    setText(m + u.getUsername());
                }
            }
        };
    }

    @FXML
    private void onApply57() {
        User u = cmbUser == null ? null : cmbUser.getValue();
        if (u == null) {
            new Alert(Alert.AlertType.WARNING, I18n.t("myshiftPlanNeedUser", "Select a user.")).showAndWait();
            return;
        }
        LocalDate d0 = dpMonth == null ? null : dpMonth.getValue();
        if (d0 == null) {
            d0 = LocalDate.now();
        }
        YearMonth ym = YearMonth.from(d0);
        String wd = ask("myshiftPlanCodeWeekday", "Weekday code (e.g. JOUR for Mon–Fri)", MyShiftPlannedDAO.DEFAULT_WEEKDAY);
        if (wd == null) {
            return;
        }
        String we = ask("myshiftPlanCodeWeekend", "Weekend / rest code (e.g. REPOS for Sat–Sun)", MyShiftPlannedDAO.DEFAULT_WEEKEND);
        if (we == null) {
            return;
        }
        int n = MyShiftPlannedDAO.applyAndSave5over7(
                u.getId(), ym, wd.trim().toUpperCase(), we.trim().toUpperCase(), Session.getUserId());
        if (lblResult != null) {
            String msg = I18n.t("myshiftPlanSaved", "Month saved. Days written: {0}");
            lblResult.setText(msg.replace("{0}", String.valueOf(n)));
        }
    }

    private String ask(String i18nKey, String dflt, String prefill) {
        TextInputDialog d = new TextInputDialog(prefill);
        d.setHeaderText(null);
        d.setTitle("SYSCO");
        d.setContentText(I18n.t(i18nKey, dflt));
        d.initOwner(selfStage);
        return d.showAndWait()
                .map(s -> s != null && !s.isBlank() ? s : prefill)
                .orElse(null);
    }

    @FXML
    private void onClose() {
        if (selfStage != null) {
            selfStage.close();
        }
    }
}
