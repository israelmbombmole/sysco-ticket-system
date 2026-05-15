package com.sysco.web.web;

import com.sysco.web.service.DirectionAuditScopeResolver;
import com.sysco.web.service.LoginAuditService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/app/login-audit")
@RequiredArgsConstructor
public class LoginAuditController {

    private static final String LOGIN_AUDIT_READ_ACCESS =
            "hasRole('ADMIN') or hasAuthority('LOGIN_AUDIT') or hasAuthority('LOGIN_AUDIT_READ') or "
                    + "hasAuthority('LOGIN_AUDIT_WRITE')";

    private final LoginAuditService service;
    private final DirectionAuditScopeResolver auditScopeResolver;

    @GetMapping
    @PreAuthorize(LOGIN_AUDIT_READ_ACCESS)
    public String page(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "detailsQ", required = false) String detailsQ,
            Authentication auth,
            Model model) {
        model.addAttribute("pageTitleKey", "nav.loginAudit");
        model.addAttribute("page", service.page(action, from, to, detailsQ, auditScopeResolver.resolve(auth)));
        model.addAttribute("loginAuditNoDirection", auditScopeResolver.lacksDirectionForScopedAudit(auth));
        return "app/login-audit";
    }

    @GetMapping("/export.csv")
    @PreAuthorize(LOGIN_AUDIT_READ_ACCESS)
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "detailsQ", required = false) String detailsQ,
            Authentication auth) {
        byte[] bytes = service.exportCsv(action, from, to, detailsQ, auditScopeResolver.resolve(auth));
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"login-audit.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(bytes.length)
                .body(resource);
    }
}
