package com.app.util;

import com.app.auth.Session;
import com.app.dao.TicketDAO;

public class SecurityUtil {

    public static boolean canAccessTicket(int ticketId) {

        int userId = Session.getUserId();
        String role = Session.getRole();

        // ADMIN is unrestricted across the system.
        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }

        // DIRECTEUR / SOUS-DIRECTEUR must be restricted to their own department.
        if ("DIRECTEUR".equalsIgnoreCase(role) || "SOUS-DIRECTEUR".equalsIgnoreCase(role)) {
            return TicketDAO.isTicketInCurrentUserDirection(ticketId);
        }

        // 🔐 Only current owner
        return TicketDAO.isUserCurrentOwner(ticketId, userId);
    }
}