package com.sysco.web.security;

import com.sysco.web.service.FileShareManagementAccessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /app/file-share-management} is limited to {@code SUPER_ADMIN}, direction {@code ADMIN}, or a short-lived
 * OTP session started from {@code /app/file-share-management/access}.
 */
@RequiredArgsConstructor
public class FileShareManagementAccessFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/app/file-share-management";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (!path.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (path.startsWith(PREFIX + "/access")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (WebAuthenticationHelper.isSuperAdmin(auth)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (WebAuthenticationHelper.isDirectionAdmin(auth)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Instant until =
                session == null ? null : (Instant) session.getAttribute(FileShareManagementAccessService.SESSION_UNTIL_ATTR);
        if (until != null && Instant.now().isBefore(until)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (session != null && until != null) {
            session.removeAttribute(FileShareManagementAccessService.SESSION_UNTIL_ATTR);
        }

        if (HttpMethod.GET.matches(request.getMethod())) {
            response.sendRedirect(contextPath + PREFIX + "/access?expired=1");
        } else {
            response.sendRedirect(contextPath + PREFIX + "/access?denied=1");
        }
    }
}
