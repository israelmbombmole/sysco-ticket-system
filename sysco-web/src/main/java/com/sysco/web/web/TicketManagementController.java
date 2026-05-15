package com.sysco.web.web;

import com.sysco.web.service.MyWorkService;
import com.sysco.web.service.TicketManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/app/ticket-management")
@RequiredArgsConstructor
public class TicketManagementController {

    private static final String TICKET_DETAIL_ACCESS =
            "hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_READ') "
                    + "or hasAuthority('TICKET_MANAGEMENT_WRITE') or hasAuthority('TICKET_MONITORING') "
                    + "or hasAuthority('TICKET_MONITORING_READ') or hasAuthority('TICKET_MONITORING_WRITE') "
                    + "or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_READ') "
                    + "or hasAuthority('MY_ACTIVITY_WRITE')";

    private static final String TICKET_CREATOR_MUTATE = "hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE') "
            + "or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')";

    private final TicketManagementService service;
    private final MyWorkService myWorkService;
    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_READ') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String page(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "agent", required = false) String agent,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tstatus", required = false) String tstatus,
            @RequestParam(name = "tagent", required = false) String tagent,
            @RequestParam(name = "tq", required = false) String tq,
            Model model) {
        model.addAttribute("pageTitleKey", "nav.ticketManagement");
        model.addAttribute(
                "page", service.page(status, agent, q, tstatus, tagent, tq, authName()));
        model.addAttribute("escalation", myWorkService.ticketEscalationMenu(authName()));
        return "app/ticket-management";
    }

    @GetMapping("/{id}")
    @PreAuthorize(TICKET_DETAIL_ACCESS)
    public String detail(
            @PathVariable("id") Long id,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            org.springframework.security.core.Authentication auth,
            Locale locale,
            Model model,
            RedirectAttributes ra) {
        try {
            var ticket = service.detail(id, auth == null ? null : auth.getName(), locale);
            model.addAttribute("pageTitleKey", "nav.ticketManagement");
            model.addAttribute("ticket", ticket);
            model.addAttribute("canWrite", canWrite(auth));
            model.addAttribute(
                    "canSelfEditTicket",
                    selfEditTicket(auth, ticket));
            String ticketEditReturnTo = sanitizeReturnTo(returnTo, ticket.id());
            model.addAttribute("ticketEditReturnTo", ticketEditReturnTo);
            model.addAttribute(
                    "ticketDetailBackHref",
                    ticketDetailBackHref(auth, ticketEditReturnTo, ticket.id()));
            model.addAttribute(
                    "showPdfExport",
                    hasTicketManagementAccess(auth)
                            || (auth != null
                                    && ticket.createdBy() != null
                                    && ticket.createdBy().equalsIgnoreCase(auth.getName())));
            model.addAttribute(
                    "canReopenTicket",
                    auth != null && service.mayReopenTicket(id, auth.getName()));
            model.addAttribute("activeTab", (tab == null || tab.isBlank()) ? "comments" : tab.trim().toLowerCase(java.util.Locale.ROOT));
            return "app/ticket-management-detail";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
            return ticketDetailAccessDeniedRedirect(auth);
        }
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String escalateTicketTarget(
            @PathVariable("id") Long id,
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam(name = "crossDirection", defaultValue = "false") boolean crossDirection,
            @RequestParam(name = "reassign", defaultValue = "false") boolean reassign,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "agent", required = false) String agent,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tstatus", required = false) String tstatus,
            @RequestParam(name = "tagent", required = false) String tagent,
            @RequestParam(name = "tq", required = false) String tq,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            myWorkService.escalateTicketFromTicketManagement(
                    id,
                    targetUserId,
                    auth == null ? null : auth.getName(),
                    crossDirection,
                    reassign,
                    canManageAll(auth));
            ra.addFlashAttribute(
                    "successKey", reassign ? "ticketMgmt.flash.reassigned" : "ticketMgmt.flash.escalated");
        } catch (IllegalArgumentException e) {
            if ("target".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.escalationTargetRequired");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
            }
        } catch (IllegalStateException e) {
            mapTicketMgmtEscalationError(e.getMessage(), ra);
        }
        return redirectTicketManagementList(status, agent, q, tstatus, tagent, tq);
    }

    @PostMapping("/{id}/escalate-external")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String escalateTicketExternalFromManagement(
            @PathVariable("id") Long id,
            @RequestParam("targetDirectionId") Long targetDirectionId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "agent", required = false) String agent,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tstatus", required = false) String tstatus,
            @RequestParam(name = "tagent", required = false) String tagent,
            @RequestParam(name = "tq", required = false) String tq,
            Authentication auth,
            RedirectAttributes ra) {
        if (targetDirectionId == null) {
            ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationDirectionRequired");
            return redirectTicketManagementList(status, agent, q, tstatus, tagent, tq);
        }
        try {
            myWorkService.escalateTicketExternalFromTicketManagement(
                    id, targetDirectionId, auth == null ? null : auth.getName(), canManageAll(auth));
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.externalEscalationSent");
        } catch (IllegalArgumentException e) {
            if ("badDirection".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationBadDirection");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
            }
        } catch (IllegalStateException e) {
            mapTicketMgmtExternalEscalationError(e.getMessage(), ra);
        }
        return redirectTicketManagementList(status, agent, q, tstatus, tagent, tq);
    }

    @PostMapping("/tasks/{jobId}/escalate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String escalateTask(
            @PathVariable("jobId") Long jobId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "agent", required = false) String agent,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tstatus", required = false) String tstatus,
            @RequestParam(name = "tagent", required = false) String tagent,
            @RequestParam(name = "tq", required = false) String tq,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            service.escalateTask(jobId, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMgmt.tasks.flash.escalated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.tasks.error.notFound");
        } catch (IllegalStateException e) {
            String code = e.getMessage();
            if ("taskEscalationNotAllowed".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.tasks.error.escalationNotAllowed");
            } else if ("alreadyClosed".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.tasks.error.alreadyClosed");
            } else if ("taskInactive".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.tasks.error.inactive");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.tasks.error.notFound");
            }
        }
        return redirectMgmtList(status, agent, q, tstatus, tagent, tq);
    }

    private static String redirectMgmtList(
            String status, String agent, String q, String tstatus, String tagent, String tq) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/ticket-management");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        if (agent != null && !agent.isBlank()) {
            b.queryParam("agent", agent);
        }
        if (q != null && !q.isBlank()) {
            b.queryParam("q", q);
        }
        if (tstatus != null && !tstatus.isBlank()) {
            b.queryParam("tstatus", tstatus);
        }
        if (tagent != null && !tagent.isBlank()) {
            b.queryParam("tagent", tagent);
        }
        if (tq != null && !tq.isBlank()) {
            b.queryParam("tq", tq);
        }
        return "redirect:" + b.encode().build().toUriString();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String close(
            @PathVariable("id") Long id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.close(id, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.closed");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
        } catch (IllegalStateException e) {
            if ("closureReviewRequired".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.closureReviewRequired");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.tasksPending");
            }
        }
        return redirectTarget(returnTo, id);
    }

    @PostMapping("/{id}/finalize-closure")
    @PreAuthorize(TICKET_DETAIL_ACCESS)
    public String finalizeClosure(
            @PathVariable("id") Long id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            @RequestParam(name = "closureTourAck", defaultValue = "false") boolean closureTourAck,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.finalizeClosureAfterTour(id, auth == null ? null : auth.getName(), closureTourAck);
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.closureFinalized");
        } catch (IllegalArgumentException e) {
            if ("tourRequired".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.closureTourRequired");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
            }
        } catch (IllegalStateException e) {
            String code = e.getMessage();
            if ("notCloseRequested".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.notCloseRequested");
            } else if ("notAllowed".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.closureNotAllowed");
            } else if ("tasksPending".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.tasksPending");
            } else if ("alreadyClosed".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.alreadyClosed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.notAllowed");
            }
        }
        return redirectTarget(returnTo, id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(TICKET_DETAIL_ACCESS)
    public String reopen(
            @PathVariable("id") Long id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.reopen(id, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.reopened");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
        } catch (IllegalStateException e) {
            String code = e.getMessage();
            if ("notClosed".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.reopenNotClosed");
            } else if ("reopenMerged".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.reopenMerged");
            } else if ("reopenNotAllowed".equals(code) || "notAllowed".equals(code)) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.reopenNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.reopenNotAllowed");
            }
        }
        return redirectTarget(returnTo, id);
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String delete(
            @PathVariable("id") Long id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            RedirectAttributes ra) {
        try {
            service.delete(id, authName());
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.deleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
        }
        return redirectTarget(returnTo, id);
    }

    @PostMapping("/{id}/update")
    @PreAuthorize(TICKET_CREATOR_MUTATE)
    public String update(
            @PathVariable("id") Long id,
            @RequestParam("title") String title,
            @RequestParam("priority") String priority,
            @RequestParam("status") String status,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            boolean creatorOnly = !canWrite(auth);
            service.update(id, title, priority, status, auth == null ? null : auth.getName(), creatorOnly);
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.updated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
        }
        return redirectTarget(returnTo, id);
    }

    @PostMapping("/{id}/comment")
    @PreAuthorize(TICKET_CREATOR_MUTATE)
    public String comment(
            @PathVariable("id") Long id,
            @RequestParam("commentText") String commentText,
            @RequestParam(name = "attachment", required = false) MultipartFile[] attachments,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            java.util.List<MultipartFile> files = attachments == null ? java.util.List.of() : java.util.Arrays.stream(attachments)
                    .filter(f -> f != null && !f.isEmpty())
                    .toList();
            boolean creatorOnly = !canWrite(auth);
            service.addComment(
                    id, commentText, files, auth == null ? null : auth.getName(), uploadsDirectory, creatorOnly);
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.commentAdded");
        } catch (IllegalArgumentException e) {
            String key = "ticketMgmt.error.comment";
            if ("commentRequired".equals(e.getMessage())) {
                key = "ticketMgmt.error.commentRequired";
            } else if ("attachmentType".equals(e.getMessage())) {
                key = "ticketMgmt.error.attachmentType";
            } else if ("notFound".equals(e.getMessage())) {
                key = "ticketMgmt.error.notFound";
            }
            ra.addFlashAttribute("errorKey", key);
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.comment");
        }
        return "redirect:/app/ticket-management/" + id + "?tab=comments";
    }

    @PostMapping("/{id}/task-from-ticket")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String createTaskFromTicket(
            @PathVariable("id") Long id,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.createTaskFromTicket(id, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.taskCreated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.notFound");
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.task");
        }
        return "redirect:/app/ticket-management/" + id;
    }

    @PostMapping("/{id}/task")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('TICKET_MANAGEMENT') or hasAuthority('TICKET_MANAGEMENT_WRITE')")
    public String createTask(
            @PathVariable("id") Long id,
            HttpServletRequest request,
            @RequestParam(name = "taskSousDirectionId", required = false) Long taskSousDirectionId,
            @RequestParam(name = "taskTitle", required = false) String taskTitle,
            @RequestParam(name = "taskDescription", required = false) String taskDescription,
            @RequestParam(name = "taskDueAt", required = false) String taskDueAt,
            @RequestParam(name = "priority", required = false) String priority,
            @RequestParam(name = "reminderMinutes", required = false) Integer reminderMinutes,
            @RequestParam(name = "attachments", required = false) MultipartFile[] attachments,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            List<Long> assigneeIds = parseAssigneeIdsFromRequest(request);
            List<MultipartFile> files = attachments == null ? List.of() : Arrays.stream(attachments)
                    .filter(f -> f != null && !f.isEmpty())
                    .toList();
            int created = service.createTask(
                    id,
                    assigneeIds,
                    taskSousDirectionId,
                    taskTitle,
                    taskDescription,
                    taskDueAt,
                    priority,
                    reminderMinutes,
                    files,
                    auth == null ? null : auth.getName());
            ra.addFlashAttribute("tasksCreatedCount", created);
        } catch (IllegalArgumentException e) {
            if ("attachmentType".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.attachmentType");
            } else {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.task");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.task");
        }
        return "redirect:/app/ticket-management/" + id;
    }

    @GetMapping("/{id}/export.pdf")
    @PreAuthorize(TICKET_DETAIL_ACCESS)
    public ResponseEntity<ByteArrayResource> exportPdf(@PathVariable("id") Long id, Authentication auth)
            throws java.io.IOException {
        try {
            if (!hasTicketManagementAccess(auth)) {
                service.requireTicketCreator(id, authName());
            }
            var export = service.exportPdf(id, authName());
            ContentDisposition disposition =
                    ContentDisposition.attachment().filename(export.attachmentFilename(), StandardCharsets.UTF_8).build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(export.pdfBytes().length)
                    .body(new ByteArrayResource(export.pdfBytes()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/comment-attachment/{commentId}")
    @PreAuthorize(TICKET_DETAIL_ACCESS)
    public ResponseEntity<ByteArrayResource> downloadCommentAttachment(
            @PathVariable("commentId") Long commentId,
            @RequestParam(name = "idx", required = false, defaultValue = "0") int idx)
            throws java.io.IOException {
        var commentOpt = service.findComment(commentId);
        if (commentOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var comment = commentOpt.get();
        var paths = comment.attachmentPaths();
        if (paths.isEmpty() || idx < 0 || idx >= paths.size()) {
            return ResponseEntity.notFound().build();
        }
        java.nio.file.Path base = java.nio.file.Path.of(uploadsDirectory).toAbsolutePath().normalize();
        java.nio.file.Path file = java.nio.file.Path.of(paths.get(idx)).toAbsolutePath().normalize();
        if (!file.startsWith(base) || !java.nio.file.Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        String filename = file.getFileName().toString();
        String contentType = java.nio.file.Files.probeContentType(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private static boolean canWrite(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "ROLE_ADMIN".equals(a)
                        || "ADMIN".equals(a)
                        || "TICKET_MANAGEMENT".equals(a)
                        || "TICKET_MANAGEMENT_WRITE".equals(a));
    }

    private static boolean hasTicketManagementAccess(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "ROLE_ADMIN".equals(a)
                        || "ADMIN".equals(a)
                        || "TICKET_MANAGEMENT".equals(a)
                        || "TICKET_MANAGEMENT_READ".equals(a)
                        || "TICKET_MANAGEMENT_WRITE".equals(a));
    }

    private static boolean hasTicketMonitoringAccess(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "ROLE_ADMIN".equals(a)
                        || "ADMIN".equals(a)
                        || "TICKET_MONITORING".equals(a)
                        || "TICKET_MONITORING_READ".equals(a)
                        || "TICKET_MONITORING_WRITE".equals(a));
    }

    /**
     * Back link from ticket detail: honor explicit {@code returnTo} (Mon activité, suivi, etc.); when the user opened the
     * ticket without {@code returnTo}, {@code sanitizedReturnTo} is the ticket detail URL — then fall back to role defaults.
     */
    private static String ticketDetailBackHref(Authentication auth, String sanitizedReturnTo, Long ticketId) {
        if (sanitizedReturnTo != null && isExplicitTicketDetailBackTarget(sanitizedReturnTo, ticketId)) {
            return sanitizedReturnTo;
        }
        if (hasTicketManagementAccess(auth)) {
            return "/app/ticket-management";
        }
        if (hasTicketMonitoringAccess(auth)) {
            return "/app/ticket-monitoring";
        }
        return "/app/my-activity";
    }

    /** True when {@code sanitized} is a real “previous page” (not the placeholder same-ticket detail URL). */
    private static boolean isExplicitTicketDetailBackTarget(String sanitized, Long ticketId) {
        if (sanitized == null || ticketId == null) {
            return false;
        }
        if ("/app/my-activity".equals(sanitized) || sanitized.startsWith("/app/my-activity?")) {
            return true;
        }
        if ("/app/ticket-monitoring".equals(sanitized) || sanitized.startsWith("/app/ticket-monitoring?")) {
            return true;
        }
        if ("/app/my-work".equals(sanitized) || sanitized.startsWith("/app/my-work?")) {
            return true;
        }
        if ("/app/ticket-management".equals(sanitized) || sanitized.startsWith("/app/ticket-management?")) {
            return true;
        }
        String detail = "/app/ticket-management/" + ticketId;
        return !sanitized.equals(detail)
                && !sanitized.startsWith(detail + "?")
                && !sanitized.startsWith(detail + "#");
    }

    private static String ticketDetailAccessDeniedRedirect(Authentication auth) {
        if (hasTicketManagementAccess(auth)) {
            return "redirect:/app/ticket-management";
        }
        if (hasTicketMonitoringAccess(auth)) {
            return "redirect:/app/ticket-monitoring";
        }
        return "redirect:/app/my-activity";
    }

    private static boolean hasMyActivityWrite(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "MY_ACTIVITY".equals(a) || "MY_ACTIVITY_WRITE".equals(a));
    }

    private static boolean selfEditTicket(Authentication auth, com.sysco.web.service.TicketManagementService.TicketDetail ticket) {
        if (auth == null || ticket == null || ticket.createdBy() == null) {
            return false;
        }
        if (!hasMyActivityWrite(auth)) {
            return false;
        }
        return ticket.createdBy().equalsIgnoreCase(auth.getName());
    }

    private static String sanitizeReturnTo(String raw, Long ticketId) {
        String fallback = "/app/ticket-management/" + ticketId;
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String t = raw.trim();
        if (t.contains("\r") || t.contains("\n") || t.contains("..")) {
            return fallback;
        }
        if ("/app/my-activity".equals(t)) {
            return t;
        }
        if (t.startsWith("/app/my-activity?")) {
            return t;
        }
        if ("/app/ticket-monitoring".equals(t)) {
            return t;
        }
        if (t.startsWith("/app/ticket-monitoring?")) {
            return t;
        }
        if ("/app/my-work".equals(t)) {
            return t;
        }
        if (t.startsWith("/app/my-work?")) {
            return t;
        }
        if ("/app/ticket-management".equals(t)) {
            return t;
        }
        if (t.startsWith("/app/ticket-management?")) {
            return t;
        }
        String prefix = "/app/ticket-management/" + ticketId;
        if (t.equals(prefix) || t.startsWith(prefix + "?") || t.startsWith(prefix + "#")) {
            return t;
        }
        return fallback;
    }

    private static String redirectTarget(String returnTo, Long ticketId) {
        return "redirect:" + sanitizeReturnTo(returnTo, ticketId);
    }

    /** Multipart + repeated {@code assigneeIds} checkboxes: read raw parameter values so every selection is bound. */
    private static List<Long> parseAssigneeIdsFromRequest(HttpServletRequest request) {
        String[] values = request.getParameterValues("assigneeIds");
        if (values == null || values.length == 0) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String v : values) {
            if (v == null || v.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(v.trim()));
            } catch (NumberFormatException ignored) {
                // skip invalid tokens
            }
        }
        return List.copyOf(ids);
    }

    private static String authName() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private static boolean canManageAll(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ADMIN".equals(a));
    }

    private static void mapTicketMgmtEscalationError(String code, RedirectAttributes ra) {
        if (code == null) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            return;
        }
        switch (code) {
            case "alreadyClosed" -> ra.addFlashAttribute("errorKey", "ticketMgmt.error.alreadyClosed");
            case "badEscalationTarget" -> ra.addFlashAttribute("errorKey", "myWork.error.badEscalationTarget");
            case "badReassignTarget" -> ra.addFlashAttribute("errorKey", "myWork.error.badReassignTarget");
            default -> ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
        }
    }

    private static void mapTicketMgmtExternalEscalationError(String code, RedirectAttributes ra) {
        if (code == null) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            return;
        }
        switch (code) {
            case "externalEscalationBadActor" ->
                    ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationBadActor");
            case "externalEscalationBadDirection" ->
                    ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationSameDirection");
            case "alreadyClosed" -> ra.addFlashAttribute("errorKey", "ticketMgmt.error.alreadyClosed");
            default -> ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
        }
    }

    private static String redirectTicketManagementList(
            String status, String agent, String q, String tstatus, String tagent, String tq) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/ticket-management");
        appendQueryIfPresent(b, "status", status);
        appendQueryIfPresent(b, "agent", agent);
        appendQueryIfPresent(b, "q", q);
        appendQueryIfPresent(b, "tstatus", tstatus);
        appendQueryIfPresent(b, "tagent", tagent);
        appendQueryIfPresent(b, "tq", tq);
        return "redirect:" + b.build().toUriString();
    }

    private static void appendQueryIfPresent(UriComponentsBuilder b, String name, String value) {
        if (value != null && !value.isBlank()) {
            b.queryParam(name, value);
        }
    }
}
