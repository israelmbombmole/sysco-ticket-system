package com.app.dao;

import com.app.model.SousDirection;
import com.app.util.DB;
import com.app.util.DbConfig;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

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

    public static ObservableList<SousDirection> getSousDirectionsByNames(List<String> names) {
        ObservableList<SousDirection> list = FXCollections.observableArrayList();
        if (names == null || names.isEmpty()) {
            return list;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(names.size(), "?"));
        String sql = "SELECT * FROM sous_directions WHERE name IN (" + placeholders + ") ORDER BY name";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < names.size(); i++) {
                ps.setString(i + 1, names.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new SousDirection(rs.getInt("id"), rs.getString("name")));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static int getOrCreateSousDirectionId(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Sous direction name cannot be empty");
        }

        SousDirection existing = getByName(clean);
        if (existing != null) {
            return existing.getId();
        }

        String insert = "INSERT INTO sous_directions(name) VALUES (?)";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = DbConfig.isOracle()
                     ? c.prepareStatement(insert, new String[] { "ID" })
                     : c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, clean);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                Number generatedId = (Number) rs.getObject(1);
                if (generatedId == null) {
                    throw new RuntimeException("Failed to create sous direction: no numeric ID returned");
                }
                return generatedId.intValue();
            }
        } catch (Exception e) {
            // Likely inserted by concurrent action, try read again.
            SousDirection retry = getByName(clean);
            if (retry != null) {
                return retry.getId();
            }
            throw new RuntimeException(e);
        }

        throw new RuntimeException("Failed to create sous direction: " + clean);
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
