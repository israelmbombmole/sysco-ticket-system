package com.sysco.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysco.web.navigation.NavItem;
import com.sysco.web.repo.UserAccountRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuidedTourService {

    private static final Map<String, String> PATH_TO_DESC_KEY = new LinkedHashMap<>();

    static {
        PATH_TO_DESC_KEY.put("/app", "tour.mod.dashboard");
        PATH_TO_DESC_KEY.put("/app/data-entry", "tour.mod.dataEntry");
        PATH_TO_DESC_KEY.put("/app/courier", "tour.mod.courier");
        PATH_TO_DESC_KEY.put("/app/courier-management", "tour.mod.courierManagement");
        PATH_TO_DESC_KEY.put("/app/data-management", "tour.mod.dataManagement");
        PATH_TO_DESC_KEY.put("/app/data-share", "tour.mod.dataShare");
        PATH_TO_DESC_KEY.put("/app/my-activity", "tour.mod.myActivity");
        PATH_TO_DESC_KEY.put("/app/my-work", "tour.mod.myWork");
        PATH_TO_DESC_KEY.put("/app/ticket-monitoring", "tour.mod.ticketMonitoring");
        PATH_TO_DESC_KEY.put("/app/ticket-management", "tour.mod.ticketManagement");
        PATH_TO_DESC_KEY.put("/app/file-share-management", "tour.mod.fileShareManagement");
        PATH_TO_DESC_KEY.put("/app/user-management", "tour.mod.userManagement");
        PATH_TO_DESC_KEY.put("/app/agenda", "tour.mod.agenda");
        PATH_TO_DESC_KEY.put("/app/login-audit", "tour.mod.loginAudit");
        PATH_TO_DESC_KEY.put("/app/file-share-audit", "tour.mod.fileShareAudit");
        PATH_TO_DESC_KEY.put("/app/create-ticket", "tour.mod.createTicket");
        PATH_TO_DESC_KEY.put("/app/job-scheduler", "tour.mod.jobScheduler");
        PATH_TO_DESC_KEY.put("/app/missions", "tour.mod.missions");
        PATH_TO_DESC_KEY.put("/app/my-shift", "tour.mod.myShift");
    }

    private final UserAccountRepository users;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public boolean needsAutoTour(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return users.findByUsernameIgnoreCase(username.trim())
                .map(u -> u.getOnboardingTutorialCompleted() == null || u.getOnboardingTutorialCompleted() == 0)
                .orElse(false);
    }

    @Transactional
    public void markTutorialCompleted(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        users.findByUsernameIgnoreCase(username.trim())
                .ifPresent(u -> {
                    u.setOnboardingTutorialCompleted(1);
                    users.save(u);
                });
    }

    /** JSON payload for {@code sysco-guided-tour.js}: steps + button labels (resolved for current locale). */
    public String buildTourPayloadJson(List<NavItem> visibleNavItems, Locale locale) {
        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(step(locale, ".app-root", "tour.step.welcome.title", "tour.step.welcome.body"));
        steps.add(step(locale, ".top-bar", "tour.step.header.title", "tour.step.header.body"));
        steps.add(step(locale, ".app-sidebar nav", "tour.step.sidebar.title", "tour.step.sidebar.body"));
        int i = 0;
        for (NavItem item : visibleNavItems) {
            String title = messageSource.getMessage(item.messageKey(), null, item.messageKey(), locale);
            String descKey = PATH_TO_DESC_KEY.getOrDefault(item.path(), "tour.mod.generic");
            String description = messageSource.getMessage(descKey, null, descKey, locale);
            steps.add(Map.of(
                    "selector", "#tour-nav-" + i,
                    "title", title,
                    "description", description));
            i++;
        }
        steps.add(step(locale, "#sysco-help-tour-btn", "tour.step.help.title", "tour.step.help.body"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("steps", steps);
        payload.put("labels", uiLabels(locale));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{\"steps\":[],\"labels\":{}}";
        }
    }

    private Map<String, String> step(Locale locale, String selector, String titleKey, String bodyKey) {
        return Map.of(
                "selector", selector,
                "title", messageSource.getMessage(titleKey, null, titleKey, locale),
                "description", messageSource.getMessage(bodyKey, null, bodyKey, locale));
    }

    private Map<String, String> uiLabels(Locale locale) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("next", messageSource.getMessage("tour.ui.next", null, "Next", locale));
        m.put("prev", messageSource.getMessage("tour.ui.prev", null, "Previous", locale));
        m.put("done", messageSource.getMessage("tour.ui.done", null, "Done", locale));
        m.put("progress", messageSource.getMessage("tour.ui.progress", null, "{{current}} / {{total}}", locale));
        return m;
    }
}
