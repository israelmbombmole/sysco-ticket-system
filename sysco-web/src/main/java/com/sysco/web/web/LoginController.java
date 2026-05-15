package com.sysco.web.web;

import com.sysco.web.service.AuthAccountService;
import com.sysco.web.service.GuidedTourService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AuthAccountService authAccountService;
    private final GuidedTourService guidedTourService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login/forgot-password")
    public String forgotPassword(
            @RequestParam(name = "usernameOrEmail", required = false) String usernameOrEmail,
            RedirectAttributes ra) {
        try {
            String temp = authAccountService.issueForgotPassword(usernameOrEmail);
            ra.addFlashAttribute("forgotPasswordTemp", temp);
            ra.addFlashAttribute("forgotPasswordOk", true);
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("forgotPasswordError", true);
        }
        return "redirect:/login";
    }

    @GetMapping("/change-password")
    public String changePasswordPage() {
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @RequestParam(name = "newPassword", required = false) String newPassword,
            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
            org.springframework.security.core.Authentication authentication,
            HttpServletRequest request,
            RedirectAttributes ra) {
        if (authentication == null || authentication.getName() == null) {
            return "redirect:/login";
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("changePasswordError", "mismatch");
            return "redirect:/change-password";
        }
        try {
            authAccountService.changePassword(authentication.getName(), newPassword);
            ra.addFlashAttribute("changePasswordOk", true);
            if (guidedTourService.needsAutoTour(authentication.getName())) {
                request.getSession().setAttribute("syscoStartGuidedTour", Boolean.TRUE);
            }
            return "redirect:/app";
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("changePasswordError", "invalid");
            return "redirect:/change-password";
        }
    }
}
