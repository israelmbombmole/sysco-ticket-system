package com.sysco.web.web;

import com.sysco.web.security.WebAuthenticationHelper;
import com.sysco.web.service.FileShareManagementAccessService;
import com.sysco.web.service.FileShareManagementService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
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

@Controller
@RequestMapping("/app/file-share-management")
@RequiredArgsConstructor
public class FileShareManagementController {

    private final FileShareManagementService service;
    private final FileShareManagementAccessService accessService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String page(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "otpQ", required = false) String otpQ,
            Authentication auth,
            HttpServletRequest request,
            Model model) {
        model.addAttribute("pageTitleKey", "nav.fileShareManagement");
        model.addAttribute("page", service.page(q, otpQ, auth, request));
        return "app/file-share-management";
    }

    @GetMapping("/access")
    @PreAuthorize("isAuthenticated()")
    public String accessGate(
            @RequestParam(name = "expired", required = false) String expired,
            @RequestParam(name = "denied", required = false) String denied,
            Authentication auth,
            HttpServletRequest request,
            Model model) {
        if (WebAuthenticationHelper.isSuperAdmin(auth) || WebAuthenticationHelper.isDirectionAdmin(auth)) {
            return "redirect:/app/file-share-management";
        }
        var grant = accessService.sessionExpiryEpochMillis(request.getSession(false));
        if (grant.isPresent() && Instant.now().toEpochMilli() < grant.get()) {
            return "redirect:/app/file-share-management";
        }
        model.addAttribute("pageTitleKey", "fileShareMgmt.access.title");
        model.addAttribute("sessionMinutesDefault", accessService.getDefaultSessionMinutes());
        if ("1".equals(expired)) {
            model.addAttribute("accessBannerKey", "fileShareMgmt.access.expired");
        }
        if ("1".equals(denied)) {
            model.addAttribute("accessBannerKey", "fileShareMgmt.access.denied");
        }
        return "app/file-share-management-access";
    }

    @PostMapping("/access/request")
    @PreAuthorize("isAuthenticated()")
    public String accessRequest(Authentication auth, RedirectAttributes ra) {
        if (WebAuthenticationHelper.isSuperAdmin(auth) || WebAuthenticationHelper.isDirectionAdmin(auth)) {
            return "redirect:/app/file-share-management";
        }
        accessService.createRequest(auth.getName());
        ra.addFlashAttribute("successKey", "fileShareMgmt.access.requestSent");
        return "redirect:/app/file-share-management/access";
    }

    @PostMapping("/access/verify")
    @PreAuthorize("isAuthenticated()")
    public String accessVerify(
            @RequestParam("otp") String otp,
            Authentication auth,
            HttpServletRequest request,
            RedirectAttributes ra) {
        if (WebAuthenticationHelper.isSuperAdmin(auth) || WebAuthenticationHelper.isDirectionAdmin(auth)) {
            return "redirect:/app/file-share-management";
        }
        try {
            accessService.verifyOtpAndOpenSession(auth.getName(), otp, request.getSession(true));
            return "redirect:/app/file-share-management";
        } catch (IllegalArgumentException e) {
            if ("badOtp".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "fileShareMgmt.access.badOtp");
            } else {
                ra.addFlashAttribute("errorKey", "fileShareMgmt.access.badOtp");
            }
        } catch (IllegalStateException e) {
            if ("expired".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "fileShareMgmt.access.otpExpired");
            } else {
                ra.addFlashAttribute("errorKey", "fileShareMgmt.access.badOtp");
            }
        }
        return "redirect:/app/file-share-management/access";
    }

    @PostMapping("/access/leave")
    @PreAuthorize("isAuthenticated()")
    public String accessLeave(HttpServletRequest request) {
        accessService.clearSession(request.getSession(false));
        return "redirect:/app/file-share-management/access";
    }

    @PostMapping("/otp-duration")
    @PreAuthorize("@syscoSec.isDirectionAdminOrSuper(authentication)")
    public String updateOtpDuration(@RequestParam("minutes") int minutes, RedirectAttributes ra) {
        service.updateOtpDuration(minutes);
        ra.addFlashAttribute("successKey", "fileShareMgmt.flash.otpDuration");
        return "redirect:/app/file-share-management";
    }

    @PostMapping("/otp-request/{id}/generate")
    @PreAuthorize("@syscoSec.isDirectionAdminOrSuper(authentication)")
    public String generateOtp(@PathVariable("id") long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.generateOtp(id, auth.getName());
            ra.addFlashAttribute("successKey", "fileShareMgmt.flash.otpGenerated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "fileShareMgmt.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "fileShareMgmt.error.notAllowed");
        }
        return "redirect:/app/file-share-management";
    }

    @PostMapping("/otp-request/{id}/delete")
    @PreAuthorize("@syscoSec.isDirectionAdminOrSuper(authentication)")
    public String deleteOtp(@PathVariable("id") long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.deleteOtpRequest(id, auth.getName());
            ra.addFlashAttribute("successKey", "fileShareMgmt.flash.otpDeleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "fileShareMgmt.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorKey", "fileShareMgmt.error.notAllowed");
        }
        return "redirect:/app/file-share-management";
    }

    @PostMapping("/file/{id}/delete")
    @PreAuthorize("@syscoSec.isDirectionAdminOrSuper(authentication)")
    public String deleteFile(@PathVariable("id") long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.deleteSharedFile(id, auth.getName());
            ra.addFlashAttribute("successKey", "fileShareMgmt.flash.fileDeleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "fileShareMgmt.error.notFound");
        }
        return "redirect:/app/file-share-management";
    }
}
