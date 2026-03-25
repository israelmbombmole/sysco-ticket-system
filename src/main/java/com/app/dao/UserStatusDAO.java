package com.app.dao;

import com.app.util.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class UserStatusDAO {

    public static void setOnline(int userId) {

        String sql = """
                INSERT INTO user_status (user_id, is_online)
                VALUES (?, 1)
                ON CONFLICT(user_id)
                DO UPDATE SET is_online = 1
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setOffline(int userId) {

        String sql = "UPDATE user_status SET is_online = 0 WHERE user_id = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}