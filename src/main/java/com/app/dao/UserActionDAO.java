package com.app.dao;

import com.app.model.UserAction;
import com.app.util.DB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;

public class UserActionDAO {

    // SAVE ACTION
   public static void log(String username, String action) {

    String sql = "INSERT INTO user_actions(username, action) VALUES(?, ?)";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);
        ps.setString(2, action);
        ps.executeUpdate();

        System.out.println("Audit log inserted: " + action);

    } catch (Exception e) {
        e.printStackTrace();
    }
}


    // FETCH ALL ACTIONS
    public static ObservableList<UserAction> findAll() {

        ObservableList<UserAction> list =
                FXCollections.observableArrayList();

        String sql = """
            SELECT username, action, created_at
            FROM user_actions
            ORDER BY created_at DESC
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                list.add(new UserAction(
                        rs.getString("username"),
                        rs.getString("action"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}
