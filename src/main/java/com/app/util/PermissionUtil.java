package com.app.util;

import com.app.auth.Session;

import java.util.Set;

public class PermissionUtil {

    public static boolean has(String permission) {
        Set<String> p = Session.getPermissions();
        return p != null && p.contains(permission);
    }

    /** Module base key, e.g. {@code "DATA_ENTRY"} — accepts legacy plain keys and {@code BASE_READ} / {@code BASE_WRITE}. */
    public static boolean canReadModule(String moduleBase) {
        return ModuleAccess.canRead(Session.getPermissions(), moduleBase);
    }

    public static boolean canWriteModule(String moduleBase) {
        return ModuleAccess.canWrite(Session.getPermissions(), moduleBase);
    }
}