package com.app.dao;

import com.app.model.MyShiftRow;
import com.app.util.DB;
import com.app.util.SqlDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class MyShiftDAO {

    private MyShiftDAO() {}

    private static String nowTs() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Closes any open session (no sign-out) for this user so a new sign-in can be recorded.
     */
    public static void closeOpenSessionsForUser(int userId) {
        String sql = "UPDATE myshift_signins SET sign_out_time = ? WHERE user_id = ? AND sign_out_time IS NULL";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            String t = nowTs();
            ps.setString(1, t);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int insertSignIn(
            int userId,
            String signinDay,
            String signInTime,
            Double latitude,
            Double longitude,
            String locationText,
            String countryCode,
            String city,
            String ip,
            boolean faceVerified,
            Double faceConfidence,
            String faceMethod) {
        closeOpenSessionsForUser(userId);
        String sql = """
                INSERT INTO myshift_signins
                (user_id, signin_day, sign_in_time, sign_out_time, latitude, longitude, location_text,
                 country_code, city, ip_address, face_verified, face_confidence, face_method)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, signinDay);
            ps.setString(3, signInTime);
            ps.setString(4, null);
            if (latitude != null) {
                ps.setDouble(5, latitude);
            } else {
                ps.setNull(5, java.sql.Types.DOUBLE);
            }
            if (longitude != null) {
                ps.setDouble(6, longitude);
            } else {
                ps.setNull(6, java.sql.Types.DOUBLE);
            }
            ps.setString(7, locationText);
            ps.setString(8, countryCode);
            ps.setString(9, city);
            ps.setString(10, ip);
            ps.setInt(11, faceVerified ? 1 : 0);
            if (faceConfidence != null) {
                ps.setDouble(12, faceConfidence);
            } else {
                ps.setNull(12, java.sql.Types.DOUBLE);
            }
            ps.setString(13, faceMethod);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static boolean signOutUser(int userId) {
        String day = java.time.LocalDate.now().toString();
        String q1 = """
                SELECT id FROM myshift_signins
                WHERE user_id = ? AND signin_day = ? AND sign_out_time IS NULL
                ORDER BY id DESC
                """ + SqlDialect.limitRows(1);
        try (Connection c = DB.getConnection()) {
            int rowId;
            try (PreparedStatement ps = c.prepareStatement(q1)) {
                ps.setInt(1, userId);
                ps.setString(2, day);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return false;
                    }
                    rowId = rs.getInt(1);
                }
            }
            String up = "UPDATE myshift_signins SET sign_out_time = ? WHERE id = ?";
            try (PreparedStatement u = c.prepareStatement(up)) {
                u.setString(1, nowTs());
                u.setInt(2, rowId);
                return u.executeUpdate() > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /** True if the user has an open sign-in today (not signed out). */
    public static boolean isPresentNow(int userId) {
        String day = java.time.LocalDate.now().toString();
        String sql = """
                SELECT 1 FROM myshift_signins
                WHERE user_id = ? AND signin_day = ? AND sign_out_time IS NULL
                """ + SqlDialect.limitRows(1);
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, day);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Agents currently present (not signed out) for this calendar day, scoped by direction and optionally sous-direction.
     * Pass {@code null} for directionId to skip direction filter (super admin).
     */
    public static List<MyShiftRow> listPresentToday(
            String day,
            Integer directionId,
            Integer sousDirectionId) {
        StringBuilder q = new StringBuilder("""
                SELECT s.id, s.user_id, s.signin_day, s.sign_in_time, s.sign_out_time, s.location_text, s.city,
                s.country_code, s.ip_address, s.face_verified, s.face_confidence, s.face_method,
                u.username, u.matricule, u.role AS user_role, u.attendance_signature AS attendance_signature
                FROM myshift_signins s
                JOIN users u ON u.id = s.user_id
                WHERE s.signin_day = ? AND s.sign_out_time IS NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(day);
        if (sousDirectionId != null) {
            q.append(" AND u.sous_direction_id = ?");
            args.add(sousDirectionId);
        } else if (directionId != null) {
            q.append(" AND u.direction_id = ?");
            args.add(directionId);
        }
        q.append(" ORDER BY s.sign_in_time");
        return queryMyShiftRows(q.toString(), args);
    }

    public static List<MyShiftRow> listRangeReport(
            String fromDay,
            String toDay,
            Integer directionId,
            Integer sousDirectionId) {
        StringBuilder q = new StringBuilder("""
                SELECT s.id, s.user_id, s.signin_day, s.sign_in_time, s.sign_out_time, s.location_text, s.city,
                s.country_code, s.ip_address, s.face_verified, s.face_confidence, s.face_method,
                u.username, u.matricule, u.role AS user_role, u.attendance_signature AS attendance_signature
                FROM myshift_signins s
                JOIN users u ON u.id = s.user_id
                WHERE s.signin_day >= ? AND s.signin_day <= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(fromDay);
        args.add(toDay);
        if (sousDirectionId != null) {
            q.append(" AND u.sous_direction_id = ?");
            args.add(sousDirectionId);
        } else if (directionId != null) {
            q.append(" AND u.direction_id = ?");
            args.add(directionId);
        }
        q.append(" ORDER BY s.signin_day, s.sign_in_time, u.username");
        return queryMyShiftRows(q.toString(), args);
    }

    private static List<MyShiftRow> queryMyShiftRows(String sql, List<Object> args) {
        List<MyShiftRow> out = new ArrayList<>();
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) {
                Object o = args.get(i);
                if (o instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (o instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else {
                    ps.setObject(i + 1, o);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs, true));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    private static MyShiftRow mapRow(ResultSet rs, boolean withUser) throws Exception {
        MyShiftRow r = new MyShiftRow();
        r.setSigninId(rs.getInt("id"));
        r.setUserId(rs.getInt("user_id"));
        r.setSigninDay(rs.getString("signin_day"));
        r.setSignInTime(rs.getString("sign_in_time"));
        r.setSignOutTime(rs.getString("sign_out_time"));
        r.setLocation(rs.getString("location_text"));
        r.setCity(rs.getString("city"));
        r.setCountry(rs.getString("country_code"));
        r.setIp(rs.getString("ip_address"));
        r.setFaceVerified(rs.getInt("face_verified") == 1);
        double fc = rs.getDouble("face_confidence");
        r.setFaceConfidence(rs.wasNull() ? null : fc);
        r.setFaceMethod(rs.getString("face_method"));
        if (withUser) {
            String un = rs.getString("username");
            r.setUsername(un);
            r.setNamesAndPostnoms(un);
            r.setMatricule(rs.getString("matricule"));
            r.setFonction(rs.getString("user_role"));
            try {
                r.setAttendanceSignature(rs.getString("attendance_signature"));
            } catch (Exception ignored) {
                // column missing before migration
            }
        }
        return r;
    }
}
