package com.sysco.web.bootstrap;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.service.UserNotificationEmailFallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aligns persisted profile e-mails with the configured ticket-notification fallback (blank or *.local → inbox).
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class NotificationEmailRepairRunner implements ApplicationRunner {

    private final UserAccountRepository users;
    private final UserNotificationEmailFallback notificationEmailFallback;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (notificationEmailFallback.describeFallbackForUi().isBlank()) {
            return;
        }
        int n = 0;
        for (UserAccount u : users.findAll()) {
            String repaired = notificationEmailFallback.resolveStoredEmail(u.getEmail());
            String cur = u.getEmail();
            if (repaired != null && !repaired.equals(cur == null ? "" : cur.trim())) {
                u.setEmail(repaired);
                users.save(u);
                n++;
            }
        }
        if (n > 0) {
            log.info("Adjusted {} user notification e-mail(s) to match fallback rules.", n);
        }
    }
}
