package com.sysco.web.web;

import com.sysco.web.service.MyShiftService;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/app/my-shift")
@RequiredArgsConstructor
public class MyShiftController {

    /** Matches {@link com.sysco.web.security.WebSyscoPermissions#isMyShiftModuleVisible}: role implies access even without MY_* authorities. */
    private static final String MY_SHIFT_PAGE =
            "hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('DIRECTEUR') or hasRole('SOUS_DIRECTEUR') "
                    + "or hasAuthority('MY_SHIFT') or hasAuthority('MY_SHIFT_READ') or hasAuthority('MY_SHIFT_WRITE')";

    private final MyShiftService myShiftService;

    @GetMapping
    @PreAuthorize(MY_SHIFT_PAGE)
    public String page(
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "policySd", required = false) Long policySousDirectionId,
            Authentication auth,
            Model model) {
        model.addAttribute("pageTitleKey", "nav.myShift");
        model.addAttribute(
                "view",
                myShiftService.page(
                        direction,
                        q,
                        from,
                        to,
                        auth == null ? null : auth.getName(),
                        auth,
                        policySousDirectionId));
        model.addAttribute("directions", myShiftService.directions());
        return "app/my-shift";
    }

    @PostMapping("/punch")
    @PreAuthorize(
            "hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('DIRECTEUR') or hasRole('SOUS_DIRECTEUR') "
                    + "or hasAuthority('MY_SHIFT') or hasAuthority('MY_SHIFT_WRITE')")
    public String punch(
            @RequestParam(name = "direction", required = false) String direction,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        String username =
                (authentication == null || authentication.getName() == null)
                        ? "agent"
                        : authentication.getName();
        try {
            myShiftService.punch(username, direction, authentication);
            redirectAttributes.addFlashAttribute("successKey", "myShift.flash.punched");
        } catch (IllegalStateException e) {
            switch (e.getMessage()) {
                case "userUnknown":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.userUnknown");
                    break;
                case "outsideWindow":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.outsideWindow");
                    break;
                default:
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.punchFailed");
                    break;
            }
        }
        return redirect(buildRedirectBack(direction, null));
    }

    @PostMapping("/policy")
    @PreAuthorize(
            "hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('DIRECTEUR') or hasRole('SOUS_DIRECTEUR')")
    public String savePolicy(
            @RequestParam(name = "weekMonday", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate weekMonday,
            @RequestParam(name = "sousDirectionId") Long sousDirectionId,
            @RequestParam(name = "arrivalAllowedFrom") @DateTimeFormat(pattern = "HH:mm") LocalTime arrivalAllowedFrom,
            @RequestParam(name = "arrivalOnTimeUntil") @DateTimeFormat(pattern = "HH:mm") LocalTime arrivalOnTimeUntil,
            @RequestParam(name = "arrivalLateUntil") @DateTimeFormat(pattern = "HH:mm") LocalTime arrivalLateUntil,
            @RequestParam(name = "departureAllowedFrom") @DateTimeFormat(pattern = "HH:mm") LocalTime departureAllowedFrom,
            @RequestParam(name = "departureAllowedUntil") @DateTimeFormat(pattern = "HH:mm") LocalTime departureAllowedUntil,
            @RequestParam(name = "direction", required = false) String direction,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            myShiftService.saveWeeklyPolicy(
                    authentication,
                    weekMonday,
                    sousDirectionId,
                    arrivalAllowedFrom,
                    arrivalOnTimeUntil,
                    arrivalLateUntil,
                    departureAllowedFrom,
                    departureAllowedUntil);
            redirectAttributes.addFlashAttribute("successKey", "myShift.flash.policySaved");
        } catch (IllegalStateException e) {
            switch (e.getMessage()) {
                case "policyForbidden":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.policyForbidden");
                    break;
                case "policyScope":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.policyScope");
                    break;
                case "policyBadSd":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.policyBadSd");
                    break;
                case "policyBadTimes":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.policyBadTimes");
                    break;
                default:
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.policySaveFailed");
                    break;
            }
        }
        return redirect(buildRedirectBack(direction, sousDirectionId));
    }

    @PostMapping("/override")
    @PreAuthorize(
            "hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('DIRECTEUR') or hasRole('SOUS_DIRECTEUR')")
    public String grantOverride(
            @RequestParam(name = "target") String target,
            @RequestParam(name = "grantScope", required = false) String grantScope,
            @RequestParam(name = "direction", required = false) String direction,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            myShiftService.grantDayOverride(authentication, target, grantScope);
            redirectAttributes.addFlashAttribute("successKey", "myShift.flash.overrideGranted");
        } catch (IllegalStateException e) {
            switch (e.getMessage()) {
                case "overrideForbidden":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideForbidden");
                    break;
                case "overrideMissingTarget":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideMissingTarget");
                    break;
                case "overrideUnknownUser":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideUnknownUser");
                    break;
                case "overrideOutOfScope":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideOutOfScope");
                    break;
                case "overrideBadScope":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideBadScope");
                    break;
                default:
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideFailed");
                    break;
            }
        }
        return redirect(buildRedirectBack(direction, null));
    }

    @PostMapping("/override/revoke")
    @PreAuthorize(
            "hasRole('ADMIN') or hasRole('SUPER_ADMIN') or hasRole('DIRECTEUR') or hasRole('SOUS_DIRECTEUR')")
    public String revokeOverride(
            @RequestParam(name = "target") String target,
            @RequestParam(name = "direction", required = false) String direction,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            myShiftService.revokeDayOverride(authentication, target);
            redirectAttributes.addFlashAttribute("successKey", "myShift.flash.overrideRevoked");
        } catch (IllegalStateException e) {
            switch (e.getMessage()) {
                case "overrideForbidden":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideForbidden");
                    break;
                case "overrideMissingTarget":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideMissingTarget");
                    break;
                case "overrideUnknownUser":
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideUnknownUser");
                    break;
                default:
                    redirectAttributes.addFlashAttribute("errorKey", "myShift.error.overrideRevokeFailed");
                    break;
            }
        }
        return redirect(buildRedirectBack(direction, null));
    }

    @GetMapping("/report.csv")
    @PreAuthorize(MY_SHIFT_PAGE)
    public ResponseEntity<ByteArrayResource> reportCsv(
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            Authentication authentication) {
        byte[] bytes = myShiftService.exportCsv(direction, q, from, to, authentication);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"myshift-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(bytes.length)
                .body(resource);
    }

    private static String redirect(UriComponentsBuilder b) {
        return "redirect:" + b.encode().build().toUriString();
    }

    private static UriComponentsBuilder buildRedirectBack(String direction, Long policySousDirectionId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath("/app/my-shift");
        if (direction != null && !direction.isBlank()) {
            b.queryParam("direction", direction);
        }
        if (policySousDirectionId != null) {
            b.queryParam("policySd", policySousDirectionId);
        }
        return b;
    }
}
