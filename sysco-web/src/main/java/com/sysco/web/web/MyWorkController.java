package com.sysco.web.web;

import com.sysco.web.service.MyWorkService;
import com.sysco.web.service.TicketManagementService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;

@Controller
@RequestMapping("/app/my-work")
@RequiredArgsConstructor
public class MyWorkController {

    private static final String TASK_VIEW_ACCESS =
            "hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_READ') or hasAuthority('MY_WORK_WRITE') "
                    + "or hasAuthority('TICKET_MONITORING') or hasAuthority('TICKET_MONITORING_READ') or hasAuthority('TICKET_MONITORING_WRITE')";

    private final MyWorkService service;
    private final TicketManagementService ticketManagementService;

    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_READ') or hasAuthority('MY_WORK_WRITE')")
    public String page(org.springframework.security.core.Authentication auth, Model model) {
        model.addAttribute("pageTitleKey", "nav.myWork");
        model.addAttribute("page", service.page(auth == null ? null : auth.getName()));
        return "app/my-work";
    }

    @GetMapping("/task/{id}")
    @PreAuthorize(TASK_VIEW_ACCESS)
    public String taskDetail(
            @PathVariable("id") long id,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Authentication auth,
            Model model,
            RedirectAttributes ra) {
        try {
            boolean monitoringReader = hasTicketMonitoringRead(auth);
            model.addAttribute("pageTitleKey", "nav.myWork");
            model.addAttribute(
                    "task",
                    service.taskDetail(id, auth == null ? null : auth.getName(), canManageAll(auth), monitoringReader));
            model.addAttribute("canComment", canCommentTask(auth));
            model.addAttribute("taskDetailBackHref", sanitizeTaskDetailReturnTo(returnTo));
            return "app/task-detail";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notFound");
            return redirectAfterTaskDetailError(auth);
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            return redirectAfterTaskDetailError(auth);
        }
    }

    @PostMapping("/ticket/{id}/close")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_WRITE')")
    public String closeTicket(@PathVariable("id") long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.closeTicket(id, auth == null ? null : auth.getName(), canManageAll(auth));
            ra.addFlashAttribute("successKey", "myWork.flash.ticketClosed");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notFound");
        } catch (IllegalStateException e) {
            if ("closeRequested".equals(e.getMessage())) {
                ra.addFlashAttribute("successKey", "myWork.flash.ticketCloseRequested");
            } else if ("tasksPending".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.tasksPending");
            } else if ("alreadyClosed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.alreadyClosed");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            }
        }
        return "redirect:/app/my-work";
    }

    /**
     * Guided closure request when direct close is not allowed (tasks completed, review / cadre path).
     * Optional {@code notifySeniorUserId} restricts notifications to one cadre on multi-assign collaboration tickets.
     */
    @PostMapping("/ticket/{id}/request-closure")
    @PreAuthorize(
            "hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_READ') or hasAuthority('MY_WORK_WRITE')")
    public String requestTicketClosureFromMyWork(
            @PathVariable("id") long id,
            @RequestParam(name = "notifySeniorUserId", required = false) String notifySeniorUserIdRaw,
            Authentication auth,
            RedirectAttributes ra) {
        Long notifySeniorUserId = null;
        if (notifySeniorUserIdRaw != null && !notifySeniorUserIdRaw.isBlank()) {
            try {
                notifySeniorUserId = Long.parseLong(notifySeniorUserIdRaw.trim());
            } catch (NumberFormatException ex) {
                ra.addFlashAttribute("errorKey", "myWork.error.badClosureDelegate");
                return "redirect:/app/my-work";
            }
        }
        try {
            ticketManagementService.requestTicketClosure(
                    id, auth == null ? null : auth.getName(), notifySeniorUserId);
            ra.addFlashAttribute("successKey", "myWork.flash.ticketCloseRequested");
        } catch (IllegalArgumentException e) {
            if ("badReviewer".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.badClosureDelegate");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notFound");
            }
        } catch (IllegalStateException e) {
            if ("tasksPending".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.tasksPending");
            } else if ("alreadyClosed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "ticketMgmt.error.alreadyClosed");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            }
        }
        return "redirect:/app/my-work";
    }

    @PostMapping("/ticket/{id}/escalate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_WRITE')")
    public String escalateTicket(
            @PathVariable("id") long id,
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam(name = "crossDirection", defaultValue = "false") boolean crossDirection,
            @RequestParam(name = "reassign", defaultValue = "false") boolean reassign,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            boolean reopened =
                    service.escalateTicket(
                            id,
                            targetUserId,
                            auth == null ? null : auth.getName(),
                            canManageAll(auth),
                            crossDirection,
                            reassign);
            if (reassign && reopened) {
                ra.addFlashAttribute("successKey", "myWork.flash.ticketReassignedReopened");
            } else if (reassign) {
                ra.addFlashAttribute("successKey", "myWork.flash.ticketReassigned");
            } else {
                ra.addFlashAttribute("successKey", "myWork.flash.ticketEscalated");
            }
        } catch (IllegalArgumentException e) {
            if ("target".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.escalationTargetRequired");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notFound");
            }
        } catch (IllegalStateException e) {
            if ("badEscalationTarget".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.badEscalationTarget");
            } else if ("badReassignTarget".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.badReassignTarget");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            }
        }
        return "redirect:/app/my-work";
    }

    @PostMapping("/ticket/{id}/escalate-external")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_WRITE')")
    public String escalateTicketExternal(
            @PathVariable("id") long id,
            @RequestParam("targetDirectionId") Long targetDirectionId,
            Authentication auth,
            RedirectAttributes ra) {
        if (targetDirectionId == null) {
            ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationDirectionRequired");
            return "redirect:/app/my-work";
        }
        try {
            service.escalateTicketExternal(
                    id, targetDirectionId, auth == null ? null : auth.getName(), canManageAll(auth));
            ra.addFlashAttribute("successKey", "myWork.flash.externalEscalationSent");
        } catch (IllegalArgumentException e) {
            if ("badDirection".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationBadDirection");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notFound");
            }
        } catch (IllegalStateException e) {
            switch (e.getMessage()) {
                case "externalEscalationBadActor" ->
                        ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationBadActor");
                case "externalEscalationBadDirection" ->
                        ra.addFlashAttribute("errorKey", "myWork.error.externalEscalationSameDirection");
                default -> ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            }
        }
        return "redirect:/app/my-work";
    }

    @PostMapping("/ticket/{id}/start")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_WRITE')")
    public String startTicket(@PathVariable("id") long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.startTicket(id, auth == null ? null : auth.getName(), canManageAll(auth));
            ra.addFlashAttribute("successKey", "myWork.flash.ticketStarted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
        }
        return "redirect:/app/my-work";
    }

    @GetMapping("/ticket/{id}/merge")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_WRITE')")
    public String mergeTicketForm(@PathVariable("id") long id, Authentication auth, Model model, RedirectAttributes ra) {
        try {
            model.addAttribute("pageTitleKey", "nav.myWork");
            model.addAttribute("mergePick", service.mergePick(id, auth == null ? null : auth.getName(), canManageAll(auth)));
            return "app/my-work-merge-ticket";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notFound");
            return "redirect:/app/my-work";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            return "redirect:/app/my-work";
        }
    }

    @PostMapping("/ticket/{id}/merge")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_WRITE')")
    public String mergeTicketSubmit(
            @PathVariable("id") long id,
            @RequestParam("survivorTicketId") long survivorTicketId,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            long survivorId =
                    service.mergeExecute(id, survivorTicketId, auth == null ? null : auth.getName(), canManageAll(auth));
            ra.addFlashAttribute("successKey", "myWork.flash.ticketMergedInto");
            return "redirect:/app/ticket-management/" + survivorId;
        } catch (IllegalArgumentException e) {
            if ("sameTicket".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.mergeSameTicket");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notFound");
            }
            return "redirect:/app/my-work";
        } catch (IllegalStateException e) {
            if ("notMergeable".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.mergeNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
            }
            return "redirect:/app/my-work";
        }
    }

    @PostMapping("/task/{id}/close")
    @PreAuthorize(
            "hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_READ') or hasAuthority('MY_WORK_WRITE')")
    public String closeTask(@PathVariable("id") long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.closeTask(id, auth == null ? null : auth.getName(), canManageAll(auth));
            ra.addFlashAttribute("successKey", "myWork.flash.taskClosed");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
        }
        return "redirect:/app/my-work";
    }

    @PostMapping("/task/{id}/start")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_WRITE')")
    public String startTask(@PathVariable("id") long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.startTask(id, auth == null ? null : auth.getName(), canManageAll(auth));
            ra.addFlashAttribute("successKey", "myWork.flash.taskStarted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
        }
        return "redirect:/app/my-work";
    }

    @PostMapping("/task/{id}/comment")
    @PreAuthorize(
            "hasRole('ADMIN') or hasAuthority('MY_WORK') or hasAuthority('MY_WORK_READ') or hasAuthority('MY_WORK_WRITE')")
    public String addTaskComment(
            @PathVariable("id") long id,
            @RequestParam("commentText") String commentText,
            @RequestParam(name = "attachment", required = false) MultipartFile[] attachmentFiles,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            List<MultipartFile> files =
                    attachmentFiles == null
                            ? List.of()
                            : Arrays.stream(attachmentFiles).filter(f -> f != null && !f.isEmpty()).toList();
            service.addTaskComment(
                    id,
                    commentText,
                    files,
                    auth == null ? null : auth.getName(),
                    canManageAll(auth),
                    uploadsDirectory);
            ra.addFlashAttribute("successKey", "ticketMgmt.flash.commentAdded");
        } catch (IllegalArgumentException e) {
            String key = "ticketMgmt.error.comment";
            if ("commentRequired".equals(e.getMessage())) {
                key = "ticketMgmt.error.commentRequired";
            } else if ("attachmentType".equals(e.getMessage())) {
                key = "ticketMgmt.error.attachmentType";
            }
            ra.addFlashAttribute("errorKey", key);
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myWork.error.notAllowed");
        } catch (IOException e) {
            ra.addFlashAttribute("errorKey", "ticketMgmt.error.comment");
        }
        return "redirect:/app/my-work/task/" + id;
    }

    @GetMapping("/task-comment-attachment/{commentId}")
    @PreAuthorize(TASK_VIEW_ACCESS)
    public ResponseEntity<ByteArrayResource> taskCommentAttachment(
            @PathVariable("commentId") long commentId,
            @RequestParam(name = "idx", required = false, defaultValue = "0") int idx,
            Authentication auth)
            throws IOException {
        Optional<Path> pathOpt =
                service.resolveTaskCommentAttachment(
                        commentId,
                        idx,
                        auth == null ? null : auth.getName(),
                        canManageAll(auth),
                        uploadsDirectory,
                        hasTicketMonitoringRead(auth));
        if (pathOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path file = pathOpt.get();
        byte[] bytes = Files.readAllBytes(file);
        String filename = file.getFileName().toString();
        String contentType = Files.probeContentType(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/task/{id}/attachment")
    @PreAuthorize(TASK_VIEW_ACCESS)
    public ResponseEntity<ByteArrayResource> taskAttachment(
            @PathVariable("id") long id,
            @RequestParam(name = "idx", defaultValue = "0") int idx,
            @RequestParam(name = "mode", defaultValue = "download") String mode,
            Authentication auth) throws java.io.IOException {
        boolean monitoringReader = hasTicketMonitoringRead(auth);
        var task = service.taskDetail(id, auth == null ? null : auth.getName(), canManageAll(auth), monitoringReader);
        var attachments = task.attachments();
        if (idx < 0 || idx >= attachments.size()) {
            return ResponseEntity.notFound().build();
        }
        // Resolve from original stored path index via service task detail order.
        String rawPaths =
                service.rawTaskAttachmentPaths(
                        id, auth == null ? null : auth.getName(), canManageAll(auth), monitoringReader);
        String[] arr = rawPaths == null ? new String[0] : rawPaths.split(";;");
        if (idx >= arr.length) {
            return ResponseEntity.notFound().build();
        }
        java.nio.file.Path file = java.nio.file.Path.of(arr[idx].trim()).toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        String filename = file.getFileName().toString();
        String contentType = java.nio.file.Files.probeContentType(file);
        String disposition = "preview".equalsIgnoreCase(mode) ? "inline" : "attachment";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + filename + "\"")
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private static boolean canManageAll(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ADMIN".equals(a));
    }

    private static boolean canCommentTask(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "ROLE_ADMIN".equals(a)
                        || "ADMIN".equals(a)
                        || "MY_WORK".equals(a)
                        || "MY_WORK_WRITE".equals(a));
    }

    private static boolean hasTicketMonitoringRead(Authentication auth) {
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

    private static boolean hasMyWorkRead(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "ROLE_ADMIN".equals(a)
                        || "ADMIN".equals(a)
                        || "MY_WORK".equals(a)
                        || "MY_WORK_READ".equals(a)
                        || "MY_WORK_WRITE".equals(a));
    }

    private static String sanitizeTaskDetailReturnTo(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/app/my-work";
        }
        String t = raw.trim();
        if (t.contains("\r") || t.contains("\n") || t.contains("..")) {
            return "/app/my-work";
        }
        if ("/app/ticket-monitoring".equals(t) || t.startsWith("/app/ticket-monitoring?")) {
            return t;
        }
        return "/app/my-work";
    }

    private static String redirectAfterTaskDetailError(Authentication auth) {
        if (hasTicketMonitoringRead(auth) && !hasMyWorkRead(auth)) {
            return "redirect:/app/ticket-monitoring";
        }
        return "redirect:/app/my-work";
    }
}
