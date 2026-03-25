package com.app.util;

import com.app.auth.Session;

public class PermissionUtil {

    public static boolean has(String permission) {

        // 🔥 DIRECTEUR = FULL ACCESS
        if (Session.getRole().equals("DIRECTEUR")) {
            return true;
        }

        return Session.getPermissions().contains(permission);
    }
}