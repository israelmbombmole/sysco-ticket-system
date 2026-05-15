package com.sysco.web.service;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.Ticket;
import com.sysco.web.repo.DepartmentRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.tickets.TicketIssuePresets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CreateTicketService {

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "docx", "xlsx", "txt");
    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    /**
     * Official DGDA “direction” labels (same order as organisation seed). Stored normalized so filtering matches
     * {@link #normalizeDirectionName(String)} output (accent-stripped, curly apostrophes unified).
     */
    private static final List<String> DGDA_TOP_DIRECTION_LABELS =
            List.of(
                    "Direction de la Réglementation et de la Facilitation",
                    "Direction de la Lutte contre la Fraude",
                    "Direction du Tarif et des Règles d'Origine",
                    "Direction de la Valeur",
                    "Direction des Autres Produits d'Accises",
                    "Direction des Huiles Minérales",
                    "Direction des Recettes du Trésor",
                    "Direction des Ressources Humaines",
                    "Direction des Équipements et de la Logistique",
                    "Direction des Statistiques, Documentation et Études Économiques",
                    "Direction des Affaires Juridiques et Contentieuses",
                    "Direction des Systèmes et Technologies d'Information",
                    "Direction de l'Audit Interne",
                    "Direction des Finances Internes",
                    "Direction des Réformes et Modernisation",
                    "Bureau de Coordination");

    private static final List<String> DGDA_SOUS_DIRECTION_ORDER_NORMALIZED =
            DGDA_TOP_DIRECTION_LABELS.stream().map(CreateTicketService::normalizeDirectionName).toList();

    private static final Set<String> CANONICAL_DGDA_DIRECTIONS = Set.copyOf(DGDA_SOUS_DIRECTION_ORDER_NORMALIZED);

    private static final Locale FR = Locale.FRENCH;

    private final TicketRepository tickets;
    private final DepartmentRepository departments;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;
    private final UserAccountRepository users;
    private final UserNotificationEmailFallback notificationEmailFallback;
    private final TicketTimelineService ticketTimeline;
    private final TicketEmailNotificationService ticketEmailNotificationService;
    private final NotificationService notificationService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    public List<IssuePresetRow> issuePresetRows() {
        return TicketIssuePresets.ORDERED_CODES.stream()
                .map(c -> new IssuePresetRow(c, "createTicket.issue." + c))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean creatorEmailMissing(String username) {
        if (username == null || username.isBlank()) {
            return true;
        }
        return users.findByUsernameIgnoreCase(username)
                .map(u -> {
                    String to = notificationEmailFallback.resolveNotificationEmail(u);
                    return to == null || to.isBlank();
                })
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public List<DeptOption> departments() {
        return departments.findAll().stream().map(d -> new DeptOption(d.getId(), d.getName())).toList();
    }

    @Transactional(readOnly = true)
    public List<SousDirectionOption> sousDirections() {
        return sousDirections.findAll().stream()
                .filter(s -> CANONICAL_DGDA_DIRECTIONS.contains(normalizeDirectionName(s.getName())))
                .sorted(
                        Comparator.comparingInt(
                                        (SousDirection s) ->
                                                dgdaSousDirectionRank(normalizeDirectionName(s.getName())))
                                .thenComparing(s -> nz(s.getName()).toLowerCase(FR)))
                .map(s -> new SousDirectionOption(s.getId(), s.getName()))
                .toList();
    }

    private static int dgdaSousDirectionRank(String normalizedName) {
        int i = DGDA_SOUS_DIRECTION_ORDER_NORMALIZED.indexOf(normalizedName);
        return i >= 0 ? i : Integer.MAX_VALUE;
    }

    /** Directions used for handling-direction cascade (sorted). */
    @Transactional(readOnly = true)
    public List<HandlingDirectionOption> handlingDirections() {
        return directions.findAll().stream()
                .sorted(Comparator.comparing((Direction d) -> nz(d.getName()).toLowerCase(FR)))
                .map(d -> new HandlingDirectionOption(d.getId(), nz(d.getName())))
                .toList();
    }

    @Transactional(readOnly = true)
    public String directionsBySousDirectionJson() {
        Set<Long> allowedHandlingSousIds =
                sousDirections.findAll().stream()
                        .filter(s -> CANONICAL_DGDA_DIRECTIONS.contains(normalizeDirectionName(s.getName())))
                        .map(SousDirection::getId)
                        .collect(Collectors.toSet());
        Map<Long, List<Map<String, Object>>> map = new TreeMap<>();
        for (Direction d : directions.findAll()) {
            Long sdId = d.getSousDirectionId();
            if (sdId == null || !allowedHandlingSousIds.contains(sdId)) {
                continue;
            }
            map.computeIfAbsent(sdId, k -> new ArrayList<>())
                    .add(Map.of(
                            "id", d.getId(),
                            "name", d.getName() == null ? "" : d.getName()));
        }
        for (List<Map<String, Object>> rows : map.values()) {
            rows.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("name", "")).toLowerCase(FR)));
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @Transactional
    /** @return canonical ticket reference e.g. {@code TCK-2026-00008} */
    public String create(
            String summary,
            String description,
            String priority,
            Long departmentId,
            String issuePresetCode,
            Long reporterSousDirectionId,
            Long reporterDirectionId,
            String reportingOffice,
            Long handlingDirectionId,
            List<MultipartFile> files,
            String username)
            throws IOException {
        if (summary == null || summary.isBlank() || handlingDirectionId == null
                || reporterSousDirectionId == null || reporterDirectionId == null) {
            throw new IllegalArgumentException("missingFields");
        }
        if (!TicketIssuePresets.isAllowed(issuePresetCode)) {
            throw new IllegalArgumentException("badPreset");
        }
        if (files != null && files.size() > 20) {
            throw new IllegalArgumentException("tooManyFiles");
        }
        validateFileTypes(files);

        var user = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("user"));
        Long deptId = effectiveDepartmentId(departmentId);
        departments.findById(deptId).orElseThrow(() -> new IllegalArgumentException("dept"));
        validateDirectionPair(reporterSousDirectionId, reporterDirectionId);
        directions.findById(handlingDirectionId).orElseThrow(() -> new IllegalArgumentException("badHandlingDirection"));

        String presetCode = issuePresetCode.trim();
        String presetLabel = messageSource.getMessage("createTicket.issue." + presetCode, null, FR);
        String extra = description == null ? "" : description.trim();
        String composedDescription =
                TicketIssuePresets.OTHER.equals(presetCode)
                        ? extra
                        : messageSource.getMessage(
                                        "createTicket.desc.categoryPrefix",
                                        new Object[] {presetLabel},
                                        FR)
                                + (extra.isEmpty() ? "" : "\n\n" + extra);
        composedDescription =
                prependOriginDetails(
                        composedDescription,
                        reporterSousDirectionId,
                        reporterDirectionId,
                        reportingOffice);

        Ticket t = new Ticket();
        t.setTitle(summary.trim());
        t.setDescription(composedDescription);
        t.setPriority(normalizePriority(priority));
        t.setStatus("OPEN");
        t.setTicketType("EXTERNAL");
        t.setDepartmentId(deptId);
        t.setCreatedBy(user.getId());
        t.setUpdatedBy(user.getId());
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        t.setReporterDirectionId(reporterDirectionId);
        t.setReporterSousDirectionId(reporterSousDirectionId);
        t.setReportingOffice(nz(reportingOffice));
        t.setIssuePresetCode(presetCode);
        t.setHandlingDirectionId(handlingDirectionId);

        Ticket saved = tickets.save(t);
        saved.setTicketNumber("TCK-" + Year.now().getValue() + "-" + String.format("%05d", saved.getId()));
        tickets.save(saved);
        ticketTimeline.log("CREATED", saved.getId(), username, null, "Ticket cr\u00e9\u00e9 (demande externe)");

        saveFiles(saved.getId(), files);

        Long handlingSousDirectionId =
                directions.findById(handlingDirectionId).map(Direction::getSousDirectionId).orElse(null);
        List<com.sysco.web.domain.UserAccount> sdRecipients =
                users.findActiveSousDirecteursWithEmailForHandlingScope(handlingDirectionId, handlingSousDirectionId);
        for (com.sysco.web.domain.UserAccount sd : sdRecipients) {
            ticketEmailNotificationService.sendTicketCreatedToSousDirecteur(sd, saved, user);
        }
        ticketEmailNotificationService.sendTicketCreatedToRequester(user, saved, presetLabel);
        String handlingDirectionLabel =
                directions.findById(handlingDirectionId).map(Direction::getName).filter(s -> !s.isBlank()).orElse("—");
        String reporterDirectionLabel =
                directions.findById(reporterDirectionId).map(Direction::getName).filter(s -> !s.isBlank()).orElse("—");
        ticketEmailNotificationService.sendDirectionInboundNewTicket(
                saved, user, true, handlingDirectionLabel, reporterDirectionLabel);
        String inAppBody =
                "Ticket "
                        + saved.getTicketNumber()
                        + " cree par "
                        + username
                        + ". Ouvrez Gestion des tickets pour l'assigner.";
        notificationService.notifyDirectionStakeholders(
                handlingDirectionId,
                "Nouveau ticket",
                inAppBody,
                "TICKET_MOVEMENT",
                "TICKET",
                saved.getId(),
                saved.getTicketNumber());

        notificationService.notifyReporterSousDirecteursTicketLogged(
                user.getId(),
                username,
                reporterSousDirectionId,
                saved.getId(),
                saved.getTicketNumber());

        return saved.getTicketNumber();
    }

    /** When {@code departmentId} is null (UI hidden), attach the first configured department. */
    private Long effectiveDepartmentId(Long departmentId) {
        if (departmentId != null) {
            return departmentId;
        }
        return departments
                .findAll()
                .stream()
                .findFirst()
                .map(d -> d.getId())
                .orElseThrow(() -> new IllegalArgumentException("dept"));
    }

    private void saveFiles(Long ticketId, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return;
        }
        Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("create-ticket-" + ticketId);
        Files.createDirectories(dir);
        int n = 0;
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            n++;
            String original = f.getOriginalFilename() == null ? ("file-" + n) : stripPath(f.getOriginalFilename());
            Path dest = dir.resolve(System.currentTimeMillis() + "_" + original);
            Files.copy(f.getInputStream(), dest);
        }
    }

    private static String stripPath(String name) {
        String s = name.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static void validateFileTypes(List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            String name = stripPath(f.getOriginalFilename() == null ? "" : f.getOriginalFilename());
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            if (!ALLOWED_EXT.contains(ext)) {
                throw new IllegalArgumentException("badFileType");
            }
        }
    }

    private static String normalizePriority(String p) {
        if (p == null || p.isBlank()) {
            return "MEDIUM";
        }
        String u = p.trim().toUpperCase(Locale.ROOT);
        return PRIORITIES.contains(u) ? u : "MEDIUM";
    }

    /**
     * Loads a ticket the given user created and may still edit from {@code /app/create-ticket}-style form (Mon activité).
     */
    @Transactional(readOnly = true)
    public Optional<TicketEditForm> loadEditableTicket(long ticketId, String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        var user = users.findByUsernameIgnoreCase(username).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        Ticket t = tickets.findById(ticketId).orElse(null);
        if (t == null || t.getCreatedBy() == null || !t.getCreatedBy().equals(user.getId())) {
            return Optional.empty();
        }
        if (blockedEditStatus(t.getStatus())) {
            return Optional.empty();
        }

        Long handlingId = t.getHandlingDirectionId();
        Long handlingSdId = null;
        if (handlingId != null) {
            Direction hDir = directions.findById(handlingId).orElse(null);
            if (hDir != null) {
                handlingSdId = hDir.getSousDirectionId();
            }
        }
        if (handlingId == null) {
            handlingId =
                    directions.findAll().stream().findFirst().map(Direction::getId).orElse(null);
        }
        String preset =
                t.getIssuePresetCode() == null || t.getIssuePresetCode().isBlank()
                        ? TicketIssuePresets.OTHER
                        : t.getIssuePresetCode().trim();

        return Optional.of(
                new TicketEditForm(
                        t.getId(),
                        safe(t.getTitle()),
                        stripOriginFromDescription(stripCategoryPrefixFromDescription(t.getDescription(), preset)),
                        normalizePriority(t.getPriority()),
                        t.getDepartmentId(),
                        preset,
                        t.getReporterSousDirectionId(),
                        t.getReporterDirectionId(),
                        safe(t.getReportingOffice()),
                        handlingSdId,
                        handlingId));
    }

    /**
     * Removes stored category prefix for editing when preset is not OTHER (best-effort).
     */
    private String stripCategoryPrefixFromDescription(String description, String presetCode) {
        if (description == null || presetCode == null || TicketIssuePresets.OTHER.equals(presetCode.trim())) {
            return description == null ? "" : description;
        }
        try {
            String prefix =
                    messageSource.getMessage(
                            "createTicket.desc.categoryPrefix",
                            new Object[] {
                                messageSource.getMessage("createTicket.issue." + presetCode.trim(), null, FR)
                            },
                            FR);
            String d = description.trim();
            if (d.startsWith(prefix)) {
                String rest = d.substring(prefix.length()).trim();
                return rest.startsWith("\n") ? rest.substring(1).trim() : rest;
            }
        } catch (Exception ignored) {
            // fall through
        }
        return description == null ? "" : description;
    }

    private static String stripOriginFromDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String d = description.trim();
        if (!d.startsWith("Origine du ticket")) {
            return d;
        }
        int idx = d.indexOf("\n\n");
        if (idx < 0) {
            return "";
        }
        return d.substring(idx + 2).trim();
    }

    @Transactional
    public void updateTicket(
            long ticketId,
            String username,
            String summary,
            String description,
            String priority,
            Long departmentId,
            String issuePresetCode,
            Long reporterSousDirectionId,
            Long reporterDirectionId,
            String reportingOffice,
            Long handlingDirectionId,
            List<MultipartFile> newFiles)
            throws IOException {
        if (summary == null || summary.isBlank() || handlingDirectionId == null
                || reporterSousDirectionId == null || reporterDirectionId == null) {
            throw new IllegalArgumentException("missingFields");
        }
        if (!TicketIssuePresets.isAllowed(issuePresetCode)) {
            throw new IllegalArgumentException("badPreset");
        }
        if (newFiles != null && newFiles.size() > 20) {
            throw new IllegalArgumentException("tooManyFiles");
        }
        validateFileTypes(newFiles);

        var user = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("user"));
        Ticket t = tickets.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        if (t.getCreatedBy() == null || !t.getCreatedBy().equals(user.getId())) {
            throw new IllegalArgumentException("notFound");
        }
        if (blockedEditStatus(t.getStatus())) {
            throw new IllegalStateException("notEditable");
        }
        Long deptId = effectiveDepartmentId(departmentId);
        departments.findById(deptId).orElseThrow(() -> new IllegalArgumentException("dept"));
        validateDirectionPair(reporterSousDirectionId, reporterDirectionId);
        directions.findById(handlingDirectionId).orElseThrow(() -> new IllegalArgumentException("badHandlingDirection"));

        String presetCode = issuePresetCode.trim();
        String presetLabel = messageSource.getMessage("createTicket.issue." + presetCode, null, FR);
        String extra = description == null ? "" : description.trim();
        String composedDescription =
                TicketIssuePresets.OTHER.equals(presetCode)
                        ? extra
                        : messageSource.getMessage(
                                        "createTicket.desc.categoryPrefix",
                                        new Object[] {presetLabel},
                                        FR)
                                + (extra.isEmpty() ? "" : "\n\n" + extra);
        composedDescription =
                prependOriginDetails(
                        composedDescription,
                        reporterSousDirectionId,
                        reporterDirectionId,
                        reportingOffice);

        t.setTitle(summary.trim());
        t.setDescription(composedDescription);
        t.setPriority(normalizePriority(priority));
        t.setDepartmentId(deptId);
        t.setReporterDirectionId(reporterDirectionId);
        t.setReporterSousDirectionId(reporterSousDirectionId);
        t.setReportingOffice(nz(reportingOffice));
        t.setIssuePresetCode(presetCode);
        t.setHandlingDirectionId(handlingDirectionId);
        t.setUpdatedBy(user.getId());
        t.setUpdatedAt(Instant.now());
        tickets.save(t);
        ticketTimeline.log(
                "UPDATED", t.getId(), username, null, "Ticket mis \u00e0 jour depuis Mon activit\u00e9");

        saveFiles(t.getId(), newFiles);
    }

    private static boolean blockedEditStatus(String status) {
        String s = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return "MERGED".equals(s)
                || "CLOSED".equals(s)
                || "CLOSE_REQUESTED".equals(s)
                || "RESOLVED".equals(s);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String normalizeDirectionName(String s) {
        if (s == null) {
            return "";
        }
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('\u2019', '\'')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.FRENCH)
                .trim();
    }

    private void validateDirectionPair(Long sousDirectionId, Long directionId) {
        Direction dir = directions.findById(directionId).orElseThrow(() -> new IllegalArgumentException("badDirection"));
        if (dir.getSousDirectionId() == null || !dir.getSousDirectionId().equals(sousDirectionId)) {
            throw new IllegalArgumentException("directionSdMismatch");
        }
        sousDirections.findById(sousDirectionId).orElseThrow(() -> new IllegalArgumentException("badSousDirection"));
    }

    private String prependOriginDetails(String base, Long sousDirectionId, Long directionId, String office) {
        String sdName = sousDirections.findById(sousDirectionId).map(SousDirection::getName).orElse("");
        String dirName = directions.findById(directionId).map(Direction::getName).orElse("");
        String officeLabel = nz(office);
        StringBuilder origin = new StringBuilder();
        origin.append("Origine du ticket");
        if (!sdName.isBlank()) {
            origin.append("\n- Direction: ").append(sdName);
        }
        if (!dirName.isBlank()) {
            origin.append("\n- Sous-direction: ").append(dirName);
        }
        if (!officeLabel.isBlank()) {
            origin.append("\n- Bureau/Etage: ").append(officeLabel);
        }
        String body = nz(base);
        if (body.isBlank()) {
            return origin.toString();
        }
        return origin + "\n\n" + body;
    }

    public record DeptOption(Long id, String name) {}

    public record SousDirectionOption(Long id, String name) {}

    public record HandlingDirectionOption(Long id, String label) {}

    public record IssuePresetRow(String code, String msgKey) {}

    public record TicketEditForm(
            Long id,
            String title,
            String description,
            String priority,
            Long departmentId,
            String issuePresetCode,
            Long reporterSousDirectionId,
            Long reporterDirectionId,
            String reportingOffice,
            Long handlingSousDirectionId,
            Long handlingDirectionId) {}
}
