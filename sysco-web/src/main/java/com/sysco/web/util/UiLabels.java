package com.sysco.web.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

/**
 * Resolves enum-like codes and known genealogy note strings for display in the user's locale.
 */
@Component("uiLabels")
public final class UiLabels {

    private final MessageSource messageSource;

    private static final Pattern MERGED_NOTE =
            Pattern.compile("^Merged ticket (.+?) into (.+?) \\(survivor was (.+?)\\)$");

    private static final String ESCALATED_TO_PREFIX = "Ticket escalated to ";

    private static final Map<String, String> GENEALOGY_KEYS = Map.ofEntries(
            Map.entry("Ticket created from data entry", "genealogy.note.dataEntry"),
            Map.entry("Ticket created", "genealogy.note.created"),
            Map.entry("Ticket assigned", "genealogy.note.assigned"),
            Map.entry("Ticket closed", "genealogy.note.closed"),
            Map.entry("Ticket closed from My Work", "genealogy.note.closedMyWork"),
            Map.entry("Ticket started from My Work", "genealogy.note.startedMyWork"),
            Map.entry("Ticket escalated", "genealogy.note.escalated"),
            Map.entry("Close requested (pending review)", "genealogy.note.closeRequestedNote"),
            Map.entry("Ticket closed after closure review", "genealogy.note.closedAfterReview"),
            Map.entry("Ticket updated", "genealogy.note.updated"),
            Map.entry("Task created from ticket", "genealogy.note.taskFromTicket"),
            Map.entry("Task created from ticket detail", "genealogy.note.taskFromDetail"),
            Map.entry("Task delegated from ticket monitoring", "genealogy.note.taskDelegatedMonitoring"),
            Map.entry("Tache auto-creee pour assignation multiple", "genealogy.note.taskAutoMulti"));

    public UiLabels(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String ticketStatus(String code, Locale locale) {
        return enumLabel("enum.ticket.status.", code, locale);
    }

    public String ticketPriority(String code, Locale locale) {
        return enumLabel("enum.ticket.priority.", code, locale);
    }

    /** Job/task row status codes (OPEN, CLOSED, IN_PROGRESS, …). */
    public String taskStatus(String code, Locale locale) {
        return enumLabel("enum.task.status.", code, locale);
    }

    public String missionStatus(String code, Locale locale) {
        return enumLabel("enum.mission.status.", code, locale);
    }

    /** Courier packet workflow status. */
    public String courierPacketStatus(String code, Locale locale) {
        return enumLabel("enum.courier.status.", code, locale);
    }

    /**
     * Localizes known system-generated genealogy notes; passes through user comments and unknown text.
     */
    public String genealogyNote(String note, Locale locale) {
        if (note == null || note.isBlank()) {
            return "";
        }
        String trimmed = note.trim();
        String key = GENEALOGY_KEYS.get(trimmed);
        if (key != null) {
            return getMessage(key, null, trimmed, locale);
        }
        if (trimmed.startsWith(ESCALATED_TO_PREFIX)) {
            String user = trimmed.substring(ESCALATED_TO_PREFIX.length()).trim();
            return getMessage("genealogy.note.escalatedTo", new Object[] {user}, trimmed, locale);
        }
        Matcher m = MERGED_NOTE.matcher(trimmed);
        if (m.matches()) {
            return getMessage(
                    "genealogy.note.merged",
                    new Object[] {m.group(1), m.group(2), m.group(3)},
                    trimmed,
                    locale);
        }
        return trimmed;
    }

    private String enumLabel(String prefix, String code, Locale locale) {
        if (code == null || code.isBlank()) {
            return "\u2014";
        }
        String raw = code.trim();
        String normalized = raw.toUpperCase(Locale.ROOT).replace(' ', '_');
        String fullKey = prefix + normalized;
        return getMessage(fullKey, null, raw, locale);
    }

    private String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        try {
            return messageSource.getMessage(code, args, defaultMessage, locale);
        } catch (NoSuchMessageException e) {
            return defaultMessage;
        }
    }
}
