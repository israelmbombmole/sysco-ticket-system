package com.sysco.web.service;

import com.sysco.web.domain.UserAccount;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the inbox used for ticket e-mails when a profile has no address or only a non-deliverable placeholder.
 */
@Component
public class UserNotificationEmailFallback {

    @Value("${sysco.mail.default-notify-email:}")
    private String configuredDefaultNotifyEmail;

    @Value("${sysco.mail.from:noreply@localhost}")
    private String mailFrom;

    /** Same logic as stored resolution but safe when {@code user} is null (returns fallback only). */
    public String resolveNotificationEmail(UserAccount user) {
        if (user == null) {
            return fallbackOnlyOrNull();
        }
        return resolveNotificationEmail(user.getEmail());
    }

    /**
     * Use explicit inbox when valid; otherwise {@code sysco.mail.default-notify-email}, then a non-placeholder
     * {@code sysco.mail.from}.
     */
    public String resolveNotificationEmail(String explicitEmail) {
        String trimmed = explicitEmail == null ? "" : explicitEmail.trim();
        if (!trimmed.isBlank() && !isUndeliverablePlaceholderAddress(trimmed)) {
            return trimmed;
        }
        return fallbackOnlyOrNull();
    }

    /** Value persisted on {@link UserAccount#setEmail}; substitutes placeholders and blank with fallback when configured. */
    public String resolveStoredEmail(String explicitEmail) {
        String trimmed = explicitEmail == null ? "" : explicitEmail.trim();
        if (!trimmed.isBlank() && !isUndeliverablePlaceholderAddress(trimmed)) {
            return trimmed;
        }
        return fallbackOnlyOrNull();
    }

    /** Short hint for admin UI (empty when nothing configured). */
    public String describeFallbackForUi() {
        String f = fallbackOnlyOrNull();
        return f == null ? "" : f;
    }

    private String fallbackOnlyOrNull() {
        String d = configuredDefaultNotifyEmail == null ? "" : configuredDefaultNotifyEmail.trim();
        if (!d.isBlank()) {
            return d;
        }
        String f = mailFrom == null ? "" : mailFrom.trim();
        if (!f.isBlank() && !isPlaceholderFromAddress(f)) {
            return f;
        }
        return null;
    }

    private static boolean isPlaceholderFromAddress(String from) {
        return "noreply@localhost".equalsIgnoreCase(from.trim());
    }

    private static boolean isUndeliverablePlaceholderAddress(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        int at = email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        return domain.endsWith(".local") || domain.equals("localhost") || domain.endsWith(".invalid");
    }
}
