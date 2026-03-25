package com.app.dao;

import com.app.model.Notification;
import com.app.util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {

    // ============================
    // CREATE NOTIFICATION
    // ============================
    public static void create(int userId, String title, String message, String type) {

    String sql = """
            INSERT INTO notifications(user_id,title,message,type)
            VALUES(?,?,?,?)
            """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        conn.setAutoCommit(true);

        ps.setInt(1, userId);
        ps.setString(2, title);
        ps.setString(3, message);
        ps.setString(4, type);

        ps.executeUpdate();

    } catch (Exception e) {
        System.err.println("Error creating notification");
        e.printStackTrace();
    }
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
                SELECT * FROM notifications
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
                        rs.getString("message"),
                        rs.getString("type"),
                        rs.getInt("is_read"),
                        rs.getString("created_at")
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
                SELECT * FROM notifications
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
                        rs.getString("message"),
                        rs.getString("type"),
                        rs.getInt("is_read"),
                        rs.getString("created_at")
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
}