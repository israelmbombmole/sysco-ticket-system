package com.sysco.web.service;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.util.DisplayDateFormatter;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileShareAuditService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private final UserAccountRepository users;

    private final AtomicLong seq = new AtomicLong(0);
    private final CopyOnWriteArrayList<AuditEvent> events = new CopyOnWriteArrayList<>();

    @PostConstruct
    void seedAfterInject() {
        seed();
    }

    /**
     * Scoped viewers see events where {@link AuditEvent#sharedBy()} or {@link AuditEvent#recipient()} belongs to
     * {@link DirectionAuditScope#directionId()}; SUPER_ADMIN sees everything.
     */
    public AuditPage page(String q, DirectionAuditScope scope) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Map<String, Long> dirByUsername = scope.unrestricted() ? Map.of() : directionByUsernameLower();
        boolean viewingAllDirections = scope.unrestricted();
        List<AuditEvent> rows = events.stream()
                .filter(e -> query.isBlank()
                        || e.fileName().toLowerCase(Locale.ROOT).contains(query)
                        || e.sharedBy().toLowerCase(Locale.ROOT).contains(query)
                        || e.recipient().toLowerCase(Locale.ROOT).contains(query)
                        || e.action().toLowerCase(Locale.ROOT).contains(query)
                        || e.timestamp().toLowerCase(Locale.ROOT).contains(query))
                .filter(e -> matchesScope(e, scope, dirByUsername))
                .sorted(Comparator.comparing(AuditEvent::at).reversed())
                .toList();
        return new AuditPage(rows, q == null ? "" : q, viewingAllDirections);
    }

    public void recordShareEvent(String fileName, String sharedBy, String recipient) {
        add(fileName, sharedBy, recipient, "Partagé", LocalDateTime.now());
    }

    public void recordPreviewEvent(String fileName, String sharedBy, String recipient) {
        add(fileName, sharedBy, recipient, "Aperçu", LocalDateTime.now());
    }

    public void recordDownloadEvent(String fileName, String sharedBy, String recipient) {
        add(fileName, sharedBy, recipient, "Téléchargé", LocalDateTime.now());
    }

    public byte[] exportCsv(String q, DirectionAuditScope scope) {
        AuditPage page = page(q, scope);
        StringBuilder sb = new StringBuilder();
        sb.append("file,shared_by,recipient,action,date,time\n");
        for (AuditEvent e : page.rows()) {
            sb.append(csv(e.fileName())).append(',')
                    .append(csv(e.sharedBy())).append(',')
                    .append(csv(e.recipient())).append(',')
                    .append(csv(e.action())).append(',')
                    .append(csv(e.date())).append(',')
                    .append(csv(e.time())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private Map<String, Long> directionByUsernameLower() {
        Map<String, Long> m = new HashMap<>();
        for (UserAccount u : users.findAll()) {
            if (u.getUsername() == null || u.getUsername().isBlank()) {
                continue;
            }
            m.put(u.getUsername().trim().toLowerCase(Locale.ROOT), u.getDirectionId());
        }
        return m;
    }

    private static boolean matchesScope(AuditEvent e, DirectionAuditScope scope, Map<String, Long> dirByUsername) {
        if (scope.unrestricted()) {
            return true;
        }
        if (scope.directionId() == null) {
            return false;
        }
        Long ds = dirByUsername.get(usernameKey(e.sharedBy()));
        Long dr = dirByUsername.get(usernameKey(e.recipient()));
        return Objects.equals(scope.directionId(), ds) || Objects.equals(scope.directionId(), dr);
    }

    private static String usernameKey(String u) {
        return u == null ? "" : u.trim().toLowerCase(Locale.ROOT);
    }

    private void seed() {
        add("Invoice-S3DLJRT-0002.pdf", "israel", "jordan-paul", "Partagé", LocalDateTime.now().minusMinutes(45));
        add("Plan-de-mission-avril.docx", "admin", "ben", "Téléchargé", LocalDateTime.now().minusMinutes(30));
        add("rapport-hebdo.xlsx", "queen", "deo", "Partagé", LocalDateTime.now().minusMinutes(12));
    }

    private void add(String file, String by, String recipient, String action, LocalDateTime at) {
        events.add(new AuditEvent(seq.incrementAndGet(), file, by, recipient, action, at));
    }

    public record AuditPage(List<AuditEvent> rows, String q, boolean viewingAllDirections) {}

    public record AuditEvent(
            long id, String fileName, String sharedBy, String recipient, String action, LocalDateTime at) {
        public String date() {
            return DisplayDateFormatter.formatLocalDate(at.toLocalDate());
        }

        public String time() {
            return TIME_FMT.format(at);
        }

        public String timestamp() {
            return DisplayDateFormatter.formatLocalDateTime(at);
        }
    }
}
