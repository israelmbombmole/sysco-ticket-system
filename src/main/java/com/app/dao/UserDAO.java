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
import java.util.Set;
import java.time.LocalDate;
import static java.util.Objects.hash;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import com.app.util.DB;
import com.app.util.PasswordUtil;
import com.app.util.RoleKeyUtil;
import com.app.util.AttendanceSignatureUtil;
import com.app.model.SousDirection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import com.app.service.AuditService;
import com.app.service.PasswordOtpService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;




public class UserDAO {

    private static List<User> filterUsersNotOnLeaveToday(List<User> users) {
        if (users == null || users.isEmpty()) {
            return users;
        }
        Set<Integer> absent = UserAbsenceDAO.userIdsAbsentOn(LocalDate.now());
        List<User> out = new ArrayList<>();
        for (User u : users) {
            if (u != null && !absent.contains(u.getId())) {
                out.add(u);
            }
        }
        return out;
    }

    private static void removeUsersOnLeaveToday(ObservableList<User> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Integer> absent = UserAbsenceDAO.userIdsAbsentOn(LocalDate.now());
        list.removeIf(u -> u != null && absent.contains(u.getId()));
    }

    private static boolean shouldRestrictUsersByDirection() {
        if (com.app.util.AccessContext.isSystemSuperAdmin()) {
            return false;
        }
        return true;
    }

    private static Integer getCurrentUserDirectionId() {
        String sql = "SELECT direction_id FROM users WHERE id = ?";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
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

    public static boolean existsByMatricule(String matricule, Integer excludeUserId) {
        String clean = matricule == null ? "" : matricule.trim();
        if (clean.isEmpty()) {
            return false;
        }

        String sql = (excludeUserId == null)
                ? "SELECT 1 FROM users WHERE matricule = ? LIMIT 1"
                : "SELECT 1 FROM users WHERE matricule = ? AND id <> ? LIMIT 1";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clean);
            if (excludeUserId != null) {
                ps.setInt(2, excludeUserId);
            }
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static User findByUsername(String username) {

    String sql = """
        SELECT id, username, matricule, password_hash, role, active
        FROM users
        WHERE username = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            User u = new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"), // 🔥 VERY IMPORTANT
                rs.getString("role"),
                rs.getInt("active") == 1
            );
            u.setMatricule(rs.getString("matricule"));
            return u;
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

            User u = new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("role"),
                    rs.getInt("active") == 1   // 🔥 convert int to boolean
            );
            u.setMatricule(rs.getString("matricule"));
            try {
                u.setAttendanceSignature(rs.getString("attendance_signature"));
            } catch (SQLException ignored) {
                // older DB file before migration
            }
            if (rs.getObject("direction_id") != null) {
                u.setDirectionId(rs.getInt("direction_id"));
            }
            if (rs.getObject("sous_direction_id") != null) {
                u.setSousDirectionId(rs.getInt("sous_direction_id"));
            }
            return u;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}       
            
    public static ObservableList<User> findAllAgents() {

    ObservableList<User> list = FXCollections.observableArrayList();

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    String sql = "SELECT * FROM users WHERE role = 'AGENT'";
    if (restrictByDirection) {
        sql += " AND direction_id = ?";
    }

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        if (restrictByDirection) {
            ps.setInt(1, directionId);
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
    
    public static int countAgents() {

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return 0;
    }

    String sql = "SELECT COUNT(*) FROM users WHERE role = 'AGENT'";
    if (restrictByDirection) {
        sql += " AND direction_id = ?";
    }

    try (Connection c = DB.getConnection();
         var stmt = c.prepareStatement(sql)) {

        if (restrictByDirection) {
            stmt.setInt(1, directionId);
        }
        var rs = stmt.executeQuery();

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
                              String matricule,
                              String role,
                              String email,
                              boolean active,
                              Integer sousDirectionId,
                              Integer directionId,
                              String attendanceSignature) {

    String sql = """
        UPDATE users
        SET username = ?,
            matricule = ?,
            role = ?,
            email = ?,
            active = ?,
            sous_direction_id = ?,
            direction_id = ?,
            attendance_signature = ?
        WHERE id = ?
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setString(1, username);
        ps.setString(2, matricule);
        ps.setString(3, role);
        ps.setString(4, email);
        ps.setInt(5, active ? 1 : 0);
        if (sousDirectionId != null) {
            ps.setInt(6, sousDirectionId);
        } else {
            ps.setNull(6, Types.INTEGER);
        }

        if(directionId != null)
            ps.setInt(7, directionId);
        else
            ps.setNull(7, Types.INTEGER);

        ps.setString(8, attendanceSignature);
        ps.setInt(9, id);

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
                              String matricule,
                              String password, // ⚠️ will be ignored
                              String role,
                              String email,
                              Integer sousDirectionId,
                              Integer directionId,
                              String attendanceSignaturePreferred) {

    String sql = """
        INSERT INTO users
        (username, matricule, password_hash, role, email, active, sous_direction_id, direction_id, must_change_password, failed_attempts)
        VALUES (?, ?, ?, ?, ?, 1, ?, ?, 1, 0)
        """;

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        // 🔥 ALWAYS USE DEFAULT PASSWORD
        String defaultPassword = PasswordUtil.hash("123456");

        ps.setString(1, username);
        ps.setString(2, matricule);
        ps.setString(3, defaultPassword);
        ps.setString(4, role);
        ps.setString(5, email);
        if (sousDirectionId != null) {
            ps.setInt(6, sousDirectionId);
        } else {
            ps.setNull(6, Types.INTEGER);
        }

        if (directionId != null)
            ps.setInt(7, directionId);
        else
            ps.setNull(7, Types.INTEGER);

        ps.executeUpdate();

        User created = findByUsername(username);
        if (created != null) {
            String pref = AttendanceSignatureUtil.clamp(attendanceSignaturePreferred);
            String sig = pref != null
                    ? pref
                    : AttendanceSignatureUtil.generateForUser(created.getId(), matricule);
            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE users SET attendance_signature = ? WHERE id = ?")) {
                up.setString(1, sig);
                up.setInt(2, created.getId());
                up.executeUpdate();
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }
}



public static void createUser(String username,
                              String matricule,
                              String password,
                              String role,
                              Integer sousDirectionId,
                              Integer directionId) {

    createUser(username, matricule, password, role, null, sousDirectionId, directionId, null);
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

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;

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
    """;

    if (restrictByDirection) {
        sql += " WHERE u.direction_id = ?";
    }

    sql += " ORDER BY u.id";

    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = executeFindAllQuery(ps, restrictByDirection, directionId)) {

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

private static ResultSet executeFindAllQuery(PreparedStatement ps,
                                             boolean restrictByDirection,
                                             Integer directionId) throws SQLException {
    if (restrictByDirection) {
        ps.setInt(1, directionId);
    }
    return ps.executeQuery();
}

    
    
    public static ObservableList<User> getAgentStats() {

    ObservableList<User> list = FXCollections.observableArrayList();

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    String sql = """
        SELECT u.id, u.username,
               COUNT(ta.ticket_id) as total
        FROM users u
        LEFT JOIN ticket_assignments ta
               ON ta.agent_id = u.id
    """;

    if (restrictByDirection) {
        sql += " WHERE u.direction_id = ? ";
    }

    sql += """
        GROUP BY u.id, u.username
        ORDER BY total DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        if (restrictByDirection) {
            ps.setInt(1, directionId);
        }
        ResultSet rs = ps.executeQuery();

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

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;

    String sql = """
    SELECT u.id,
           u.username,
           u.matricule,
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
""";

    if (restrictByDirection) {
        sql += " WHERE u.direction_id = ?";
    }
    sql += " ORDER BY u.id";

    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return users;
    }

    try (Connection conn = DB.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        if (restrictByDirection) {
            stmt.setInt(1, directionId);
        }
        ResultSet rs = stmt.executeQuery();

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
            user.setMatricule(rs.getString("matricule"));
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

    /**
     * Active users from every direction (no direction filter), with direction and sous-direction ids for mission
     * participant selection across departments.
     */
    public static List<User> getAllUsersForMissionParticipantPicker() {

        List<User> users = new ArrayList<>();

        String sql = """
                SELECT u.id,
                       u.username,
                       u.matricule,
                       u.role,
                       u.active,
                       u.email,
                       u.direction_id,
                       u.sous_direction_id,
                       u.hidden,
                       sd.name AS sous_direction_name,
                       d.name AS direction_name
                FROM users u
                LEFT JOIN sous_directions sd ON u.sous_direction_id = sd.id
                LEFT JOIN directions d ON u.direction_id = d.id
                WHERE u.active = 1
                ORDER BY d.name, sd.name, u.username
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

                user.setEmail(rs.getString("email"));
                user.setMatricule(rs.getString("matricule"));
                user.setDirectionName(rs.getString("direction_name"));
                user.setHidden(rs.getInt("hidden") == 1);

                Integer dirId = rs.getObject("direction_id") != null
                        ? rs.getInt("direction_id")
                        : null;
                user.setDirectionId(dirId);

                if (rs.getObject("sous_direction_id") != null) {
                    user.setSousDirectionId(rs.getInt("sous_direction_id"));
                } else {
                    user.setSousDirectionId(null);
                }

                users.add(user);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return users;
    }

    /**
     * All active users with role ADMIN, ignoring direction restrictions.
     * Used when non-admin workflows must notify every admin (e.g. File Share Management OTP requests).
     */
    public static List<User> getAllActiveAdminsGlobal() {

        List<User> users = new ArrayList<>();

        String sql = """
                SELECT u.id,
                       u.username,
                       u.matricule,
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
                WHERE u.active = 1 AND UPPER(TRIM(u.role)) = 'ADMIN'
                ORDER BY u.id
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {

                User user = new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getInt("active") == 1,
                        rs.getString("sous_direction_name")
                );

                user.setEmail(rs.getString("email"));
                user.setMatricule(rs.getString("matricule"));
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

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;

    String sql = "SELECT * FROM users WHERE role = 'AGENT'";
    if (restrictByDirection) {
        sql += " AND direction_id = ?";
    }

    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (restrictByDirection) {
            ps.setInt(1, directionId);
        }
        ResultSet rs = ps.executeQuery();

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

    public static List<String> getTicketNotificationEmails() {
        List<String> emails = new ArrayList<>();
        /*
         * Notify leadership roles that should see new tickets by email.
         * - Normalize role (ignore hyphen/space differences): SOUS-DIRECTEUR / SOUS DIRECTEUR / etc.
         * - Exclude hidden users.
         * - Require a non-blank email (Oracle seed admins often have NULL email until edited in User Management).
         */
        String sql = """
            SELECT DISTINCT TRIM(email) AS email
            FROM users
            WHERE COALESCE(active, 0) <> 0
              AND COALESCE(hidden, 0) = 0
              AND UPPER(REPLACE(REPLACE(TRIM(role), '-', ''), ' ', ''))
                  IN ('ADMIN', 'DIRECTEUR', 'SOUSDIRECTEUR', 'INSPECTEUR')
              AND email IS NOT NULL
              AND LENGTH(TRIM(email)) > 0
            """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String em = rs.getString("email");
                if (em != null && !em.isBlank()) {
                    emails.add(em.trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return emails;
    }

    public static String getEmailByUserId(int userId) {
        String sql = """
            SELECT email
            FROM users
            WHERE id = ?
              AND email IS NOT NULL
              AND TRIM(email) <> ''
        """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("email");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static ObservableList<User> findActiveUsersByRoles(List<String> roles) {
    ObservableList<User> list = FXCollections.observableArrayList();

    if (roles == null || roles.isEmpty()) {
        return list;
    }

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    String placeholders = String.join(",", java.util.Collections.nCopies(roles.size(), "?"));

    String sql = """
        SELECT id, username, role, active, sous_direction_id
        FROM users
        WHERE active = 1
          AND role IN (""" + placeholders + ") ORDER BY username";

    if (restrictByDirection) {
        sql = sql.replace("ORDER BY username", "AND direction_id = ? ORDER BY username");
    }

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        for (int i = 0; i < roles.size(); i++) {
            ps.setString(i + 1, roles.get(i));
        }

        if (restrictByDirection) {
            ps.setInt(roles.size() + 1, directionId);
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

    removeUsersOnLeaveToday(list);
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

  public static int countActiveUsersByRole(String role) {
    String sql = "SELECT COUNT(*) FROM users WHERE role = ? AND active = 1";

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

    /** Active users with role in a direction (dashboard role tiles). */
    public static int countActiveUsersByRoleInDirection(String role, int directionId) {
        String sql = "SELECT COUNT(*) FROM users WHERE role = ? AND active = 1 AND direction_id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setInt(2, directionId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Dashboard headcount: org-wide for {@code ADMIN}, otherwise limited to the session user's direction.
     */
    public static int countActiveUsersByRoleForDashboard(String role) {
        if (TicketDAO.hasOrgWideTicketAccess()) {
            return countActiveUsersByRole(role);
        }
        Integer dirId = TicketDAO.getCurrentUserDirectionId();
        if (dirId == null || dirId <= 0) {
            return 0;
        }
        return countActiveUsersByRoleInDirection(role, dirId);
    }

    /** Rows for dashboard role statistic cards (same scope as {@link #countActiveUsersByRoleForDashboard(String)}). */
    public static List<User> listActiveUsersByRoleForDashboard(String role) {
        List<User> list = new ArrayList<>();
        if (role == null || role.isBlank()) {
            return list;
        }
        try (Connection conn = DB.getConnection()) {
            String sql = """
                    SELECT u.id, u.username, u.role, u.direction_id, d.name AS direction_name
                    FROM users u
                    LEFT JOIN directions d ON d.id = u.direction_id
                    WHERE u.active = 1 AND u.role = ?""";
            if (!TicketDAO.hasOrgWideTicketAccess()) {
                Integer dirId = TicketDAO.getCurrentUserDirectionId();
                if (dirId == null || dirId <= 0) {
                    return list;
                }
                sql += " AND u.direction_id = ?";
                sql += " ORDER BY u.username";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, role);
                    ps.setInt(2, dirId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapDashboardUserRow(rs));
                        }
                    }
                }
            } else {
                sql += " ORDER BY u.username";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, role);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(mapDashboardUserRow(rs));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static User mapDashboardUserRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setRole(rs.getString("role"));
        int d = rs.getInt("direction_id");
        u.setDirectionId(rs.wasNull() ? null : d);
        String dn = readDirectionNameAlias(rs);
        if (dn != null && !dn.isBlank()) {
            u.setDirectionName(dn.trim());
        }
        return u;
    }

    /** SQLite / JDBC lowercases aliases; Oracle may expose {@code DIRECTION_NAME}. */
    private static String readDirectionNameAlias(ResultSet rs) {
        try {
            String s = rs.getString("direction_name");
            if (s != null) {
                return s;
            }
        } catch (SQLException ignored) {
        }
        try {
            return rs.getString("DIRECTION_NAME");
        } catch (SQLException ignored) {
        }
        return null;
    }
  
  public static List<User> getAgents() {

    List<User> list = new ArrayList<>();

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    String sql = "SELECT * FROM users WHERE role = 'AGENT'";
    if (restrictByDirection) {
        sql += " AND direction_id = ?";
    }

    try (Connection conn = DB.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        if (restrictByDirection) {
            ps.setInt(1, directionId);
        }
        ResultSet rs = ps.executeQuery();

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
 
 
 
 
 
 
public static User login(String usernameOrMatricule, String password) {

    String sql = """
        SELECT *
        FROM users
        WHERE LOWER(username) = LOWER(?)
           OR LOWER(COALESCE(matricule, '')) = LOWER(?)
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        String loginId = usernameOrMatricule == null ? "" : usernameOrMatricule.trim();
        ps.setString(1, loginId);
        ps.setString(2, loginId);

        try (ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                AuditService.log(
                        loginId,
                        "LOGIN_FAILURE",
                        "AUTH",
                        null,
                        "Unknown username or matricule");
                return null;
            }

            int userId = rs.getInt("id");
            int failedAttempts = rs.getInt("failed_attempts");
            int mustChange = rs.getInt("must_change_password");
            boolean active = rs.getInt("active") == 1;
            String dbUsername = rs.getString("username");

            String storedHash = rs.getString("password_hash");
            String otpHash = getOptionalOtpHash(rs);
            String otpExp = getOptionalOtpExp(rs);

            if (!active) {
                AuditService.log(
                        dbUsername,
                        "LOGIN_FAILURE",
                        "AUTH",
                        userId,
                        "Account is disabled or locked by administrator");
                throw new RuntimeException(
                        "Compte désactivé ou bloqué. Contactez l'administrateur."
                );
            }

            if (failedAttempts >= 5) {
                AuditService.log(
                        dbUsername,
                        "LOGIN_FAILURE",
                        "AUTH",
                        userId,
                        "Account locked after too many failed attempts");
                throw new RuntimeException(
                        "Votre compte est bloqué. Veuillez contacter l'administrateur."
                );
            }

            if (org.mindrot.jbcrypt.BCrypt.checkpw(password, storedHash)) {
                resetAttempts(c, userId);
                return finishSuccessfulLogin(c, userId, storedHash, mustChange);
            }

            if (otpHash != null
                    && !otpHash.isBlank()
                    && isOtpTimeValid(otpExp)
                    && org.mindrot.jbcrypt.BCrypt.checkpw(password, otpHash)) {
                resetAttempts(c, userId);
                try (PreparedStatement up = c.prepareStatement("""
                        UPDATE users SET password_otp_hash=NULL, password_otp_expires_at=NULL, must_change_password=1, failed_attempts=0
                        WHERE id=?
                        """)) {
                    up.setInt(1, userId);
                    up.executeUpdate();
                }
                try (PreparedStatement rel = c.prepareStatement(
                        "SELECT * FROM users WHERE id = ?")) {
                    rel.setInt(1, userId);
                    try (ResultSet r2 = rel.executeQuery()) {
                        if (r2.next()) {
                            User u = mapUser(r2);
                            u.setMustChangePassword(true);
                            u.setPasswordResetOtpUsed(true);
                            return u;
                        }
                    }
                }
                return null;
            }

            int next = failedAttempts + 1;
            if (next >= 5) {
                lockUser(c, userId);
                AuditService.log(
                        dbUsername,
                        "LOGIN_FAILURE",
                        "AUTH",
                        userId,
                        "Invalid password; account locked after 5 failed attempts"
                );
                throw new RuntimeException("Trop de tentatives. Compte bloqué.");
            } else {
                updateAttempts(c, userId, next);
                AuditService.log(
                        dbUsername,
                        "LOGIN_FAILURE",
                        "AUTH",
                        userId,
                        "Invalid password (" + next + "/5)"
                );
                throw new RuntimeException("Identifiants invalides (" + next + "/5)");
            }
        }
    } catch (RuntimeException e) {
        throw e;
    } catch (Exception e) {
        throw new RuntimeException(e.getMessage());
    }
}

    private static User finishSuccessfulLogin(
            Connection c,
            int userId,
            String storedHash,
            int mustChangeFlag) throws Exception {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            p.setInt(1, userId);
            try (ResultSet r2 = p.executeQuery()) {
                if (!r2.next()) {
                    return null;
                }
                User user = mapUser(r2);
                boolean isAdminDefault =
                        "admin".equalsIgnoreCase(r2.getString("username"))
                                && org.mindrot.jbcrypt.BCrypt.checkpw("admin123", storedHash);
                if (isAdminDefault) {
                    user.setMustChangePassword(false);
                } else {
                    user.setMustChangePassword(mustChangeFlag == 1);
                }
                return user;
            }
        }
    }

    private static String getOptionalOtpHash(ResultSet rs) {
        try {
            return rs.getString("password_otp_hash");
        } catch (SQLException e) {
            return null;
        }
    }

    private static String getOptionalOtpExp(ResultSet rs) {
        try {
            return rs.getString("password_otp_expires_at");
        } catch (SQLException e) {
            return null;
        }
    }

    private static boolean isOtpTimeValid(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        try {
            return LocalDateTime.parse(expiresAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .isAfter(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generates a 5-character one-time password, stores its hash, valid 24h. Shown to the user once.
     * @return plain code, or {@code null} if the account was not found or is inactive
     */
    public static String issueOrRegeneratePasswordOtp(String usernameOrMatricule) {
        String loginId = usernameOrMatricule == null ? "" : usernameOrMatricule.trim();
        if (loginId.isEmpty()) {
            return null;
        }
        String sql = """
            SELECT id, username, active FROM users
            WHERE LOWER(username) = LOWER(?)
               OR LOWER(COALESCE(matricule, '')) = LOWER(?)
            """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, loginId);
            ps.setString(2, loginId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getInt("active") != 1) {
                    return null;
                }
                int userId = rs.getInt("id");
                String uname = rs.getString("username");
                String plain = PasswordOtpService.generatePlainCode();
                String hash = org.mindrot.jbcrypt.BCrypt.hashpw(plain, org.mindrot.jbcrypt.BCrypt.gensalt());
                String exp = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                String up = "UPDATE users SET password_otp_hash=?, password_otp_expires_at=? WHERE id=?";
                try (PreparedStatement u = c.prepareStatement(up)) {
                    u.setString(1, hash);
                    u.setString(2, exp);
                    u.setInt(3, userId);
                    u.executeUpdate();
                }
                AuditService.log(
                        uname,
                        "PASSWORD_OTP_ISSUED",
                        "USER",
                        userId,
                        "5-character one-time password issued (24h). Previous code invalidated.");
                return plain;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
            must_change_password=0,
            password_otp_hash=NULL,
            password_otp_expires_at=NULL
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
    user.setMatricule(rs.getString("matricule"));
    if (rs.getObject("direction_id") != null) {
        user.setDirectionId(rs.getInt("direction_id"));
    } else {
        user.setDirectionId(null);
    }
    if (rs.getObject("sous_direction_id") != null) {
        user.setSousDirectionId(rs.getInt("sous_direction_id"));
    }

    return user;
}
 
 public static ObservableList<User> getUsersByRoles(List<String> roles) {

    ObservableList<User> list = FXCollections.observableArrayList();

    if (roles == null || roles.isEmpty()) return list;

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    String placeholders = String.join(",", roles.stream().map(r -> "?").toList());

    String sql = "SELECT * FROM users WHERE role IN (" + placeholders + ") AND active = 1";
    if (restrictByDirection) {
        sql += " AND direction_id = ?";
    }

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        for (int i = 0; i < roles.size(); i++) {
            ps.setString(i + 1, roles.get(i));
        }

        if (restrictByDirection) {
            ps.setInt(roles.size() + 1, directionId);
        }

        ResultSet rs = ps.executeQuery();

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
 
public static List<User> getAssignableUsers() {

    List<User> list = new ArrayList<>();

    boolean restrictByDirection = shouldRestrictUsersByDirection();
    Integer directionId = restrictByDirection ? getCurrentUserDirectionId() : null;
    if (restrictByDirection && (directionId == null || directionId <= 0)) {
        return list;
    }

    String sql = "SELECT id, username, role FROM users WHERE active = 1";
    if (restrictByDirection) {
        sql += " AND direction_id = ?";
    }

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        if (restrictByDirection) {
            ps.setInt(1, directionId);
        }
        ResultSet rs = ps.executeQuery();

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

    return filterUsersNotOnLeaveToday(list);
}

public static ObservableList<User> getActiveUsersByDirection(int directionId) {
    ObservableList<User> list = FXCollections.observableArrayList();
    String sql = """
        SELECT id, username, role, active
        FROM users
        WHERE active = 1
          AND direction_id = ?
        ORDER BY username
    """;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, directionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));
            u.setActive(rs.getInt("active") == 1);
            list.add(u);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    removeUsersOnLeaveToday(list);
    return list;
}

public static ObservableList<User> getActiveUsersByDirectionAndSousDirection(int directionId, int sousDirectionId) {
    ObservableList<User> list = FXCollections.observableArrayList();
    String sql = """
        SELECT id, username, role, active
        FROM users
        WHERE active = 1
          AND direction_id = ?
          AND sous_direction_id = ?
        ORDER BY username
    """;
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, directionId);
        ps.setInt(2, sousDirectionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));
            u.setActive(rs.getInt("active") == 1);
            list.add(u);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    removeUsersOnLeaveToday(list);
    return list;
}

/** [0]=directionId or null, [1]=sousDirectionId or null at DB level. */
public static Integer[] getDirectionSousForUser(int userId) {
    String sql = "SELECT direction_id, sous_direction_id FROM users WHERE id = ?";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setInt(1, userId);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                // Use column order so Oracle/aliases cannot break label lookup.
                int di = rs.getInt(1);
                Integer d = rs.wasNull() ? null : di;
                int si = rs.getInt(2);
                Integer s = rs.wasNull() ? null : si;
                return new Integer[] { d, s };
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return new Integer[] { null, null };
}

public static ObservableList<User> findActiveByRoleAndDirection(String role, int directionId) {
    ObservableList<User> list = FXCollections.observableArrayList();
    if (role == null || directionId <= 0) {
        return list;
    }
    String sql = "SELECT id, username, role, active FROM users WHERE active = 1 AND UPPER(role) = UPPER(?) AND direction_id = ? ORDER BY username";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, role);
        ps.setInt(2, directionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));
            u.setActive(rs.getInt("active") == 1);
            list.add(u);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return list;
}

/** Users attached to any direction row in the same logical cluster (handles duplicate {@code directions} rows). */
public static ObservableList<User> findActiveByRoleAndDirectionCluster(String role, int anyDirectionInCluster) {
    ObservableList<User> list = FXCollections.observableArrayList();
    if (role == null || anyDirectionInCluster <= 0) {
        return list;
    }
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    for (int did : com.app.dao.DirectionDAO.getDirectionIdCluster(anyDirectionInCluster)) {
        for (User u : findActiveByRoleAndDirection(role, did)) {
            if (seen.add(u.getUsername().toLowerCase(java.util.Locale.ROOT))) {
                list.add(u);
            }
        }
    }
    return list;
}

public static ObservableList<User> findActiveByRoleAndSousDirection(String role, int sousDirectionId) {
    ObservableList<User> list = FXCollections.observableArrayList();
    if (role == null || sousDirectionId <= 0) {
        return list;
    }
    String sql = "SELECT id, username, role, active FROM users WHERE active = 1 AND UPPER(role) = UPPER(?) AND sous_direction_id = ? ORDER BY username";
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, role);
        ps.setInt(2, sousDirectionId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setRole(rs.getString("role"));
            u.setActive(rs.getInt("active") == 1);
            list.add(u);
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return list;
}

/**
 * Active users for courrier Parcours: any account linked to the direction cluster by {@code direction_id} or
 * by {@code sous_direction_id} (any sous under that direction). Resolves role labels with accents vs our
 * {@link RoleKeyUtil} keys, and includes people only attached at sous level (which {@link #findActiveByRoleAndDirection} misses).
 */
public static ObservableList<User> findActiveByRoleInCourierScope(String canonicalRole, int anyDirectionInCluster) {
    java.util.Set<String> want = new java.util.HashSet<>();
    if (canonicalRole != null) {
        want.add(RoleKeyUtil.normalizeForScope(canonicalRole));
    }
    return findActiveInCourierScopeFiltered(anyDirectionInCluster, want);
}

/**
 * One combo for Vérificateur + assistant: both roles, users deduplicated, sorted.
 */
public static ObservableList<User> findActiveByRolesInCourierScope(int anyDirectionInCluster, String... canonicalRoles) {
    java.util.Set<String> want = new java.util.HashSet<>();
    if (canonicalRoles != null) {
        for (String r : canonicalRoles) {
            if (r != null) {
                String n = RoleKeyUtil.normalizeForScope(r);
                if (!n.isEmpty()) {
                    want.add(n);
                }
            }
        }
    }
    return findActiveInCourierScopeFiltered(anyDirectionInCluster, want);
}

private static ObservableList<User> findActiveInCourierScopeFiltered(
        int anyDirectionInCluster, java.util.Set<String> wantedRoleNormalized) {
    ObservableList<User> list = FXCollections.observableArrayList();
    if (wantedRoleNormalized == null || wantedRoleNormalized.isEmpty() || anyDirectionInCluster <= 0) {
        return list;
    }
    java.util.List<Integer> dIds = DirectionDAO.getDirectionIdCluster(anyDirectionInCluster);
    if (dIds == null || dIds.isEmpty()) {
        return list;
    }
    java.util.LinkedHashSet<Integer> sousInScope = new java.util.LinkedHashSet<>();
    for (int did : dIds) {
        for (SousDirection sd : DirectionDAO.getSousDirectionsForDirection(did)) {
            sousInScope.add(sd.getId());
        }
    }
    StringBuilder sql = new StringBuilder(
            "SELECT id, username, role, active FROM users WHERE active = 1 AND (");
    sql.append("direction_id IN (");
    for (int i = 0; i < dIds.size(); i++) {
        sql.append(i > 0 ? ",?" : "?");
    }
    sql.append(")");
    if (!sousInScope.isEmpty()) {
        sql.append(" OR sous_direction_id IN (");
        int i0 = 0;
        for (int ignored : sousInScope) {
            sql.append(i0++ > 0 ? ",?" : "?");
        }
        sql.append(")");
    }
    sql.append(") ORDER BY username");
    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql.toString())) {
        int p = 1;
        for (int did : dIds) {
            ps.setInt(p++, did);
        }
        for (int sid : sousInScope) {
            ps.setInt(p++, sid);
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String rdb = rs.getString("role");
                if (rdb == null || !wantedRoleNormalized.contains(RoleKeyUtil.normalizeForScope(rdb))) {
                    continue;
                }
                String un = rs.getString("username");
                if (un == null) {
                    continue;
                }
                if (seen.add(un.toLowerCase(Locale.ROOT))) {
                    User u = new User();
                    u.setId(rs.getInt("id"));
                    u.setUsername(un);
                    u.setRole(rdb);
                    u.setActive(true);
                    list.add(u);
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    list.sort(Comparator.comparing(
            (User u) -> u.getUsername() == null ? "" : u.getUsername(),
            String.CASE_INSENSITIVE_ORDER));
    return list;
}

    /** User ids in a direction for one of the given roles (e.g. SECRETAIRE, DIRECTEUR). */
    public static List<Integer> findUserIdsInDirectionForRoles(int directionId, String... roles) {
        List<Integer> out = new ArrayList<>();
        if (directionId <= 0 || roles == null || roles.length == 0) {
            return out;
        }
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < roles.length; i++) {
            in.append(i > 0 ? ",?" : "?");
        }
        String sql = "SELECT id FROM users WHERE active = 1 AND direction_id = ? AND UPPER(role) IN (" + in + ")";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, directionId);
            for (int i = 0; i < roles.length; i++) {
                ps.setString(2 + i, roles[i].trim().toUpperCase(java.util.Locale.ROOT));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getInt(1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    /**
     * Active users for MyShift monthly planning. If {@code sousDirectionId} is set, it wins;
     * else if {@code directionId} is set, filter by direction; else all active users (e.g. super admin).
     */
    public static List<User> listForShiftPlan(Integer directionId, Integer sousDirectionId) {
        List<User> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT u.id, u.username, u.matricule FROM users u WHERE u.active = 1 ");
        if (sousDirectionId != null) {
            sql.append("AND u.sous_direction_id = ? ");
        } else if (directionId != null) {
            sql.append("AND u.direction_id = ? ");
        }
        sql.append("ORDER BY u.username");
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int p = 1;
            if (sousDirectionId != null) {
                ps.setInt(p++, sousDirectionId);
            } else if (directionId != null) {
                ps.setInt(p++, directionId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User u = new User();
                    u.setId(rs.getInt(1));
                    u.setUsername(rs.getString(2));
                    u.setMatricule(rs.getString(3));
                    out.add(u);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }
}
