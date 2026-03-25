package com.app.session;

public class LoggedUser {

    private static int id;
    private static String username;
    private static String role;

    public static void setId(int userId) {
        id = userId;
    }

    public static int getId() {
        return id;
    }

    public static void setUsername(String u) {
        username = u;
    }

    public static String getUsername() {
        return username;
    }

    public static void setRole(String r) {
        role = r;
    }

    public static String getRole() {
        return role;
    }
}


