package com.app.util;

import java.util.Set;

/**
 * Module permissions stored as {@code BASE_READ} and {@code BASE_WRITE} in {@code user_permissions}.
 * Legacy rows with only {@code BASE} (no suffix) grant both read and write.
 * Write implies read for access checks.
 */
public final class ModuleAccess {

    public static final String SUFFIX_READ = "_READ";
    public static final String SUFFIX_WRITE = "_WRITE";

    private ModuleAccess() {}

    public static String readKey(String base) {
        return base + SUFFIX_READ;
    }

    public static String writeKey(String base) {
        return base + SUFFIX_WRITE;
    }

    /** Legacy full access: plain {@code base} key without suffix. */
    public static boolean hasLegacyFull(Set<String> perms, String base) {
        return perms != null && base != null && perms.contains(base);
    }

    public static boolean canRead(Set<String> perms, String base) {
        if (perms == null || base == null) {
            return false;
        }
        if (perms.contains(base)) {
            return true;
        }
        if (perms.contains(readKey(base))) {
            return true;
        }
        return perms.contains(writeKey(base));
    }

    public static boolean canWrite(Set<String> perms, String base) {
        if (perms == null || base == null) {
            return false;
        }
        if (perms.contains(base)) {
            return true;
        }
        return perms.contains(writeKey(base));
    }

    public static boolean readExplicit(Set<String> perms, String base) {
        return perms != null && base != null && perms.contains(readKey(base));
    }

    public static boolean writeExplicit(Set<String> perms, String base) {
        return perms != null && base != null && perms.contains(writeKey(base));
    }
}
