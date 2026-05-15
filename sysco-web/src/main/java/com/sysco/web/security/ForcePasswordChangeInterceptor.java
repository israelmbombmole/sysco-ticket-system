package com.sysco.web.security;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {

    private final UserAccountRepository users;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/images/")
                || uri.startsWith("/login")
                || uri.startsWith("/change-password")
                || uri.startsWith("/logout")
                || uri.startsWith("/error")) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return true;
        }
        /*
         * Same resolution as SyscoUserDetailsService / NavigationAdvice: principal name may match username or matricule.
         * If we only checked username, valid sessions could be cleared and JSON APIs (e.g. mission assistant) would see 302 → HTML → fetch JSON parse errors.
         */
        Optional<UserAccount> userOpt =
                users.findByUsernameIgnoreCase(auth.getName()).or(() -> users.findByMatriculeIgnoreCase(auth.getName()));
        if (userOpt.isEmpty()) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + "/login?sessionInvalid");
            return false;
        }
        boolean mustChange = userOpt.get().getMustChangePassword() != null
                && userOpt.get().getMustChangePassword() == 1;
        /*
         * Password-expiry redirect breaks same-origin fetch() to JSON controllers (302 → HTML login/change page → JSON parse errors).
         * Full-page navigation still forces the change; APIs under .../api/... remain usable so assistants and similar tools work.
         */
        if (mustChange && !isApiJsonRequest(request)) {
            response.sendRedirect(request.getContextPath() + "/change-password?required");
            return false;
        }
        return true;
    }

    private static boolean isApiJsonRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String cp = request.getContextPath();
        if (cp != null && !cp.isBlank() && path.startsWith(cp)) {
            path = path.substring(cp.length());
        }
        return path.contains("/api/");
    }
}
