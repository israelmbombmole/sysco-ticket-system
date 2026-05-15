package com.sysco.web.service;

import com.sysco.web.domain.FileShareMgmtAccessRequest;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.FileShareMgmtAccessRequestRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.WebAuthenticationHelper;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileShareManagementAccessService {

    public static final String SESSION_UNTIL_ATTR = "SYSCO_FILE_SHARE_MGMT_ACCESS_UNTIL";

    /** Time window to enter the OTP after the direction admin generates it. */
    private static final int OTP_ENTRY_MINUTES = 15;

    private static final List<String> OPEN_STATUSES =
            List.of(FileShareMgmtAccessRequest.STATUS_PENDING, FileShareMgmtAccessRequest.STATUS_OTP_ISSUED);

    private final FileShareMgmtAccessRequestRepository accessRepo;
    private final UserAccountRepository users;
    private final NotificationService notificationService;

    private volatile int defaultSessionMinutes = 5;

    public int getDefaultSessionMinutes() {
        return defaultSessionMinutes;
    }

    public void setDefaultSessionMinutes(int minutes) {
        this.defaultSessionMinutes = Math.max(5, Math.min(180, minutes));
    }

    public List<FileShareMgmtAccessRequest> listOpenRequestsForActor(String actorUsername) {
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalArgumentException("user"));
        String role = actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase(Locale.ROOT);
        if ("SUPER_ADMIN".equals(role)) {
            return accessRepo.findByStatusInOrderByCreatedAtDesc(OPEN_STATUSES);
        }
        if ("ADMIN".equals(role)) {
            Long dirId = actor.getDirectionId();
            if (dirId == null) {
                return accessRepo.findByStatusInOrderByCreatedAtDesc(OPEN_STATUSES);
            }
            return accessRepo.findByDirectionIdAndStatusInOrderByCreatedAtDesc(dirId, OPEN_STATUSES);
        }
        return List.of();
    }

    @Transactional
    public FileShareMgmtAccessRequest createRequest(String requesterUsername) {
        UserAccount u = users.findByUsernameIgnoreCase(requesterUsername).orElseThrow(() -> new IllegalArgumentException("user"));
        Optional<FileShareMgmtAccessRequest> existing =
                accessRepo.findFirstByRequesterUserIdAndStatusInOrderByCreatedAtDesc(u.getId(), OPEN_STATUSES);
        if (existing.isPresent()) {
            return existing.get();
        }
        FileShareMgmtAccessRequest r = new FileShareMgmtAccessRequest();
        r.setRequesterUserId(u.getId());
        r.setDirectionId(u.getDirectionId());
        r.setStatus(FileShareMgmtAccessRequest.STATUS_PENDING);
        r.setSessionMinutes(defaultSessionMinutes);
        return accessRepo.save(r);
    }

    @Transactional
    public void issueOtp(long requestId, String actorUsername) {
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalArgumentException("user"));
        FileShareMgmtAccessRequest r = accessRepo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        if (!OPEN_STATUSES.contains(r.getStatus())) {
            throw new IllegalArgumentException("notFound");
        }
        ensureActorMayManageRequest(actor, r);
        String otp = generateOtpDigits();
        LocalDateTime now = LocalDateTime.now();
        r.setOtpCode(otp);
        r.setOtpValidUntil(now.plusMinutes(OTP_ENTRY_MINUTES));
        r.setStatus(FileShareMgmtAccessRequest.STATUS_OTP_ISSUED);
        r.setIssuedByUserId(actor.getId());
        r.setSessionMinutes(defaultSessionMinutes);
        accessRepo.save(r);

        UserAccount requester =
                users.findById(r.getRequesterUserId()).orElseThrow(() -> new IllegalStateException("notFound"));
        String msg = String.format(
                Locale.ROOT,
                "Code d'accès Gestion partage fichiers : %s (valide %d min pour saisie). Session après validation : %d min.",
                otp,
                OTP_ENTRY_MINUTES,
                r.getSessionMinutes());
        notificationService.createAndPush(
                requester.getId(),
                "Accès gestion partage fichiers",
                msg,
                "FILE_SHARE_MGMT_OTP",
                "FILE_SHARE_MGMT",
                r.getId(),
                otp);
    }

    @Transactional
    public void deleteOtpRequest(long requestId, String actorUsername) {
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalArgumentException("user"));
        FileShareMgmtAccessRequest r = accessRepo.findById(requestId).orElseThrow(() -> new IllegalArgumentException("notFound"));
        ensureActorMayManageRequest(actor, r);
        accessRepo.delete(r);
    }

    @Transactional
    public void verifyOtpAndOpenSession(String username, String otpRaw, HttpSession session) {
        if (session == null) {
            throw new IllegalStateException("session");
        }
        UserAccount u = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("user"));
        String otp = otpRaw == null ? "" : otpRaw.trim();
        if (otp.isEmpty()) {
            throw new IllegalArgumentException("badOtp");
        }
        List<FileShareMgmtAccessRequest> issued =
                accessRepo.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(u.getId(), FileShareMgmtAccessRequest.STATUS_OTP_ISSUED);
        LocalDateTime now = LocalDateTime.now();
        FileShareMgmtAccessRequest match =
                issued.stream().filter(r -> otp.equalsIgnoreCase(r.getOtpCode())).findFirst().orElse(null);
        if (match == null) {
            throw new IllegalArgumentException("badOtp");
        }
        if (match.getOtpValidUntil() != null && match.getOtpValidUntil().isBefore(now)) {
            throw new IllegalStateException("expired");
        }
        match.setStatus(FileShareMgmtAccessRequest.STATUS_CONSUMED);
        accessRepo.save(match);

        int mins = Math.max(1, match.getSessionMinutes());
        session.setAttribute(SESSION_UNTIL_ATTR, Instant.now().plusSeconds(mins * 60L));
    }

    public Optional<Long> sessionExpiryEpochMillis(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Instant until = (Instant) session.getAttribute(SESSION_UNTIL_ATTR);
        if (until == null) {
            return Optional.empty();
        }
        return Optional.of(until.toEpochMilli());
    }

    public void clearSession(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_UNTIL_ATTR);
        }
    }

    private void ensureActorMayManageRequest(UserAccount actor, FileShareMgmtAccessRequest r) {
        String role = actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase(Locale.ROOT);
        if ("SUPER_ADMIN".equals(role)) {
            return;
        }
        if ("ADMIN".equals(role)) {
            Long ad = actor.getDirectionId();
            Long rd = r.getDirectionId();
            if (ad == null || rd == null) {
                return;
            }
            if (ad.equals(rd)) {
                return;
            }
        }
        throw new IllegalStateException("notAllowed");
    }

    private static String generateOtpDigits() {
        int v = 100000 + new SecureRandom().nextInt(900000);
        return String.valueOf(v);
    }

}
