package com.app.dao;

import com.app.util.DB;

import java.sql.*;
import java.util.*;

public class UserPermissionDAO {

    public static void savePermissions(int userId, List<String> permissions) {

        String deleteSql = "DELETE FROM user_permissions WHERE user_id=?";
        String insertSql = "INSERT INTO user_permissions(user_id, permission) VALUES (?, ?)";

        try (Connection conn = DB.getConnection()) {

            // 🔥 Clear old permissions
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, userId);
                ps.executeUpdate();
            }

            // 🔥 Insert new ones
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {

                for (String perm : permissions) {
                    ps.setInt(1, userId);
                    ps.setString(2, perm);
                    ps.addBatch();
                }

                ps.executeBatch();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Set<String> getPermissions(int userId) {

        Set<String> list = new HashSet<>();

        String sql = "SELECT permission FROM user_permissions WHERE user_id=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(rs.getString("permission"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}