package com.app.util;

import com.app.auth.Session;
import com.app.dao.TicketDAO;

public class SecurityUtil {

    public static boolean canAccessTicket(int ticketId) {

        int userId = Session.getUserId();
        String role = Session.getRole();

        // 🔓 High level override
        if (RoleFlowUtil.isHighLevel(role)) {
            return true;
        }

        // 🔐 Only current owner
        return TicketDAO.isUserCurrentOwner(ticketId, userId);
    }
}