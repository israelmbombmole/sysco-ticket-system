package com.sysco.web.service;

import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.CourierPacket;
import com.sysco.web.domain.Department;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.TicketAssignment;
import com.sysco.web.domain.TicketEvent;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.CourierPacketRepository;
import com.sysco.web.repo.DepartmentRepository;
import com.sysco.web.repo.NotificationRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.TicketCommentRepository;
import com.sysco.web.repo.TicketEventRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.util.DisplayDateFormatter;
import com.sysco.web.util.PriorityFrenchLabels;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Period operational report PDF aligned with the JavaFX SYSCO technical report: summary tables, agent workload,
 * ticket and task listings, ticket events, courriers, and assignment lines.
 */
@Service
@RequiredArgsConstructor
public class OperationalReportPdfService {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private static final int LIM_TICKETS = 400;
    private static final int LIM_TASKS = 400;
    private static final int LIM_EVENTS = 300;
    private static final int LIM_ASSIGN = 400;
    private static final int LIM_COURIERS = 400;

    private final TicketRepository tickets;
    private final UserAccountRepository users;
    private final DepartmentRepository departments;
    private final AutomatedJobRepository jobs;
    private final TicketAssignmentRepository ticketAssignments;
    private final TicketEventRepository ticketEvents;
    private final CourierPacketRepository courierPackets;
    private final NotificationRepository notifications;
    private final TicketCommentRepository ticketComments;
    private final LoginAuditService loginAuditService;

    public record OperationalReportResult(
            byte[] pdf, int periodTotal, int periodOpen, int periodInProgress, int periodClosed) {}

    @Transactional(readOnly = true)
    public OperationalReportResult build(LocalDate from, LocalDate to) throws IOException {
        ZoneId z = ZoneId.systemDefault();
        Instant rangeStart = from.atStartOfDay(z).toInstant();
        Instant rangeEndExcl = to.plusDays(1).atStartOfDay(z).toInstant();

        Map<Long, String> userNames =
                users.findAll().stream()
                        .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername, (a, b) -> a));
        Map<Long, String> deptNames =
                departments.findAll().stream()
                        .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        Instant reportNow = Instant.now();
        List<Ticket> periodCreated =
                tickets.findAll().stream()
                        .filter(t -> t.getMergedIntoTicketId() == null)
                        .filter(t -> !PlannerTicketVisibility.isHiddenFromOperationalViews(t, reportNow))
                        .filter(t -> t.getCreatedAt() != null
                                && !t.getCreatedAt().isBefore(rangeStart)
                                && t.getCreatedAt().isBefore(rangeEndExcl))
                        .sorted(Comparator.comparing(Ticket::getCreatedAt).reversed())
                        .toList();

        int pTotal = periodCreated.size();
        int pOpen =
                (int) periodCreated.stream()
                        .filter(t -> "OPEN".equalsIgnoreCase(safe(t.getStatus())))
                        .count();
        int pInProg =
                (int) periodCreated.stream()
                        .filter(t -> {
                            String s = safe(t.getStatus()).toUpperCase(Locale.ROOT);
                            return "IN_PROGRESS".equals(s) || "ASSIGNED".equals(s);
                        })
                        .count();
        int pClosed =
                (int) periodCreated.stream()
                        .filter(t -> {
                            String s = safe(t.getStatus()).toUpperCase(Locale.ROOT);
                            return "CLOSED".equals(s) || "RESOLVED".equals(s);
                        })
                        .count();

        long activeUsers = users.findAll().stream().filter(UserAccount::isActiveBool).count();

        long closedInPeriod =
                tickets.findAll().stream()
                        .filter(t -> t.getMergedIntoTicketId() == null)
                        .filter(t -> t.getClosedAt() != null
                                && !t.getClosedAt().isBefore(rangeStart)
                                && t.getClosedAt().isBefore(rangeEndExcl))
                        .count();

        List<AutomatedJob> jobsCreated =
                jobs.findCreatedBetween(rangeStart, rangeEndExcl, PageRequest.of(0, 8000));
        List<AutomatedJob> jobsClosed =
                jobs.findClosedBetween(rangeStart, rangeEndExcl, PageRequest.of(0, 8000));
        long tasksCompletedInPeriod =
                jobsClosed.stream()
                        .filter(j -> "CLOSED".equalsIgnoreCase(safe(j.getStatus()))
                                || "RESOLVED".equalsIgnoreCase(safe(j.getStatus()))
                                || j.getClosedAt() != null)
                        .count();

        Map<String, Long> taskStatusCreated =
                jobsCreated.stream()
                        .collect(Collectors.groupingBy(j -> safe(j.getStatus()).toUpperCase(Locale.ROOT), Collectors.counting()));

        long commentsCount = ticketComments.countCreatedBetween(rangeStart, rangeEndExcl);
        long commentsWithAtt = ticketComments.countWithAttachmentBetween(rangeStart, rangeEndExcl);
        long notifCount = notifications.countCreatedBetween(rangeStart, rangeEndExcl);
        long loginAuditCount = loginAuditService.countConnectionLikeEventsBetween(rangeStart, rangeEndExcl);

        List<TicketAssignment> assignsInPeriod =
                ticketAssignments.findAssignedBetween(rangeStart, rangeEndExcl, PageRequest.of(0, LIM_ASSIGN));
        List<TicketEvent> eventsInPeriod =
                ticketEvents.findCreatedBetween(rangeStart, rangeEndExcl, PageRequest.of(0, LIM_EVENTS));
        List<CourierPacket> couriersTouched =
                courierPackets.findTouchedBetween(rangeStart, rangeEndExcl, PageRequest.of(0, LIM_COURIERS));

        Map<Long, AgentAgg> agentMap = buildAgentStats(rangeStart, rangeEndExcl, assignsInPeriod, jobsCreated, jobsClosed, eventsInPeriod);

        Map<Long, String> ticketRefById =
                tickets.findAll().stream()
                        .collect(Collectors.toMap(Ticket::getId, OperationalReportPdfService::ticketRef, (a, b) -> a));

        byte[] pdf =
                renderPdf(
                        from,
                        to,
                        activeUsers,
                        pTotal,
                        pOpen,
                        pInProg,
                        pClosed,
                        closedInPeriod,
                        jobsCreated.size(),
                        tasksCompletedInPeriod,
                        taskStatusCreated,
                        commentsCount,
                        commentsWithAtt,
                        notifCount,
                        loginAuditCount,
                        agentMap,
                        periodCreated,
                        jobsCreated,
                        eventsInPeriod,
                        assignsInPeriod,
                        couriersTouched,
                        userNames,
                        deptNames,
                        ticketRefById);

        return new OperationalReportResult(pdf, pTotal, pOpen, pInProg, pClosed);
    }

    private Map<Long, AgentAgg> buildAgentStats(
            Instant rangeStart,
            Instant rangeEndExcl,
            List<TicketAssignment> assignsInPeriod,
            List<AutomatedJob> jobsCreated,
            List<AutomatedJob> jobsClosed,
            List<TicketEvent> eventsInPeriod) {
        Map<Long, AgentAgg> map = new LinkedHashMap<>();
        for (UserAccount u : users.findAll()) {
            if (!u.isActiveBool()) {
                continue;
            }
            map.put(u.getId(), new AgentAgg(u.getUsername(), safe(u.getRole())));
        }
        for (TicketAssignment a : assignsInPeriod) {
            AgentAgg g = map.get(a.getUserId());
            if (g != null) {
                g.assignments++;
            }
        }
        for (AutomatedJob j : jobsCreated) {
            if (j.getAssigneeUserId() != null) {
                AgentAgg g = map.get(j.getAssigneeUserId());
                if (g != null) {
                    g.tasksCreated++;
                }
            }
        }
        for (AutomatedJob j : jobsClosed) {
            if (j.getAssigneeUserId() == null) {
                continue;
            }
            boolean done = j.getClosedAt() != null
                    || "CLOSED".equalsIgnoreCase(safe(j.getStatus()))
                    || "RESOLVED".equalsIgnoreCase(safe(j.getStatus()));
            if (done) {
                AgentAgg g = map.get(j.getAssigneeUserId());
                if (g != null) {
                    g.tasksClosedInPeriod++;
                }
            }
        }
        for (TicketEvent e : eventsInPeriod) {
            if (e.getActorUserId() != null) {
                AgentAgg g = map.get(e.getActorUserId());
                if (g != null) {
                    g.ticketEvents++;
                }
            }
        }
        return map;
    }

    private byte[] renderPdf(
            LocalDate from,
            LocalDate to,
            long activeUsers,
            int pTotal,
            int pOpen,
            int pInProg,
            int pClosed,
            long closedInPeriod,
            int tasksCreated,
            long tasksCompletedInPeriod,
            Map<String, Long> taskStatusCreated,
            long commentsCount,
            long commentsWithAtt,
            long notifCount,
            long loginAuditCount,
            Map<Long, AgentAgg> agentMap,
            List<Ticket> periodCreated,
            List<AutomatedJob> jobsCreated,
            List<TicketEvent> eventsInPeriod,
            List<TicketAssignment> assignsInPeriod,
            List<CourierPacket> couriersTouched,
            Map<Long, String> userNames,
            Map<Long, String> deptNames,
            Map<Long, String> ticketRefById)
            throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfCanvas cv = new PdfCanvas(doc);
            cv.cover(from, to, activeUsers);

            cv.section("2. Synthese des tickets crees sur la periode");
            String closureRate =
                    pTotal <= 0 ? "0 %" : String.format(Locale.FRENCH, "%.1f %%", (pClosed * 100.0 / pTotal));
            float[] w5 = {78, 78, 78, 110, 95};
            cv.tableHeaderRow(new String[] {"Total crees", "Ouverts", "En cours", "Clotures / resolus", "Taux cloture"}, w5, true);
            cv.tableDataRow(
                    new String[] {
                        String.valueOf(pTotal),
                        String.valueOf(pOpen),
                        String.valueOf(pInProg),
                        String.valueOf(pClosed),
                        closureRate
                    },
                    w5);
            cv.paragraph(
                    "Tickets effectivement clotures durant la periode (date de cloture dans l'intervalle) : "
                            + closedInPeriod,
                    92);

            cv.section("3. Taches rattachees aux tickets (planificateur / Mon travail)");
            cv.paragraph("Taches creees sur la periode : " + tasksCreated, 92);
            cv.paragraph("Taches terminees / cloture enregistree sur la periode : " + tasksCompletedInPeriod, 92);
            if (taskStatusCreated.isEmpty()) {
                cv.paragraph("(Aucune tache creee sur cette periode.)", 92);
            } else {
                float[] w2 = {220, 80};
                cv.tableHeaderRow(new String[] {"Statut (taches creees)", "Volume"}, w2, true);
                List<Map.Entry<String, Long>> stRows =
                        taskStatusCreated.entrySet().stream()
                                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                                .toList();
                for (Map.Entry<String, Long> e : stRows) {
                    cv.tableDataRow(new String[] {e.getKey(), String.valueOf(e.getValue())}, w2);
                }
            }

            cv.section("4. Donnees transverses");
            cv.paragraph("Commentaires tickets sur la periode : " + commentsCount, 92);
            cv.paragraph("Commentaires avec piece jointe : " + commentsWithAtt, 92);
            cv.paragraph("Evenements de connexion (audit interne SYSCO Web) : " + loginAuditCount, 92);
            cv.paragraph("Notifications generees sur la periode : " + notifCount, 92);

            cv.section("5. Charge et production par agent (periode)");
            List<AgentAgg> agents =
                    agentMap.values().stream()
                            .filter(AgentAgg::hasActivity)
                            .sorted(Comparator.comparing(a -> a.username.toLowerCase(Locale.ROOT)))
                            .toList();
            float[] wA = {78, 74, 54, 54, 54, 54, 54};
            cv.tableHeaderRow(
                    new String[] {
                        "Agent",
                        "Role",
                        "Assign.",
                        "Taches cr.",
                        "Taches cl.",
                        "Even.",
                        "Total activite"
                    },
                    wA,
                    true);
            if (agents.isEmpty()) {
                cv.paragraph("(Aucune activite agregee sur cette periode.)", 92);
            } else {
                for (AgentAgg a : agents) {
                    int tot = a.assignments + a.tasksCreated + a.tasksClosedInPeriod + a.ticketEvents;
                    cv.tableDataRow(
                            new String[] {
                                a.username,
                                a.role,
                                String.valueOf(a.assignments),
                                String.valueOf(a.tasksCreated),
                                String.valueOf(a.tasksClosedInPeriod),
                                String.valueOf(a.ticketEvents),
                                String.valueOf(tot)
                            },
                            wA);
                }
            }

            cv.section("6. Detail des tickets crees sur la periode (max " + LIM_TICKETS + " lignes)");
            float[] wT = {68, 168, 58, 52, 72, 92};
            cv.tableHeaderRow(new String[] {"Ref.", "Titre", "Statut", "Priorite", "Departement", "Cree le"}, wT, true);
            int tc = 0;
            for (Ticket t : periodCreated) {
                if (tc++ >= LIM_TICKETS) {
                    break;
                }
                cv.tableDataRow(
                        new String[] {
                            ticketRef(t),
                            safe(t.getTitle()),
                            safe(t.getStatus()),
                            PriorityFrenchLabels.french(t.getPriority()),
                            deptNames.getOrDefault(t.getDepartmentId(), "-"),
                            t.getCreatedAt() == null ? "-" : TS.format(LocalDateTime.ofInstant(t.getCreatedAt(), ZoneId.systemDefault()))
                        },
                        wT);
            }
            if (periodCreated.isEmpty()) {
                cv.paragraph("(Aucun ticket cree sur cette periode.)", 92);
            }

            cv.section("7. Detail des taches creees sur la periode (max " + LIM_TASKS + " lignes)");
            float[] wJ = {62, 148, 88, 72, 100};
            cv.tableHeaderRow(new String[] {"Ticket", "Tache", "Assigne", "Statut", "Creee le"}, wJ, true);
            int jc = 0;
            for (AutomatedJob j : jobsCreated.stream().sorted(Comparator.comparing(AutomatedJob::getCreatedAt).reversed()).toList()) {
                if (jc++ >= LIM_TASKS) {
                    break;
                }
                String tref =
                        j.getTicketId() == null
                                ? "-"
                                : ticketRefById.getOrDefault(j.getTicketId(), "#" + j.getTicketId());
                cv.tableDataRow(
                        new String[] {
                            tref,
                            safe(j.getJobTitle()),
                            userNames.getOrDefault(j.getAssigneeUserId(), "-"),
                            safe(j.getStatus()),
                            j.getCreatedAt() == null ? "-" : formatInstant(j.getCreatedAt())
                        },
                        wJ);
            }
            if (jobsCreated.isEmpty()) {
                cv.paragraph("(Aucune tache creee sur cette periode.)", 92);
            }

            cv.section("8. Historique des tickets (evenements sur la periode, max " + LIM_EVENTS + " lignes)");
            float[] wE = {56, 72, 168, 72, 92};
            cv.tableHeaderRow(new String[] {"Ticket", "Type", "Note", "Acteur", "Horodatage"}, wE, true);
            for (TicketEvent e : eventsInPeriod) {
                String note = safe(e.getNote());
                if (note.length() > 120) {
                    note = note.substring(0, 117) + "...";
                }
                cv.tableDataRow(
                        new String[] {
                            ticketRefById.getOrDefault(e.getTicketId(), "#" + e.getTicketId()),
                            safe(e.getEventType()),
                            note,
                            userNames.getOrDefault(e.getActorUserId(), "-"),
                            e.getCreatedAt() == null ? "-" : formatInstant(e.getCreatedAt())
                        },
                        wE);
            }
            if (eventsInPeriod.isEmpty()) {
                cv.paragraph("(Aucun evenement sur cette periode.)", 92);
            }

            cv.section("9. Courriers (cree ou resolu sur la periode, max " + LIM_COURIERS + " lignes)");
            float[] wC = {68, 140, 56, 62, 72, 72};
            cv.tableHeaderRow(new String[] {"Ref", "Titre", "Statut", "Ticket lie", "Cree le", "Resolu le"}, wC, true);
            for (CourierPacket c : couriersTouched) {
                String linked =
                        c.getLinkedTicketId() == null
                                ? "-"
                                : ticketRefById.getOrDefault(c.getLinkedTicketId(), "#" + c.getLinkedTicketId());
                cv.tableDataRow(
                        new String[] {
                            safe(c.getRefCode()),
                            safe(c.getTitle()),
                            safe(c.getStatus()),
                            linked,
                            c.getCreatedAt() == null ? "-" : formatInstant(c.getCreatedAt()),
                            c.getResolvedAt() == null ? "-" : formatInstant(c.getResolvedAt())
                        },
                        wC);
            }
            if (couriersTouched.isEmpty()) {
                cv.paragraph("(Aucun courrier touche sur cette periode.)", 92);
            }

            cv.section("10. Lignes d'assignation de tickets (periode, max " + LIM_ASSIGN + " lignes)");
            float[] wAs = {72, 58, 58, 130, 52, 92};
            cv.tableHeaderRow(new String[] {"Agent", "Ticket", "Titre", "Statut ticket", "Assigne le"}, wAs, true);
            for (TicketAssignment a : assignsInPeriod) {
                Ticket t = tickets.findById(a.getTicketId()).orElse(null);
                cv.tableDataRow(
                        new String[] {
                            userNames.getOrDefault(a.getUserId(), "?"),
                            t == null ? "#" + a.getTicketId() : ticketRef(t),
                            t == null ? "-" : clip(safe(t.getTitle()), 48),
                            t == null ? "-" : safe(t.getStatus()),
                            a.getAssignedAt() == null ? "-" : formatInstant(a.getAssignedAt())
                        },
                        wAs);
            }
            if (assignsInPeriod.isEmpty()) {
                cv.paragraph("(Aucune assignation sur cette periode.)", 92);
            }

            cv.footerNote();
            cv.closeLastPage();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String clip(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars - 1) + ".";
    }

    private static String formatInstant(Instant i) {
        return TS.format(LocalDateTime.ofInstant(i, ZoneId.systemDefault()));
    }

    private static String ticketRef(Ticket t) {
        if (t.getTicketNumber() != null && !t.getTicketNumber().isBlank()) {
            return t.getTicketNumber().trim();
        }
        return "TCK-" + t.getId();
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static String pdfText(String v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch >= 32 && ch <= 255) {
                sb.append(ch);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static List<String> wrapLines(String s, int maxChars) {
        String text = s == null ? "" : s.replace("\r", "");
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw;
            while (line.length() > maxChars) {
                out.add(line.substring(0, maxChars));
                line = line.substring(maxChars);
            }
            out.add(line);
        }
        if (out.isEmpty()) {
            out.add("-");
        }
        return out;
    }

    private static final class AgentAgg {
        final String username;
        final String role;
        int assignments;
        int tasksCreated;
        int tasksClosedInPeriod;
        int ticketEvents;

        AgentAgg(String username, String role) {
            this.username = username;
            this.role = role;
        }

        boolean hasActivity() {
            return assignments + tasksCreated + tasksClosedInPeriod + ticketEvents > 0;
        }
    }

    private final class PdfCanvas {
        private static final float MX = 40f;
        private static final float MY = 50f;
        private static final float FOOTER = 30f;
        private static final float ROW_H = 13f;
        private static final float PAGE_W = PDRectangle.A4.getWidth();
        private static final float PAGE_H = PDRectangle.A4.getHeight();

        private final PDDocument doc;
        private PDPage page;
        private PDPageContentStream cs;
        private float y;
        private int pageIndex;

        PdfCanvas(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage(true);
        }

        private void newPage(boolean first) throws IOException {
            if (cs != null) {
                endPageFooter();
                cs.close();
            }
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            pageIndex++;
            float ph = PAGE_H;
            float pw = PAGE_W;
            if (first) {
                cs.setNonStrokingColor(0.1f, 0.28f, 0.46f);
                cs.addRect(0, ph - 58, pw, 58);
                cs.fill();
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(MX, ph - 36);
                cs.showText(pdfText("RAPPORT TECHNIQUE D'EXPLOITATION — SYSCO"));
                cs.endText();
                cs.setNonStrokingColor(0.93f, 0.95f, 0.97f);
                cs.addRect(0, ph - 58 - 24, pw, 24);
                cs.fill();
                cs.setNonStrokingColor(0.2f, 0.24f, 0.3f);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9);
                cs.newLineAtOffset(MX, ph - 72);
                cs.showText(pdfText("Document operationnel et statistique — usage interne"));
                cs.endText();
                cs.setNonStrokingColor(0f, 0f, 0f);
                y = ph - 58 - 24 - 22;
            } else {
                cs.setNonStrokingColor(0.9f, 0.92f, 0.95f);
                cs.addRect(0, ph - 30, pw, 30);
                cs.fill();
                cs.setNonStrokingColor(0.15f, 0.2f, 0.32f);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.newLineAtOffset(MX, ph - 18);
                cs.showText(pdfText("SYSCO — Rapport d'exploitation (suite)"));
                cs.endText();
                cs.setNonStrokingColor(0f, 0f, 0f);
                y = ph - 30 - 16;
            }
        }

        private void endPageFooter() throws IOException {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 8);
            cs.setNonStrokingColor(0.42f, 0.45f, 0.5f);
            cs.newLineAtOffset(MX, MY - 14);
            cs.showText(pdfText("Page " + pageIndex + " | SYSCO — Confidentiel"));
            cs.endText();
            cs.setNonStrokingColor(0f, 0f, 0f);
        }

        private void closeLastPage() throws IOException {
            if (cs != null) {
                endPageFooter();
                cs.close();
                cs = null;
            }
        }

        private void ensure(float needBelowY) throws IOException {
            if (y - needBelowY < MY + FOOTER) {
                newPage(false);
            }
        }

        void cover(LocalDate from, LocalDate to, long activeUsers) throws IOException {
            ensure(80);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
            writeLine(MX, y, "1. Identification et periode couverte");
            y -= 18;
            cs.setFont(PDType1Font.HELVETICA, 10);
            paragraph(
                    "Periode analysee : du "
                            + from
                            + " au "
                            + to
                            + " (inclus). Cle systeme : "
                            + from
                            + "_au_"
                            + to,
                    88);
            paragraph("Date et heure de generation : " + DisplayDateFormatter.formatLocalDateTime(LocalDateTime.now()), 88);
            paragraph("Utilisateurs actifs enregistres (total) : " + activeUsers, 88);
            y -= 8;
        }

        void section(String title) throws IOException {
            ensure(30);
            y -= 6;
            cs.setStrokingColor(0.78f, 0.82f, 0.88f);
            cs.setLineWidth(0.6f);
            cs.moveTo(MX, y + 2);
            cs.lineTo(PAGE_W - MX, y + 2);
            cs.stroke();
            cs.setStrokingColor(0f, 0f, 0f);
            y -= 8;
            cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
            writeLine(MX, y, pdfText(title));
            y -= 16;
            cs.setFont(PDType1Font.HELVETICA, 9);
        }

        void paragraph(String text, int wrap) throws IOException {
            for (String line : wrapLines(pdfText(text), wrap)) {
                ensure(14);
                writeLine(MX, y, line);
                y -= 12;
            }
        }

        void tableHeaderRow(String[] cells, float[] colW, boolean bold) throws IOException {
            ensure(ROW_H + 6);
            float x = MX;
            cs.setFont(bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA, 8);
            for (int i = 0; i < cells.length && i < colW.length; i++) {
                String c = clipCell(cells[i], colW[i], 8);
                cs.beginText();
                cs.newLineAtOffset(x + 2, y - ROW_H + 4);
                cs.showText(c);
                cs.endText();
                x += colW[i];
            }
            strokeRowBottom();
            y -= ROW_H;
        }

        void tableDataRow(String[] cells, float[] colW) throws IOException {
            ensure(ROW_H + 4);
            float x = MX;
            cs.setFont(PDType1Font.HELVETICA, 8);
            for (int i = 0; i < cells.length && i < colW.length; i++) {
                String c = clipCell(cells[i], colW[i], 8);
                cs.beginText();
                cs.newLineAtOffset(x + 2, y - ROW_H + 4);
                cs.showText(c);
                cs.endText();
                x += colW[i];
            }
            strokeRowBottom();
            y -= ROW_H;
        }

        private void strokeRowBottom() throws IOException {
            cs.setStrokingColor(0.88f, 0.9f, 0.93f);
            cs.setLineWidth(0.4f);
            cs.moveTo(MX, y - ROW_H);
            cs.lineTo(PAGE_W - MX, y - ROW_H);
            cs.stroke();
            cs.setStrokingColor(0f, 0f, 0f);
        }

        private String clipCell(String raw, float widthPt, float fontSize) {
            String cell = pdfText(raw == null ? "" : raw);
            int maxC = Math.max(4, (int) (widthPt / (fontSize * 0.52f)));
            if (cell.length() > maxC) {
                return cell.substring(0, maxC - 1) + ".";
            }
            return cell;
        }

        void footerNote() throws IOException {
            ensure(36);
            y -= 8;
            cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 8);
            paragraph(
                    "SYSCO — Rapport genere automatiquement. Donnees extraites de la base applicative Web. Confidentiel.",
                    90);
        }

        private void writeLine(float x, float yy, String text) throws IOException {
            cs.beginText();
            cs.newLineAtOffset(x, yy);
            cs.showText(text);
            cs.endText();
        }
    }
}
