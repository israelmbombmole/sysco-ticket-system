package com.sysco.web.service;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.domain.UserPermission;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.repo.UserPermissionRepository;
import com.sysco.web.security.RoleKeys;
import com.sysco.web.util.DisplayDateFormatter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserAccountRepository users;
    private final UserPermissionRepository perms;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;
    private final PasswordEncoder passwordEncoder;
    private final LoginAuditService loginAuditService;
    private final UserNotificationEmailFallback notificationEmailFallback;

    private static final List<String> ROLE_OPTIONS = List.of(
            "SUPER_ADMIN", "ADMIN", "DIRECTEUR", "SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR", "VERIFICATEUR",
            "VERIFICATEUR-ASSISTANT", "COURIER", "SECRETAIRE");

    private static final List<PermissionModule> MODULES = List.of(
            new PermissionModule("DIRECTEUR_DASHBOARD", "DIRECTEUR Tableau de bord"),
            new PermissionModule("DATA_ENTRY", "Saisie des données"),
            new PermissionModule("DATA_MANAGEMENT", "Gestion des données"),
            new PermissionModule("DATASHARE", "Partage de données"),
            new PermissionModule("MY_ACTIVITY", "Mon activité"),
            new PermissionModule("MY_WORK", "Mon travail"),
            new PermissionModule("TICKET_MONITORING", "Suivi des tickets"),
            new PermissionModule("TICKET_MANAGEMENT", "Gestion des tickets"),
            new PermissionModule("FILE_SHARE_MANAGEMENT", "Gestion du partage de fichiers"),
            new PermissionModule("USER_MANAGEMENT", "Gestion des utilisateurs"),
            new PermissionModule("LEAVE_MANAGEMENT", "Agenda"),
            new PermissionModule("LOGIN_AUDIT", "Audit de connexion"),
            new PermissionModule("FILE_SHARE_AUDIT", "Audit du partage de fichiers"),
            new PermissionModule("CREATE_TICKET", "Créer un ticket"),
            new PermissionModule("JOB_SCHEDULER", "Planificateur de tâches"),
            new PermissionModule("MISSIONS", "Missions"),
            new PermissionModule("PHYSICAL_COURIER", "Module courrier physique"),
            new PermissionModule("MY_SHIFT", "MyShift (présence)"));

    public UserPage page(String viewerUsername) {
        UserAccount viewer =
                users.findByUsernameIgnoreCase(viewerUsername == null ? "" : viewerUsername.trim()).orElse(null);
        Map<Long, String> directionNames = directionNameMap();
        Map<Long, String> sousNames = sousNameMap();
        List<UserVm> rows = users.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(u -> new UserVm(
                        u.getId(),
                        u.getUsername(),
                        u.getMatricule(),
                        u.getRole(),
                        u.isActiveBool(),
                        directionNames.getOrDefault(u.getDirectionId(), ""),
                        sousNames.getOrDefault(u.getSousDirectionId(), ""),
                        u.getEmail(),
                        u.getAttendanceSignature(),
                        perms.findByUserId(u.getId()).stream().map(UserPermission::getPermission).toList()))
                .toList();
        List<BlockedLoginVm> blockedRows =
                viewer == null ? List.of() : buildBlockedLoginRows(viewer, directionNames, sousNames);
        return new UserPage(
                rows,
                blockedRows,
                roleOptions(),
                modules(),
                directionCatalog(),
                defaultPermissionsByRole(),
                notificationEmailFallback.describeFallbackForUi());
    }

    @Transactional
    public void create(UserForm form) {
        if (form.username() == null || form.username().isBlank() || form.role() == null || form.role().isBlank()) {
            throw new IllegalArgumentException("missingFields");
        }
        UserAccount u = new UserAccount();
        applyFormToUser(u, form, true);
        users.save(u);
        replacePermissions(u.getId(), buildPermissionStrings(form.role(), form.permissions()));
    }

    @Transactional
    public void update(Long id, UserForm form) {
        UserAccount u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        applyFormToUser(u, form, false);
        users.save(u);
        replacePermissions(u.getId(), buildPermissionStrings(form.role(), form.permissions()));
    }

    @Transactional
    public void toggleActive(Long id) {
        UserAccount u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        u.setActive(u.isActiveBool() ? 0 : 1);
        users.save(u);
    }

    @Transactional
    public void changeRole(Long id, String role) {
        UserAccount u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        String normalizedRole = normalizeRole(role);
        u.setRole(normalizedRole);
        users.save(u);
        replacePermissions(id, buildPermissionStrings(normalizedRole, null));
    }

    @Transactional
    public void resetPassword(Long id) {
        UserAccount u = users.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        u.setPasswordHash(passwordEncoder.encode("123456"));
        u.setMustChangePassword(1);
        u.setFailedAttempts(0);
        u.setLockUntil(null);
        u.setLoginLockoutTier(0);
        users.save(u);
    }

    @Transactional
    public void clearLoginLockout(Long id, String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            throw new IllegalArgumentException("notFound");
        }
        UserAccount actor =
                users.findByUsernameIgnoreCase(actorUsername.trim()).orElseThrow(() -> new IllegalArgumentException("notFound"));
        UserAccount target = users.findById(id).orElseThrow(() -> new IllegalArgumentException("notFound"));
        if (!mayManageLoginLockout(actor, target)) {
            throw new IllegalStateException("unlockNotAllowed");
        }
        target.setFailedAttempts(0);
        target.setLockUntil(null);
        target.setLoginLockoutTier(0);
        users.save(target);
        loginAuditService.record(
                target.getUsername(),
                "CONNEXION_DEVEROUILLEE",
                actor.getUsername(),
                "D\u00e9verrouillage connexion (gestion utilisateurs)");
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || !users.existsById(id)) {
            throw new IllegalArgumentException("notFound");
        }
        perms.deleteByUserId(id);
        users.deleteById(id);
    }

    public List<String> roleOptions() {
        return ROLE_OPTIONS;
    }

    public List<PermissionModule> modules() {
        return MODULES;
    }

    public Map<String, List<String>> defaultPermissionsByRole() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String role : ROLE_OPTIONS) {
            out.put(role, new ArrayList<>(buildPermissionStrings(role, null)));
        }
        return out;
    }

    public Map<String, List<String>> directionCatalog() {
        LinkedHashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("Direction de la Réglementation et de la Facilitation", List.of("Réglementation", "Facilitation"));
        map.put("Direction de la Lutte contre la Fraude", List.of("Liaison et Renseignements", "Stratégies et Planification", "Audit a posteriori"));
        map.put("Direction du Tarif et des Règles d’Origine", List.of("Tarif", "Règles d’origine"));
        map.put("Direction de la Valeur", List.of("Évaluation", "Recours et valeurs de base"));
        map.put("Direction des Autres Produits d’Accises", List.of("Alcools, Boissons alcooliques et Limonades", "Tabacs et autres Produits d’Accises"));
        map.put("Direction des Huiles Minérales", List.of("Producteurs", "Distributeurs"));
        map.put("Direction des Recettes du Trésor", List.of("Recettes de Douanes", "Recettes des Accises", "Budget et Recettes Connexes"));
        map.put("Direction des Ressources Humaines", List.of("Recrutement et Formation", "Administration", "OEuvres Sociales", "Relations Publiques et Protocole"));
        map.put("Direction des Équipements et de la Logistique", List.of("Gestion du Patrimoine", "Imprimerie et Approvisionnements"));
        map.put("Direction des Statistiques, Documentation et Études Économiques", List.of("Statistiques et Études Économiques", "Documentation"));
        map.put("Direction des Affaires Juridiques et Contentieuses", List.of("Affaires Contentieuses", "Affaires Juridiques"));
        map.put("Direction des Systèmes et Technologies d’Information", List.of("Développement et Maintenance des Applications", "Réseaux, Télécommunications et Maintenance Hardware", "Sydonia"));
        map.put("Direction de l’Audit Interne", List.of());
        map.put("Direction des Finances Internes", List.of("Comptabilité et Trésorerie", "Budget Interne"));
        map.put("Direction des Réformes et Modernisation", List.of());
        map.put("Bureau de Coordination", List.of());
        return map;
    }

    private void applyFormToUser(UserAccount u, UserForm form, boolean creating) {
        String role = normalizeRole(form.role());
        u.setUsername(form.username().trim());
        u.setMatricule(blankToNull(form.matricule()));
        u.setRole(role);
        u.setEmail(notificationEmailFallback.resolveStoredEmail(form.email()));
        u.setAttendanceSignature(blankToNull(form.signatureCode()));
        u.setActive(form.active() ? 1 : 0);
        u.setDirectionId(resolveDirectionId(form.directionName()));
        u.setSousDirectionId(isSousDirectionForbidden(role) ? null : resolveSousDirectionId(form.sousDirectionName()));
        if (creating) {
            String raw = (form.password() == null || form.password().isBlank()) ? "123456" : form.password();
            u.setPasswordHash(passwordEncoder.encode(raw));
            u.setMustChangePassword(1);
            u.setHidden(0);
            u.setFailedAttempts(0);
            u.setLockUntil(null);
            u.setLoginLockoutTier(0);
            u.setOnboardingTutorialCompleted(0);
        } else if (form.password() != null && !form.password().isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(form.password()));
            u.setMustChangePassword(1);
            u.setFailedAttempts(0);
            u.setLockUntil(null);
            u.setLoginLockoutTier(0);
        }
    }

    private void replacePermissions(Long userId, Set<String> permissionStrings) {
        perms.deleteByUserId(userId);
        for (String p : permissionStrings) {
            UserPermission row = new UserPermission();
            row.setUserId(userId);
            row.setPermission(p);
            perms.save(row);
        }
    }

    private Set<String> buildPermissionStrings(String role, List<String> formPermissions) {
        Set<String> out = new LinkedHashSet<>();
        if (formPermissions != null && !formPermissions.isEmpty()) {
            out.addAll(formPermissions);
            return out;
        }
        String r = normalizeRole(role);
        for (PermissionModule m : MODULES) {
            String k = m.key();
            if ("SUPER_ADMIN".equals(r) || "ADMIN".equals(r) || "DIRECTEUR".equals(r)) {
                out.add(k + "_READ");
                out.add(k + "_WRITE");
                continue;
            }
            if ("SECRETAIRE".equals(r)) {
                if (Set.of("DATA_ENTRY", "DATASHARE", "MY_ACTIVITY", "MY_WORK", "PHYSICAL_COURIER").contains(k)) {
                    out.add(k + "_READ");
                    out.add(k + "_WRITE");
                } else {
                    out.add(k + "_READ");
                }
                continue;
            }
            if ("COURIER".equals(r)) {
                if (Set.of("PHYSICAL_COURIER", "DATA_ENTRY", "MY_ACTIVITY", "MY_WORK").contains(k)) {
                    out.add(k + "_READ");
                    out.add(k + "_WRITE");
                }
                continue;
            }
            if (Set.of("SOUS-DIRECTEUR", "INSPECTEUR", "CONTROLEUR", "VERIFICATEUR", "VERIFICATEUR-ASSISTANT").contains(r)) {
                out.add(k + "_READ");
                if (!Set.of("USER_MANAGEMENT", "LOGIN_AUDIT").contains(k)) {
                    out.add(k + "_WRITE");
                }
            }
        }
        return out;
    }

    private String normalizeRole(String role) {
        String r = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if ("SOUS_DIRECTEUR".equals(r)) {
            return "SOUS-DIRECTEUR";
        }
        if ("VERIFICATEUR_ASSISTANT".equals(r)) {
            return "VERIFICATEUR-ASSISTANT";
        }
        if ("COURRIER".equals(r)) {
            return "COURIER";
        }
        return r;
    }

    private static boolean isSousDirectionForbidden(String normalizedRole) {
        return "DIRECTEUR".equals(normalizedRole) || "SECRETAIRE".equals(normalizedRole);
    }

    private Long resolveDirectionId(String directionName) {
        String d = blankToNull(directionName);
        if (d == null) {
            return null;
        }
        return directions.findAll().stream()
                .filter(x -> d.equalsIgnoreCase(x.getName()))
                .map(Direction::getId)
                .findFirst()
                .orElseGet(() -> {
                    Direction created = new Direction();
                    created.setName(d);
                    return directions.save(created).getId();
                });
    }

    private Long resolveSousDirectionId(String sousDirectionName) {
        String s = blankToNull(sousDirectionName);
        if (s == null) {
            return null;
        }
        return sousDirections.findAll().stream()
                .filter(x -> s.equalsIgnoreCase(x.getName()))
                .map(SousDirection::getId)
                .findFirst()
                .orElseGet(() -> {
                    SousDirection created = new SousDirection();
                    created.setName(s);
                    return sousDirections.save(created).getId();
                });
    }

    private Map<Long, String> directionNameMap() {
        Map<Long, String> m = new LinkedHashMap<>();
        for (Direction d : directions.findAll()) {
            m.put(d.getId(), d.getName());
        }
        return m;
    }

    private Map<Long, String> sousNameMap() {
        Map<Long, String> m = new LinkedHashMap<>();
        for (SousDirection d : sousDirections.findAll()) {
            m.put(d.getId(), d.getName());
        }
        return m;
    }

    private List<BlockedLoginVm> buildBlockedLoginRows(
            UserAccount viewer, Map<Long, String> directionNames, Map<Long, String> sousNames) {
        Instant now = Instant.now();
        List<UserAccount> candidates =
                users.findAll().stream()
                        .filter(u -> !isGloballyExemptFromLoginLockout(u))
                        .filter(u -> isLoginLockConcern(u, now))
                        .filter(u -> visibleBlockedUserForViewer(viewer, u))
                        .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                        .toList();
        List<BlockedLoginVm> out = new ArrayList<>();
        for (UserAccount u : candidates) {
            String dir = directionNames.getOrDefault(u.getDirectionId(), "");
            String sous = sousNames.getOrDefault(u.getSousDirectionId(), "");
            boolean locked = u.getLockUntil() != null && u.getLockUntil().isAfter(now);
            int fa = u.getFailedAttempts() == null ? 0 : u.getFailedAttempts();
            int tier = u.getLoginLockoutTier() == null ? 0 : u.getLoginLockoutTier();
            String lockFmt =
                    u.getLockUntil() == null ? "" : DisplayDateFormatter.formatInstant(u.getLockUntil());
            String statusKey;
            if (locked) {
                statusKey = "userMgmt.blocked.status.activeLock";
            } else if (fa >= AuthAccountService.MAX_ATTEMPTS_BEFORE_LOCK - 1) {
                statusKey = "userMgmt.blocked.status.riskAttempts";
            } else if (tier > 0) {
                statusKey = "userMgmt.blocked.status.priorTier";
            } else {
                statusKey = "userMgmt.blocked.status.riskAttempts";
            }
            boolean canUnlockHere = mayManageLoginLockout(viewer, u);
            out.add(
                    new BlockedLoginVm(
                            u.getId(),
                            u.getUsername(),
                            blankToEmpty(u.getMatricule()),
                            u.getRole(),
                            dir,
                            sous,
                            locked ? lockFmt : "",
                            fa,
                            tier,
                            statusKey,
                            canUnlockHere));
        }
        return List.copyOf(out);
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /** Matches {@link AuthAccountService}: global operators are never subject to progressive login lockout. */
    private static boolean isGloballyExemptFromLoginLockout(UserAccount u) {
        if (u == null) {
            return true;
        }
        String rk = RoleKeys.normalizeForScope(u.getRole());
        return "SUPER_ADMIN".equals(rk) || "ADMIN".equals(rk);
    }

    private static boolean isLoginLockConcern(UserAccount u, Instant now) {
        int fa = u.getFailedAttempts() == null ? 0 : u.getFailedAttempts();
        int tier = u.getLoginLockoutTier() == null ? 0 : u.getLoginLockoutTier();
        if (u.getLockUntil() != null && u.getLockUntil().isAfter(now)) {
            return true;
        }
        if (tier > 0) {
            return true;
        }
        return fa >= AuthAccountService.MAX_ATTEMPTS_BEFORE_LOCK - 1;
    }

    private static boolean visibleBlockedUserForViewer(UserAccount viewer, UserAccount target) {
        String vr = RoleKeys.normalizeForScope(viewer.getRole());
        if ("SUPER_ADMIN".equals(vr) || "ADMIN".equals(vr)) {
            return true;
        }
        Long vd = viewer.getDirectionId();
        Long td = target.getDirectionId();
        return vd != null && vd.equals(td);
    }

    /**
     * Direction heads ({@code DIRECTEUR}, {@code SOUS-DIRECTEUR}, …) may unlock accounts in their direction only;
     * {@code ADMIN}/{@code SUPER_ADMIN} may unlock anyone except cross-administrator locks stay restricted.
     */
    private static boolean mayManageLoginLockout(UserAccount actor, UserAccount target) {
        if (actor == null || target == null) {
            return false;
        }
        String ar = RoleKeys.normalizeForScope(actor.getRole());
        String tr = RoleKeys.normalizeForScope(target.getRole());
        if ("SUPER_ADMIN".equals(ar)) {
            return true;
        }
        if ("ADMIN".equals(ar)) {
            return !"SUPER_ADMIN".equals(tr);
        }
        if ("SUPER_ADMIN".equals(tr) || "ADMIN".equals(tr)) {
            return false;
        }
        Long ad = actor.getDirectionId();
        Long td = target.getDirectionId();
        if (td == null) {
            return false;
        }
        return ad != null && ad.equals(td);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    public record UserPage(
            List<UserVm> rows,
            List<BlockedLoginVm> blockedLoginRows,
            List<String> roleOptions,
            List<PermissionModule> modules,
            Map<String, List<String>> directionCatalog,
            Map<String, List<String>> defaultPermissionsByRole,
            String ticketNotifyFallbackHint) {}

    public record BlockedLoginVm(
            Long id,
            String username,
            String matricule,
            String role,
            String directionName,
            String sousDirectionName,
            String lockUntilDisplay,
            int failedAttempts,
            int lockTier,
            String statusMessageKey,
            boolean viewerMayUnlock) {}

    public record UserVm(
            Long id,
            String username,
            String matricule,
            String role,
            boolean active,
            String directionName,
            String sousDirectionName,
            String email,
            String signatureCode,
            List<String> permissions) {}

    public record PermissionModule(String key, String label) {}

    public record UserForm(
            String username,
            String matricule,
            String signatureCode,
            String password,
            String role,
            String email,
            boolean active,
            String directionName,
            String sousDirectionName,
            List<String> permissions) {
        public List<String> permissions() {
            return permissions == null ? new ArrayList<>() : permissions;
        }
    }
}
