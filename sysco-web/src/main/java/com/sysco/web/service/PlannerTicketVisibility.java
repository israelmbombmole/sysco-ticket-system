package com.sysco.web.service;

import com.sysco.web.domain.Ticket;
import java.time.Instant;

/** Planner-created tickets are suppressed from operational UI until {@link Ticket#getPlannerVisibleAfter()}. */
public final class PlannerTicketVisibility {

    private PlannerTicketVisibility() {}

    /** True while the ticket must not appear in ticket lists, monitoring, Mon travail, etc. (still visible in the planner). */
    public static boolean isHiddenFromOperationalViews(Ticket t, Instant now) {
        if (t == null || now == null) {
            return false;
        }
        Instant v = t.getPlannerVisibleAfter();
        return v != null && now.isBefore(v);
    }
}
