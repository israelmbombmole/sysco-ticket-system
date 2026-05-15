package com.app.dao;

import com.app.model.CourierJourneyLine;
import com.app.model.CourierPacket;
import com.app.model.SousDirection;
import com.app.model.User;
import com.app.util.AccessContext;
import com.app.util.DB;
import com.app.util.DbConfig;
import com.app.util.I18n;
import com.app.util.RoleKeyUtil;
import com.app.util.TicketUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Physical courier tracking: packets and journey events.
 */
public final class CourierPacketDAO {

    private CourierPacketDAO() {}

    public static final String E_CREATED = "CREATED";
    public static final String E_DIRECTION = "DIRECTION_SET";
    public static final String E_SOUS = "SOUS_ROUTED";
    public static final String E_ASS_SD = "ASSIGNED_SOUS_DIRECTEUR";
    public static final String E_ASS_IN = "ASSIGNED_INSPECTEUR";
    public static final String E_ASS_CO = "ASSIGNED_CONTROLEUR";
    public static final String E_ASS_VE = "ASSIGNED_VERIFICATEUR";
    public static final String E_RESOLVED = "RESOLVED";
    public static final String E_GRANT_SECRET = "SECRET_ROUTING_GRANTED";
    public static final String E_TICKET = "TICKET_CREATED";
    /** Additional direction visibility (beyond primary {@code target_direction_id}). */
    public static final String E_EXTRA_DIRECTION = "EXTRA_DIRECTION";

    private static void insertEvent(Connection c, int packetId, String type, Integer actor, Integer related,
            Integer directionId, Integer sousDirectionId, String note) throws SQLException {
        if (DbConfig.isOracle()) {
            String sql = """
                INSERT INTO courier_journey_events (packet_id, event_type, at_time, actor_user_id, related_user_id, direction_id, sous_direction_id, note)
                VALUES (?, ?, SYSTIMESTAMP, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                int i = 1;
                ps.setInt(i++, packetId);
                ps.setString(i++, type);
                setNullableInt(ps, i++, actor);
                setNullableInt(ps, i++, related);
                setNullableInt(ps, i++, directionId);
                setNullableInt(ps, i++, sousDirectionId);
                if (note != null) {
                    ps.setString(i, note);
                } else {
                    ps.setNull(i, Types.CLOB);
                }
                ps.executeUpdate();
            }
        } else {
            String sql = """
                INSERT INTO courier_journey_events (packet_id, event_type, actor_user_id, related_user_id, direction_id, sous_direction_id, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, packetId);
                ps.setString(2, type);
                setNullableInt(ps, 3, actor);
                setNullableInt(ps, 4, related);
                setNullableInt(ps, 5, directionId);
                setNullableInt(ps, 6, sousDirectionId);
                if (note != null) {
                    ps.setString(7, note);
                } else {
                    ps.setNull(7, Types.VARCHAR);
                }
                ps.executeUpdate();
            }
        }
    }

    private static void setNullableInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v != null) {
            ps.setInt(i, v);
        } else {
            ps.setNull(i, Types.INTEGER);
        }
    }

    public static int createPacket(String title, String description, int createdBy) throws SQLException {
        return createPacketFull(title, description, createdBy, null, "MEDIUM", null, null);
    }

    public static int createPacketFull(
            String title,
            String description,
            int createdBy,
            String sender,
            String priority,
            String registrationDate,
            String attachmentPath) throws SQLException {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("title");
        }
        String pr = (priority == null || priority.isBlank()) ? "MEDIUM" : priority.trim();
        String reg = registrationDate != null ? registrationDate.trim() : null;
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int id;
                if (DbConfig.isOracle()) {
                    String ins = """
                            INSERT INTO courier_packets (ref_code, title, description, status, created_by, sender, priority, registration_date, attachment_path, secretaire_can_route_sous)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """;
                    try (PreparedStatement ps = c.prepareStatement(ins, new String[] { "ID" })) {
                        ps.setString(1, "PENDING");
                        ps.setString(2, t);
                        if (description != null) {
                            ps.setString(3, description);
                        } else {
                            ps.setNull(3, Types.CLOB);
                        }
                        ps.setString(4, CourierPacket.ST_AWAITING_DIRECTION);
                        ps.setInt(5, createdBy);
                        if (sender != null) {
                            ps.setString(6, sender);
                        } else {
                            ps.setNull(6, Types.VARCHAR);
                        }
                        ps.setString(7, pr);
                        if (reg != null) {
                            ps.setString(8, reg);
                        } else {
                            ps.setNull(8, Types.VARCHAR);
                        }
                        if (attachmentPath != null) {
                            ps.setString(9, attachmentPath);
                        } else {
                            ps.setNull(9, Types.VARCHAR);
                        }
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (!rs.next()) {
                                throw new SQLException("no id");
                            }
                            id = (int) rs.getLong(1);
                        }
                    }
                } else {
                    String ins = """
                            INSERT INTO courier_packets (ref_code, title, description, status, created_by, sender, priority, registration_date, attachment_path, secretaire_can_route_sous)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """;
                    try (PreparedStatement ps = c.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, "PENDING");
                        ps.setString(2, t);
                        if (description != null) {
                            ps.setString(3, description);
                        } else {
                            ps.setNull(3, Types.VARCHAR);
                        }
                        ps.setString(4, CourierPacket.ST_AWAITING_DIRECTION);
                        ps.setInt(5, createdBy);
                        if (sender != null) {
                            ps.setString(6, sender);
                        } else {
                            ps.setNull(6, Types.VARCHAR);
                        }
                        ps.setString(7, pr);
                        if (reg != null) {
                            ps.setString(8, reg);
                        } else {
                            ps.setNull(8, Types.VARCHAR);
                        }
                        if (attachmentPath != null) {
                            ps.setString(9, attachmentPath);
                        } else {
                            ps.setNull(9, Types.VARCHAR);
                        }
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (!rs.next()) {
                                throw new SQLException("no id");
                            }
                            id = rs.getInt(1);
                        }
                    }
                }
                int y = java.time.Year.now(java.time.ZoneId.systemDefault()).getValue();
                String ref = "CP-" + y + "-" + String.format("%05d", id);
                try (PreparedStatement up = c.prepareStatement("UPDATE courier_packets SET ref_code = ? WHERE id = ?")) {
                    up.setString(1, ref);
                    up.setInt(2, id);
                    up.executeUpdate();
                }
                insertEvent(c, id, E_CREATED, createdBy, null, null, null, null);
                c.commit();
                return id;
            } catch (Exception e) {
                c.rollback();
                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }
                throw new SQLException(e);
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public static void setDirection(int packetId, int directionId, int actorId) throws SQLException {
        String sql = "UPDATE courier_packets SET target_direction_id = ?, status = ? WHERE id = ?";
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, directionId);
                ps.setString(2, CourierPacket.ST_DIRECTED);
                ps.setInt(3, packetId);
                ps.executeUpdate();
            }
            insertEvent(c, packetId, E_DIRECTION, actorId, null, directionId, null, null);
            c.commit();
        }
        notifyDirectionStaffOnRoute(packetId, directionId);
    }

    public static void setSousDirection(int packetId, int sousDirectionId, int actorId) throws SQLException {
        setSousDirection(packetId, sousDirectionId, actorId, null, false);
    }

    public static void setSousDirection(
            int packetId, int sousDirectionId, int actorId, String actorRole, boolean systemAdmin) throws SQLException {
        List<CourierPacket> one = queryPackets(selectBase() + " WHERE p.id = ?", List.of(packetId));
        if (one.isEmpty()) {
            throw new SQLException("Packet not found");
        }
        CourierPacket p = one.get(0);
        if (p.getTargetDirectionId() == null) {
            throw new IllegalStateException("direction_first");
        }
        if (!DirectionDAO.isSousDirectionUnderPacketDirection(sousDirectionId, p.getTargetDirectionId())) {
            throw new IllegalStateException("sous_mismatch");
        }
        int sousOptions = DirectionDAO.getSousDirectionsForDirectionCluster(p.getTargetDirectionId()).size();
        if (sousOptions > 0
                && actorRole != null
                && "SECRETAIRE".equalsIgnoreCase(actorRole)
                && !p.isSecretaireMayRouteSous()
                && !systemAdmin) {
            throw new IllegalStateException("secretaire_not_granted");
        }
        String sql = "UPDATE courier_packets SET target_sous_direction_id = ?, status = ? WHERE id = ?";
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, sousDirectionId);
                ps.setString(2, CourierPacket.ST_SOUS_ASSIGNED);
                ps.setInt(3, packetId);
                ps.executeUpdate();
            }
            insertEvent(c, packetId, E_SOUS, actorId, null, null, sousDirectionId, null);
            c.commit();
        }
        int tid = 0;
        if (p.getLinkedTicketId() == null) {
            int depId = DepartmentDAO.getFirstDepartmentId();
            tid = TicketDAO.createInternalFromCourier(p, depId);
            if (tid > 0) {
                try (Connection c2 = DB.getConnection();
                     PreparedStatement up = c2.prepareStatement(
                             "UPDATE courier_packets SET linked_ticket_id = ? WHERE id = ?")) {
                    up.setInt(1, tid);
                    up.setInt(2, packetId);
                    up.executeUpdate();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try (Connection c2 = DB.getConnection()) {
                    c2.setAutoCommit(false);
                    try {
                        insertEvent(c2, packetId, E_TICKET, actorId, null, null, null, TicketUtil.formatTicketRef(tid));
                        c2.commit();
                    } catch (Exception e) {
                        c2.rollback();
                    } finally {
                        c2.setAutoCommit(true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            tid = p.getLinkedTicketId();
        }
        notifySousRouted(p, sousDirectionId, tid, actorId);
    }

    /**
     * Read packet fields in a short-lived connection, then close before inserting notifications.
     * SQLite: an open {@link ResultSet} holds a read lock; writing from another connection then causes SQLITE_BUSY.
     */
    private static void notifyDirectionStaffOnRoute(int packetId, int directionId) {
        String ref;
        String title;
        int creator;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT ref_code, title, created_by FROM courier_packets WHERE id = ?")) {
            ps.setInt(1, packetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                String r0 = rs.getString(1);
                ref = r0 != null ? r0 : "";
                String t0 = rs.getString(2);
                title = t0 != null ? t0 : "";
                creator = rs.getInt(3);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        String titleN = I18n.t("courierNotifDirTitle", "Courrier — direction");
        String body = I18n.t("courierNotifDirBody", "Courrier: ") + ref + " — " + title
                + " (" + I18n.t("courierNotifDirAction", "direction assigned") + ")";
        for (int uid : UserDAO.findUserIdsInDirectionForRoles(directionId, "SECRETAIRE", "DIRECTEUR")) {
            NotificationDAO.create(uid, titleN, body, "COURIER", "COURIER_PACKET", packetId, ref);
        }
        if (creator > 0) {
            String tb = I18n.t("courierNotifCreatorRouted", "Your courrier was notified: ") + ref;
            NotificationDAO.create(creator, titleN, tb, "COURIER", "COURIER_PACKET", packetId, ref);
        }
    }

    private static void notifySousRouted(CourierPacket p, int sousId, int ticketId, int actorId) {
        String ref = p.getRefCode() != null ? p.getRefCode() : String.valueOf(p.getId());
        String t = p.getTitle() == null ? "" : p.getTitle();
        String sousName = null;
        if (p.getTargetDirectionId() != null) {
            for (SousDirection sd : DirectionDAO.getSousDirectionsForDirection(p.getTargetDirectionId())) {
                if (sd.getId() == sousId) {
                    sousName = sd.getName();
                    break;
                }
            }
        }
        if (sousName == null || sousName.isBlank()) {
            sousName = "SD #" + sousId;
        }
        String ticketLine = ticketId > 0
                ? " — " + I18n.t("courierNotifInternalTicket", "Internal ticket:") + " " + TicketUtil.formatTicketRef(ticketId)
                : "";
        String lineMsg = ref + " — " + t + " — " + I18n.t("courierNotifSousRoutedTo", "Routed to: ") + sousName + ticketLine;
        String titleN = I18n.t("courierNotifSousTitle", "Courrier — sous-direction");
        Set<Integer> sent = new LinkedHashSet<>();
        for (User u : UserDAO.findActiveByRoleAndSousDirection("SOUS-DIRECTEUR", sousId)) {
            if (sent.add(u.getId())) {
                NotificationDAO.create(u.getId(), titleN, lineMsg, "COURIER", "COURIER_PACKET", p.getId(), ref);
            }
        }
        if (p.getTargetDirectionId() != null) {
            for (int uid : UserDAO.findUserIdsInDirectionForRoles(
                    p.getTargetDirectionId(), "DIRECTEUR", "SECRETAIRE")) {
                if (uid == actorId) {
                    continue;
                }
                if (sent.add(uid)) {
                    NotificationDAO.create(uid, titleN, lineMsg, "COURIER", "COURIER_PACKET", p.getId(), ref);
                }
            }
        }
        if (p.getCreatedBy() > 0 && p.getCreatedBy() != actorId && sent.add(p.getCreatedBy())) {
            NotificationDAO.create(
                    p.getCreatedBy(),
                    I18n.t("courierNotifCourierSous", "Votre courrier"),
                    lineMsg,
                    "COURIER", "COURIER_PACKET", p.getId(), ref);
        }
    }

    public static void assignSousDirecteur(int packetId, int userId, int actorId) throws SQLException {
        doAssign(packetId, "assigned_sous_directeur_id", userId, E_ASS_SD, actorId, userId);
    }

    public static void assignInspecteur(int packetId, int userId, int actorId) throws SQLException {
        doAssign(packetId, "assigned_inspecteur_id", userId, E_ASS_IN, actorId, userId);
    }

    public static void assignControleur(int packetId, int userId, int actorId) throws SQLException {
        doAssign(packetId, "assigned_controleur_id", userId, E_ASS_CO, actorId, userId);
    }

    public static void assignVerificateur(int packetId, int userId, int actorId) throws SQLException {
        doAssign(packetId, "assigned_verificateur_id", userId, E_ASS_VE, actorId, userId);
    }

    private static void doAssign(int packetId, String col, int assignee, String ev, int actorId, int relatedUser) throws SQLException {
        String sql = "UPDATE courier_packets SET " + col + " = ?, status = ? WHERE id = ?";
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, assignee);
                ps.setString(2, CourierPacket.ST_IN_PROGRESS);
                ps.setInt(3, packetId);
                ps.executeUpdate();
            }
            insertEvent(c, packetId, ev, actorId, relatedUser, null, null, null);
            c.commit();
        }
        notifyAfterAssign(packetId, ev, assignee, actorId);
    }

    public static void resolve(int packetId, int actorId) throws SQLException {
        if (DbConfig.isOracle()) {
            String u = "UPDATE courier_packets SET status = ?, resolved_at = SYSTIMESTAMP, resolved_by = ? WHERE id = ?";
            try (Connection c = DB.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(u)) {
                    ps.setString(1, CourierPacket.ST_RESOLVED);
                    ps.setInt(2, actorId);
                    ps.setInt(3, packetId);
                    ps.executeUpdate();
                }
                insertEvent(c, packetId, E_RESOLVED, actorId, null, null, null, null);
                c.commit();
            }
        } else {
            String u = "UPDATE courier_packets SET status = ?, resolved_by = ?, resolved_at = datetime('now','localtime') WHERE id = ?";
            try (Connection c = DB.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement(u)) {
                    ps.setString(1, CourierPacket.ST_RESOLVED);
                    ps.setInt(2, actorId);
                    ps.setInt(3, packetId);
                    ps.executeUpdate();
                }
                insertEvent(c, packetId, E_RESOLVED, actorId, null, null, null, null);
                c.commit();
            }
        }
        notifyAfterResolve(packetId, actorId);
    }

    private static String selectBase() {
        String extraAgg = DbConfig.isOracle()
                ? "(SELECT LISTAGG(d_extra.name, ', ') WITHIN GROUP (ORDER BY d_extra.name) "
                + "FROM courier_packet_extra_directions x "
                + "INNER JOIN directions d_extra ON x.direction_id = d_extra.id "
                + "WHERE x.packet_id = p.id)"
                : "(SELECT GROUP_CONCAT(d_extra.name, ', ') "
                + "FROM courier_packet_extra_directions x "
                + "INNER JOIN directions d_extra ON x.direction_id = d_extra.id "
                + "WHERE x.packet_id = p.id)";
        return """
                SELECT p.id, p.ref_code, p.title, p.description, p.status,
                       p.target_direction_id, p.target_sous_direction_id, p.created_by, p.created_at, p.resolved_at,
                       p.assigned_sous_directeur_id, p.assigned_inspecteur_id, p.assigned_controleur_id, p.assigned_verificateur_id,
                       p.sender, p.priority, p.registration_date, p.attachment_path, p.secretaire_can_route_sous, p.linked_ticket_id,
                       d.name AS dname, sd.name AS sdname, uc.username AS ucname,
                       u1.username AS n1, u2.username AS n2, u3.username AS n3, u4.username AS n4,
                """
                + extraAgg
                + """
                 AS extra_direction_names
                FROM courier_packets p
                LEFT JOIN directions d ON p.target_direction_id = d.id
                LEFT JOIN sous_directions sd ON p.target_sous_direction_id = sd.id
                LEFT JOIN users uc ON p.created_by = uc.id
                LEFT JOIN users u1 ON p.assigned_sous_directeur_id = u1.id
                LEFT JOIN users u2 ON p.assigned_inspecteur_id = u2.id
                LEFT JOIN users u3 ON p.assigned_controleur_id = u3.id
                LEFT JOIN users u4 ON p.assigned_verificateur_id = u4.id
                """;
    }

    /**
     * Directeur and per-direction {@code ADMIN} (not system super) share the same list scope.
     */
    private static void appendScopeForDirectionManager(StringBuilder where, List<Object> args, Integer myDirectionId) {
        if (myDirectionId != null) {
            appendTargetDirectionInCluster(where, args, myDirectionId);
        } else {
            where.append(" AND p.target_direction_id IS NOT NULL ");
        }
    }

    /**
     * Primary direction or any row in {@code courier_packet_extra_directions} matching the cluster
     * (duplicate direction labels share one cluster).
     */
    private static void appendTargetDirectionInCluster(StringBuilder where, List<Object> args, int anchorId) {
        java.util.List<Integer> c = DirectionDAO.getDirectionIdCluster(anchorId);
        if (c.isEmpty()) {
            where.append(" AND 1=0 ");
            return;
        }
        if (c.size() == 1) {
            int cid = c.get(0);
            where.append(" AND (p.target_direction_id = ? OR EXISTS (SELECT 1 FROM courier_packet_extra_directions xed "
                    + "WHERE xed.packet_id = p.id AND xed.direction_id = ?)) ");
            args.add(cid);
            args.add(cid);
            return;
        }
        where.append(" AND (p.target_direction_id IN (");
        for (int i = 0; i < c.size(); i++) {
            if (i > 0) {
                where.append(", ");
            }
            where.append("?");
            args.add(c.get(i));
        }
        where.append(") OR EXISTS (SELECT 1 FROM courier_packet_extra_directions xed WHERE xed.packet_id = p.id "
                + "AND xed.direction_id IN (");
        for (int i = 0; i < c.size(); i++) {
            if (i > 0) {
                where.append(", ");
            }
            where.append("?");
            args.add(c.get(i));
        }
        where.append("))) ");
    }

    public static List<CourierPacket> listForScope(
            String viewRole, Integer myDirectionId, Integer mySousId, Integer filterDirId, String filterStatus) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        String r = com.app.util.RoleKeyUtil.normalizeForScope(
                viewRole == null || viewRole.isBlank() ? "" : viewRole);
        if ("COURIER".equals(r)) {
            // Enregistrement + affectation direction: list everything (creators and filters)
        } else if ("SECRETAIRE".equals(r)) {
            if (myDirectionId == null || myDirectionId <= 0) {
                return List.of();
            }
            appendTargetDirectionInCluster(where, args, myDirectionId);
        } else if ("ADMIN".equals(r)) {
            if (com.app.util.AccessContext.isSystemSuperAdmin()) {
                // company-wide
            } else {
                appendScopeForDirectionManager(where, args, myDirectionId);
            }
        } else if ("DIRECTEUR".equals(r)) {
            if (myDirectionId == null || myDirectionId <= 0) {
                return List.of();
            }
            appendTargetDirectionInCluster(where, args, myDirectionId);
        } else if ("SOUS-DIRECTEUR".equals(r) && mySousId != null) {
            where.append(" AND p.target_sous_direction_id = ? ");
            args.add(mySousId);
        } else if ("INSPECTEUR".equals(r) && myDirectionId != null) {
            appendTargetDirectionInCluster(where, args, myDirectionId);
        } else if (("CONTROLEUR".equals(r) || "VERIFICATEUR".equals(r) || "VERIFICATEUR-ASSISTANT".equals(r)) && mySousId != null) {
            where.append(" AND p.target_sous_direction_id = ? ");
            args.add(mySousId);
        } else if (("CONTROLEUR".equals(r) || "VERIFICATEUR".equals(r) || "VERIFICATEUR-ASSISTANT".equals(r)) && myDirectionId != null) {
            appendTargetDirectionInCluster(where, args, myDirectionId);
        } else {
            return List.of();
        }
        if (filterDirId != null && filterDirId > 0) {
            appendTargetDirectionInCluster(where, args, filterDirId);
        }
        if (filterStatus != null && !filterStatus.isBlank() && !"ALL".equalsIgnoreCase(filterStatus)) {
            if ("OPEN".equalsIgnoreCase(filterStatus)) {
                where.append(" AND p.status != ? ");
                args.add(CourierPacket.ST_RESOLVED);
            } else {
                where.append(" AND p.status = ? ");
                args.add(filterStatus);
            }
        }
        where.append(" ORDER BY p.id DESC");
        return queryPackets(selectBase() + where, args);
    }

    public static CourierPacket getById(int id) {
        List<CourierPacket> list = queryPackets(selectBase() + " WHERE p.id = ?", List.of(id));
        return list.isEmpty() ? null : list.get(0);
    }

    private static boolean targetDirectionMatchesUser(Integer targetDirectionId, Integer userDirectionId) {
        if (targetDirectionId == null || userDirectionId == null || userDirectionId <= 0) {
            return false;
        }
        return DirectionDAO.getDirectionIdCluster(userDirectionId).contains(targetDirectionId);
    }

    /** IDs of directions where this packet is duplicated for visibility (not the primary routing row). */
    public static List<Integer> listExtraDirectionIds(int packetId) {
        List<Integer> out = new ArrayList<>();
        if (packetId <= 0) {
            return out;
        }
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT direction_id FROM courier_packet_extra_directions WHERE packet_id = ? ORDER BY direction_id")) {
            ps.setInt(1, packetId);
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
     * Packet is covered by direction {@code anchorDirectionId}'s cluster via primary routing or extra-direction rows.
     */
    public static boolean isPacketReachableFromDirectionCluster(CourierPacket p, Integer anchorDirectionId) {
        if (p == null || anchorDirectionId == null || anchorDirectionId <= 0) {
            return false;
        }
        if (targetDirectionMatchesUser(p.getTargetDirectionId(), anchorDirectionId)) {
            return true;
        }
        List<Integer> cluster = DirectionDAO.getDirectionIdCluster(anchorDirectionId);
        for (int dirId : listExtraDirectionIds(p.getId())) {
            if (cluster.contains(dirId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Same visibility as {@link #listForScope} (without status filter) for read-only details.
     */
    public static boolean mayViewPacket(CourierPacket p, String listRole, Integer myDirectionId, Integer mySousId) {
        if (p == null) {
            return false;
        }
        String r = com.app.util.RoleKeyUtil.normalizeForScope(
                listRole == null || listRole.isBlank() ? "" : listRole);
        if (r.isEmpty()) {
            return false;
        }
        if ("COURIER".equals(r)) {
            return true;
        }
        if ("SECRETAIRE".equals(r)) {
            if (myDirectionId == null || myDirectionId <= 0) {
                return false;
            }
            return isPacketReachableFromDirectionCluster(p, myDirectionId);
        }
        if ("ADMIN".equals(r)) {
            if (com.app.util.AccessContext.isSystemSuperAdmin()) {
                return true;
            }
            if (myDirectionId == null) {
                return p.getTargetDirectionId() != null || !listExtraDirectionIds(p.getId()).isEmpty();
            }
            return isPacketReachableFromDirectionCluster(p, myDirectionId);
        }
        if ("DIRECTEUR".equals(r)) {
            if (myDirectionId == null || myDirectionId <= 0) {
                return false;
            }
            return isPacketReachableFromDirectionCluster(p, myDirectionId);
        }
        if ("SOUS-DIRECTEUR".equals(r) && mySousId != null) {
            return p.getTargetSousDirectionId() != null && mySousId.equals(p.getTargetSousDirectionId());
        }
        if ("INSPECTEUR".equals(r) && myDirectionId != null) {
            return isPacketReachableFromDirectionCluster(p, myDirectionId);
        }
        if (("CONTROLEUR".equals(r) || "VERIFICATEUR".equals(r) || "VERIFICATEUR-ASSISTANT".equals(r)) && mySousId != null) {
            return p.getTargetSousDirectionId() != null && mySousId.equals(p.getTargetSousDirectionId());
        }
        if (("CONTROLEUR".equals(r) || "VERIFICATEUR".equals(r) || "VERIFICATEUR-ASSISTANT".equals(r)) && myDirectionId != null) {
            return isPacketReachableFromDirectionCluster(p, myDirectionId);
        }
        return false;
    }

    /**
     * Edit / redirect / delete / multi-direction tools in courier management (direction-level roles only).
     */
    public static boolean mayAdministerCourier(CourierPacket p, String listRole, Integer myDirectionId, Integer mySousId) {
        if (!mayViewPacket(p, listRole, myDirectionId, mySousId)) {
            return false;
        }
        String adminRole = RoleKeyUtil.normalizeForScope(listRole == null ? "" : listRole);
        if ("ADMIN".equals(adminRole) && AccessContext.isSystemSuperAdmin()) {
            return true;
        }
        if ("ADMIN".equals(adminRole) && !AccessContext.isSystemSuperAdmin()) {
            return myDirectionId != null && isPacketReachableFromDirectionCluster(p, myDirectionId);
        }
        if ("DIRECTEUR".equals(adminRole) || "SECRETAIRE".equals(adminRole)) {
            return myDirectionId != null && isPacketReachableFromDirectionCluster(p, myDirectionId);
        }
        return false;
    }

    public static List<CourierPacket> queryPackets(String sql, List<Object> args) {
        List<CourierPacket> out = new ArrayList<>();
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) {
                Object a = args.get(i);
                if (a instanceof Integer) {
                    ps.setInt(i + 1, (Integer) a);
                } else {
                    ps.setString(i + 1, String.valueOf(a));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapPacket(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static ObservableList<CourierJourneyLine> loadJourney(int packetId) {
        ObservableList<CourierJourneyLine> lines = FXCollections.observableArrayList();
        String sql;
        if (DbConfig.isOracle()) {
            sql = """
                SELECT e.event_type, e.at_time, e.actor_user_id, e.related_user_id, e.direction_id, e.sous_direction_id, e.note,
                       ua.username AS an, ur.username AS rn, d.name AS dname, sd.name AS sname
                FROM courier_journey_events e
                LEFT JOIN users ua ON e.actor_user_id = ua.id
                LEFT JOIN users ur ON e.related_user_id = ur.id
                LEFT JOIN directions d ON e.direction_id = d.id
                LEFT JOIN sous_directions sd ON e.sous_direction_id = sd.id
                WHERE e.packet_id = ?
                ORDER BY e.id ASC
                """;
        } else {
            sql = """
                SELECT e.event_type, e.at_time, e.actor_user_id, e.related_user_id, e.direction_id, e.sous_direction_id, e.note,
                       ua.username AS an, ur.username AS rn, d.name AS dname, sd.name AS sname
                FROM courier_journey_events e
                LEFT JOIN users ua ON e.actor_user_id = ua.id
                LEFT JOIN users ur ON e.related_user_id = ur.id
                LEFT JOIN directions d ON e.direction_id = d.id
                LEFT JOIN sous_directions sd ON e.sous_direction_id = sd.id
                WHERE e.packet_id = ?
                ORDER BY e.id ASC
                """;
        }
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, packetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // SQLite JDBC does not implement ResultSet.getObject(int, Class)
                    String at = readJourneyEventTime(rs, 2);
                    String ev = rs.getString(1);
                    String line = formatJourneyLine(ev, rs);
                    lines.add(new CourierJourneyLine(at, line));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lines;
    }

    private static String readJourneyEventTime(ResultSet rs, int column) throws SQLException {
        String s = rs.getString(column);
        if (s != null && !s.isBlank()) {
            return s;
        }
        java.sql.Timestamp t = rs.getTimestamp(column);
        if (t != null) {
            return t.toString();
        }
        return "";
    }

    /** Legacy events stored {@code ticketId=123}; new events store the formatted ref (TCK-…). */
    private static String formatTicketRefForJourney(String note) {
        if (note == null || note.isBlank()) {
            return "—";
        }
        if (note.startsWith("ticketId=")) {
            try {
                int id = Integer.parseInt(note.substring("ticketId=".length()).trim());
                return TicketUtil.formatTicketRef(id);
            } catch (Exception e) {
                return note;
            }
        }
        return note;
    }

    private static String formatJourneyLine(String ev, ResultSet rs) throws SQLException {
        return switch (ev) {
            case E_CREATED -> "Enregistrement (création)";
            case E_DIRECTION -> "Redirection vers direction: " + rs.getString("dname");
            case E_EXTRA_DIRECTION -> {
                String rm = rs.getString("note");
                String dn = rs.getString("dname");
                String label = dn != null && !dn.isBlank() ? dn : "";
                if ("removed".equals(rm)) {
                    yield I18n.t("courierEvExtraRemoved", "Additional direction removed") + ": " + label;
                }
                yield I18n.t("courierEvExtraAdded", "Additional direction") + ": " + label;
            }
            case E_SOUS -> "Sous-direction: " + rs.getString("sname");
            case E_ASS_SD -> "Assigné sous-directeur: " + rs.getString("rn");
            case E_ASS_IN -> "Assigné inspecteur: " + rs.getString("rn");
            case E_ASS_CO -> "Assigné contrôleur: " + rs.getString("rn");
            case E_ASS_VE -> "Assigné vérificateur: " + rs.getString("rn");
            case E_RESOLVED -> "Résolu";
            case E_GRANT_SECRET -> I18n.t("courierEvGrant", "Délégation: la secrétaire peut router vers la sous-direction.");
            case E_TICKET -> {
                String n = rs.getString("note");
                yield I18n.t("courierEvTicket", "Internal ticket: ") + formatTicketRefForJourney(n);
            }
            default -> ev;
        };
    }

    private static CourierPacket mapPacket(ResultSet rs) throws SQLException {
        CourierPacket p = new CourierPacket();
        p.setId(rs.getInt("id"));
        p.setRefCode(rs.getString("ref_code"));
        p.setTitle(rs.getString("title"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        int td = rs.getInt("target_direction_id");
        p.setTargetDirectionId(rs.wasNull() ? null : (td == 0 ? null : td));
        int ts = rs.getInt("target_sous_direction_id");
        p.setTargetSousDirectionId(rs.wasNull() ? null : (ts == 0 ? null : ts));
        p.setCreatedBy(rs.getInt("created_by"));
        String created = rs.getString("created_at");
        p.setCreatedAt(created != null ? created : "");
        String resolved = rs.getString("resolved_at");
        p.setResolvedAt(resolved != null ? resolved : "");
        p.setTargetDirectionName(rs.getString("dname"));
        p.setTargetSousDirectionName(rs.getString("sdname"));
        p.setCreatedByName(rs.getString("ucname"));
        p.setAssignSousDirName(rs.getString("n1"));
        p.setAssignInspName(rs.getString("n2"));
        p.setAssignCtrlName(rs.getString("n3"));
        p.setAssignVerifName(rs.getString("n4"));
        p.setAssignSousDirecteurId(intOrNull(rs, "assigned_sous_directeur_id"));
        p.setAssignInspecteurId(intOrNull(rs, "assigned_inspecteur_id"));
        p.setAssignControleurId(intOrNull(rs, "assigned_controleur_id"));
        p.setAssignVerificateurId(intOrNull(rs, "assigned_verificateur_id"));
        p.setSender(safeString(rs, "sender"));
        p.setPriority(safeString(rs, "priority"));
        p.setRegistrationDate(safeString(rs, "registration_date"));
        p.setAttachmentPath(safeString(rs, "attachment_path"));
        int scf = rs.getInt("secretaire_can_route_sous");
        p.setSecretaireMayRouteSousFromDb(rs.wasNull() ? 0 : scf);
        p.setLinkedTicketId(intOrNull(rs, "linked_ticket_id"));
        p.setExtraDirectionNames(safeString(rs, "extra_direction_names"));
        return p;
    }

    private static String safeString(ResultSet rs, String col) {
        try {
            String s = rs.getString(col);
            return s == null ? "" : s;
        } catch (SQLException e) {
            return "";
        }
    }

    public static void setSecretaireMayRouteSous(int packetId, int directionId, int actorId) throws SQLException {
        String u = "UPDATE courier_packets SET secretaire_can_route_sous = 1 WHERE id = ? AND target_direction_id = ?";
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(u)) {
                ps.setInt(1, packetId);
                ps.setInt(2, directionId);
                if (ps.executeUpdate() == 0) {
                    c.rollback();
                    c.setAutoCommit(true);
                    throw new SQLException("not_found");
                }
            }
            insertEvent(c, packetId, E_GRANT_SECRET, actorId, null, directionId, null, I18n.t("courierNoteGrant", "delegate"));
            c.commit();
        }
        String ref = safeRefForPacket(packetId);
        if (ref == null || ref.isEmpty()) {
            ref = String.valueOf(packetId);
        }
        String titleN = I18n.t("courierNotifGrantTitle", "Courrier — délégation");
        for (int uid : UserDAO.findUserIdsInDirectionForRoles(directionId, "SECRETAIRE")) {
            String body = I18n.t("courierNotifGrantBody", "Vous pouvez choisir la sous-direction pour ce courrier (ref. dans la notification).");
            NotificationDAO.create(uid, titleN, body, "COURIER", "COURIER_PACKET", packetId, ref);
        }
        String titleDir = I18n.t("courierNotifGrantDirInfoTitle", "Courrier - delegation (director)");
        String bodyDir = I18n.t("courierNotifGrantDirInfoBody", "The secretary is now able to select the sub-direction. Ref. ") + ref;
        for (int uid : UserDAO.findUserIdsInDirectionForRoles(directionId, "DIRECTEUR")) {
            if (uid == actorId) {
                continue;
            }
            NotificationDAO.create(uid, titleDir, bodyDir, "COURIER", "COURIER_PACKET", packetId, ref);
        }
        List<CourierPacket> pl = queryPackets(selectBase() + " WHERE p.id=?", List.of(packetId));
        if (!pl.isEmpty() && pl.get(0).getCreatedBy() > 0) {
            int cr = pl.get(0).getCreatedBy();
            String crT = I18n.t("courierNotifGrantCrTitle", "Your courrier");
            String crB = I18n.t("courierNotifGrantCrBody", "The direction has authorized the secretary to select the sub-direction. Ref. ") + ref;
            NotificationDAO.create(cr, crT, crB, "COURIER", "COURIER_PACKET", packetId, ref);
        }
    }

    private static void notifyAfterAssign(int packetId, String eventCode, int assigneeId, int actorId) {
        try {
            List<CourierPacket> list = queryPackets(selectBase() + " WHERE p.id=?", List.of(packetId));
            if (list.isEmpty()) {
                return;
            }
            CourierPacket p = list.get(0);
            User assigneeU = UserDAO.findById(assigneeId);
            String assigneeName = assigneeU != null && assigneeU.getUsername() != null
                    ? assigneeU.getUsername()
                    : String.valueOf(assigneeId);
            String roleLabel = switch (eventCode) {
                case E_ASS_SD -> I18n.t("courierNotifRoleSd", "Sous-director");
                case E_ASS_IN -> I18n.t("courierNotifRoleInsp", "Inspector");
                case E_ASS_CO -> I18n.t("courierNotifRoleCtrl", "Controller");
                case E_ASS_VE -> I18n.t("courierNotifRoleVerif", "Auditor");
                default -> eventCode;
            };
            String ref = p.getRefCode() != null && !p.getRefCode().isBlank()
                    ? p.getRefCode()
                    : String.valueOf(p.getId());
            String ttitle = p.getTitle() == null ? "" : p.getTitle();
            String ticketLine = p.getLinkedTicketId() != null
                    ? " — " + I18n.t("courierNotifInternalTicket", "Internal ticket:") + " "
                            + TicketUtil.formatTicketRef(p.getLinkedTicketId())
                    : "";
            String actorName = null;
            if (actorId > 0) {
                User a = UserDAO.findById(actorId);
                if (a != null) {
                    actorName = a.getUsername();
                }
            }
            String byActor = (actorName != null && !actorName.isBlank())
                    ? " — " + I18n.t("courierNotifActionBy", "by") + " " + actorName
                    : "";
            String titleN = I18n.t("courierNotifAssignTitle", "Courrier — assignment");
            Set<Integer> recipients = new LinkedHashSet<>();
            recipients.add(assigneeId);
            if (p.getCreatedBy() > 0) {
                recipients.add(p.getCreatedBy());
            }
            if (p.getTargetDirectionId() != null) {
                for (int u : UserDAO.findUserIdsInDirectionForRoles(
                        p.getTargetDirectionId(), "DIRECTEUR", "SECRETAIRE")) {
                    recipients.add(u);
                }
            }
            if (p.getTargetSousDirectionId() != null) {
                for (User u : UserDAO.findActiveByRoleAndSousDirection("SOUS-DIRECTEUR", p.getTargetSousDirectionId())) {
                    recipients.add(u.getId());
                }
            }
            for (int uid : recipients) {
                if (uid == assigneeId) {
                    String myBody = I18n.t("courierNotifAssignToYou", "You are assigned: ")
                            + roleLabel + " — " + ref + " — " + ttitle
                            + ticketLine + byActor;
                    NotificationDAO.create(uid, titleN, myBody, "COURIER", "COURIER_PACKET", packetId, ref);
                } else if (uid == actorId) {
                    // actor already performed the action
                } else {
                    String fyi = ref + " — " + ttitle + " — " + roleLabel + ": " + assigneeName + ticketLine + byActor;
                    NotificationDAO.create(uid, titleN, fyi, "COURIER", "COURIER_PACKET", packetId, ref);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void notifyAfterResolve(int packetId, int actorId) {
        try {
            List<CourierPacket> list = queryPackets(selectBase() + " WHERE p.id=?", List.of(packetId));
            if (list.isEmpty()) {
                return;
            }
            CourierPacket p = list.get(0);
            String ref = p.getRefCode() != null && !p.getRefCode().isBlank()
                    ? p.getRefCode()
                    : String.valueOf(p.getId());
            String t = p.getTitle() == null ? "" : p.getTitle();
            User act = UserDAO.findById(actorId);
            String an = (act != null && act.getUsername() != null) ? act.getUsername() : "—";
            String titleN = I18n.t("courierNotifResolvedTitle", "Courrier — resolved");
            String lineMsg = ref + " — " + t + " — " + I18n.t("courierNotifResolvedBy", "Resolved by: ") + an;
            Set<Integer> recipients = new LinkedHashSet<>();
            if (p.getCreatedBy() > 0) {
                recipients.add(p.getCreatedBy());
            }
            if (p.getTargetDirectionId() != null) {
                for (int u : UserDAO.findUserIdsInDirectionForRoles(
                        p.getTargetDirectionId(), "DIRECTEUR", "SECRETAIRE")) {
                    recipients.add(u);
                }
            }
            if (p.getTargetSousDirectionId() != null) {
                for (User u : UserDAO.findActiveByRoleAndSousDirection("SOUS-DIRECTEUR", p.getTargetSousDirectionId())) {
                    recipients.add(u.getId());
                }
            }
            for (int aid : new int[] {
                nz(p.getAssignSousDirecteurId()),
                nz(p.getAssignInspecteurId()),
                nz(p.getAssignControleurId()),
                nz(p.getAssignVerificateurId())
            }) {
                if (aid > 0) {
                    recipients.add(aid);
                }
            }
            for (int uid : recipients) {
                if (uid == actorId) {
                    continue;
                }
                NotificationDAO.create(uid, titleN, lineMsg, "COURIER", "COURIER_PACKET", packetId, ref);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String safeRefForPacket(int packetId) {
        List<CourierPacket> list = queryPackets(selectBase() + " WHERE p.id=?", List.of(packetId));
        if (list.isEmpty()) {
            return "";
        }
        String r = list.get(0).getRefCode();
        return r == null ? "" : r;
    }

    public static void adminUpdatePacket(
            int packetId,
            String title,
            String description,
            String sender,
            String priority,
            String registrationDate,
            String attachmentPath) throws SQLException {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("title");
        }
        String u = """
                UPDATE courier_packets SET title=?, description=?, sender=?, priority=?, registration_date=?, attachment_path=?
                WHERE id = ?
                """;
        String pr = (priority == null || priority.isBlank()) ? "MEDIUM" : priority.trim();
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(u)) {
            ps.setString(1, t);
            if (description != null) {
                ps.setString(2, description);
            } else {
                ps.setNull(2, Types.CLOB);
            }
            if (sender != null) {
                ps.setString(3, sender);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.setString(4, pr);
            if (registrationDate != null) {
                ps.setString(5, registrationDate);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            if (attachmentPath != null) {
                ps.setString(6, attachmentPath);
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            ps.setInt(7, packetId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("not_found");
            }
        }
    }

    public static void addExtraDirection(int packetId, int directionId, int actorId) throws SQLException {
        CourierPacket p = getById(packetId);
        if (p == null) {
            throw new SQLException("not_found");
        }
        if (p.getTargetDirectionId() != null && p.getTargetDirectionId() == directionId) {
            throw new IllegalStateException("already_primary");
        }
        if (listExtraDirectionIds(packetId).contains(directionId)) {
            return;
        }
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO courier_packet_extra_directions (packet_id, direction_id) VALUES (?, ?)")) {
                ps.setInt(1, packetId);
                ps.setInt(2, directionId);
                ps.executeUpdate();
            }
            insertEvent(c, packetId, E_EXTRA_DIRECTION, actorId, null, directionId, null, null);
            c.commit();
        }
        notifyDirectionStaffOnRoute(packetId, directionId);
    }

    public static void removeExtraDirection(int packetId, int directionId, int actorId) throws SQLException {
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM courier_packet_extra_directions WHERE packet_id = ? AND direction_id = ?")) {
                ps.setInt(1, packetId);
                ps.setInt(2, directionId);
                if (ps.executeUpdate() == 0) {
                    c.rollback();
                    c.setAutoCommit(true);
                    return;
                }
            }
            insertEvent(c, packetId, E_EXTRA_DIRECTION, actorId, null, directionId, null, "removed");
            c.commit();
        }
    }

    /**
     * Change primary direction. If the courrier is {@code RESOLVED} and {@code reopenWorkflow} is false, only the
     * direction (and valid sous) are updated. If {@code reopenWorkflow} is true, status returns to {@code DIRECTED}
     * and resolution is cleared.
     */
    public static void adminRedirectPrimary(int packetId, int newDirectionId, int actorId, boolean reopenWorkflow)
            throws SQLException {
        CourierPacket p = getById(packetId);
        if (p == null) {
            throw new SQLException("not_found");
        }
        boolean wasResolved = CourierPacket.ST_RESOLVED.equalsIgnoreCase(p.getStatus());
        boolean keepResolved = wasResolved && !reopenWorkflow;

        Integer oldSous = p.getTargetSousDirectionId();
        boolean sousValid = oldSous == null
                || DirectionDAO.isSousDirectionUnderPacketDirection(oldSous, newDirectionId);
        Integer newSous = sousValid ? oldSous : null;

        if (keepResolved) {
            try (Connection c = DB.getConnection()) {
                c.setAutoCommit(false);
                String uq = "UPDATE courier_packets SET target_direction_id = ?, target_sous_direction_id = ? WHERE id = ?";
                try (PreparedStatement ps = c.prepareStatement(uq)) {
                    ps.setInt(1, newDirectionId);
                    if (newSous != null) {
                        ps.setInt(2, newSous);
                    } else {
                        ps.setNull(2, Types.INTEGER);
                    }
                    ps.setInt(3, packetId);
                    ps.executeUpdate();
                }
                insertEvent(c, packetId, E_DIRECTION, actorId, null, newDirectionId, null,
                        I18n.t("courierNoteAdminRedirectResolved", "admin redirect (kept resolved)"));
                c.commit();
            }
            notifyDirectionStaffOnRoute(packetId, newDirectionId);
            return;
        }

        String newStatus = p.getStatus() != null ? p.getStatus() : CourierPacket.ST_AWAITING_DIRECTION;
        if (wasResolved && reopenWorkflow) {
            newStatus = CourierPacket.ST_DIRECTED;
        } else if (!sousValid) {
            if (CourierPacket.ST_SOUS_ASSIGNED.equalsIgnoreCase(newStatus)
                    || CourierPacket.ST_IN_PROGRESS.equalsIgnoreCase(newStatus)) {
                newStatus = CourierPacket.ST_DIRECTED;
            }
        }

        boolean clearResolvedFields = wasResolved && reopenWorkflow;
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            String sql;
            if (clearResolvedFields) {
                sql = DbConfig.isOracle()
                        ? """
                        UPDATE courier_packets SET target_direction_id = ?, status = ?, target_sous_direction_id = ?,
                        resolved_at = NULL, resolved_by = NULL
                        WHERE id = ?
                        """
                        : """
                        UPDATE courier_packets SET target_direction_id = ?, status = ?, target_sous_direction_id = ?,
                        resolved_at = NULL, resolved_by = NULL
                        WHERE id = ?
                        """;
            } else {
                sql = """
                        UPDATE courier_packets SET target_direction_id = ?, status = ?, target_sous_direction_id = ?
                        WHERE id = ?
                        """;
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, newDirectionId);
                ps.setString(2, newStatus);
                if (newSous != null) {
                    ps.setInt(3, newSous);
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                ps.setInt(4, packetId);
                ps.executeUpdate();
            }
            insertEvent(c, packetId, E_DIRECTION, actorId, null, newDirectionId, null,
                    I18n.t("courierNoteAdminRedirect", "admin redirect"));
            c.commit();
        }
        notifyDirectionStaffOnRoute(packetId, newDirectionId);
    }

    public static boolean adminDeletePacket(int packetId) {
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM courier_packets WHERE id = ?")) {
            ps.setInt(1, packetId);
            return ps.executeUpdate() == 1;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void updateMyPacket(
            int packetId,
            int userId,
            String title,
            String description,
            String sender,
            String priority,
            String registrationDate,
            String attachmentPath) throws SQLException {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("title");
        }
        String u = """
                UPDATE courier_packets SET title=?, description=?, sender=?, priority=?, registration_date=?, attachment_path=?
                WHERE id = ? AND created_by = ? AND UPPER(status) <> 'RESOLVED'
                """;
        String pr = (priority == null || priority.isBlank()) ? "MEDIUM" : priority.trim();
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(u)) {
            ps.setString(1, t);
            if (description != null) {
                ps.setString(2, description);
            } else {
                ps.setNull(2, Types.CLOB);
            }
            if (sender != null) {
                ps.setString(3, sender);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.setString(4, pr);
            if (registrationDate != null) {
                ps.setString(5, registrationDate);
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            if (attachmentPath != null) {
                ps.setString(6, attachmentPath);
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            ps.setInt(7, packetId);
            ps.setInt(8, userId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("not_allowed");
            }
        }
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    /** Home dashboard: total in scope, not resolved, awaiting sous-direction (DIRECTED), resolved. */
    public record HomeCourierStats(int total, int notResolved, int directedAwaitingSous, int resolved) {}

    private static int countOne(String sql, List<Object> args) {
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.size(); i++) {
                bindArg(ps, i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static void bindArg(PreparedStatement ps, int i, Object a) throws SQLException {
        if (a instanceof Integer) {
            ps.setInt(i, (Integer) a);
        } else {
            ps.setString(i, String.valueOf(a));
        }
    }

    public static HomeCourierStats statsForDirection(int directionId) {
        if (directionId <= 0) {
            return new HomeCourierStats(0, 0, 0, 0);
        }
        String w = "target_direction_id = ?";
        int total = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w, List.of(directionId));
        int resolved = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w + " AND status = ?",
                List.of(directionId, CourierPacket.ST_RESOLVED));
        int notRes = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w + " AND status <> ?",
                List.of(directionId, CourierPacket.ST_RESOLVED));
        int awaitSous = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w + " AND status = ?",
                List.of(directionId, CourierPacket.ST_DIRECTED));
        return new HomeCourierStats(total, notRes, awaitSous, resolved);
    }

    public static HomeCourierStats statsForSous(int sousId) {
        if (sousId <= 0) {
            return new HomeCourierStats(0, 0, 0, 0);
        }
        String w = "target_sous_direction_id = ?";
        int total = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w, List.of(sousId));
        int resolved = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w + " AND status = ?",
                List.of(sousId, CourierPacket.ST_RESOLVED));
        int notRes = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w + " AND status <> ?",
                List.of(sousId, CourierPacket.ST_RESOLVED));
        // Third slot: in active pipeline (sous-direction is set; processing / assignments)
        int inPipe = countOne("SELECT COUNT(*) FROM courier_packets WHERE " + w + " AND status IN (?, ?)",
                List.of(sousId, CourierPacket.ST_SOUS_ASSIGNED, CourierPacket.ST_IN_PROGRESS));
        return new HomeCourierStats(total, notRes, inPipe, resolved);
    }

    /** All courriers in the company (e.g. ADMIN). */
    public static HomeCourierStats statsCompany() {
        int total = countOne("SELECT COUNT(*) FROM courier_packets", List.of());
        int resolved = countOne("SELECT COUNT(*) FROM courier_packets WHERE status = ?", List.of(CourierPacket.ST_RESOLVED));
        int notRes = countOne("SELECT COUNT(*) FROM courier_packets WHERE status <> ?", List.of(CourierPacket.ST_RESOLVED));
        int awaitSous = countOne("SELECT COUNT(*) FROM courier_packets WHERE status = ?", List.of(CourierPacket.ST_DIRECTED));
        return new HomeCourierStats(total, notRes, awaitSous, resolved);
    }

    /** COURIER home: my creations. */
    public static HomeCourierStats statsCreatedByUser(int userId) {
        if (userId <= 0) {
            return new HomeCourierStats(0, 0, 0, 0);
        }
        int total = countOne("SELECT COUNT(*) FROM courier_packets WHERE created_by = ?", List.of(userId));
        int resolved = countOne("SELECT COUNT(*) FROM courier_packets WHERE created_by = ? AND status = ?",
                List.of(userId, CourierPacket.ST_RESOLVED));
        int notRes = countOne("SELECT COUNT(*) FROM courier_packets WHERE created_by = ? AND status <> ?",
                List.of(userId, CourierPacket.ST_RESOLVED));
        int inPipe = countOne("SELECT COUNT(*) FROM courier_packets WHERE created_by = ? AND status IN (?, ?, ?, ?)",
                List.of(userId, CourierPacket.ST_AWAITING_DIRECTION, CourierPacket.ST_REGISTERED, CourierPacket.ST_DIRECTED, CourierPacket.ST_SOUS_ASSIGNED));
        return new HomeCourierStats(total, notRes, inPipe, resolved);
    }

    /** Courriers: direction set, sub-direction not, not resolved (company). */
    public static int countAwaitingSousCompany() {
        return countOne("SELECT COUNT(*) FROM courier_packets WHERE target_direction_id IS NOT NULL AND target_sous_direction_id IS NULL AND status <> ?",
                List.of(CourierPacket.ST_RESOLVED));
    }

    /** SECRETAIRE / DIRECTEUR home: same scope, limited to one direction. */
    public static int countAwaitingSousForDirection(int directionId) {
        if (directionId <= 0) {
            return 0;
        }
        return countOne(
                "SELECT COUNT(*) FROM courier_packets WHERE target_direction_id = ? AND target_sous_direction_id IS NULL AND status <> ?",
                List.of(directionId, CourierPacket.ST_RESOLVED));
    }

    public static List<CourierPacket> listTopForHome(String viewRole, Integer myDir, Integer mySous, int maxRows) {
        List<CourierPacket> all = listForScope(viewRole, myDir, mySous, null, null);
        if (all.size() <= maxRows) {
            return all;
        }
        return all.subList(0, maxRows);
    }

    private static final int COURIER_DASHBOARD_DETAIL_LIMIT = 3000;

    /**
     * Drill-down lists for director dashboard courrier tiles ({@link #statsCompany()}).
     * {@code bucket}: {@code total}, {@code open} (not resolved), {@code await_sous}, {@code resolved}.
     */
    public static List<CourierPacket> listPacketsCompanyDashboardBucket(String bucket) {
        String b = bucket == null ? "" : bucket.trim().toLowerCase();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        switch (b) {
            case "open":
            case "not_resolved":
            case "notresolved":
                where.append(" AND p.status <> ? ");
                args.add(CourierPacket.ST_RESOLVED);
                break;
            case "await_sous":
            case "awaitsous":
                where.append(" AND p.status = ? ");
                args.add(CourierPacket.ST_DIRECTED);
                break;
            case "resolved":
                where.append(" AND p.status = ? ");
                args.add(CourierPacket.ST_RESOLVED);
                break;
            case "total":
            default:
                break;
        }
        String sql = selectBase() + where + " ORDER BY p.id DESC LIMIT " + COURIER_DASHBOARD_DETAIL_LIMIT;
        return queryPackets(sql, args);
    }

    /**
     * Drill-down for {@link #statsForDirection(int)} tiles (single {@code target_direction_id}).
     */
    public static List<CourierPacket> listPacketsDirectionDashboardBucket(int directionId, String bucket) {
        if (directionId <= 0) {
            return List.of();
        }
        String b = bucket == null ? "" : bucket.trim().toLowerCase();
        StringBuilder where = new StringBuilder(" WHERE p.target_direction_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(directionId);
        switch (b) {
            case "open":
            case "not_resolved":
                where.append(" AND p.status <> ? ");
                args.add(CourierPacket.ST_RESOLVED);
                break;
            case "await_sous":
                where.append(" AND p.status = ? ");
                args.add(CourierPacket.ST_DIRECTED);
                break;
            case "resolved":
                where.append(" AND p.status = ? ");
                args.add(CourierPacket.ST_RESOLVED);
                break;
            case "total":
            default:
                break;
        }
        String sql = selectBase() + where + " ORDER BY p.id DESC LIMIT " + COURIER_DASHBOARD_DETAIL_LIMIT;
        return queryPackets(sql, args);
    }

    /**
     * Drill-down for {@link #statsForSous(int)} tiles (single {@code target_sous_direction_id}).
     * {@code bucket}: {@code total}, {@code open}, {@code pipeline}, {@code resolved}.
     */
    public static List<CourierPacket> listPacketsSousDashboardBucket(int sousId, String bucket) {
        if (sousId <= 0) {
            return List.of();
        }
        String b = bucket == null ? "" : bucket.trim().toLowerCase();
        StringBuilder where = new StringBuilder(" WHERE p.target_sous_direction_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(sousId);
        switch (b) {
            case "open":
            case "not_resolved":
                where.append(" AND p.status <> ? ");
                args.add(CourierPacket.ST_RESOLVED);
                break;
            case "pipeline":
                where.append(" AND p.status IN (?, ?) ");
                args.add(CourierPacket.ST_SOUS_ASSIGNED);
                args.add(CourierPacket.ST_IN_PROGRESS);
                break;
            case "resolved":
                where.append(" AND p.status = ? ");
                args.add(CourierPacket.ST_RESOLVED);
                break;
            case "total":
            default:
                break;
        }
        String sql = selectBase() + where + " ORDER BY p.id DESC LIMIT " + COURIER_DASHBOARD_DETAIL_LIMIT;
        return queryPackets(sql, args);
    }
}
