package com.app.dao;

import com.app.model.Direction;   // ✅ CORRECT IMPORT
import com.app.util.DB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class DirectionDAO {

    public static ObservableList<Direction> getDirectionsBySousDirection(int sousDirectionId) {

        ObservableList<Direction> list = FXCollections.observableArrayList();

        String sql = "SELECT * FROM directions WHERE sous_direction_id=? ORDER BY name";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, sousDirectionId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new Direction(
                        rs.getInt("id"),
                        rs.getString("name")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
}