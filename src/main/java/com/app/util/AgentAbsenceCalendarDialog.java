package com.app.util;

import com.app.dao.UserAbsenceDAO;
import com.app.model.UserAbsence;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.beans.binding.Bindings;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Month calendar showing absence vs mission periods with distinct colors; long spans use alternating weekly shades.
 */
public final class AgentAbsenceCalendarDialog {

    /** Force readable text; app theme may set light labels on light backgrounds. */
    private static final String STYLE_MONTH_TITLE =
            "-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#1a1a1a;";
    private static final String STYLE_LEGEND_LABEL =
            "-fx-font-size:11px; -fx-text-fill:#2d2d2d;";
    private static final String STYLE_DIALOG_ROOT = "-fx-background-color:#eceff1;";
    private static final String STYLE_DAY_NUM = "-fx-font-weight:bold; -fx-text-fill:#1a1a1a;";

    private AgentAbsenceCalendarDialog() {}

    public static void show(Window owner, String username, int userId) {
        List<UserAbsence> absences = UserAbsenceDAO.listByUserId(userId);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(MessageFormat.format(
                I18n.t("leaveCalendarTitle", "Schedule — {0}"),
                username != null ? username : ""));

        AtomicReference<YearMonth> ymRef = new AtomicReference<>(YearMonth.from(LocalDate.now()));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setStyle(STYLE_DIALOG_ROOT);

        Label lblMonth = new Label();
        lblMonth.setStyle(STYLE_MONTH_TITLE);

        Button btnPrev = new Button(I18n.t("leaveCalendarPrev", "◀"));
        Button btnNext = new Button(I18n.t("leaveCalendarNext", "▶"));
        Button btnClose = new Button(I18n.t("close", "Close"));
        btnClose.setOnAction(e -> stage.close());

        HBox nav = new HBox(12, btnPrev, lblMonth, btnNext);
        nav.setAlignment(Pos.CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(8, 0, 0, 0));

        String[] dow = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
        if (Locale.getDefault().getLanguage().startsWith("fr")) {
            dow = new String[] { "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim" };
        }
        for (int i = 0; i < 7; i++) {
            Label h = new Label(dow[i]);
            h.setStyle("-fx-font-weight:bold; -fx-text-fill:#555;");
            grid.add(h, i, 0);
        }

        Runnable rebuild = () -> {
            YearMonth ym = ymRef.get();
            lblMonth.setText(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ym.getYear());
            while (grid.getChildren().size() > 7) {
                grid.getChildren().remove(grid.getChildren().size() - 1);
            }
            LocalDate first = ym.atDay(1);
            int col = first.getDayOfWeek().getValue() - 1;
            int row = 1;
            int daysInMonth = ym.lengthOfMonth();
            for (int d = 1; d <= daysInMonth; d++) {
                LocalDate day = ym.atDay(d);
                StackPane cell = buildDayCell(day, absences, null, null);
                grid.add(cell, col, row);
                col++;
                if (col > 6) {
                    col = 0;
                    row++;
                }
            }
        };

        btnPrev.setOnAction(e -> {
            ymRef.set(ymRef.get().minusMonths(1));
            rebuild.run();
        });
        btnNext.setOnAction(e -> {
            ymRef.set(ymRef.get().plusMonths(1));
            rebuild.run();
        });

        rebuild.run();

        FlowPane legend = buildLegendFlow(
                legendItem(I18n.t("leaveCalendarLegendMission", "Mission"), "#42a5f5"),
                legendItem(I18n.t("leaveCalendarLegendLeave", "Leave / holiday"), "#ff9800"),
                legendItem(I18n.t("leaveCalendarLegendAdmin", "Administrative absence"), "#9e9e9e"));

        HBox gridRow = new HBox(grid);
        gridRow.setAlignment(Pos.CENTER);

        VBox content = new VBox(12, nav, gridRow, legend);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(Region.USE_PREF_SIZE);
        content.setFillWidth(false);
        content.setStyle("-fx-background-color:transparent;");

        StackPane centerHolder = new StackPane(content);
        StackPane.setAlignment(content, Pos.CENTER);

        ScrollPane sp = new ScrollPane(centerHolder);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setPannable(false);
        sp.setStyle("-fx-background:transparent; -fx-background-color:#eceff1;");
        centerHolder.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, sp.getWidth() - 4),
                sp.widthProperty()));
        centerHolder.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, sp.getHeight() - 4),
                sp.heightProperty()));
        root.setCenter(sp);

        HBox bottomBar = new HBox(btnClose);
        bottomBar.setAlignment(Pos.CENTER_RIGHT);
        bottomBar.setPadding(new Insets(8, 0, 0, 0));
        bottomBar.setStyle("-fx-background-color:#eceff1;");
        root.setBottom(bottomBar);

        Scene scene = new Scene(root, 600, 540);
        AppUiStyles.applyToScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();
        javafx.application.Platform.runLater(stage::centerOnScreen);
    }

    /**
     * Read-only month navigator highlighting only the mission date range (same colors as mission absences).
     * Safe when {@code start} or {@code end} is null (empty calendar).
     */
    public static VBox createMissionPeriodCalendarNode(LocalDate start, LocalDate end) {
        VBox wrap = new VBox(8);
        wrap.setPadding(new Insets(4, 0, 0, 0));
        if (start == null || end == null) {
            Label empty = new Label(I18n.t("missionOverviewNoDates", "No start/end dates for this mission."));
            empty.setStyle("-fx-text-fill:#666;");
            wrap.getChildren().add(empty);
            return wrap;
        }
        LocalDate s = start.isBefore(end) ? start : end;
        LocalDate e = end.isBefore(start) ? start : end;

        AtomicReference<YearMonth> ymRef = new AtomicReference<>(YearMonth.from(s));

        Label lblMonth = new Label();
        lblMonth.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#1a1a1a;");

        Button btnPrev = new Button(I18n.t("leaveCalendarPrev", "◀"));
        Button btnNext = new Button(I18n.t("leaveCalendarNext", "▶"));
        HBox nav = new HBox(12, btnPrev, lblMonth, btnNext);
        nav.setAlignment(Pos.CENTER);

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(8, 0, 0, 0));

        String[] dow = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
        if (Locale.getDefault().getLanguage().startsWith("fr")) {
            dow = new String[] { "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim" };
        }
        for (int i = 0; i < 7; i++) {
            Label h = new Label(dow[i]);
            h.setStyle("-fx-font-weight:bold; -fx-text-fill:#555;");
            grid.add(h, i, 0);
        }

        Runnable rebuild = () -> {
            YearMonth ym = ymRef.get();
            lblMonth.setText(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + ym.getYear());
            while (grid.getChildren().size() > 7) {
                grid.getChildren().remove(grid.getChildren().size() - 1);
            }
            LocalDate first = ym.atDay(1);
            int col = first.getDayOfWeek().getValue() - 1;
            int row = 1;
            int daysInMonth = ym.lengthOfMonth();
            for (int d = 1; d <= daysInMonth; d++) {
                LocalDate day = ym.atDay(d);
                StackPane cell = buildDayCell(day, Collections.emptyList(), s, e);
                grid.add(cell, col, row);
                col++;
                if (col > 6) {
                    col = 0;
                    row++;
                }
            }
        };

        btnPrev.setOnAction(ev -> {
            ymRef.set(ymRef.get().minusMonths(1));
            rebuild.run();
        });
        btnNext.setOnAction(ev -> {
            ymRef.set(ymRef.get().plusMonths(1));
            rebuild.run();
        });

        rebuild.run();

        FlowPane legend = buildLegendFlow(
                legendItem(I18n.t("leaveCalendarLegendMission", "Mission"), "#42a5f5"));

        HBox gridRow = new HBox(grid);
        gridRow.setAlignment(Pos.CENTER);

        VBox block = new VBox(10, nav, gridRow, legend);
        block.setAlignment(Pos.CENTER);
        block.setMaxWidth(Region.USE_PREF_SIZE);
        block.setStyle("-fx-background-color:transparent;");
        StackPane holder = new StackPane(block);
        StackPane.setAlignment(block, Pos.CENTER);
        wrap.setStyle("-fx-background-color:transparent;");
        wrap.getChildren().add(holder);
        wrap.setAlignment(Pos.CENTER);
        return wrap;
    }

    /** One row when space allows; wraps so every legend line stays visible. */
    private static FlowPane buildLegendFlow(HBox... rows) {
        FlowPane legend = new FlowPane(16, 10);
        legend.setAlignment(Pos.CENTER);
        legend.setPadding(new Insets(12, 8, 4, 8));
        legend.setPrefWrapLength(560);
        legend.setStyle("-fx-background-color:transparent;");
        legend.getChildren().addAll(rows);
        return legend;
    }

    private static HBox legendItem(String text, String color) {
        HBox h = new HBox(8);
        h.setAlignment(Pos.CENTER_LEFT);
        Rectangle r = new Rectangle(28, 14);
        r.setFill(Color.web(color));
        Label l = new Label(text + " · " + I18n.t("leaveCalendarWeekAlt", "alternating weeks"));
        l.setStyle(STYLE_LEGEND_LABEL);
        l.setWrapText(false);
        l.setMinWidth(Region.USE_PREF_SIZE);
        h.getChildren().addAll(r, l);
        return h;
    }

    private static StackPane buildDayCell(LocalDate day, List<UserAbsence> absences,
            LocalDate missionHighlightStart, LocalDate missionHighlightEnd) {
        Label num = new Label(String.valueOf(day.getDayOfMonth()));
        num.setStyle(STYLE_DAY_NUM);

        String bg = "#ffffff";
        String border = "#e0e0e0";

        if (missionHighlightStart != null && missionHighlightEnd != null
                && !day.isBefore(missionHighlightStart) && !day.isAfter(missionHighlightEnd)) {
            boolean altWeek = weekStripeIndex(missionHighlightStart, day) % 2 == 0;
            bg = altWeek ? "#90caf9" : "#42a5f5";
            border = "#1565c0";
            StackPane pane = new StackPane(num);
            pane.setMinSize(36, 32);
            pane.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:4; -fx-border-color:" + border
                    + "; -fx-border-radius:4; -fx-border-width:1;");
            return pane;
        }

        for (UserAbsence a : absences) {
            LocalDate s = parseIso(a.getStartDate());
            LocalDate e = parseIso(a.getEndDate());
            if (s == null || e == null) {
                continue;
            }
            if (!day.isBefore(s) && !day.isAfter(e)) {
                String type = a.getAbsenceType() != null ? a.getAbsenceType().trim().toUpperCase() : "";
                boolean mission = UserAbsence.TYPE_MISSION.equalsIgnoreCase(type);
                boolean adminAbs = UserAbsence.TYPE_ABSENCE.equalsIgnoreCase(type);
                boolean altWeek = weekStripeIndex(s, day) % 2 == 0;
                if (mission) {
                    bg = altWeek ? "#90caf9" : "#42a5f5";
                    border = "#1565c0";
                } else if (adminAbs) {
                    bg = altWeek ? "#e0e0e0" : "#bdbdbd";
                    border = "#616161";
                } else {
                    bg = altWeek ? "#ffcc80" : "#ff9800";
                    border = "#e65100";
                }
                break;
            }
        }

        StackPane pane = new StackPane(num);
        pane.setMinSize(36, 32);
        pane.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:4; -fx-border-color:" + border
                + "; -fx-border-radius:4; -fx-border-width:1;");
        return pane;
    }

    /** Week index from period start (0 = first week) for alternating colors within a long absence. */
    private static long weekStripeIndex(LocalDate periodStart, LocalDate day) {
        long days = ChronoUnit.DAYS.between(periodStart, day);
        return Math.floorDiv(days, 7);
    }

    private static LocalDate parseIso(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String t = raw.trim();
            return LocalDate.parse(t.length() >= 10 ? t.substring(0, 10) : t);
        } catch (Exception e) {
            return null;
        }
    }
}
