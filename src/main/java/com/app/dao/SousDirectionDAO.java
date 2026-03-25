package com.app.dao;

import com.app.model.SousDirection;
import com.app.util.DB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SousDirectionDAO {

    public static ObservableList<SousDirection> findAll() {

        ObservableList<SousDirection> list = FXCollections.observableArrayList();

       String sql = """
    SELECT * FROM sous_directions
    ORDER BY 
        CASE WHEN name = 'AUTRE' THEN 1 ELSE 0 END,
        name
""";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new SousDirection(
                        rs.getInt("id"),
                        rs.getString("name")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
    
    
    
    public static ObservableList<SousDirection> getAllSousDirections() {

    ObservableList<SousDirection> list = FXCollections.observableArrayList();

    String sql = """
        SELECT * FROM sous_directions
        ORDER BY 
            CASE WHEN name = 'AUTRE' THEN 1 ELSE 0 END,
            name
        """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            list.add(new SousDirection(
                    rs.getInt("id"),
                    rs.getString("name")
            ));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
    
    public static SousDirection getByName(String name) {

    String sql = "SELECT * FROM sous_directions WHERE name = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, name);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return new SousDirection(
                    rs.getInt("id"),
                    rs.getString("name")
            );
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}
    
    
}
