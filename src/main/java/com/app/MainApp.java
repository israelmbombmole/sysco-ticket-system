package com.app;

import com.app.service.AutomationSchedulerService;
import com.app.util.AppUiStyles;
import com.app.util.DB;
import com.app.util.LanguageManager;
import java.io.File;
import java.util.Locale;
import java.util.TimeZone;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Window sizing matches {@code C:/sqlite/sysco-ticket-system-preview}: work-area-based initial size
 * with caps, floors, and fixed minimum dimensions.
 */
public class MainApp extends Application {

    /** Same logic as the preview app's {@code MainApp.start} stage sizing. */
    public static void applyPreviewWindowSize(Stage stage) {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.min(1300, visualBounds.getWidth() * 0.92);
        double initialHeight = Math.min(800, visualBounds.getHeight() * 0.92);
        stage.setWidth(Math.max(860, initialWidth));
        stage.setHeight(Math.max(560, initialHeight));
        stage.setMinWidth(640);
        stage.setMinHeight(420);
    }

    @Override
    public void start(Stage stage) throws Exception {

        DB.init();
        AutomationSchedulerService.start();
        File dataRoot = new File(System.getProperty("sysco.data.dir", System.getProperty("user.dir")));
        dataRoot.mkdirs();
        new File(dataRoot, "uploads").mkdirs();
        new File(dataRoot, "uploads/missions").mkdirs();
        // Always start from French on login page.
        LanguageManager.setLocale(Locale.FRENCH);

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/login.fxml"),
                LanguageManager.getBundle()
        );

        Scene scene = new Scene(loader.load());
        AppUiStyles.applyToScene(scene);

        stage.setTitle(LanguageManager.getBundle().getString("appTitle"));
        var iconUrl = getClass().getResource("/images/app-icon.png");
        if (iconUrl != null) {
            try {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            } catch (Exception ignored) { }
        }

        applyPreviewWindowSize(stage);
        stage.setResizable(true);

        stage.setScene(scene);
        stage.centerOnScreen();

        stage.show();
    }

    public static void main(String[] args) {

        // Set timezone BEFORE JavaFX starts
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Kinshasa"));

        System.out.println("Java Timezone: " + java.time.ZoneId.systemDefault());

        launch(args);
    }
}
