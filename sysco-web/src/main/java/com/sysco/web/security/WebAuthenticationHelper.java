package com.sysco.web.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class WebAuthenticationHelper {

    private WebAuthenticationHelper() {}

    public static boolean isSuperAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_SUPER_ADMIN");
    }

    public static boolean isDirectionAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_ADMIN") && !isSuperAdmin(authentication);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            if (authority.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
