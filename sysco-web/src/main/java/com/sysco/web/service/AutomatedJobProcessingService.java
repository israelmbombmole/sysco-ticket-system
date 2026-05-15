package com.sysco.web.service;

import com.sysco.web.domain.AutomatedJob;
import com.sysco.web.domain.Ticket;
import com.sysco.web.repo.AutomatedJobRepository;
import com.sysco.web.repo.TicketRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes persisted {@link AutomatedJob} rows: sends reminder notifications before the due instant,
 * sends due notifications (and optional ticket timeline entries), then advances recurrence or
 * deactivates one-shot jobs. Planner UI stored jobs but nothing polled until this ran.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutomatedJobProcessingService {

    private final AutomatedJobRepository jobs;
    private final NotificationService notifications;
    private final TicketTimelineService ticketTimeline;
    private final TicketRepository tickets;

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private static final DateTimeFormatter[] PARSE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ROOT),
    };

    /** ISO-8601 local date-time, including optional fractional seconds (e.g. from JS datetime-local). */
    private static final DateTimeFormatter ISO_LOCAL_OPTIONAL_FRAC =
            new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE)
                    .appendLiteral('T')
                    .appendValue(ChronoField.HOUR_OF_DAY, 2)
                    .appendLiteral(':')
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
                    .optionalEnd()
                    .optionalEnd()
                    .toFormatter(Locale.ROOT);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOne(long jobId) {
        AutomatedJob j = jobs.findById(jobId).orElse(null);
        if (j == null) {
            return;
        }
        if (j.getActive() == null || j.getActive() != 1) {
            return;
        }
        if (j.getAssigneeUserId() == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueLdt = parseDueAt(j.getDueAt());
        if (dueLdt == null) {
            log.warn("Automated job {} has unparsable due_at '{}'", jobId, j.getDueAt());
            return;
        }

        String dueKey = j.getDueAt() == null ? "" : j.getDueAt().trim();
        int remMin =
                j.getReminderMinutes() == null || j.getReminderMinutes() < 1 ? 60 : j.getReminderMinutes();
        LocalDateTime reminderInstant = dueLdt.minusMinutes(remMin);

        // Due or overdue: notify once per scheduled instant (ONCE keeps flag until inactive)
        if (!now.isBefore(dueLdt)) {
            if ("ONCE".equalsIgnoreCase(safeRecurrence(j.getRecurrence()))) {
                if (dueKey.equals(safe(j.getLastDueNotifiedFor()))) {
                    return;
                }
            }
            fireDue(j, dueKey, dueLdt);
            return;
        }

        // Reminder: only at/after (due - reminder), and only if that instant was not already past when the job was created
        // (avoids an immediate "reminder" right after saving a job with a tight due window).
        if (now.isBefore(reminderInstant)) {
            return;
        }
        if (j.getCreatedAt() != null) {
            LocalDateTime createdLdt = LocalDateTime.ofInstant(j.getCreatedAt(), ZoneId.systemDefault());
            if (createdLdt.isAfter(reminderInstant)) {
                return;
            }
        }
        if (dueKey.equals(safe(j.getLastReminderAt()))) {
            return;
        }

        sendReminder(j, dueLdt);
        j.setLastReminderAt(dueKey);
        jobs.save(j);
    }

    private void fireDue(AutomatedJob j, String dueKey, LocalDateTime dueLdt) {
        Long assignee = j.getAssigneeUserId();
        String title = j.getJobTitle() == null ? "Task" : j.getJobTitle();
        String ref = formatTargetRef(j);
        String ticketSuffix = formatTicketSuffix(j.getTicketId());
        notifications.notifyScheduledJobDue(
                assignee, title, ticketSuffix, j.getTicketId(), j.getId(), ref, j.getTicketId() != null);

        if (j.getTicketId() != null) {
            ticketTimeline.log(
                    "JOB_DUE",
                    j.getTicketId(),
                    null,
                    assignee,
                    "T\u00e2che planifi\u00e9e \u00e9chue : " + title,
                    false);
        }

        String rec = safeRecurrence(j.getRecurrence());
        if ("ONCE".equals(rec)) {
            j.setLastDueNotifiedFor(dueKey);
            j.setActive(0);
        } else {
            LocalDateTime next = nextOccurrence(dueLdt, rec);
            if (next == null) {
                j.setActive(0);
                j.setLastDueNotifiedFor(dueKey);
            } else {
                j.setDueAt(OUT.format(next));
                j.setLastReminderAt(null);
                j.setLastDueNotifiedFor(null);
            }
        }
        jobs.save(j);
    }

    private void sendReminder(AutomatedJob j, LocalDateTime dueLdt) {
        String title = j.getJobTitle() == null ? "Task" : j.getJobTitle();
        String ref = formatTargetRef(j);
        String ticketSuffix = formatTicketSuffix(j.getTicketId());
        notifications.notifyScheduledJobReminder(
                j.getAssigneeUserId(),
                title,
                dueLdt,
                ticketSuffix,
                j.getTicketId(),
                j.getId(),
                ref,
                j.getTicketId() != null);
    }

    private String formatTicketSuffix(Long ticketId) {
        if (ticketId == null) {
            return "";
        }
        return tickets
                .findById(ticketId)
                .map(t -> " — " + ticketRef(t))
                .orElse("");
    }

    private String formatTargetRef(AutomatedJob j) {
        if (j.getTicketId() != null) {
            return tickets.findById(j.getTicketId()).map(t -> ticketRef(t)).orElse("TICKET-" + j.getTicketId());
        }
        return "JOB-" + j.getId();
    }

    private static String ticketRef(Ticket t) {
        return (t.getTicketNumber() == null || t.getTicketNumber().isBlank())
                ? ("#" + t.getId())
                : t.getTicketNumber().trim();
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static String safeRecurrence(String r) {
        return r == null ? "ONCE" : r.trim().toUpperCase(Locale.ROOT);
    }

    /** Active rows still considered open work (aligns with Mon travail / ticket lifecycle blocking). */
    public static boolean isActiveOpenTask(AutomatedJob j) {
        if (j.getActive() == null || j.getActive() != 1) {
            return false;
        }
        return !"CLOSED".equalsIgnoreCase(safe(j.getStatus()));
    }

    /**
     * Whether {@link AutomatedJob#getDueAt()} allows worker actions ({@code now >= due}). Blank or unparsable due means
     * <strong>no schedule lock</strong> (actions allowed). Ticket-linked tasks additionally skip this gate in Mon travail
     * so assignees can work before the displayed deadline.
     */
    public static boolean isDueInstantReached(AutomatedJob j) {
        LocalDateTime due = parseDueAt(j.getDueAt());
        if (due == null) {
            return true;
        }
        return !LocalDateTime.now().isBefore(due);
    }

    static LocalDateTime parseDueAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.endsWith("Z")) {
            try {
                return Instant.parse(s).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        String normalized = s.replace('T', ' ').trim();
        for (DateTimeFormatter f : PARSE_FORMATS) {
            try {
                return LocalDateTime.parse(stripZoneSuffix(normalized), f);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        String isoLocal = normalized.contains(" ") ? normalized.replaceFirst(" ", "T") : normalized;
        if (looksLikeIsoYmd(isoLocal)) {
            try {
                return LocalDateTime.parse(isoLocal, ISO_LOCAL_OPTIONAL_FRAC);
            } catch (DateTimeParseException ignored) {
                // fall through
            }
            try {
                return LocalDateTime.parse(isoLocal, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        try {
            return OffsetDateTime.parse(s.replace(" ", "T")).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean looksLikeIsoYmd(String isoLocal) {
        return isoLocal.length() >= 10
                && Character.isDigit(isoLocal.charAt(0))
                && isoLocal.charAt(4) == '-'
                && isoLocal.charAt(7) == '-';
    }

    /** Strip trailing offset like {@code +02:00} / {@code +0200} so space-separated local patterns can parse. */
    private static String stripZoneSuffix(String normalized) {
        String t = normalized;
        int plus = lastSignificantOffsetStart(t, '+');
        int minus = lastSignificantOffsetStart(t, '-');
        int cut = Math.max(plus, minus);
        if (cut > 10) {
            return t.substring(0, cut).trim();
        }
        return t;
    }

    private static int lastSignificantOffsetStart(String t, char sign) {
        if (sign == '-' && t.length() > 10) {
            int dateMinus = t.indexOf('-', 5);
            if (dateMinus > 0) {
                int secondMinus = t.indexOf('-', dateMinus + 1);
                if (secondMinus > dateMinus) {
                    int idx = t.lastIndexOf(sign);
                    return idx > secondMinus ? idx : -1;
                }
            }
        }
        if (sign == '+') {
            int idx = t.lastIndexOf(sign);
            return idx > 10 ? idx : -1;
        }
        return -1;
    }

    private static LocalDateTime nextOccurrence(LocalDateTime current, String recurrenceUpper) {
        return switch (recurrenceUpper) {
            case "DAILY" -> current.plusDays(1);
            case "WEEKLY" -> current.plusWeeks(1);
            case "MONTHLY" -> current.plusMonths(1);
            default -> null;
        };
    }
}
