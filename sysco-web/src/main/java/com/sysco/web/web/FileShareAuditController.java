package com.sysco.web.web;

import com.sysco.web.service.DirectionAuditScopeResolver;
import com.sysco.web.service.FileShareAuditService;
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
@RequestMapping("/app/file-share-audit")
@RequiredArgsConstructor
public class FileShareAuditController {

    private static final String FILE_AUDIT_READ_ACCESS =
            "hasRole('ADMIN') or hasAuthority('FILE_SHARE_AUDIT') or hasAuthority('FILE_SHARE_AUDIT_READ') or "
                    + "hasAuthority('FILE_SHARE_AUDIT_WRITE')";

    private final FileShareAuditService service;
    private final DirectionAuditScopeResolver auditScopeResolver;

    @GetMapping
    @PreAuthorize(FILE_AUDIT_READ_ACCESS)
    public String page(
            @RequestParam(name = "q", required = false) String q, Authentication auth, Model model) {
        model.addAttribute("pageTitleKey", "nav.fileShareAudit");
        model.addAttribute("page", service.page(q, auditScopeResolver.resolve(auth)));
        model.addAttribute("fileShareAuditNoDirection", auditScopeResolver.lacksDirectionForScopedAudit(auth));
        return "app/file-share-audit";
    }

    @GetMapping("/export.csv")
    @PreAuthorize(FILE_AUDIT_READ_ACCESS)
    public ResponseEntity<ByteArrayResource> export(
            @RequestParam(name = "q", required = false) String q, Authentication auth) {
        byte[] bytes = service.exportCsv(q, auditScopeResolver.resolve(auth));
        ByteArrayResource resource = new ByteArrayResource(bytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"file-share-audit.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(bytes.length)
                .body(resource);
    }
}
