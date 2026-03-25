package com.app.dao;

import com.app.model.Department;
import com.app.util.DB;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class DepartmentDAO {

    public static List<Department> getAllDepartments() {

    List<Department> list = new ArrayList<>();

    String sql = "SELECT * FROM departments ORDER BY name";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            list.add(new Department(
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