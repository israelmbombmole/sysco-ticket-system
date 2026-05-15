package com.sysco.web.web;

import com.sysco.web.navigation.NavigationRegistry;
import com.sysco.web.navigation.NavItem;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.WebSyscoPermissions;
import com.sysco.web.service.ChatService;
import com.sysco.web.service.GuidedTourService;
import com.sysco.web.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavigationAdvice {

    private final NotificationService notificationService;
    private final ChatService chatService;
    private final GuidedTourService guidedTourService;
    private final UserAccountRepository users;
    private final boolean formationMode;

    public NavigationAdvice(
            NotificationService notificationService,
            ChatService chatService,
            GuidedTourService guidedTourService,
            UserAccountRepository users,
            @Value("${sysco.formation.enabled:false}") boolean formationMode) {
        this.notificationService = notificationService;
        this.chatService = chatService;
        this.guidedTourService = guidedTourService;
        this.users = users;
        this.formationMode = formationMode;
    }

    @ModelAttribute("syscoFormationMode")
    public boolean syscoFormationMode() {
        return formationMode;
    }

    @ModelAttribute("navItems")
    public List<NavItem> navItems(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }
        return NavigationRegistry.mainNav().stream()
                .filter(item -> WebSyscoPermissions.canAccessNavPath(authentication, item.path()))
                .toList();
    }

    /** Active sidebar highlight — compares to {@link NavItem#path()}. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getServletPath();
    }

    @ModelAttribute("headerDisplayUsername")
    public String headerDisplayUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "";
        }
        String key = authentication.getName();
        return users.findByUsernameIgnoreCase(key)
                .map(u -> u.getUsername() == null ? key : u.getUsername())
                .or(() -> users.findByMatriculeIgnoreCase(key).map(u -> u.getUsername() == null ? key : u.getUsername()))
                .orElse(key);
    }

    @ModelAttribute("headerUnreadNotifications")
    public long headerUnreadNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return 0;
        }
        return notificationService.page(authentication.getName()).unreadCount();
    }

    /**
     * Unread chat messages for header/floating badges (per-conversation read cursors; opening a thread marks it read).
     */
    @ModelAttribute("headerUnreadChat")
    public long headerUnreadChat(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return 0;
        }
        return chatService.countUnreadChat(authentication.getName());
    }

    /**
     * One-shot flag: after login (or password change), start the guided tour once when the shell renders.
     */
    @ModelAttribute("syscoAutoStartTour")
    public boolean syscoAutoStartTour(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object flag = session.getAttribute("syscoStartGuidedTour");
        if (Boolean.TRUE.equals(flag)) {
            session.removeAttribute("syscoStartGuidedTour");
            return true;
        }
        return false;
    }

    @ModelAttribute("tourGuidePayloadJson")
    public String tourGuidePayloadJson(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "{\"steps\":[],\"labels\":{}}";
        }
        List<NavItem> items = NavigationRegistry.mainNav().stream()
                .filter(item -> WebSyscoPermissions.canAccessNavPath(authentication, item.path()))
                .toList();
        Locale locale = LocaleContextHolder.getLocale();
        return guidedTourService.buildTourPayloadJson(items, locale);
    }
}
