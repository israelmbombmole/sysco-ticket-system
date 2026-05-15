package com.app.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalises role labels from the DB (accents, spacing, hyphens) so they match
 * the canonical keys used in courrier/ticket scoping.
 */
public final class RoleKeyUtil {

    private RoleKeyUtil() {
    }

    public static String normalizeForScope(String viewRole) {
        if (viewRole == null || viewRole.isBlank()) {
            return "";
        }
        String s = Normalizer.normalize(viewRole.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        s = s.toUpperCase(Locale.ROOT).replace('\u00A0', ' ').replace("\u00AD", "").trim();
        s = s.replace('’', '\'').replace('`', '\'');
        if (s.contains("VERIFICATEUR") && s.contains("ASSISTANT")) {
            return "VERIFICATEUR-ASSISTANT";
        }
        if (s.contains("SOUS") && s.contains("DIRECTEUR")) {
            return "SOUS-DIRECTEUR";
        }
        // French role label is spelled with double "r" — align with our canonical COURIER key
        if ("COURRIER".equals(s)) {
            return "COURIER";
        }
        // DB labels are sometimes expanded ("Directrice", "Directeur de …"); courrier/ticket scope keys stay canonical.
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

    /** For courrier list: same as session role, but keep ADMIN for super-user logic. */
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
        return n;
    }
}
