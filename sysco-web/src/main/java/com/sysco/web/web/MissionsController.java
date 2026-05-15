package com.sysco.web.web;

import com.sysco.web.service.MissionAssistantService;
import com.sysco.web.service.MissionService;
import com.sysco.web.service.MissionService.MissionDetailContext;
import com.sysco.web.service.MissionService.MissionForm;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app/missions")
@RequiredArgsConstructor
public class MissionsController {

    private final MissionService missionService;
    private final MissionAssistantService missionAssistantService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_READ') or hasAuthority('MISSIONS_WRITE')")
    public String page(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "selected", required = false) String selected,
            @RequestParam(name = "tab", required = false) String tab,
            Authentication auth,
            Model model) {

        model.addAttribute("pageTitleKey", "nav.missions");
        model.addAttribute("page", missionService.page(auth, status, q, from, to, selected, tab));
        model.addAttribute("statusOptions", List.of("PLANNED", "IN_PROGRESS", "REPORTED"));
        model.addAttribute("participantChoices", missionService.participantChoices());
        model.addAttribute("defaultMissionStart", LocalDate.now());
        model.addAttribute("defaultMissionEnd", LocalDate.now().plusWeeks(1));
        model.addAttribute("missionsAssistantLiveAi", missionAssistantService.isLiveAiConfigured());
        return "app/missions";
    }

    @GetMapping("/{code:M-[0-9]{4}-[0-9]+}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_READ') or hasAuthority('MISSIONS_WRITE')")
    public String missionDetail(
            @PathVariable("code") String code,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Authentication auth,
            Model model) {
        try {
            MissionDetailContext ctx = missionService.detailContext(code, auth);
            model.addAttribute("detail", ctx.detail());
            model.addAttribute("canManageMission", ctx.canManage());
            model.addAttribute("activeTab", "report".equalsIgnoreCase(tab) ? "report" : "mission");
            model.addAttribute("pageTitleKey", "nav.missions");
            model.addAttribute("missionDetailBackHref", sanitizeMissionReturnTo(returnTo));
            return "app/mission-detail";
        } catch (IllegalArgumentException e) {
            if ("forbidden".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** Relative URL only; used by mission detail "back" navigation. */
    private static String sanitizeMissionReturnTo(String raw) {
        String fallback = "/app/missions";
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String t = raw.trim();
        if (t.contains("\r") || t.contains("\n") || t.contains("..")) {
            return fallback;
        }
        if (!t.startsWith("/app/missions")) {
            return fallback;
        }
        return t;
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_WRITE')")
    public String saveMission(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam("title") String title,
            @RequestParam("site") String site,
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate,
            @RequestParam("status") String status,
            @RequestParam(name = "leadUserId", required = false) Long leadUserId,
            @RequestParam(name = "participantMenIds", required = false) List<Long> participantMenIds,
            @RequestParam(name = "participantWomenIds", required = false) List<Long> participantWomenIds,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "objectives", required = false) String objectives,
            @RequestParam(name = "orderReference", required = false) String orderReference,
            @RequestParam(name = "orderIssueDate", required = false) LocalDate orderIssueDate,
            @RequestParam(name = "orderIssuedBy", required = false) String orderIssuedBy,
            @RequestParam(name = "orderBody", required = false) String orderBody,
            @RequestParam(name = "transportDetail", required = false) String transportDetail,
            @RequestParam(name = "durationNote", required = false) String durationNote,
            @RequestParam(name = "expensesNote", required = false) String expensesNote,
            @RequestParam(name = "departureNote", required = false) String departureNote,
            @RequestParam(name = "returnNote", required = false) String returnNote,
            @RequestParam(name = "observationsNote", required = false) String observationsNote,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        try {
            String savedCode =
                    missionService.saveMission(
                            new MissionForm(
                                    code,
                                    title,
                                    site,
                                    startDate,
                                    endDate,
                                    status,
                                    leadUserId,
                                    participantMenIds,
                                    participantWomenIds,
                                    description,
                                    objectives,
                                    orderReference,
                                    orderIssueDate,
                                    orderIssuedBy,
                                    orderBody,
                                    transportDetail,
                                    durationNote,
                                    expensesNote,
                                    departureNote,
                                    returnNote,
                                    observationsNote,
                                    ""),
                            auth);
            redirectAttributes.addFlashAttribute("successKey", "missions.flash.saved");
            return "redirect:/app/missions?selected=" + savedCode + "&tab=mission";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorKey", mapMissionError(e.getMessage()));
            String suffix =
                    code != null && !code.isBlank()
                            ? "?selected=" + code.trim() + "&tab=mission"
                            : "?tab=mission";
            return "redirect:/app/missions" + suffix;
        }
    }

    @PostMapping("/report")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_WRITE')")
    public String saveReport(
            @RequestParam("code") String code,
            @RequestParam(name = "reportText", required = false) String reportText,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        try {
            missionService.saveReport(code, reportText, auth);
            redirectAttributes.addFlashAttribute("successKey", "missions.flash.reportSaved");
        } catch (IllegalArgumentException e) {
            if ("missingMission".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.notFound");
            } else if ("forbidden".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.forbidden");
            } else {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.report");
            }
        }
        return reportTabRedirect(code, returnTo);
    }

    @PostMapping("/report/assign")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_WRITE')")
    public String assignReport(
            @RequestParam("code") String code,
            @RequestParam(name = "assigneeUserId", required = false) String assigneeUserIdParam,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        Long assigneeUserId = null;
        if (assigneeUserIdParam != null && !assigneeUserIdParam.isBlank()) {
            try {
                assigneeUserId = Long.parseLong(assigneeUserIdParam.trim());
            } catch (NumberFormatException e) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.invalidAssignee");
                return reportTabRedirect(code, returnTo);
            }
        }
        try {
            missionService.assignReport(code, assigneeUserId, auth);
            redirectAttributes.addFlashAttribute("successKey", "missions.flash.reportAssigneeSaved");
        } catch (IllegalArgumentException e) {
            if ("missingMission".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.notFound");
            } else if ("forbidden".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.forbidden");
            } else if ("invalidAssignee".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.invalidAssignee");
            } else {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.report");
            }
        }
        return reportTabRedirect(code, returnTo);
    }

    @PostMapping(value = "/report/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_WRITE')")
    public String uploadReportAttachment(
            @RequestParam("code") String code,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        try {
            missionService.uploadReportAttachment(code, file, auth);
            redirectAttributes.addFlashAttribute("successKey", "missions.flash.attachmentUploaded");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorKey", mapReportAttachmentError(e.getMessage()));
        } catch (java.io.IOException e) {
            redirectAttributes.addFlashAttribute("errorKey", "missions.error.attachmentFailed");
        }
        return reportTabRedirect(code, returnTo);
    }

    @PostMapping("/report/attachment/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_WRITE')")
    public String deleteReportAttachment(
            @RequestParam("code") String code,
            @RequestParam("attachmentId") long attachmentId,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            Authentication auth,
            RedirectAttributes redirectAttributes) {
        try {
            missionService.deleteReportAttachment(code, attachmentId, auth);
            redirectAttributes.addFlashAttribute("successKey", "missions.flash.attachmentDeleted");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorKey", mapReportAttachmentError(e.getMessage()));
        } catch (java.io.IOException e) {
            redirectAttributes.addFlashAttribute("errorKey", "missions.error.attachmentFailed");
        }
        return reportTabRedirect(code, returnTo);
    }

    @GetMapping("/{code}/report/attachment/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_READ') or hasAuthority('MISSIONS_WRITE')")
    public ResponseEntity<Resource> downloadReportAttachment(
            @PathVariable("code") String code, @PathVariable("id") long id, Authentication auth) {
        try {
            Path path = missionService.resolveReportAttachmentFile(code, id, auth);
            Resource resource = new FileSystemResource(path.toFile());
            String fn = path.getFileName().toString();
            int us = fn.indexOf('_');
            String display = us >= 0 && us + 1 < fn.length() ? fn.substring(us + 1) : fn;
            String safe =
                    display.replace("\"", "'").replaceAll("[\\r\\n]", "_");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safe + "\"")
                    .contentLength(Files.size(path))
                    .body(resource);
        } catch (IllegalArgumentException e) {
            if ("forbidden".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not readable", e);
        }
    }

    private static String reportTabRedirect(String code, String returnTo) {
        if ("detail".equalsIgnoreCase(returnTo)) {
            return "redirect:/app/missions/" + code.trim() + "?tab=report";
        }
        return "redirect:/app/missions?selected=" + code.trim() + "&tab=report";
    }

    private static String mapReportAttachmentError(String code) {
        if (code == null) {
            return "missions.error.attachmentFailed";
        }
        return switch (code) {
            case "forbidden" -> "missions.error.forbidden";
            case "missingMission", "missingAttachment" -> "missions.error.notFound";
            case "attachmentEmpty" -> "missions.error.attachmentEmpty";
            case "attachmentType" -> "missions.error.attachmentType";
            case "attachmentLimit" -> "missions.error.attachmentLimit";
            default -> "missions.error.attachmentFailed";
        };
    }

    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_WRITE')")
    public String deleteMission(
            @RequestParam("code") String code, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            missionService.delete(code, auth);
            redirectAttributes.addFlashAttribute("successKey", "missions.flash.deleted");
        } catch (IllegalArgumentException e) {
            if ("missingMission".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.notFound");
            } else if ("forbidden".equals(e.getMessage())) {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.forbidden");
            } else {
                redirectAttributes.addFlashAttribute("errorKey", "missions.error.save");
            }
        }
        return "redirect:/app/missions";
    }

    @GetMapping("/{code}/order.docx")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_READ') or hasAuthority('MISSIONS_WRITE')")
    public ResponseEntity<ByteArrayResource> downloadOrder(
            @PathVariable("code") String code, Authentication auth) {
        byte[] bytes;
        try {
            bytes = missionService.orderDocumentBytes(code, auth);
        } catch (IllegalArgumentException e) {
            if ("forbidden".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden", e);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission order not found", e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build document", e);
        }
        String safeName = code.replaceAll("[^a-zA-Z0-9._-]", "_");
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeName + "-ordre-mission.docx\"")
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .contentLength(bytes.length)
                .body(resource);
    }

    @GetMapping("/attendance.xlsx")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MISSIONS') or hasAuthority('MISSIONS_READ') or hasAuthority('MISSIONS_WRITE')")
    public ResponseEntity<ByteArrayResource> exportAttendanceExcel(
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            Authentication auth) {
        byte[] bytes;
        try {
            bytes = missionService.exportAttendanceExcel(auth, from, to);
        } catch (IllegalArgumentException e) {
            if ("forbidden".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad request", e);
        }
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"missions-attendance.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(resource);
    }

    private static String mapMissionError(String code) {
        if (code == null) {
            return "missions.error.save";
        }
        return switch (code) {
            case "badDates" -> "missions.error.badDates";
            case "missingFields" -> "missions.error.missingFields";
            case "forbidden" -> "missions.error.forbidden";
            case "badLead", "inactiveLead" -> "missions.error.badLead";
            case "badParticipant", "inactiveParticipant" -> "missions.error.badParticipant";
            case "participantDuplicate" -> "missions.error.duplicateParticipant";
            case "missingMission" -> "missions.error.notFound";
            case "invalidAssignee" -> "missions.error.invalidAssignee";
            default -> "missions.error.save";
        };
    }
}
