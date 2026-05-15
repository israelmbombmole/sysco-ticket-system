package com.sysco.web.web;

import com.sysco.web.service.UserManagementService;
import com.sysco.web.service.UserManagementService.UserForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;

@Controller
@RequestMapping("/app/user-management")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_READ') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String page(Model model, Authentication auth) {
        model.addAttribute("pageTitleKey", "nav.userManagement");
        model.addAttribute("page", service.page(auth != null ? auth.getName() : ""));
        return "app/user-management";
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String create(
            @RequestParam("username") String username,
            @RequestParam(name = "matricule", required = false) String matricule,
            @RequestParam(name = "signatureCode", required = false) String signatureCode,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam("role") String role,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "active", defaultValue = "false") boolean active,
            @RequestParam(name = "directionName", required = false) String directionName,
            @RequestParam(name = "sousDirectionName", required = false) String sousDirectionName,
            @RequestParam(name = "permissions", required = false) java.util.List<String> permissions,
            RedirectAttributes ra) {
        try {
            service.create(new UserForm(
                    username, matricule, signatureCode, password, role, email, active, directionName, sousDirectionName, permissions));
            ra.addFlashAttribute("successKey", "userMgmt.flash.created");
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "userMgmt.error.save");
        }
        return "redirect:/app/user-management";
    }

    @PostMapping("/{id}/update")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String update(
            @PathVariable("id") Long id,
            @RequestParam("username") String username,
            @RequestParam(name = "matricule", required = false) String matricule,
            @RequestParam(name = "signatureCode", required = false) String signatureCode,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam("role") String role,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "active", defaultValue = "false") boolean active,
            @RequestParam(name = "directionName", required = false) String directionName,
            @RequestParam(name = "sousDirectionName", required = false) String sousDirectionName,
            @RequestParam(name = "permissions", required = false) java.util.List<String> permissions,
            RedirectAttributes ra) {
        try {
            service.update(id, new UserForm(
                    username, matricule, signatureCode, password, role, email, active, directionName, sousDirectionName, permissions));
            ra.addFlashAttribute("successKey", "userMgmt.flash.updated");
        } catch (Exception e) {
            ra.addFlashAttribute("errorKey", "userMgmt.error.save");
        }
        return "redirect:/app/user-management";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String toggle(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            service.toggleActive(id);
            ra.addFlashAttribute("successKey", "userMgmt.flash.toggled");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "userMgmt.error.notFound");
        }
        return "redirect:/app/user-management";
    }

    @PostMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String role(@PathVariable("id") Long id, @RequestParam("role") String role, RedirectAttributes ra) {
        try {
            service.changeRole(id, role);
            ra.addFlashAttribute("successKey", "userMgmt.flash.role");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "userMgmt.error.notFound");
        }
        return "redirect:/app/user-management";
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String reset(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            service.resetPassword(id);
            ra.addFlashAttribute("successKey", "userMgmt.flash.password");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "userMgmt.error.notFound");
        }
        return "redirect:/app/user-management";
    }

    @PostMapping("/{id}/unlock-login")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String unlockLogin(@PathVariable("id") Long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.clearLoginLockout(id, auth != null ? auth.getName() : "");
            ra.addFlashAttribute("successKey", "userMgmt.flash.unlockLogin");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "userMgmt.error.notFound");
        } catch (IllegalStateException e) {
            if ("unlockNotAllowed".equals(e.getMessage())) {
                ra.addFlashAttribute("errorKey", "userMgmt.error.unlockNotAllowed");
            } else {
                ra.addFlashAttribute("errorKey", "userMgmt.error.notFound");
            }
        }
        return "redirect:/app/user-management";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER_MANAGEMENT') or hasAuthority('USER_MANAGEMENT_WRITE')")
    public String delete(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            service.delete(id);
            ra.addFlashAttribute("successKey", "userMgmt.flash.deleted");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorKey", "userMgmt.error.notFound");
        }
        return "redirect:/app/user-management";
    }
}
