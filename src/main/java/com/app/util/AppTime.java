package com.app.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class AppTime {

    // Force UTC+1
    private static final ZoneId APP_ZONE = ZoneId.of("UTC+1");

    private static final DateTimeFormatter DB_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Always return time in UTC+1
    public static LocalDateTime now() {
        return ZonedDateTime.now(APP_ZONE).toLocalDateTime();
    }

    // Format time for database
    public static String nowDB() {
        return now().format(DB_FORMAT);
    }

    // Parse database timestamp
    public static LocalDateTime parse(String timestamp) {
        return LocalDateTime.parse(timestamp.substring(0, 19), DB_FORMAT);
    }
}