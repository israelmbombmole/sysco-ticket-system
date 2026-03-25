package com.app.dao;


import com.app.auth.Session;
import com.app.util.DB;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class TicketHistoryDAO {

    public static void log(Connection conn, int ticketId, String action, String description) {

    String sql = """
        INSERT INTO ticket_history(ticket_id,action,description,user_id,username)
        VALUES(?,?,?,?,?)
    """;

    try (PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, ticketId);
        ps.setString(2, action);
        ps.setString(3, description);
        ps.setInt(4, Session.getUserId());
        ps.setString(5, Session.getUsername());

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
}