package com.app.util;

import com.app.model.MyShiftRow;

/** Shared labels for MyShift tables (per-user registry signature code). */
public final class MyShiftAttendanceLabels {

    private MyShiftAttendanceLabels() {}

    /** Registry "Signature": user's assigned {@code attendance_signature}; dash if unset. */
    public static String signature(MyShiftRow r) {
        if (r == null) {
            return I18n.t("myshiftSignatureDash", "—");
        }
        String s = r.getAttendanceSignature();
        if (s != null && !s.isBlank()) {
            return s.trim();
        }
        return I18n.t("myshiftSignatureDash", "—");
    }
}
