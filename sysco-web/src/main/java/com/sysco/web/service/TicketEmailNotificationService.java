package com.sysco.web.service;

import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.security.RoleKeys;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketEmailNotificationService {

    private static final Locale FR = Locale.FRENCH;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MessageSource messageSource;
    private final UserNotificationEmailFallback notificationEmailFallback;

    @Value("${sysco.mail.from:noreply@localhost}")
    private String fromAddress;

    @Value("${sysco.mail.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    /** Shared inbox notified when a new ticket is logged for a direction (external wizard or internal data entry). */
    @Value("${sysco.mail.direction-new-ticket-inbox:}")
    private String directionNewTicketInbox;

    private String tr(String code, Object... args) {
        return messageSource.getMessage(code, args, FR);
    }

    private static String hashRef(String ticketNumber) {
        if (ticketNumber == null || ticketNumber.isBlank()) {
            return "";
        }
        String t = ticketNumber.trim();
        return t.startsWith("#") ? t : "#" + t;
    }

    private static String canonicalTicketRef(Ticket ticket) {
        if (ticket == null) {
            return "";
        }
        if (ticket.getTicketNumber() != null && !ticket.getTicketNumber().isBlank()) {
            return ticket.getTicketNumber().trim();
        }
        int year = java.time.Year.now().getValue();
        if (ticket.getCreatedAt() != null) {
            year =
                    java.time.LocalDateTime.ofInstant(
                                    ticket.getCreatedAt(), java.time.ZoneId.systemDefault())
                            .getYear();
        }
        return "TCK-" + year + "-" + String.format("%05d", ticket.getId());
    }

    public void sendTicketCreatedToRequester(UserAccount creator, Ticket ticket, String presetLabel) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String to = notificationEmailFallback.resolveNotificationEmail(creator);
        if (sender == null || to == null || to.isBlank()) {
            return;
        }
        String ref = hashRef(canonicalTicketRef(ticket));
        String subject = tr("mail.ticket.created.requester.subject", ref);
        String body =
                tr(
                        "mail.ticket.created.requester.body",
                        creator.getUsername(),
                        ref,
                        nz(presetLabel),
                        nz(ticket.getTitle()),
                        appLink("/app/my-activity"));
        send(sender, to.trim(), subject, body);
    }

    /**
     * “Inbound” alert for the handling direction: one message to the configured direction inbox (default shared
     * ticketing mailbox). External flow names the reporter direction; internal flow names the creating agent only.
     */
    public void sendDirectionInboundNewTicket(
            Ticket ticket,
            UserAccount reporter,
            boolean external,
            String handlingDirectionName,
            String reporterDirectionName) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String to = directionInboundRecipient();
        if (sender == null || to == null || to.isBlank()) {
            return;
        }
        String ref = hashRef(canonicalTicketRef(ticket));
        String handling = nz(handlingDirectionName);
        String reporterDir = nz(reporterDirectionName);
        String reporterLine = reporter == null ? "-" : reporterLine(reporter);
        String subject = tr("mail.ticket.created.directionInbound.subject", ref);
        String body =
                external
                        ? tr(
                                "mail.ticket.created.directionInbound.body.external",
                                ref,
                                handling,
                                reporterDir.isBlank() ? tr("mail.ticket.created.directionInbound.originUnknown") : reporterDir,
                                reporterLine,
                                nz(ticket.getTitle()),
                                appLink("/app/ticket-management"))
                        : tr(
                                "mail.ticket.created.directionInbound.body.internal",
                                ref,
                                handling,
                                reporterLine,
                                nz(ticket.getTitle()),
                                appLink("/app/ticket-management"));
        send(sender, to.trim(), subject, body);
    }

    private String directionInboundRecipient() {
        String v = directionNewTicketInbox == null ? "" : directionNewTicketInbox.trim();
        if (!v.isBlank()) {
            return v;
        }
        String fb = notificationEmailFallback.describeFallbackForUi();
        return fb == null ? "" : fb.trim();
    }

    private static String reporterLine(UserAccount reporter) {
        String u = nz(reporter.getUsername());
        String em = reporter.getEmail() == null ? "" : reporter.getEmail().trim();
        if (em.isBlank()) {
            return u.isBlank() ? "-" : u;
        }
        return u.isBlank() ? em : u + " — " + em;
    }

    public void sendTicketCreatedToSousDirecteur(UserAccount sousDirecteur, Ticket ticket, UserAccount reporter) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String to = notificationEmailFallback.resolveNotificationEmail(sousDirecteur);
        if (sender == null || to == null || to.isBlank()) {
            return;
        }
        String ref = hashRef(canonicalTicketRef(ticket));
        String subject = tr("mail.ticket.created.sd.subject", ref);
        String reporterLine =
                reporter == null ? "-" : (nz(reporter.getUsername()) + " — " + nz(reporter.getEmail()));
        String body =
                tr(
                        "mail.ticket.created.sd.body",
                        sousDirecteur.getUsername(),
                        ref,
                        reporterLine,
                        nz(ticket.getTitle()),
                        nz(ticket.getReportingOffice()),
                        appLink("/app/ticket-management"));
        send(sender, to.trim(), subject, body);
    }

    public void sendTicketClosedToRequester(UserAccount creator, Ticket ticket, UserAccount closedByUser) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String to = notificationEmailFallback.resolveNotificationEmail(creator);
        if (sender == null || to == null || to.isBlank()) {
            return;
        }
        String ref = hashRef(canonicalTicketRef(ticket));
        String closerLine =
                closedByUser == null
                        ? tr("mail.ticket.closed.closer.unknown")
                        : tr(
                                "mail.ticket.closed.closer.line",
                                nz(closedByUser.getUsername()),
                                nz(RoleKeys.normalizeForScope(closedByUser.getRole())));
        String subject = tr("mail.ticket.closed.requester.subject", ref);
        String body =
                tr(
                        "mail.ticket.closed.requester.body",
                        creator.getUsername(),
                        ref,
                        nz(ticket.getTitle()),
                        appLink("/app/my-activity"),
                        appLink("/login"),
                        appLink("/app/ticket-management/" + ticket.getId()),
                        closerLine);
        send(sender, to.trim(), subject, body);
    }

    /**
     * Advises senior staff (strictly above the closing officer, with ticket access) that they may sign in and reopen
     * the dossier from Ticket management.
     */
    public void sendTicketClosedSupervisorMayReopen(UserAccount recipient, Ticket ticket, UserAccount closedByUser) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String to = notificationEmailFallback.resolveNotificationEmail(recipient);
        if (sender == null || to == null || to.isBlank() || closedByUser == null) {
            return;
        }
        String ref = hashRef(canonicalTicketRef(ticket));
        String subject = tr("mail.ticket.closed.supervisor.subject", ref);
        String body =
                tr(
                        "mail.ticket.closed.supervisor.body",
                        recipient.getUsername(),
                        ref,
                        nz(closedByUser.getUsername()),
                        nz(RoleKeys.normalizeForScope(closedByUser.getRole())),
                        nz(ticket.getTitle()),
                        appLink("/login"),
                        appLink("/app/ticket-management/" + ticket.getId()));
        send(sender, to.trim(), subject, body);
    }

    private String appLink(String path) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String p = path == null ? "" : path;
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return base + p;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private void send(JavaMailSender sender, String to, String subject, String body) {
        try {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setFrom(fromAddress);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(body, false);
            sender.send(msg);
        } catch (Exception e) {
            log.warn("Ticket e-mail could not be sent to {}: {}", to, e.getMessage());
        }
    }
}
