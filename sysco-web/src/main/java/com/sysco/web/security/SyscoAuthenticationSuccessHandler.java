package com.sysco.web.security;

import com.sysco.web.service.AuthAccountService;
import com.sysco.web.service.GuidedTourService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SyscoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthAccountService authAccountService;
    private final GuidedTourService guidedTourService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        String username = authentication.getName();
        boolean mustChange = authAccountService.registerSuccess(username);
        if (!mustChange && guidedTourService.needsAutoTour(username)) {
            request.getSession().setAttribute("syscoStartGuidedTour", Boolean.TRUE);
        }
        response.sendRedirect(mustChange ? "/change-password?required" : "/app");
    }
}
