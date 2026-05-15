package com.app.util;

import java.util.LinkedHashSet;
import java.util.Set;

public class RoleFlowUtil {

    // =========================
    // ROLE CONSTANTS (MATCH UI EXACTLY)
    // =========================
    public static final String DIRECTEUR = "DIRECTEUR";
    public static final String SOUS_DIRECTEUR = "SOUS-DIRECTEUR";
    public static final String INSPECTEUR = "INSPECTEUR";
    public static final String CONTROLEUR = "CONTROLEUR";
    public static final String VERIFICATEUR = "VERIFICATEUR";
    public static final String ASSISTANT = "VERIFICATEUR-ASSISTANT";

    // =========================
    // NORMALIZE
    // =========================
    public static String normalize(String role) {
        if (role == null) return "";
        String r = role.trim().toUpperCase();
        // ADMIN is superuser and follows DIRECTEUR capability baseline.
        if ("ADMIN".equals(r)) {
            return DIRECTEUR;
        }
        return r;
    }

    // =========================
    // HIGH LEVEL
    // =========================
    public static boolean isHighLevel(String role) {
        role = normalize(role);
        return DIRECTEUR.equals(role) || SOUS_DIRECTEUR.equals(role);
    }

    /** Roles that may use Ticket Monitoring → Assign Ticket (unassigned list). */
    public static boolean canSeeUnassignedTicketsForAssignment(String role) {
        if (role == null) {
            return false;
        }
        role = normalize(role);
        if ("ADMIN".equals(role)) {
            return true;
        }
        return DIRECTEUR.equals(role)
                || SOUS_DIRECTEUR.equals(role)
                || INSPECTEUR.equals(role)
                || CONTROLEUR.equals(role);
    }

    // =========================
    // PERMISSIONS
    // =========================
    public static boolean canSeeAllTickets(String role) {
        role = normalize(role);
        return DIRECTEUR.equals(role) || SOUS_DIRECTEUR.equals(role);
    }

    public static boolean canCloseTicket(String role) {
        role = normalize(role);
        return DIRECTEUR.equals(role)
                || SOUS_DIRECTEUR.equals(role)
                || INSPECTEUR.equals(role)
                || CONTROLEUR.equals(role);
    }

    // =========================
    // ASSIGNMENT RULES
    // =========================
    public static boolean canAssign(String fromRole, String toRole) {

        fromRole = normalize(fromRole);
        toRole = normalize(toRole);

        switch (fromRole) {

            case DIRECTEUR:
                return toRole.equals(DIRECTEUR)
                        || toRole.equals(SOUS_DIRECTEUR)
                        || toRole.equals(INSPECTEUR)
                        || toRole.equals(CONTROLEUR)
                        || toRole.equals(VERIFICATEUR)
                        || toRole.equals(ASSISTANT);

            case SOUS_DIRECTEUR:
                return toRole.equals(SOUS_DIRECTEUR)
                        || toRole.equals(INSPECTEUR)
                        || toRole.equals(CONTROLEUR)
                        || toRole.equals(VERIFICATEUR)
                        || toRole.equals(ASSISTANT);

            case INSPECTEUR:
                return toRole.equals(INSPECTEUR)
                        || toRole.equals(CONTROLEUR)
                        || toRole.equals(VERIFICATEUR)
                        || toRole.equals(ASSISTANT);

            case CONTROLEUR:
                return toRole.equals(CONTROLEUR)
                        || toRole.equals(VERIFICATEUR)
                        || toRole.equals(ASSISTANT);

            default:
                return false; // VERIFICATEUR & ASSISTANT cannot assign
        }
    }

    // =========================
    // ESCALATION RULES
    // =========================
    public static boolean isEscalation(String fromRole, String toRole) {

        fromRole = normalize(fromRole);
        toRole = normalize(toRole);

        return (SOUS_DIRECTEUR.equals(fromRole) && DIRECTEUR.equals(toRole))
                || (INSPECTEUR.equals(fromRole) && SOUS_DIRECTEUR.equals(toRole))
                || (CONTROLEUR.equals(fromRole) && INSPECTEUR.equals(toRole))
                || ((VERIFICATEUR.equals(fromRole) || ASSISTANT.equals(fromRole))
                    && CONTROLEUR.equals(toRole));
    }

    // =========================
    // GET ESCALATION TARGET
    // =========================
    public static String getEscalationRole(String role) {

        role = normalize(role);

        switch (role) {
            case SOUS_DIRECTEUR:
                return DIRECTEUR;
            case INSPECTEUR:
                return SOUS_DIRECTEUR;
            case CONTROLEUR:
                return INSPECTEUR;
            case VERIFICATEUR:
            case ASSISTANT:
                return CONTROLEUR;
            default:
                return null;
        }
    }

    // =========================
    // ASSIGNMENT TYPE
    // =========================
    public static String getAssignmentType(String fromRole, String toRole, boolean multiAssignment) {

        if (multiAssignment) {
            return "MULTI_ASSIGNMENT";
        }

        if (isEscalation(fromRole, toRole)) {
            return "ESCALATION";
        }

        return "REASSIGNMENT";
    }

    // =========================
    // ALLOWED TARGET ROLES (UI)
    // =========================
    public static Set<String> getAllowedTargetRoles(String fromRole) {

        fromRole = normalize(fromRole);

        Set<String> roles = new LinkedHashSet<>();

        switch (fromRole) {

            case DIRECTEUR:
                roles.add(DIRECTEUR);
                roles.add(SOUS_DIRECTEUR);
                roles.add(INSPECTEUR);
                roles.add(CONTROLEUR);
                roles.add(VERIFICATEUR);
                roles.add(ASSISTANT);
                break;

            case SOUS_DIRECTEUR:
                roles.add(SOUS_DIRECTEUR);
                roles.add(INSPECTEUR);
                roles.add(CONTROLEUR);
                roles.add(VERIFICATEUR);
                roles.add(ASSISTANT);
                break;

            case INSPECTEUR:
                roles.add(INSPECTEUR);
                roles.add(CONTROLEUR);
                roles.add(VERIFICATEUR);
                roles.add(ASSISTANT);
                break;

            case CONTROLEUR:
                roles.add(CONTROLEUR);
                roles.add(VERIFICATEUR);
                roles.add(ASSISTANT);
                break;

            case VERIFICATEUR:
            case ASSISTANT:
                // cannot assign
                break;
        }

        return roles;
    }

    /** Roles strictly higher than current role, in escalation order. */
    public static Set<String> getHigherRolesForCloseRequest(String fromRole) {
        fromRole = normalize(fromRole);
        Set<String> roles = new LinkedHashSet<>();

        switch (fromRole) {
            case ASSISTANT:
            case VERIFICATEUR:
                roles.add(CONTROLEUR);
                roles.add(INSPECTEUR);
                roles.add(SOUS_DIRECTEUR);
                roles.add(DIRECTEUR);
                break;
            case CONTROLEUR:
                roles.add(INSPECTEUR);
                roles.add(SOUS_DIRECTEUR);
                roles.add(DIRECTEUR);
                break;
            case INSPECTEUR:
                roles.add(SOUS_DIRECTEUR);
                roles.add(DIRECTEUR);
                break;
            case SOUS_DIRECTEUR:
                roles.add(DIRECTEUR);
                break;
            default:
                break;
        }

        return roles;
    }

    /** Internal ticket escalation targets allowed per role. */
    public static Set<String> getAllowedInternalEscalationRoles(String fromRole) {
        fromRole = normalize(fromRole);
        Set<String> roles = new LinkedHashSet<>();
        switch (fromRole) {
            case ASSISTANT:
                roles.add(DIRECTEUR);
                break;
            case VERIFICATEUR:
                roles.add(CONTROLEUR);
                roles.add(INSPECTEUR);
                break;
            case CONTROLEUR:
                roles.add(INSPECTEUR);
                roles.add(SOUS_DIRECTEUR);
                break;
            case INSPECTEUR:
                roles.add(SOUS_DIRECTEUR);
                break;
            case SOUS_DIRECTEUR:
                roles.add(DIRECTEUR);
                break;
            case DIRECTEUR:
            default:
                break;
        }
        return roles;
    }

    /** Roles that may approve external escalation request for current role. */
    public static Set<String> getExternalEscalationApproverRoles(String fromRole) {
        fromRole = normalize(fromRole);
        Set<String> roles = new LinkedHashSet<>();
        switch (fromRole) {
            case ASSISTANT:
            case VERIFICATEUR:
                roles.add(CONTROLEUR);
                roles.add(INSPECTEUR);
                break;
            case CONTROLEUR:
                roles.add(INSPECTEUR);
                roles.add(SOUS_DIRECTEUR);
                break;
            default:
                break;
        }
        return roles;
    }

    public static boolean requiresExternalEscalationApproval(String fromRole) {
        return !getExternalEscalationApproverRoles(fromRole).isEmpty();
    }
}