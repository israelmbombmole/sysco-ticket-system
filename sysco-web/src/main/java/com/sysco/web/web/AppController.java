package com.sysco.web.web;

import com.sysco.web.service.DashboardMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppController {

    private final DashboardMetricsService dashboardMetrics;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String dashboard(org.springframework.security.core.Authentication auth, Model model) {
        model.addAttribute("pageTitleKey", "nav.dashboard");
        model.addAttribute("dash", dashboardMetrics.snapshot(auth == null ? null : auth.getName()));
        model.addAttribute("authUsername", auth == null ? "" : auth.getName());
        return "app/dashboard";
    }
}
