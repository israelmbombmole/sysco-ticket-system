package com.app.util;

import java.util.ResourceBundle;

public final class I18n {

    private I18n() {}

    public static String t(String key) {
        return t(key, key);
    }

    public static String t(String key, String fallback) {
        try {
            ResourceBundle b = LanguageManager.getBundle();
            if (b != null && b.containsKey(key)) {
                return b.getString(key);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static String status(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return t("status." + status, status);
    }
}
