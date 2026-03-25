package com.app.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String timeAgo(String timestamp) {

        try {

            LocalDateTime created =
                    LocalDateTime.parse(timestamp.substring(0,19), formatter);

            // Use PC system time
            LocalDateTime now = LocalDateTime.now();

            Duration duration = Duration.between(created, now);

            long seconds = Math.abs(duration.getSeconds());

            if (seconds < 60)
                return "just now";

            long minutes = seconds / 60;

            if (minutes < 60)
                return minutes + " min ago";

            long hours = minutes / 60;

            if (hours < 24)
                return hours + " hr ago";

            long days = hours / 24;

            if (days == 1)
                return "yesterday";

            return days + " days ago";

        } catch (Exception e) {
            return "";
        }
    }
}