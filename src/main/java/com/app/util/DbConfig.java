package com.app.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads {@code db.properties} from {@code sysco.data.dir} (or {@code user.dir}),
 * or classpath {@code /db.properties}. If missing, {@link DbVendor#SQLITE} is used.
 * <p>
 * For multiple PCs against one Oracle server: set {@code oracle.url} to the host that runs Oracle
 * (not {@code localhost} on each client). Optional overrides (highest precedence first):
 * system properties {@code sysco.db.vendor}, {@code sysco.oracle.url}, {@code sysco.oracle.user},
 * {@code sysco.oracle.password}; environment variables {@code SYSCO_DB_VENDOR}, {@code SYSCO_ORACLE_URL},
 * {@code SYSCO_ORACLE_USER}, {@code SYSCO_ORACLE_PASSWORD}.
 */
public final class DbConfig {

    private static volatile DbVendor vendor = DbVendor.SQLITE;
    private static volatile String sqliteJdbcUrl;
    private static volatile String oracleUrl;
    private static volatile String oracleUser;
    private static volatile String oraclePassword;

    private DbConfig() {}

    public static void load() {
        Properties p = new Properties();
        File base = new File(System.getProperty("sysco.data.dir", System.getProperty("user.dir")));
        File propsFile = new File(base, "db.properties");
        try {
            if (propsFile.isFile()) {
                try (FileInputStream fis = new FileInputStream(propsFile)) {
                    p.load(fis);
                }
            }
            if (p.isEmpty()) {
                try (InputStream cp = DbConfig.class.getResourceAsStream("/db.properties")) {
                    if (cp != null) {
                        p.load(cp);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        applyExternalOverrides(p);

        String v = p.getProperty("db.vendor", "sqlite").trim().toLowerCase();
        vendor = "oracle".equals(v) ? DbVendor.ORACLE : DbVendor.SQLITE;

        sqliteJdbcUrl = "jdbc:sqlite:" + new File(base, "app.db").getAbsolutePath().replace('\\', '/');

        oracleUrl = p.getProperty("oracle.url", "jdbc:oracle:thin:@//localhost:1521/XEPDB1").trim();
        oracleUser = p.getProperty("oracle.user", "").trim();
        oraclePassword = p.getProperty("oracle.password", "");

        SqlDialect.init(vendor);
    }

    public static DbVendor getVendor() {
        return vendor;
    }

    public static boolean isOracle() {
        return vendor == DbVendor.ORACLE;
    }

    public static String getSqliteJdbcUrl() {
        return sqliteJdbcUrl != null ? sqliteJdbcUrl : buildDefaultSqliteUrl();
    }

    private static String buildDefaultSqliteUrl() {
        File root = new File(System.getProperty("sysco.data.dir", System.getProperty("user.dir")));
        return "jdbc:sqlite:" + new File(root, "app.db").getAbsolutePath().replace('\\', '/');
    }

    public static String getOracleUrl() {
        return oracleUrl;
    }

    public static String getOracleUser() {
        return oracleUser;
    }

    public static String getOraclePassword() {
        return oraclePassword;
    }

    /**
     * System properties and env vars override file values so each PC can point at the same DB server
     * without editing {@code db.properties}, when desired.
     */
    private static void applyExternalOverrides(Properties p) {
        putOverride(p, "db.vendor", "sysco.db.vendor", "SYSCO_DB_VENDOR");
        putOverride(p, "oracle.url", "sysco.oracle.url", "SYSCO_ORACLE_URL");
        putOverride(p, "oracle.user", "sysco.oracle.user", "SYSCO_ORACLE_USER");
        putOverride(p, "oracle.password", "sysco.oracle.password", "SYSCO_ORACLE_PASSWORD");
    }

    private static void putOverride(Properties p, String key, String sysProp, String envVar) {
        String v = firstNonBlank(System.getProperty(sysProp), System.getenv(envVar));
        if (v != null) {
            p.setProperty(key, v);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null) {
            String t = a.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        if (b != null) {
            String t = b.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }
}
