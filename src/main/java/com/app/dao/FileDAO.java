package com.app.dao;

import com.app.util.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FileDAO {

    public static int countFilesForUser(int userId){

        String sql = """
            SELECT COUNT(*)
            FROM datashare_recipients
            WHERE user_id = ?
        """;

        try(Connection conn = DB.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){

            ps.setInt(1, userId);

            ResultSet rs = ps.executeQuery();

            if(rs.next()){
                return rs.getInt(1);
            }

        }catch(Exception e){
            e.printStackTrace();
        }

        return 0;
    }

}