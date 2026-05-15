package com.sysco.web.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component("syscoSec")
public class SyscoSecurityExpressions {

    public boolean isSuperAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_SUPER_ADMIN");
    }

    /** Direction-scoped admin ({@code ADMIN}) — excludes {@code SUPER_ADMIN} even though both carry {@code ROLE_ADMIN}. */
    public boolean isDirectionAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_ADMIN") && !isSuperAdmin(authentication);
    }

    public boolean isDirectionAdminOrSuper(Authentication authentication) {
        return isSuperAdmin(authentication) || isDirectionAdmin(authentication);
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
