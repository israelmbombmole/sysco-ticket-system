package com.sysco.web.service;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginAuditService {

    private final UserAccountRepository users;

    private final AtomicLong seq = new AtomicLong(0);
    private final CopyOnWriteArrayList<AuditRow> rows = new CopyOnWriteArrayList<>();

    @PostConstruct
    void seedAfterInject() {
        seed();
    }

    public AuditPage page(String action, LocalDate from, LocalDate to, String detailsQ, DirectionAuditScope scope) {
        String a = action == null ? "" : action.trim();
        String q = detailsQ == null ? "" : detailsQ.trim().toLowerCase(Locale.ROOT);
        LocalDate safeFrom = from;
        LocalDate safeTo = to;
        if (safeFrom != null && safeTo != null && safeTo.isBefore(safeFrom)) {
            LocalDate tmp = safeFrom;
            safeFrom = safeTo;
            safeTo = tmp;
        }
        final LocalDate filterFrom = safeFrom;
        final LocalDate filterTo = safeTo;
        Map<String, Long> dirByUsername = scope.unrestricted() ? Map.of() : directionByUsernameLower();
        boolean viewingAllDirections = scope.unrestricted();
        List<AuditRow> filtered = rows.stream()
                .filter(r -> a.isBlank() || r.action().equalsIgnoreCase(a))
                .filter(r -> filterFrom == null || !r.when().toLocalDate().isBefore(filterFrom))
                .filter(r -> filterTo == null || !r.when().toLocalDate().isAfter(filterTo))
                .filter(r -> q.isBlank() || contains(r.details(), q) || contains(r.user(), q) || contains(r.entity(), q))
                .filter(r -> matchesScope(r, scope, dirByUsername))
                .sorted(Comparator.comparing(AuditRow::when).reversed())
                .toList();
        return new AuditPage(filtered, a, safeFrom, safeTo, detailsQ == null ? "" : detailsQ, viewingAllDirections);
    }

    public byte[] exportCsv(String action, LocalDate from, LocalDate to, String detailsQ, DirectionAuditScope scope) {
        AuditPage p = page(action, from, to, detailsQ, scope);
        StringBuilder sb = new StringBuilder();
        sb.append("user,action,entity,details,when\n");
        for (AuditRow r : p.rows()) {
            sb.append(csv(r.user())).append(',')
                    .append(csv(r.action())).append(',')
                    .append(csv(r.entity())).append(',')
                    .append(csv(r.details())).append(',')
                    .append(csv(r.whenFmt())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public void record(String user, String action, String entity, String details) {
        add(user == null || user.isBlank() ? "unknown" : user, action, entity, details, LocalDateTime.now());
    }

    /** Connexion / login style events in the in-memory audit log (periode [start, endExcl)). */
    public long countConnectionLikeEventsBetween(Instant rangeStart, Instant rangeEndExcl) {
        if (rangeStart == null || rangeEndExcl == null) {
            return 0;
        }
        ZoneId z = ZoneId.systemDefault();
        return rows.stream()
                .filter(r -> r.when() != null)
                .filter(r -> {
                    Instant iw = r.when().atZone(z).toInstant();
                    return !iw.isBefore(rangeStart) && iw.isBefore(rangeEndExcl);
                })
                .filter(r -> isLoginLike(r.action()))
                .count();
    }

    private static boolean isLoginLike(String action) {
        if (action == null) {
            return false;
        }
        String a = action.toUpperCase(Locale.ROOT);
        return a.contains("CONNEXION") || a.contains("CONNECT") || "LOGIN".equals(a);
    }

    private static String csv(String s) {
        String v = s == null ? "" : s;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
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

    private static boolean matchesScope(AuditRow r, DirectionAuditScope scope, Map<String, Long> dirByUsername) {
        if (scope.unrestricted()) {
            return true;
        }
        if (scope.directionId() == null) {
            return false;
        }
        String key = r.user() == null ? "" : r.user().trim().toLowerCase(Locale.ROOT);
        return Objects.equals(scope.directionId(), dirByUsername.get(key));
    }

    private void seed() {
        add("admin", "CONNEXION", "SYSTÈME", "Utilisateur connecté", LocalDateTime.now().minusMinutes(8));
        add("israel", "CONNEXION", "SYSTÈME", "Utilisateur connecté", LocalDateTime.now().minusHours(2));
        add("jordan-paul", "CONNEXION", "SYSTÈME", "Utilisateur connecté", LocalDateTime.now().minusHours(3));
        add("queen", "DÉCONNEXION", "SYSTÈME", "Utilisateur déconnecté", LocalDateTime.now().minusHours(4));
        add("deo", "CONNEXION", "SYSTÈME", "Utilisateur connecté", LocalDateTime.now().minusHours(5));
    }

    private void add(String user, String action, String entity, String details, LocalDateTime when) {
        rows.add(new AuditRow(seq.incrementAndGet(), user, action, entity, details, when));
    }

    public record AuditPage(
            List<AuditRow> rows,
            String action,
            LocalDate from,
            LocalDate to,
            String detailsQ,
            boolean viewingAllDirections) {}

    public record AuditRow(long id, String user, String action, String entity, String details, LocalDateTime when) {
        public String whenFmt() {
            return com.sysco.web.util.DisplayDateFormatter.formatLocalDateTime(when);
        }
    }
}
