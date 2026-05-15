package com.sysco.web.service;

import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.LoginIdentifierPolicy;
import com.sysco.web.security.RoleKeys;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthAccountService {

    static final int MAX_ATTEMPTS_BEFORE_LOCK = 5;
    private static final long BASE_LOCK_MINUTES = 10;
    private static final long MAX_LOCK_MINUTES = 300;

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LoginAuditService loginAuditService;

    private Optional<UserAccount> findByLoginKey(String raw) {
        return LoginIdentifierPolicy.parseLoginKey(raw)
                .flatMap(key -> users.findByUsernameIgnoreCase(key).or(() -> users.findByMatriculeIgnoreCase(key)));
    }

    /** Minutes remaining on an active lock, or empty if none (caller supplies fallback). */
    @Transactional(readOnly = true)
    public Optional<Integer> minutesRemainingOnLock(String loginKey) {
        return findByLoginKey(loginKey)
                .map(UserAccount::getLockUntil)
                .filter(until -> until.isAfter(Instant.now()))
                .map(until -> (int) Math.max(1, (Duration.between(Instant.now(), until).toSeconds() + 59) / 60));
    }

    /**
     * After {@link org.springframework.security.authentication.BadCredentialsException} — increments counters or locks.
     *
     * @return redirect path (starting with /login?...)
     */
    @Transactional
    public String afterBadCredentials(String loginKey) {
        Optional<UserAccount> userOpt = findByLoginKey(loginKey);
        if (userOpt.isEmpty()) {
            return "/login?error";
        }
        UserAccount u = userOpt.get();
        String rk = RoleKeys.normalizeForScope(u.getRole());
        if ("SUPER_ADMIN".equals(rk) || "ADMIN".equals(rk)) {
            loginAuditService.record(
                    u.getUsername(),
                    "CONNEXION_ECHOUEE",
                    "SYST\u00c8ME",
                    "Tentative admin invalide (aucune limite de verrouillage)");
            return "/login?error";
        }

        if (u.getLockUntil() != null && u.getLockUntil().isAfter(Instant.now())) {
            int mins = minutesRemainingOnLock(loginKey).orElse(10);
            loginAuditService.record(u.getUsername(), "CONNEXION_BLOQUEE", "SYSTÈME", "Compte verrouillé");
            return "/login?locked&minutes=" + mins;
        }

        int failed = (u.getFailedAttempts() == null ? 0 : u.getFailedAttempts()) + 1;
        u.setFailedAttempts(failed);

        if (failed >= MAX_ATTEMPTS_BEFORE_LOCK) {
            int tierBefore = u.getLoginLockoutTier() == null ? 0 : u.getLoginLockoutTier();
            long lockMinutes = lockMinutesForTier(tierBefore);
            u.setLockUntil(Instant.now().plus(lockMinutes, ChronoUnit.MINUTES));
            u.setLoginLockoutTier(tierBefore + 1);
            u.setFailedAttempts(0);
            users.save(u);
            loginAuditService.record(
                    u.getUsername(),
                    "CONNEXION_BLOQUEE",
                    "SYSTÈME",
                    MAX_ATTEMPTS_BEFORE_LOCK + " tentatives échouées — verrou " + lockMinutes + " min (palier " + tierBefore + ")");
            return "/login?locked&minutes=" + lockMinutes + "&fresh=1";
        }

        users.save(u);
        loginAuditService.record(
                u.getUsername(),
                "CONNEXION_ECHOUEE",
                "SYSTÈME",
                "Tentative " + failed + "/" + MAX_ATTEMPTS_BEFORE_LOCK + " invalide");
        return "/login?attempts=" + failed;
    }

    static long lockMinutesForTier(int tierBeforeApplication) {
        long m = BASE_LOCK_MINUTES;
        for (int i = 0; i < tierBeforeApplication; i++) {
            m = Math.min(m * 2, MAX_LOCK_MINUTES);
        }
        return Math.min(m, MAX_LOCK_MINUTES);
    }

    @Transactional
    public boolean registerSuccess(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return users.findByUsernameIgnoreCase(username).map(u -> {
            u.setFailedAttempts(0);
            u.setLockUntil(null);
            u.setLoginLockoutTier(0);
            users.save(u);
            loginAuditService.record(username, "CONNEXION", "SYSTÈME", "Connexion réussie");
            return u.getMustChangePassword() != null && u.getMustChangePassword() == 1;
        }).orElse(false);
    }

    @Transactional
    public void registerLogout(String username) {
        if (username != null && !username.isBlank()) {
            loginAuditService.record(username, "DÉCONNEXION", "SYSTÈME", "Utilisateur déconnecté");
        }
    }

    @Transactional
    public String issueForgotPassword(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            throw new IllegalArgumentException("missing");
        }
        String normalized = usernameOrEmail.trim();
        UserAccount u = findByLoginKey(normalized)
                .or(() -> users.findByEmailIgnoreCase(normalized))
                .orElseThrow(() -> new IllegalArgumentException("notFound"));
        String temp = randomFiveChars();
        u.setPasswordHash(passwordEncoder.encode(temp));
        u.setMustChangePassword(1);
        u.setFailedAttempts(0);
        u.setLockUntil(null);
        u.setLoginLockoutTier(0);
        users.save(u);
        loginAuditService.record(u.getUsername(), "MOT_DE_PASSE_OUBLIE", "SYSTÈME", "Mot de passe temporaire généré");
        return temp;
    }

    @Transactional
    public void changePassword(String username, String newPassword) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("user");
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            throw new IllegalArgumentException("password");
        }
        UserAccount u = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("user"));
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePassword(0);
        u.setFailedAttempts(0);
        u.setLockUntil(null);
        u.setLoginLockoutTier(0);
        users.save(u);
        loginAuditService.record(username, "MOT_DE_PASSE_MODIFIE", "SYSTÈME", "Mot de passe changé");
    }

    private static String randomFiveChars() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
