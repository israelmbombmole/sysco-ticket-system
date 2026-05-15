package com.sysco.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Reflects whether Spring registered an SMTP {@link JavaMailSender} (requires non-blank {@code spring.mail.host}). */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailOutboundAvailability {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public boolean isConfigured() {
        return mailSenderProvider.getIfAvailable() != null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logMailStatus() {
        if (isConfigured()) {
            log.info("Outbound e-mail is enabled (spring.mail.host is set). Ticket notifications will be attempted.");
        } else {
            log.warn(
                    "Outbound e-mail is disabled: spring.mail.host is blank. Set SPRING_MAIL_HOST (and credentials) to deliver ticket e-mails.");
        }
    }
}
