package com.app.dao;

import com.app.auth.Session;
import com.app.model.User;
import com.app.security.RoleUtil;
import com.app.util.DB;
import org.mindrot.jbcrypt.BCrypt;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import static java.util.Objects.hash;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import com.app.util.DB;
import com.app.util.PasswordUtil;




public class UserDAO {

    public static User findByUsername(String username) {

    String sql = """
        SELECT
            id,
            username,
            password_hash,
            role,
            active,
            is_super_admin,
            direction_id,
            sous_direction_id
        FROM users
        WHERE username = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            User user = new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"), // 🔥 VERY IMPORTANT
                rs.getString("role"),
                rs.getInt("active") == 1
            );
            user.setSuperAdmin(rs.getInt("is_super_admin") == 1);
            if (rs.getObject("direction_id") != null) {
                user.setDirectionId(rs.getInt("direction_id"));
            }
            if (rs.getObject("sous_direction_id") != null) {
                user.setSousDirectionId(rs.getInt("sous_direction_id"));
            }
            return user;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}

    
     public static User findById(int id) {

    String sql = "SELECT * FROM users WHERE id = ?";

    try (Connection conn = DB.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setInt(1, id);

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {

            User user = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("role"),
                    rs.getInt("active") == 1   // 🔥 convert int to boolean
            );
            user.setSuperAdmin(rs.getInt("is_super_admin") == 1);
            if (rs.getObject("direction_id") != null) {
                user.setDirectionId(rs.getInt("direction_id"));
            }
            if (rs.getObject("sous_direction_id") != null) {
                user.setSousDirectionId(rs.getInt("sous_direction_id"));
            }
            return user;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}       
            
    public static ObservableList<User> findAllAgents() {

    ObservableList<User> list = FXCollections.observableArrayList();

    String sql = "SELECT * FROM users WHERE role = 'AGENT'";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            User u = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getBoolean("active"),
                    rs.getObject("sous_direction_id") != null
                            ? rs.getInt("sous_direction_id")
                            : null
            );

            list.add(u);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
    public static int countAgents() {

    String sql = "SELECT COUNT(*) FROM users WHERE role = 'AGENT'";

    try (Connection c = DB.getConnection();
         var stmt = c.prepareStatement(sql);
         var rs = stmt.executeQuery()) {

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
}
    
    public static void toggleActive(int userId) {

    String sql = """
        UPDATE users
        SET active = CASE WHEN active = 1 THEN 0 ELSE 1 END,
            failed_attempts = CASE WHEN active = 0 THEN 0 ELSE failed_attempts END
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
    
    
    
    public static User authenticate(String username, String password) {

    String sql = """
        SELECT id, username, password_hash, role, active
        FROM users
        WHERE username = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {

            int id = rs.getInt("id");
            String dbUsername = rs.getString("username");
            String hash = rs.getString("password_hash");
            String role = rs.getString("role");
            boolean active = rs.getInt("active") == 1;

            if (!active) {
                System.out.println("User inactive.");
                return null;
            }

            if (hash != null && BCrypt.checkpw(password, hash)) {

                return new User(
                        id,
                        dbUsername,
                        hash,   // pass password hash
                        role,
                        active
                );
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}



    
   public static void updateUser(int id,
                              String username,
                              String role,
                              String email,
                              boolean active,
                              int sousDirectionId,
                              Integer directionId) {

    String sql = """
        UPDATE users
        SET username = ?,
            role = ?,
            email = ?,
            active = ?,
            sous_direction_id = ?,
            direction_id = ?
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);
        ps.setString(2, role);
        ps.setString(3, email);
        ps.setInt(4, active ? 1 : 0);
        ps.setInt(5, sousDirectionId);

        if(directionId != null)
            ps.setInt(6, directionId);
        else
            ps.setNull(6, Types.INTEGER);

        ps.setInt(7, id);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}


 public static List<User> getUsersBySousDirection(int sousDirectionId) {

    List<User> list = new ArrayList<>();

    String sql = "SELECT * FROM users WHERE sous_direction_id = ? AND active = 1";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, sousDirectionId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            User user = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getBoolean("active"),
                    rs.getInt("sous_direction_id")
            );

            list.add(user);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}


 public static String getUserRole(int userId) {

    String sql = "SELECT role FROM users WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getString("role");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}


 
public static int getSousDirectionId(String name) {

    String sql = "SELECT id FROM sous_directions WHERE name = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, name);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    throw new RuntimeException("Sous Direction not found: " + name);
}
    
        

    public static void createUser(String username,
                              String password, // ⚠️ will be ignored
                              String role,
                              String email,
                              int sousDirectionId,
                              Integer directionId) {

    String sql = """
        INSERT INTO users
        (username, password_hash, role, email, active, sous_direction_id, direction_id, must_change_password, failed_attempts)
        VALUES (?, ?, ?, ?, 1, ?, ?, 1, 0)
        """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        // 🔥 ALWAYS USE DEFAULT PASSWORD
        String defaultPassword = PasswordUtil.hash("123456");

        ps.setString(1, username);
        ps.setString(2, defaultPassword);
        ps.setString(3, role);
        ps.setString(4, email);
        ps.setInt(5, sousDirectionId);

        if (directionId != null)
            ps.setInt(6, directionId);
        else
            ps.setNull(6, Types.INTEGER);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}



public static void createUser(String username,
                              String password,
                              String role,
                              int sousDirectionId,
                              Integer directionId) {

    createUser(username, password, role, null, sousDirectionId, directionId);
}

    
    
    public static void updateActive(int userId, boolean active) {

    String sql = "UPDATE users SET active = ? WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, active ? 1 : 0);
        ps.setInt(2, userId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    
    
    public static void updateRole(int userId, String role) {

    String sql = "UPDATE users SET role = ? WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, role);
        ps.setInt(2, userId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    
    
    public static ObservableList<User> findAll() {

    ObservableList<User> list = FXCollections.observableArrayList();

    String sql = """
        SELECT u.id,
               u.username,
               u.role,
               u.active,
               u.sous_direction_id,
               sd.name AS sous_direction
        FROM users u
        LEFT JOIN sous_directions sd
            ON u.sous_direction_id = sd.id
        ORDER BY u.id
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            User user = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getInt("active") == 1,
                    rs.getInt("sous_direction_id")
            );

            user.setSousDirectionName(rs.getString("sous_direction"));

            list.add(user);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}

    public static ObservableList<User> getAgentStats() {

    ObservableList<User> list = FXCollections.observableArrayList();

    String sql = """
        SELECT u.id, u.username,
               COUNT(ta.ticket_id) as total
        FROM users u
        LEFT JOIN ticket_assignments ta
               ON ta.agent_id = u.id
        GROUP BY u.id, u.username
        ORDER BY total DESC
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {

            User user = new User();

            user.setId(rs.getInt("id"));
            user.setUsername(rs.getString("username"));
            user.setTicketCount(rs.getInt("total"));

            list.add(user);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
    
    
    
    public static List<User> getAllUsers() {

    List<User> users = new ArrayList<>();

    String sql = """
    SELECT u.id,
           u.username,
           u.role,
           u.active,
           u.email,
           u.direction_id,
           u.hidden,  
           sd.name AS sous_direction_name,
           d.name AS direction_name
    FROM users u
    LEFT JOIN sous_directions sd ON u.sous_direction_id = sd.id
    LEFT JOIN directions d ON u.direction_id = d.id
    ORDER BY u.id
""";

    try (Connection conn = DB.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        while (rs.next()) {

            User user = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getInt("active") == 1,
                    rs.getString("sous_direction_name")
            );

            // extra fields
            user.setEmail(rs.getString("email"));
            user.setDirectionName(rs.getString("direction_name"));
            user.setHidden(rs.getInt("hidden") == 1);

            Integer dirId = rs.getObject("direction_id") != null
                    ? rs.getInt("direction_id")
                    : null;

            user.setDirectionId(dirId);

            users.add(user);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return users;
}
    
    public static ObservableList<User> getAllAgents() {

    ObservableList<User> list = FXCollections.observableArrayList();

    String sql = "SELECT * FROM users WHERE role = 'AGENT'";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));

            list.add(u);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
   
    
    public static List<User> getAdmins() {

    List<User> list = new ArrayList<>();

    String sql = "SELECT * FROM users WHERE role = 'ADMIN'";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            User user = new User();

            user.setId(rs.getInt("id"));
            user.setUsername(rs.getString("username"));
            user.setRole(rs.getString("role"));

            list.add(user);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    
    
    
    public static String getUsername(int userId) {

    String sql = "SELECT username FROM users WHERE id = ?";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getString("username");
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return "Unknown";
}
    
    public static List<String> getAdminEmails() {

    List<String> emails = new ArrayList<>();

    String sql = """
        SELECT email
        FROM users
        WHERE role = 'ADMIN'
        AND email IS NOT NULL
        AND email <> ''
    """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {
            emails.add(rs.getString("email"));
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return emails;
}
    
    public static ObservableList<User> findActiveUsersByRoles(List<String> roles) {
    ObservableList<User> list = FXCollections.observableArrayList();

    if (roles == null || roles.isEmpty()) {
        return list;
    }

    String placeholders = String.join(",", java.util.Collections.nCopies(roles.size(), "?"));

    String sql = """
        SELECT id, username, role, active, sous_direction_id
        FROM users
        WHERE active = 1
          AND role IN (""" + placeholders + ") ORDER BY username";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        for (int i = 0; i < roles.size(); i++) {
            ps.setString(i + 1, roles.get(i));
        }

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            User u = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("role"),
                    rs.getBoolean("active"),
                    rs.getObject("sous_direction_id") != null
                            ? rs.getInt("sous_direction_id")
                            : null
            );
            list.add(u);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
    public static ObservableList<User> findAssignableUsers(String fromRole) {
    java.util.List<String> roles = new java.util.ArrayList<>(
            com.app.util.RoleFlowUtil.getAllowedTargetRoles(fromRole)
    );
    return findActiveUsersByRoles(roles);
}
    
  public static int countUsersByRole(String role) {
    String sql = "SELECT COUNT(*) FROM users WHERE role = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, role);
        ResultSet rs = ps.executeQuery();

        return rs.next() ? rs.getInt(1) : 0;

    } catch (Exception e) {
        e.printStackTrace();
        return 0;
    }
} 
  
  public static List<User> getAgents() {

    List<User> list = new ArrayList<>();

    String sql = "SELECT * FROM users WHERE role = 'AGENT'";

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        while (rs.next()) {

            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));

            list.add(u);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
  
  
 public static void deleteUser(int userId) {

    try (Connection c = DB.getConnection()) {

        c.setAutoCommit(false);

        // 🔥 DELETE ALL DEPENDENCIES (ADD MORE IF NEEDED)

        c.prepareStatement("DELETE FROM ticket_tasks WHERE assigned_to=" + userId).executeUpdate();
        c.prepareStatement("DELETE FROM ticket_tasks WHERE assigned_by=" + userId).executeUpdate();

        c.prepareStatement("DELETE FROM tickets WHERE created_by=" + userId).executeUpdate();
        c.prepareStatement("DELETE FROM tickets WHERE assigned_to=" + userId).executeUpdate();

        c.prepareStatement("DELETE FROM messages WHERE sender_id=" + userId).executeUpdate();

        c.prepareStatement("DELETE FROM notifications WHERE user_id=" + userId).executeUpdate();

        c.prepareStatement("DELETE FROM login_audit WHERE user_id=" + userId).executeUpdate();

        c.prepareStatement("DELETE FROM system_audit WHERE user_id=" + userId).executeUpdate();

        c.prepareStatement("DELETE FROM user_permissions WHERE user_id=" + userId).executeUpdate();

        // 👉 ADD ANY OTHER TABLE HERE

        // ✅ FINALLY DELETE USER
        PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id=?");
        ps.setInt(1, userId);
        ps.executeUpdate();

        c.commit();

        System.out.println("✅ User deleted completely");

    } catch (Exception e) {
        e.printStackTrace();
    }
}
 
 
 public static boolean verifyPassword(int userId, String password) {

    String sql = "SELECT password_hash FROM users WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {

            // ✅ THIS IS YOUR storedHash
            String storedHash = rs.getString("password_hash");

            // 🔍 Debug (optional)
            System.out.println("DB hash: " + storedHash);

            // ✅ compare using BCrypt
            return org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return false;
}
    
 public static void softDelete(int userId) {

    String sql = "UPDATE users SET active = 0, hidden = 1 WHERE id = ?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}   
 
 public static void resetPassword(int userId) {

    String sql = """
        UPDATE users
        SET password_hash=?,
            must_change_password=1,
            failed_attempts=0,
            lock_until=NULL
        WHERE id=?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, PasswordUtil.hash("123456"));
        ps.setInt(2, userId);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
 
 
 
 
 
 
public static User login(String username, String password) {

    String sql = "SELECT * FROM users WHERE username=?";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);

        ResultSet rs = ps.executeQuery();

        if (rs.next()) {

            int userId = rs.getInt("id");
            int failedAttempts = rs.getInt("failed_attempts");
            int mustChange = rs.getInt("must_change_password");
            boolean active = rs.getInt("active") == 1;

            String storedHash = rs.getString("password_hash");

            // ======================
            // 🚫 CHECK IF DISABLED (LOCKED BY ADMIN OR SYSTEM)
            // ======================
            if (!active) {
                throw new RuntimeException(
                    "Compte désactivé ou bloqué. Contactez l'administrateur."
                );
            }

            // ======================
            // 🔒 CHECK IF LOCKED BY ATTEMPTS
            // ======================
            if (failedAttempts >= 5) {
                throw new RuntimeException(
                    "Votre compte est bloqué. Veuillez contacter l'administrateur."
                );
            }

            // ======================
            // ✅ PASSWORD CORRECT
            // ======================
            if (org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash)) {

                // reset attempts using SAME connection ✅
                resetAttempts(c, userId);

                User user = mapUser(rs);

                // 🔥 ADMIN DEFAULT BYPASS
                boolean isAdminDefault =
                        "admin".equalsIgnoreCase(rs.getString("username")) &&
                        org.mindrot.jbcrypt.BCrypt.checkpw("admin123", storedHash);

                if (isAdminDefault) {
                    user.setMustChangePassword(false);
                } else {
                    user.setMustChangePassword(mustChange == 1);
                }

                return user;
            }

            // ======================
            // ❌ WRONG PASSWORD
            // ======================
            failedAttempts++;

            if (failedAttempts >= 5) {

                lockUser(c, userId);

                throw new RuntimeException(
                    "Trop de tentatives. Compte bloqué."
                );

            } else {

                updateAttempts(c, userId, failedAttempts);

                throw new RuntimeException(
                    "Identifiants invalides (" + failedAttempts + "/5)"
                );
            }
        }

    } catch (Exception e) {
        throw new RuntimeException(e.getMessage());
    }

    return null;
}
 
 
 
 
 
 private static void updateAttempts(Connection c, int userId, int attempts) {

    String sql = "UPDATE users SET failed_attempts=? WHERE id=?";

    try (PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, attempts);
        ps.setInt(2, userId);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
 
 private static void resetAttempts(Connection c, int userId) {

    String sql = """
        UPDATE users
        SET failed_attempts = 0
        WHERE id = ?
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
 
 
private static void lockUser(Connection c, int userId) {

    String sql = """
        UPDATE users
        SET failed_attempts = 5,
            active = 0
        WHERE id = ?
    """;

    try (PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
 
 
 
 public static void updatePassword(int userId, String newPassword) {

    String sql = """
        UPDATE users
        SET password_hash=?,
            must_change_password=0
        WHERE id=?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, PasswordUtil.hash(newPassword));
        ps.setInt(2, userId);

        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}
 
 private static User mapUser(ResultSet rs) throws Exception {

    User user = new User(
            rs.getInt("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getInt("active") == 1
    );

    // 🔥 IMPORTANT (for force password change)
    user.setMustChangePassword(rs.getInt("must_change_password") == 1);
    try {
        user.setSuperAdmin(rs.getInt("is_super_admin") == 1);
    } catch (Exception ignored) {}
    try {
        if (rs.getObject("direction_id") != null) {
            user.setDirectionId(rs.getInt("direction_id"));
        }
    } catch (Exception ignored) {}
    try {
        if (rs.getObject("sous_direction_id") != null) {
            user.setSousDirectionId(rs.getInt("sous_direction_id"));
        }
    } catch (Exception ignored) {}

    return user;
}
 
 public static ObservableList<User> getUsersByRoles(List<String> roles) {

    ObservableList<User> list = FXCollections.observableArrayList();

    if (roles == null || roles.isEmpty()) return list;

    String placeholders = String.join(",", roles.stream().map(r -> "?").toList());

    String sql = """
        SELECT 
            u.*,
            d.name AS direction_name
        FROM users u
        LEFT JOIN directions d ON d.id = u.direction_id
        WHERE u.role IN (""" + placeholders + ") AND u.active = 1";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        for (int i = 0; i < roles.size(); i++) {
            ps.setString(i + 1, roles.get(i));
        }

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            User u = new User();

            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));
            if (rs.getObject("direction_id") != null) {
                u.setDirectionId(rs.getInt("direction_id"));
            }
            u.setDirectionName(rs.getString("direction_name"));

            list.add(u);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
 
public static List<User> getAssignableUsers() {

    List<User> list = new ArrayList<>();

    String sql = "SELECT id, username, role FROM users WHERE active = 1";

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {

        String myRole = Session.getRole();

        if (myRole == null) {
            System.out.println("❌ Session role is NULL");
            return list;
        }

        int myLevel = RoleUtil.getLevel(myRole);

        while (rs.next()) {

            String targetRole = rs.getString("role");

            if (targetRole == null) continue;

            int targetLevel = RoleUtil.getLevel(targetRole);

            // ✅ allow same or lower hierarchy
            if (targetLevel <= myLevel) {

                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setRole(targetRole);

                list.add(u);
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}
 
  
}
