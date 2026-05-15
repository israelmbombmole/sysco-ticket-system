package com.sysco.web.security;

import java.text.Normalizer;
import java.util.Locale;

/** Mirrors {@code com.app.util.RoleKeyUtil} from the JavaFX SYSCO client for RBAC checks on the server. */
public final class RoleKeys {

    private RoleKeys() {}

    /**
     * Normalizes DB role labels (accents, spacing) to canonical routing keys such as {@code SOUS-DIRECTEUR},
     * {@code VERIFICATEUR-ASSISTANT}, {@code COURIER}.
     */
    public static String normalizeForScope(String viewRole) {
        if (viewRole == null || viewRole.isBlank()) {
            return "";
        }
        String s = Normalizer.normalize(viewRole.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        s = s.toUpperCase(Locale.ROOT).replace('\u00A0', ' ').replace("\u00AD", "").trim();
        s = s.replace('’', '\'').replace('`', '\'');
        if (s.contains("VERIFICATEUR") && s.contains("ASSISTANT")) {
            return "VERIFICATEUR-ASSISTANT";
        }
        if (s.contains("SOUS") && s.contains("DIRECTEUR")) {
            return "SOUS-DIRECTEUR";
        }
        if ("COURRIER".equals(s)) {
            return "COURIER";
        }
        if ("DIRECTEUR".equals(s) || s.startsWith("DIRECTEUR ")) {
            return "DIRECTEUR";
        }
        if ("DIRECTRICE".equals(s) || s.startsWith("DIRECTRICE ")) {
            return "DIRECTEUR";
        }
        if ("SECRETAIRE".equals(s) || s.startsWith("SECRETAIRE ")) {
            return "SECRETAIRE";
        }
        return s;
    }

    /** Same as desktop {@code RoleKeyUtil#listRoleKey} — used for courrier DAO-style lists. */
    public static String listRoleKey(String viewRole) {
        if (viewRole == null || viewRole.isBlank()) {
            return "";
        }
        String n = normalizeForScope(viewRole);
        if (n.isEmpty()) {
            return "";
        }
        if ("ADMIN".equalsIgnoreCase(n)) {
            return "ADMIN";
        }
        if ("SUPER_ADMIN".equalsIgnoreCase(n)) {
            return "SUPER_ADMIN";
        }
        return n;
    }
}
