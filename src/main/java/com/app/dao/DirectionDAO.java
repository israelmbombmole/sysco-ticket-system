package com.app.dao;

import com.app.model.Direction;   // ✅ CORRECT IMPORT
import com.app.model.SousDirection;
import com.app.util.DB;
import com.app.util.DbConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class DirectionDAO {

    /**
     * Single display name for the systems / IT service (matches seed in {@link com.app.util.DB} and departments list).
     * Variants like {@code de l'Information}, {@code d'Information}, {@code (DSTI)} map to this via
     * {@link #directionClusterKey(String)}.
     */
    public static final String CANONICAL_DIRECTION_SYSTEMES_TI =
            "Direction des Systèmes et Technologies d\u2019information";

    private static final String SYSTEMS_TI_CLUSTER_KEY = "cluster:systemes-technologies-info";

    public static ObservableList<Direction> getAllDirections() {
        ObservableList<Direction> list = FXCollections.observableArrayList();
        String sql = "SELECT * FROM directions ORDER BY name";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Direction(rs.getInt("id"), rs.getString("name")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static Direction getByName(String name) {
        String sql = "SELECT * FROM directions WHERE name = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new Direction(rs.getInt("id"), rs.getString("name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Direction getById(int id) {
        if (id <= 0) {
            return null;
        }
        String sql = "SELECT id, name FROM directions WHERE id = ?";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Direction(rs.getInt("id"), rs.getString("name"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Strips a trailing parenthetical short name, e.g. "Direction … (ABC)" → "Direction …",
     * so UI catalog keys and DB labels can be matched.
     */
    public static String baseDirectionLabel(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("\\s*\\([^)]+\\)\\s*$", "").trim();
    }

    private static String normalizeApostrophesAndSpaces(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('`', '\'')
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * True for any spelling of the systems / IT direction, including {@code d'information} vs
     * {@code de l'Information} and {@code (DSTI)} (after parenthetical strip).
     */
    private static boolean isSystemsTechnologiesInformationDirectionName(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        if (!n.contains("systemes") || !n.contains("technologies")) {
            return false;
        }
        return n.contains("information");
    }

    /**
     * Stable key for duplicate detection: one key for the same logical service (parenthetical stripped;
     * systems/IT spellings normalized).
     */
    public static String directionClusterKey(String name) {
        if (name == null) {
            return "";
        }
        String base = baseDirectionLabel(name);
        if (base.isEmpty()) {
            base = name.trim();
        }
        String norm = normalizeApostrophesAndSpaces(base);
        if (isSystemsTechnologiesInformationDirectionName(norm)) {
            return SYSTEMS_TI_CLUSTER_KEY;
        }
        return norm.toLowerCase(Locale.ROOT);
    }

    /**
     * All {@code directions.id} values that refer to the same service after stripping a trailing
     * acronym in parentheses (two rows that match once the suffix is removed).
     * Use this so courrier rows and user accounts stay aligned when duplicate rows exist.
     */
    public static List<Integer> getDirectionIdCluster(int anchorId) {
        if (anchorId <= 0) {
            return List.of();
        }
        Direction anchor = getById(anchorId);
        if (anchor == null) {
            return List.of(anchorId);
        }
        String want = directionClusterKey(anchor.getName());
        if (want.isEmpty()) {
            return List.of(anchorId);
        }
        List<Integer> out = new ArrayList<>();
        for (Direction d : getAllDirections()) {
            if (d.getName() != null && directionClusterKey(d.getName()).equals(want)) {
                out.add(d.getId());
            }
        }
        if (out.isEmpty()) {
            return List.of(anchorId);
        }
        return out;
    }

    public static int getOrCreateDirectionId(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("Direction name cannot be empty");
        }

        Direction existing = getByName(clean);
        if (existing != null) {
            return existing.getId();
        }
        String wantKey = directionClusterKey(clean);
        for (Direction row : getAllDirections()) {
            if (row.getName() != null && row.getName().equalsIgnoreCase(clean)) {
                return row.getId();
            }
            if (row.getName() != null
                    && !wantKey.isEmpty()
                    && directionClusterKey(row.getName()).equals(wantKey)) {
                return row.getId();
            }
        }

        SousDirection autre = SousDirectionDAO.getByName("AUTRE");
        int autreId = (autre != null) ? autre.getId() : SousDirectionDAO.getOrCreateSousDirectionId("AUTRE");

        String nameToSave = SYSTEMS_TI_CLUSTER_KEY.equals(wantKey) ? CANONICAL_DIRECTION_SYSTEMES_TI : clean;

        String sql = "INSERT INTO directions(name, sous_direction_id) VALUES (?, ?)";
        try (Connection c = DB.getConnection();
             PreparedStatement ps = DbConfig.isOracle()
                     ? c.prepareStatement(sql, new String[] { "ID" })
                     : c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nameToSave);
            ps.setInt(2, autreId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                Number generatedId = (Number) rs.getObject(1);
                if (generatedId == null) {
                    throw new RuntimeException("Failed to create direction: no numeric ID returned");
                }
                return generatedId.intValue();
            }
        } catch (Exception e) {
            Direction retry = getByName(clean);
            if (retry != null) {
                return retry.getId();
            }
            if (SYSTEMS_TI_CLUSTER_KEY.equals(wantKey)) {
                Direction r2 = getByName(CANONICAL_DIRECTION_SYSTEMES_TI);
                if (r2 != null) {
                    return r2.getId();
                }
            }
            throw new RuntimeException(e);
        }

        throw new RuntimeException("Failed to create direction: " + clean);
    }

    public static ObservableList<Direction> getDirectionsBySousDirection(int sousDirectionId) {

        ObservableList<Direction> list = FXCollections.observableArrayList();

        String sql = "SELECT * FROM directions WHERE sous_direction_id=? ORDER BY name";

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, sousDirectionId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new Direction(
                        rs.getInt("id"),
                        rs.getString("name")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public static ObservableList<SousDirection> getSousDirectionsForDirection(int directionId) {
        ObservableList<SousDirection> list = FXCollections.observableArrayList();
        String sql = """
            SELECT sd.id, sd.name
            FROM direction_sous_direction_map m
            JOIN sous_directions sd ON sd.id = m.sous_direction_id
            WHERE m.direction_id = ?
            ORDER BY CASE WHEN sd.name = 'AUTRE' THEN 1 ELSE 0 END, sd.name
        """;
        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, directionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new SousDirection(rs.getInt("id"), rs.getString("name")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback for legacy data: keep previous behavior if mapping missing.
        if (list.isEmpty()) {
            String fallback = """
                SELECT sd.id, sd.name
                FROM directions d
                JOIN sous_directions sd ON sd.id = d.sous_direction_id
                WHERE d.id = ?
            """;
            try (Connection c = DB.getConnection();
                 PreparedStatement ps = c.prepareStatement(fallback)) {
                ps.setInt(1, directionId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    list.add(new SousDirection(rs.getInt("id"), rs.getString("name")));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return list;
    }

    /**
     * Merges sous-directions for every {@link #getDirectionIdCluster} id (duplicate “same” direction rows in DB).
     */
    public static ObservableList<SousDirection> getSousDirectionsForDirectionCluster(int anyDirectionInCluster) {
        java.util.LinkedHashSet<Integer> seen = new java.util.LinkedHashSet<>();
        ObservableList<SousDirection> out = FXCollections.observableArrayList();
        if (anyDirectionInCluster <= 0) {
            return out;
        }
        for (int did : getDirectionIdCluster(anyDirectionInCluster)) {
            for (SousDirection sd : getSousDirectionsForDirection(did)) {
                if (seen.add(sd.getId())) {
                    out.add(sd);
                }
            }
        }
        return out;
    }

    /** True if the sous-direction is linked to this courrier's direction, including cluster duplicates. */
    public static boolean isSousDirectionUnderPacketDirection(int sousDirectionId, int targetDirectionId) {
        for (SousDirection sd : getSousDirectionsForDirectionCluster(targetDirectionId)) {
            if (sd.getId() == sousDirectionId) {
                return true;
            }
        }
        return false;
    }

    private static final class DirNameRow {
        final int id;
        final String name;
        DirNameRow(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * Strips trailing parentheticals (e.g. " (SYSCO)") from {@code directions.name} and merges duplicate
     * direction rows that refer to the same service, so a single id is used everywhere.
     */
    public static void applyStripParentheticalsAndMergeDuplicates(Connection c) {
        if (c == null) {
            return;
        }
        try {
            c.setAutoCommit(false);
            List<DirNameRow> rows = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, name FROM directions ORDER BY id")) {
                while (rs.next()) {
                    rows.add(new DirNameRow(rs.getInt(1), rs.getString(2)));
                }
            }
            java.util.LinkedHashMap<String, List<DirNameRow>> byBase = new java.util.LinkedHashMap<>();
            for (DirNameRow r : rows) {
                String key = directionClusterKey(r.name);
                if (key.isEmpty()) {
                    String raw = r.name == null ? "" : r.name;
                    key = raw.trim().toLowerCase(Locale.ROOT);
                }
                byBase.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
            for (List<DirNameRow> group : byBase.values()) {
                group.sort(Comparator.comparingInt(x -> x.id));
                DirNameRow surv = group.get(0);
                for (int i = 1; i < group.size(); i++) {
                    mergeDirectionIdInto(c, group.get(i).id, surv.id);
                }
                String clusterKey = directionClusterKey(surv.name);
                String canonical;
                if (SYSTEMS_TI_CLUSTER_KEY.equals(clusterKey)) {
                    canonical = CANONICAL_DIRECTION_SYSTEMES_TI;
                } else {
                    canonical = baseDirectionLabel(surv.name == null ? "" : surv.name);
                    if (canonical.isEmpty() && surv.name != null) {
                        canonical = surv.name.trim();
                    }
                }
                if (!canonical.isEmpty() && (surv.name == null || !canonical.equals(surv.name))) {
                    try (PreparedStatement u = c.prepareStatement("UPDATE directions SET name = ? WHERE id = ?")) {
                        u.setString(1, canonical);
                        u.setInt(2, surv.id);
                        u.executeUpdate();
                    }
                }
            }
            c.commit();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                c.rollback();
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
        } finally {
            try {
                c.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private static void mergeDirectionIdInto(Connection c, int fromId, int toId) throws SQLException {
        if (fromId <= 0 || toId <= 0 || fromId == toId) {
            return;
        }
        execUpdate(c, "UPDATE users SET direction_id = ? WHERE direction_id = ?", toId, fromId);
        execUpdate(c, "UPDATE courier_packets SET target_direction_id = ? WHERE target_direction_id = ?", toId, fromId);
        execUpdate(c, "UPDATE courier_journey_events SET direction_id = ? WHERE direction_id = ?", toId, fromId);
        if (tableHasColumn(c, "ticket_external_escalations", "from_direction_id")) {
            execUpdate(c, "UPDATE ticket_external_escalations SET from_direction_id = ? WHERE from_direction_id = ?",
                    toId, fromId);
        }
        if (tableHasColumn(c, "ticket_external_escalations", "to_direction_id")) {
            execUpdate(c, "UPDATE ticket_external_escalations SET to_direction_id = ? WHERE to_direction_id = ?",
                    toId, fromId);
        }
        try (PreparedStatement d = c.prepareStatement(
                "DELETE FROM direction_sous_direction_map WHERE direction_id = ?"
                        + " AND EXISTS (SELECT 1 FROM direction_sous_direction_map t"
                        + " WHERE t.direction_id = ? AND t.sous_direction_id = direction_sous_direction_map.sous_direction_id)")) {
            d.setInt(1, fromId);
            d.setInt(2, toId);
            d.executeUpdate();
        }
        execUpdate(c, "UPDATE direction_sous_direction_map SET direction_id = ? WHERE direction_id = ?", toId, fromId);
        try (PreparedStatement del = c.prepareStatement("DELETE FROM directions WHERE id = ?")) {
            del.setInt(1, fromId);
            del.executeUpdate();
        }
    }

    private static void execUpdate(Connection c, String sql, int a, int b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            ps.executeUpdate();
        }
    }

    private static boolean tableHasColumn(Connection c, String table, String col) {
        try {
            ResultSet rs = c.getMetaData().getColumns(null, null, table, col);
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }
}