package com.sysco.web.service;

import com.sysco.web.domain.AgendaAbsence;
import com.sysco.web.domain.AgendaPublicHoliday;
import com.sysco.web.domain.AttendanceRecord;
import com.sysco.web.domain.ShiftWeeklyPolicy;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.AgendaAbsenceRepository;
import com.sysco.web.repo.AgendaPublicHolidayRepository;
import com.sysco.web.repo.AttendanceRecordRepository;
import com.sysco.web.repo.ShiftWeeklyPolicyRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.service.MissionService.MissionCalendarSpan;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smart agenda: persisted absences / leave, public holidays, overlap checks, and timeline insights.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    static final List<String> ABSENCE_TYPES = List.of("CONGÉ", "MISSION", "ABSENCE", "FORMATION", "MALADIE");

    /** Used to map punch instants to calendar days (aligned with Ma garde reporting). */
    private static final ZoneId AGENDA_ZONE = ZoneId.systemDefault();

    private final AgendaAbsenceRepository absences;
    private final AgendaPublicHolidayRepository holidays;
    private final UserAccountRepository users;
    private final MessageSource messages;
    private final MissionService missionService;
    private final AttendanceRecordRepository attendanceRecords;
    private final ShiftWeeklyPolicyRepository shiftWeeklyPolicies;

    @Transactional(readOnly = true)
    public AgendaPage page(
            LocalDate stateDate, String q, Locale locale, Long calendarUserId, YearMonth calendarMonth, String viewerUsername) {
        LocalDate at = stateDate == null ? LocalDate.now() : stateDate;
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        Map<Long, UserAccount> userMap =
                users.findAll().stream().collect(Collectors.toMap(UserAccount::getId, u -> u, (a, b) -> a));
        UserAccount viewer =
                viewerUsername == null || viewerUsername.isBlank()
                        ? null
                        : users.findByUsernameIgnoreCase(viewerUsername.trim()).orElse(null);
        Long scopeDirectionId = effectiveRosterScopeDirectionId(viewer);
        boolean rosterScopedToDirection =
                viewer != null && viewer.getDirectionId() != null && !rosterSeesAllDirections(viewer);

        List<AgendaAbsence> allAbs = absences.findAllByOrderByStartDateDesc();
        List<AgendaAbsence> filtered =
                allAbs.stream()
                        .filter(a -> matchesDirectionScope(userMap.get(a.getUserId()), scopeDirectionId))
                        .filter(a -> matchesSearch(a, query))
                        .sorted(Comparator.comparing(AgendaAbsence::getStartDate).reversed())
                        .toList();

        Set<Long> absentUserIdsAt =
                filtered.stream()
                        .filter(a -> !at.isBefore(a.getStartDate()) && !at.isAfter(a.getEndDate()))
                        .map(AgendaAbsence::getUserId)
                        .collect(Collectors.toSet());

        List<UserCard> available =
                users.findAll().stream()
                        .filter(UserAccount::isActiveBool)
                        .filter(u -> matchesDirectionScope(u, scopeDirectionId))
                        .filter(u -> !absentUserIdsAt.contains(u.getId()))
                        .filter(u -> matchesUserSearch(u, query))
                        .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                        .map(u -> new UserCard(u.getId(), u.getUsername(), safeRole(u.getRole())))
                        .toList();

        List<AbsenceRow> leaveNow =
                filtered.stream()
                        .filter(a -> !at.isBefore(a.getStartDate()) && !at.isAfter(a.getEndDate()))
                        .map(a -> toAbsenceRow(a, userMap))
                        .toList();

        List<AbsenceRow> allRows = filtered.stream().map(a -> toAbsenceRow(a, userMap)).toList();

        List<AgendaPublicHoliday> holidayToday = holidays.findCovering(at);
        boolean publicHoliday = !holidayToday.isEmpty();
        String holidayLabel =
                publicHoliday ? holidayToday.stream().map(AgendaPublicHoliday::getLabel).collect(Collectors.joining(" · ")) : null;

        LocalDate horizonEnd = at.plusDays(14);
        List<TimelineItem> timeline = buildTimeline(at, horizonEnd, userMap, locale, scopeDirectionId);

        List<HolidayRow> holidayRows =
                holidays.findAllByOrderByStartDateAsc().stream()
                        .map(h -> new HolidayRow(h.getId(), h.getStartDate(), h.getEndDate(), h.getLabel()))
                        .toList();

        List<String> insightLines = buildInsights(at, available.size(), leaveNow.size(), publicHoliday, holidayLabel, timeline, locale);

        List<String> usernames =
                users.findAll().stream()
                        .filter(UserAccount::isActiveBool)
                        .filter(u -> matchesDirectionScope(u, scopeDirectionId))
                        .map(UserAccount::getUsername)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();

        YearMonth ym = calendarMonth != null ? calendarMonth : YearMonth.from(at);
        List<AgentCalendarPick> calendarPicks =
                users.findAll().stream()
                        .filter(UserAccount::isActiveBool)
                        .filter(u -> matchesDirectionScope(u, scopeDirectionId))
                        .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                        .map(u -> new AgentCalendarPick(u.getId(), u.getUsername(), safeRole(u.getRole())))
                        .toList();
        final Long requestedCalendarUserId = calendarUserId;
        Long calUid = requestedCalendarUserId;
        if (requestedCalendarUserId != null
                && calendarPicks.stream().noneMatch(p -> p.userId() == requestedCalendarUserId)) {
            calUid = null;
        }
        if (calUid == null && !calendarPicks.isEmpty()) {
            calUid = calendarPicks.get(0).userId();
        }
        List<List<CalendarCell>> calendarWeeks =
                calUid == null ? List.of() : buildAgentCalendar(ym, calUid, userMap, locale);
        String monthLabel = ym.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale));
        String prevMonth = ym.minusMonths(1).toString();
        String nextMonth = ym.plusMonths(1).toString();
        String summaryAtDate =
                messages.getMessage(
                        "agenda.summary", new Object[] {available.size(), leaveNow.size()}, locale);

        return new AgendaPage(
                available,
                leaveNow,
                allRows,
                timeline,
                holidayRows,
                insightLines,
                publicHoliday,
                holidayLabel,
                usernames,
                at,
                q == null ? "" : q,
                ABSENCE_TYPES,
                calendarPicks,
                calUid,
                ym.toString(),
                monthLabel,
                calendarWeeks,
                prevMonth,
                nextMonth,
                summaryAtDate,
                rosterScopedToDirection);
    }

    /**
     * {@code ADMIN} / {@code SUPER_ADMIN} see the whole organization in the agenda roster; others are limited to their
     * direction (when set).
     */
    private static boolean rosterSeesAllDirections(UserAccount viewer) {
        if (viewer == null) {
            return false;
        }
        String r = RoleKeys.normalizeForScope(viewer.getRole());
        return "SUPER_ADMIN".equals(r) || "ADMIN".equals(r);
    }

    /** {@code null} means no direction filter (entire org). */
    private static Long effectiveRosterScopeDirectionId(UserAccount viewer) {
        if (viewer == null || rosterSeesAllDirections(viewer)) {
            return null;
        }
        return viewer.getDirectionId();
    }

    /** When {@code viewerDirectionId} is set, only users in that direction are visible in roster, calendar pickers, etc. */
    private static boolean matchesDirectionScope(UserAccount user, Long viewerDirectionId) {
        if (viewerDirectionId == null) {
            return true;
        }
        return user != null && viewerDirectionId.equals(user.getDirectionId());
    }

    @Transactional
    public void addAbsence(String username, String type, LocalDate from, LocalDate to, String remarks, Long actorUserId) {
        if (username == null
                || username.isBlank()
                || type == null
                || type.isBlank()
                || from == null
                || to == null) {
            throw new IllegalArgumentException("missingFields");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("badDates");
        }
        UserAccount user =
                users.findByUsernameIgnoreCase(username.trim()).orElseThrow(() -> new IllegalArgumentException("unknownUser"));
        if (!user.isActiveBool()) {
            throw new IllegalArgumentException("inactiveUser");
        }
        if (actorUserId != null) {
            UserAccount actor = users.findById(actorUserId).orElse(null);
            if (actor != null
                    && actor.getDirectionId() != null
                    && user.getDirectionId() != null
                    && !actor.getDirectionId().equals(user.getDirectionId())) {
                throw new IllegalArgumentException("directionScope");
            }
        }
        String normType = type.trim().toUpperCase(Locale.ROOT);
        long overlap =
                absences.countOverlapping(user.getId(), from, to, null);
        if (overlap > 0) {
            throw new IllegalArgumentException("overlap");
        }
        AgendaAbsence a = new AgendaAbsence();
        a.setUserId(user.getId());
        a.setAbsenceType(normType);
        a.setStartDate(from);
        a.setEndDate(to);
        a.setRemarks(remarks == null ? "" : remarks.trim());
        a.setCreatedAt(Instant.now());
        a.setCreatedBy(actorUserId);
        absences.save(a);
    }

    @Transactional
    public void deleteAbsence(long id) {
        if (!absences.existsById(id)) {
            throw new IllegalArgumentException("notFound");
        }
        absences.deleteById(id);
    }

    @Transactional
    public void addHoliday(LocalDate start, LocalDate end, String label) {
        if (start == null || end == null || label == null || label.isBlank()) {
            throw new IllegalArgumentException("missingFields");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("badDates");
        }
        AgendaPublicHoliday h = new AgendaPublicHoliday();
        h.setStartDate(start);
        h.setEndDate(end);
        h.setLabel(label.trim());
        h.setCreatedAt(Instant.now());
        holidays.save(h);
    }

    @Transactional
    public void deleteHoliday(long id) {
        if (!holidays.existsById(id)) {
            throw new IllegalArgumentException("notFound");
        }
        holidays.deleteById(id);
    }

    private boolean matchesSearch(AgendaAbsence a, String q) {
        if (q.isBlank()) {
            return true;
        }
        UserAccount u = users.findById(a.getUserId()).orElse(null);
        String un = u != null && u.getUsername() != null ? u.getUsername().toLowerCase(Locale.ROOT) : "";
        String role = u != null && u.getRole() != null ? u.getRole().toLowerCase(Locale.ROOT) : "";
        String type =
                a.getAbsenceType() != null ? a.getAbsenceType().toLowerCase(Locale.ROOT) : "";
        String rem = a.getRemarks() != null ? a.getRemarks().toLowerCase(Locale.ROOT) : "";
        return un.contains(q) || role.contains(q) || type.contains(q) || rem.contains(q);
    }

    private boolean matchesUserSearch(UserAccount u, String q) {
        if (q.isBlank()) {
            return true;
        }
        return u.getUsername().toLowerCase(Locale.ROOT).contains(q)
                || (u.getRole() != null && u.getRole().toLowerCase(Locale.ROOT).contains(q));
    }

    private AbsenceRow toAbsenceRow(AgendaAbsence a, Map<Long, UserAccount> userMap) {
        UserAccount u = userMap.get(a.getUserId());
        String name = u != null ? u.getUsername() : ("#" + a.getUserId());
        String role = u != null ? safeRole(u.getRole()) : "";
        return new AbsenceRow(
                a.getId(),
                a.getUserId(),
                name,
                role,
                a.getAbsenceType(),
                a.getStartDate(),
                a.getEndDate(),
                a.getRemarks() == null ? "" : a.getRemarks(),
                ChronoUnit.DAYS.between(a.getStartDate(), a.getEndDate()) + 1);
    }

    private List<TimelineItem> buildTimeline(
            LocalDate from, LocalDate through, Map<Long, UserAccount> userMap, Locale locale, Long viewerDirectionId) {
        LinkedHashMap<String, TimelineItem> merged = new LinkedHashMap<>();

        String holidayKind = messages.getMessage("agenda.timeline.kind.holiday", null, locale);
        for (AgendaPublicHoliday h : holidays.findSpanningWindow(from, through)) {
            merged.put(
                    "H-" + h.getId(),
                    new TimelineItem(h.getStartDate(), h.getEndDate(), "HOLIDAY", h.getLabel(), holidayKind));
        }

        for (AgendaAbsence a : absences.findSpanningWindow(from, through)) {
            UserAccount u = userMap.get(a.getUserId());
            if (!matchesDirectionScope(u, viewerDirectionId)) {
                continue;
            }
            String name = u != null ? u.getUsername() : ("#" + a.getUserId());
            LocalDate s = a.getStartDate().isBefore(from) ? from : a.getStartDate();
            LocalDate e = a.getEndDate().isAfter(through) ? through : a.getEndDate();
            String title =
                    name
                            + " · "
                            + a.getAbsenceType()
                            + (a.getRemarks() != null && !a.getRemarks().isBlank()
                                    ? " — " + truncate(a.getRemarks(), 80)
                                    : "");
            merged.put(
                    "A-" + a.getId(),
                    new TimelineItem(s, e, "ABSENCE", title, kindLabelForAbsence(a.getAbsenceType(), locale)));
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(TimelineItem::startDate).thenComparing(TimelineItem::kind))
                .toList();
    }

    private String kindLabelForAbsence(String type, Locale locale) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        String key =
                switch (t) {
                    case "MISSION" -> "agenda.kind.mission";
                    case "CONGÉ", "CONGE" -> "agenda.kind.leave";
                    case "MALADIE" -> "agenda.kind.sick";
                    case "FORMATION" -> "agenda.kind.training";
                    case "ABSENCE" -> "agenda.kind.admin";
                    default -> "agenda.kind.other";
                };
        return messages.getMessage(key, null, locale);
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private List<String> buildInsights(
            LocalDate at,
            int availableCount,
            int onLeaveCount,
            boolean publicHoliday,
            String holidayLabel,
            List<TimelineItem> timeline,
            Locale locale) {
        List<String> lines = new ArrayList<>();
        lines.add(
                messages.getMessage(
                        "agenda.insight.headline",
                        new Object[] {availableCount, onLeaveCount},
                        locale));

        if (publicHoliday && holidayLabel != null) {
            lines.add(messages.getMessage("agenda.insight.publicHoliday", new Object[] {holidayLabel}, locale));
        }

        timeline.stream()
                .filter(t -> "HOLIDAY".equals(t.kind()))
                .findFirst()
                .ifPresent(
                        t -> lines.add(
                                messages.getMessage(
                                        "agenda.insight.nextHoliday",
                                        new Object[] {t.title(), t.startDate(), t.endDate()},
                                        locale)));

        timeline.stream()
                .filter(t -> "ABSENCE".equals(t.kind()))
                .findFirst()
                .ifPresent(
                        t -> lines.add(
                                messages.getMessage(
                                        "agenda.insight.nextAbsence",
                                        new Object[] {t.title(), t.startDate()},
                                        locale)));

        lines.add(messages.getMessage("agenda.insight.smartHint", null, locale));
        return lines;
    }

    private List<List<CalendarCell>> buildAgentCalendar(
            YearMonth ym, long userId, Map<Long, UserAccount> userMap, Locale locale) {
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        LocalDate gridStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = monthEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<MissionCalendarSpan> missions = missionService.missionSpansForUser(userId, gridStart, gridEnd);
        List<AgendaAbsence> userAbs =
                absences.findAll().stream()
                        .filter(a -> Objects.equals(a.getUserId(), userId))
                        .filter(a -> !a.getEndDate().isBefore(gridStart) && !a.getStartDate().isAfter(gridEnd))
                        .toList();
        List<AgendaPublicHoliday> holSpans = holidays.findSpanningWindow(gridStart, gridEnd);

        UserAccount calUser = userMap.get(userId);
        Long sousDirectionId = calUser != null ? calUser.getSousDirectionId() : null;
        Map<LocalDate, ShiftWeeklyPolicy> policyByWeekMonday = loadWeeklyPoliciesForGrid(sousDirectionId, gridStart, gridEnd);
        Instant attFrom = gridStart.atStartOfDay(AGENDA_ZONE).toInstant();
        Instant attUntil = gridEnd.plusDays(1).atStartOfDay(AGENDA_ZONE).toInstant();
        List<AttendanceRecord> userAttendance = attendanceRecords.findByUserIdAndArrivalAtRange(userId, attFrom, attUntil);
        Map<LocalDate, List<AttendanceRecord>> attendanceByDay =
                userAttendance.stream()
                        .collect(Collectors.groupingBy(a -> a.getArrivalAt().atZone(AGENDA_ZONE).toLocalDate()));

        List<List<CalendarCell>> weeks = new ArrayList<>();
        List<CalendarCell> row = new ArrayList<>();
        for (LocalDate d = gridStart; !d.isAfter(gridEnd); d = d.plusDays(1)) {
            boolean inMonth = YearMonth.from(d).equals(ym);
            List<CalendarChip> chips = new ArrayList<>();
            collectHolidayChips(d, holSpans, chips, locale);
            if (!isWeekend(d)) {
                collectShiftPolicyChip(d, sousDirectionId, policyByWeekMonday, chips, locale);
            }
            collectMissionChips(d, missions, chips, locale);
            collectAbsenceChips(d, userAbs, chips, locale);
            collectAttendanceChips(d, attendanceByDay.getOrDefault(d, List.of()), chips, locale);
            chips.sort(Comparator.comparingInt(CalendarChip::sortOrder));
            if (chips.size() > 5) {
                List<CalendarChip> cut = new ArrayList<>(chips.subList(0, 4));
                cut.add(
                        new CalendarChip(
                                "chip-more",
                                "+" + (chips.size() - 4) + " …",
                                "",
                                99));
                chips = cut;
            }
            row.add(new CalendarCell(d, inMonth, chips, d.getDayOfMonth()));
            if (row.size() == 7) {
                weeks.add(row);
                row = new ArrayList<>();
            }
        }
        return weeks;
    }

    private static boolean isWeekend(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
    }

    private void collectHolidayChips(
            LocalDate d, List<AgendaPublicHoliday> holSpans, List<CalendarChip> chips, Locale locale) {
        String kind = messages.getMessage("agenda.cal.short.holiday", null, locale);
        for (AgendaPublicHoliday h : holSpans) {
            if (!d.isBefore(h.getStartDate()) && !d.isAfter(h.getEndDate())) {
                chips.add(
                        new CalendarChip(
                                "chip-holiday",
                                kind,
                                truncate(h.getLabel(), 44),
                                0));
            }
        }
    }

    private void collectMissionChips(LocalDate d, List<MissionCalendarSpan> missions, List<CalendarChip> chips, Locale locale) {
        for (MissionCalendarSpan m : missions) {
            if (!d.isBefore(m.startDate()) && !d.isAfter(m.endDate())) {
                String line1 =
                        messages.getMessage(
                                "agenda.cal.short.fieldMission", new Object[] {m.code()}, locale);
                String line2 =
                        (m.lead()
                                        ? messages.getMessage("agenda.cal.mission.lead", null, locale) + " · "
                                        : "")
                                + truncate(m.title(), 40);
                chips.add(new CalendarChip("chip-mission-field", line1, line2, 1));
            }
        }
    }

    private void collectAbsenceChips(LocalDate d, List<AgendaAbsence> userAbs, List<CalendarChip> chips, Locale locale) {
        for (AgendaAbsence a : userAbs) {
            if (!d.isBefore(a.getStartDate()) && !d.isAfter(a.getEndDate())) {
                chips.add(
                        new CalendarChip(
                                absenceChipClass(a.getAbsenceType()),
                                kindLabelForAbsence(a.getAbsenceType(), locale),
                                truncate(a.getRemarks() == null ? "" : a.getRemarks(), 44),
                                3));
            }
        }
    }

    /**
     * For each Monday in the calendar grid, resolve the effective Ma garde policy: the latest saved policy
     * whose {@code weekMonday} is on or before that week (carry-forward), so sous-directeurs need not re-save
     * every week for the agent calendar to show windows.
     */
    private Map<LocalDate, ShiftWeeklyPolicy> loadWeeklyPoliciesForGrid(
            Long sousDirectionId, LocalDate gridStart, LocalDate gridEnd) {
        if (sousDirectionId == null) {
            return Map.of();
        }
        List<ShiftWeeklyPolicy> historic =
                shiftWeeklyPolicies.findBySousDirectionIdOrderByWeekMondayAsc(sousDirectionId);
        if (historic.isEmpty()) {
            return Map.of();
        }
        LocalDate firstMonday = gridStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = gridEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, ShiftWeeklyPolicy> effectiveByWeekMonday = new HashMap<>();
        ShiftWeeklyPolicy carry = null;
        int i = 0;
        for (LocalDate m = firstMonday; !m.isAfter(lastMonday); m = m.plusWeeks(1)) {
            while (i < historic.size() && !historic.get(i).getWeekMonday().isAfter(m)) {
                carry = historic.get(i);
                i++;
            }
            if (carry != null && !carry.getWeekMonday().isAfter(m)) {
                effectiveByWeekMonday.put(m, carry);
            }
        }
        return effectiveByWeekMonday;
    }

    private void collectShiftPolicyChip(
            LocalDate d,
            Long sousDirectionId,
            Map<LocalDate, ShiftWeeklyPolicy> policyByWeekMonday,
            List<CalendarChip> chips,
            Locale locale) {
        if (sousDirectionId == null) {
            return;
        }
        LocalDate weekMon = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        ShiftWeeklyPolicy p = policyByWeekMonday.get(weekMon);
        if (p == null) {
            return;
        }
        String line1 = messages.getMessage("agenda.cal.short.shiftPolicy", null, locale);
        String line2 = formatShiftPolicySummary(p, locale);
        chips.add(new CalendarChip("chip-shift-policy", line1, truncate(line2, 52), 2));
    }

    private String formatShiftPolicySummary(ShiftWeeklyPolicy p, Locale locale) {
        DateTimeFormatter tf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);
        return messages.getMessage(
                "agenda.cal.policyWindows",
                new Object[] {
                    p.getArrivalAllowedFrom().format(tf),
                    p.getArrivalLateUntil().format(tf),
                    p.getDepartureAllowedFrom().format(tf),
                    p.getDepartureAllowedUntil().format(tf)
                },
                locale);
    }

    private void collectAttendanceChips(
            LocalDate d, List<AttendanceRecord> dayRecords, List<CalendarChip> chips, Locale locale) {
        if (dayRecords == null || dayRecords.isEmpty()) {
            return;
        }
        DateTimeFormatter tf = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale);
        String punchLabel = messages.getMessage("agenda.cal.short.arrivalPunch", null, locale);
        dayRecords.stream()
                .sorted(Comparator.comparing(AttendanceRecord::getArrivalAt))
                .forEach(
                        a -> {
                            LocalTime t = a.getArrivalAt().atZone(AGENDA_ZONE).toLocalTime();
                            StringBuilder line2 = new StringBuilder(t.format(tf));
                            if (a.isLateArrival()) {
                                line2.append(" · ").append(messages.getMessage("agenda.cal.arrival.lateFlag", null, locale));
                            }
                            if (a.isOutsideWindowOverride()) {
                                line2
                                        .append(" · ")
                                        .append(messages.getMessage("agenda.cal.arrival.overrideFlag", null, locale));
                            }
                            chips.add(
                                    new CalendarChip(
                                            "chip-attendance-punch",
                                            punchLabel,
                                            truncate(line2.toString(), 44),
                                            4));
                        });
    }

    private static String absenceChipClass(String type) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "CONGÉ", "CONGE" -> "chip-abs-conge";
            case "MALADIE" -> "chip-abs-sick";
            case "FORMATION" -> "chip-abs-training";
            case "MISSION" -> "chip-abs-mission";
            case "ABSENCE" -> "chip-abs-admin";
            default -> "chip-abs-other";
        };
    }

    private static String safeRole(String role) {
        return role == null ? "" : role.trim();
    }

    public record AgendaPage(
            List<UserCard> availableUsers,
            List<AbsenceRow> onLeaveNow,
            List<AbsenceRow> allAbsences,
            List<TimelineItem> upcomingTimeline,
            List<HolidayRow> publicHolidays,
            List<String> insightLines,
            boolean publicHolidayAtStateDate,
            String publicHolidayLabel,
            List<String> usernames,
            LocalDate stateDate,
            String q,
            List<String> absenceTypeOptions,
            List<AgentCalendarPick> calendarAgents,
            Long calendarUserId,
            String calendarMonth,
            String calendarMonthLabel,
            List<List<CalendarCell>> calendarWeeks,
            String calendarPrevMonth,
            String calendarNextMonth,
            String summaryAtDate,
            boolean rosterScopedToDirection) {}

    public record AgentCalendarPick(long userId, String username, String role) {}

    public record CalendarCell(LocalDate date, boolean currentMonth, List<CalendarChip> chips, int dayOfMonth) {}

    public record CalendarChip(String cssClass, String line1, String line2, int sortOrder) {
        /** Tooltip / title text for calendar chips (avoids fragile EL in templates). */
        public String tooltip() {
            String l1 = line1 == null ? "" : line1;
            String l2 = line2 == null ? "" : line2.trim();
            if (l2.isEmpty()) {
                return l1;
            }
            return l1 + " — " + l2;
        }

        /** JavaBeans alias for Spring EL / Thymeleaf property {@code tooltip}. */
        public String getTooltip() {
            return tooltip();
        }
    }

    public record UserCard(long userId, String username, String role) {}

    public record AbsenceRow(
            long id,
            long userId,
            String username,
            String role,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String remarks,
            long durationDays) {}

    public record TimelineItem(LocalDate startDate, LocalDate endDate, String kind, String title, String subtitle) {}

    public record HolidayRow(long id, LocalDate startDate, LocalDate endDate, String label) {}
}
