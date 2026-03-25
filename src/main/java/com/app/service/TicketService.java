package com.app.service;

import com.app.auth.Session;
import com.app.dao.TicketDAO;
import com.app.model.Ticket;

public class TicketService {

    // ================= START WORK =================
    public static void startWork(Ticket ticket) {

        validateOwnership(ticket);
        validateTransition(ticket.getStatus(), "IN_PROGRESS");

        TicketDAO.startWork(ticket.getId(), Session.getUserId());

        ticket.setStatus("IN_PROGRESS");
    }

    // ================= CLOSE TICKET =================
    public static void closeTicket(Ticket ticket) {

        validateOwnership(ticket);
        validateTransition(ticket.getStatus(), "CLOSED");

        TicketDAO.closeTicket(ticket.getId());

        ticket.setStatus("CLOSED");
    }

    // ================= MERGE TICKET =================
public static void mergeTicket(Ticket source, Ticket target, String note) {

    validateMergePermission(source, target);
    validateTransition(source.getStatus(), "MERGED");

    TicketDAO.mergeTicket(source.getId(), target.getId(), note);

    source.setStatus("MERGED");
}

    // ================= VALIDATIONS =================

    private static void validateOwnership(Ticket ticket) {

        if (ticket.getAssignedTo() == null) {
            throw new RuntimeException("Ticket not assigned.");
        }

        if (!Session.getRole().equals("ADMIN")
                && ticket.getAssignedTo() != Session.getUserId()) {

            throw new RuntimeException("You are not assigned to this ticket.");
        }
    }

    // STRICT LIFECYCLE ENGINE
    private static void validateTransition(String current, String next) {

        switch (current) {

            case "OPEN" -> {
                if (!next.equals("IN_PROGRESS") && !next.equals("MERGED"))
                    throw new RuntimeException("Illegal transition from OPEN to " + next);
            }

            case "IN_PROGRESS" -> {
                if (!next.equals("CLOSED") && !next.equals("MERGED"))
                    throw new RuntimeException("Illegal transition from IN_PROGRESS to " + next);
            }

            case "CLOSED", "MERGED" ->
                throw new RuntimeException("Ticket already finalized.");

            default ->
                throw new RuntimeException("Unknown ticket state: " + current);
        }
    }

    // ================= MERGE PERMISSION =================
    private static void validateMergePermission(Ticket source, Ticket target) {

        String role = Session.getRole();
        int userId = Session.getUserId();

        if (role.equals("ADMIN")) return;

        if (role.equals("AGENT")) return;

        // USER restrictions
        if (role.equals("USER")) {

            boolean sameOwner =
                    (source.getAssignedTo() != null &&
                     target.getAssignedTo() != null &&
                     source.getAssignedTo() == userId &&
                     target.getAssignedTo() == userId);

            boolean sameCreator =
        source.getCreatedById() != null &&
        target.getCreatedById() != null &&
        source.getCreatedById().equals(userId) &&
        target.getCreatedById().equals(userId);
            
            if (!sameOwner && !sameCreator) {
                throw new RuntimeException("You cannot merge tickets you do not own.");
            }
        }
    }
}