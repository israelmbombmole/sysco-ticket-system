package com.sysco.web.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * DGDA-style mission order (.docx) — institutional layout (letterhead block, Messieurs / Mesdames, signature).
 */
public final class MissionOrderDocxGenerator {

    private static final String FONT = "Times New Roman";

    public record ParticipantLine(String displayName, String matricule, String grade, String fonction) {}

    public record DgdaOrderPayload(
            String orderTitleLine,
            String headerRightDateLine,
            List<ParticipantLine> messieurs,
            List<ParticipantLine> mesdames,
            String designationParagraph,
            String departText,
            String retourText,
            String objetParagraph,
            String transportParagraph,
            String indemnitesLine,
            String observationsParagraph,
            String faitLine,
            String signatoryClosingLine,
            String signatoryNameLine,
            String footerSlogan) {}

    private MissionOrderDocxGenerator() {}

    public static byte[] build(DgdaOrderPayload p) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            headerTable(doc, p.headerRightDateLine());
            blankParagraph(doc);
            titleParagraph(doc, p.orderTitleLine());
            blankParagraph(doc);

            sectionLabel(doc, "Messieurs :");
            for (ParticipantLine line : p.messieurs()) {
                bulletParticipant(doc, line);
            }
            if (p.messieurs().isEmpty()) {
                mutedLine(doc, "—");
            }

            blankParagraph(doc);
            sectionLabel(doc, "Mesdames :");
            for (ParticipantLine line : p.mesdames()) {
                bulletParticipant(doc, line);
            }
            if (p.mesdames().isEmpty()) {
                mutedLine(doc, "—");
            }

            blankParagraph(doc);
            justifiedParagraph(doc, p.designationParagraph());

            blankParagraph(doc);
            departRetourLine(doc, "Départ : ", p.departText(), "Retour : ", p.retourText());

            blankParagraph(doc);
            labeledBlock(doc, "Objet de la mission : ", p.objetParagraph());

            blankParagraph(doc);
            labeledBlock(doc, "Mode de transport et itinéraire : ", p.transportParagraph());

            blankParagraph(doc);
            labeledBlock(doc, "Indemnités accordées : ", p.indemnitesLine());

            blankParagraph(doc);
            labeledBlock(doc, "Observations : ", p.observationsParagraph());

            blankParagraph(doc);
            blankParagraph(doc);
            signatureBlock(doc, p.faitLine(), p.signatoryClosingLine(), p.signatoryNameLine());

            if (p.footerSlogan() != null && !p.footerSlogan.isBlank()) {
                blankParagraph(doc);
                blankParagraph(doc);
                sloganFooter(doc, p.footerSlogan());
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void headerTable(XWPFDocument doc, String rightDateLine) {
        XWPFTable table = doc.createTable(1, 2);
        table.setWidth("100%");
        XWPFTableRow row = table.getRow(0);
        XWPFTableCell left = row.getCell(0);
        XWPFTableCell right = row.getCell(1);
        clearCell(left);
        clearCell(right);

        addCellLines(
                left,
                List.of(
                        "République Démocratique du Congo",
                        "Ministère des Finances",
                        "Direction Générale des Douanes et Accises",
                        "DGDA"));

        XWPFParagraph pr = right.addParagraph();
        pr.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun rr = pr.createRun();
        rr.setFontFamily(FONT);
        rr.setFontSize(11);
        rr.setText(rightDateLine == null ? "" : rightDateLine);

        XWPFParagraph pr2 = right.addParagraph();
        pr2.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r2 = pr2.createRun();
        r2.setFontFamily(FONT);
        r2.setFontSize(11);
        r2.setItalic(true);
        r2.setText("Le Directeur Général");
    }

    private static void clearCell(XWPFTableCell cell) {
        while (cell.getParagraphs().size() > 0) {
            cell.removeParagraph(0);
        }
    }

    private static void addCellLines(XWPFTableCell cell, List<String> lines) {
        for (String line : lines) {
            XWPFParagraph p = cell.addParagraph();
            XWPFRun r = p.createRun();
            r.setFontFamily(FONT);
            r.setFontSize(11);
            r.setText(line);
        }
    }

    private static void blankParagraph(XWPFDocument doc) {
        doc.createParagraph();
    }

    private static void titleParagraph(XWPFDocument doc, String title) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(12);
        r.setBold(true);
        r.setUnderline(UnderlinePatterns.SINGLE);
        r.setText(title == null ? "" : title.toUpperCase(Locale.FRENCH));
    }

    private static void sectionLabel(XWPFDocument doc, String label) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(11);
        r.setBold(true);
        r.setText(label);
    }

    private static void bulletParticipant(XWPFDocument doc, ParticipantLine line) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationFirstLine(360);
        String name = line.displayName() == null ? "" : line.displayName();
        String mat = line.matricule() == null || line.matricule().isBlank() ? "—" : line.matricule();
        String grade = line.grade() == null || line.grade().isBlank() ? "—" : line.grade();
        String fonc = line.fonction() == null || line.fonction().isBlank() ? "—" : line.fonction();
        XWPFRun nameRun = p.createRun();
        nameRun.setFontFamily(FONT);
        nameRun.setFontSize(11);
        nameRun.setBold(true);
        nameRun.setText(name);
        XWPFRun rest = p.createRun();
        rest.setFontFamily(FONT);
        rest.setFontSize(11);
        rest.setBold(false);
        rest.setText(
                ", matricule : "
                        + mat
                        + ", grade : "
                        + grade
                        + ", fonction : "
                        + fonc
                        + " ;");
    }

    private static void mutedLine(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(11);
        r.setItalic(true);
        r.setColor("666666");
        r.setText(text);
    }

    private static void justifiedParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(11);
        r.setText(text == null ? "" : text);
    }

    private static void departRetourLine(XWPFDocument doc, String dLabel, String dVal, String rLabel, String rVal) {
        XWPFTable t = doc.createTable(1, 2);
        t.setWidth("100%");
        XWPFTableRow row = t.getRow(0);
        clearCell(row.getCell(0));
        clearCell(row.getCell(1));
        XWPFParagraph pl = row.getCell(0).addParagraph();
        XWPFRun rl = pl.createRun();
        rl.setFontFamily(FONT);
        rl.setFontSize(11);
        rl.setBold(true);
        rl.setText(dLabel);
        rl.setBold(false);
        rl.setText(dVal == null ? "" : dVal);

        XWPFParagraph pr = row.getCell(1).addParagraph();
        pr.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun rr = pr.createRun();
        rr.setFontFamily(FONT);
        rr.setFontSize(11);
        rr.setBold(true);
        rr.setText(rLabel);
        rr.setBold(false);
        rr.setText(rVal == null ? "" : rVal);
    }

    private static void labeledBlock(XWPFDocument doc, String label, String body) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun lb = p.createRun();
        lb.setFontFamily(FONT);
        lb.setFontSize(11);
        lb.setBold(true);
        lb.setText(label);
        XWPFRun tx = p.createRun();
        tx.setFontFamily(FONT);
        tx.setFontSize(11);
        tx.setBold(false);
        tx.setText(body == null ? "" : body);
    }

    private static void signatureBlock(XWPFDocument doc, String fait, String closing, String name) {
        XWPFParagraph pr = doc.createParagraph();
        pr.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r = pr.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(11);
        r.setText(fait == null ? "" : fait);

        XWPFParagraph p2 = doc.createParagraph();
        p2.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r2 = p2.createRun();
        r2.setFontFamily(FONT);
        r2.setFontSize(11);
        r2.setText(closing == null ? "" : closing);

        doc.createParagraph();

        XWPFParagraph p3 = doc.createParagraph();
        p3.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r3 = p3.createRun();
        r3.setFontFamily(FONT);
        r3.setFontSize(11);
        r3.setItalic(true);
        r3.setText("Signature");

        XWPFParagraph p4 = doc.createParagraph();
        p4.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r4 = p4.createRun();
        r4.setFontFamily(FONT);
        r4.setFontSize(11);
        r4.setBold(true);
        r4.setText(name == null ? "" : name);
    }

    private static void sloganFooter(XWPFDocument doc, String slogan) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setFontFamily(FONT);
        r.setFontSize(10);
        r.setItalic(true);
        r.setText(slogan);
    }
}
