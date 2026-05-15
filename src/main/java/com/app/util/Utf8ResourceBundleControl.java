package com.app.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/** Loads {@code .properties} as UTF-8 so accents are correct regardless of file encoding. */
public final class Utf8ResourceBundleControl extends ResourceBundle.Control {

    @Override
    public List<String> getFormats(String baseName) {
        if (baseName == null) {
            throw new NullPointerException();
        }
        return FORMAT_PROPERTIES;
    }

    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
            throws IllegalAccessException, InstantiationException, IOException {
        if (!"java.properties".equals(format)) {
            return null;
        }
        String bundleName = toBundleName(baseName, locale);
        String resourceName = toResourceName(bundleName, "properties");
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            if (stream == null) {
                return null;
            }
            return new PropertyResourceBundle(
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }
}
