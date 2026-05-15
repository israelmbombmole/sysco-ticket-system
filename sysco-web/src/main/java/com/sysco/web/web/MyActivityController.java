package com.sysco.web.web;

import com.sysco.web.service.CreateTicketService;
import com.sysco.web.service.MyActivityService;
import com.sysco.web.service.TicketManagementService;
import java.io.IOException;
import java.util.List;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app/my-activity")
@RequiredArgsConstructor
public class MyActivityController {

    private final MyActivityService service;
    private final CreateTicketService createTicketService;
    private final TicketManagementService ticketManagementService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_READ') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String page(
            @RequestParam(name = "ticketQ", required = false) String ticketQ,
            @RequestParam(name = "fileQ", required = false) String fileQ,
            org.springframework.security.core.Authentication auth,
            Model model) {
        model.addAttribute("pageTitleKey", "nav.myActivity");
        model.addAttribute(
                "page",
                service.page(auth == null ? null : auth.getName(), ticketQ, fileQ, allowTicketSelfEdit(auth)));
        return "app/my-activity";
    }

    @PostMapping("/ticket/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String deleteTicket(
            @PathVariable("id") Long id,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.deleteOwnUnassignedTicket(auth == null ? null : auth.getName(), id);
            ra.addFlashAttribute("successKey", "myActivity.flash.ticketDeleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.notAllowed");
        }
        return "redirect:/app/my-activity";
    }

    @GetMapping("/ticket/{id}/edit")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_READ') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String editTicketForm(
            @PathVariable("id") Long id,
            Authentication auth,
            Model model,
            RedirectAttributes ra) {
        var draft = createTicketService.loadEditableTicket(id, auth == null ? "" : auth.getName());
        if (draft.isEmpty()) {
            ra.addFlashAttribute("errorKey", "myActivity.error.ticketEditNotAllowed");
            return "redirect:/app/my-activity";
        }
        model.addAttribute("pageTitleKey", "myActivity.editTicketTitle");
        CreateTicketController.enrichCreateTicketModel(model, createTicketService);
        model.addAttribute("editTicket", draft.get());
        return "app/create-ticket";
    }

    @PostMapping("/ticket/{id}/update")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String updateTicket(
            @PathVariable("id") Long id,
            @RequestParam("summary") String summary,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam("priority") String priority,
            @RequestParam(name = "departmentId", required = false) Long departmentId,
            @RequestParam("issuePresetCode") String issuePresetCode,
            @RequestParam(name = "reporterSousDirectionId", required = false) Long reporterSousDirectionId,
            @RequestParam(name = "reporterDirectionId", required = false) Long reporterDirectionId,
            @RequestParam(name = "reportingOffice", required = false) String reportingOffice,
            @RequestParam("handlingDirectionId") Long handlingDirectionId,
            @RequestParam(name = "files", required = false) MultipartFile[] files,
            Authentication auth,
            RedirectAttributes ra) {
        List<MultipartFile> fileList =
                files == null
                        ? List.of()
                        : java.util.Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).toList();
        try {
            createTicketService.updateTicket(
                    id,
                    auth == null ? "" : auth.getName(),
                    summary,
                    description,
                    priority,
                    departmentId,
                    issuePresetCode,
                    reporterSousDirectionId,
                    reporterDirectionId,
                    reportingOffice,
                    handlingDirectionId,
                    fileList);
            ra.addFlashAttribute("successKey", "myActivity.flash.ticketUpdated");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            ra.addFlashAttribute(
                    "errorKey",
                    switch (msg) {
                        case "missingFields", "dept" -> "createTicket.error.required";
                        case "user" -> "createTicket.error.auth";
                        case "badFileType" -> "createTicket.error.attachmentType";
                        case "tooManyFiles" -> "createTicket.error.tooManyFiles";
                        case "badPreset" -> "createTicket.error.badPreset";
                        case "directionSdMismatch" -> "createTicket.error.directionMismatch";
                        case "badDirection", "badHandlingDirection", "badSousDirection" -> "createTicket.error.badDirection";
                        default -> "myActivity.error.ticketEditNotAllowed";
                    });
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.ticketEditNotAllowed");
        } catch (IOException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.ticketUpdateFailed");
        }
        return "redirect:/app/my-activity";
    }

    @GetMapping("/ticket/{id}/merge")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String mergeTicketForm(
            @PathVariable("id") long id, Authentication auth, Model model, RedirectAttributes ra) {
        try {
            model.addAttribute("pageTitleKey", "myActivity.merge.pageTitle");
            model.addAttribute(
                    "mergePick",
                    ticketManagementService.mergeSurvivorChoices(
                            id, auth == null ? null : auth.getName(), false, true));
            return "app/my-activity-merge-ticket";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.mergeNotFound");
            return "redirect:/app/my-activity";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.mergeNotAllowed");
            return "redirect:/app/my-activity";
        }
    }

    @PostMapping("/ticket/{id}/merge")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String mergeTicketSubmit(
            @PathVariable("id") long id,
            @RequestParam("survivorTicketId") long survivorTicketId,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            ticketManagementService.mergeTicketsAbsorbIntoSurvivor(
                    id, survivorTicketId, auth == null ? null : auth.getName(), false, true);
            ra.addFlashAttribute("successKey", "myActivity.flash.ticketMerged");
        } catch (IllegalArgumentException e) {
            if ("sameTicket".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.mergeSameTicket");
            } else {
                ra.addFlashAttribute("errorKey", "myActivity.error.mergeNotFound");
            }
        } catch (IllegalStateException e) {
            if ("notMergeable".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "myWork.error.mergeNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "myActivity.error.mergeNotAllowed");
            }
        }
        return "redirect:/app/my-activity";
    }

    @PostMapping("/file/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String deleteSharedFile(
            @PathVariable("id") Long id,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.deleteOwnSharedFile(id, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "myActivity.flash.fileDeleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.notFound");
        }
        return "redirect:/app/my-activity";
    }

    @PostMapping("/file/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String updateVisibility(
            @PathVariable("id") Long id,
            @RequestParam(name = "visibilityMinutes", required = false) Integer visibilityMinutes,
            @RequestParam(name = "visibleUntilEnd", required = false) String visibleUntilEnd,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.updateSharedFileVisibility(
                    id, auth == null ? null : auth.getName(), visibilityMinutes, visibleUntilEnd);
            ra.addFlashAttribute("successKey", "myActivity.flash.visibilityUpdated");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            ra.addFlashAttribute(
                    "errorKey",
                    "visibilityRequired".equals(msg) || "badDateTime".equals(msg)
                            ? "myActivity.error.visibilityInvalid"
                            : "myActivity.error.notFound");
        }
        return "redirect:/app/my-activity";
    }

    @PostMapping("/file/{id}/regenerate-otp")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String regenerateOtp(
            @PathVariable("id") Long id,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            String otp = service.regenerateSharedFileOtp(id, auth == null ? null : auth.getName());
            ra.addFlashAttribute("successKey", "myActivity.flash.otpRegenerated");
            ra.addFlashAttribute("otpInfo", otp);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.notFound");
        }
        return "redirect:/app/my-activity";
    }

    @PostMapping("/file/{id}/replace")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MY_ACTIVITY') or hasAuthority('MY_ACTIVITY_WRITE')")
    public String replaceFile(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.replaceSharedFile(id, auth == null ? null : auth.getName(), file);
            ra.addFlashAttribute("successKey", "myActivity.flash.fileReplaced");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            ra.addFlashAttribute(
                    "errorKey", "emptyFile".equals(msg) ? "myActivity.error.emptyFile" : "myActivity.error.notFound");
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "myActivity.error.replaceFailed");
        }
        return "redirect:/app/my-activity";
    }

    private static boolean allowTicketSelfEdit(Authentication auth) {
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a == null ? "" : a.getAuthority())
                .anyMatch(a -> "MY_ACTIVITY".equals(a) || "MY_ACTIVITY_WRITE".equals(a));
    }
}
