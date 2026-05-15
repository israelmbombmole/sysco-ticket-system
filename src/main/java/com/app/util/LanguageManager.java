package com.app.util;

import java.util.Locale;
import java.util.ResourceBundle;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class LanguageManager {

    private static final ObjectProperty<Locale> locale =
            new SimpleObjectProperty<>(Locale.FRENCH);

    public static void setLocale(Locale newLocale) {
        locale.set(newLocale);
    }

    public static Locale getLocale() {
        return locale.get();
    }

    public static ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    private static final Utf8ResourceBundleControl UTF8_CONTROL = new Utf8ResourceBundleControl();

    public static ResourceBundle getBundle() {
        return ResourceBundle.getBundle("lang.messages", locale.get(), UTF8_CONTROL);
    }
}