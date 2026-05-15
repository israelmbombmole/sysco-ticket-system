package com.sysco.web.service.courier;

/**
 * Labels shown in courier tables when {@link com.sysco.web.domain.Direction#getName()} or sous-direction rows
 * store a combined "direction — sous" title or legacy inverted values.
 */
public final class CourierDisplayLabels {

    private CourierDisplayLabels() {}

    /** Primary direction title only; strips the first {@code " — "} suffix when present (preserves casing). */
    public static String directionColumnLabel(String rawDirectionName) {
        if (rawDirectionName == null || rawDirectionName.isBlank()) {
            return "";
        }
        String s = rawDirectionName.trim();
        int idx = s.indexOf(" — ");
        return idx > 0 ? s.substring(0, idx).trim() : s;
    }

    /** Portion after the first {@code " — "} when the direction row holds a combined label. */
    public static String sousSuffixFromDirectionLabel(String rawDirectionName) {
        if (rawDirectionName == null || rawDirectionName.isBlank()) {
            return "";
        }
        String s = rawDirectionName.trim();
        int idx = s.indexOf(" — ");
        if (idx < 0) {
            return "";
        }
        return s.substring(idx + 3).trim();
    }

    /**
     * Sous-direction column: empty until a sous-directeur is assigned. When populated, prefers the sous-direction
     * record name unless it duplicates the direction/base title (legacy inverted data), in which case the suffix from
     * the combined direction label is used.
     */
    public static String sousColumnLabel(boolean sousDirecteurAssigned, String rawDirectionName, String rawSousName) {
        if (!sousDirecteurAssigned) {
            return "";
        }
        String dirFull = rawDirectionName == null ? "" : rawDirectionName.trim();
        String base = directionColumnLabel(dirFull);
        String suffix = sousSuffixFromDirectionLabel(dirFull);
        String sous = rawSousName == null ? "" : rawSousName.trim();

        if (sous.isEmpty()) {
            return suffix;
        }
        if (sous.equals(dirFull) || sous.equals(base)) {
            return suffix.isEmpty() ? sous : suffix;
        }
        return sous;
    }
}
