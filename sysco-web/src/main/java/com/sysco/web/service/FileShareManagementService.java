package com.sysco.web.service;

import com.sysco.web.domain.FileShareMgmtAccessRequest;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.security.WebAuthenticationHelper;
import com.sysco.web.util.DisplayDateFormatter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileShareManagementService {

    private final DataShareService dataShareService;
    private final FileShareManagementAccessService accessService;
    private final UserAccountRepository users;

    public FileSharePage page(String q, String otpQ, Authentication auth, HttpServletRequest request) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("auth");
        }
        String username = auth.getName();
        UserAccount actor = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("user"));

        boolean superAdmin = WebAuthenticationHelper.isSuperAdmin(auth);
        boolean directionAdmin = WebAuthenticationHelper.isDirectionAdmin(auth);

        List<DataShareService.SharedFileRow> fileRows =
                dataShareService.managementRowsForViewer(q, superAdmin, actor.getDirectionId());

        String oq = normalize(otpQ);
        List<MgmtOtpRequestRow> reqRows =
                accessService.listOpenRequestsForActor(username).stream()
                        .filter(r -> oq.isBlank()
                                || resolveUsername(r.getRequesterUserId()).toLowerCase(Locale.ROOT).contains(oq)
                                || r.getStatus().toLowerCase(Locale.ROOT).contains(oq))
                        .sorted(Comparator.comparing(FileShareMgmtAccessRequest::getCreatedAt).reversed())
                        .map(this::toMgmtOtpRow)
                        .toList();

        boolean showAdminPanels = superAdmin || directionAdmin;
        Long grantMs =
                accessService.sessionExpiryEpochMillis(request.getSession(false)).orElse(null);
        boolean otpGrantActive = grantMs != null && !showAdminPanels;

        return new FileSharePage(
                fileRows,
                reqRows,
                q == null ? "" : q,
                otpQ == null ? "" : otpQ,
                accessService.getDefaultSessionMinutes(),
                showAdminPanels,
                otpGrantActive,
                grantMs);
    }

    public void updateOtpDuration(int minutes) {
        accessService.setDefaultSessionMinutes(minutes);
    }

    public void generateOtp(long requestId, String actorUsername) {
        accessService.issueOtp(requestId, actorUsername);
    }

    public void deleteOtpRequest(long requestId, String actorUsername) {
        accessService.deleteOtpRequest(requestId, actorUsername);
    }

    public void deleteSharedFile(long fileId, String actorUsername) {
        UserAccount actor = users.findByUsernameIgnoreCase(actorUsername).orElseThrow(() -> new IllegalArgumentException("user"));
        boolean superAdmin = "SUPER_ADMIN".equalsIgnoreCase(actor.getRole() == null ? "" : actor.getRole().trim());
        dataShareService.deleteSharedFileForManagement(fileId, superAdmin, actor.getDirectionId());
    }

    private MgmtOtpRequestRow toMgmtOtpRow(FileShareMgmtAccessRequest r) {
        return new MgmtOtpRequestRow(
                r.getId(),
                resolveUsername(r.getRequesterUserId()),
                r.getCreatedAt(),
                r.getStatus(),
                r.getOtpCode() == null ? "" : r.getOtpCode());
    }

    private String resolveUsername(Long userId) {
        return users.findById(userId).map(UserAccount::getUsername).orElse("-");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public record FileSharePage(
            List<DataShareService.SharedFileRow> sharedFiles,
            List<MgmtOtpRequestRow> otpRequests,
            String q,
            String otpQ,
            int otpDurationMinutes,
            boolean showAdminPanels,
            boolean otpGrantActive,
            Long grantExpiresAtEpochMillis) {}

    public record MgmtOtpRequestRow(long id, String requestedBy, LocalDateTime requestedAt, String status, String otpCode) {
        public String requestedAtFmt() {
            return DisplayDateFormatter.formatLocalDateTime(requestedAt);
        }
    }
}
