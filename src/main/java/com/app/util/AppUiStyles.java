package com.app.util;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

/**
 * Applies the shared {@code app.css} once per scene so windows look consistent and readable on any monitor.
 */
public final class AppUiStyles {

    private static final String APP_CSS =
            AppUiStyles.class.getResource("/style/app.css").toExternalForm();

    private AppUiStyles() {}

    /**
     * Muted, institutional colors for public-sector UI (replaces bright blues / cyans / neons
     * when Java code must use {@code setStyle} on table cells and small action buttons).
     */
    public static final class Gov {
        /** Main scene fill; avoids a default/cyan show-through on some LAFs. */
        public static final String SCENE_FILL = "#e8ebef";

        public static final String BTN_PRIMARY_11 = "-fx-background-color:#1e4d7a; -fx-text-fill:white; -fx-font-size:11;";
        public static final String BTN_WARN_11 = "-fx-background-color:#8a4f05; -fx-text-fill:white; -fx-font-size:11;";
        public static final String BTN_SUCCESS_11 = "-fx-background-color:#166534; -fx-text-fill:white; -fx-font-size:11;";
        public static final String BTN_NEUTRAL_11 = "-fx-background-color:#4b5563; -fx-text-fill:white; -fx-font-size:11;";
        public static final String BTN_MERGE_11 = "-fx-background-color:#4a3f69; -fx-text-fill:white; -fx-font-size:11;";
        public static final String BTN_DANGER_11 = "-fx-background-color:#9f1d1d; -fx-text-fill:white; -fx-font-size:11;";

        public static final String STATUS_ASSIGNED = "-fx-background-color:#1e4d7a; -fx-text-fill:white;";
        public static final String STATUS_IN_PROGRESS = "-fx-background-color:#8a4f05; -fx-text-fill:white;";
        public static final String STATUS_ESCALATED = "-fx-background-color:#9f1d1d; -fx-text-fill:white;";
        public static final String STATUS_CLOSED = "-fx-background-color:#166534; -fx-text-fill:white;";

        public static final String PRIORITY_LOW = "-fx-background-color:#3d5a4a; -fx-text-fill:white;";

        public static final String BTN_TABLE_ACTION = "-fx-background-color:#1e4d7a; -fx-text-fill:white;";
    }

    public static void applyToScene(Scene scene) {
        if (scene == null) {
            return;
        }
        if (APP_CSS != null && !scene.getStylesheets().contains(APP_CSS)) {
            scene.getStylesheets().add(APP_CSS);
        }
        scene.setFill(Color.web(Gov.SCENE_FILL));
    }
}
