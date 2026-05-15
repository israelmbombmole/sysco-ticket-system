package com.app.dao;

import com.app.auth.Session;
import com.app.model.DataShareAudit;
import com.app.util.DB;
import com.app.util.DbConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.time.LocalDate;
import java.time.LocalTime;

import java.util.ArrayList;
import java.util.List;

public class DataShareAuditDAO {

    private static String normalizeTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String value = raw.trim();
        int dot = value.indexOf('.');
        if (dot > 0) {
            value = value.substring(0, dot);
        }
        return value;
    }

    private static boolean shouldRestrictByDirection() {
        String role = Session.getRole();
        return role != null && !"ADMIN".equalsIgnoreCase(role);
    }

    private static Integer getCurrentUserDirectionId() {
        String sql = "SELECT direction_id FROM users WHERE id = ?";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Session.getUserId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int directionId = rs.getInt("direction_id");
                return rs.wasNull() ? null : directionId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void log(int fileId,
                           String fileName,
                           int sharedBy,
                           String sharedByName,
                           int recipientId,
                           String recipientName,
                           String action){

        String sql = DbConfig.isOracle()
                ? """
    INSERT INTO datashare_audit
    (file_id,file_name,shared_by_id,shared_by_name,
     recipient_id,recipient_name,action,"date","time")
    VALUES (?,?,?,?,?,?,?,?,?)
"""
                : """
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
            ps.setString(9, LocalTime.now().withNano(0).toString());

            ps.executeUpdate();

        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public static List<DataShareAudit> getAll(){

    List<DataShareAudit> list = new ArrayList<>();
    boolean restrictByDirection = shouldRestrictByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    String selectCols = DbConfig.isOracle()
            ? """
        a.id,
        a.file_id,
        a.file_name,
        a.shared_by_id,
        a.shared_by_name,
        a.recipient_id,
        a.recipient_name,
        a.action,
        a."date" AS shared_date,
        a."time" AS shared_time
        """
            : """
        a.id,
        a.file_id,
        a.file_name,
        a.shared_by_id,
        a.shared_by_name,
        a.recipient_id,
        a.recipient_name,
        a.action,
        a.date AS shared_date,
        a.time AS shared_time
        """;

    String sql = restrictByDirection
            ? "SELECT " + selectCols + """
        FROM datashare_audit a
        LEFT JOIN users shared_by_user ON shared_by_user.id = a.shared_by_id
        LEFT JOIN users recipient_user ON recipient_user.id = a.recipient_id
        WHERE shared_by_user.direction_id = ?
           OR recipient_user.direction_id = ?
        ORDER BY a.id DESC
    """
            : "SELECT " + selectCols + """
        FROM datashare_audit a
        ORDER BY a.id DESC
    """;

    try(Connection conn = DB.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)){

        if (restrictByDirection) {
            stmt.setInt(1, directionId);
            stmt.setInt(2, directionId);
        }
        ResultSet rs = stmt.executeQuery();

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
        rs.getString("shared_date"),
        normalizeTime(rs.getString("shared_time"))
);

            list.add(audit);
        }

    }catch(Exception e){
        e.printStackTrace();
    }

    return list;
}
}