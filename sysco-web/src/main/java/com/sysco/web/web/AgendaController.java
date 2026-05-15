package com.sysco.web.web;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.service.AgendaService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/app/agenda")
@RequiredArgsConstructor
public class AgendaController {

    /**
     * Must stay aligned with {@link com.sysco.web.security.WebSyscoPermissions} nav rule for {@code /app/agenda}
     * (LEAVE_MANAGEMENT or USER_MANAGEMENT).
     */
    private static final String AGENDA_READ =
            "hasRole('ADMIN') or hasAuthority('LEAVE_MANAGEMENT') or hasAuthority('LEAVE_MANAGEMENT_READ') "
                    + "or hasAuthority('LEAVE_MANAGEMENT_WRITE') or hasAuthority('USER_MANAGEMENT') "
                    + "or hasAuthority('USER_MANAGEMENT_READ') or hasAuthority('USER_MANAGEMENT_WRITE')";

    private static final String AGENDA_WRITE =
            "hasRole('ADMIN') or hasAuthority('LEAVE_MANAGEMENT') or hasAuthority('LEAVE_MANAGEMENT_WRITE') "
                    + "or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')";

    private final AgendaService agendaService;
    private final UserAccountRepository users;

    @GetMapping
    @PreAuthorize(AGENDA_READ)
    public String page(
            @RequestParam(name = "stateDate", required = false) LocalDate stateDate,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "calendarUserId", required = false) Long calendarUserId,
            @RequestParam(name = "calendarMonth", required = false) String calendarMonth,
            Locale locale,
            Authentication auth,
            Model model) {
        model.addAttribute("pageTitleKey", "nav.agenda");
        YearMonth ym = parseCalendarMonth(calendarMonth, stateDate);
        String viewer = auth != null ? auth.getName() : null;
        model.addAttribute("agendaPage", agendaService.page(stateDate, q, locale, calendarUserId, ym, viewer));
        return "app/agenda";
    }

    private static YearMonth parseCalendarMonth(String calendarMonth, LocalDate stateDate) {
        if (calendarMonth != null && !calendarMonth.isBlank()) {
            try {
                return YearMonth.parse(calendarMonth.trim());
            } catch (Exception ignored) {
                // fall through
            }
        }
        LocalDate ref = stateDate != null ? stateDate : LocalDate.now();
        return YearMonth.from(ref);
    }

    @PostMapping("/absence/add")
    @PreAuthorize(AGENDA_WRITE)
    public String addAbsence(
            @RequestParam("username") String username,
            @RequestParam("type") String type,
            @RequestParam("from") LocalDate from,
            @RequestParam("to") LocalDate to,
            @RequestParam(name = "remarks", required = false) String remarks,
            @RequestParam(name = "stateDate", required = false) LocalDate stateDate,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "calendarUserId", required = false) Long calendarUserId,
            @RequestParam(name = "calendarMonth", required = false) String calendarMonth,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            Long actorId = users.findByUsernameIgnoreCase(auth.getName()).map(UserAccount::getId).orElse(null);
            agendaService.addAbsence(username, type, from, to, remarks, actorId);
            ra.addFlashAttribute("successKey", "agenda.flash.absenceAdded");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", mapError(e.getMessage()));
        }
        return redirectWithFilters(stateDate, q, calendarUserId, calendarMonth);
    }

    @PostMapping("/absence/{id}/delete")
    @PreAuthorize(AGENDA_WRITE)
    public String deleteAbsence(
            @PathVariable("id") long id,
            @RequestParam(name = "stateDate", required = false) LocalDate stateDate,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "calendarUserId", required = false) Long calendarUserId,
            @RequestParam(name = "calendarMonth", required = false) String calendarMonth,
            RedirectAttributes ra) {
        try {
            agendaService.deleteAbsence(id);
            ra.addFlashAttribute("successKey", "agenda.flash.absenceDeleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "agenda.error.notFound");
        }
        return redirectWithFilters(stateDate, q, calendarUserId, calendarMonth);
    }

    @PostMapping("/holiday/add")
    @PreAuthorize(AGENDA_WRITE)
    public String addHoliday(
            @RequestParam("start") LocalDate start,
            @RequestParam("end") LocalDate end,
            @RequestParam("label") String label,
            @RequestParam(name = "stateDate", required = false) LocalDate stateDate,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "calendarUserId", required = false) Long calendarUserId,
            @RequestParam(name = "calendarMonth", required = false) String calendarMonth,
            RedirectAttributes ra) {
        try {
            agendaService.addHoliday(start, end, label);
            ra.addFlashAttribute("successKey", "agenda.flash.holidayAdded");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", mapError(e.getMessage()));
        }
        return redirectWithFilters(stateDate, q, calendarUserId, calendarMonth);
    }

    @PostMapping("/holiday/{id}/delete")
    @PreAuthorize(AGENDA_WRITE)
    public String deleteHoliday(
            @PathVariable("id") long id,
            @RequestParam(name = "stateDate", required = false) LocalDate stateDate,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "calendarUserId", required = false) Long calendarUserId,
            @RequestParam(name = "calendarMonth", required = false) String calendarMonth,
            RedirectAttributes ra) {
        try {
            agendaService.deleteHoliday(id);
            ra.addFlashAttribute("successKey", "agenda.flash.holidayDeleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "agenda.error.notFound");
        }
        return redirectWithFilters(stateDate, q, calendarUserId, calendarMonth);
    }

    private static String redirectWithFilters(
            LocalDate stateDate, String q, Long calendarUserId, String calendarMonth) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/agenda");
        if (stateDate != null) {
            b.queryParam("stateDate", stateDate);
        }
        if (q != null && !q.isBlank()) {
            b.queryParam("q", q);
        }
        if (calendarUserId != null) {
            b.queryParam("calendarUserId", calendarUserId);
        }
        if (calendarMonth != null && !calendarMonth.isBlank()) {
            b.queryParam("calendarMonth", calendarMonth.trim());
        }
        return "redirect:" + b.build().toUriString();
    }

    private static String mapError(String code) {
        if (code == null) {
            return "agenda.error.generic";
        }
        return switch (code) {
            case "badDates" -> "agenda.error.badDates";
            case "missingFields" -> "agenda.error.missingFields";
            case "overlap" -> "agenda.error.overlap";
            case "unknownUser" -> "agenda.error.unknownUser";
            case "inactiveUser" -> "agenda.error.inactiveUser";
            case "directionScope" -> "agenda.error.directionScope";
            default -> "agenda.error.generic";
        };
    }
}
