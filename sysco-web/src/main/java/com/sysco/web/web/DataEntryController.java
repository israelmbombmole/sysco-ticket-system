package com.sysco.web.web;

import com.sysco.web.service.DataEntryService;
import com.sysco.web.util.DisplayDateFormatter;
import com.sysco.web.web.dto.TicketRow;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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
@RequestMapping("/app/data-entry")
@RequiredArgsConstructor
public class DataEntryController {

    private static final String DATA_ENTRY_READ_ACCESS =
            "hasRole('ADMIN') or hasAuthority('DATA_ENTRY') or hasAuthority('DATA_ENTRY_READ') or "
                    + "hasAuthority('DATA_ENTRY_WRITE')";
    private static final String DATA_ENTRY_WRITE_ACCESS =
            "hasRole('ADMIN') or hasAuthority('DATA_ENTRY') or hasAuthority('DATA_ENTRY_WRITE')";

    private final DataEntryService dataEntryService;

    @GetMapping
    @PreAuthorize(DATA_ENTRY_READ_ACCESS)
    public String page(Model model) {
        model.addAttribute("pageTitleKey", "nav.dataEntry");
        model.addAttribute("priorities", DataEntryService.PRIORITIES);
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        model.addAttribute("todayDisplay", DisplayDateFormatter.formatLocalDate(today));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new DataEntryForm("", "", "MEDIUM"));
        }
        return "app/data-entry";
    }

    @PostMapping
    @PreAuthorize(DATA_ENTRY_WRITE_ACCESS)
    public String submit(
            @RequestParam("expediteur") String expediteur,
            @RequestParam("objet") String objet,
            @RequestParam("priority") String priority,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            RedirectAttributes redirectAttributes,
            org.springframework.security.core.Authentication authentication) {

        List<MultipartFile> list =
                files == null ? List.of() : Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).collect(Collectors.toList());

        try {
            String ticketRef = dataEntryService.submit(
                    expediteur, objet, priority, list, authentication.getName());
            redirectAttributes.addFlashAttribute("createdTicketRef", ticketRef);
            redirectAttributes.addFlashAttribute("form", new DataEntryForm("", "", "MEDIUM"));
        } catch (IllegalArgumentException e) {
            if ("requiredFields".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "dataEntry.error.required");
            } else if ("attachmentType".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "dataEntry.error.attachmentType");
            } else if ("tooManyFiles".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "dataEntry.error.tooManyFiles");
            } else if ("noDepartment".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "dataEntry.error.department");
            } else if ("user".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "dataEntry.error.auth");
            } else {
                redirectAttributes.addFlashAttribute("errorKey", "dataEntry.error.generic");
            }
            redirectAttributes.addFlashAttribute("form", new DataEntryForm(expediteur, objet, priority));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorKey", "dataEntry.error.generic");
            redirectAttributes.addFlashAttribute("form", new DataEntryForm(expediteur, objet, priority));
        }

        return "redirect:/app/data-entry";
    }

    @org.springframework.web.bind.annotation.ModelAttribute("recentEntries")
    public List<TicketRow> recentEntries(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return List.of();
        }
        return dataEntryService.recentEntriesForUser(authentication.getName(), 8);
    }

    public record DataEntryForm(String expediteur, String objet, String priority) {
        public String normalizedPriority() {
            if (priority == null || priority.isBlank()) {
                return "MEDIUM";
            }
            String up = priority.trim().toUpperCase(java.util.Locale.ROOT);
            return DataEntryService.PRIORITIES.contains(up) ? up : "MEDIUM";
        }
    }
}
