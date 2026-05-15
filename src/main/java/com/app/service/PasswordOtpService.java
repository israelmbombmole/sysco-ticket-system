package com.app.service;

import java.security.SecureRandom;

/**
 * One-time password for AD-style "forgot password" / reset (short code, single use, expires).
 */
public final class PasswordOtpService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 5;
    private static final SecureRandom RND = new SecureRandom();

    private PasswordOtpService() {
    }

    public static String generatePlainCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[RND.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
