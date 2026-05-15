package com.sysco.web.web;

import com.sysco.web.service.JobSchedulerService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

@Controller
@RequestMapping("/app/job-scheduler")
@RequiredArgsConstructor
public class JobSchedulerController {

    private final JobSchedulerService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('JOB_SCHEDULER') or hasAuthority('JOB_SCHEDULER_READ') or hasAuthority('JOB_SCHEDULER_WRITE')")
    public String page(Model model, Authentication auth) {
        model.addAttribute("pageTitleKey", "nav.jobScheduler");
        model.addAttribute("page", service.page(auth != null ? auth.getName() : ""));
        return "app/job-scheduler";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('JOB_SCHEDULER') or hasAuthority('JOB_SCHEDULER_WRITE')")
    public String save(
            @RequestParam("title") String title,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "dueDate", required = false) LocalDate dueDate,
            @RequestParam(name = "dueTime", required = false) LocalTime dueTime,
            @RequestParam(name = "reminderMinutes", required = false, defaultValue = "60") int reminderMinutes,
            @RequestParam(name = "recurrence", required = false, defaultValue = "ONCE") String recurrence,
            @RequestParam(name = "assigneeIds", required = false) List<Long> assigneeIds,
            @RequestParam(name = "ticketId", required = false) String ticketIdRaw,
            Authentication auth,
            RedirectAttributes ra) {
        Long ticketId = parseOptionalLong(ticketIdRaw);
        try {
            service.saveJob(
                    title,
                    description,
                    dueDate,
                    dueTime,
                    reminderMinutes,
                    recurrence,
                    assigneeIds,
                    ticketId,
                    auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "scheduler.flash.saved");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", mapSchedulerError(e.getMessage()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "scheduler.error.save");
        }
        return "redirect:/app/job-scheduler";
    }

    private static Long parseOptionalLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String mapSchedulerError(String code) {
        if (code == null) {
            return "scheduler.error.save";
        }
        return switch (code) {
            case "badTitle" -> "scheduler.error.badTitle";
            case "badDueDate" -> "scheduler.error.badDueDate";
            case "badDueTime" -> "scheduler.error.badDueTime";
            case "assigneeRequired" -> "scheduler.error.assigneeRequired";
            case "badAssignee" -> "scheduler.error.badAssignee";
            case "inactiveAssignee" -> "scheduler.error.inactiveAssignee";
            case "ticketNotFound" -> "scheduler.error.ticketNotFound";
            case "ticketMerged" -> "scheduler.error.ticketMerged";
            case "noDepartment" -> "scheduler.error.noDepartment";
            default -> "scheduler.error.save";
        };
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('JOB_SCHEDULER') or hasAuthority('JOB_SCHEDULER_WRITE')")
    public String toggle(@PathVariable("id") long id, RedirectAttributes ra) {
        try {
            service.toggle(id);
            ra.addFlashAttribute("successKey", "scheduler.flash.toggled");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "scheduler.error.notFound");
        }
        return "redirect:/app/job-scheduler";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('JOB_SCHEDULER') or hasAuthority('JOB_SCHEDULER_WRITE')")
    public String delete(@PathVariable("id") long id, RedirectAttributes ra) {
        try {
            service.deleteJob(id);
            ra.addFlashAttribute("successKey", "scheduler.flash.deleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "scheduler.error.notFound");
        }
        return "redirect:/app/job-scheduler";
    }

    @PostMapping("/report")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('JOB_SCHEDULER') or hasAuthority('JOB_SCHEDULER_WRITE')")
    public Object report(@RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to, RedirectAttributes ra) {
        try {
            byte[] pdf = service.generatePeriodReportPdf(from, to);
            String filename = "rapport-exploitation-" + from + "_au_" + to + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(new ByteArrayResource(pdf));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "scheduler.error.report");
            return "redirect:/app/job-scheduler";
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "scheduler.error.report");
            return "redirect:/app/job-scheduler";
        }
    }
}
