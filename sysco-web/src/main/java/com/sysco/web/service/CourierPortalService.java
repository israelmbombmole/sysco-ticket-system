package com.sysco.web.service;

import com.sysco.web.domain.CourierJourneyEvent;
import com.sysco.web.domain.CourierPacket;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.Ticket;
import com.sysco.web.domain.TicketAssignment;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.CourierJourneyEventRepository;
import com.sysco.web.repo.CourierPacketRepository;
import com.sysco.web.repo.DepartmentRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.TicketAssignmentRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.service.courier.CourierDisplayLabels;
import com.sysco.web.service.courier.CourierPacketScope;
import com.sysco.web.util.DisplayDateFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CourierPortalService {

    public static final String ST_REGISTERED = "REGISTERED";
    public static final String ST_AWAITING_DIRECTION = "AWAITING_DIRECTION";
    public static final String ST_DIRECTED = "DIRECTED";
    public static final String ST_SOUS_ASSIGNED = "SOUS_ASSIGNED";
    public static final String ST_RESOLVED = "RESOLVED";
    private static final List<String> CANONICAL_DGDA_DIRECTIONS = List.of(
            "Direction de la Réglementation et de la Facilitation",
            "Direction de la Lutte contre la Fraude",
            "Direction du Tarif et des Règles d’Origine",
            "Direction de la Valeur",
            "Direction des Autres Produits d’Accises",
            "Direction des Huiles Minérales",
            "Direction des Recettes du Trésor",
            "Direction des Ressources Humaines",
            "Direction des Équipements et de la Logistique",
            "Direction des Statistiques, Documentation et Études Économiques",
            "Direction des Affaires Juridiques et Contentieuses",
            "Direction des Systèmes et Technologies d’Information",
            "Direction de l’Audit Interne",
            "Direction des Finances Internes",
            "Direction des Réformes et Modernisation",
            "Bureau de Coordination");

    private static final String E_CREATED = "CREATED";
    private static final String E_DIRECTION = "DIRECTION_SET";
    private static final String E_SOUS = "SOUS_ROUTED";
    private static final String E_RESOLVED = "RESOLVED";
    private static final String E_TICKET = "TICKET_CREATED";
    private static final String E_ASSIGN_SOUS_DIRECTEUR = "ASSIGN_SOUS_DIRECTEUR";
    private static final String E_ASSIGN_INSPECTEUR = "ASSIGN_INSPECTEUR";
    private static final String E_ASSIGN_CONTROLEUR = "ASSIGN_CONTROLEUR";
    private static final String E_ASSIGN_VERIFICATEUR = "ASSIGN_VERIFICATEUR";

    /** Priority levels — exposed for Thymeleaf registration form. */
    public static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "pdf", "docx", "xlsx", "txt");

    private final CourierPacketRepository packets;
    private final CourierJourneyEventRepository events;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;
    private final DepartmentRepository departments;
    private final TicketRepository tickets;
    private final TicketAssignmentRepository ticketAssignments;
    private final UserAccountRepository users;
    private final DataManagementService dataManagementService;
    private final NotificationService notificationService;

    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    public CourierPortalView buildPortalView(
            UserAccount ua, Long filterDirectionId, String filterStatus, Long selectedPacketId) {

        String scopeRole = RoleKeys.listRoleKey(ua.getRole());
        String rk = RoleKeys.normalizeForScope(ua.getRole());

        Specification<CourierPacket> spec =
                CourierPacketScope.forUser(ua, scopeRole).and(statusSpecification(filterStatus));
        if (filterDirectionId != null && filterDirectionId > 0) {
            Specification<CourierPacket> dirF =
                    (root, q, cb) -> cb.equal(root.get("targetDirectionId"), filterDirectionId);
            spec = spec.and(dirF);
        }

        List<CourierPacket> list = packets.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));

        Map<Long, String> dirNames = new LinkedHashMap<>();
        for (Direction d : directions.findAll()) {
            dirNames.put(d.getId(), d.getName());
        }
        Map<Long, String> sousNames = new LinkedHashMap<>();
        for (SousDirection sd : sousDirections.findAll()) {
            sousNames.put(sd.getId(), sd.getName());
        }

        List<CourierPacketRow> rows = new ArrayList<>();
        for (CourierPacket p : list) {
            String linkedTicketRef = resolveTicketRef(p.getLinkedTicketId());
            String rawDir =
                    p.getTargetDirectionId() == null ? "" : dirNames.getOrDefault(p.getTargetDirectionId(), "?");
            String rawSous = p.getTargetSousDirectionId() == null
                    ? ""
                    : sousNames.getOrDefault(p.getTargetSousDirectionId(), "?");
            boolean sousDirAssigned = p.getAssignedSousDirecteurId() != null;
            rows.add(new CourierPacketRow(
                    p.getId(),
                    nz(p.getRefCode()),
                    nz(p.getTitle()),
                    nz(p.getSender()),
                    nz(p.getPriority()),
                    nz(p.getStatus()),
                    CourierDisplayLabels.directionColumnLabel(rawDir),
                    CourierDisplayLabels.sousColumnLabel(sousDirAssigned, rawDir, rawSous),
                    linkedTicketRef,
                    formatInstant(p.getCreatedAt())));
        }

        Optional<CourierPacket> selected = Optional.empty();
        if (selectedPacketId != null) {
            selected = packets.findById(selectedPacketId);
            if (selected.isPresent() && !mayViewPacket(selected.get(), ua, scopeRole)) {
                selected = Optional.empty();
            }
        }

        CourierPacketDetail detail = null;
        List<String> journeyLines = List.of();
        boolean canSetDirection = false;
        boolean canSetSous = false;
        boolean canResolve = false;
        boolean canAssignSousDirecteur = false;
        boolean canGrantSecretaireAssign = false;
        boolean secretaireAssignGranted = false;
        List<SousOption> sousChoices = List.of();
        List<UserPick> sousDirecteurChoices = List.of();

        if (selected.isPresent()) {
            CourierPacket p = selected.get();
            String rawDir =
                    p.getTargetDirectionId() == null ? "" : dirNames.getOrDefault(p.getTargetDirectionId(), "");
            String rawSous = p.getTargetSousDirectionId() == null
                    ? ""
                    : sousNames.getOrDefault(p.getTargetSousDirectionId(), "");
            boolean sousDirAssigned = p.getAssignedSousDirecteurId() != null;
            detail = new CourierPacketDetail(
                    p.getId(),
                    nz(p.getRefCode()),
                    nz(p.getTitle()),
                    nz(p.getDescription()),
                    nz(p.getSender()),
                    nz(p.getPriority()),
                    nz(p.getStatus()),
                    p.getTargetDirectionId(),
                    CourierDisplayLabels.directionColumnLabel(rawDir),
                    p.getTargetSousDirectionId(),
                    CourierDisplayLabels.sousColumnLabel(sousDirAssigned, rawDir, rawSous),
                    resolveTicketRef(p.getLinkedTicketId()),
                    nz(p.getAttachmentPath()));

            journeyLines = loadJourneyLines(p.getId());

            canSetDirection = maySetDirection(p, ua, rk);
            canSetSous = maySetSous(p, ua, rk);
            canResolve = mayViewPacket(p, ua, scopeRole) && !ST_RESOLVED.equalsIgnoreCase(nz(p.getStatus()));
            canAssignSousDirecteur = mayAssignSousDirecteur(p, ua, rk);
            canGrantSecretaireAssign = mayGrantSecretaireAssignSousDirecteur(p, ua, rk);
            secretaireAssignGranted = p.getSecretaireCanRouteSous() != null && p.getSecretaireCanRouteSous() == 1;

            if (p.getTargetDirectionId() != null) {
                sousChoices = sousChoicesForDirection(p.getTargetDirectionId());
            }
            sousDirecteurChoices = assignableUsersForRole("SOUS-DIRECTEUR", p.getTargetDirectionId());
        }

        boolean showRegister = Set.of("COURIER", "ADMIN", "SUPER_ADMIN", "DIRECTEUR", "SECRETAIRE").contains(rk);
        boolean showFilterBar = Set.of("COURIER", "SECRETAIRE", "DIRECTEUR", "ADMIN", "SUPER_ADMIN").contains(rk);

        return new CourierPortalView(
                rows,
                directionOptionsFor(ua, rk),
                sousChoices,
                selected.map(CourierPacket::getId).orElse(null),
                detail,
                journeyLines,
                showRegister,
                showFilterBar,
                canSetDirection,
                canSetSous,
                canResolve,
                canAssignSousDirecteur,
                canGrantSecretaireAssign,
                secretaireAssignGranted,
                sousDirecteurChoices,
                rk);
    }

    private List<UserPick> assignableUsersForRole(String normalizedRole, Long directionId) {
        return users.findAll().stream()
                .filter(u -> u.getActive() != null && u.getActive() == 1)
                .filter(u -> normalizedRole.equals(RoleKeys.normalizeForScope(u.getRole())))
                .filter(u -> directionId == null || u.getDirectionId() == null || directionId.equals(u.getDirectionId()))
                .map(u -> new UserPick(u.getId(), u.getUsername()))
                .toList();
    }

    private List<SousOption> sousChoicesForDirection(long directionId) {
        Optional<Direction> d = directions.findById(directionId);
        if (d.isEmpty()) {
            return List.of();
        }
        Long linked = d.get().getSousDirectionId();
        if (linked != null) {
            return sousDirections
                    .findById(linked)
                    .map(sd -> List.of(new SousOption(sd.getId(), sd.getName())))
                    .orElse(List.of());
        }
        return sousDirections.findAll().stream()
                .map(sd -> new SousOption(sd.getId(), sd.getName()))
                .toList();
    }

    private List<DirectionOption> directionOptionsFor(UserAccount ua, String rk) {
        List<DirectionOption> opts = new ArrayList<>();
        opts.add(new DirectionOption(null, ""));
        List<Direction> all = directions.findAll();
        List<DirectionOption> canonical = canonicalDirectionOptions(all);
        if ("DIRECTEUR".equals(rk) || "SECRETAIRE".equals(rk) || "SOUS-DIRECTEUR".equals(rk) || "INSPECTEUR".equals(rk)) {
            if (ua.getDirectionId() == null) {
                return opts;
            }
            String actorCanon = all.stream()
                    .filter(d -> ua.getDirectionId().equals(d.getId()))
                    .map(Direction::getName)
                    .map(CourierPortalService::canonicalBaseName)
                    .findFirst()
                    .orElse("");
            canonical = canonical.stream()
                    .filter(o -> ua.getDirectionId().equals(o.id()) || canonicalBaseName(o.label()).equals(actorCanon))
                    .toList();
        }
        opts.addAll(canonical);
        return opts;
    }

    private List<DirectionOption> canonicalDirectionOptions(List<Direction> allDirections) {
        List<DirectionOption> out = new ArrayList<>();
        for (String canonicalLabel : CANONICAL_DGDA_DIRECTIONS) {
            Direction chosen = chooseDirectionForCanonical(allDirections, canonicalLabel);
            if (chosen != null) {
                out.add(new DirectionOption(chosen.getId(), canonicalLabel));
            }
        }
        return out;
    }

    private Direction chooseDirectionForCanonical(List<Direction> allDirections, String canonicalName) {
        String canon = normalizeDirectionName(canonicalName);
        Direction exact = allDirections.stream()
                .filter(d -> normalizeDirectionName(d.getName()).equals(canon))
                .findFirst()
                .orElse(null);
        if (exact != null) {
            return exact;
        }
        Direction ensemble = allDirections.stream()
                .filter(d -> normalizeDirectionName(d.getName()).startsWith(canon + " — ensemble direction"))
                .findFirst()
                .orElse(null);
        if (ensemble != null) {
            return ensemble;
        }
        return allDirections.stream()
                .filter(d -> canonicalBaseName(d.getName()).equals(canon))
                .findFirst()
                .orElse(null);
    }

    private static String canonicalBaseName(String raw) {
        String n = normalizeDirectionName(raw);
        int idx = n.indexOf(" — ");
        return idx > 0 ? n.substring(0, idx).trim() : n;
    }

    private static String normalizeDirectionName(String s) {
        if (s == null) {
            return "";
        }
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('’', '\'')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.FRENCH)
                .trim();
    }

    private List<String> loadJourneyLines(Long packetId) {
        List<CourierJourneyEvent> evs = events.findByPacketIdOrderByIdAsc(packetId);
        List<String> lines = new ArrayList<>();
        for (CourierJourneyEvent e : evs) {
            Instant t = e.getAtTime() != null ? e.getAtTime() : Instant.now();
            String ts = DisplayDateFormatter.formatInstant(t);
            lines.add(ts + " — " + journeyLabel(e));
        }
        return lines;
    }

    private static String journeyLabel(CourierJourneyEvent e) {
        String type = e.getEventType() == null ? "" : e.getEventType();
        String note = e.getNote() == null ? "" : e.getNote();
        return switch (type) {
            case E_CREATED -> "Created";
            case E_DIRECTION -> "Direction set";
            case E_SOUS -> "Sub-direction routed";
            case E_RESOLVED -> "Resolved";
            case E_TICKET -> "Linked ticket " + note;
            case E_ASSIGN_SOUS_DIRECTEUR -> "Sous-directeur assigned: " + note;
            case E_ASSIGN_INSPECTEUR -> "Inspecteur assigned: " + note;
            case E_ASSIGN_CONTROLEUR -> "Contrôleur assigned: " + note;
            case E_ASSIGN_VERIFICATEUR -> "Vérificateur assigned: " + note;
            case "EXTRA_DIRECTION" -> "Extra direction " + (note != null && note.contains("removed") ? "(removed)" : "");
            default -> type + (note.isEmpty() ? "" : ": " + note);
        };
    }

    private static Specification<CourierPacket> statusSpecification(String filterStatus) {
        return (root, q, cb) -> {
            if (filterStatus == null || filterStatus.isBlank() || "ALL".equalsIgnoreCase(filterStatus)) {
                return cb.conjunction();
            }
            if ("OPEN".equalsIgnoreCase(filterStatus)) {
                return cb.notEqual(cb.upper(root.get("status")), ST_RESOLVED.toUpperCase(Locale.ROOT));
            }
            if ("RESOLVED".equalsIgnoreCase(filterStatus)) {
                return cb.equal(cb.upper(root.get("status")), ST_RESOLVED.toUpperCase(Locale.ROOT));
            }
            return cb.conjunction();
        };
    }

    private boolean mayViewPacket(CourierPacket p, UserAccount ua, String scopeRole) {
        Specification<CourierPacket> spec =
                CourierPacketScope.forUser(ua, scopeRole).and((root, q, cb) -> cb.equal(root.get("id"), p.getId()));
        return packets.count(spec) > 0;
    }

    private boolean mayIntake(UserAccount ua, String rk) {
        return Set.of("COURIER", "ADMIN", "SUPER_ADMIN", "DIRECTEUR", "SECRETAIRE").contains(rk);
    }

    private boolean maySetDirection(CourierPacket p, UserAccount ua, String rk) {
        if (!mayIntake(ua, rk)) {
            return false;
        }
        String st = nz(p.getStatus());
        if (!(ST_AWAITING_DIRECTION.equals(st) || ST_REGISTERED.equals(st))) {
            return false;
        }
        if (!ua.getId().equals(p.getCreatedBy()) && !"ADMIN".equals(rk) && !"SUPER_ADMIN".equals(rk)) {
            return false;
        }
        return true;
    }

    private boolean maySetSous(CourierPacket p, UserAccount ua, String rk) {
        if (!Set.of("SECRETAIRE", "ADMIN", "SUPER_ADMIN", "DIRECTEUR").contains(rk)) {
            return false;
        }
        if (p.getTargetDirectionId() == null) {
            return false;
        }
        if ("SECRETAIRE".equals(rk) && (p.getSecretaireCanRouteSous() == null || p.getSecretaireCanRouteSous() != 1)) {
            return false;
        }
        if ("DIRECTEUR".equals(rk) || "SECRETAIRE".equals(rk)) {
            return ua.getDirectionId() != null && ua.getDirectionId().equals(p.getTargetDirectionId());
        }
        return true;
    }

    private boolean mayAssignSousDirecteur(CourierPacket p, UserAccount ua, String rk) {
        if (!Set.of("SECRETAIRE", "ADMIN", "SUPER_ADMIN", "DIRECTEUR").contains(rk)) {
            return false;
        }
        if (ST_RESOLVED.equalsIgnoreCase(nz(p.getStatus()))) {
            return false;
        }
        if ("SECRETAIRE".equals(rk) && (p.getSecretaireCanRouteSous() == null || p.getSecretaireCanRouteSous() != 1)) {
            return false;
        }
        if ("ADMIN".equals(rk) || "SUPER_ADMIN".equals(rk)) {
            return true;
        }
        return ua.getDirectionId() != null
                && p.getTargetDirectionId() != null
                && ua.getDirectionId().equals(p.getTargetDirectionId());
    }

    private boolean mayGrantSecretaireAssignSousDirecteur(CourierPacket p, UserAccount ua, String rk) {
        if (!"DIRECTEUR".equals(rk)) {
            return false;
        }
        if (ua.getDirectionId() == null || p.getTargetDirectionId() == null) {
            return false;
        }
        return ua.getDirectionId().equals(p.getTargetDirectionId());
    }

    @Transactional
    public long register(
            String title,
            String description,
            String sender,
            String priority,
            String registrationDate,
            List<MultipartFile> attachmentFiles,
            UserAccount actor,
            String rk)
            throws IOException {

        if (!mayIntake(actor, rk)) {
            throw new IllegalStateException("not_allowed");
        }
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("title");
        }
        if (attachmentFiles != null && attachmentFiles.size() > 15) {
            throw new IllegalArgumentException("tooManyFiles");
        }
        validateAttachmentTypes(attachmentFiles);
        String pr = normalizePriority(priority);
        Instant now = Instant.now();

        CourierPacket p = new CourierPacket();
        p.setRefCode("PENDING");
        p.setTitle(t);
        p.setDescription(description);
        p.setStatus(ST_AWAITING_DIRECTION);
        p.setCreatedBy(actor.getId());
        p.setSender(sender);
        p.setPriority(pr);
        p.setRegistrationDate(registrationDate != null ? registrationDate : LocalDateString.today());
        p.setCreatedAt(now);
        p.setSecretaireCanRouteSous(0);

        CourierPacket saved = packets.save(p);
        int year = Year.now(ZoneId.systemDefault()).getValue();
        String ref = "CP-" + year + "-" + String.format("%05d", saved.getId());
        saved.setRefCode(ref);
        packets.save(saved);

        insertEvent(saved.getId(), E_CREATED, actor.getId(), null, null, null);

        String firstAttachmentPath = saveAttachments(saved.getId(), attachmentFiles);
        if (!firstAttachmentPath.isBlank()) {
            saved.setAttachmentPath(firstAttachmentPath);
            packets.save(saved);
        }
        return saved.getId();
    }

    @Transactional
    public void applyDirection(Long packetId, Long directionId, UserAccount actor) {
        String scopeRole = RoleKeys.listRoleKey(actor.getRole());
        String rk = RoleKeys.normalizeForScope(actor.getRole());
        CourierPacket p =
                packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayViewPacket(p, actor, scopeRole)) {
            throw new IllegalStateException("not_allowed");
        }
        if (!maySetDirection(p, actor, rk)) {
            throw new IllegalStateException("not_allowed");
        }
        Direction d = directions.findById(directionId).orElseThrow(() -> new IllegalArgumentException("direction"));
        p.setTargetDirectionId(d.getId());
        p.setStatus(ST_DIRECTED);
        packets.save(p);
        insertEvent(p.getId(), E_DIRECTION, actor.getId(), directionId, null, null);
        String ref = nz(p.getRefCode()).isBlank() ? ("CP-" + p.getId()) : p.getRefCode();
        notificationService.notifyCourierInboundToDirectionLeads(
                d.getId(),
                ref,
                CourierDisplayLabels.directionColumnLabel(d.getName()),
                p.getId(),
                ref);
    }

    @Transactional
    public void applySous(Long packetId, Long sousId, UserAccount actor) {
        String scopeRole = RoleKeys.listRoleKey(actor.getRole());
        String rk = RoleKeys.normalizeForScope(actor.getRole());
        CourierPacket p =
                packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayViewPacket(p, actor, scopeRole)) {
            throw new IllegalStateException("not_allowed");
        }
        if (!maySetSous(p, actor, rk)) {
            throw new IllegalStateException("not_allowed");
        }
        if (p.getTargetDirectionId() == null) {
            throw new IllegalStateException("direction_first");
        }
        SousDirection sd = sousDirections.findById(sousId).orElseThrow(() -> new IllegalArgumentException("sous"));
        Direction dir = directions.findById(p.getTargetDirectionId()).orElseThrow();
        Long expectedSous = dir.getSousDirectionId();
        if (expectedSous != null && !expectedSous.equals(sd.getId())) {
            throw new IllegalStateException("sous_mismatch");
        }

        p.setTargetSousDirectionId(sd.getId());
        p.setStatus(ST_SOUS_ASSIGNED);
        packets.save(p);
        insertEvent(p.getId(), E_SOUS, actor.getId(), null, sousId, null);

        if (p.getLinkedTicketId() == null) {
            long deptId = departments.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("noDepartment"))
                    .getId();
            long ticketId = createInternalTicketFromCourier(p, actor.getId(), deptId);
            if (ticketId > 0) {
                p.setLinkedTicketId(ticketId);
                packets.save(p);
                Ticket t = tickets.findById(ticketId).orElseThrow();
                insertEvent(p.getId(), E_TICKET, actor.getId(), null, null, nz(t.getTicketNumber()));
                notificationService.notifyCourierTicketCreatedForDirectionLeads(
                        p.getTargetDirectionId(),
                        p.getTargetSousDirectionId(),
                        t.getId(),
                        nz(t.getTicketNumber()),
                        nz(p.getRefCode()),
                        actor.getId());
            }
        }
    }

    @Transactional
    public void resolve(Long packetId, UserAccount actor) {
        String scopeRole = RoleKeys.listRoleKey(actor.getRole());
        CourierPacket p =
                packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayViewPacket(p, actor, scopeRole)) {
            throw new IllegalStateException("not_allowed");
        }
        if (ST_RESOLVED.equalsIgnoreCase(nz(p.getStatus()))) {
            return;
        }
        p.setStatus(ST_RESOLVED);
        p.setResolvedAt(Instant.now());
        p.setResolvedBy(actor.getId());
        packets.save(p);
        insertEvent(p.getId(), E_RESOLVED, actor.getId(), null, null, null);
        dataManagementService.appendCourierResolvedRow(
                nz(p.getRegistrationDate()),
                nz(p.getSender()),
                nz(p.getTitle()),
                resolveSousDirectionName(p.getTargetSousDirectionId()),
                nz(actor.getUsername()),
                DisplayDateFormatter.formatInstant(p.getResolvedAt()));
    }

    @Transactional
    public void assignSousDirecteur(Long packetId, Long userId, UserAccount actor) {
        assignUser(packetId, userId, actor, E_ASSIGN_SOUS_DIRECTEUR, "SOUS-DIRECTEUR");
    }

    @Transactional
    public void setSecretaireCanAssignSousDirecteur(Long packetId, boolean enabled, UserAccount actor) {
        String scopeRole = RoleKeys.listRoleKey(actor.getRole());
        String rk = RoleKeys.normalizeForScope(actor.getRole());
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayViewPacket(p, actor, scopeRole) || !mayGrantSecretaireAssignSousDirecteur(p, actor, rk)) {
            throw new IllegalStateException("not_allowed");
        }
        p.setSecretaireCanRouteSous(enabled ? 1 : 0);
        packets.save(p);
        insertEvent(packetId, "SECRETAIRE_ASSIGN_PERMISSION", actor.getId(), null, null, enabled ? "enabled" : "disabled");
    }

    @Transactional
    public void assignInspecteur(Long packetId, Long userId, UserAccount actor) {
        assignUser(packetId, userId, actor, E_ASSIGN_INSPECTEUR, "INSPECTEUR");
    }

    @Transactional
    public void assignControleur(Long packetId, Long userId, UserAccount actor) {
        assignUser(packetId, userId, actor, E_ASSIGN_CONTROLEUR, "CONTROLEUR");
    }

    @Transactional
    public void assignVerificateur(Long packetId, Long userId, UserAccount actor) {
        assignUser(packetId, userId, actor, E_ASSIGN_VERIFICATEUR, "VERIFICATEUR");
    }

    @Transactional(readOnly = true)
    public AttachmentFile attachment(Long packetId, UserAccount actor) {
        String scopeRole = RoleKeys.listRoleKey(actor.getRole());
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayViewPacket(p, actor, scopeRole)) {
            throw new IllegalStateException("not_allowed");
        }
        String path = nz(p.getAttachmentPath()).trim();
        if (path.isBlank()) {
            throw new IllegalArgumentException("attachment");
        }
        Path file = Path.of(path).toAbsolutePath().normalize();
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("attachment");
        }
        return new AttachmentFile(file, file.getFileName().toString());
    }

    private void assignUser(Long packetId, Long userId, UserAccount actor, String eventType, String expectedRole) {
        String scopeRole = RoleKeys.listRoleKey(actor.getRole());
        String rk = RoleKeys.normalizeForScope(actor.getRole());
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayViewPacket(p, actor, scopeRole) || !mayAssignSousDirecteur(p, actor, rk)) {
            throw new IllegalStateException("not_allowed");
        }
        UserAccount assignee = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("user"));
        if (!expectedRole.equals(RoleKeys.normalizeForScope(assignee.getRole()))) {
            throw new IllegalArgumentException("role_mismatch");
        }
        if (p.getTargetDirectionId() != null
                && assignee.getDirectionId() != null
                && !p.getTargetDirectionId().equals(assignee.getDirectionId())) {
            throw new IllegalStateException("not_allowed");
        }

        if (E_ASSIGN_SOUS_DIRECTEUR.equals(eventType)) {
            p.setAssignedSousDirecteurId(assignee.getId());
        } else if (E_ASSIGN_INSPECTEUR.equals(eventType)) {
            p.setAssignedInspecteurId(assignee.getId());
        } else if (E_ASSIGN_CONTROLEUR.equals(eventType)) {
            p.setAssignedControleurId(assignee.getId());
        } else if (E_ASSIGN_VERIFICATEUR.equals(eventType)) {
            p.setAssignedVerificateurId(assignee.getId());
        }
        if (!ST_RESOLVED.equalsIgnoreCase(nz(p.getStatus()))) {
            p.setStatus(ST_SOUS_ASSIGNED);
        }
        packets.save(p);
        if (E_ASSIGN_SOUS_DIRECTEUR.equals(eventType) && p.getLinkedTicketId() != null) {
            Ticket t = tickets.findById(p.getLinkedTicketId()).orElse(null);
            if (t != null) {
                t.setAssignedTo(assignee.getId());
                if (!ticketAssignments.existsByTicketIdAndUserId(t.getId(), assignee.getId())) {
                    TicketAssignment ta = new TicketAssignment();
                    ta.setTicketId(t.getId());
                    ta.setUserId(assignee.getId());
                    ta.setAssignedAt(Instant.now());
                    ticketAssignments.save(ta);
                }
                if (!"CLOSED".equalsIgnoreCase(nz(t.getStatus())) && !"RESOLVED".equalsIgnoreCase(nz(t.getStatus()))) {
                    t.setStatus("ASSIGNED");
                }
                t.setUpdatedAt(Instant.now());
                t.setUpdatedBy(actor.getId());
                tickets.save(t);
                String ticketRef =
                        t.getTicketNumber() == null || t.getTicketNumber().isBlank()
                                ? ("#" + t.getId())
                                : t.getTicketNumber().trim();
                notificationService.notifyTicketAssignedToYou(assignee.getId(), ticketRef, t.getId());
            }
        }
        insertEvent(packetId, eventType, actor.getId(), null, null, assignee.getUsername());
    }

    private long createInternalTicketFromCourier(CourierPacket p, Long createdByUserId, long departmentId) {
        Instant now = Instant.now();
        Ticket t = new Ticket();
        String title = "[Courrier] " + nz(p.getRefCode()) + " — " + nz(p.getTitle());
        String desc = nz(p.getDescription());
        if (p.getSender() != null && !p.getSender().isBlank()) {
            desc = desc.isEmpty() ? "" : desc + "\n\n";
            desc = desc + "Expéditeur: " + p.getSender();
        }
        desc = desc + "\n\n— Courrier: " + nz(p.getRefCode());
        t.setTitle(title);
        t.setDescription(desc);
        t.setPriority(nz(p.getPriority()).isEmpty() ? "MEDIUM" : p.getPriority());
        t.setStatus("OPEN");
        t.setTicketType("INTERNAL");
        t.setDepartmentId(departmentId);
        t.setAssignedTo(null);
        t.setCreatedBy(createdByUserId);
        t.setUpdatedBy(createdByUserId);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        Ticket saved = tickets.save(t);
        String num = "TCK-" + Year.now().getValue() + "-" + String.format("%05d", saved.getId());
        saved.setTicketNumber(num);
        tickets.save(saved);
        return saved.getId();
    }

    private void insertEvent(Long packetId, String type, Long actorId, Long dirId, Long sousId, String note) {
        CourierJourneyEvent e = new CourierJourneyEvent();
        e.setPacketId(packetId);
        e.setEventType(type);
        e.setAtTime(Instant.now());
        e.setActorUserId(actorId);
        e.setDirectionId(dirId);
        e.setSousDirectionId(sousId);
        e.setNote(note);
        events.save(e);
    }

    private String saveAttachments(Long packetId, List<MultipartFile> files) throws IOException {
        if (files == null) {
            return "";
        }
        Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("courier").resolve(String.valueOf(packetId));
        Files.createDirectories(dir);
        int n = 0;
        String firstStored = "";
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            n++;
            if (n > 15) {
                break;
            }
            String orig = f.getOriginalFilename();
            String safe = orig == null || orig.isBlank() ? "file-" + n : stripPath(orig);
            Path dest = dir.resolve(System.currentTimeMillis() + "_" + safe);
            Files.copy(f.getInputStream(), dest);
            if (firstStored.isBlank()) {
                firstStored = dest.toString();
            }
        }
        return firstStored;
    }

    private static void validateAttachmentTypes(List<MultipartFile> files) {
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
            if (!ALLOWED_ATTACHMENT_EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException("attachmentType");
            }
        }
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private String resolveTicketRef(Long ticketId) {
        if (ticketId == null) {
            return "";
        }
        return tickets.findById(ticketId)
                .map(t -> (t.getTicketNumber() == null || t.getTicketNumber().isBlank()) ? ("TCK-" + t.getId()) : t.getTicketNumber())
                .orElse("");
    }

    private static String formatInstant(Instant i) {
        return DisplayDateFormatter.formatInstant(i);
    }

    private String resolveSousDirectionName(Long sousDirectionId) {
        if (sousDirectionId == null) {
            return "";
        }
        return sousDirections.findById(sousDirectionId).map(SousDirection::getName).orElse("");
    }

    private static final class LocalDateString {
        static String today() {
            return java.time.LocalDate.now(ZoneId.systemDefault()).toString();
        }
    }

    public record CourierPortalView(
            List<CourierPacketRow> rows,
            List<DirectionOption> directionFilters,
            List<SousOption> sousChoicesForSelected,
            Long selectedId,
            CourierPacketDetail detail,
            List<String> journeyLines,
            boolean showRegister,
            boolean showFilterBar,
            boolean canSetDirection,
            boolean canSetSous,
            boolean canResolve,
            boolean canAssignSousDirecteur,
            boolean canGrantSecretaireAssign,
            boolean secretaireAssignGranted,
            List<UserPick> sousDirecteurChoices,
            String actorRoleKey) {}

    public record CourierPacketRow(
            Long id,
            String refCode,
            String title,
            String sender,
            String priority,
            String status,
            String directionName,
            String sousName,
            String linkedTicketRef,
            String createdAt) {}

    public record CourierPacketDetail(
            Long id,
            String refCode,
            String title,
            String description,
            String sender,
            String priority,
            String status,
            Long targetDirectionId,
            String directionName,
            Long targetSousId,
            String sousName,
            String linkedTicketRef,
            String attachmentPath) {}

    public record DirectionOption(Long id, String label) {}

    public record SousOption(Long id, String label) {}

    public record UserPick(Long id, String label) {}

    public record AttachmentFile(Path path, String fileName) {}
}
