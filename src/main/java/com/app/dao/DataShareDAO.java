package com.app.dao;

import com.app.auth.Session;
import com.app.model.DataShareFile;
import com.app.model.User;
import com.app.util.DB;
import com.app.util.DbConfig;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class DataShareDAO {
    public static final long MAX_SHARE_FILE_SIZE_BYTES = 15L * 1024L * 1024L; // 15 MB

    private static void validateShareFileSize(String fileName, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new RuntimeException("Invalid file path.");
        }
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new RuntimeException("Selected file does not exist.");
        }
        long size = file.length();
        if (size > MAX_SHARE_FILE_SIZE_BYTES) {
            throw new RuntimeException(
                    "File \"" + fileName + "\" exceeds 15 MB limit."
            );
        }
    }

    private static String generateOtp() {
        int n = ThreadLocalRandom.current().nextInt(100000, 1_000_000);
        return String.valueOf(n);
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("OTP hashing failed", e);
        }
    }

    // =====================================================
    // SHARE FILE
    // =====================================================
   public static int shareFile(String fileName,
                            String filePath,
                            List<User> recipients,
                            String expirationDate) {

    int fileId = 0;
    validateShareFileSize(fileName, filePath);

    String insertFileSQL = """
            INSERT INTO datashare_files
            (file_name, file_path, shared_by_id, shared_by_name,
             shared_role, sous_direction, date_shared, time_shared)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    try (Connection conn = DB.getConnection()) {

        conn.setAutoCommit(false);

        // =====================================
        // INSERT FILE
        // =====================================
        try (PreparedStatement fileStmt = DbConfig.isOracle()
                ? conn.prepareStatement(insertFileSQL, new String[] { "ID" })
                : conn.prepareStatement(insertFileSQL, Statement.RETURN_GENERATED_KEYS)) {

            fileStmt.setString(1, fileName);
            fileStmt.setString(2, filePath);
            fileStmt.setInt(3, Session.getUserId());
            fileStmt.setString(4, Session.getUsername());
            fileStmt.setString(5, Session.getRole());
            fileStmt.setString(6, Session.getSousDirection());
            fileStmt.setString(7, LocalDate.now().toString());
            fileStmt.setString(8, LocalTime.now().toString());

            fileStmt.executeUpdate();

            ResultSet rs = fileStmt.getGeneratedKeys();

            if (!rs.next()) {
                throw new SQLException("Failed to retrieve file ID.");
            }

            Number generatedId = (Number) rs.getObject(1);
            if (generatedId == null) {
                throw new SQLException("Failed to retrieve numeric file ID.");
            }
            fileId = generatedId.intValue();
        }

        // =====================================
        // INSERT RECIPIENTS
        // =====================================
        String insertRecipientSQL = """
                INSERT INTO datashare_recipients
                (file_id, recipient_id, recipient_name, recipient_role, otp_hash, otp_expires_at, otp_verified_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL)
                """;

        List<String> recipientOtps = new ArrayList<>();
        try (PreparedStatement recipientStmt =
                     conn.prepareStatement(insertRecipientSQL)) {

            for (User user : recipients) {
                String otp = generateOtp();
                String otpHash = sha256(otp);
                String otpExpiresAt = LocalDateTime.now().plusHours(24).toString();

                recipientStmt.setInt(1, fileId);
                recipientStmt.setInt(2, user.getId());
                recipientStmt.setString(3, user.getUsername());
                recipientStmt.setString(4, user.getRole());
                recipientStmt.setString(5, otpHash);
                recipientStmt.setString(6, otpExpiresAt);

                recipientStmt.addBatch();
                recipientOtps.add(otp);
            }

            recipientStmt.executeBatch();
        }

        // =====================================
        // COMMIT MAIN TRANSACTION
        // =====================================
        conn.commit();

        // =====================================
        // CREATE NOTIFICATIONS + AUDIT
        // (done after commit for safety)
        // =====================================
        for (int i = 0; i < recipients.size(); i++) {
            User user = recipients.get(i);
            String otp = i < recipientOtps.size() ? recipientOtps.get(i) : "";

            // 🔔 Notification includes OTP so receiver can unlock access.
            NotificationDAO.create(
                    user.getId(),
                    "New File Shared",
                    Session.getUsername() + " shared \"" + fileName + "\" with you. OTP: " + otp,
                    "DATASHARE",
                    "DATASHARE_FILE",
                    fileId,
                    fileName
            );

            // 📜 Audit log
            DataShareAuditDAO.log(
                    fileId,
                    fileName,
                    Session.getUserId(),
                    Session.getUsername(),
                    user.getId(),
                    user.getUsername(),
                    "SHARED"
            );
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return fileId;
}

    // =====================================================
    // FILES SHARED WITH USER
    // =====================================================
    public static ResultSet getFilesSharedWithUser(int userId) {

        String sql = """
                SELECT f.*
                FROM datashare_files f
                JOIN datashare_recipients r
                ON f.id = r.file_id
                WHERE r.recipient_id = ?
                ORDER BY f.id DESC
                """;

        try {

            Connection conn = DB.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);

            return stmt.executeQuery();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // =====================================================
    // INBOX FILES
    // =====================================================
    public static List<DataShareFile> getInboxFiles(int userId) {

        List<DataShareFile> files = new ArrayList<>();

        String sql = """
                SELECT f.id,
                       f.file_name,
                       f.file_path,
                       f.shared_by_name,
                       f.shared_role,
                       f.date_shared,
                       f.time_shared
                FROM datashare_files f
                JOIN datashare_recipients r
                ON f.id = r.file_id
                WHERE r.recipient_id = ?
                ORDER BY f.id DESC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {

                String time = rs.getString("time_shared");

if(time != null && time.contains(".")){
    time = time.substring(0, time.indexOf("."));
}

DataShareFile file = new DataShareFile(
        rs.getInt("id"),
        rs.getString("file_name"),
        rs.getString("file_path"),
        rs.getString("shared_by_name"),
        rs.getString("shared_role"),
        rs.getString("date_shared"),
        time
);

                files.add(file);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return files;
    }

    // =====================================================
    // LOG VIEW ACTION
    // =====================================================
    public static void logView(int fileId, String fileName) {

        DataShareAuditDAO.log(
                fileId,
                fileName,
                Session.getUserId(),
                Session.getUsername(),
                Session.getUserId(),
                Session.getUsername(),
                "VIEWED"
        );
    }

    // =====================================================
    // LOG DOWNLOAD ACTION
    // =====================================================
    public static void logDownload(int fileId, String fileName) {

        DataShareAuditDAO.log(
                fileId,
                fileName,
                Session.getUserId(),
                Session.getUsername(),
                Session.getUserId(),
                Session.getUsername(),
                "DOWNLOADED"
        );
    }

    // =====================================================
    // DELETE FILE
    // =====================================================
    public static void deleteFile(int fileId, String fileName) {

        String sql = "DELETE FROM datashare_files WHERE id=?";

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, fileId);
            stmt.executeUpdate();

            // ================================
            // AUDIT LOG (DELETE)
            // ================================
            DataShareAuditDAO.log(
                    fileId,
                    fileName,
                    Session.getUserId(),
                    Session.getUsername(),
                    Session.getUserId(),
                    Session.getUsername(),
                    "DELETED"
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================
    // SEND MESSAGE
    // =====================================================
    public static void sendMessage(int fileId, String message) {

        String sql = """
            INSERT INTO datashare_messages
            (file_id, sender_id, sender_name, message, date_sent, time_sent)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DB.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, fileId);
            stmt.setInt(2, Session.getUserId());
            stmt.setString(3, Session.getUsername());
            stmt.setString(4, message);
            stmt.setString(5, LocalDate.now().toString());
            stmt.setString(6, LocalTime.now().toString());

            stmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void deleteFile(int id){

    String sql = "DELETE FROM datashare_files WHERE id = ?";

    try(Connection c = DB.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)){

        ps.setInt(1,id);
        ps.executeUpdate();

    }catch(Exception e){
        e.printStackTrace();
    }
}
    
   public static List<DataShareFile> getAllSharedFiles(){

    List<DataShareFile> files = new ArrayList<>();

    String sql = """
        SELECT
            id,
            file_name,
            file_path,
            file_size,
            downloads,
            expiration_date,
            shared_by_name AS shared_by,
            shared_role AS role,
            date_shared AS shared_date,
            time_shared AS shared_time
        FROM datashare_files
        ORDER BY id DESC
    """;

    try(Connection c = DB.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()){

        while(rs.next()){

            // GET TIME
            String time = rs.getString("shared_time");

            // FORMAT TIME (remove milliseconds/nanoseconds)
            if(time != null && time.contains(".")){
                time = time.substring(0, time.indexOf("."));
            }

            DataShareFile file = new DataShareFile(
                    rs.getInt("id"),
                    rs.getString("file_name"),
                    rs.getString("file_path"),
                    rs.getString("shared_by"),
                    rs.getString("role"),
                    rs.getString("shared_date"),
                    time
            );

            file.setFileSize(rs.getLong("file_size"));
            file.setDownloads(rs.getInt("downloads"));
            file.setExpirationDate(rs.getString("expiration_date"));

            files.add(file);
        }

    }catch(Exception e){
        e.printStackTrace();
    }

    return files;
}

    public static boolean isOtpVerifiedForRecipient(int fileId, int recipientId) {
        String sql = """
            SELECT otp_hash, otp_verified_at
            FROM datashare_recipients
            WHERE file_id = ?
              AND recipient_id = ?
            LIMIT 1
        """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            ps.setInt(2, recipientId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return false;
            String otpHash = rs.getString("otp_hash");
            if (otpHash == null || otpHash.isBlank()) {
                // Legacy shares before OTP rollout remain accessible.
                return true;
            }
            String verified = rs.getString("otp_verified_at");
            return verified != null && !verified.isBlank();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean verifyOtpForRecipient(int fileId, int recipientId, String otpInput) {
        if (otpInput == null || otpInput.isBlank()) return false;
        String sql = """
            SELECT otp_hash, otp_expires_at
            FROM datashare_recipients
            WHERE file_id = ?
              AND recipient_id = ?
            LIMIT 1
        """;
        String updateSql = """
            UPDATE datashare_recipients
            SET otp_verified_at = ?
            WHERE file_id = ?
              AND recipient_id = ?
        """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            ps.setInt(2, recipientId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return false;

            String otpHash = rs.getString("otp_hash");
            String otpExpiresAt = rs.getString("otp_expires_at");
            if (otpHash == null || otpHash.isBlank()) return false;

            if (otpExpiresAt != null && !otpExpiresAt.isBlank()) {
                LocalDateTime exp = LocalDateTime.parse(otpExpiresAt);
                if (LocalDateTime.now().isAfter(exp)) {
                    return false;
                }
            }

            boolean ok = otpHash.equalsIgnoreCase(sha256(otpInput.trim()));
            if (!ok) return false;

            try (PreparedStatement up = conn.prepareStatement(updateSql)) {
                up.setString(1, LocalDateTime.now().toString());
                up.setInt(2, fileId);
                up.setInt(3, recipientId);
                up.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    
    public static void increaseDownloadCount(int fileId){

    String sql = """
        UPDATE datashare_files
        SET downloads = downloads + 1
        WHERE id = ?
    """;

    try(Connection c = DB.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)){

        ps.setInt(1,fileId);
        ps.executeUpdate();

    }catch(Exception e){
        e.printStackTrace();
    }
}
    
    public static void deleteExpiredFiles(){

    String sql = """
        DELETE FROM datashare_files
        WHERE expiration_date IS NOT NULL
        AND DATE(expiration_date) < DATE('now')
    """;

    try(Connection c = DB.getConnection();
        PreparedStatement ps = c.prepareStatement(sql)){

        ps.executeUpdate();

    }catch(Exception e){
        e.printStackTrace();
    }
}

public static String formatSize(long size){

    if(size > 1024*1024)
        return (size/(1024*1024)) + " MB";

    if(size > 1024)
        return (size/1024) + " KB";

    return size + " B";
}    
    
public static List<DataShareFile> getFilesSharedBy(int userId) {

    List<DataShareFile> list = new ArrayList<>();

    String sql = """
        SELECT *
        FROM datashare_files
        WHERE shared_by_id = ?
        ORDER BY date_shared DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            String time = rs.getString("time_shared");

if(time != null && time.contains(".")){
    time = time.substring(0, time.indexOf("."));
}

DataShareFile f = new DataShareFile(

    rs.getInt("id"),
    rs.getString("file_name"),
    rs.getString("file_path"),
    rs.getString("shared_by_name"),
    rs.getString("shared_role"),
    rs.getString("date_shared"),
    time

);

            list.add(f);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}




public static List<DataShareFile> getFilesSharedWith(int userId) {

    List<DataShareFile> list = new ArrayList<>();

    String sql = """
        SELECT f.*, u.username AS shared_by
        FROM datashare_files f
        JOIN datashare_recipients r ON f.id = r.file_id
        JOIN users u ON f.shared_by_id = u.id
        WHERE r.recipient_id = ?
        ORDER BY f.date_shared DESC
    """;

    try (Connection c = DB.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        while (rs.next()) {

            String time = rs.getString("time_shared");

if(time != null && time.contains(".")){
    time = time.substring(0, time.indexOf("."));
}

DataShareFile f = new DataShareFile(

    rs.getInt("id"),
    rs.getString("file_name"),
    rs.getString("file_path"),
    rs.getString("shared_by_name"),
    rs.getString("shared_role"),
    rs.getString("date_shared"),
    time

);

            list.add(f);
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return list;
}


    
    
}