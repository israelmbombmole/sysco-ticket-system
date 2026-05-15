package com.sysco.web.web;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.service.CourierManagementService;
import com.sysco.web.service.CourierPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/app/courier-management")
@RequiredArgsConstructor
public class CourierManagementController {

    private final CourierManagementService courierManagementService;
    private final UserAccountRepository userAccountRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_READ') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String page(
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long selected,
            Authentication authentication,
            Model model) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();

        var page = courierManagementService.buildPage(ua, dir, status, selected, q);

        model.addAttribute("pageTitleKey", "nav.courierManagement");
        model.addAttribute("page", page);
        model.addAttribute("filterDir", dir);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterQs", filterQueryPrefix(dir, status, q));
        model.addAttribute("courierPriorities", CourierPortalService.PRIORITIES);
        return "app/courier-management";
    }

    private static String filterQueryPrefix(Long dir, String status, String q) {
        StringBuilder sb = new StringBuilder();
        if (dir != null) {
            sb.append("dir=").append(dir).append("&");
        }
        if (status != null && !status.isBlank()) {
            sb.append("status=").append(status).append("&");
        }
        if (q != null && !q.isBlank()) {
            sb.append("q=").append(org.springframework.web.util.UriUtils.encodeQueryParam(q, java.nio.charset.StandardCharsets.UTF_8)).append("&");
        }
        return sb.toString();
    }

    @PostMapping("/update")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String update(
            @RequestParam Long packetId,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false, defaultValue = "MEDIUM") String priority,
            @RequestParam(required = false) String registrationDate,
            @RequestParam(required = false) String attachmentPath,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierManagementService.adminUpdate(
                    packetId, title, description, sender, priority, registrationDate, attachmentPath, ua);
            ra.addFlashAttribute("flashSuccess", "courierMgmt.flash.updated");
        } catch (IllegalArgumentException e) {
            if ("packet".equals(e.getMessage())) {
                ra.addFlashAttribute("flashError", "courierMgmt.error.notFound");
            } else {
                ra.addFlashAttribute("flashError", "courierMgmt.error.titleRequired");
            }
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.generic");
        }
        return redirect(dir, status, q, packetId);
    }

    @PostMapping("/redirect-primary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String redirectPrimary(
            @RequestParam Long packetId,
            @RequestParam Long directionId,
            @RequestParam(defaultValue = "false") boolean reopenWorkflow,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierManagementService.redirectPrimary(packetId, directionId, reopenWorkflow, ua);
            ra.addFlashAttribute("flashSuccess", "courierMgmt.flash.redirected");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.generic");
        }
        return redirect(dir, status, q, packetId);
    }

    @PostMapping("/extra/add")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String extraAdd(
            @RequestParam Long packetId,
            @RequestParam Long directionId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierManagementService.addExtraDirection(packetId, directionId, ua);
            ra.addFlashAttribute("flashSuccess", "courierMgmt.flash.extraAdded");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notFound");
        } catch (IllegalStateException ex) {
            if ("already_primary".equals(ex.getMessage())) {
                ra.addFlashAttribute("flashError", "courierMgmt.error.extraDup");
            } else {
                ra.addFlashAttribute("flashError", "courierMgmt.error.notAllowed");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.generic");
        }
        return redirect(dir, status, q, packetId);
    }

    @PostMapping("/extra/remove")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String extraRemove(
            @RequestParam Long packetId,
            @RequestParam Long directionId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierManagementService.removeExtraDirection(packetId, directionId, ua);
            ra.addFlashAttribute("flashSuccess", "courierMgmt.flash.extraRemoved");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.generic");
        }
        return redirect(dir, status, q, packetId);
    }

    @PostMapping("/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String delete(
            @RequestParam Long packetId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) String q,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierManagementService.deletePacket(packetId, ua);
            ra.addFlashAttribute("flashSuccess", "courierMgmt.flash.deleted");
            return redirect(dir, status, q, null);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courierMgmt.error.generic");
        }
        return redirect(dir, status, q, packetId);
    }

    private static String redirect(Long dir, String status, String q, Long selected) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/courier-management");
        if (dir != null) {
            b.queryParam("dir", dir);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        if (q != null && !q.isBlank()) {
            b.queryParam("q", q);
        }
        if (selected != null) {
            b.queryParam("selected", selected);
        }
        return "redirect:" + b.build().toUriString();
    }
}
