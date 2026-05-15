package com.app.dao;

import com.app.auth.Session;
import com.app.util.DB;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ChatDAO {

    // =====================================================
    // SEND PRIVATE MESSAGE
    // =====================================================
    public static void sendMessage(int receiverId, String message) {

        String sql = """
                INSERT INTO chat_messages
                (sender_id, receiver_id, message, date_sent, time_sent)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Session.getUserId());
            stmt.setInt(2, receiverId);
            stmt.setString(3, message);
            stmt.setString(4, LocalDate.now().toString());
            stmt.setString(5, LocalTime.now().toString());

            stmt.executeUpdate();

            NotificationDAO.create(
                receiverId,
                "New Message",
                Session.getUsername()+ " sent you a message",
                "CHAT_MESSAGE",
                "CHAT_USER",
                Session.getUserId(),
                null
        );
            
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================
    // GET CONVERSATION BETWEEN TWO USERS
    // =====================================================
    public static List<String> getConversation(int otherUserId) {

        List<String> messages = new ArrayList<>();

        String sql = """
                SELECT sender_id, message, date_sent, time_sent
                FROM chat_messages
                WHERE (sender_id = ? AND receiver_id = ?)
                   OR (sender_id = ? AND receiver_id = ?)
                ORDER BY id ASC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Session.getUserId());
            stmt.setInt(2, otherUserId);
            stmt.setInt(3, otherUserId);
            stmt.setInt(4, Session.getUserId());

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {

                int senderId = rs.getInt("sender_id");
                String message = rs.getString("message");
                String time = rs.getString("time_sent");

                String prefix = senderId == Session.getUserId()
                        ? "Me (" + time + "): "
                        : "Them (" + time + "): ";

                messages.add(prefix + message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }
}