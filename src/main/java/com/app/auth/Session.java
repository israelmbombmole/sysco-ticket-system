package com.app.auth;

import com.app.model.User;
import java.util.HashSet;
import java.util.Set;

public class Session {

    private static int userId;
    private static String username;
    private static String role;
    private static String sousDirection;
    private static Set<String> permissions = new HashSet<>();

    // ==========================
    // LOGIN SESSION
    // ==========================

    public static void set(User user) {

    if (user != null) {
        userId = user.getId();
        username = user.getUsername();
        role = user.getRole();
        sousDirection = user.getSousDirection();

        System.out.println("SESSION SET → ID=" + userId + " USER=" + username);
    }
}

    // ==========================
    // GETTERS
    // ==========================

    public static int getUserId() {
        return userId;
    }

    public static String getUsername() {
        return username;
    }

    public static String getRole() {
        return role;
    }

    public static String getSousDirection() {
        return sousDirection;
    }

    // ==========================
    // LOGIN CHECK
    // ==========================

    public static boolean isLoggedIn() {
        return userId != 0;
    }

    // ==========================
    // CLEAR SESSION
    // ==========================

    public static void clear() {
        userId = 0;
        username = null;
        role = null;
        sousDirection = null;
    }
    

public static void setPermissions(Set<String> perms) {
    permissions = perms;
}

public static Set<String> getPermissions() {
    return permissions;
}


    
    
}