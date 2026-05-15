package com.app.service.myshift;

import com.app.util.DbConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * MyShift: optional face API and geo settings in {@code db.properties} (same search path as
 * {@link DbConfig}).
 */
public final class MyShiftConfig {

    private static volatile Properties loaded;

    private MyShiftConfig() {}

    public static String faceApiUrl() {
        return get("myshift.face.api.url", "");
    }

    public static String faceApiKey() {
        return get("myshift.face.api.key", "");
    }

    public static int faceMinConfidencePercent() {
        String v = get("myshift.face.minConfidencePercent", "60");
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(v.trim())));
        } catch (Exception e) {
            return 60;
        }
    }

    private static String get(String key, String dflt) {
        if (String.valueOf(System.getProperty(key, "")).trim().length() > 0) {
            return System.getProperty(key, dflt).trim();
        }
        String env = key.toUpperCase().replace('.', '_');
        String e = System.getenv(env);
        if (e != null && !e.isBlank()) {
            return e.trim();
        }
        Properties p = ensureProps();
        return p.getProperty(key, dflt) != null ? p.getProperty(key, dflt).trim() : dflt;
    }

    private static Properties ensureProps() {
        Properties c = loaded;
        if (c != null) {
            return c;
        }
        synchronized (MyShiftConfig.class) {
            if (loaded != null) {
                return loaded;
            }
            Properties p = new Properties();
            File base = new File(System.getProperty("sysco.data.dir", System.getProperty("user.dir")));
            File f = new File(base, "db.properties");
            try {
                if (f.isFile()) {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        p.load(fis);
                    }
                }
                if (p.isEmpty()) {
                    try (InputStream cp = MyShiftConfig.class.getResourceAsStream("/db.properties")) {
                        if (cp != null) {
                            p.load(cp);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            loaded = p;
            return p;
        }
    }
}
