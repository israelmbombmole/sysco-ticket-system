package com.app.dao;

import com.app.auth.Session;
import com.app.model.Message;
import com.app.util.CryptoUtil;
import com.app.util.DB;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    public static void sendMessage(Message msg) {

        String sql = """
            INSERT INTO messages(sender_id, receiver_id, message)
            VALUES(?,?,?)
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, msg.getSenderId());
            stmt.setInt(2, msg.getReceiverId());
            stmt.setString(3, CryptoUtil.encrypt(msg.getContent()));
            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        NotificationDAO.create(
        msg.getReceiverId(),
        "New Message",
        "New message from "+Session.getUsername(),
        "MESSAGE"
);
        
        
    }

    public static List<Message> getConversation(
            int user1, int user2, int offset, int limit) {

        List<Message> messages = new ArrayList<>();

        String sql = """
            SELECT * FROM messages
            WHERE (sender_id = ? AND receiver_id = ?)
               OR (sender_id = ? AND receiver_id = ?)
            ORDER BY id ASC
            LIMIT ? OFFSET ?
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, user1);
            stmt.setInt(2, user2);
            stmt.setInt(3, user2);
            stmt.setInt(4, user1);
            stmt.setInt(5, limit);
            stmt.setInt(6, offset);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                messages.add(mapMessage(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }

    public static List<Integer> getUsersWithUnreadMessages(int receiverId) {

        List<Integer> senders = new ArrayList<>();

        String sql = """
            SELECT DISTINCT sender_id
            FROM messages
            WHERE receiver_id = ?
            AND is_read = 0
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, receiverId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                senders.add(rs.getInt("sender_id"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return senders;
    }

    public static List<Message> getNewMessages(int user1, int user2, int lastId) {

        List<Message> messages = new ArrayList<>();

        String sql = """
            SELECT * FROM messages
            WHERE (
                (sender_id = ? AND receiver_id = ?)
                OR
                (sender_id = ? AND receiver_id = ?)
            )
            AND id > ?
            ORDER BY id ASC
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, user1);
            stmt.setInt(2, user2);
            stmt.setInt(3, user2);
            stmt.setInt(4, user1);
            stmt.setInt(5, lastId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                messages.add(mapMessage(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }

    public static void markAsRead(int senderId, int receiverId) {

        String sql = """
            UPDATE messages
            SET is_read = 1
            WHERE sender_id = ?
            AND receiver_id = ?
            AND is_read = 0
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getUnreadCount(int senderId, int receiverId) {

        String sql = """
            SELECT COUNT(*) FROM messages
            WHERE sender_id = ?
            AND receiver_id = ?
            AND is_read = 0
        """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    private static Message mapMessage(ResultSet rs) throws SQLException {

        return new Message(
                rs.getInt("id"),
                rs.getInt("sender_id"),
                rs.getInt("receiver_id"),
                CryptoUtil.decrypt(rs.getString("message")),
                rs.getString("created_at"),
                rs.getInt("is_read")
        );
    }

    public static List<Message> getConversation(int user1, int user2) {
        return getConversation(user1, user2, 0, 1000);
    }
    
    
    
    public static int countMessagesForUser(int userId){

    String sql = "SELECT COUNT(*) FROM messages WHERE receiver_id=?";

    try(Connection conn = DB.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)){

        ps.setInt(1,userId);

        ResultSet rs = ps.executeQuery();

        if(rs.next())
            return rs.getInt(1);

    }catch(Exception e){
        e.printStackTrace();
    }

    return 0;
}
    
    
    
    
    
}