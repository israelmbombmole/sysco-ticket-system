package com.sysco.web.service;

import com.sysco.web.domain.AttendanceRecord;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.ShiftPunchDayOverride;
import com.sysco.web.domain.ShiftWeeklyPolicy;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AttendanceRecordRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.ShiftPunchDayOverrideRepository;
import com.sysco.web.repo.ShiftWeeklyPolicyRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.security.WebSyscoPermissions;
import com.sysco.web.util.DisplayDateFormatter;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyShiftService {
    private static final String DEFAULT_DIRECTION = "DIRECTION GENERALE";

    private final UserAccountRepository users;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;
    private final AttendanceRecordRepository attendance;
    private final ShiftWeeklyPolicyRepository weeklyPolicies;
    private final ShiftPunchDayOverrideRepository dayOverrides;
    private final NotificationService notificationService;

    public ShiftPage page(
            String direction,
            String q,
            LocalDate from,
            LocalDate to,
            String viewerUsername,
            Authentication authentication,
            Long policySousDirectionId) {
        ZoneId zone = ZoneId.systemDefault();
        ViewerRole vr = ViewerRole.from(authentication);
        String safeDirection = direction == null ? "" : direction.trim();
        String safeQ = q == null ? "" : q.trim();
        LocalDate safeFrom = from == null ? LocalDate.now(zone).minusDays(7) : from;
        LocalDate requestedTo = to == null ? LocalDate.now(zone) : to;
        LocalDate safeTo = requestedTo.isBefore(safeFrom) ? safeFrom : requestedTo;

        Map<Long, Direction> directionById =
                directions.findAll().stream().collect(Collectors.toMap(Direction::getId, d -> d, (a, b) -> a));
        Map<Long, UserAccount> userMap = users.findAll().stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u, (a, b) -> a));

        UserAccount viewer =
                viewerUsername == null || viewerUsername.isBlank()
                        ? null
                        : users.findByUsernameIgnoreCase(viewerUsername.trim()).orElse(null);

        LocalDate today = LocalDate.now(zone);
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();

        List<Long> managedUserIds = resolveManagedUserIds(viewer, vr, directionById);

        List<AttendanceRow> present =
                attendance.findCurrentlyPresentForDay(todayStart, todayEnd).stream()
                        .map(r -> toRow(r, userMap, zone))
                        .filter(r -> managedUserIds.contains(r.userId()))
                        .filter(r -> matchesDirection(r.direction(), safeDirection))
                        .filter(r -> matchesSearch(r, safeQ))
                        .sorted(Comparator.comparing(AttendanceRow::arrivalAt).reversed())
                        .toList();

        Instant rangeStart = safeFrom.atStartOfDay(zone).toInstant();
        Instant rangeEndExclusive = safeTo.plusDays(1).atStartOfDay(zone).toInstant();

        List<AttendanceRow> report =
                attendance.findByArrivalAtInRange(rangeStart, rangeEndExclusive).stream()
                        .map(r -> toRow(r, userMap, zone))
                        .filter(r ->
                                managedUserIds.contains(r.userId())
                                        || (viewer != null && viewer.getId().equals(r.userId())))
                        .filter(r -> matchesDirection(r.direction(), safeDirection))
                        .filter(r -> matchesSearch(r, safeQ))
                        .toList();

        MySessionSummary mySession = buildMySession(viewerUsername, zone, userMap, today);
        ShiftPeriodStats stats = buildStats(present, report);

        Long viewerSd = viewer == null ? null : effectiveSousDirectionId(viewer, directionById);
        LocalDate weekMonday = today.with(DayOfWeek.MONDAY);
        Optional<ShiftWeeklyPolicy> policyOpt =
                viewerSd == null
                        ? Optional.empty()
                        : weeklyPolicies.findByWeekMondayAndSousDirectionId(weekMonday, viewerSd);

        Optional<ShiftPunchDayOverride> viewerOv =
                viewer == null
                        ? Optional.empty()
                        : dayOverrides.findByForDateAndUserId(today, viewer.getId());
        boolean arrivalBypass = viewerOv.map(ShiftPunchDayOverride::isAllowArrivalBypass).orElse(false);
        boolean departureBypass = viewerOv.map(ShiftPunchDayOverride::isAllowDepartureBypass).orElse(false);
        LocalTime nowTrunc = LocalTime.now(zone).truncatedTo(ChronoUnit.MINUTES);

        boolean arrivalEligible =
                viewer == null
                        || punchArrivalAllowed(vr, policyOpt.orElse(null), nowTrunc, arrivalBypass, mySession.inOffice());
        boolean departureEligible =
                viewer == null
                        || punchDepartureAllowed(vr, policyOpt.orElse(null), nowTrunc, departureBypass, mySession.inOffice());

        WeeklyPolicyForm policyForm = buildPolicyForm(weekMonday, vr, policySousDirectionId);
        List<NamedOption> sdChoices = sousDirectionChoices(vr);

        List<TodayAgentStatusRow> directorRows = List.of();
        List<TodayOverrideNoticeRow> overrideNotices = List.of();
        if (vr.shiftWideManagement() && !managedUserIds.isEmpty()) {
            directorRows = buildDirectorTodayRows(zone, today, userMap, managedUserIds, directionById);
        }
        if ((vr.supervisorLike() || vr.adminLike()) && !managedUserIds.isEmpty()) {
            overrideNotices = buildOverrideNoticeRows(zone, today, userMap, managedUserIds);
        }

        boolean hasManagedTeam = !managedUserIds.isEmpty();
        ShiftUiModel ui =
                new ShiftUiModel(
                        !vr.supervisorLike() && !vr.adminLike(),
                        vr.supervisorLike() || vr.adminLike(),
                        vr.shiftWideManagement(),
                        vr.supervisorLike() || vr.adminLike(),
                        hasManagedTeam,
                        viewer != null && !mySession.inOffice() && arrivalEligible,
                        viewer != null && mySession.inOffice() && departureEligible,
                        punchBlockedKey(viewer, mySession, arrivalEligible, departureEligible, policyOpt.isPresent()),
                        policyForm,
                        sdChoices,
                        directorRows,
                        overrideNotices);

        return new ShiftPage(
                present,
                report,
                safeDirection,
                safeQ,
                safeFrom,
                safeTo,
                LocalDateTime.now(zone),
                vr.supervisorLike() || vr.adminLike() ? present.size() : 0,
                mySession,
                stats,
                ui);
    }

    private static String punchBlockedKey(
            UserAccount viewer,
            MySessionSummary session,
            boolean arrivalOk,
            boolean departureOk,
            boolean policyConfigured) {
        if (viewer == null) {
            return "";
        }
        if (!session.inOffice() && !arrivalOk && policyConfigured) {
            return "myShift.punch.blocked.arrivalWindow";
        }
        if (session.inOffice() && !departureOk && policyConfigured) {
            return "myShift.punch.blocked.departureWindow";
        }
        if (!session.inOffice() && !arrivalOk && !policyConfigured) {
            return "";
        }
        return "";
    }

    private WeeklyPolicyForm buildPolicyForm(LocalDate weekMonday, ViewerRole vr, Long policySousDirectionSelector) {
        if (!vr.supervisorLike() && !vr.adminLike()) {
            return null;
        }
        Long targetSd;
        if (policySousDirectionSelector != null && sousDirections.existsById(policySousDirectionSelector)) {
            targetSd = policySousDirectionSelector;
        } else {
            targetSd =
                    sousDirections.findAll().stream()
                            .min(Comparator.comparing(SousDirection::getName, String.CASE_INSENSITIVE_ORDER))
                            .map(SousDirection::getId)
                            .orElse(null);
        }

        if (targetSd == null) {
            return new WeeklyPolicyForm(
                    weekMonday,
                    null,
                    LocalTime.of(6, 0),
                    LocalTime.of(8, 30),
                    LocalTime.of(9, 30),
                    LocalTime.of(16, 0),
                    LocalTime.of(22, 0),
                    false);
        }

        Long sdKey = targetSd;
        Optional<ShiftWeeklyPolicy> existing = weeklyPolicies.findByWeekMondayAndSousDirectionId(weekMonday, sdKey);
        if (existing.isEmpty()) {
            return new WeeklyPolicyForm(
                    weekMonday,
                    sdKey,
                    LocalTime.of(6, 0),
                    LocalTime.of(8, 30),
                    LocalTime.of(9, 30),
                    LocalTime.of(16, 0),
                    LocalTime.of(22, 0),
                    false);
        }
        ShiftWeeklyPolicy p = existing.get();
        return new WeeklyPolicyForm(
                weekMonday,
                p.getSousDirectionId(),
                p.getArrivalAllowedFrom(),
                p.getArrivalOnTimeUntil(),
                p.getArrivalLateUntil(),
                p.getDepartureAllowedFrom(),
                p.getDepartureAllowedUntil(),
                true);
    }

    private List<NamedOption> sousDirectionChoices(ViewerRole vr) {
        if (!vr.supervisorLike() && !vr.adminLike()) {
            return List.of();
        }
        return sousDirections.findAll().stream()
                .sorted(Comparator.comparing(SousDirection::getName, String.CASE_INSENSITIVE_ORDER))
                .map(sd -> new NamedOption(sd.getId(), sd.getName()))
                .toList();
    }

    private List<TodayAgentStatusRow> buildDirectorTodayRows(
            ZoneId zone,
            LocalDate today,
            Map<Long, UserAccount> userMap,
            List<Long> managedUserIds,
            Map<Long, Direction> directionById) {
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        Set<Long> managed = Set.copyOf(managedUserIds);
        Map<Long, AttendanceRecord> firstArrivalByUser = new LinkedHashMap<>();
        attendance.findByArrivalAtInRange(dayStart, dayEnd).stream()
                .filter(a -> managed.contains(a.getUserId()))
                .sorted(Comparator.comparing(AttendanceRecord::getArrivalAt))
                .forEach(a -> firstArrivalByUser.putIfAbsent(a.getUserId(), a));

        List<TodayAgentStatusRow> rows = new ArrayList<>();
        for (Long uid : managedUserIds) {
            UserAccount ua = userMap.get(uid);
            if (ua == null) {
                continue;
            }
            AttendanceRecord rec = firstArrivalByUser.get(uid);
            Optional<ShiftPunchDayOverride> ov = dayOverrides.findByForDateAndUserId(today, uid);
            String overrideAccessKey = overrideAccessSummaryKey(ov);
            boolean awaitingArrivalBypass = rec == null && ov.map(ShiftPunchDayOverride::isAllowArrivalBypass).orElse(false);
            String statusKey;
            String arrivalLabel = "—";
            if (rec == null) {
                statusKey =
                        awaitingArrivalBypass
                                ? "myShift.director.status.awaitingWithAccess"
                                : "myShift.director.status.absent";
            } else {
                arrivalLabel = DisplayDateFormatter.formatInstant(rec.getArrivalAt());
                if (rec.isOutsideWindowOverride()) {
                    statusKey = "myShift.director.status.overridePunch";
                } else if (rec.isLateArrival()) {
                    statusKey = "myShift.director.status.late";
                } else {
                    statusKey = "myShift.director.status.onTime";
                }
            }
            rows.add(
                    new TodayAgentStatusRow(
                            uid,
                            ua.getUsername(),
                            resolveMatricule(ua, uid),
                            ua.getRole() == null ? "" : ua.getRole().trim(),
                            arrivalLabel,
                            overrideAccessKey,
                            statusKey));
        }
        rows.sort(Comparator.comparing(TodayAgentStatusRow::username, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private List<TodayOverrideNoticeRow> buildOverrideNoticeRows(
            ZoneId zone,
            LocalDate today,
            Map<Long, UserAccount> userMap,
            List<Long> managedUserIds) {
        Set<Long> managed = Set.copyOf(managedUserIds);
        List<TodayOverrideNoticeRow> out = new ArrayList<>();
        for (ShiftPunchDayOverride o : dayOverrides.findByForDate(today)) {
            if (!o.isAllowArrivalBypass() && !o.isAllowDepartureBypass()) {
                continue;
            }
            if (!managed.contains(o.getUserId())) {
                continue;
            }
            UserAccount grantee = userMap.get(o.getUserId());
            UserAccount granter = userMap.get(o.getGrantedByUserId());
            if (grantee == null) {
                continue;
            }
            String granterLabel =
                    granter == null ? ("id-" + o.getGrantedByUserId()) : granter.getUsername();
            out.add(
                    new TodayOverrideNoticeRow(
                            grantee.getUsername(),
                            resolveMatricule(grantee, grantee.getId()),
                            granterLabel,
                            DisplayDateFormatter.formatInstant(o.getCreatedAt()),
                            o.isAllowArrivalBypass(),
                            o.isAllowDepartureBypass()));
        }
        out.sort(Comparator.comparing(TodayOverrideNoticeRow::granteeUsername, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private List<Long> resolveManagedUserIds(UserAccount viewer, ViewerRole vr, Map<Long, Direction> directionById) {
        if (viewer == null || (!vr.supervisorLike() && !vr.adminLike())) {
            return List.of();
        }
        Set<Long> scopeSd = new LinkedHashSet<>();
        // ADMIN / SUPER_ADMIN / DIRECTEUR / SOUS-DIRECTEUR — same managed population (all active users by sous-direction).
        sousDirections.findAll().forEach(sd -> scopeSd.add(sd.getId()));
        if (scopeSd.isEmpty()) {
            return List.of();
        }
        return users.findByActiveOrderByUsernameAsc(1).stream()
                .filter(u -> {
                    Long esd = effectiveSousDirectionId(u, directionById);
                    return esd != null && scopeSd.contains(esd);
                })
                .map(UserAccount::getId)
                .toList();
    }

    private static Long effectiveSousDirectionId(UserAccount u, Map<Long, Direction> directionById) {
        if (u == null) {
            return null;
        }
        if (u.getSousDirectionId() != null) {
            return u.getSousDirectionId();
        }
        if (u.getDirectionId() == null) {
            return null;
        }
        Direction d = directionById.get(u.getDirectionId());
        return d == null ? null : d.getSousDirectionId();
    }

    private static boolean punchArrivalAllowed(
            ViewerRole vr,
            ShiftWeeklyPolicy policy,
            LocalTime nowTrunc,
            boolean arrivalBypass,
            boolean inOffice) {
        if (vr.shiftWideManagement()) {
            return true;
        }
        if (inOffice) {
            return false;
        }
        if (policy == null) {
            return true;
        }
        if (arrivalBypass && outsideArrivalWindow(policy, nowTrunc)) {
            return true;
        }
        return withinInclusive(nowTrunc, trunc(policy.getArrivalAllowedFrom()), trunc(policy.getArrivalLateUntil()));
    }

    private static boolean punchDepartureAllowed(
            ViewerRole vr,
            ShiftWeeklyPolicy policy,
            LocalTime nowTrunc,
            boolean departureBypass,
            boolean inOffice) {
        if (vr.shiftWideManagement()) {
            return true;
        }
        if (!inOffice) {
            return false;
        }
        if (policy == null) {
            return true;
        }
        if (departureBypass && outsideDepartureWindow(policy, nowTrunc)) {
            return true;
        }
        return withinInclusive(nowTrunc, trunc(policy.getDepartureAllowedFrom()), trunc(policy.getDepartureAllowedUntil()));
    }

    private static boolean outsideArrivalWindow(ShiftWeeklyPolicy policy, LocalTime nowTrunc) {
        return !withinInclusive(nowTrunc, trunc(policy.getArrivalAllowedFrom()), trunc(policy.getArrivalLateUntil()));
    }

    private static boolean outsideDepartureWindow(ShiftWeeklyPolicy policy, LocalTime nowTrunc) {
        return !withinInclusive(nowTrunc, trunc(policy.getDepartureAllowedFrom()), trunc(policy.getDepartureAllowedUntil()));
    }

    /** Visible breakdown for supervisors: none / arrival-only / departure-only / both. */
    private static String overrideAccessSummaryKey(Optional<ShiftPunchDayOverride> ov) {
        if (ov.isEmpty()) {
            return "myShift.director.overrideAccess.none";
        }
        ShiftPunchDayOverride o = ov.get();
        boolean a = o.isAllowArrivalBypass();
        boolean d = o.isAllowDepartureBypass();
        if (a && d) {
            return "myShift.director.overrideAccess.full";
        }
        if (a) {
            return "myShift.director.overrideAccess.arrivalOnly";
        }
        if (d) {
            return "myShift.director.overrideAccess.departureOnly";
        }
        return "myShift.director.overrideAccess.none";
    }

    private static LocalTime trunc(LocalTime t) {
        return t == null ? LocalTime.MIDNIGHT : t.truncatedTo(ChronoUnit.MINUTES);
    }

    private static boolean withinInclusive(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return true;
        }
        if (!end.isBefore(start)) {
            return !now.isBefore(start) && !now.isAfter(end);
        }
        // Overnight window (rare): after start OR before end
        return !now.isBefore(start) || !now.isAfter(end);
    }

    private ShiftPeriodStats buildStats(List<AttendanceRow> present, List<AttendanceRow> report) {
        long completed = report.stream().filter(r -> r.departureAt() != null).count();
        long openInReport = report.stream().filter(r -> r.departureAt() == null).count();
        return new ShiftPeriodStats(present.size(), report.size(), completed, openInReport);
    }

    private MySessionSummary buildMySession(String viewerUsername, ZoneId zone, Map<Long, UserAccount> userMap, LocalDate today) {
        if (viewerUsername == null || viewerUsername.isBlank()) {
            return MySessionSummary.empty();
        }
        UserAccount me = users.findByUsernameIgnoreCase(viewerUsername.trim()).orElse(null);
        if (me == null) {
            return MySessionSummary.empty();
        }

        Optional<AttendanceRecord> open =
                attendance.findFirstByUserIdAndDepartureAtIsNullOrderByArrivalAtDesc(me.getId());
        boolean inOffice =
                open.isPresent() && sameCalendarDay(open.get().getArrivalAt(), today, zone);

        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<AttendanceRecord> todayRecords =
                attendance.findByArrivalAtInRange(dayStart, dayEnd).stream()
                        .filter(a -> me.getId().equals(a.getUserId()))
                        .sorted(Comparator.comparing(AttendanceRecord::getArrivalAt))
                        .toList();

        String firstIn =
                todayRecords.isEmpty()
                        ? ""
                        : DisplayDateFormatter.formatInstant(todayRecords.get(0).getArrivalAt());

        LocalDateTime openArrivalLdt =
                open.map(o -> LocalDateTime.ofInstant(o.getArrivalAt(), zone)).orElse(null);
        String elapsed =
                inOffice && openArrivalLdt != null
                        ? formatElapsed(Duration.between(openArrivalLdt, LocalDateTime.now(zone)))
                        : "";

        LocalDate monday = today.with(DayOfWeek.MONDAY);
        Instant weekStart = monday.atStartOfDay(zone).toInstant();
        Instant weekEnd = monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        long weekDaysPresent =
                attendance.findByArrivalAtInRange(weekStart, weekEnd).stream()
                        .filter(a -> me.getId().equals(a.getUserId()))
                        .map(a -> LocalDate.ofInstant(a.getArrivalAt(), zone))
                        .distinct()
                        .count();

        Long openArrivalEpochMs =
                inOffice && open.isPresent() ? open.get().getArrivalAt().toEpochMilli() : null;

        return new MySessionSummary(inOffice, firstIn, elapsed, weekDaysPresent, openArrivalEpochMs);
    }

    private static String formatElapsed(Duration d) {
        if (d == null || d.isNegative()) {
            return "";
        }
        long totalMin = d.toMinutes();
        long h = totalMin / 60;
        long m = totalMin % 60;
        return h + "h " + m + "min";
    }

    private static boolean sameCalendarDay(Instant instant, LocalDate day, ZoneId zone) {
        return LocalDate.ofInstant(instant, zone).equals(day);
    }

    @Transactional
    public void punch(String username, String direction, Authentication authentication) {
        String normalizedUser = (username == null || username.isBlank()) ? "agent" : username.trim();
        UserAccount account = users.findByUsernameIgnoreCase(normalizedUser).orElse(null);
        if (account == null) {
            throw new IllegalStateException("userUnknown");
        }
        Long userId = account.getId();

        ViewerRole vr = ViewerRole.from(authentication);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalTime nowTrunc = LocalTime.now(zone).truncatedTo(ChronoUnit.MINUTES);
        Map<Long, Direction> directionById =
                directions.findAll().stream().collect(Collectors.toMap(Direction::getId, d -> d, (a, b) -> a));

        Long sd = effectiveSousDirectionId(account, directionById);
        LocalDate weekMonday = today.with(DayOfWeek.MONDAY);
        Optional<ShiftWeeklyPolicy> policyOpt =
                sd == null ? Optional.empty() : weeklyPolicies.findByWeekMondayAndSousDirectionId(weekMonday, sd);

        Optional<ShiftPunchDayOverride> overrideOpt = dayOverrides.findByForDateAndUserId(today, userId);
        boolean arrivalBypass = overrideOpt.map(ShiftPunchDayOverride::isAllowArrivalBypass).orElse(false);
        boolean departureBypass = overrideOpt.map(ShiftPunchDayOverride::isAllowDepartureBypass).orElse(false);

        Optional<AttendanceRecord> danglingOpen =
                attendance.findFirstByUserIdAndDepartureAtIsNullOrderByArrivalAtDesc(userId);
        if (danglingOpen.isPresent()
                && !sameCalendarDay(danglingOpen.get().getArrivalAt(), today, zone)) {
            AttendanceRecord stale = danglingOpen.get();
            stale.setDepartureAt(Instant.now());
            attendance.save(stale);
        }

        Optional<AttendanceRecord> open =
                attendance.findFirstByUserIdAndDepartureAtIsNullOrderByArrivalAtDesc(userId);

        if (open.isPresent()
                && sameCalendarDay(open.get().getArrivalAt(), today, zone)) {
            if (!punchDepartureAllowed(vr, policyOpt.orElse(null), nowTrunc, departureBypass, true)) {
                throw new IllegalStateException("outsideWindow");
            }
            boolean departureUsedBypass =
                    policyOpt.isPresent()
                            && departureBypass
                            && outsideDepartureWindow(policyOpt.get(), nowTrunc);
            AttendanceRecord r = open.get();
            r.setDepartureAt(Instant.now());
            attendance.save(r);
            if (departureUsedBypass) {
                consumeDepartureBypass(userId, today);
            }
            notificationService.notifyAttendancePunchToSousDirecteurs(
                    account.getId(),
                    account.getUsername(),
                    false,
                    r.getId(),
                    effectiveSousDirectionId(account, directionById));
            return;
        }

        if (!punchArrivalAllowed(vr, policyOpt.orElse(null), nowTrunc, arrivalBypass, false)) {
            throw new IllegalStateException("outsideWindow");
        }

        boolean lateArrival =
                policyOpt.isPresent() && nowTrunc.isAfter(trunc(policyOpt.get().getArrivalOnTimeUntil()));
        boolean outsideOverride =
                policyOpt.isPresent()
                        && arrivalBypass
                        && outsideArrivalWindow(policyOpt.get(), nowTrunc);

        String usedDirection = resolveDirection(account, direction);
        String signature =
                account.getAttendanceSignature() == null || account.getAttendanceSignature().isBlank()
                        ? normalizedUser
                        : account.getAttendanceSignature().trim();

        AttendanceRecord n = new AttendanceRecord();
        n.setUserId(userId);
        n.setDirectionName(usedDirection);
        Instant now = Instant.now();
        n.setArrivalAt(now);
        n.setCreatedAt(now);
        n.setLateArrival(lateArrival);
        n.setOutsideWindowOverride(outsideOverride);
        attendance.save(n);
        if (outsideOverride) {
            consumeArrivalBypass(userId, today);
        }
        notificationService.notifyAttendancePunchToSousDirecteurs(
                account.getId(),
                account.getUsername(),
                true,
                n.getId(),
                effectiveSousDirectionId(account, directionById));
    }

    private void consumeArrivalBypass(Long userId, LocalDate today) {
        dayOverrides
                .findByForDateAndUserId(today, userId)
                .ifPresent(
                        o -> {
                            o.setAllowArrivalBypass(false);
                            persistOverrideOrDeleteIfEmpty(o);
                        });
    }

    private void consumeDepartureBypass(Long userId, LocalDate today) {
        dayOverrides
                .findByForDateAndUserId(today, userId)
                .ifPresent(
                        o -> {
                            o.setAllowDepartureBypass(false);
                            persistOverrideOrDeleteIfEmpty(o);
                        });
    }

    private void persistOverrideOrDeleteIfEmpty(ShiftPunchDayOverride o) {
        if (!o.isAllowArrivalBypass() && !o.isAllowDepartureBypass()) {
            dayOverrides.delete(o);
        } else {
            dayOverrides.save(o);
        }
    }

    @Transactional
    public void saveWeeklyPolicy(
            Authentication authentication,
            LocalDate weekMonday,
            Long sousDirectionId,
            LocalTime arrivalAllowedFrom,
            LocalTime arrivalOnTimeUntil,
            LocalTime arrivalLateUntil,
            LocalTime departureAllowedFrom,
            LocalTime departureAllowedUntil) {
        ViewerRole vr = ViewerRole.from(authentication);
        if (!vr.adminLike() && !vr.supervisorLike()) {
            throw new IllegalStateException("policyForbidden");
        }
        UserAccount actor =
                authentication == null || authentication.getName() == null
                        ? null
                        : users.findByUsernameIgnoreCase(authentication.getName().trim()).orElse(null);

        LocalDate monday = weekMonday == null ? LocalDate.now(ZoneId.systemDefault()).with(DayOfWeek.MONDAY) : weekMonday;

        Long targetSd = sousDirectionId;
        if (targetSd == null || !sousDirections.existsById(targetSd)) {
            throw new IllegalStateException("policyBadSd");
        }
        LocalTime aFrom = trunc(arrivalAllowedFrom);
        LocalTime onTime = trunc(arrivalOnTimeUntil);
        LocalTime lateUntil = trunc(arrivalLateUntil);
        LocalTime dFrom = trunc(departureAllowedFrom);
        LocalTime dUntil = trunc(departureAllowedUntil);
        if (aFrom.isAfter(onTime) || onTime.isAfter(lateUntil)) {
            throw new IllegalStateException("policyBadTimes");
        }
        if (dFrom.isAfter(dUntil)) {
            throw new IllegalStateException("policyBadTimes");
        }

        ShiftWeeklyPolicy p =
                weeklyPolicies
                        .findByWeekMondayAndSousDirectionId(monday, targetSd)
                        .orElseGet(ShiftWeeklyPolicy::new);
        p.setWeekMonday(monday);
        p.setSousDirectionId(targetSd);
        p.setArrivalAllowedFrom(aFrom);
        p.setArrivalOnTimeUntil(onTime);
        p.setArrivalLateUntil(lateUntil);
        p.setDepartureAllowedFrom(dFrom);
        p.setDepartureAllowedUntil(dUntil);
        p.setUpdatedAt(Instant.now());
        p.setUpdatedByUserId(actor == null ? null : actor.getId());
        weeklyPolicies.save(p);
    }

    @Transactional
    public void grantDayOverride(Authentication authentication, String usernameOrMatricule, String grantScopeRaw) {
        ViewerRole vr = ViewerRole.from(authentication);
        if (!vr.adminLike() && !vr.supervisorLike()) {
            throw new IllegalStateException("overrideForbidden");
        }
        String raw = usernameOrMatricule == null ? "" : usernameOrMatricule.trim();
        if (raw.isBlank()) {
            throw new IllegalStateException("overrideMissingTarget");
        }
        OverrideGrantScope scope = OverrideGrantScope.parse(grantScopeRaw);
        UserAccount actor =
                authentication == null || authentication.getName() == null
                        ? null
                        : users.findByUsernameIgnoreCase(authentication.getName().trim()).orElse(null);
        UserAccount target =
                users.findByUsernameIgnoreCase(raw)
                        .or(() -> users.findByMatriculeIgnoreCase(raw))
                        .orElseThrow(() -> new IllegalStateException("overrideUnknownUser"));

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Long granterId = actor == null ? target.getId() : actor.getId();

        ShiftPunchDayOverride o =
                dayOverrides
                        .findByForDateAndUserId(today, target.getId())
                        .orElseGet(ShiftPunchDayOverride::new);
        boolean keepArr = o.getId() != null && o.isAllowArrivalBypass();
        boolean keepDep = o.getId() != null && o.isAllowDepartureBypass();
        o.setForDate(today);
        o.setUserId(target.getId());
        o.setGrantedByUserId(granterId);
        o.setCreatedAt(Instant.now());
        switch (scope) {
            case BOTH -> {
                o.setAllowArrivalBypass(true);
                o.setAllowDepartureBypass(true);
            }
            case ARRIVAL_ONLY -> {
                o.setAllowArrivalBypass(true);
                o.setAllowDepartureBypass(o.getId() != null ? keepDep : false);
            }
            case DEPARTURE_ONLY -> {
                o.setAllowDepartureBypass(true);
                o.setAllowArrivalBypass(o.getId() != null ? keepArr : false);
            }
        }
        dayOverrides.save(o);
    }

    @Transactional
    public void revokeDayOverride(Authentication authentication, String usernameOrMatricule) {
        ViewerRole vr = ViewerRole.from(authentication);
        if (!vr.adminLike() && !vr.supervisorLike()) {
            throw new IllegalStateException("overrideForbidden");
        }
        String raw = usernameOrMatricule == null ? "" : usernameOrMatricule.trim();
        if (raw.isBlank()) {
            throw new IllegalStateException("overrideMissingTarget");
        }
        UserAccount target =
                users.findByUsernameIgnoreCase(raw)
                        .or(() -> users.findByMatriculeIgnoreCase(raw))
                        .orElseThrow(() -> new IllegalStateException("overrideUnknownUser"));

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        dayOverrides.deleteByForDateAndUserId(today, target.getId());
    }

    private enum OverrideGrantScope {
        BOTH,
        ARRIVAL_ONLY,
        DEPARTURE_ONLY;

        static OverrideGrantScope parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return BOTH;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "both" -> BOTH;
                case "arrival", "arrival_only" -> ARRIVAL_ONLY;
                case "departure", "departure_only" -> DEPARTURE_ONLY;
                default -> throw new IllegalStateException("overrideBadScope");
            };
        }
    }

    public byte[] exportCsv(String direction, String q, LocalDate from, LocalDate to, Authentication authentication) {
        String viewer = authentication == null ? null : authentication.getName();
        ShiftPage page = page(direction, q, from, to, viewer, authentication, null);
        StringBuilder sb = new StringBuilder();
        sb.append("noms_prenoms,matricule,fonction,heure_arrivee,signature,heure_depart,lieu,retard,derogation\n");
        for (AttendanceRow r : page.reportRows()) {
            sb.append(csv(r.fullName())).append(',')
                    .append(csv(r.matricule())).append(',')
                    .append(csv(r.functionName())).append(',')
                    .append(csv(fmt(r.arrivalAt()))).append(',')
                    .append(csv(r.signature())).append(',')
                    .append(csv(r.departureAt() == null ? "" : fmt(r.departureAt()))).append(',')
                    .append(csv(r.direction())).append(',')
                    .append(r.lateArrival() ? "oui" : "non").append(',')
                    .append(r.outsideWindowOverride() ? "oui" : "non").append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private AttendanceRow toRow(AttendanceRecord r, Map<Long, UserAccount> userMap, ZoneId zone) {
        UserAccount ua = userMap.get(r.getUserId());
        String un = ua == null ? ("id-" + r.getUserId()) : ua.getUsername();
        String fullName = displayName(ua, un);
        String matricule = resolveMatricule(ua, r.getUserId());
        String fn = ua == null || ua.getRole() == null || ua.getRole().isBlank() ? "AGENT" : ua.getRole().trim();
        String sig =
                ua == null || ua.getAttendanceSignature() == null || ua.getAttendanceSignature().isBlank()
                        ? un
                        : ua.getAttendanceSignature().trim();

        return new AttendanceRow(
                r.getUserId(),
                r.getId(),
                un,
                fullName,
                matricule,
                fn,
                r.getDirectionName(),
                LocalDateTime.ofInstant(r.getArrivalAt(), zone),
                r.getDepartureAt() == null ? null : LocalDateTime.ofInstant(r.getDepartureAt(), zone),
                sig,
                r.isLateArrival(),
                r.isOutsideWindowOverride());
    }

    private static String displayName(UserAccount ua, String fallbackUsername) {
        if (ua == null) {
            return fallbackUsername;
        }
        if (ua.getEmail() != null && !ua.getEmail().isBlank()) {
            return ua.getUsername() + " (" + ua.getEmail().trim() + ")";
        }
        return ua.getUsername();
    }

    private static boolean matchesDirection(String rowDirection, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return rowDirection != null && rowDirection.equalsIgnoreCase(filter.trim());
    }

    private static boolean matchesSearch(AttendanceRow row, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String n = q.toLowerCase(Locale.ROOT);
        return row.fullName().toLowerCase(Locale.ROOT).contains(n)
                || row.matricule().toLowerCase(Locale.ROOT).contains(n)
                || row.functionName().toLowerCase(Locale.ROOT).contains(n)
                || row.direction().toLowerCase(Locale.ROOT).contains(n)
                || row.username().toLowerCase(Locale.ROOT).contains(n);
    }

    private static String fmt(LocalDateTime v) {
        if (v == null) {
            return "";
        }
        return DisplayDateFormatter.formatLocalDateTime(v);
    }

    private static String csv(String s) {
        String n = s == null ? "" : s;
        return "\"" + n.replace("\"", "\"\"") + "\"";
    }

    public record ShiftPage(
            List<AttendanceRow> presentRows,
            List<AttendanceRow> reportRows,
            String direction,
            String q,
            LocalDate from,
            LocalDate to,
            LocalDateTime now,
            int presentCount,
            MySessionSummary mySession,
            ShiftPeriodStats stats,
            ShiftUiModel ui) {}

    public record ShiftUiModel(
            boolean agentFocusLayout,
            boolean showSupervisorPanels,
            boolean showDirectorSummary,
            boolean showWeeklyPolicyForm,
            boolean hasManagedTeam,
            boolean canPunchArrival,
            boolean canPunchDeparture,
            String punchBlockedReasonKey,
            WeeklyPolicyForm weeklyPolicy,
            List<NamedOption> policySousDirectionChoices,
            List<TodayAgentStatusRow> directorTodayRows,
            List<TodayOverrideNoticeRow> todayOverrideGrants) {}

    public record WeeklyPolicyForm(
            LocalDate weekMonday,
            Long sousDirectionId,
            LocalTime arrivalAllowedFrom,
            LocalTime arrivalOnTimeUntil,
            LocalTime arrivalLateUntil,
            LocalTime departureAllowedFrom,
            LocalTime departureAllowedUntil,
            boolean existsInDb) {}

    public record NamedOption(long id, String label) {}

    public record TodayAgentStatusRow(
            long userId,
            String username,
            String matricule,
            String roleLabel,
            String arrivalFormatted,
            /** Message key: {@code myShift.director.overrideAccess.*} */
            String overrideAccessKey,
            String statusMessageKey) {}

    public record TodayOverrideNoticeRow(
            String granteeUsername,
            String matricule,
            String granterUsername,
            String grantedAt,
            boolean arrivalBypass,
            boolean departureBypass) {}

    public record MySessionSummary(
            boolean inOffice,
            String todayFirstArrivalFormatted,
            String elapsedInOfficeLabel,
            long weekDaysPresent,
            Long openArrivalEpochMs) {
        static MySessionSummary empty() {
            return new MySessionSummary(false, "", "", 0, null);
        }
    }

    public record ShiftPeriodStats(long presentNowCount, long sessionsInPeriod, long completedSessions, long openSessionsInPeriod) {}

    public List<String> directions() {
        List<String> out = directions.findAll().stream()
                .map(d -> d.getName() == null ? "" : d.getName().trim())
                .filter(n -> !n.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (out.isEmpty()) {
            List<String> fallback = new ArrayList<>();
            fallback.add(DEFAULT_DIRECTION);
            fallback.add("KATANGA");
            fallback.add("IT");
            fallback.add("FINANCE");
            return fallback;
        }
        return out;
    }

    private String resolveDirection(UserAccount account, String requestedDirection) {
        if (requestedDirection != null && !requestedDirection.isBlank()) {
            return requestedDirection.trim();
        }
        if (account != null && account.getDirectionId() != null) {
            return directions.findById(account.getDirectionId())
                    .map(d -> d.getName() == null ? "" : d.getName().trim())
                    .filter(n -> !n.isBlank())
                    .orElse(DEFAULT_DIRECTION);
        }
        return DEFAULT_DIRECTION;
    }

    private static String resolveMatricule(UserAccount account, Long userId) {
        if (account != null && account.getMatricule() != null && !account.getMatricule().isBlank()) {
            return account.getMatricule().trim();
        }
        if (userId != null) {
            return "MAT-" + String.format("%04d", userId);
        }
        return "MAT-0000";
    }

    public record AttendanceRow(
            long userId,
            long id,
            String username,
            String fullName,
            String matricule,
            String functionName,
            String direction,
            LocalDateTime arrivalAt,
            LocalDateTime departureAt,
            String signature,
            boolean lateArrival,
            boolean outsideWindowOverride) {}

    private record ViewerRole(boolean adminLike, boolean directorLike, boolean sousDirecteurLike) {
        static ViewerRole from(Authentication authentication) {
            if (authentication == null || !authentication.isAuthenticated()) {
                return new ViewerRole(false, false, false);
            }
            // Primary Spring role is ROLE_<ROLE_KEY> with underscores (e.g. ROLE_SOUS_DIRECTEUR); normalize to SOUS-DIRECTEUR.
            String normalized =
                    RoleKeys.normalizeForScope(WebSyscoPermissions.resolveRole(authentication));
            boolean admin =
                    authentication.getAuthorities().stream()
                            .anyMatch(
                                    a -> {
                                        String auth = a.getAuthority();
                                        return "ROLE_SUPER_ADMIN".equals(auth) || "ROLE_ADMIN".equals(auth);
                                    });
            boolean director = "DIRECTEUR".equalsIgnoreCase(normalized);
            boolean sous = "SOUS-DIRECTEUR".equalsIgnoreCase(normalized);
            return new ViewerRole(admin, director, sous);
        }

        boolean supervisorLike() {
            return directorLike || sousDirecteurLike;
        }

        /** ADMIN / SUPER_ADMIN / DIRECTEUR / SOUS-DIRECTEUR — full Ma garde tooling like organisation admin. */
        boolean shiftWideManagement() {
            return adminLike || directorLike || sousDirecteurLike;
        }
    }
}
