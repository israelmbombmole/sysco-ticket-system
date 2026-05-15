package com.sysco.web.security;

import com.sysco.web.service.AuthAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SyscoLogoutSuccessHandler implements LogoutSuccessHandler {

    private final AuthAccountService authAccountService;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (authentication != null) {
            authAccountService.registerLogout(authentication.getName());
        }
        response.sendRedirect("/login?logout");
    }
}
