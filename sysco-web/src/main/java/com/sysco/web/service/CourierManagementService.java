package com.sysco.web.service;

import com.sysco.web.domain.CourierJourneyEvent;
import com.sysco.web.domain.CourierPacket;
import com.sysco.web.domain.CourierPacketExtraDirection;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.CourierJourneyEventRepository;
import com.sysco.web.repo.CourierPacketExtraDirectionRepository;
import com.sysco.web.repo.CourierPacketRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.TicketRepository;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.service.courier.CourierDisplayLabels;
import com.sysco.web.service.courier.CourierPacketScope;
import com.sysco.web.util.DisplayDateFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourierManagementService {

    private static final String E_DIRECTION = "DIRECTION_SET";
    private static final String E_EXTRA_DIRECTION = "EXTRA_DIRECTION";

    private final CourierPacketRepository packets;
    private final CourierPacketExtraDirectionRepository extras;
    private final CourierJourneyEventRepository events;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;
    private final TicketRepository tickets;

    public CourierManagementPage buildPage(
            UserAccount ua, Long filterDirectionId, String filterStatus, Long selectedPacketId, String filterQuery) {

        String scopeRole = RoleKeys.listRoleKey(ua.getRole());

        Specification<CourierPacket> spec =
                CourierPacketScope.forUser(ua, scopeRole).and(statusSpecification(filterStatus));
        if (filterDirectionId != null && filterDirectionId > 0) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("targetDirectionId"), filterDirectionId));
        }
        spec = spec.and(searchSpecification(filterQuery));

        List<CourierPacket> list = packets.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));

        Map<Long, String> dirNames = new LinkedHashMap<>();
        for (Direction d : directions.findAll()) {
            dirNames.put(d.getId(), d.getName());
        }
        Map<Long, String> sousNames = new LinkedHashMap<>();
        for (SousDirection sd : sousDirections.findAll()) {
            sousNames.put(sd.getId(), sd.getName());
        }

        List<CourierMgmtRow> rows = new ArrayList<>();
        for (CourierPacket p : list) {
            String rawDir =
                    p.getTargetDirectionId() == null ? "" : dirNames.getOrDefault(p.getTargetDirectionId(), "?");
            String rawSous = p.getTargetSousDirectionId() == null
                    ? ""
                    : sousNames.getOrDefault(p.getTargetSousDirectionId(), "?");
            boolean sousDirAssigned = p.getAssignedSousDirecteurId() != null;
            rows.add(new CourierMgmtRow(
                    p.getId(),
                    nz(p.getRefCode()),
                    nz(p.getTitle()),
                    nz(p.getSender()),
                    nz(p.getPriority()),
                    nz(p.getStatus()),
                    CourierDisplayLabels.directionColumnLabel(rawDir),
                    extraNames(p.getId(), dirNames),
                    CourierDisplayLabels.sousColumnLabel(sousDirAssigned, rawDir, rawSous),
                    p.getLinkedTicketId(),
                    resolveTicketRef(p.getLinkedTicketId()),
                    formatInstant(p.getCreatedAt())));
        }

        Optional<CourierPacket> sel = Optional.empty();
        if (selectedPacketId != null) {
            sel = packets.findById(selectedPacketId);
            if (sel.isPresent() && !mayViewPacket(sel.get(), ua, scopeRole)) {
                sel = Optional.empty();
            }
        }

        CourierMgmtDetail detail = null;
        Long selId = sel.map(CourierPacket::getId).orElse(null);
        boolean canEdit = false;
        boolean canRedirect = false;
        boolean canExtra = false;
        boolean canDelete = false;
        List<DirectionPick> extraRows = List.of();
        List<DirectionPick> extraAddCandidates = List.of();
        List<JourneyStep> journeySteps = List.of();

        if (sel.isPresent()) {
            CourierPacket p = sel.get();
            List<Long> extraIds = extraDirectionIds(p.getId());
            String rawDirDetail =
                    p.getTargetDirectionId() == null ? "" : dirNames.getOrDefault(p.getTargetDirectionId(), "");
            detail = new CourierMgmtDetail(
                    p.getId(),
                    nz(p.getRefCode()),
                    nz(p.getTitle()),
                    nz(p.getDescription()),
                    nz(p.getSender()),
                    nz(p.getPriority()),
                    nz(p.getStatus()),
                    nz(p.getRegistrationDate()),
                    nz(p.getAttachmentPath()),
                    p.getTargetDirectionId(),
                    CourierDisplayLabels.directionColumnLabel(rawDirDetail),
                    extraIds);

            canEdit = mayAdminister(p, ua);
            canRedirect = canEdit;
            canExtra = canEdit;
            canDelete = canEdit;

            extraRows =
                    extraIds.stream()
                            .map(id -> new DirectionPick(id, dirNames.getOrDefault(id, "#" + id)))
                            .toList();

            Set<Long> blocked = new HashSet<>(extraIds);
            if (p.getTargetDirectionId() != null) {
                blocked.add(p.getTargetDirectionId());
            }
            extraAddCandidates = directions.findAll().stream()
                    .filter(d -> !blocked.contains(d.getId()))
                    .map(d -> new DirectionPick(d.getId(), d.getName()))
                    .toList();
            journeySteps = loadJourneySteps(p.getId(), dirNames, sousNames);
        }

        return new CourierManagementPage(
                rows,
                directionFilterOptions(),
                directions.findAll().stream().map(d -> new DirectionPick(d.getId(), d.getName())).toList(),
                filterDirectionId,
                filterStatus,
                filterQuery == null ? "" : filterQuery.trim(),
                selId,
                detail,
                canEdit,
                canRedirect,
                canExtra,
                canDelete,
                extraRows,
                extraAddCandidates,
                journeySteps);
    }

    private List<Long> extraDirectionIds(Long packetId) {
        return extras.findByPacketId(packetId).stream()
                .map(CourierPacketExtraDirection::getDirectionId)
                .sorted()
                .toList();
    }

    private String extraNames(Long packetId, Map<Long, String> dirNames) {
        return extras.findByPacketId(packetId).stream()
                .map(CourierPacketExtraDirection::getDirectionId)
                .map(id -> CourierDisplayLabels.directionColumnLabel(dirNames.getOrDefault(id, "?" + id)))
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private List<DirectionFilterOption> directionFilterOptions() {
        List<DirectionFilterOption> opts = new ArrayList<>();
        opts.add(new DirectionFilterOption(null, ""));
        for (Direction d : directions.findAll()) {
            opts.add(new DirectionFilterOption(d.getId(), d.getName()));
        }
        return opts;
    }

    private static Specification<CourierPacket> statusSpecification(String filterStatus) {
        return (root, q, cb) -> {
            if (filterStatus == null || filterStatus.isBlank() || "ALL".equalsIgnoreCase(filterStatus)) {
                return cb.conjunction();
            }
            if ("OPEN".equalsIgnoreCase(filterStatus)) {
                return cb.notEqual(
                        cb.upper(root.get("status")), CourierPortalService.ST_RESOLVED.toUpperCase(Locale.ROOT));
            }
            if ("RESOLVED".equalsIgnoreCase(filterStatus)) {
                return cb.equal(
                        cb.upper(root.get("status")), CourierPortalService.ST_RESOLVED.toUpperCase(Locale.ROOT));
            }
            return cb.conjunction();
        };
    }

    private static Specification<CourierPacket> searchSpecification(String filterQuery) {
        return (root, q, cb) -> {
            if (filterQuery == null || filterQuery.isBlank()) {
                return cb.conjunction();
            }
            String like = "%" + filterQuery.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("refCode")), like),
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("sender")), like));
        };
    }

    private boolean mayViewPacket(CourierPacket p, UserAccount ua, String scopeRole) {
        Specification<CourierPacket> spec =
                CourierPacketScope.forUser(ua, scopeRole).and((root, q, cb) -> cb.equal(root.get("id"), p.getId()));
        return packets.count(spec) > 0;
    }

    /** Mirrors {@code CourierPacketDAO#mayAdministerCourier} (without Oracle cluster / super-admin nuance). */
    public boolean mayAdminister(CourierPacket p, UserAccount ua) {
        String scopeRole = RoleKeys.listRoleKey(ua.getRole());
        if (!mayViewPacket(p, ua, scopeRole)) {
            return false;
        }
        if (ua.getId() == null || p.getCreatedBy() == null || !ua.getId().equals(p.getCreatedBy())) {
            return false;
        }
        String st = nz(p.getStatus()).toUpperCase(Locale.ROOT);
        return CourierPortalService.ST_AWAITING_DIRECTION.equals(st) || CourierPortalService.ST_REGISTERED.equals(st);
    }

    private boolean packetReachableFromDirection(CourierPacket p, long dirId) {
        if (p.getTargetDirectionId() != null && p.getTargetDirectionId().equals(dirId)) {
            return true;
        }
        return extras.existsByPacketIdAndDirectionId(p.getId(), dirId);
    }

    @Transactional
    public void adminUpdate(
            Long packetId,
            String title,
            String description,
            String sender,
            String priority,
            String registrationDate,
            String attachmentPath,
            UserAccount actor) {
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayAdminister(p, actor)) {
            throw new IllegalStateException("not_allowed");
        }
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("title");
        }
        String pr = priority == null || priority.isBlank() ? "MEDIUM" : priority.trim();
        p.setTitle(t);
        p.setDescription(description);
        p.setSender(sender);
        p.setPriority(pr);
        p.setRegistrationDate(registrationDate);
        p.setAttachmentPath(attachmentPath);
        packets.save(p);
    }

    @Transactional
    public void redirectPrimary(Long packetId, Long newDirectionId, boolean reopenWorkflow, UserAccount actor) {
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayAdminister(p, actor)) {
            throw new IllegalStateException("not_allowed");
        }
        Direction newDir = directions.findById(newDirectionId).orElseThrow(() -> new IllegalArgumentException("direction"));
        boolean wasResolved = CourierPortalService.ST_RESOLVED.equalsIgnoreCase(nz(p.getStatus()));
        boolean keepResolved = wasResolved && !reopenWorkflow;

        Long oldSous = p.getTargetSousDirectionId();
        Long newLinkedSous = newDir.getSousDirectionId();
        boolean sousValid =
                oldSous == null || (newLinkedSous != null && newLinkedSous.equals(oldSous));
        Long newSousVal = sousValid ? oldSous : null;

        if (keepResolved) {
            p.setTargetDirectionId(newDirectionId);
            p.setTargetSousDirectionId(newSousVal);
            packets.save(p);
            insertEvent(
                    packetId,
                    E_DIRECTION,
                    actor.getId(),
                    newDirectionId,
                    null,
                    "admin redirect (kept resolved)");
            return;
        }

        String newStatus = p.getStatus() != null ? p.getStatus() : CourierPortalService.ST_AWAITING_DIRECTION;
        if (wasResolved && reopenWorkflow) {
            newStatus = CourierPortalService.ST_DIRECTED;
        } else if (!sousValid
                && (CourierPortalService.ST_SOUS_ASSIGNED.equalsIgnoreCase(newStatus)
                        || "IN_PROGRESS".equalsIgnoreCase(newStatus))) {
            newStatus = CourierPortalService.ST_DIRECTED;
        }

        boolean clearResolved = wasResolved && reopenWorkflow;
        p.setTargetDirectionId(newDirectionId);
        p.setTargetSousDirectionId(newSousVal);
        p.setStatus(newStatus);
        if (clearResolved) {
            p.setResolvedAt(null);
            p.setResolvedBy(null);
        }
        packets.save(p);
        insertEvent(packetId, E_DIRECTION, actor.getId(), newDirectionId, null, "admin redirect");
    }

    @Transactional
    public void addExtraDirection(Long packetId, Long directionId, UserAccount actor) {
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayAdminister(p, actor)) {
            throw new IllegalStateException("not_allowed");
        }
        directions.findById(directionId).orElseThrow(() -> new IllegalArgumentException("direction"));
        if (p.getTargetDirectionId() != null && p.getTargetDirectionId().equals(directionId)) {
            throw new IllegalStateException("already_primary");
        }
        if (extras.existsByPacketIdAndDirectionId(packetId, directionId)) {
            return;
        }
        CourierPacketExtraDirection row = new CourierPacketExtraDirection();
        row.setPacketId(packetId);
        row.setDirectionId(directionId);
        extras.save(row);
        insertEvent(packetId, E_EXTRA_DIRECTION, actor.getId(), directionId, null, null);
    }

    @Transactional
    public void removeExtraDirection(Long packetId, Long directionId, UserAccount actor) {
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayAdminister(p, actor)) {
            throw new IllegalStateException("not_allowed");
        }
        if (!extras.existsByPacketIdAndDirectionId(packetId, directionId)) {
            throw new IllegalArgumentException("direction");
        }
        extras.deleteByPacketIdAndDirectionId(packetId, directionId);
        insertEvent(packetId, E_EXTRA_DIRECTION, actor.getId(), directionId, null, "removed");
    }

    @Transactional
    public void deletePacket(Long packetId, UserAccount actor) {
        CourierPacket p = packets.findById(packetId).orElseThrow(() -> new IllegalArgumentException("packet"));
        if (!mayAdminister(p, actor)) {
            throw new IllegalStateException("not_allowed");
        }
        packets.deleteById(packetId);
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private String resolveTicketRef(Long ticketId) {
        if (ticketId == null) {
            return "";
        }
        return tickets.findById(ticketId)
                .map(t ->
                        (t.getTicketNumber() == null || t.getTicketNumber().isBlank())
                                ? ("TCK-" + t.getId())
                                : t.getTicketNumber())
                .orElse("");
    }

    private static String formatInstant(Instant i) {
        return DisplayDateFormatter.formatInstant(i);
    }

    private List<JourneyStep> loadJourneySteps(Long packetId, Map<Long, String> dirNames, Map<Long, String> sousNames) {
        String packetDirFull = packets
                .findById(packetId)
                .filter(p -> p.getTargetDirectionId() != null)
                .map(p -> dirNames.getOrDefault(p.getTargetDirectionId(), ""))
                .orElse("");
        return events.findByPacketIdOrderByIdAsc(packetId).stream()
                .map(e -> {
                    String type = e.getEventType() == null ? "" : e.getEventType().trim();
                    String time = formatInstant(e.getAtTime());
                    String label = switch (type) {
                        case "CREATED" -> "Création du courrier";
                        case "DIRECTION_SET" -> "Aiguillage vers direction "
                                + CourierDisplayLabels.directionColumnLabel(
                                        dirNames.getOrDefault(e.getDirectionId(), "—"));
                        case "SOUS_ROUTED" -> {
                            String rawSous = sousNames.getOrDefault(e.getSousDirectionId(), "");
                            String fixed =
                                    CourierDisplayLabels.sousColumnLabel(true, packetDirFull, rawSous);
                            String shown = fixed.isBlank()
                                    ? (rawSous.isBlank() ? "—" : rawSous)
                                    : fixed;
                            yield "Aiguillage sous-direction " + shown;
                        }
                        case "ASSIGN_SOUS_DIRECTEUR" -> "Assigné au sous-directeur: " + nz(e.getNote());
                        case "RESOLVED" -> "Courrier résolu";
                        case "TICKET_CREATED" -> "Ticket lié créé: " + nz(e.getNote());
                        default -> type + (nz(e.getNote()).isBlank() ? "" : " — " + nz(e.getNote()));
                    };
                    String tone = switch (type) {
                        case "CREATED" -> "created";
                        case "DIRECTION_SET", "SOUS_ROUTED" -> "routing";
                        case "ASSIGN_SOUS_DIRECTEUR", "ASSIGN_INSPECTEUR", "ASSIGN_CONTROLEUR", "ASSIGN_VERIFICATEUR" -> "assigned";
                        case "RESOLVED" -> "resolved";
                        default -> "neutral";
                    };
                    return new JourneyStep(type, label, time, tone);
                })
                .toList();
    }

    public record CourierManagementPage(
            List<CourierMgmtRow> rows,
            List<DirectionFilterOption> directionFilters,
            List<DirectionPick> allDirections,
            Long filterDir,
            String filterStatus,
            String filterQuery,
            Long selectedId,
            CourierMgmtDetail detail,
            boolean canEdit,
            boolean canRedirect,
            boolean canExtra,
            boolean canDelete,
            List<DirectionPick> extraRows,
            List<DirectionPick> extraAddCandidates,
            List<JourneyStep> journeySteps) {}

    public record JourneyStep(String eventType, String label, String at, String tone) {}

    public record DirectionFilterOption(Long id, String label) {}

    public record DirectionPick(Long id, String label) {}

    public record CourierMgmtRow(
            Long id,
            String refCode,
            String title,
            String sender,
            String priority,
            String status,
            String directionName,
            String extraDirections,
            String sousName,
            Long linkedTicketId,
            String linkedTicketRef,
            String createdAt) {}

    public record CourierMgmtDetail(
            Long id,
            String refCode,
            String title,
            String description,
            String sender,
            String priority,
            String status,
            String registrationDate,
            String attachmentPath,
            Long targetDirectionId,
            String directionName,
            List<Long> extraDirectionIds) {}
}
