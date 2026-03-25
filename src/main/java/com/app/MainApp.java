package com.app;

import com.app.util.DB;
import java.io.File;
import java.util.TimeZone;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        DB.init();
        new File("uploads").mkdirs();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/view/login.fxml")
        );

        Scene scene = new Scene(loader.load());

        stage.setTitle("SYSCO 1.0");

        // Default size
        stage.setWidth(1300);
        stage.setHeight(800);

        // Prevent layouts from breaking
        stage.setMinWidth(1100);
        stage.setMinHeight(700);

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