package com.sysco.web.service;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.Ticket;
import com.sysco.web.repo.DepartmentRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import com.sysco.web.web.dto.TicketRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DataEntryService {

    public static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "pdf", "docx", "xlsx", "txt");

    private final TicketRepository ticketRepository;
    private final DepartmentRepository departmentRepository;
    private final DirectionRepository directionRepository;
    private final UserAccountRepository userAccountRepository;
    private final TicketTimelineService ticketTimeline;
    private final DirectionScopeService directionScopeService;
    private final NotificationService notificationService;
    private final TicketEmailNotificationService ticketEmailNotificationService;
    private final MessageSource messageSource;

    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    @Value("${sysco.data-entry.audit-csv:data/data_entry_audit.csv}")
    private String auditCsvPath;

    @Transactional
    /** @return canonical ticket reference e.g. {@code TCK-2026-00008} */
    public String submit(String expediteur, String objet, String priority, List<MultipartFile> files, String username)
            throws IOException {

        String title = expediteur == null ? "" : expediteur.trim();
        String description = objet == null ? "" : objet.trim();
        String pri = normalizePriority(priority);

        if (title.isEmpty() || description.isEmpty()) {
            throw new IllegalArgumentException("requiredFields");
        }
        if (files != null && files.size() > 20) {
            throw new IllegalArgumentException("tooManyFiles");
        }
        validateAttachmentTypes(files);

        long deptId =
                departmentRepository.findAll().stream()
                        .findFirst()
                        .map(d -> d.getId())
                        .orElseThrow(() -> new IllegalArgumentException("noDepartment"));

        var user = userAccountRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("user"));

        Instant now = Instant.now();
        Ticket t = new Ticket();
        t.setTitle(title);
        t.setDescription(description);
        t.setPriority(pri);
        t.setStatus("OPEN");
        t.setTicketType("INTERNAL");
        t.setDepartmentId(deptId);
        t.setCreatedBy(user.getId());
        t.setUpdatedBy(user.getId());
        t.setCreatedAt(now);
        t.setUpdatedAt(now);

        Ticket saved = ticketRepository.save(t);
        String ticketNumber = "TCK-" + Year.now().getValue() + "-" + String.format("%05d", saved.getId());
        saved.setTicketNumber(ticketNumber);
        ticketRepository.save(saved);
        ticketTimeline.log(
                "CREATED", saved.getId(), username, null, "Ticket cr\u00e9\u00e9 depuis la saisie des donn\u00e9es");

        notificationService.notifyReporterSousDirecteursTicketLogged(
                user.getId(),
                username,
                directionScopeService.effectiveSousDirectionId(user),
                saved.getId(),
                ticketNumber);

        String categoryInternal =
                messageSource.getMessage("mail.ticket.created.category.internal", null, Locale.FRENCH);
        ticketEmailNotificationService.sendTicketCreatedToRequester(user, saved, categoryInternal);

        Long handlingDirId = user.getDirectionId();
        String handlingDirectionLabel =
                handlingDirId == null
                        ? "—"
                        : directionRepository
                                .findById(handlingDirId)
                                .map(Direction::getName)
                                .filter(s -> !s.isBlank())
                                .orElse("—");
        ticketEmailNotificationService.sendDirectionInboundNewTicket(
                saved, user, false, handlingDirectionLabel, null);

        Path uploadRoot = Path.of(uploadsDirectory).toAbsolutePath().normalize();
        Path ticketDir = uploadRoot.resolve("ticket-" + saved.getId());
        Files.createDirectories(ticketDir);

        if (files != null) {
            int n = 0;
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) {
                    continue;
                }
                n++;
                if (n > 20) {
                    break;
                }
                String original = f.getOriginalFilename();
                String safe = original == null || original.isBlank() ? "file-" + n : stripPath(original);
                Path dest = ticketDir.resolve(System.currentTimeMillis() + "_" + safe);
                Files.copy(f.getInputStream(), dest);
            }
        }

        appendCsv(LocalDate.now(), title, description, pri, saved.getId(), ticketNumber);

        return ticketNumber;
    }

    @Transactional(readOnly = true)
    public List<TicketRow> recentEntriesForUser(String username, int limit) {
        var user = userAccountRepository
                .findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("user"));
        int safeLimit = Math.max(1, Math.min(limit, 30));
        return ticketRepository.loadRecentTicketRowsByCreator(user.getId(), PageRequest.of(0, safeLimit));
    }

    private static String stripPath(String name) {
        String s = name.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static String normalizePriority(String p) {
        if (p == null || p.isBlank()) {
            return "MEDIUM";
        }
        String u = p.trim().toUpperCase(Locale.ROOT);
        return PRIORITIES.contains(u) ? u : "MEDIUM";
    }

    private static void validateAttachmentTypes(List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            String name = stripPath(f.getOriginalFilename());
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException("attachmentType");
            }
        }
    }

    private void appendCsv(
            LocalDate date, String expediteur, String objet, String priority, Long ticketId, String ticketNumber)
            throws IOException {
        Path path = Path.of(auditCsvPath).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        boolean addHeader = !Files.exists(path);
        String line = String.join(
                        ",",
                        csvEscape(date.toString()),
                        csvEscape(expediteur),
                        csvEscape(objet),
                        csvEscape(priority),
                        csvEscape(String.valueOf(ticketId)),
                        csvEscape(ticketNumber))
                + "\n";
        synchronized (DataEntryService.class) {
            if (addHeader) {
                Files.writeString(
                        path,
                        "date_enregistrement,expediteur,objet,priority,ticket_id,ticket_number\n",
                        StandardCharsets.UTF_8);
            }
            Files.writeString(path, line, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "\"\"";
        }
        String x = s.replace("\"", "\"\"");
        return "\"" + x + "\"";
    }
}
