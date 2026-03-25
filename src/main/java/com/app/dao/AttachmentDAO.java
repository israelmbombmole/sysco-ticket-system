package com.app.dao;

import com.app.model.Attachment;
import com.app.util.DB;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AttachmentDAO {

    public static void addAttachment(int ticketId, String name, String path, String type) {

        String sql = """
            INSERT INTO ticket_attachments(ticket_id,file_name,file_path,file_type)
            VALUES(?,?,?,?)
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, ticketId);
            ps.setString(2, name);
            ps.setString(3, path);
            ps.setString(4, type);

            ps.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ObservableList<Attachment> getAttachments(int ticketId) {

        ObservableList<Attachment> list = FXCollections.observableArrayList();

        String sql = """
            SELECT * FROM ticket_attachments
            WHERE ticket_id = ?
        """;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, ticketId);

            ResultSet rs = ps.executeQuery();

           while (rs.next()) {

    Attachment att = new Attachment();

    att.setId(rs.getInt("id"));
    att.setTicketId(rs.getInt("ticket_id"));

    att.setTaskId(null); // ✅ safe (ticket attachments don't use task_id)

    att.setFileName(rs.getString("file_name"));
    att.setFilePath(rs.getString("file_path"));
    att.setFileType(rs.getString("file_type"));

    list.add(att);
}

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }
    
  public static void deleteAttachment(int attachmentId) {

    String sql = "DELETE FROM ticket_attachments WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, attachmentId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
  
  public static void deleteByTicket(int ticketId) {

    String sql = "DELETE FROM ticket_attachments WHERE ticket_id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
  
  
    
  
  
  public static void deleteAttachment(int ticketId, String path) {

    String sql = "DELETE FROM ticket_attachments WHERE ticket_id=? AND file_path=?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setString(2, path);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
  
  public static List<String> getAttachmentsByTicket(int ticketId) {

    List<String> files = new ArrayList<>();

    String sql = "SELECT file_path FROM ticket_attachments WHERE ticket_id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            String path = rs.getString("file_path");

            System.out.println("ATTACHMENT FOUND: " + path);

            files.add(path);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return files;
}
  
public static void addTaskAttachment(int taskId,
                                     String fileName,
                                     String filePath) {

    String sql = """
        INSERT INTO task_attachments(
            task_id,
            file_name,
            file_path,
            uploaded_by
        )
        VALUES (?, ?, ?, ?)
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);
        ps.setString(2, fileName);
        ps.setString(3, filePath);
        ps.setInt(4, com.app.auth.Session.getUserId());

        ps.executeUpdate();

        System.out.println("📎 Task attachment saved: " + fileName);

    } catch (Exception e) {
        e.printStackTrace();
    }
}
   
 public static List<Attachment> getTaskAttachments(int taskId) {

    List<Attachment> list = new ArrayList<>();

    String sql = """
        SELECT * FROM task_attachments
        WHERE task_id=?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, taskId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Attachment att = new Attachment();

            att.setId(rs.getInt("id"));
            att.setTaskId(rs.getInt("task_id"));
            att.setFileName(rs.getString("file_name"));
            att.setFilePath(rs.getString("file_path"));

            list.add(att);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
  
 
 
 
 
public static List<Attachment> getAllTaskAttachmentsByTicket(int ticketId) {

    List<Attachment> list = new ArrayList<>();

    String sql = """
        SELECT 
            ta.id,
            ta.task_id,
            ta.file_name,
            ta.file_path,
            ta.uploaded_at
        FROM task_attachments ta
        JOIN ticket_tasks t ON ta.task_id = t.id
        WHERE t.ticket_id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            Attachment att = new Attachment();

            att.setId(rs.getInt("id"));
            att.setTaskId(rs.getInt("task_id"));
            att.setFileName(rs.getString("file_name"));   // ✅ FIXED
            att.setFilePath(rs.getString("file_path"));   // ✅ FIXED

            list.add(att);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
 
 
    
    

}
