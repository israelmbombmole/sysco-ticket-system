package com.app.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/** Simple month grid (Mon..Sun) with one shift code per day (read-only). */
public final class MyShiftMonthGridUtil {

    private MyShiftMonthGridUtil() {}

    public static void rebuild(
            GridPane grid,
            YearMonth ym,
            Map<LocalDate, String> byDay,
            String emptyText,
            Locale loc) {
        if (grid == null || ym == null) {
            return;
        }
        String empty = emptyText != null ? emptyText : "—";
        grid.getChildren().clear();
        for (int i = 0; i < 7; i++) {
            DayOfWeek w = (i < 6) ? DayOfWeek.of(i + 1) : DayOfWeek.SUNDAY;
            Label h = new Label(w.getDisplayName(TextStyle.NARROW, loc));
            h.getStyleClass().add("myshift-teams-mcal-head");
            h.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHalignment(h, HPos.CENTER);
            grid.add(h, i, 0);
        }
        int len = ym.lengthOfMonth();
        int offset = (ym.atDay(1).getDayOfWeek().getValue() - 1) % 7;
        for (int day = 1; day <= len; day++) {
            int idx = offset + day - 1;
            int r = 1 + idx / 7;
            int c = idx % 7;
            LocalDate date = ym.atDay(day);
            String code = byDay.get(date);
            boolean has = code != null && !code.isBlank();
            String text = day + "\n" + (has ? code : empty);
            Label cell = new Label(text);
            cell.setWrapText(true);
            cell.setMaxWidth(Double.MAX_VALUE);
            cell.getStyleClass().add("myshift-teams-mcal-cell");
            if (has) {
                cell.getStyleClass().add("myshift-teams-mcal-cell-filled");
            }
            GridPane.setHalignment(cell, HPos.CENTER);
            grid.add(cell, c, r);
        }
    }
}
