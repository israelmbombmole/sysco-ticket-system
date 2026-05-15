package com.sysco.web.service;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.DirectionScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectionAuditScopeResolver {

    private final UserAccountRepository users;
    private final DirectionScopeService directionScopeService;

    public DirectionAuditScope resolve(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return new DirectionAuditScope(false, null);
        }
        UserAccount viewer = users.findByUsernameIgnoreCase(auth.getName()).orElse(null);
        if (viewer == null) {
            return new DirectionAuditScope(false, null);
        }
        if (directionScopeService.isSuperAdmin(viewer)) {
            return new DirectionAuditScope(true, null);
        }
        return new DirectionAuditScope(false, viewer.getDirectionId());
    }

    public boolean lacksDirectionForScopedAudit(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return false;
        }
        UserAccount viewer = users.findByUsernameIgnoreCase(auth.getName()).orElse(null);
        return viewer != null
                && !directionScopeService.isSuperAdmin(viewer)
                && viewer.getDirectionId() == null;
    }
}
