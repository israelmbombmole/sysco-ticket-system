package com.app.dao;

import com.app.model.Notification;
import com.app.util.DB;
import com.app.util.SqlDialect;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    // ============================
    // CREATE NOTIFICATION
    // ============================
    public static void create(int userId, String title, String message, String type) {
        create(userId, title, message, type, null, null, null);
    }

    public static void create(int userId, String title, String message, String type,
                              String targetType, Integer targetId, String targetRef) {

    String sql = """
            INSERT INTO notifications(user_id,title,"message",type,target_type,target_id,target_ref)
            VALUES(?,?,?,?,?,?,?)
            """;

    for (int attempt = 1; attempt <= 8; attempt++) {
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(true);

            ps.setInt(1, userId);
            ps.setString(2, title);
            ps.setString(3, message);
            ps.setString(4, type);
            ps.setString(5, targetType);
            if (targetId == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, targetId);
            }
            ps.setString(7, targetRef);

            ps.executeUpdate();
            return;

        } catch (Exception e) {
            boolean busy = isBusyError(e);
            if (busy && attempt < 8) {
                try {
                    Thread.sleep(120L * attempt);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }
            System.err.println("Error creating notification");
            e.printStackTrace();
            return;
        }
    }
}

    private static boolean isBusyError(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current.getClass().getName().endsWith("SQLiteException")) {
                String msg = current.getMessage();
                if (msg != null && (msg.contains("SQLITE_BUSY") || msg.contains("SQLITE_BUSY_SNAPSHOT")
                        || msg.contains("SQLITE_LOCKED"))) {
                    return true;
                }
            } else {
                String msg = current.getMessage();
                if (msg != null && (msg.contains("SQLITE_BUSY") || msg.contains("SQLITE_LOCKED"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
    
    
    public static void markAllRead(int userId){

    String sql = """
        UPDATE notifications
        SET is_read=1
        WHERE user_id=?
    """;

    try(Connection conn = DB.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)){

        ps.setInt(1,userId);
        ps.executeUpdate();

    }catch(Exception e){
        e.printStackTrace();
    }
}
    

    // ============================
    // GET UNREAD NOTIFICATIONS
    // ============================
    public static List<Notification> getUnread(int userId) {

        List<Notification> list = new ArrayList<>();

        String sql = """
                SELECT id, user_id, title, "message" AS message_text, type, is_read, created_at, target_type, target_id, target_ref
                FROM notifications
                WHERE user_id=?
                AND is_read=0
                ORDER BY id DESC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                list.add(new Notification(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("title"),
                        rs.getString("message_text"),
                        rs.getString("type"),
                        rs.getInt("is_read"),
                        rs.getString("created_at"),
                        rs.getString("target_type"),
                        rs.getObject("target_id") != null ? rs.getInt("target_id") : null,
                        rs.getString("target_ref")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ============================
    // GET ALL NOTIFICATIONS
    // ============================
    public static List<Notification> getAll(int userId) {

        List<Notification> list = new ArrayList<>();

        String sql = """
                SELECT id, user_id, title, "message" AS message_text, type, is_read, created_at, target_type, target_id, target_ref
                FROM notifications
                WHERE user_id=?
                ORDER BY id DESC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                list.add(new Notification(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("title"),
                        rs.getString("message_text"),
                        rs.getString("type"),
                        rs.getInt("is_read"),
                        rs.getString("created_at"),
                        rs.getString("target_type"),
                        rs.getObject("target_id") != null ? rs.getInt("target_id") : null,
                        rs.getString("target_ref")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ============================
    // GET UNREAD COUNT (for badge)
    // ============================
    public static int getUnreadCount(int userId) {

        String sql = """
                SELECT COUNT(*) FROM notifications
                WHERE user_id=? AND is_read=0
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    // ============================
    // MARK NOTIFICATION AS READ
    // ============================
    public static void markRead(int id) {

        String sql = "UPDATE notifications SET is_read=1 WHERE id=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final String TYPE_OTP_MGMT_REQUEST = "DATASHARE_MGMT_OTP_REQUEST";

    /**
     * Unread File Share Management OTP requests addressed to this admin.
     */
    public static List<Notification> getUnreadOtpManagementRequests(int adminUserId) {

        List<Notification> list = new ArrayList<>();

        String sql = """
                SELECT id, user_id, title, "message" AS message_text, type, is_read, created_at, target_type, target_id, target_ref
                FROM notifications
                WHERE user_id=?
                AND is_read=0
                AND type=?
                ORDER BY id DESC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, adminUserId);
            ps.setString(2, TYPE_OTP_MGMT_REQUEST);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                list.add(new Notification(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("title"),
                        rs.getString("message_text"),
                        rs.getString("type"),
                        rs.getInt("is_read"),
                        rs.getString("created_at"),
                        rs.getString("target_type"),
                        rs.getObject("target_id") != null ? rs.getInt("target_id") : null,
                        rs.getString("target_ref")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    /**
     * Recent File Share Management OTP requests for this admin (read and unread), newest first.
     */
    public static List<Notification> getOtpManagementRequestsForAdmin(int adminUserId, int maxRows) {

        List<Notification> list = new ArrayList<>();

        if (maxRows <= 0) {
            maxRows = 200;
        }

        String sql = """
                SELECT id, user_id, title, "message" AS message_text, type, is_read, created_at, target_type, target_id, target_ref
                FROM notifications
                WHERE user_id=? AND type=?
                ORDER BY id DESC
                """ + SqlDialect.limitRows(maxRows);

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, adminUserId);
            ps.setString(2, TYPE_OTP_MGMT_REQUEST);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                list.add(new Notification(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("title"),
                        rs.getString("message_text"),
                        rs.getString("type"),
                        rs.getInt("is_read"),
                        rs.getString("created_at"),
                        rs.getString("target_type"),
                        rs.getObject("target_id") != null ? rs.getInt("target_id") : null,
                        rs.getString("target_ref")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    /**
     * Clears pending OTP request rows for all admins once an OTP has been issued to this user.
     */
    public static void dismissUnreadOtpManagementRequestsForRequester(int requesterUserId) {

        String sql = """
                UPDATE notifications SET is_read=1
                WHERE is_read=0 AND type=? AND target_id=?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, TYPE_OTP_MGMT_REQUEST);
            ps.setInt(2, requesterUserId);
            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes one File Share Management OTP request row; only if it belongs to this admin.
     */
    public static boolean deleteOtpManagementRequestForAdmin(int notificationId, int adminUserId) {

        String sql = """
                DELETE FROM notifications
                WHERE id=? AND user_id=? AND type=?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, notificationId);
            ps.setInt(2, adminUserId);
            ps.setString(3, TYPE_OTP_MGMT_REQUEST);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}