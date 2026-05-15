package com.sysco.web.web;

import com.sysco.web.service.DataShareService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/app/data-share")
@RequiredArgsConstructor
public class DataShareController {

    private final DataShareService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_READ') or hasAuthority('DATASHARE_WRITE')")
    public String page(
            @RequestParam(name = "fileQ", required = false) String fileQ,
            @RequestParam(name = "sousDirectionId", required = false) Long sousDirectionId,
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam(name = "shareOpen", required = false) String shareOpen,
            org.springframework.security.core.Authentication auth,
            Model model) {
        String username = auth == null ? null : auth.getName();
        boolean sharePanelOpen = "1".equals(shareOpen);
        model.addAttribute("sharePanelOpen", sharePanelOpen);
        model.addAttribute("pageTitleKey", "nav.dataShare");
        model.addAttribute("page", service.page(fileQ, sousDirectionId, directionId, username));
        return "app/data-share";
    }

    @PostMapping("/share")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_WRITE')")
    public String share(
            @RequestParam("file") MultipartFile[] files,
            @RequestParam(name = "expiresAt", required = false) LocalDate expiresAt,
            @RequestParam(name = "visibilityMinutes", required = false) Integer visibilityMinutes,
            @RequestParam(name = "recipientIds", required = false) List<Long> recipientIds,
            @RequestParam(name = "sousDirectionId", required = false) Long sousDirectionId,
            @RequestParam(name = "directionId", required = false) Long directionId,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            List<MultipartFile> fileList = files == null ? List.of() : java.util.Arrays.stream(files)
                    .filter(f -> f != null && !f.isEmpty())
                    .toList();
            var result = service.share(
                    fileList,
                    expiresAt,
                    visibilityMinutes,
                    recipientIds,
                    sousDirectionId,
                    directionId,
                    auth == null ? "" : auth.getName());
            ra.addFlashAttribute("successKey", "dataShare.flash.shared");
            if (!result.generatedOtps().isEmpty()) {
                String summary = result.generatedOtps().stream()
                        .map(o -> o.recipientUsername() + " [" + o.fileName() + "] : " + o.otpCode())
                        .collect(java.util.stream.Collectors.joining(" | "));
                ra.addFlashAttribute("otpInfo", summary);
            }
        } catch (IllegalArgumentException e) {
            String key = "dataShare.error.share";
            if ("invalidShare".equals(e.getMessage())) {
                key = "dataShare.error.invalid";
            } else if ("invalidRecipients".equals(e.getMessage())) {
                key = "dataShare.error.recipients";
            } else if ("user".equals(e.getMessage())) {
                key = "dataShare.error.auth";
            }
            ra.addFlashAttribute("errorKey", key);
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "dataShare.error.share");
        }
        StringBuilder suffix = new StringBuilder();
        if (sousDirectionId != null) {
            suffix.append("&sousDirectionId=").append(sousDirectionId);
        }
        if (directionId != null) {
            suffix.append("&directionId=").append(directionId);
        }
        return "redirect:/app/data-share?shareOpen=1" + suffix;
    }

    @PostMapping("/file/{id}/preview")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_READ') or hasAuthority('DATASHARE_WRITE')")
    public Object preview(
            @PathVariable("id") long id,
            @RequestParam("otp") String otp,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        String username = auth == null ? "" : auth.getName();
        try {
            String fileName = service.fileNameForRecipient(id, username);
            ByteArrayResource data = service.accessFile(id, otp, username, DataShareService.AccessKind.PREVIEW);
            MediaType mediaType =
                    MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
            ContentDisposition inline =
                    ContentDisposition.inline()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build();
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, inline.toString())
                    .body(data);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "badOtp".equals(e.getMessage()) ? "dataShare.error.otp" : "dataShare.error.fileNotFound");
        } catch (IllegalStateException e) {
            if ("expired".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "dataShare.error.expired");
            } else {
                ra.addFlashAttribute("errorKey", "dataShare.error.fileNotFound");
            }
        }
        return "redirect:/app/data-share";
    }

    @PostMapping("/file/{id}/download")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_READ') or hasAuthority('DATASHARE_WRITE')")
    public Object download(
            @PathVariable("id") long id,
            @RequestParam("otp") String otp,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        String username = auth == null ? "" : auth.getName();
        try {
            String fileName = service.fileNameForRecipient(id, username);
            ByteArrayResource data = service.accessFile(id, otp, username, DataShareService.AccessKind.DOWNLOAD);
            MediaType mediaType =
                    MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
            ContentDisposition attachment =
                    ContentDisposition.attachment()
                            .filename(fileName, StandardCharsets.UTF_8)
                            .build();
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, attachment.toString())
                    .body(data);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "badOtp".equals(e.getMessage()) ? "dataShare.error.otp" : "dataShare.error.fileNotFound");
        } catch (IllegalStateException e) {
            if ("expired".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "dataShare.error.expired");
            } else {
                ra.addFlashAttribute("errorKey", "dataShare.error.fileNotFound");
            }
        }
        return "redirect:/app/data-share";
    }

    @PostMapping("/file/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_WRITE')")
    public String updateSenderVisibility(
            @PathVariable("id") long id,
            @RequestParam(name = "visibilityMinutes", required = false) Integer visibilityMinutes,
            @RequestParam(name = "visibleUntilEnd", required = false) String visibleUntilEnd,
            @RequestParam(name = "fileQ", required = false) String fileQ,
            @RequestParam(name = "sousDirectionId", required = false) Long sousDirectionId,
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam(name = "shareOpen", required = false) String shareOpen,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            LocalDateTime end = parseVisibleUntilEnd(visibleUntilEnd);
            service.updateVisibility(
                    id, auth == null ? "" : auth.getName(), visibilityMinutes, end);
            ra.addFlashAttribute("successKey", "dataShare.flash.visibilityUpdated");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            ra.addFlashAttribute(
                    "errorKey",
                    "visibilityRequired".equals(msg) || "badDateTime".equals(msg)
                            ? "dataShare.error.visibilityInvalid"
                            : "dataShare.error.fileNotFound");
        }
        return redirectDataSharePreserveFilters(sousDirectionId, directionId, fileQ, shareOpen);
    }

    @PostMapping("/file/{id}/regenerate-otp")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_WRITE')")
    public String regenerateSenderOtp(
            @PathVariable("id") long id,
            @RequestParam(name = "fileQ", required = false) String fileQ,
            @RequestParam(name = "sousDirectionId", required = false) Long sousDirectionId,
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam(name = "shareOpen", required = false) String shareOpen,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            String otp = service.regenerateOtpForSender(id, auth == null ? "" : auth.getName());
            ra.addFlashAttribute("successKey", "dataShare.flash.otpRegenerated");
            ra.addFlashAttribute("otpInfo", otp);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "dataShare.error.fileNotFound");
        }
        return redirectDataSharePreserveFilters(sousDirectionId, directionId, fileQ, shareOpen);
    }

    @PostMapping("/file/{id}/replace")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_WRITE')")
    public String replaceSenderFile(
            @PathVariable("id") long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "fileQ", required = false) String fileQ,
            @RequestParam(name = "sousDirectionId", required = false) Long sousDirectionId,
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam(name = "shareOpen", required = false) String shareOpen,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.replaceSharedFile(id, auth == null ? "" : auth.getName(), file);
            ra.addFlashAttribute("successKey", "dataShare.flash.fileReplaced");
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            ra.addFlashAttribute(
                    "errorKey",
                    "emptyFile".equals(msg) ? "dataShare.error.emptyFile" : "dataShare.error.fileNotFound");
        } catch (IOException e) {
            ra.addFlashAttribute("errorKey", "dataShare.error.replaceFailed");
        }
        return redirectDataSharePreserveFilters(sousDirectionId, directionId, fileQ, shareOpen);
    }

    @PostMapping("/file/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('DATASHARE') or hasAuthority('DATASHARE_WRITE')")
    public String deleteSenderShare(
            @PathVariable("id") long id,
            @RequestParam(name = "fileQ", required = false) String fileQ,
            @RequestParam(name = "sousDirectionId", required = false) Long sousDirectionId,
            @RequestParam(name = "directionId", required = false) Long directionId,
            @RequestParam(name = "shareOpen", required = false) String shareOpen,
            org.springframework.security.core.Authentication auth,
            RedirectAttributes ra) {
        try {
            service.deleteSenderShare(id, auth == null ? "" : auth.getName());
            ra.addFlashAttribute("successKey", "dataShare.flash.fileDeleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "dataShare.error.fileNotFound");
        }
        return redirectDataSharePreserveFilters(sousDirectionId, directionId, fileQ, shareOpen);
    }

    private static LocalDateTime parseVisibleUntilEnd(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("badDateTime");
        }
    }

    private static String redirectDataSharePreserveFilters(Long sousDirectionId, Long directionId, String fileQ, String shareOpen) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/data-share");
        if (fileQ != null && !fileQ.isBlank()) {
            b.queryParam("fileQ", fileQ);
        }
        if (sousDirectionId != null) {
            b.queryParam("sousDirectionId", sousDirectionId);
        }
        if (directionId != null) {
            b.queryParam("directionId", directionId);
        }
        if ("1".equals(shareOpen)) {
            b.queryParam("shareOpen", "1");
        }
        return "redirect:" + b.build().toUriString();
    }
}
