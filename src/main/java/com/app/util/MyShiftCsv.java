package com.app.util;

import com.app.model.MyShiftRow;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

public final class MyShiftCsv {

    private MyShiftCsv() {}

    public static void writeUtf8Bom(List<MyShiftRow> rows, File f) throws Exception {
        char sep = ',';
        try (FileOutputStream os = new FileOutputStream(f);
             Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             BufferedWriter bw = new BufferedWriter(w)) {
            bw.write('\uFEFF');
            String hNames = I18n.t("myshiftColNamesGivenNames", "Names & given names");
            String hMat = I18n.t("matricule", "ID");
            String hFonc = I18n.t("myshiftColFonctions", "Functions");
            String hIn = I18n.t("myshiftColIn", "Arrival");
            String hSig = I18n.t("myshiftColSignature", "Signature");
            String hOut = I18n.t("myshiftColOut", "Departure");
            String hLoc = I18n.t("myshiftColLocationShort", "Place");
            bw.write(join(sep, hNames, hMat, hFonc, hIn, hSig, hOut, hLoc));
            bw.newLine();
            for (MyShiftRow r : rows) {
                bw.write(join(
                        sep,
                        esc(r.getNamesAndPostnoms(), sep),
                        esc(nvl(r.getMatricule()), sep),
                        esc(nvl(r.getFonction()), sep),
                        esc(r.getSignInTime(), sep),
                        esc(MyShiftAttendanceLabels.signature(r), sep),
                        esc(nvl(r.getSignOutTime()), sep),
                        esc(nvl(r.getLocation()), sep)));
                bw.newLine();
            }
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String v, char sep) {
        if (v == null) {
            return "";
        }
        String s = v.replace("\"", "\"\"");
        if (s.indexOf(sep) >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s + '"';
        }
        return s;
    }

    private static String join(char sep, String... a) {
        StringJoiner j = new StringJoiner(String.valueOf(sep));
        for (String x : a) {
            j.add(x);
        }
        return j.toString();
    }
}
