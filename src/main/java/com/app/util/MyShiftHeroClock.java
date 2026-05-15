package com.app.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Live clock in MyShift page headers (SIRH / pointage style). Stop the returned
 * {@link Timeline} when the view is no longer in the scene graph.
 */
public final class MyShiftHeroClock {

    private MyShiftHeroClock() {}

    /** @return a running 1s timeline, or {@code null} if {@code timeLabel} is null */
    public static Timeline install(Label timeLabel) {
        if (timeLabel == null) {
            return null;
        }
        tick(timeLabel);
        Timeline t = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> tick(timeLabel))
        );
        t.setCycleCount(Animation.INDEFINITE);
        t.play();
        return t;
    }

    private static void tick(Label timeLabel) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm:ss")
                .withLocale(LanguageManager.getLocale());
        timeLabel.setText(f.format(LocalTime.now()));
    }
}
