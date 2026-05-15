package com.sysco.web.bootstrap;

import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.domain.UserPermission;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.repo.UserPermissionRepository;
import com.sysco.web.service.UserNotificationEmailFallback;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time (idempotent) seed for Direction des Systèmes et Technologies d'Information (DSTI):
 * accounts provided by operations spreadsheet (login = short name or matricule, default password 123456).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class DstiOrganizationSeedRunner implements CommandLineRunner {

    private static final String DGDA_DSTI_SOUS_DIRECTION_NAME =
            "Direction des Systèmes et Technologies d'Information";

    private static final String DEFAULT_DSTI_PASSWORD = "123456";

    /** Same baseline permissions as {@link SyscoDataInitializer} demo accounts. */
    private static final List<String> BASE_PERMISSIONS = List.of(
            "DASHBOARD",
            "DATA_ENTRY_WRITE",
            "DATA_MANAGEMENT_WRITE",
            "DATASHARE_WRITE",
            "MY_ACTIVITY_WRITE",
            "MY_WORK_WRITE",
            "TICKET_MONITORING_WRITE",
            "TICKET_MANAGEMENT_WRITE",
            "FILE_SHARE_MANAGEMENT_WRITE",
            "USER_MANAGEMENT_WRITE",
            "LOGIN_AUDIT_WRITE",
            "FILE_SHARE_AUDIT_WRITE",
            "CREATE_TICKET_WRITE",
            "JOB_SCHEDULER_WRITE",
            "MISSIONS_WRITE",
            "PHYSICAL_COURIER_WRITE",
            "LEAVE_MANAGEMENT_WRITE",
            "MY_SHIFT_WRITE");

    /** Staff list from operations spreadsheet image. */
    private static final List<StaffRow> STAFF = List.of(
            new StaffRow("MPOYO WA KAMIBA", "903093", "DIRECTEUR a.i.", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("KAYEMBE MPUMBU", "901805", "S/DIRECTEUR", "Sydonia"),
            new StaffRow("MPOYO WA KAMIBA", "903093", "S/DIRECTEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("TSHIBANGU KABWE", "905209", "S/DIRECTEUR", "Développement et Maintenance des Applications"),
            new StaffRow("MASKINI YANULA", "905205", "INSPECTEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("ZONGIA NYI YAWILI", "905238", "INSPECTEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("BUKASA MUKEBA", "905208", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("ILEKA DISASI", "905216", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("ISASI NDELO MIKO", "905254", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("KATUKU LUBUTA", "905236", "CONTROLEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("KAYIBA MIONGO", "905256", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("LENGA ESOMBE", "905253", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("LOMBEYA LIKUND’ELIO", "905242", "CONTROLEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("LUKOMBO YAFU", "905485", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("LUTATUKA MANDANGI", "905227", "CONTROLEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("MBALA NTOYA", "906121", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("LUVUEZO ZOLANA", "904106", "CONTROLEUR", ""),
            new StaffRow("MAGERA WA MAGERA", "903359", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("MANGYO ECIBA", "906610", "CONTROLEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("MUYMBA SI NYEMBO", "905234", "CONTROLEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("NSIMBA LUZOLO", "905231", "CONTROLEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("NYAMABO MUJANYAI", "905212", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("SEMAHUNDO MUFURA", "907039", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("TANZEY NKOTO", "903352", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("TEKWE MUKINI", "905222", "CONTROLEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("TUNGANE TAMBWE", "905250", "CONTROLEUR", "Développement et Maintenance des Applications"),
            new StaffRow("BANGU NZUMBA", "906913", "VERIFICATEUR", ""),
            new StaffRow("BOMINA N’SONI FOURVELLE", "906599", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("ISOMBA BOYOKO", "907312", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("KABESE BERNARD", "907379", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("KAPEND MUKIN", "907010", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("KASONGO YANGAYAYE", "907371", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("KONGOLO BETU", "906952", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("KUKABUKA MIKE", "905226", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("LENGA NDOLOMINGO", "905245", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("LUKAKU ZOLA", "906246", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("LUMUNDE DJOMBO", "907372", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("LUSAMBU KOMBA", "906600", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("MAKELELA MUBENESHAY", "907358", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("MANYANGA KABESE", "907304", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("MASANGU MUMBOLE", "907387", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("MAWAWA NAWEJ", "906993", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("MBALA MALAFU", "906995", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("NANDULA AMBO", "905247", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("NFAN MUSHIDI", "907012", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("NGE NDUJAYA ANINYA", "907052", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("NGONGO KAYA", "907398", "VERIFICATEUR", "Sydonia"),
            new StaffRow("NTAMB IYAMB", "907266", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("PUNGWE MWAMBA", "907037", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("RAMAZANI MOHAMED", "906999", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("SHABANI AMADI", "906947", "VERIFICATEUR", "Sydonia"),
            new StaffRow("SHAKA MAGERA", "907041", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("SONIA KABUO", "906912", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("TAMBWE SABITI", "906945", "VERIFICATEUR", "Réseaux, Télécommunications et Maintenance Hardware"),
            new StaffRow("TSHIKUTA KAFUTSHI", "906994", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("YAKASA KILESE", "907245", "VERIFICATEUR", "Développement et Maintenance des Applications"),
            new StaffRow("LUVUEZO MWADI", "907390", "VERIFICATEUR ASS.", "Développement et Maintenance des Applications"));

    private final PasswordEncoder passwordEncoder;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;
    private final UserAccountRepository users;
    private final UserPermissionRepository perms;
    private final UserNotificationEmailFallback notificationEmailFallback;

    @Override
    @Transactional
    public void run(String... args) {
        SousDirection dstiParent = sousDirections
                .findByNameIgnoreCase(DGDA_DSTI_SOUS_DIRECTION_NAME)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "DGDA seed missing DSTI parent direction — ensure DgdaOrganizationSeedRunner ran."));
        List<Direction> dstiDirections = directions.findAllBySousDirectionIdOrderByNameAsc(dstiParent.getId());
        if (dstiDirections.size() < 3) {
            throw new IllegalStateException("DGDA seed missing DSTI child directions.");
        }
        Map<String, Direction> directionByLabel = new HashMap<>();
        for (Direction d : dstiDirections) {
            String n = normalize(d.getName());
            if (n.contains("developpement")) {
                directionByLabel.put("dev", d);
            } else if (n.contains("reseaux") || n.contains("telecommunication")) {
                directionByLabel.put("network", d);
            } else if (n.contains("sydonia")) {
                directionByLabel.put("sydonia", d);
            }
        }
        if (directionByLabel.size() < 3) {
            throw new IllegalStateException("Could not map DSTI child directions (dev/network/sydonia).");
        }
        upsertSpreadsheetUsers(dstiParent, directionByLabel);
    }

    private void upsertSpreadsheetUsers(SousDirection dstiParent, Map<String, Direction> directionByLabel) {
        Set<String> reservedUsernames = Set.of("admin", "superadmin");
        Set<String> desiredMatricules = new HashSet<>();
        for (StaffRow row : STAFF) {
            desiredMatricules.add(row.matricule());
        }
        for (UserAccount existing : users.findAll()) {
            boolean systemAccount = existing.getRole() != null
                    && "SUPER_ADMIN".equalsIgnoreCase(existing.getRole().trim().replace(' ', '_'));
            if (systemAccount || reservedUsernames.contains(normalize(existing.getUsername()))) {
                continue;
            }
            if (!desiredMatricules.contains(trim(existing.getMatricule()))) {
                perms.deleteByUserId(existing.getId());
                existing.setActive(0);
                existing.setHidden(1);
                users.save(existing);
            }
        }

        String hash = passwordEncoder.encode(DEFAULT_DSTI_PASSWORD);
        Set<String> takenUsernames = new HashSet<>();
        for (UserAccount u : users.findAll()) {
            if (u.getUsername() != null && !u.getUsername().isBlank()) {
                takenUsernames.add(u.getUsername().toLowerCase(Locale.ROOT));
            }
        }
        List<StaffPrepared> prepared = buildPreparedRows(takenUsernames, directionByLabel);
        for (StaffPrepared p : prepared) {
            UserAccount existing = users.findByMatriculeIgnoreCase(p.matricule()).orElse(null);
            boolean isNew = existing == null;
            UserAccount ua = isNew ? new UserAccount() : existing;
            ua.setUsername(p.username());
            ua.setMatricule(p.matricule());
            ua.setRole(p.role());
            ua.setActive(1);
            ua.setDirectionId(p.directionId());
            ua.setSousDirectionId(dstiParent.getId());
            ua.setHidden(0);
            ua.setAttendanceSignature(p.username());
            // First-time spreadsheet accounts only: default password + forced change once.
            // Do not reset on every startup sync — that would send users back to /change-password forever.
            if (isNew) {
                ua.setPasswordHash(hash);
                ua.setMustChangePassword(1);
                ua.setOnboardingTutorialCompleted(1);
            }
            ua.setEmail(notificationEmailFallback.resolveStoredEmail(ua.getEmail()));
            ua = users.save(ua);
            resetPermissions(ua.getId());
        }
        log.info("DSTI spreadsheet users synchronized: {} active users.", prepared.size());
    }

    private List<StaffPrepared> buildPreparedRows(Set<String> takenUsernames, Map<String, Direction> directionByLabel) {
        List<StaffPrepared> out = new ArrayList<>();
        Set<String> seenMatricules = new HashSet<>();
        for (StaffRow row : STAFF) {
            if (!seenMatricules.add(row.matricule())) {
                log.warn("Duplicate matricule '{}' detected in spreadsheet list; first row kept.", row.matricule());
                continue;
            }
            String username = allocateSpreadsheetUsername(row.fullName(), row.matricule(), takenUsernames);
            Direction direction = resolveDirectionForSousLabel(directionByLabel, row.sousDirectionLabel());
            out.add(new StaffPrepared(
                    row.fullName(),
                    row.matricule(),
                    username,
                    normalizeRole(row.roleLabel()),
                    direction.getId()));
        }
        out.sort(Comparator.comparing(StaffPrepared::matricule));
        return out;
    }

    /**
     * Short login names without numeric suffixes: prefer first name, then first+last concatenated, then matricule
     * (always unique per row).
     */
    private static String allocateSpreadsheetUsername(String fullName, String matricule, Set<String> takenUsernames) {
        String first = firstNameToken(fullName);
        String last = lastNameToken(fullName);
        String matKey = trim(matricule).toLowerCase(Locale.ROOT);

        List<String> candidates = new ArrayList<>();
        if (!first.isBlank()) {
            candidates.add(first);
        }
        if (!first.isBlank() && !last.isBlank() && !first.equalsIgnoreCase(last)) {
            candidates.add(first + last);
        }
        if (!matKey.isBlank()) {
            candidates.add(matKey);
        }
        if (candidates.isEmpty()) {
            candidates.add("user");
        }

        for (String raw : candidates) {
            String key = raw.toLowerCase(Locale.ROOT);
            if (!takenUsernames.contains(key)) {
                takenUsernames.add(key);
                return raw;
            }
        }
        throw new IllegalStateException(
                "Cannot allocate username for DSTI row (matricule "
                        + matricule
                        + ", full name "
                        + fullName
                        + "); all candidates collide with existing logins.");
    }

    private Direction resolveDirectionForSousLabel(Map<String, Direction> directionByLabel, String sousDirectionLabel) {
        String label = normalize(sousDirectionLabel);
        if (label.contains("sydonia")) {
            return directionByLabel.get("sydonia");
        }
        if (label.contains("developpement")) {
            return directionByLabel.get("dev");
        }
        return directionByLabel.get("network");
    }

    private void resetPermissions(Long userId) {
        perms.deleteByUserId(userId);
        for (String p : BASE_PERMISSIONS) {
            UserPermission row = new UserPermission();
            row.setUserId(userId);
            row.setPermission(p);
            perms.save(row);
        }
    }

    private static String normalizeRole(String roleLabel) {
        String n = normalize(roleLabel);
        if (n.contains("s/directeur")) {
            return "SOUS-DIRECTEUR";
        }
        if (n.contains("directeur")) {
            return "DIRECTEUR";
        }
        if (n.contains("inspecteur")) {
            return "INSPECTEUR";
        }
        if (n.contains("controleur")) {
            return "CONTROLEUR";
        }
        if (n.contains("verificateur") && n.contains("ass")) {
            return "VERIFICATEUR ASSISTANT";
        }
        if (n.contains("verificateur")) {
            return "VERIFICATEUR";
        }
        return "CONTROLEUR";
    }

    private static String firstNameToken(String fullName) {
        String cleaned = normalize(fullName).replaceAll("[^a-z0-9\\s]", " ").trim();
        String[] tokens = cleaned.split("\\s+");
        if (tokens.length == 0 || tokens[0].isBlank()) {
            return "user";
        }
        return tokens[0];
    }

    private static String lastNameToken(String fullName) {
        String cleaned = normalize(fullName).replaceAll("[^a-z0-9\\s]", " ").trim();
        String[] tokens = cleaned.split("\\s+");
        if (tokens.length < 2) {
            return "";
        }
        String last = tokens[tokens.length - 1];
        return last.isBlank() ? "" : last;
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('’', '\'')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String trim(String input) {
        return input == null ? "" : input.trim();
    }

    private record StaffRow(String fullName, String matricule, String roleLabel, String sousDirectionLabel) {}

    private record StaffPrepared(String fullName, String matricule, String username, String role, Long directionId) {}
}
