package com.sysco.web.service;



import com.sysco.web.domain.DataShareSharedFile;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;

import com.sysco.web.domain.UserAccount;

import com.sysco.web.repo.DataShareSharedFileRepository;

import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;

import com.sysco.web.repo.UserAccountRepository;

import com.sysco.web.util.DisplayDateFormatter;

import java.io.IOException;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.StandardCopyOption;

import java.time.LocalDate;

import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashMap;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Locale;

import java.util.Map;

import java.util.Set;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.core.io.ByteArrayResource;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;



@Service

@Slf4j

@RequiredArgsConstructor

public class DataShareService {



    private final UserAccountRepository users;

    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;

    private final FileShareAuditService fileShareAuditService;

    private final NotificationService notificationService;

    private final DataShareSharedFileRepository shareRepo;

    public enum AccessKind {
        PREVIEW,
        DOWNLOAD
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private static final int MAX_VISIBILITY_MINUTES = 10080;



    @Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")

    private String uploadsDirectory;



    public DataSharePage page(String fileQ, Long sousDirectionId, Long directionId, String currentUser) {

        String fq = normalize(fileQ);

        UserAccount actor = currentUser == null ? null : users.findByUsernameIgnoreCase(currentUser).orElse(null);

        Long actorSousDirectionId = actor == null ? null : actor.getSousDirectionId();
        Long activeSousDirectionId = sousDirectionId == null ? actorSousDirectionId : sousDirectionId;
        Long activeDirectionId = directionId;
        if (activeDirectionId != null && activeSousDirectionId == null) {
            activeSousDirectionId = directions.findById(activeDirectionId).map(Direction::getSousDirectionId).orElse(null);
        }
        final Long selectedSousDirectionId = activeSousDirectionId;
        final Long selectedDirectionId = activeDirectionId;



        Map<Long, String> directionMap = new LinkedHashMap<>();
        directions.findAll().forEach(d -> directionMap.put(d.getId(), d.getName()));



        List<RecipientChoice> recipients = users.findAll().stream()

                .filter(UserAccount::isActiveBool)

                .filter(u -> currentUser == null || !u.getUsername().equalsIgnoreCase(currentUser))

                .filter(u -> selectedSousDirectionId == null || selectedSousDirectionId.equals(u.getSousDirectionId()))
                .filter(u -> selectedDirectionId == null || selectedDirectionId.equals(u.getDirectionId()))

                .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))

                .map(u -> new RecipientChoice(

                        u.getId(),

                        u.getUsername(),

                        u.getRole(),

                        u.getDirectionId(),

                        directionMap.getOrDefault(u.getDirectionId(), "")))

                .toList();



        LocalDateTime now = LocalDateTime.now();

        List<DataShareSharedFile> receivedSource =
                currentUser == null
                        ? shareRepo.findAllByOrderBySharedAtDesc()
                        : shareRepo.findByRecipientUsernameIgnoreCaseOrderBySharedAtDesc(currentUser);

        List<SharedFileRow> received = receivedSource.stream()

                .map(DataShareService::fromEntity)

                .filter(e -> fq.isBlank() || e.fileName().toLowerCase(Locale.ROOT).contains(fq))

                .sorted(Comparator.comparing(SharedFileEvent::sharedAt).reversed())

                .map(e -> toSharedFileRow(e, now, false))

                .toList();



        List<SousDirectionChoice> sousDirectionChoices = sousDirections.findAll().stream()
                .sorted(Comparator.comparing(d -> d.getName().toLowerCase(Locale.ROOT)))
                .map(d -> new SousDirectionChoice(d.getId(), d.getName()))
                .toList();
        List<DirectionChoice> directionChoices = selectedSousDirectionId == null
                ? List.of()
                : directions.findAllBySousDirectionIdOrderByNameAsc(selectedSousDirectionId).stream()
                        .map(d -> new DirectionChoice(d.getId(), d.getName()))
                        .toList();

        List<SharedFileRow> sent =
                actor == null ? List.of() : sharedByUser(actor.getUsername(), fileQ == null ? "" : fileQ);

        return new DataSharePage(

                recipients,

                received,

                sent,

                fileQ == null ? "" : fileQ,
                sousDirectionChoices,
                directionChoices,
                actorSousDirectionId,
                selectedSousDirectionId,
                selectedDirectionId);

    }



    public List<SharedFileRow> sharedByUser(String username, String fileQ) {

        String fq = normalize(fileQ);

        LocalDateTime now = LocalDateTime.now();

        if (username == null) {

            return List.of();

        }

        return shareRepo.findBySharedByIgnoreCaseOrderBySharedAtDesc(username).stream()

                .map(DataShareService::fromEntity)

                .filter(e -> fq.isBlank() || e.fileName().toLowerCase(Locale.ROOT).contains(fq))

                .sorted(Comparator.comparing(SharedFileEvent::sharedAt).reversed())

                .map(e -> toSharedFileRow(e, now, true))

                .toList();

    }



    public List<SharedFileRow> managementRows(String q) {

        String fq = normalize(q);

        LocalDateTime now = LocalDateTime.now();

        return shareRepo.findAllByOrderBySharedAtDesc().stream()

                .map(DataShareService::fromEntity)

                .filter(e -> fq.isBlank()

                        || e.fileName().toLowerCase(Locale.ROOT).contains(fq)

                        || e.sharedBy().toLowerCase(Locale.ROOT).contains(fq)

                        || (e.sharedByRole() != null
                                && e.sharedByRole().toLowerCase(Locale.ROOT).contains(fq))

                        || e.recipient().toLowerCase(Locale.ROOT).contains(fq))

                .sorted(Comparator.comparing(SharedFileEvent::sharedAt).reversed())

                .map(e -> toSharedFileRow(e, now, false))

                .toList();

    }

    /**
     * Management list scoped by direction: {@code SUPER_ADMIN} sees all; direction {@code ADMIN} sees rows where
     * sharer or recipient belongs to that direction.
     */
    public List<SharedFileRow> managementRowsForViewer(String q, boolean superAdmin, Long actorDirectionId) {

        String fq = normalize(q);

        LocalDateTime now = LocalDateTime.now();

        return shareRepo.findAllByOrderBySharedAtDesc().stream()

                .map(DataShareService::fromEntity)

                .filter(e -> fq.isBlank()

                        || e.fileName().toLowerCase(Locale.ROOT).contains(fq)

                        || e.sharedBy().toLowerCase(Locale.ROOT).contains(fq)

                        || (e.sharedByRole() != null
                                && e.sharedByRole().toLowerCase(Locale.ROOT).contains(fq))

                        || e.recipient().toLowerCase(Locale.ROOT).contains(fq))

                .filter(e -> superAdmin || managementRowTouchesDirection(e, actorDirectionId))

                .sorted(Comparator.comparing(SharedFileEvent::sharedAt).reversed())

                .map(e -> toSharedFileRow(e, now, false))

                .toList();

    }

    private boolean managementRowTouchesDirection(SharedFileEvent e, Long directionId) {

        if (directionId == null) {

            return false;

        }

        boolean sharerMatch =

                users.findByUsernameIgnoreCase(e.sharedBy())

                        .filter(u -> directionId.equals(u.getDirectionId()))

                        .isPresent();

        boolean recipientMatch =

                users.findByUsernameIgnoreCase(e.recipient())

                        .filter(u -> directionId.equals(u.getDirectionId()))

                        .isPresent();

        return sharerMatch || recipientMatch;

    }

    public void deleteSharedFileForManagement(long fileId, boolean superAdmin, Long actorDirectionId) {

        DataShareSharedFile target =

                shareRepo.findById(fileId).orElseThrow(() -> new IllegalArgumentException("notFound"));

        if (!superAdmin) {

            SharedFileEvent e = fromEntity(target);

            if (!managementRowTouchesDirection(e, actorDirectionId)) {

                throw new IllegalArgumentException("notFound");

            }

        }

        deleteSharedFile(fileId);

    }



    public void deleteSharedFile(long fileId) {

        DataShareSharedFile target =
                shareRepo.findById(fileId).orElseThrow(() -> new IllegalArgumentException("notFound"));

        String path = target.getFilePath();

        shareRepo.delete(target);

        cleanupOrphanFile(path);

    }



    /**

     * Sender removes one recipient-share row; deletes the stored file if no other row references it.

     */

    public void deleteSenderShare(long fileId, String ownerUsername) {

        DataShareSharedFile target =
                shareRepo.findById(fileId).orElseThrow(() -> new IllegalArgumentException("notFound"));

        if (!target.getSharedBy().equalsIgnoreCase(ownerUsername)) {

            throw new IllegalArgumentException("notFound");

        }

        String path = target.getFilePath();

        shareRepo.delete(target);

        cleanupOrphanFile(path);

    }



    public void updateVisibility(

            long fileId, String ownerUsername, Integer visibilityMinutes, LocalDateTime visibleUntilEnd) {

        DataShareSharedFile target =
                shareRepo.findById(fileId).orElseThrow(() -> new IllegalArgumentException("notFound"));

        if (!target.getSharedBy().equalsIgnoreCase(ownerUsername)) {

            throw new IllegalArgumentException("notFound");

        }

        LocalDateTime newEnd = resolveNewVisibleUntil(visibilityMinutes, visibleUntilEnd);

        target.setVisibleUntil(newEnd);

        shareRepo.save(target);

    }



    public String regenerateOtpForSender(long fileId, String ownerUsername) {

        DataShareSharedFile target =
                shareRepo.findById(fileId).orElseThrow(() -> new IllegalArgumentException("notFound"));

        if (!target.getSharedBy().equalsIgnoreCase(ownerUsername)) {

            throw new IllegalArgumentException("notFound");

        }

        String otp = generateOtp();

        target.setOtpCode(otp);

        target.setViewed(false);

        shareRepo.save(target);

        users.findByUsernameIgnoreCase(target.getRecipientUsername())

                .ifPresent(u -> notifyRecipientOtp(u.getId(), target.getFileName(), ownerUsername, otp, OtpNotificationKind.REGENERATED));

        return otp;

    }



    @Transactional

    public void replaceSharedFile(long fileId, String ownerUsername, MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {

            throw new IllegalArgumentException("emptyFile");

        }

        DataShareSharedFile target =
                shareRepo.findById(fileId).orElseThrow(() -> new IllegalArgumentException("notFound"));

        if (!target.getSharedBy().equalsIgnoreCase(ownerUsername)) {

            throw new IllegalArgumentException("notFound");

        }

        Path p = Path.of(target.getFilePath()).toAbsolutePath().normalize();

        if (!Files.exists(p) || !Files.isRegularFile(p)) {

            throw new IllegalArgumentException("notFound");

        }

        Files.copy(file.getInputStream(), p, StandardCopyOption.REPLACE_EXISTING);

        String newDisplayName = safeOriginalFilename(file);

        List<DataShareSharedFile> siblings =
                shareRepo.findByFilePathAndSharedByIgnoreCase(target.getFilePath(), ownerUsername);

        for (DataShareSharedFile sib : siblings) {

            sib.setViewed(false);

            if (newDisplayName != null) {

                sib.setFileName(newDisplayName);

            }

            String otp = generateOtp();

            sib.setOtpCode(otp);

            shareRepo.save(sib);

            users.findByUsernameIgnoreCase(sib.getRecipientUsername())

                    .ifPresent(

                            u -> notifyRecipientOtp(

                                    u.getId(), sib.getFileName(), ownerUsername, otp, OtpNotificationKind.FILE_REPLACED));

        }

    }



    public ShareResult share(

            List<MultipartFile> files,

            LocalDate expiresAt,

            Integer visibilityMinutes,

            List<Long> recipientIds,

            Long sousDirectionId,
            Long directionId,

            String actorUsername) {

        if (files == null || files.isEmpty() || recipientIds == null || recipientIds.isEmpty()) {

            throw new IllegalArgumentException("invalidShare");

        }



        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalArgumentException("user"));

        Long targetSousDirection = sousDirectionId == null ? actor.getSousDirectionId() : sousDirectionId;
        Long targetDirection = directionId;

        Set<Long> uniqueRecipientIds = new LinkedHashSet<>(recipientIds);

        List<UserAccount> recipients = users.findAllById(uniqueRecipientIds).stream()

                .filter(UserAccount::isActiveBool)

                .filter(u -> !u.getUsername().equalsIgnoreCase(actor.getUsername()))

                .filter(u -> targetSousDirection == null || targetSousDirection.equals(u.getSousDirectionId()))
                .filter(u -> targetDirection == null || targetDirection.equals(u.getDirectionId()))

                .toList();

        if (recipients.isEmpty()) {

            throw new IllegalArgumentException("invalidRecipients");

        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime visibleUntil = computeVisibleUntil(now, visibilityMinutes, expiresAt);



        List<GeneratedOtp> generatedOtps = new ArrayList<>();

        for (MultipartFile file : files) {

            String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()

                    ? "piece-jointe.bin"

                    : file.getOriginalFilename();

            String storedPath = storeSharedFile(fileName, file);

            for (UserAccount r : recipients) {

                String otp = generateOtp();

                DataShareSharedFile row = new DataShareSharedFile();

                row.setFileName(fileName);

                row.setSharedBy(actor.getUsername());

                row.setSharedByRole(actor.getRole() == null ? "" : actor.getRole());

                row.setRecipientUsername(r.getUsername());

                row.setSharedAt(now);

                row.setVisibleUntil(visibleUntil);

                row.setViewed(false);

                row.setFilePath(storedPath);

                row.setOtpCode(otp);

                shareRepo.save(row);

                fileShareAuditService.recordShareEvent(fileName, actor.getUsername(), r.getUsername());

                generatedOtps.add(new GeneratedOtp(r.getUsername(), fileName, otp));

            }

        }

        for (GeneratedOtp go : generatedOtps) {
            users.findByUsernameIgnoreCase(go.recipientUsername())
                    .ifPresent(
                            u -> notifyRecipientOtp(
                                    u.getId(), go.fileName(), actor.getUsername(), go.otpCode(), OtpNotificationKind.NEW_SHARE));
        }

        return new ShareResult(generatedOtps);

    }



    private static LocalDateTime computeVisibleUntil(LocalDateTime now, Integer visibilityMinutes, LocalDate expiresAt) {

        if (visibilityMinutes != null && visibilityMinutes > 0) {

            int capped = Math.min(visibilityMinutes, MAX_VISIBILITY_MINUTES);

            return now.plusMinutes(capped);

        }

        if (expiresAt != null) {

            return expiresAt.atTime(23, 59, 59);

        }

        return null;

    }



    private static LocalDateTime resolveNewVisibleUntil(Integer visibilityMinutes, LocalDateTime visibleUntilEnd) {

        if (visibleUntilEnd != null) {

            return visibleUntilEnd;

        }

        if (visibilityMinutes != null && visibilityMinutes > 0) {

            int capped = Math.min(visibilityMinutes, MAX_VISIBILITY_MINUTES);

            return LocalDateTime.now().plusMinutes(capped);

        }

        throw new IllegalArgumentException("visibilityRequired");

    }



    public ByteArrayResource accessFile(long fileId, String otp, String actorUsername, AccessKind accessKind) {

        DataShareSharedFile ent =
                shareRepo.findById(fileId).orElseThrow(() -> new IllegalArgumentException("notFound"));

        if (!ent.getRecipientUsername().equalsIgnoreCase(actorUsername)) {

            throw new IllegalArgumentException("notFound");

        }

        SharedFileEvent event = fromEntity(ent);

        if (event.visibleUntil() != null && event.visibleUntil().isBefore(LocalDateTime.now())) {

            throw new IllegalStateException("expired");

        }

        if (otp == null || otp.isBlank() || !event.otpCode().equalsIgnoreCase(otp.trim())) {

            throw new IllegalArgumentException("badOtp");

        }

        Path p = Path.of(event.filePath()).toAbsolutePath().normalize();

        try {

            if (!Files.exists(p) || !Files.isRegularFile(p)) {

                throw new IllegalArgumentException("notFound");

            }

            byte[] bytes = Files.readAllBytes(p);

            notifySenderAccess(event, actorUsername, accessKind);

            if (accessKind == AccessKind.PREVIEW) {
                fileShareAuditService.recordPreviewEvent(event.fileName(), event.sharedBy(), event.recipient());
            } else {
                fileShareAuditService.recordDownloadEvent(event.fileName(), event.sharedBy(), event.recipient());
            }

            replaceEvent(event, copy(event, event.visibleUntil(), event.visibleUntil(), true, event.otpCode()));

            return new ByteArrayResource(bytes);

        } catch (IOException e) {

            throw new IllegalStateException("readError", e);

        }

    }



    private enum OtpNotificationKind {
        NEW_SHARE,
        REGENERATED,
        FILE_REPLACED
    }

    private void notifyRecipientOtp(

            Long recipientUserId,

            String fileName,

            String senderUsername,

            String otpCode,

            OtpNotificationKind kind) {

        String title =

                switch (kind) {

                    case NEW_SHARE -> "Fichier partagé";

                    case REGENERATED -> "Partage : nouveau code OTP";

                    case FILE_REPLACED -> "Partage : fichier remplacé";

                };

        String message =

                switch (kind) {

                    case NEW_SHARE -> String.format(

                            "%s vous a partagé « %s ». Code OTP : %s. Saisissez-le dans Partage des données (Aperçu ou Télécharger).",

                            senderUsername,

                            fileName,

                            otpCode);

                    case REGENERATED -> String.format(

                            "%s a prolongé / renouvelé l’accès à « %s ». Nouveau code OTP : %s. "

                                    + "Saisissez-le dans Partage des données (Aperçu ou Télécharger).",

                            senderUsername,

                            fileName,

                            otpCode);

                    case FILE_REPLACED -> String.format(

                            "%s a remplacé le fichier « %s » par une nouvelle version. Nouveau code OTP : %s. "

                                    + "Saisissez-le dans Partage des données (Aperçu ou Télécharger) pour consulter le nouveau fichier.",

                            senderUsername,

                            fileName,

                            otpCode);

                };

        notificationService.createAndPush(

                recipientUserId, title, message, "DATASHARE_OTP", "DATASHARE", null, fileName);

    }



    private void notifySenderAccess(SharedFileEvent event, String recipientUsername, AccessKind accessKind) {

        users.findByUsernameIgnoreCase(event.sharedBy())

                .ifPresent(sender -> {

                    boolean download = accessKind == AccessKind.DOWNLOAD;

                    String title = download ? "Partage : téléchargement" : "Partage : prévisualisation";

                    String verb = download ? "a téléchargé" : "a prévisualisé";

                    String message =

                            String.format("%s %s le fichier « %s ».", recipientUsername, verb, event.fileName());

                    notificationService.createAndPush(

                            sender.getId(),

                            title,

                            message,

                            "DATASHARE_ACCESS",

                            "DATASHARE",

                            event.id(),

                            event.fileName());

                });

    }



    public String fileNameForRecipient(long fileId, String actorUsername) {

        return shareRepo

                .findById(fileId)

                .filter(e -> e.getRecipientUsername().equalsIgnoreCase(actorUsername))

                .map(DataShareSharedFile::getFileName)

                .orElseThrow(() -> new IllegalArgumentException("notFound"));

    }



    private SharedFileRow toSharedFileRow(SharedFileEvent e, LocalDateTime now, boolean senderView) {

        boolean expired = e.visibleUntil() != null && e.visibleUntil().isBefore(now);

        String until =

                e.visibleUntil() == null ? "" : DisplayDateFormatter.formatLocalDateTime(e.visibleUntil());

        String roleColumn =
                senderView
                        ? users
                                .findByUsernameIgnoreCase(e.recipient())
                                .map(UserAccount::getRole)
                                .map(String::trim)
                                .filter(r -> !r.isEmpty())
                                .orElse("")
                        : (e.sharedByRole() == null ? "" : e.sharedByRole());

        return new SharedFileRow(

                e.id(),

                e.fileName(),

                e.sharedBy(),

                roleColumn,

                DisplayDateFormatter.formatLocalDate(e.sharedAt().toLocalDate()),

                TIME_FMT.format(e.sharedAt()),

                until,

                senderView || !e.viewed(),

                e.recipient(),

                expired,

                e.sharedAt());

    }



    private void replaceEvent(SharedFileEvent old, SharedFileEvent neu) {

        DataShareSharedFile row =
                shareRepo.findById(old.id()).orElseThrow(() -> new IllegalArgumentException("notFound"));

        row.setVisibleUntil(neu.visibleUntil());

        row.setViewed(neu.viewed());

        row.setOtpCode(neu.otpCode());

        shareRepo.save(row);

    }



    private static SharedFileEvent copy(

            SharedFileEvent e,

            LocalDateTime ignoredOldVu,

            LocalDateTime visibleUntil,

            boolean viewed,

            String otp) {

        return new SharedFileEvent(

                e.id(),

                e.fileName(),

                e.sharedBy(),

                e.sharedByRole(),

                e.recipient(),

                e.sharedAt(),

                visibleUntil,

                viewed,

                e.filePath(),

                otp);

    }



    private void cleanupOrphanFile(String path) {

        if (path == null || path.isBlank()) {

            return;

        }

        boolean stillUsed = shareRepo.countByFilePath(path) > 0;

        if (!stillUsed) {

            try {

                Files.deleteIfExists(Path.of(path).toAbsolutePath().normalize());

            } catch (IOException ex) {

                log.warn("Could not delete orphan shared file {}: {}", path, ex.getMessage());

            }

        }

    }



    private String storeSharedFile(String fileName, MultipartFile file) {

        try {

            Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("data-share");

            Files.createDirectories(dir);

            String safeName = fileName.replace('\\', '_').replace('/', '_');

            Path dest = dir.resolve(System.currentTimeMillis() + "_" + safeName);

            Files.copy(file.getInputStream(), dest);

            return dest.toString();

        } catch (IOException e) {

            throw new IllegalStateException("storeError", e);

        }

    }



    private static String generateOtp() {

        int v = 100000 + new java.security.SecureRandom().nextInt(900000);

        return String.valueOf(v);

    }

    /** Same sanitization as {@link #storeSharedFile} for display name updates on replace. */

    private static String safeOriginalFilename(MultipartFile file) {

        if (file == null) {

            return null;

        }

        String n = file.getOriginalFilename();

        if (n == null || n.isBlank()) {

            return null;

        }

        return n.replace('\\', '_').replace('/', '_');

    }



    private static String normalize(String s) {

        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);

    }



    public record DataSharePage(

            List<RecipientChoice> recipients,

            List<SharedFileRow> receivedFiles,

            List<SharedFileRow> sentFiles,

            String fileQ,

            List<SousDirectionChoice> sousDirections,
            List<DirectionChoice> directions,

            Long actorSousDirectionId,
            Long selectedSousDirectionId,

            Long selectedDirectionId) {}

    public record SousDirectionChoice(Long id, String name) {}


    public record DirectionChoice(Long id, String name) {}



    public record RecipientChoice(Long id, String username, String role, Long directionId, String directionName) {}



    /**

     * @param role "Fichiers reçus": expéditeur ; "Mes fichiers partagés" / vue envoyeur : rôle du destinataire

     * @param canDelete recipient view: unused in UI; sender view: kept true so sender may always revoke

     */

    public record SharedFileRow(

            Long id,

            String fileName,

            String sharedBy,

            String role,

            String date,

            String time,

            String visibleUntil,

            boolean canDelete,

            String recipient,

            boolean expired,

            LocalDateTime sharedAt) {}



    public record ShareResult(List<GeneratedOtp> generatedOtps) {}



    public record GeneratedOtp(String recipientUsername, String fileName, String otpCode) {}



    private static SharedFileEvent fromEntity(DataShareSharedFile e) {

        String role = e.getSharedByRole();

        return new SharedFileEvent(

                e.getId(),

                e.getFileName(),

                e.getSharedBy(),

                role == null ? "" : role,

                e.getRecipientUsername(),

                e.getSharedAt(),

                e.getVisibleUntil(),

                e.isViewed(),

                e.getFilePath(),

                e.getOtpCode());

    }

    private record SharedFileEvent(

            long id,

            String fileName,

            String sharedBy,

            String sharedByRole,

            String recipient,

            LocalDateTime sharedAt,

            LocalDateTime visibleUntil,

            boolean viewed,

            String filePath,

            String otpCode) {}

}


