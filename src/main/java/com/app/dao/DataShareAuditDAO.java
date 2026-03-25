package com.app.dao;

import com.app.model.DataShareAudit;
import com.app.util.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.time.LocalDate;
import java.time.LocalTime;

import java.util.ArrayList;
import java.util.List;

public class DataShareAuditDAO {

    public static void log(int fileId,
                           String fileName,
                           int sharedBy,
                           String sharedByName,
                           int recipientId,
                           String recipientName,
                           String action){

        String sql = """
    INSERT INTO datashare_audit
    (file_id,file_name,shared_by_id,shared_by_name,
     recipient_id,recipient_name,action,date,time)
    VALUES (?,?,?,?,?,?,?,?,?)
""";

        try(Connection conn = DB.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){

            ps.setInt(1,fileId);
            ps.setString(2,fileName);
            ps.setInt(3,sharedBy);
            ps.setString(4,sharedByName);
            ps.setInt(5,recipientId);
            ps.setString(6,recipientName);
            ps.setString(7,action);
            ps.setString(8,LocalDate.now().toString());
            ps.setString(9,LocalTime.now().toString());

            ps.executeUpdate();

        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public static List<DataShareAudit> getAll(){

    List<DataShareAudit> list = new ArrayList<>();

    String sql = """
        SELECT *
        FROM datashare_audit
        ORDER BY id DESC
    """;

    try(Connection conn = DB.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()){

        while(rs.next()){

            DataShareAudit audit = new DataShareAudit(
        rs.getInt("id"),
        rs.getInt("file_id"),
        rs.getString("file_name"),
        rs.getInt("shared_by_id"),
        rs.getString("shared_by_name"),
        rs.getInt("recipient_id"),
        rs.getString("recipient_name"),
        rs.getString("action"),
        rs.getString("date"),
        rs.getString("time")
);

            list.add(audit);
        }

    }catch(Exception e){
        e.printStackTrace();
    }

    return list;
}
}