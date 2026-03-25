package com.app.util;

import java.util.Locale;
import java.util.ResourceBundle;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class LanguageManager {

    private static final ObjectProperty<Locale> locale =
            new SimpleObjectProperty<>(Locale.ENGLISH);

    public static void setLocale(Locale newLocale) {
        locale.set(newLocale);
    }

    public static Locale getLocale() {
        return locale.get();
    }

    public static ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    public static ResourceBundle getBundle() {
        return ResourceBundle.getBundle("lang.messages", locale.get());
    }
}