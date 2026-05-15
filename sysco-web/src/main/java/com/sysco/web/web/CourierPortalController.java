package com.sysco.web.web;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.service.CourierPortalService;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/app/courier")
@RequiredArgsConstructor
public class CourierPortalController {

    private final CourierPortalService courierPortalService;
    private final UserAccountRepository userAccountRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_READ') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String page(
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) Long selected,
            Authentication authentication,
            Model model) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();

        var view = courierPortalService.buildPortalView(ua, dir, status, selected);

        model.addAttribute("pageTitleKey", "nav.courier");
        model.addAttribute("view", view);
        model.addAttribute("filterDir", dir);
        model.addAttribute("filterStatus", status);
        model.addAttribute("selectedParam", selected);
        model.addAttribute("filterQs", filterQueryPrefix(dir, status));
        model.addAttribute("courierPriorities", CourierPortalService.PRIORITIES);
        return "app/courier";
    }

    private static String filterQueryPrefix(Long dir, String status) {
        StringBuilder sb = new StringBuilder();
        if (dir != null) {
            sb.append("dir=").append(dir).append("&");
        }
        if (status != null && !status.isBlank()) {
            sb.append("status=").append(status).append("&");
        }
        return sb.toString();
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String register(
            @RequestParam("objet") String objet,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "sender", required = false) String sender,
            @RequestParam(value = "priority", required = false, defaultValue = "MEDIUM") String priority,
            @RequestParam(value = "registrationDate", required = false) String registrationDate,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        String rk = RoleKeys.normalizeForScope(ua.getRole());

        var list = files == null
                ? java.util.List.<MultipartFile>of()
                : Arrays.stream(files).filter(f -> f != null && !f.isEmpty()).collect(Collectors.toList());

        try {
            long id = courierPortalService.register(objet, description, sender, priority, registrationDate, list, ua, rk);
            ra.addFlashAttribute("flashSuccess", "courier.flash.registered");
            return redirectToCourier(dir, status, id);
        } catch (IllegalArgumentException e) {
            if ("attachmentType".equals(e.getMessage())) {
                ra.addFlashAttribute("flashError", "courier.error.attachmentType");
            } else if ("tooManyFiles".equals(e.getMessage())) {
                ra.addFlashAttribute("flashError", "courier.error.tooManyFiles");
            } else if ("packet".equals(e.getMessage()) || "direction".equals(e.getMessage()) || "sous".equals(e.getMessage())) {
                ra.addFlashAttribute("flashError", "courier.error.notFound");
            } else {
                ra.addFlashAttribute("flashError", "courier.error.titleRequired");
            }
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, null);
    }

    @PostMapping("/direction")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String direction(
            @RequestParam Long packetId,
            @RequestParam Long directionId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.applyDirection(packetId, directionId, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.direction");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    @PostMapping("/sous")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String sous(
            @RequestParam Long packetId,
            @RequestParam Long sousId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.applySous(packetId, sousId, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.sous");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException ex) {
            if ("sous_mismatch".equals(ex.getMessage())) {
                ra.addFlashAttribute("flashError", "courier.error.sousMismatch");
            } else if ("direction_first".equals(ex.getMessage())) {
                ra.addFlashAttribute("flashError", "courier.error.needDirection");
            } else {
                ra.addFlashAttribute("flashError", "courier.error.notAllowed");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    @PostMapping("/resolve")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String resolve(
            @RequestParam Long packetId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {

        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.resolve(packetId, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.resolved");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    @PostMapping("/assign-sous-directeur")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String assignSousDirecteur(
            @RequestParam Long packetId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {
        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.assignSousDirecteur(packetId, userId, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.assigned");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    @PostMapping("/secretaire-assign-permission")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String toggleSecretaireAssignPermission(
            @RequestParam Long packetId,
            @RequestParam boolean enabled,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {
        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.setSecretaireCanAssignSousDirecteur(packetId, enabled, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.secretairePermission");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    @PostMapping("/assign-inspecteur")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String assignInspecteur(
            @RequestParam Long packetId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {
        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.assignInspecteur(packetId, userId, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.assigned");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    @PostMapping("/assign-controleur")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String assignControleur(
            @RequestParam Long packetId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {
        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.assignControleur(packetId, userId, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.assigned");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    @PostMapping("/assign-verificateur")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public String assignVerificateur(
            @RequestParam Long packetId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long dir,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            Authentication authentication,
            RedirectAttributes ra) {
        UserAccount ua =
                userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        try {
            courierPortalService.assignVerificateur(packetId, userId, ua);
            ra.addFlashAttribute("flashSuccess", "courier.flash.assigned");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", "courier.error.notFound");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("flashError", "courier.error.notAllowed");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "courier.error.generic");
        }
        return redirectToCourier(dir, status, packetId);
    }

    private static String redirectToCourier(Long dir, String status, Long selected) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/courier");
        if (dir != null) {
            b.queryParam("dir", dir);
        }
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        if (selected != null) {
            b.queryParam("selected", selected);
        }
        return "redirect:" + b.build().toUriString();
    }

    @GetMapping("/attachment/{packetId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PHYSICAL_COURIER') or hasAuthority('PHYSICAL_COURIER_READ') or hasAuthority('PHYSICAL_COURIER_WRITE')")
    public ResponseEntity<ByteArrayResource> attachment(
            @PathVariable("packetId") Long packetId,
            @RequestParam(name = "download", defaultValue = "false") boolean download,
            Authentication authentication) throws Exception {
        UserAccount ua = userAccountRepository.findByUsernameIgnoreCase(authentication.getName()).orElseThrow();
        var f = courierPortalService.attachment(packetId, ua);
        byte[] bytes = Files.readAllBytes(f.path());
        String detected = Files.probeContentType(f.path());
        MediaType mediaType = (detected == null || detected.isBlank())
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(detected);
        String cd = (download ? "attachment" : "inline") + "; filename=\"" + f.fileName() + "\"";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                .body(new ByteArrayResource(bytes));
    }
}
