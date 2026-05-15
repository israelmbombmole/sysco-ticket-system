package com.app.util;

import com.app.dao.NotificationDAO;
import com.app.dao.UserDAO;
import com.app.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.prefs.Preferences;

public final class DataShareManagementOtpService {

    private DataShareManagementOtpService() {}

    private static final long OTP_TTL_MILLIS = 10L * 60L * 1000L;      // 10 minutes

    public static final int MIN_SESSION_MINUTES = 1;
    public static final int MAX_SESSION_MINUTES = 20;
    private static final int DEFAULT_SESSION_MINUTES = 20;

    private static Preferences prefs() {
        return Preferences.userRoot().node("com/app/sysco/datashare_mgmt_otp");
    }

    /**
     * How long (after successful OTP) a user may stay on File Share Management. Admin-configurable, 1–20 minutes.
     */
    public static int getSessionDurationMinutes() {
        int v = prefs().getInt("sessionMinutes", DEFAULT_SESSION_MINUTES);
        return Math.max(MIN_SESSION_MINUTES, Math.min(MAX_SESSION_MINUTES, v));
    }

    public static void setSessionDurationMinutes(int minutes) {
        int v = Math.max(MIN_SESSION_MINUTES, Math.min(MAX_SESSION_MINUTES, minutes));
        prefs().putInt("sessionMinutes", v);
    }

    private static long accessTtlMillis() {
        return getSessionDurationMinutes() * 60_000L;
    }

    private record OtpEntry(String otpHash, long expiresAt) {}

    private static final ConcurrentHashMap<Integer, OtpEntry> pendingOtpByUser = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Long> accessGrantByUser = new ConcurrentHashMap<>();

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("OTP hashing failed", e);
        }
    }

    private static String generateOtp() {
        int n = ThreadLocalRandom.current().nextInt(100000, 1_000_000);
        return String.valueOf(n);
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        pendingOtpByUser.entrySet().removeIf(e -> e.getValue() == null || e.getValue().expiresAt() <= now);
        accessGrantByUser.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= now);
    }

    public static boolean hasActiveAccess(int userId) {
        cleanupExpired();
        Long expiresAt = accessGrantByUser.get(userId);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    public static void requestOtpFromAdmin(int requesterId, String requesterUsername) {
        List<User> admins = UserDAO.getAllActiveAdminsGlobal();
        for (User user : admins) {
            if (user == null) {
                continue;
            }
            NotificationDAO.create(
                    user.getId(),
                    "OTP Request",
                    requesterUsername + " requested OTP for File Share Management access.",
                    "DATASHARE_MGMT_OTP_REQUEST",
                    "DATASHARE_MGMT_OTP_REQUEST",
                    requesterId,
                    requesterUsername
            );
        }
    }

    public static String issueOtpForUser(int requesterId, int adminId, String adminUsername) {
        String otp = generateOtp();
        String hash = sha256(otp);
        long expiresAt = System.currentTimeMillis() + OTP_TTL_MILLIS;
        pendingOtpByUser.put(requesterId, new OtpEntry(hash, expiresAt));

        int sessionMin = getSessionDurationMinutes();
        NotificationDAO.create(
                requesterId,
                "OTP Issued",
                adminUsername + " generated OTP for File Share Management access. OTP: " + otp
                        + " (session " + sessionMin + " min)",
                "DATASHARE_MGMT_OTP",
                "DATASHARE_MGMT_OTP",
                null,
                null
        );

        NotificationDAO.create(
                adminId,
                "OTP Generated",
                "OTP generated for user ID " + requesterId + ".",
                "DATASHARE_MGMT_OTP_ADMIN",
                "DATASHARE_MGMT_OTP_ADMIN",
                requesterId,
                null
        );

        NotificationDAO.dismissUnreadOtpManagementRequestsForRequester(requesterId);

        return otp;
    }

    public static boolean verifyOtpAndGrant(int userId, String otpInput) {
        if (otpInput == null || otpInput.isBlank()) {
            return false;
        }
        cleanupExpired();
        OtpEntry entry = pendingOtpByUser.get(userId);
        if (entry == null) {
            return false;
        }
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            pendingOtpByUser.remove(userId);
            return false;
        }
        if (!sha256(otpInput.trim()).equals(entry.otpHash())) {
            return false;
        }
        pendingOtpByUser.remove(userId);
        accessGrantByUser.put(userId, System.currentTimeMillis() + accessTtlMillis());
        return true;
    }
}

