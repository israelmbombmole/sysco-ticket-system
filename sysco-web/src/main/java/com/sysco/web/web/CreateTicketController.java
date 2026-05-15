package com.sysco.web.web;

import com.sysco.web.service.CreateTicketService;
import com.sysco.web.service.MailOutboundAvailability;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app/create-ticket")
@RequiredArgsConstructor
public class CreateTicketController {

    private final CreateTicketService service;
    private final MailOutboundAvailability mailOutboundAvailability;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CREATE_TICKET') or hasAuthority('CREATE_TICKET_READ') or hasAuthority('CREATE_TICKET_WRITE')")
    public String page(Model model) {
        enrichCreateTicketModel(model, service);
        return "app/create-ticket";
    }

    /** Shared with {@link MyActivityController} for the same form template. */
    public static void enrichCreateTicketModel(Model model, CreateTicketService svc) {
        model.addAttribute("pageTitleKey", "nav.createTicket");
        model.addAttribute("sousDirections", svc.sousDirections());
        model.addAttribute("issuePresets", svc.issuePresetRows());
        model.addAttribute("directionsBySousJson", svc.directionsBySousDirectionJson());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CREATE_TICKET') or hasAuthority('CREATE_TICKET_WRITE')")
    public String submit(
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
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        List<MultipartFile> fileList =
                files == null ? List.of() : java.util.Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).toList();
        try {
            String ticketRef =
                    service.create(
                            summary,
                            description,
                            priority,
                            departmentId,
                            issuePresetCode,
                            reporterSousDirectionId,
                            reporterDirectionId,
                            reportingOffice,
                            handlingDirectionId,
                            fileList,
                            auth == null ? "" : auth.getName());
            ra.addFlashAttribute("successKey", "createTicket.flash.created");
            ra.addFlashAttribute("createdTicketRef", ticketRef);
            if (auth != null && auth.getName() != null && service.creatorEmailMissing(auth.getName())) {
                ra.addFlashAttribute("warnNoEmail", Boolean.TRUE);
            }
            if (!mailOutboundAvailability.isConfigured()) {
                ra.addFlashAttribute("warnMailDisabled", Boolean.TRUE);
            }
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", mapCreateTicketError(e.getMessage()));
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "createTicket.error.create");
        }
        return "redirect:/app/create-ticket";
    }

    private static String mapCreateTicketError(String code) {
        if (code == null) {
            return "createTicket.error.create";
        }
        return switch (code) {
            case "missingFields", "dept" -> "createTicket.error.required";
            case "badFileType" -> "createTicket.error.attachmentType";
            case "tooManyFiles" -> "createTicket.error.tooManyFiles";
            case "user" -> "createTicket.error.auth";
            case "badPreset" -> "createTicket.error.badPreset";
            case "directionSdMismatch" -> "createTicket.error.directionMismatch";
            case "badDirection", "badHandlingDirection", "badSousDirection" -> "createTicket.error.badDirection";
            default -> "createTicket.error.create";
        };
    }
}
