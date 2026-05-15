package com.sysco.web.web;

import com.sysco.web.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/app/notifications")
@RequiredArgsConstructor
public class NotificationsController {

    private final NotificationService notifications;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String page(org.springframework.security.core.Authentication auth, Model model) {
        model.addAttribute("pageTitleKey", "nav.notifications");
        model.addAttribute("page", notifications.page(auth.getName()));
        return "app/notifications";
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public String markRead(@PathVariable("id") Long id, org.springframework.security.core.Authentication auth) {
        notifications.markRead(id, auth.getName());
        return "redirect:/app/notifications";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated()")
    public String delete(@PathVariable("id") Long id, org.springframework.security.core.Authentication auth) {
        notifications.deleteNotification(id, auth.getName());
        return "redirect:/app/notifications";
    }
}
