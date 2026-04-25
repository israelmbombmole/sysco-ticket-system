package com.app;

import com.app.util.DB;
import com.app.util.LanguageManager;
import java.io.File;
import java.util.TimeZone;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        DB.init();
        new File("uploads").mkdirs();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/login.fxml"),
                LanguageManager.getBundle()
        );

        Scene scene = new Scene(loader.load());

        stage.setTitle(LanguageManager.getBundle().getString("appName") + " 1.0");

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.min(1300, visualBounds.getWidth() * 0.92);
        double initialHeight = Math.min(800, visualBounds.getHeight() * 0.92);
        stage.setWidth(Math.max(860, initialWidth));
        stage.setHeight(Math.max(560, initialHeight));
        stage.setMinWidth(640);
        stage.setMinHeight(420);

        // Allow resizing
        stage.setResizable(true);

        stage.setScene(scene);
        stage.centerOnScreen();

        stage.show();
    }

    public static void main(String[] args) {

        // 🔥 Set timezone BEFORE JavaFX starts
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Kinshasa"));

        // Optional debug (you can remove later)
        System.out.println("Java Timezone: " + java.time.ZoneId.systemDefault());

        launch(args);
    }
}