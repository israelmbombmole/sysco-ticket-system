package com.app.security;

public class RoleUtil {

    public static int getLevel(String role) {

        return switch (role) {
            case "DIRECTEUR" -> 6;
            case "SOUS-DIRECTEUR" -> 5;
            case "INSPECTEUR" -> 4;
            case "CONTROLEUR" -> 3;
            case "COURRIER" -> 3;
            case "VERIFICATEUR" -> 2;
            case "VERIFICATEUR-ASSISTANT" -> 1;
            default -> 0;
        };
    }

    public static boolean canManage(String currentRole, String targetRole) {
        return getLevel(currentRole) > getLevel(targetRole);
    }

    public static boolean isHighLevel(String role) {
        return getLevel(role) >= 5;
    }
    
    public static boolean canAssign(String fromRole, String toRole) {
    return getLevel(fromRole) >= getLevel(toRole);
}
}