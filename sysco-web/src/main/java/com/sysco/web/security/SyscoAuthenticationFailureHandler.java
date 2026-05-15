package com.sysco.web.security;

import com.sysco.web.service.AuthAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SyscoAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final AuthAccountService authAccountService;

    private static String redirectTarget(HttpServletRequest request, String pathAndQuery) {
        String ctx = request.getContextPath();
        String prefix = ctx == null ? "" : ctx;
        return prefix + pathAndQuery;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        String loginKey = request.getParameter("username");

        if (exception instanceof LockedException) {
            int mins = authAccountService.minutesRemainingOnLock(loginKey).orElse(10);
            response.sendRedirect(redirectTarget(request, "/login?locked&minutes=" + mins));
            return;
        }
        if (exception instanceof DisabledException) {
            response.sendRedirect(redirectTarget(request, "/login?inactive"));
            return;
        }

        String target = authAccountService.afterBadCredentials(loginKey);
        response.sendRedirect(redirectTarget(request, target));
    }
}
