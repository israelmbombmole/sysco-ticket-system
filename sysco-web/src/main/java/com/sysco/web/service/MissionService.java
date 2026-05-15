package com.sysco.web.service;

import com.sysco.web.domain.FieldMission;
import com.sysco.web.domain.FieldMissionAttachment;
import com.sysco.web.domain.FieldMissionParticipant;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AttendanceRecordRepository;
import com.sysco.web.repo.FieldMissionAttachmentRepository;
import com.sysco.web.repo.FieldMissionParticipantRepository;
import com.sysco.web.repo.FieldMissionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.util.DisplayDateFormatter;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Field missions aligned with the desktop SYSCO client: {@code field_missions}, participants, official order text,
 * report — persisted in the database (not the legacy in-memory demo store).
 */
@Service
@RequiredArgsConstructor
public class MissionService {

    private static final DateTimeFormatter REPORT_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ORDER_DAY_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String DEFAULT_OBSERVATIONS_FR =
            "Les Autorités tant civiles, militaires que celles de la Police Nationale Congolaise sont priées "
                    + "d'apporter assistance aux porteurs du présent ordre de mission en cas de nécessité.";
    private static final String DEFAULT_INDEMNITES_FR = "À charge de la DGDA.";
    private static final String FOOTER_SLOGAN_FR =
            "Tous mobilisés pour une douane d'action et d'excellence !";

    private static final int MAX_MISSION_REPORT_ATTACHMENTS = 20;
    private static final Set<String> ALLOWED_REPORT_ATTACHMENT_EXT =
            Set.of("png", "jpg", "jpeg", "pdf", "doc", "docx", "xlsx", "txt");

    private final FieldMissionRepository fieldMissions;
    private final FieldMissionParticipantRepository participants;
    private final FieldMissionAttachmentRepository missionAttachments;
    private final UserAccountRepository users;
    private final SousDirectionRepository sousDirections;
    private final AttendanceRecordRepository attendance;

    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    @Transactional(readOnly = true)
    public MissionPage page(
            Authentication auth,
            String status,
            String q,
            LocalDate from,
            LocalDate to,
            String selectedCode,
            String tab) {
        String safeTab = "report".equalsIgnoreCase(tab) ? "report" : "mission";
        UserAccount viewer = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);

        List<FieldMission> raw =
                fieldMissions.findAll(buildListSpec(viewer.getId(), sysAdmin, status, q, from, to));
        Map<Long, String> leadNames = resolveLeadNames(raw);

        List<MissionRow> rows =
                raw.stream().map(m -> toRow(m, leadNames)).sorted(missionRowComparator()).toList();

        MissionDetail selected = null;
        if (selectedCode != null && !selectedCode.isBlank()) {
            Optional<FieldMission> found = fieldMissions.findByMissionCode(selectedCode.trim());
            if (found.isPresent() && mayView(found.get(), viewer.getId(), sysAdmin)) {
                selected = toDetail(found.get(), leadNames, viewer.getId(), sysAdmin);
            }
        }
        AttendanceVision attendanceVision = buildAttendanceVision(viewer, sysAdmin, from, to);
        return new MissionPage(rows, selected, safeTab, safeStr(status), safeStr(q), from, to, attendanceVision);
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendanceExcel(Authentication auth, LocalDate from, LocalDate to) {
        UserAccount viewer = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        AttendanceVision vision = buildAttendanceVision(viewer, sysAdmin, from, to);
        if (vision == null) {
            throw new IllegalArgumentException("forbidden");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Attendance");
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("Agent");
            head.createCell(1).setCellValue("Role");
            head.createCell(2).setCellValue("Direction");
            head.createCell(3).setCellValue("Login");
            head.createCell(4).setCellValue("Logout");
            head.createCell(5).setCellValue("State");
            int r = 1;
            for (AttendanceVisionRow row : vision.rows()) {
                Row rr = sheet.createRow(r++);
                rr.createCell(0).setCellValue(row.username());
                rr.createCell(1).setCellValue(row.role());
                rr.createCell(2).setCellValue(row.directionName());
                rr.createCell(3).setCellValue(row.loginAt());
                rr.createCell(4).setCellValue(row.logoutAt() == null ? "" : row.logoutAt());
                rr.createCell(5).setCellValue(row.presentNow() ? "PRESENT" : "OUT");
            }
            for (int i = 0; i <= 5; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("excelFailed", e);
        }
    }

    /**
     * Full mission row for the ticket-style detail page (404/403 if missing or not visible).
     */
    @Transactional(readOnly = true)
    public MissionDetailContext detailContext(String code, Authentication auth) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("missingMission");
        }
        UserAccount viewer = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m =
                fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(m, viewer.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        Map<Long, String> leadNames = resolveLeadNames(List.of(m));
        MissionDetail d = toDetail(m, leadNames, viewer.getId(), sysAdmin);
        return new MissionDetailContext(d, mayManageMission(m, viewer.getId(), sysAdmin));
    }

    public List<UserChoice> participantChoices() {
        return users.findByActiveOrderByUsernameAsc(1).stream()
                .map(u -> new UserChoice(u.getId(), u.getUsername() + " — " + safeRole(u.getRole())))
                .toList();
    }

    /**
     * Field missions (lead or participant) overlapping {@code [from, to]} for agenda calendar chips.
     */
    @Transactional(readOnly = true)
    public List<MissionCalendarSpan> missionSpansForUser(long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        return fieldMissions.findForMyWork(userId).stream()
                .map(m -> toCalendarSpan(m, userId, from, to))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MissionCalendarSpan::startDate).thenComparing(MissionCalendarSpan::code))
                .toList();
    }

    private MissionCalendarSpan toCalendarSpan(FieldMission m, long userId, LocalDate winStart, LocalDate winEnd) {
        LocalDate s = parseLocalDate(m.getStartDate());
        LocalDate e = parseLocalDate(m.getEndDate());
        if (s == null || e == null) {
            return null;
        }
        if (e.isBefore(winStart) || s.isAfter(winEnd)) {
            return null;
        }
        boolean lead = Objects.equals(m.getLeadUserId(), userId);
        return new MissionCalendarSpan(
                m.getMissionCode(),
                m.getTitle(),
                blank(m.getSiteLocation()),
                s,
                e,
                lead,
                blank(m.getStatus()));
    }

    @Transactional(readOnly = true)
    public List<MissionRow> missionsForUser(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        UserAccount u = users.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (u == null) {
            return List.of();
        }
        List<FieldMission> list = fieldMissions.findForMyWork(u.getId());
        Map<Long, String> leadNames = resolveLeadNames(list);
        return list.stream()
                .sorted(missionEntityComparator())
                .map(m -> toRow(m, leadNames))
                .toList();
    }

    @Transactional
    public String saveMission(MissionForm form, Authentication auth) {
        validateMission(form);
        UserAccount actor = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);

        boolean isNew = form.code() == null || form.code().isBlank();
        if (isNew) {
            if (!mayCreateMission(actor, sysAdmin)) {
                throw new IllegalArgumentException("forbidden");
            }
            return insertMission(form, actor.getId());
        }

        FieldMission existing =
                fieldMissions.findByMissionCode(form.code().trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(existing, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        if (!mayManageMission(existing, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }

        validateLeadAndParticipants(form.leadUserId(), form.participantMenIds(), form.participantWomenIds());
        applyMissionFields(existing, form, false);
        existing.setUpdatedAt(Instant.now());
        fieldMissions.save(existing);
        replaceParticipants(existing.getId(), form.participantMenIds(), form.participantWomenIds());
        return existing.getMissionCode();
    }

    @Transactional
    public void saveReport(String code, String reportText, Authentication auth) {
        UserAccount actor = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m = fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        if (!mayEditReport(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        String body = blankToNull(reportText);
        m.setReportText(body);
        if (body != null && m.getReportAuthorId() == null) {
            m.setReportAuthorId(actor.getId());
        }
        m.setUpdatedAt(Instant.now());
        fieldMissions.save(m);
    }

    /**
     * Lead (or admin) designates which mission participant writes the report; {@code null} means the lead writes
     * it.
     */
    @Transactional
    public void assignReport(String code, Long assigneeUserId, Authentication auth) {
        UserAccount actor = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m =
                fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        if (!mayAssignReport(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        if (assigneeUserId == null) {
            m.setReportAssigneeUserId(null);
        } else {
            if (!isAllowedReportAssignee(m, assigneeUserId)) {
                throw new IllegalArgumentException("invalidAssignee");
            }
            m.setReportAssigneeUserId(assigneeUserId);
        }
        m.setUpdatedAt(Instant.now());
        fieldMissions.save(m);
    }

    @Transactional
    public void uploadReportAttachment(String code, MultipartFile file, Authentication auth) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("attachmentEmpty");
        }
        UserAccount actor = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m =
                fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        if (!mayEditReport(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        validateMissionReportAttachment(file);
        if (missionAttachments.countByMissionId(m.getId()) >= MAX_MISSION_REPORT_ATTACHMENTS) {
            throw new IllegalArgumentException("attachmentLimit");
        }
        Path dir =
                Path.of(uploadsDirectory)
                        .toAbsolutePath()
                        .normalize()
                        .resolve("mission-report")
                        .resolve(String.valueOf(m.getId()));
        Files.createDirectories(dir);
        String safe = stripPath(file.getOriginalFilename() == null ? "file.bin" : file.getOriginalFilename());
        Path dest = dir.resolve(System.currentTimeMillis() + "_" + safe);
        Files.copy(file.getInputStream(), dest);

        FieldMissionAttachment row = new FieldMissionAttachment();
        row.setMissionId(m.getId());
        row.setFileName(safe);
        row.setFilePath(dest.toAbsolutePath().toString());
        row.setUploadedBy(actor.getId());
        row.setUploadedAt(Instant.now());
        missionAttachments.save(row);
        m.setUpdatedAt(Instant.now());
        fieldMissions.save(m);
    }

    @Transactional
    public void deleteReportAttachment(String code, long attachmentId, Authentication auth) throws IOException {
        UserAccount actor = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m =
                fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        if (!mayEditReport(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        FieldMissionAttachment a =
                missionAttachments.findById(attachmentId).orElseThrow(() -> new IllegalArgumentException("missingAttachment"));
        if (!Objects.equals(a.getMissionId(), m.getId())) {
            throw new IllegalArgumentException("forbidden");
        }
        Path root =
                Path.of(uploadsDirectory)
                        .toAbsolutePath()
                        .normalize()
                        .resolve("mission-report")
                        .resolve(String.valueOf(m.getId()));
        Path filePath = Path.of(a.getFilePath()).normalize().toAbsolutePath();
        if (!filePath.startsWith(root)) {
            throw new IllegalArgumentException("forbidden");
        }
        Files.deleteIfExists(filePath);
        missionAttachments.delete(a);
        m.setUpdatedAt(Instant.now());
        fieldMissions.save(m);
    }

    @Transactional(readOnly = true)
    public Path resolveReportAttachmentFile(String code, long attachmentId, Authentication auth) {
        UserAccount viewer = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m =
                fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(m, viewer.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        FieldMissionAttachment a =
                missionAttachments.findById(attachmentId).orElseThrow(() -> new IllegalArgumentException("missingAttachment"));
        if (!Objects.equals(a.getMissionId(), m.getId())) {
            throw new IllegalArgumentException("missingAttachment");
        }
        Path root =
                Path.of(uploadsDirectory)
                        .toAbsolutePath()
                        .normalize()
                        .resolve("mission-report")
                        .resolve(String.valueOf(m.getId()));
        Path filePath = Path.of(a.getFilePath()).normalize().toAbsolutePath();
        if (!filePath.startsWith(root) || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("missingAttachment");
        }
        return filePath;
    }

    @Transactional
    public void delete(String code, Authentication auth) {
        UserAccount actor = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m = fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayManageMission(m, actor.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        fieldMissions.delete(m);
    }

    @Transactional(readOnly = true)
    public byte[] orderDocumentBytes(String code, Authentication auth) {
        UserAccount viewer = loadUser(auth);
        boolean sysAdmin = isSysAdmin(auth);
        FieldMission m = fieldMissions.findByMissionCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("missingMission"));
        if (!mayView(m, viewer.getId(), sysAdmin)) {
            throw new IllegalArgumentException("forbidden");
        }
        try {
            return MissionOrderDocxGenerator.build(buildDgdaPayload(m));
        } catch (IOException e) {
            throw new IllegalStateException("docxFailed", e);
        }
    }

    private String insertMission(MissionForm form, long createdByUserId) {
        validateLeadAndParticipants(form.leadUserId(), form.participantMenIds(), form.participantWomenIds());

        FieldMission m = new FieldMission();
        m.setMissionCode("P-" + System.currentTimeMillis());
        m.setCreatedBy(createdByUserId);
        m.setCreatedAt(Instant.now());
        m.setUpdatedAt(Instant.now());
        applyMissionFields(m, form, true);
        FieldMission saved = fieldMissions.saveAndFlush(m);

        String code = "M-" + Year.now().getValue() + "-" + String.format("%04d", saved.getId());
        saved.setMissionCode(code);
        fieldMissions.save(saved);

        replaceParticipants(saved.getId(), form.participantMenIds(), form.participantWomenIds());
        return code;
    }

    private void applyMissionFields(FieldMission m, MissionForm form, boolean isNew) {
        m.setTitle(form.title().trim());
        m.setSiteLocation(form.site().trim());
        m.setStartDate(form.startDate().toString());
        m.setEndDate(form.endDate().toString());
        m.setDescription(blankToNull(form.description()));
        m.setObjectives(blankToNull(form.objectives()));
        m.setStatus(normalizeStatus(form.status()));
        m.setOrderReference(blankToNull(form.orderReference()));
        m.setOrderIssueDate(form.orderIssueDate() != null ? form.orderIssueDate().toString() : null);
        m.setOrderIssuedBy(blankToNull(form.orderIssuedBy()));
        m.setOrderBody(blankToNull(form.orderBody()));
        m.setTransportDetail(blankToNull(form.transportDetail()));
        m.setDurationNote(blankToNull(form.durationNote()));
        m.setExpensesNote(blankToNull(form.expensesNote()));
        m.setDepartureNote(blankToNull(form.departureNote()));
        m.setReturnNote(blankToNull(form.returnNote()));
        m.setObservationsNote(blankToNull(form.observationsNote()));

        if (form.leadUserId() != null && form.leadUserId() > 0) {
            m.setLeadUserId(form.leadUserId());
        } else {
            m.setLeadUserId(null);
        }
    }

    private void replaceParticipants(long missionId, List<Long> menIds, List<Long> womenIds) {
        participants.deleteByMissionId(missionId);
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        for (Long uid : normalizeParticipantIds(menIds)) {
            if (!seen.add(uid)) {
                throw new IllegalArgumentException("participantDuplicate");
            }
            FieldMissionParticipant p = new FieldMissionParticipant();
            p.setMissionId(missionId);
            p.setUserId(uid);
            p.setSalutation("M");
            participants.save(p);
        }
        for (Long uid : normalizeParticipantIds(womenIds)) {
            if (!seen.add(uid)) {
                throw new IllegalArgumentException("participantDuplicate");
            }
            FieldMissionParticipant p = new FieldMissionParticipant();
            p.setMissionId(missionId);
            p.setUserId(uid);
            p.setSalutation("F");
            participants.save(p);
        }
    }

    private List<Long> normalizeParticipantIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                set.add(id);
            }
        }
        return new ArrayList<>(set);
    }

    private void validateLeadAndParticipants(Long leadId, List<Long> menIds, List<Long> womenIds) {
        if (leadId != null && leadId > 0) {
            UserAccount u = users.findById(leadId).orElseThrow(() -> new IllegalArgumentException("badLead"));
            if (!isActive(u)) {
                throw new IllegalArgumentException("inactiveLead");
            }
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<Long> merged = new ArrayList<>();
        for (Long id : normalizeParticipantIds(menIds)) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException("participantDuplicate");
            }
            merged.add(id);
        }
        for (Long id : normalizeParticipantIds(womenIds)) {
            if (!seen.add(id)) {
                throw new IllegalArgumentException("participantDuplicate");
            }
            merged.add(id);
        }
        for (Long id : merged) {
            UserAccount u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("badParticipant"));
            if (!isActive(u)) {
                throw new IllegalArgumentException("inactiveParticipant");
            }
        }
    }

    private static boolean isActive(UserAccount u) {
        return u.getActive() != null && u.getActive() == 1;
    }

    private static void validateMission(MissionForm form) {
        if (form == null
                || blank(form.title()).isEmpty()
                || blank(form.site()).isEmpty()
                || form.startDate() == null
                || form.endDate() == null) {
            throw new IllegalArgumentException("missingFields");
        }
        if (form.endDate().isBefore(form.startDate())) {
            throw new IllegalArgumentException("badDates");
        }
    }

    private static String normalizeStatus(String s) {
        String v = safeStr(s).toUpperCase(Locale.ROOT);
        return switch (v) {
            case "PLANNED", "IN_PROGRESS", "REPORTED" -> v;
            // Legacy UI codes → desktop DB codes
            case "PLANIFIEE" -> "PLANNED";
            case "EN_COURS" -> "IN_PROGRESS";
            case "TERMINEE" -> "REPORTED";
            default -> "PLANNED";
        };
    }

    private Specification<FieldMission> buildListSpec(
            Long viewerId, boolean sysAdmin, String status, String q, LocalDate from, LocalDate to) {
        Specification<FieldMission> vis = visibleTo(viewerId, sysAdmin);
        Specification<FieldMission> st = matchesOptionalStatus(status);
        Specification<FieldMission> search = matchesSearch(q);
        Specification<FieldMission> period = matchesPeriod(from, to);
        return vis.and(st).and(search).and(period);
    }

    private Specification<FieldMission> visibleTo(Long viewerId, boolean sysAdmin) {
        return (root, query, cb) -> {
            if (sysAdmin) {
                return cb.conjunction();
            }
            Subquery<Long> sq = query.subquery(Long.class);
            var pr = sq.from(FieldMissionParticipant.class);
            sq.select(cb.literal(1L))
                    .where(cb.and(
                            cb.equal(pr.get("missionId"), root.get("id")), cb.equal(pr.get("userId"), viewerId)));
            return cb.or(
                    cb.equal(root.get("createdBy"), viewerId),
                    cb.equal(root.get("leadUserId"), viewerId),
                    cb.exists(sq));
        };
    }

    private Specification<FieldMission> matchesOptionalStatus(String status) {
        return (root, query, cb) -> {
            if (status == null || status.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(root.get("status"), normalizeStatus(status));
        };
    }

    private Specification<FieldMission> matchesSearch(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String needle = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            Predicate title = cb.like(cb.lower(root.get("title")), needle);
            Predicate code = cb.like(cb.lower(root.get("missionCode")), needle);
            Predicate site = cb.like(cb.lower(root.get("siteLocation")), needle);
            Predicate desc = cb.like(cb.lower(root.get("description")), needle);
            Predicate obj = cb.like(cb.lower(root.get("objectives")), needle);
            return cb.or(title, code, site, desc, obj);
        };
    }

    /** Same window as desktop {@code MissionDAO.listAll}: optional start-from / end-to date filters. */
    private Specification<FieldMission> matchesPeriod(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (from != null) {
                String fs = from.toString();
                ps.add(cb.or(cb.isNull(root.get("startDate")), cb.greaterThanOrEqualTo(root.get("startDate"), fs)));
            }
            if (to != null) {
                String ts = to.toString();
                ps.add(cb.or(cb.isNull(root.get("endDate")), cb.lessThanOrEqualTo(root.get("endDate"), ts)));
            }
            if (ps.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private boolean mayView(FieldMission m, long userId, boolean sysAdmin) {
        if (sysAdmin) {
            return true;
        }
        if (Objects.equals(m.getCreatedBy(), userId) || Objects.equals(m.getLeadUserId(), userId)) {
            return true;
        }
        return participants.findByMissionId(m.getId()).stream().anyMatch(p -> Objects.equals(p.getUserId(), userId));
    }

    private boolean mayManageMission(FieldMission m, long userId, boolean sysAdmin) {
        return sysAdmin || Objects.equals(m.getCreatedBy(), userId);
    }

    private boolean mayEditReport(FieldMission m, long userId, boolean sysAdmin) {
        if (sysAdmin) {
            return true;
        }
        Long assignee = m.getReportAssigneeUserId();
        if (assignee != null) {
            return Objects.equals(assignee, userId);
        }
        return m.getLeadUserId() != null && Objects.equals(m.getLeadUserId(), userId);
    }

    private boolean mayAssignReport(FieldMission m, long userId, boolean sysAdmin) {
        if (sysAdmin) {
            return true;
        }
        return m.getLeadUserId() != null && Objects.equals(m.getLeadUserId(), userId);
    }

    private boolean isAllowedReportAssignee(FieldMission m, long assigneeUserId) {
        return participants.findByMissionId(m.getId()).stream()
                .map(FieldMissionParticipant::getUserId)
                .anyMatch(uid -> Objects.equals(uid, assigneeUserId));
    }

    private boolean mayCreateMission(UserAccount actor, boolean sysAdmin) {
        if (sysAdmin) {
            return true;
        }
        String r = RoleKeys.normalizeForScope(actor.getRole());
        return "DIRECTEUR".equals(r) || "SOUS-DIRECTEUR".equals(r) || "INSPECTEUR".equals(r) || "ADMIN".equalsIgnoreCase(r);
    }

    private Map<Long, String> resolveLeadNames(List<FieldMission> missions) {
        Set<Long> ids =
                missions.stream().map(FieldMission::getLeadUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername));
    }

    private MissionRow toRow(FieldMission m, Map<Long, String> leadNames) {
        String lead =
                m.getLeadUserId() != null ? leadNames.getOrDefault(m.getLeadUserId(), "—") : "—";
        return new MissionRow(
                m.getMissionCode(),
                m.getTitle(),
                blank(m.getSiteLocation()),
                parseLocalDate(m.getStartDate()),
                parseLocalDate(m.getEndDate()),
                m.getStatus(),
                lead);
    }

    private MissionDetail toDetail(FieldMission m, Map<Long, String> leadNames, long viewerUserId, boolean sysAdmin) {
        List<FieldMissionParticipant> prows = participants.findByMissionId(m.getId());
        List<Long> pids = prows.stream().map(FieldMissionParticipant::getUserId).toList();
        List<Long> menIds =
                prows.stream()
                        .filter(pr -> !"F".equalsIgnoreCase(blank(pr.getSalutation())))
                        .map(FieldMissionParticipant::getUserId)
                        .toList();
        List<Long> womenIds =
                prows.stream()
                        .filter(pr -> "F".equalsIgnoreCase(blank(pr.getSalutation())))
                        .map(FieldMissionParticipant::getUserId)
                        .toList();
        List<UserAccount> pus = users.findAllById(pids);
        List<String> labels = pus.stream().map(u -> u.getUsername() + " — " + safeRole(u.getRole())).sorted().toList();

        List<UserChoice> delegateChoices =
                prows.stream()
                        .map(FieldMissionParticipant::getUserId)
                        .distinct()
                        .map(uid -> users.findById(uid).orElse(null))
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                        .map(u -> new UserChoice(u.getId(), u.getUsername() + " — " + safeRole(u.getRole())))
                        .toList();

        String leadName =
                m.getLeadUserId() != null ? leadNames.getOrDefault(m.getLeadUserId(), "—") : "—";

        Long raId = m.getReportAssigneeUserId();
        String raLabel = null;
        if (raId != null) {
            raLabel = users.findById(raId).map(u -> u.getUsername() + " — " + safeRole(u.getRole())).orElse("—");
        }

        boolean canEdit = mayEditReport(m, viewerUserId, sysAdmin);
        boolean canAssign = mayAssignReport(m, viewerUserId, sysAdmin);

        List<MissionReportAttachmentRow> reportAttachments = buildReportAttachmentRows(m.getId());

        Instant submitted = m.getReportSubmittedAt();
        return new MissionDetail(
                m.getMissionCode(),
                m.getTitle(),
                blank(m.getSiteLocation()),
                parseLocalDate(m.getStartDate()),
                parseLocalDate(m.getEndDate()),
                m.getStatus(),
                leadName,
                m.getLeadUserId(),
                blank(m.getDescription()),
                blank(m.getObjectives()),
                pids,
                menIds,
                womenIds,
                labels,
                buildOrderDocumentPlainText(m),
                blank(m.getReportText()),
                submitted,
                submitted == null ? null : REPORT_TS.format(submitted),
                blank(m.getOrderReference()),
                parseLocalDate(m.getOrderIssueDate()),
                blank(m.getOrderIssuedBy()),
                blank(m.getOrderBody()),
                blank(m.getTransportDetail()),
                blank(m.getDurationNote()),
                blank(m.getExpensesNote()),
                blank(m.getDepartureNote()),
                blank(m.getReturnNote()),
                blank(m.getObservationsNote()),
                raId,
                raLabel,
                canEdit,
                canAssign,
                delegateChoices,
                reportAttachments);
    }

    private List<MissionReportAttachmentRow> buildReportAttachmentRows(long missionId) {
        List<FieldMissionAttachment> rows = missionAttachments.findByMissionIdOrderByUploadedAtDesc(missionId);
        Set<Long> uids =
                rows.stream().map(FieldMissionAttachment::getUploadedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names =
                users.findAllById(uids).stream()
                        .collect(Collectors.toMap(UserAccount::getId, u -> blank(u.getUsername()), (a, b) -> a));
        return rows.stream()
                .map(
                        a ->
                                new MissionReportAttachmentRow(
                                        a.getId(),
                                        blank(a.getFileName()),
                                        a.getUploadedAt() == null ? "—" : REPORT_TS.format(a.getUploadedAt()),
                                        a.getUploadedBy() == null ? "—" : names.getOrDefault(a.getUploadedBy(), "—")))
                .toList();
    }

    private static void validateMissionReportAttachment(MultipartFile f) {
        String name = stripPath(f.getOriginalFilename() == null ? "" : f.getOriginalFilename());
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_REPORT_ATTACHMENT_EXT.contains(ext)) {
            throw new IllegalArgumentException("attachmentType");
        }
    }

    private static String stripPath(String name) {
        String s = name.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static Comparator<FieldMission> missionEntityComparator() {
        return Comparator.comparing(
                        (FieldMission m) -> parseLocalDate(m.getStartDate()),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FieldMission::getId, Comparator.reverseOrder());
    }

    private static Comparator<MissionRow> missionRowComparator() {
        return Comparator.comparing(MissionRow::startDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MissionRow::code, Comparator.reverseOrder());
    }

    private static LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            String d = s.length() >= 10 ? s.substring(0, 10) : s;
            return LocalDate.parse(d);
        } catch (Exception e) {
            return null;
        }
    }

    /** Plain-text preview (same content as the downloadable .docx). */
    private String buildOrderDocumentPlainText(FieldMission m) {
        MissionOrderDocxGenerator.DgdaOrderPayload p = buildDgdaPayload(m);
        StringBuilder sb = new StringBuilder();
        sb.append("République Démocratique du Congo\n");
        sb.append("Ministère des Finances\n");
        sb.append("Direction Générale des Douanes et Accises\n");
        sb.append("DGDA\n");
        sb.append(p.headerRightDateLine()).append("\n");
        sb.append("Le Directeur Général\n\n");
        sb.append(p.orderTitleLine()).append("\n\n");
        sb.append("Messieurs :\n");
        if (p.messieurs().isEmpty()) {
            sb.append("—\n");
        } else {
            for (MissionOrderDocxGenerator.ParticipantLine line : p.messieurs()) {
                sb.append(line.displayName())
                        .append(", matricule : ")
                        .append(line.matricule())
                        .append(", grade : ")
                        .append(line.grade())
                        .append(", fonction : ")
                        .append(line.fonction())
                        .append(" ;\n");
            }
        }
        sb.append("\nMesdames :\n");
        if (p.mesdames().isEmpty()) {
            sb.append("—\n");
        } else {
            for (MissionOrderDocxGenerator.ParticipantLine line : p.mesdames()) {
                sb.append(line.displayName())
                        .append(", matricule : ")
                        .append(line.matricule())
                        .append(", grade : ")
                        .append(line.grade())
                        .append(", fonction : ")
                        .append(line.fonction())
                        .append(" ;\n");
            }
        }
        sb.append("\n")
                .append(p.designationParagraph())
                .append("\n\nDépart : ")
                .append(p.departText())
                .append("\nRetour : ")
                .append(p.retourText())
                .append("\n\nObjet de la mission : ")
                .append(p.objetParagraph())
                .append("\n\nMode de transport et itinéraire :\n")
                .append(p.transportParagraph())
                .append("\n\nIndemnités accordées : ")
                .append(p.indemnitesLine())
                .append("\n\nObservations :\n")
                .append(p.observationsParagraph())
                .append("\n\n")
                .append(p.faitLine())
                .append("\n")
                .append(p.signatoryClosingLine())
                .append("\nSignature\n")
                .append(p.signatoryNameLine())
                .append("\n\n")
                .append(p.footerSlogan());
        return sb.toString();
    }

    private MissionOrderDocxGenerator.DgdaOrderPayload buildDgdaPayload(FieldMission m) {
        LocalDate start = parseLocalDate(m.getStartDate());
        LocalDate end = parseLocalDate(m.getEndDate());
        LocalDate issue = parseLocalDate(m.getOrderIssueDate());

        String headerDate =
                issue != null ? ("Kinshasa, le " + ORDER_DAY_FR.format(issue)) : "Kinshasa, le ………";

        String orderRef = blank(m.getOrderReference());
        String orderTitle = "ORDRE DE MISSION N° " + (orderRef.isEmpty() ? m.getMissionCode() : orderRef);

        List<FieldMissionParticipant> plist = participants.findByMissionId(m.getId());
        Set<Long> participantIds =
                plist.stream().map(FieldMissionParticipant::getUserId).collect(Collectors.toSet());

        List<UserAccount> menUsers = new ArrayList<>();
        List<UserAccount> womenUsers = new ArrayList<>();
        for (FieldMissionParticipant fp : plist) {
            UserAccount u = users.findById(fp.getUserId()).orElse(null);
            if (u == null) {
                continue;
            }
            if ("F".equalsIgnoreCase(blank(fp.getSalutation()))) {
                womenUsers.add(u);
            } else {
                menUsers.add(u);
            }
        }
        menUsers.sort(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER));
        womenUsers.sort(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER));

        Long leadId = m.getLeadUserId();
        if (leadId != null && leadId > 0 && !participantIds.contains(leadId)) {
            users.findById(leadId).ifPresent(u -> menUsers.add(0, u));
        }

        List<MissionOrderDocxGenerator.ParticipantLine> messieurs =
                menUsers.stream().map(this::toParticipantLine).toList();
        List<MissionOrderDocxGenerator.ParticipantLine> mesdames =
                womenUsers.stream().map(this::toParticipantLine).toList();

        String duration = resolveDurationPhrase(m, start, end);
        String site = blank(m.getSiteLocation());
        if (site.isEmpty()) {
            site = "la zone de mission précisée au dossier";
        }
        String designation =
                "Sont désignés pour effectuer une mission officielle de "
                        + duration
                        + " dans "
                        + site
                        + ".";

        String depart = blank(m.getDepartureNote());
        if (depart.isEmpty()) {
            depart = "Open";
        }
        String retour = blank(m.getReturnNote());
        if (retour.isEmpty()) {
            retour = "Open";
        }

        String objet = blank(m.getObjectives());
        if (objet.isEmpty()) {
            objet = blank(m.getTitle());
        }

        String transport = blank(m.getTransportDetail());
        if (transport.isEmpty()) {
            transport = "—";
        }

        String indemnites = blank(m.getExpensesNote());
        if (indemnites.isEmpty()) {
            indemnites = DEFAULT_INDEMNITES_FR;
        }

        String observations = blank(m.getObservationsNote());
        if (observations.isEmpty()) {
            observations = blank(m.getOrderBody());
        }
        if (observations.isEmpty()) {
            observations = DEFAULT_OBSERVATIONS_FR;
        }

        String fait =
                issue != null
                        ? ("Fait à Kinshasa, le " + ORDER_DAY_FR.format(issue))
                        : "Fait à Kinshasa, le ………";

        String signatoryName = blank(m.getOrderIssuedBy());
        if (signatoryName.isEmpty()) {
            signatoryName = " ";
        }

        return new MissionOrderDocxGenerator.DgdaOrderPayload(
                orderTitle,
                headerDate,
                messieurs,
                mesdames,
                designation,
                depart,
                retour,
                objet,
                transport,
                indemnites,
                observations,
                fait,
                "Le Directeur Général,",
                signatoryName,
                FOOTER_SLOGAN_FR);
    }

    private MissionOrderDocxGenerator.ParticipantLine toParticipantLine(UserAccount u) {
        String mat = formatMatriculeDisplay(u.getMatricule());
        String grade = frenchRoleGrade(u.getRole());
        String fonction = resolveFonctionLabel(u);
        return new MissionOrderDocxGenerator.ParticipantLine(
                displayUpperName(u.getUsername()), mat, grade, fonction);
    }

    private static String displayUpperName(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        return username.trim().toUpperCase(Locale.FRENCH);
    }

    private String resolveFonctionLabel(UserAccount u) {
        Long sid = u.getSousDirectionId();
        if (sid == null) {
            return "—";
        }
        return sousDirections.findById(sid).map(SousDirection::getName).orElse("—");
    }

    private static String formatMatriculeDisplay(String raw) {
        String m = blank(raw);
        return m.isEmpty() ? "—" : m;
    }

    private static String frenchRoleGrade(String role) {
        String r = RoleKeys.normalizeForScope(role == null ? "" : role);
        if (r.isEmpty()) {
            return "—";
        }
        return switch (r) {
            case "DIRECTEUR" -> "Directeur";
            case "SOUS-DIRECTEUR" -> "Sous-Directeur";
            case "INSPECTEUR" -> "Inspecteur";
            case "CONTROLEUR" -> "Contrôleur";
            case "VERIFICATEUR" -> "Vérificateur";
            case "VERIFICATEUR-ASSISTANT" -> "Vérificateur Assistant";
            case "SECRETAIRE" -> "Secrétaire";
            case "ADMIN", "SUPER_ADMIN" -> "Administrateur";
            default -> r.charAt(0) + r.substring(1).toLowerCase(Locale.FRENCH).replace('_', ' ');
        };
    }

    private static String resolveDurationPhrase(FieldMission m, LocalDate start, LocalDate end) {
        String note = blank(m.getDurationNote());
        if (!note.isEmpty()) {
            return note.contains("jour") ? note : note + " jours";
        }
        if (start != null && end != null && !end.isBefore(start)) {
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            return days + " jour" + (days > 1 ? "s" : "");
        }
        return "—";
    }

    private UserAccount loadUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new IllegalStateException("auth");
        }
        return users.findByUsernameIgnoreCase(auth.getName()).orElseThrow(() -> new IllegalStateException("auth"));
    }

    private static boolean isSysAdmin(Authentication auth) {
        return auth != null
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blankToNull(String s) {
        String b = blank(s);
        return b.isEmpty() ? null : b;
    }

    private static String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safeRole(String role) {
        return role == null ? "" : role.trim();
    }

    private AttendanceVision buildAttendanceVision(UserAccount viewer, boolean sysAdmin, LocalDate from, LocalDate to) {
        if (viewer == null) {
            return null;
        }
        String role = RoleKeys.normalizeForScope(viewer.getRole());
        boolean allowed = sysAdmin || "SOUS-DIRECTEUR".equals(role);
        if (!allowed) {
            return null;
        }
        Long directionId = viewer.getDirectionId();
        if (!sysAdmin && directionId == null) {
            return new AttendanceVision(List.of(), List.of(), 0, 0, 0, LocalDate.now(), LocalDate.now(), false);
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate start = from == null ? LocalDate.now(zone) : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) {
            end = start;
        }
        Instant startAt = start.atStartOfDay(zone).toInstant();
        Instant endExclusive = end.plusDays(1).atStartOfDay(zone).toInstant();
        Map<Long, UserAccount> userMap = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u, (a, b) -> a));
        List<AttendanceVisionRow> rows = attendance.findByArrivalAtInRange(startAt, endExclusive).stream()
                .map(a -> toAttendanceVisionRow(a, userMap, zone))
                .filter(Objects::nonNull)
                .filter(r -> sysAdmin || (r.directionId() != null && r.directionId().equals(directionId)))
                .sorted(Comparator.comparing(AttendanceVisionRow::loginAtInstant).reversed())
                .toList();
        int presentNow = (int) rows.stream().filter(AttendanceVisionRow::presentNow).count();
        int withLogout = (int) rows.stream().filter(r -> r.logoutAtInstant() != null).count();
        int withoutLogout = rows.size() - withLogout;
        List<String> ai = buildAttendanceAiInsights(rows, presentNow, withoutLogout);
        return new AttendanceVision(rows, ai, presentNow, withLogout, withoutLogout, start, end, true);
    }

    private static List<String> buildAttendanceAiInsights(
            List<AttendanceVisionRow> rows, int presentNow, int withoutLogout) {
        List<String> out = new ArrayList<>();
        if (rows.isEmpty()) {
            out.add("Aucune session detectee sur la periode selectionnee.");
            return out;
        }
        out.add("Presence en cours: " + presentNow + " agent(s) connecte(s).");
        long lateArrivals = rows.stream()
                .filter(r -> r.loginAtInstant() != null)
                .filter(r -> r.loginAtInstant().getHour() >= 9)
                .count();
        if (lateArrivals > 0) {
            out.add("Risque de retard: " + lateArrivals + " arrivee(s) apres 09:00.");
        } else {
            out.add("Ponctualite bonne: aucune arrivee apres 09:00.");
        }
        if (withoutLogout > 0) {
            out.add("Action recommandee: " + withoutLogout + " session(s) sans heure de logout.");
        } else {
            out.add("Toutes les sessions cloturees ont une heure de logout.");
        }
        return out;
    }

    private static AttendanceVisionRow toAttendanceVisionRow(
            com.sysco.web.domain.AttendanceRecord record, Map<Long, UserAccount> userMap, ZoneId zone) {
        UserAccount user = userMap.get(record.getUserId());
        if (user == null) {
            return null;
        }
        LocalDateTime login = LocalDateTime.ofInstant(record.getArrivalAt(), zone);
        LocalDateTime logout =
                record.getDepartureAt() == null ? null : LocalDateTime.ofInstant(record.getDepartureAt(), zone);
        return new AttendanceVisionRow(
                user.getId(),
                user.getUsername() == null ? "" : user.getUsername(),
                RoleKeys.normalizeForScope(user.getRole()),
                user.getDirectionId(),
                record.getDirectionName() == null ? "" : record.getDirectionName(),
                DisplayDateFormatter.formatLocalDateTime(login),
                logout == null ? null : DisplayDateFormatter.formatLocalDateTime(logout),
                login,
                logout,
                logout == null);
    }

    public record MissionPage(
            List<MissionRow> rows,
            MissionDetail selected,
            String activeTab,
            String status,
            String q,
            LocalDate from,
            LocalDate to,
            AttendanceVision attendanceVision) {}

    public record MissionDetailContext(MissionDetail detail, boolean canManage) {}

    public record MissionRow(
            String code,
            String title,
            String site,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String responsible) {}

    public record MissionDetail(
            String code,
            String title,
            String site,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String responsible,
            Long leadUserId,
            String description,
            String objectives,
            List<Long> participantIds,
            List<Long> participantMenIds,
            List<Long> participantWomenIds,
            List<String> participantLabels,
            String orderDocument,
            String reportText,
            Instant reportSubmittedAt,
            String reportSubmittedDisplay,
            String orderReference,
            LocalDate orderIssueDate,
            String orderIssuedBy,
            String orderBody,
            String transportDetail,
            String durationNote,
            String expensesNote,
            String departureNote,
            String returnNote,
            String observationsNote,
            Long reportAssigneeUserId,
            String reportAssigneeLabel,
            boolean canEditReport,
            boolean canAssignReport,
            List<UserChoice> reportDelegateChoices,
            List<MissionReportAttachmentRow> reportAttachments) {}

    public record MissionReportAttachmentRow(
            long id, String fileName, String uploadedAtDisplay, String uploadedByUsername) {}

    public record MissionCalendarSpan(
            String code, String title, String site, LocalDate startDate, LocalDate endDate, boolean lead, String status) {}

    public record MissionForm(
            String code,
            String title,
            String site,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            Long leadUserId,
            List<Long> participantMenIds,
            List<Long> participantWomenIds,
            String description,
            String objectives,
            String orderReference,
            LocalDate orderIssueDate,
            String orderIssuedBy,
            String orderBody,
            String transportDetail,
            String durationNote,
            String expensesNote,
            String departureNote,
            String returnNote,
            String observationsNote,
            String reportText) {}

    public record UserChoice(long id, String label) {}

    public record AttendanceVision(
            List<AttendanceVisionRow> rows,
            List<String> aiInsights,
            int presentNowCount,
            int withLogoutCount,
            int withoutLogoutCount,
            LocalDate from,
            LocalDate to,
            boolean canExport) {}

    public record AttendanceVisionRow(
            Long userId,
            String username,
            String role,
            Long directionId,
            String directionName,
            String loginAt,
            String logoutAt,
            LocalDateTime loginAtInstant,
            LocalDateTime logoutAtInstant,
            boolean presentNow) {}
}
