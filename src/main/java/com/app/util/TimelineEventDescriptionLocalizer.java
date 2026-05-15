package com.app.util;

import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Localizes system-generated ticket/task timeline descriptions stored in English in the DB.
 * User-authored {@code COMMENT} text is returned unchanged.
 */
public final class TimelineEventDescriptionLocalizer {

    private TimelineEventDescriptionLocalizer() {}

    private static String fmt(String key, String fallback, Object... args) {
        return MessageFormat.format(I18n.t(key, fallback), args);
    }

    public static String localizeTicketTimelineDescription(String type, String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String t = type == null ? "" : type.trim().toUpperCase();
        if ("COMMENT".equals(t)) {
            return description;
        }

        Matcher m;

        if ("CREATED".equals(t)) {
            m = Pattern.compile("^(.+?) ticket created by (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.createdByType", "{0} ticket created by {1}", m.group(1), m.group(2));
            }
        }
        if ("ASSIGNED".equals(t)) {
            m = Pattern.compile("^Ticket assigned to (.+?) \\((.+?)\\)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.assignedTo", "Ticket assigned to {0} ({1})", m.group(1), m.group(2));
            }
        }
        if ("STARTED".equals(t)) {
            m = Pattern.compile("^Work started by (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.workStartedBy", "Work started by {0}", m.group(1));
            }
            m = Pattern.compile("^Ticket started by (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.ticketStartedBy", "Ticket started by {0}", m.group(1));
            }
        }
        if ("CLOSED".equals(t)) {
            m = Pattern.compile("^Ticket closed by (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.closedBy", "Ticket closed by {0}", m.group(1));
            }
        }
        if ("TICKET_MERGED".equals(t)) {
            m = Pattern.compile("^Merged tickets into (.+?): (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.mergedInto", "Merged tickets into {0}: {1}", m.group(1), m.group(2));
            }
        }
        if ("TASK_COMPLETED".equals(t)) {
            m = Pattern.compile("^Task #(\\d+) completed by (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.taskCompleted", "Task #{0} completed by {1}", m.group(1), m.group(2));
            }
        }
        if ("ALL_TASKS_COMPLETED".equals(t)) {
            m = Pattern.compile("^All tasks completed for ticket (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.allTasksDone", "All tasks completed for ticket {0}", m.group(1));
            }
        }
        if ("REASSIGNED".equals(t)) {
            m = Pattern.compile("^Ticket reassigned to (.+?) \\((.+?)\\)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.reassignedTo", "Ticket reassigned to {0} ({1})", m.group(1), m.group(2));
            }
        }
        if ("ESCALATED".equals(t)) {
            m = Pattern.compile("^Ticket escalated from (.+?) to (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.ticket.escalatedFromTo", "Ticket escalated from {0} to {1}", m.group(1), m.group(2));
            }
        }
        if ("SLA_BREACHED".equals(t) && "SLA deadline exceeded.".equals(description)) {
            return I18n.t("timeline.sla.breached", "SLA deadline exceeded.");
        }
        if ("SLA_WARNING".equals(t)) {
            m = Pattern.compile("^Ticket approaching SLA breach \\((\\d+)% used\\)\\.$").matcher(description);
            if (m.find()) {
                return fmt("timeline.sla.warning", "Ticket approaching SLA breach ({0}% used).", m.group(1));
            }
        }
        if ("ESCALATED_L2".equals(t)
                && "Escalated to Level 2 authority due to severe SLA breach.".equals(description)) {
            return I18n.t("timeline.sla.escalatedL2", "Escalated to Level 2 authority due to severe SLA breach.");
        }
        if ("ESCALATED_L1".equals(t) && "Escalated to Level 1 supervisor.".equals(description)) {
            return I18n.t("timeline.sla.escalatedL1", "Escalated to Level 1 supervisor.");
        }

        return description;
    }

    public static String localizeTaskTimelineDescription(String type, String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String t = type == null ? "" : type.trim().toUpperCase();
        if ("COMMENT".equals(t)) {
            return description;
        }

        Matcher m;

        if ("TASK_STARTED".equals(t)) {
            m = Pattern.compile("^Task started by (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.task.startedBy", "Task started by {0}", m.group(1));
            }
        }
        if ("TASK_COMPLETED".equals(t)) {
            m = Pattern.compile("^Task completed by (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.task.completedBy", "Task completed by {0}", m.group(1));
            }
        }
        if ("TASK_REASSIGNED".equals(t)) {
            m = Pattern.compile("^Task reassigned to (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.task.reassignedTo", "Task reassigned to {0}", m.group(1));
            }
        }
        if ("TASK_CREATED".equals(t)) {
            m = Pattern.compile("^Created task \"(.+?)\" and assigned to (.+)$").matcher(description);
            if (m.find()) {
                return fmt("timeline.task.createdAssigned", "Created task \"{0}\" and assigned to {1}",
                        m.group(1), m.group(2));
            }
        }

        return description;
    }
}
