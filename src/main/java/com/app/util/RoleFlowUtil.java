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
    public static final String COURRIER = "COURRIER";

    // =========================
    // NORMALIZE
    // =========================
    public static String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }

    // =========================
    // HIGH LEVEL
    // =========================
    public static boolean isHighLevel(String role) {
        role = normalize(role);
        return DIRECTEUR.equals(role) || SOUS_DIRECTEUR.equals(role);
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
            case COURRIER:
                return toRole.equals(DIRECTEUR)
                        || toRole.equals(SOUS_DIRECTEUR)
                        || toRole.equals(INSPECTEUR)
                        || toRole.equals(CONTROLEUR);

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
            case COURRIER:
                roles.add(DIRECTEUR);
                roles.add(SOUS_DIRECTEUR);
                roles.add(INSPECTEUR);
                roles.add(CONTROLEUR);
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
}