package com.app.controller;

import com.app.auth.Session;
import com.app.dao.MyShiftDAO;
import com.app.dao.MyShiftPlannedDAO;
import com.app.dao.UserAbsenceDAO;
import com.app.service.AuditService;
import com.app.service.myshift.FaceAuthClient;
import com.app.service.myshift.FaceAuthResult;
import com.app.service.myshift.GeoInfo;
import com.app.service.myshift.GeoLocationClient;
import com.app.service.myshift.MyShiftConfig;
import com.app.service.myshift.MyShiftWebcamSession;
import com.app.util.I18n;
import com.app.util.LanguageManager;
import com.app.util.MyShiftHeroClock;
import com.app.util.MyShiftMonthGridUtil;
import com.github.sarxos.webcam.Webcam;
import javafx.animation.Timeline;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class MyShiftAgentController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MIN_FACE_BYTES = 32;

    @FXML
    private ImageView imgPreview;
    @FXML
    private Label lblClock;
    @FXML
    private Label lblContextDate;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblDev;
    @FXML
    private Button btnStartWebcam;
    @FXML
    private Button btnStopWebcam;
    @FXML
    private GridPane grdPlan;
    @FXML
    private Label lblCalMonth;
    @FXML
    private Button btnCalPrev;
    @FXML
    private Button btnCalNext;

    private byte[] lastImageBytes;
    private YearMonth planMonth = YearMonth.now();
    private MyShiftWebcamSession webcamSession;
    private boolean webcamStartPending;
    private Timeline myshiftClock;

    @FXML
    private void initialize() {
        if (lblContextDate != null) {
            DateTimeFormatter dayFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(LanguageManager.getLocale());
            lblContextDate.setText(dayFmt.format(LocalDate.now()));
        }
        if (lblDev != null) {
            String url = MyShiftConfig.faceApiUrl();
            if (url == null || url.isBlank()) {
                lblDev.setText(I18n.t("myshiftDevFaceHint",
                        "Face API URL is not set (db.properties: myshift.face.api.url). A dev check is used (not for production)."));
            } else {
                lblDev.setText("");
            }
        }
        if (grdPlan != null) {
            updateCalMonthLabel();
            reloadPlanGrid();
        }
        myshiftClock = MyShiftHeroClock.install(lblClock);
        if (imgPreview != null) {
            imgPreview.sceneProperty().addListener((o, oldScene, newScene) -> {
                if (oldScene != null && newScene == null) {
                    stopWebcamInternal();
                    if (myshiftClock != null) {
                        myshiftClock.stop();
                        myshiftClock = null;
                    }
                }
            });
        }
        updateLocalStatusLine();
        refreshWebcamButtonState();
    }

    @FXML
    private void onStartWebcam() {
        if (imgPreview == null) {
            return;
        }
        if (webcamStartPending) {
            return;
        }
        if (webcamSession != null && webcamSession.isRunning()) {
            return;
        }
        stopWebcamInternal();
        webcamStartPending = true;
        if (btnStartWebcam != null) {
            btnStartWebcam.setDisable(true);
        }
        if (btnStopWebcam != null) {
            btnStopWebcam.setDisable(true);
        }
        if (lblStatus != null) {
            setStatus(lblStatus, I18n.t("myshiftWebcamStarting", "Starting the camera…"), "neutral");
        }
        final AtomicReference<String> err = new AtomicReference<>();
        Task<MyShiftWebcamSession> task = new Task<>() {
            @Override
            protected MyShiftWebcamSession call() {
                MyShiftWebcamSession s = new MyShiftWebcamSession(imgPreview);
                if (!s.start()) {
                    err.set(s.getLastErrorMessage());
                    return null;
                }
                return s;
            }
        };
        task.setOnSucceeded(e -> {
            try {
                MyShiftWebcamSession s = task.getValue();
                if (s != null) {
                    webcamSession = s;
                    setStatus(
                            lblStatus,
                            I18n.t("myshiftWebcamReady", "Camera on. Sign in to use the current frame."),
                            "neutral"
                    );
                } else {
                    showWebcamStartFailedMessage(err.get());
                    updateLocalStatusLine();
                }
            } finally {
                finishWebcamStartAttempt();
            }
        });
        task.setOnFailed(e -> {
            try {
                showWebcamStartFailedMessage(
                        task.getException() != null && task.getException().getMessage() != null
                                ? task.getException().getMessage()
                                : null
                );
                updateLocalStatusLine();
            } finally {
                finishWebcamStartAttempt();
            }
        });
        new Thread(task, "myshift-webcam-start").start();
    }

    private void finishWebcamStartAttempt() {
        webcamStartPending = false;
        refreshWebcamButtonState();
    }

    private void showWebcamStartFailedMessage(String errorDetail) {
        String base;
        try {
            base = Webcam.getWebcams().isEmpty()
                    ? I18n.t("myshiftWebcamNone", "No webcam was found.")
                    : I18n.t("myshiftWebcamError", "Could not start the camera.");
        } catch (Exception e) {
            base = I18n.t("myshiftWebcamError", "Could not start the camera.");
        }
        if (errorDetail != null) {
            String m = errorDetail.toLowerCase();
            if (m.contains("busy")
                    || m.contains("in use")
                    || m.contains("lock")
                    || m.contains("cannot be started")) {
                base = base + "\n\n" + I18n.t(
                        "myshiftWebcamHintBusy",
                        "If another app is using the camera (Teams, browser, etc.), close it and try again."
                );
            }
        }
        new Alert(Alert.AlertType.WARNING, base).showAndWait();
    }

    @FXML
    private void onStopWebcam() {
        stopWebcamInternal();
        updateLocalStatusLine();
        refreshWebcamButtonState();
    }

    private void stopWebcamInternal() {
        if (webcamSession != null) {
            webcamSession.stop();
            webcamSession = null;
        }
    }

    private void refreshWebcamButtonState() {
        boolean on = webcamSession != null && webcamSession.isRunning();
        if (btnStartWebcam != null) {
            btnStartWebcam.setDisable(on || webcamStartPending);
        }
        if (btnStopWebcam != null) {
            btnStopWebcam.setDisable(!on || webcamStartPending);
        }
    }

    private void setStatus(Label l, String text, String mode) {
        if (l == null) {
            return;
        }
        l.setText(text);
        l.getStyleClass().removeAll("myshift-teams-status-ok", "myshift-teams-status-err");
        if ("ok".equals(mode)) {
            l.getStyleClass().add("myshift-teams-status-ok");
        } else if ("err".equals(mode)) {
            l.getStyleClass().add("myshift-teams-status-err");
        }
    }

    @FXML
    private void onChoosePhoto() {
        stopWebcamInternal();
        refreshWebcamButtonState();
        FileChooser ch = new FileChooser();
        ch.setTitle(I18n.t("myshiftChoosePhoto", "Choose a face photo"));
        ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        Window w = imgPreview != null && imgPreview.getScene() != null
                ? imgPreview.getScene().getWindow()
                : null;
        File f = w != null ? ch.showOpenDialog(w) : ch.showOpenDialog(null);
        if (f == null) {
            return;
        }
        try {
            lastImageBytes = Files.readAllBytes(f.toPath());
            if (lastImageBytes.length > 12_000_000) {
                lastImageBytes = null;
                if (lblStatus != null) {
                    setStatus(lblStatus, I18n.t("myshiftImageTooLarge", "Image is too large."), "err");
                }
                return;
            }
            if (imgPreview != null) {
                imgPreview.setImage(new Image(new java.io.ByteArrayInputStream(lastImageBytes)));
            }
            if (lblStatus != null) {
                setStatus(lblStatus, I18n.t("myshiftPhotoReady", "Photo loaded. You can sign in."), "neutral");
            }
        } catch (Exception e) {
            lastImageBytes = null;
            if (lblStatus != null) {
                setStatus(lblStatus, I18n.t("myshiftPhotoReadError", "Could not read the image."), "err");
            }
        }
    }

    private boolean canAttemptSignIn() {
        if (webcamSession != null && webcamSession.isRunning()) {
            return true;
        }
        return lastImageBytes != null && lastImageBytes.length >= MIN_FACE_BYTES;
    }

    @FXML
    private void onSignIn() {
        int uid = Session.getUserId();
        if (UserAbsenceDAO.isUserAbsentOn(uid, LocalDate.now())) {
            new Alert(Alert.AlertType.WARNING,
                    I18n.t("myshiftBlockedByLeave", "You are recorded as absent today. Sign-in is not allowed."))
                    .showAndWait();
            return;
        }
        if (!canAttemptSignIn()) {
            new Alert(Alert.AlertType.WARNING, I18n.t("myshiftNeedPhoto", "Start the camera or choose a face photo, then sign in."))
                    .showAndWait();
            return;
        }
        if (lblStatus != null) {
            setStatus(lblStatus, I18n.t("myshiftWorking", "Verifying…"), "neutral");
        }
        final MyShiftWebcamSession wSession = webcamSession;
        final boolean useWebcam = wSession != null && wSession.isRunning();
        final byte[] fileBytes = !useWebcam ? lastImageBytes : null;
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                byte[] imageBytes;
                if (useWebcam) {
                    imageBytes = wSession != null ? wSession.getImageJpegBytes() : null;
                } else {
                    imageBytes = fileBytes;
                }
                if (imageBytes == null || imageBytes.length < MIN_FACE_BYTES) {
                    throw new IllegalStateException(
                            I18n.t("myshiftNeedPhoto", "Start the camera or choose a face photo, then sign in."));
                }
                FaceAuthResult face = FaceAuthClient.verify(uid, imageBytes);
                if (!face.isOk()) {
                    throw new IllegalStateException(
                            I18n.t("myshiftFaceFailed", "Face verification failed or could not be completed."));
                }
                GeoInfo geo = GeoLocationClient.fetch();
                String day = LocalDate.now().toString();
                String t = java.time.LocalDateTime.now().format(TS);
                int id = MyShiftDAO.insertSignIn(
                        uid,
                        day,
                        t,
                        geo != null ? geo.latitude : null,
                        geo != null ? geo.longitude : null,
                        geo != null && geo.displayLabel != null ? geo.displayLabel
                                : I18n.t("myshiftLocationUnknown", "Location unknown"),
                        geo != null ? geo.countryCode : null,
                        geo != null ? geo.city : null,
                        geo != null ? geo.publicIp : null,
                        true,
                        face.getScore01() > 0 ? face.getScore01() : null,
                        face.getMethod()
                );
                if (id <= 0) {
                    throw new IllegalStateException(I18n.t("err.error", "Error"));
                }
                AuditService.log(
                        Session.getUsername(),
                        "MYSHIFT",
                        "ATTENDANCE",
                        uid,
                        "Sign-in id=" + id
                );
                return I18n.t("myshiftSignInOk", "You are signed in. Have a good day.");
            }
        };
        task.setOnSucceeded(e -> {
            if (lblStatus != null) {
                setStatus(lblStatus, task.getValue(), "ok");
            }
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (lblStatus != null) {
                setStatus(
                        lblStatus,
                        ex != null && ex.getMessage() != null
                                ? ex.getMessage()
                                : I18n.t("err.error", "Error"),
                        "err"
                );
            }
        });
        new Thread(task, "myshift-signin").start();
    }

    @FXML
    private void onSignOut() {
        int uid = Session.getUserId();
        boolean ok = MyShiftDAO.signOutUser(uid);
        if (ok) {
            AuditService.log(Session.getUsername(), "MYSHIFT", "ATTENDANCE", uid, "Sign-out");
            if (lblStatus != null) {
                setStatus(lblStatus, I18n.t("myshiftSignOutOk", "You are signed out."), "neutral");
            }
        } else {
            if (lblStatus != null) {
                setStatus(lblStatus, I18n.t("myshiftSignOutNone", "No open sign-in was found for today."), "neutral");
            }
        }
    }

    private void updateLocalStatusLine() {
        if (lblStatus == null) {
            return;
        }
        if (MyShiftDAO.isPresentNow(Session.getUserId())) {
            setStatus(
                    lblStatus,
                    I18n.t("myshiftStatusIn", "You are still signed in (not signed out today)."),
                    "neutral"
            );
        } else {
            setStatus(
                    lblStatus,
                    I18n.t("myshiftStatusOut", "You are not signed in for today."),
                    "neutral"
            );
        }
    }

    private void updateCalMonthLabel() {
        if (lblCalMonth == null) {
            return;
        }
        Locale loc = LanguageManager.getLocale();
        String m = planMonth.getMonth().getDisplayName(TextStyle.FULL, loc);
        lblCalMonth.setText(
                m.substring(0, 1).toUpperCase(loc) + m.substring(1) + " " + planMonth.getYear()
        );
    }

    @FXML
    private void onCalPrev() {
        if (grdPlan == null) {
            return;
        }
        planMonth = planMonth.minusMonths(1);
        updateCalMonthLabel();
        reloadPlanGrid();
    }

    @FXML
    private void onCalNext() {
        if (grdPlan == null) {
            return;
        }
        planMonth = planMonth.plusMonths(1);
        updateCalMonthLabel();
        reloadPlanGrid();
    }

    private void reloadPlanGrid() {
        if (grdPlan == null) {
            return;
        }
        int uid = Session.getUserId();
        Map<LocalDate, String> m = MyShiftPlannedDAO.getMonthForUser(uid, planMonth);
        MyShiftMonthGridUtil.rebuild(
                grdPlan,
                planMonth,
                m,
                I18n.t("myshiftPlanEmptyDay", "—"),
                LanguageManager.getLocale()
        );
    }
}
