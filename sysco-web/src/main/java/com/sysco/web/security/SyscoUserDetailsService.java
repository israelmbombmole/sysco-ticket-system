package com.sysco.web.security;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.domain.UserPermission;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.repo.UserPermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyscoUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserPermissionRepository userPermissionRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String key = LoginIdentifierPolicy.parseLoginKey(username)
                .orElseThrow(() -> new UsernameNotFoundException("invalid login identifier"));
        UserAccount u = userAccountRepository
                .findByUsernameIgnoreCase(key)
                .or(() -> userAccountRepository.findByMatriculeIgnoreCase(key))
                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
        if (!u.isActiveBool()) {
            throw new DisabledException("User inactive");
        }
        String rk = RoleKeys.normalizeForScope(u.getRole());
        boolean lockoutExempt = "SUPER_ADMIN".equals(rk) || "ADMIN".equals(rk);
        if (!lockoutExempt
                && u.getLockUntil() != null
                && u.getLockUntil().isAfter(java.time.Instant.now())) {
            throw new LockedException("User locked");
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        String rawRole = u.getRole() == null ? "" : u.getRole().trim();
        String roleKey = rawRole.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleKey));
        if ("SUPER_ADMIN".equals(roleKey)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        for (UserPermission p : userPermissionRepository.findByUserId(u.getId())) {
            if (p.getPermission() != null && !p.getPermission().isBlank()) {
                authorities.add(new SimpleGrantedAuthority(p.getPermission().trim()));
            }
        }
        return User.builder()
                .username(u.getUsername())
                .password(u.getPasswordHash())
                .disabled(false)
                .authorities(authorities)
                .build();
    }
}
