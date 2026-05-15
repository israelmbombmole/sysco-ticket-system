package com.sysco.web.web;

import com.sysco.web.service.TicketMonitoringService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/app/ticket-monitoring")
@RequiredArgsConstructor
public class TicketMonitoringController {

    private final TicketMonitoringService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_READ') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String page(Model model) {
        model.addAttribute("pageTitleKey", "nav.ticketMonitoring");
        model.addAttribute("page", service.page(authName()));
        return "app/ticket-monitoring";
    }

    @GetMapping("/export-agent-stats.csv")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_READ') or hasAuthority('TICKET_MONITORING_WRITE')")
    public ResponseEntity<ByteArrayResource> exportAgentStats(
            org.springframework.security.core.Authentication auth, Locale locale) {
        byte[] bytes =
                service.exportAgentStatsCsv(auth == null ? null : auth.getName(), locale);
        String fn = "ticket-monitoring-agent-stats-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fn + "\"")
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String assign(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("assigneeId") Long assigneeId,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.assign(ticketId, assigneeId, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMonitoring.flash.assigned");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMonitoring.error.invalidSelection");
        } catch (IllegalStateException e) {
            if ("assignmentNotAllowed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assignmentNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assigneeInactive");
            }
        }
        return "redirect:/app/ticket-monitoring";
    }

    @PostMapping("/assign-bulk")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String assignBulk(
            @RequestParam(name = "ticketIds", required = false) List<Long> ticketIds,
            @RequestParam(name = "assigneeIds", required = false) List<Long> assigneeIds,
            @RequestParam(name = "createTasks", defaultValue = "false") boolean createTasks,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.assignBulk(ticketIds, assigneeIds, createTasks, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", createTasks
                    ? "ticketMonitoring.flash.assignedWithTasks"
                    : "ticketMonitoring.flash.assigned");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMonitoring.error.invalidSelection");
        } catch (IllegalStateException e) {
            if ("assignmentNotAllowed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assignmentNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assigneeInactive");
            }
        }
        return "redirect:/app/ticket-monitoring";
    }

    @PostMapping("/assign-with-tasks")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String assignWithTasks(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("assigneeIds") List<Long> assigneeIds,
            @RequestParam(name = "taskTitles", required = false) List<String> taskTitles,
            @RequestParam(name = "taskDescriptions", required = false) List<String> taskDescriptions,
            @RequestParam(name = "taskDueAts", required = false) List<String> taskDueAts,
            @RequestParam(name = "reminderMinutes", required = false) List<Integer> reminderMinutes,
            @RequestParam(name = "priorities", required = false) List<String> priorities,
            MultipartHttpServletRequest multipartRequest,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            java.util.List<java.util.List<MultipartFile>> attachmentGroups = new java.util.ArrayList<>();
            int groupCount = assigneeIds == null ? 0 : assigneeIds.size();
            for (int i = 0; i < groupCount; i++) {
                attachmentGroups.add(multipartRequest.getFiles("taskAttachments" + i));
            }
            service.assignWithTasks(
                    ticketId,
                    assigneeIds,
                    taskTitles,
                    taskDescriptions,
                    taskDueAts,
                    reminderMinutes,
                    priorities,
                    attachmentGroups,
                    auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMonitoring.flash.assignedWithTasks");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMonitoring.error.invalidSelection");
        } catch (IllegalStateException e) {
            if ("assignmentNotAllowed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assignmentNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assigneeInactive");
            }
        }
        return "redirect:/app/ticket-monitoring";
    }

    @PostMapping("/delegate-task")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String delegateTask(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("assigneeId") Long assigneeId,
            @RequestParam(name = "taskTitle", required = false) String taskTitle,
            @RequestParam(name = "taskDescription", required = false) String taskDescription,
            @RequestParam(name = "taskDueAt", required = false) String taskDueAt,
            @RequestParam(name = "reminderMinutes", required = false) Integer reminderMinutes,
            @RequestParam(name = "priority", required = false) String priority,
            @RequestParam(name = "attachments", required = false) MultipartFile[] attachments,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.createDelegationTask(
                    ticketId,
                    assigneeId,
                    taskTitle,
                    taskDescription,
                    taskDueAt,
                    reminderMinutes,
                    priority,
                    attachments == null ? java.util.List.of() : java.util.Arrays.stream(attachments).toList(),
                    auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMonitoring.flash.taskDelegated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMonitoring.error.invalidSelection");
        } catch (IllegalStateException e) {
            if ("assignmentNotAllowed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assignmentNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assigneeInactive");
            }
        }
        return "redirect:/app/ticket-monitoring";
    }

    @PostMapping("/delegate-tasks")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String delegateTasks(
            @RequestParam("ticketId") Long ticketId,
            @RequestParam("assigneeIds") List<Long> assigneeIds,
            @RequestParam(name = "taskTitles", required = false) List<String> taskTitles,
            @RequestParam(name = "taskDescriptions", required = false) List<String> taskDescriptions,
            @RequestParam(name = "taskDueAts", required = false) List<String> taskDueAts,
            @RequestParam(name = "reminderMinutes", required = false) List<Integer> reminderMinutes,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.createDelegationTasks(
                    ticketId,
                    assigneeIds,
                    taskTitles,
                    taskDescriptions,
                    taskDueAts,
                    reminderMinutes,
                    auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMonitoring.flash.taskDelegated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMonitoring.error.invalidSelection");
        } catch (IllegalStateException e) {
            if ("assignmentNotAllowed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assignmentNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.assigneeInactive");
            }
        }
        return "redirect:/app/ticket-monitoring";
    }

    @PostMapping("/{ticketId}/escalate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String escalate(
            @PathVariable("ticketId") Long ticketId,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.escalate(ticketId, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMonitoring.flash.escalated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMonitoring.error.invalidSelection");
        } catch (IllegalStateException e) {
            if ("escalationNotAllowed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.escalationNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.alreadyClosed");
            }
        }
        return "redirect:/app/ticket-monitoring";
    }

    @PostMapping("/{ticketId}/close")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_WRITE')")
    public String close(
            @PathVariable("ticketId") Long ticketId,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.close(ticketId, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMonitoring.flash.closed");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMonitoring.error.invalidSelection");
        } catch (IllegalStateException e) {
            if ("closureReviewRequired".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.closureReviewRequired");
            } else if ("tasksPending".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.tasksPending");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMonitoring.error.tasksPending");
            }
        }
        return "redirect:/app/ticket-monitoring";
    }

    private static String authName() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
