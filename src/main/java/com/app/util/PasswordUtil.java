package com.app.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {

    // =========================
    // 🔐 HASH PASSWORD
    // =========================
    public static String hash(String plainPassword) {

        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }

        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    // =========================
    // 🔐 VERIFY PASSWORD
    // =========================
    public static boolean verify(String plainPassword, String hashedPassword) {

        if (plainPassword == null || hashedPassword == null) {
            return false;
        }

        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // =========================
    // 🔐 OPTIONAL: PASSWORD VALIDATION
    // =========================
    public static boolean isStrongPassword(String password) {

        if (password == null) return false;

        // Minimum rules (you can adjust)
        return password.length() >= 6;
    }
}