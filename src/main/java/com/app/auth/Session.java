package com.app.auth;

import com.app.model.User;
import java.util.HashSet;
import java.util.Set;

public class Session {

    private static int userId;
    private static String username;
    private static String role;
    private static String sousDirection;
    private static Integer sousDirectionId;
    private static Integer directionId;
    private static boolean superAdmin;
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
        sousDirectionId = user.getSousDirectionId();
        directionId = user.getDirectionId();
        superAdmin = user.isSuperAdmin();

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

    public static Integer getSousDirectionId() {
        return sousDirectionId;
    }

    public static Integer getDirectionId() {
        return directionId;
    }

    public static boolean isSuperAdmin() {
        return superAdmin;
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
        sousDirectionId = null;
        directionId = null;
        superAdmin = false;
    }
    

public static void setPermissions(Set<String> perms) {
    permissions = perms;
}

public static Set<String> getPermissions() {
    return permissions;
}


    
    
}