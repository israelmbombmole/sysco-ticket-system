package com.sysco.web.service;

import com.sysco.web.domain.ChatConversation;
import com.sysco.web.domain.ChatMessage;
import com.sysco.web.domain.Direction;
import com.sysco.web.domain.SousDirection;
import com.sysco.web.domain.UserAccount;
import com.sysco.web.repo.ChatConversationRepository;
import com.sysco.web.repo.ChatMessageRepository;
import com.sysco.web.repo.DirectionRepository;
import com.sysco.web.repo.SousDirectionRepository;
import com.sysco.web.repo.UserAccountRepository;
import com.sysco.web.util.DisplayDateFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserAccountRepository users;
    private final DirectionRepository directions;
    private final SousDirectionRepository sousDirections;
    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final SimpMessagingTemplate messagingTemplate;
    @org.springframework.beans.factory.annotation.Value("${sysco.uploads.directory:${user.home}/.sysco-web/uploads}")
    private String uploadsDirectory;

    @Transactional
    public ChatPage page(
            String username,
            Long sousDirectionId,
            Long directionId,
            Long userId,
            Long conversationId,
            boolean allUsersMode) {
        UserAccount me = users.findByUsernameIgnoreCase(username).orElseThrow();
        Long effectiveDirectionId = directionId;
        if (effectiveDirectionId == null && userId != null) {
            effectiveDirectionId = users.findById(userId)
                    .map(UserAccount::getDirectionId)
                    .orElse(null);
        }
        List<SousDirectionOption> sousDirOptions = sousDirections.findAll().stream()
                .map(sd -> new SousDirectionOption(sd.getId(), sd.getName() == null ? "" : sd.getName().trim()))
                .filter(sd -> !sd.label().isBlank())
                .sorted(Comparator.comparing(SousDirectionOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<DirectionOption> dirs = directionOptions(sousDirectionId);
        List<UserOption> usersInDirection =
                allUsersMode ? allActiveOrgChatUsers(me) : userIdOptions(me, sousDirectionId, directionId);
        Long selectedConversation = conversationId;
        if (selectedConversation == null && userId != null) {
            selectedConversation =
                    findExistingConversation(me.getId(), userId).map(ChatConversation::getId).orElse(null);
        }
        if (selectedConversation != null) {
            markConversationReadForParticipant(me, selectedConversation);
        }
        List<ConversationRow> myConversations =
                conversations.findByParticipant(me.getId()).stream().map(c -> toConversationRow(me, c)).toList();
        boolean hasUnreadPeerInSidebar = myConversations.stream().anyMatch(ConversationRow::unreadFromPeer);
        List<MessageRow> thread = selectedConversation == null
                ? List.of()
                : messages.findTop200ByConversationIdOrderByCreatedAtAsc(selectedConversation).stream()
                        .map(m -> {
                            String attPath = m.getAttachmentPath();
                            String attName = attPath == null ? "" : fileName(attPath);
                            return new MessageRow(
                                    m.getId(),
                                    m.getConversationId(),
                                    m.getSenderUserId(),
                                    users.findById(m.getSenderUserId()).map(UserAccount::getUsername).orElse(""),
                                    m.getMessageText(),
                                    DisplayDateFormatter.formatChatMessageInstant(m.getCreatedAt()),
                                    attName,
                                    attPath == null ? "" : attPath,
                                    looksLikeImageAttachment(attName),
                                    m.getEditedAt() != null);
                        })
                        .toList();
        String selectedPeerUsername = "";
        if (selectedConversation != null) {
            selectedPeerUsername = conversations
                    .findById(selectedConversation)
                    .map(conv -> {
                        Long peerId = me.getId().equals(conv.getUserAId()) ? conv.getUserBId() : conv.getUserAId();
                        return users.findById(peerId).map(UserAccount::getUsername).orElse("");
                    })
                    .orElse("");
        }
        return new ChatPage(
                sousDirOptions,
                dirs,
                usersInDirection,
                myConversations,
                selectedConversation,
                thread,
                sousDirectionId,
                effectiveDirectionId,
                userId,
                selectedPeerUsername,
                hasUnreadPeerInSidebar,
                allUsersMode);
    }

    @Transactional
    public MessageRow sendMessage(String username, Long directionId, Long toUserId, String text, MultipartFile attachment) {
        UserAccount me = resolveCurrentUser(username).orElseThrow(() -> new IllegalArgumentException("invalid"));
        UserAccount peer = users.findById(toUserId).orElseThrow();
        if ((text == null || text.isBlank()) && (attachment == null || attachment.isEmpty())) {
            throw new IllegalArgumentException("invalid");
        }
        if (me.getId().equals(peer.getId())) {
            throw new IllegalArgumentException("invalid");
        }
        if (!peer.isActiveBool()) {
            throw new IllegalArgumentException("invalid");
        }
        Optional<ChatConversation> existingConv = findExistingConversation(me.getId(), peer.getId());
        Long resolvedDirection =
                existingConv.map(ChatConversation::getDirectionId).orElseGet(() -> resolveConversationDirectionId(me, peer, directionId));
        ChatConversation conv =
                existingConv.orElseGet(
                        () -> {
                            ChatConversation created = new ChatConversation();
                            created.setDirectionId(resolvedDirection);
                            created.setUserAId(me.getId());
                            created.setUserBId(peer.getId());
                            created.setCreatedAt(Instant.now());
                            created.setUpdatedAt(Instant.now());
                            return conversations.save(created);
                        });
        conv.setUpdatedAt(Instant.now());
        conversations.save(conv);

        ChatMessage msg = new ChatMessage();
        msg.setConversationId(conv.getId());
        msg.setSenderUserId(me.getId());
        msg.setMessageText(text == null ? "" : text.trim());
        msg.setAttachmentPath(storeAttachment(conv.getId(), attachment));
        msg.setCreatedAt(Instant.now());
        ChatMessage saved = messages.save(msg);

        String savedAttPath = saved.getAttachmentPath();
        String savedAttName = savedAttPath == null ? "" : fileName(savedAttPath);
        MessageRow row = new MessageRow(
                saved.getId(),
                saved.getConversationId(),
                saved.getSenderUserId(),
                me.getUsername(),
                saved.getMessageText(),
                DisplayDateFormatter.formatChatMessageInstant(saved.getCreatedAt()),
                savedAttName,
                savedAttPath == null ? "" : savedAttPath,
                looksLikeImageAttachment(savedAttName),
                saved.getEditedAt() != null);
        messagingTemplate.convertAndSendToUser(me.getUsername(), "/queue/chat", row);
        messagingTemplate.convertAndSendToUser(peer.getUsername(), "/queue/chat", row);
        return row;
    }

    @Transactional(readOnly = true)
    public TargetResolution resolveTargetByMatricule(String requesterUsername, String matricule) {
        String key = matricule == null ? "" : matricule.trim();
        if (key.isBlank()) {
            throw new IllegalArgumentException("invalid");
        }
        UserAccount me = users.findByUsernameIgnoreCase(requesterUsername).orElseThrow();
        UserAccount peer = users.findByMatriculeIgnoreCase(key).orElseThrow(() -> new IllegalArgumentException("notFound"));
        if (!peer.isActiveBool() || peer.getId().equals(me.getId())) {
            throw new IllegalArgumentException("notFound");
        }
        return buildTargetResolution(me, peer);
    }

    @Transactional(readOnly = true)
    public TargetSuggestion resolveTargetByAssistant(String requesterUsername, String freeText) {
        String key = freeText == null ? "" : freeText.trim();
        if (key.isBlank()) {
            throw new IllegalArgumentException("invalid");
        }
        UserAccount me = resolveCurrentUser(requesterUsername).orElseThrow(() -> new IllegalArgumentException("invalid"));
        return tryResolveAssistantSuggestion(me, key).orElseThrow(() -> new IllegalArgumentException("notFound"));
    }

    /** Live assistant search: never throws; short or empty query yields empty suggestion. */
    @Transactional(readOnly = true)
    public TargetSuggestion liveSearchChatTargets(String requesterUsername, String query) {
        String key = query == null ? "" : query.trim();
        if (key.length() < 2) {
            return new TargetSuggestion(null, List.of());
        }
        Optional<UserAccount> me = resolveCurrentUser(requesterUsername);
        if (me.isEmpty()) {
            return new TargetSuggestion(null, List.of());
        }
        return tryResolveAssistantSuggestion(me.get(), key).orElseGet(() -> new TargetSuggestion(null, List.of()));
    }

    private Optional<TargetSuggestion> tryResolveAssistantSuggestion(UserAccount me, String key) {
        UserAccount exactByMatricule = users.findByMatriculeIgnoreCase(key).orElse(null);
        if (exactByMatricule != null
                && exactByMatricule.isActiveBool()
                && !exactByMatricule.getId().equals(me.getId())) {
            TargetResolution selected = buildTargetResolution(me, exactByMatricule);
            return Optional.of(new TargetSuggestion(selected, List.of(selected)));
        }
        List<UserAccount> candidates = users.findActiveByUsernameOrMatriculeContains(key).stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        List<UserAccount> ranked = rankCandidates(candidates, key);
        List<TargetResolution> topCandidates = ranked.stream()
                .limit(3)
                .map(peer -> buildTargetResolution(me, peer))
                .toList();
        return Optional.of(new TargetSuggestion(topCandidates.get(0), topCandidates));
    }

    private List<UserAccount> rankCandidates(List<UserAccount> candidates, String rawKey) {
        String key = normalizeKey(rawKey);
        List<ScoredUser> scored = new ArrayList<>();
        for (UserAccount c : candidates) {
            String username = normalizeKey(c.getUsername());
            String matricule = normalizeKey(c.getMatricule());
            int score = 0;
            if (key.equals(matricule)) {
                score += 500;
            }
            if (key.equals(username)) {
                score += 450;
            }
            if (!matricule.isBlank() && matricule.startsWith(key)) {
                score += 250;
            }
            if (!username.isBlank() && username.startsWith(key)) {
                score += 200;
            }
            if (!matricule.isBlank() && matricule.contains(key)) {
                score += 120;
            }
            if (!username.isBlank() && username.contains(key)) {
                score += 90;
            }
            score -= Math.abs(username.length() - key.length());
            scored.add(new ScoredUser(c, score));
        }
        scored.sort((a, b) -> {
            int byScore = Integer.compare(b.score(), a.score());
            if (byScore != 0) {
                return byScore;
            }
            String ua = a.user().getUsername() == null ? "" : a.user().getUsername();
            String ub = b.user().getUsername() == null ? "" : b.user().getUsername();
            return ua.compareToIgnoreCase(ub);
        });
        return scored.stream().map(ScoredUser::user).toList();
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .trim();
    }

    private TargetResolution buildTargetResolution(UserAccount me, UserAccount peer) {
        Direction direction =
                peer.getDirectionId() == null ? null : directions.findById(peer.getDirectionId()).orElse(null);
        Long sousDirectionId = direction == null ? null : direction.getSousDirectionId();
        String sousDirectionName =
                sousDirectionId == null
                        ? ""
                        : sousDirections.findById(sousDirectionId).map(SousDirection::getName).orElse("");
        Long conversationId = findExistingConversation(me.getId(), peer.getId()).map(ChatConversation::getId).orElse(null);
        return new TargetResolution(
                peer.getId(),
                peer.getUsername(),
                peer.getMatricule() == null ? "" : peer.getMatricule(),
                peer.getDirectionId(),
                direction == null ? "" : direction.getName(),
                sousDirectionId,
                sousDirectionName,
                conversationId);
    }

    private Optional<ChatConversation> findExistingConversation(Long userA, Long userB) {
        List<ChatConversation> list = conversations.findBetweenUsersOrderByUpdatedAtDesc(userA, userB);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private Long resolveConversationDirectionId(UserAccount me, UserAccount peer, Long requestedDirectionId) {
        if (requestedDirectionId != null && directions.existsById(requestedDirectionId)) {
            return requestedDirectionId;
        }
        if (peer.getDirectionId() != null && directions.existsById(peer.getDirectionId())) {
            return peer.getDirectionId();
        }
        if (me.getDirectionId() != null && directions.existsById(me.getDirectionId())) {
            return me.getDirectionId();
        }
        return directions.findAll().stream().findFirst().map(Direction::getId).orElse(null);
    }

    @Transactional
    public void editMessage(String username, Long messageId, String newText) {
        UserAccount me = users.findByUsernameIgnoreCase(username).orElseThrow();
        ChatMessage m = messages.findById(messageId).orElseThrow(() -> new IllegalArgumentException("invalid"));
        if (!me.getId().equals(m.getSenderUserId())) {
            throw new IllegalStateException("notAllowed");
        }
        if (newText == null || newText.isBlank()) {
            throw new IllegalArgumentException("invalid");
        }
        m.setMessageText(newText.trim());
        m.setEditedAt(Instant.now());
        messages.save(m);
    }

    @Transactional(readOnly = true)
    public AttachmentFile attachment(String username, Long messageId) {
        UserAccount me = users.findByUsernameIgnoreCase(username).orElseThrow();
        ChatMessage m = messages.findById(messageId).orElseThrow(() -> new IllegalArgumentException("invalid"));
        ChatConversation c = conversations.findById(m.getConversationId()).orElseThrow(() -> new IllegalArgumentException("invalid"));
        if (!me.getId().equals(c.getUserAId()) && !me.getId().equals(c.getUserBId())) {
            throw new IllegalStateException("notAllowed");
        }
        if (m.getAttachmentPath() == null || m.getAttachmentPath().isBlank()) {
            throw new IllegalArgumentException("invalid");
        }
        Path p = Path.of(m.getAttachmentPath()).toAbsolutePath().normalize();
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new IllegalArgumentException("invalid");
        }
        return new AttachmentFile(p, p.getFileName().toString());
    }

    private String storeAttachment(Long conversationId, MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return "";
        }
        try {
            Path dir = Path.of(uploadsDirectory).toAbsolutePath().normalize().resolve("chat").resolve(String.valueOf(conversationId));
            Files.createDirectories(dir);
            String name = attachment.getOriginalFilename() == null ? "piece-jointe.bin" : attachment.getOriginalFilename();
            Path dest = dir.resolve(System.currentTimeMillis() + "_" + name.replace('\\', '_').replace('/', '_'));
            Files.copy(attachment.getInputStream(), dest);
            return dest.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String fileName(String path) {
        try {
            return Path.of(path).getFileName().toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean looksLikeImageAttachment(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".png")
                || n.endsWith(".jpg")
                || n.endsWith(".jpeg")
                || n.endsWith(".gif")
                || n.endsWith(".webp")
                || n.endsWith(".bmp")
                || n.endsWith(".svg");
    }

    /** Incoming peer messages in any conversation where the reader has not advanced past them yet. */
    @Transactional(readOnly = true)
    public long countUnreadChat(String username) {
        UserAccount u = resolveCurrentUser(username).orElse(null);
        if (u == null) {
            return 0;
        }
        return messages.countUnreadIncomingPerConversation(u.getId());
    }

    private void markConversationReadForParticipant(UserAccount me, Long conversationId) {
        ChatConversation c = conversations.findById(conversationId).orElse(null);
        if (c == null) {
            return;
        }
        if (!me.getId().equals(c.getUserAId()) && !me.getId().equals(c.getUserBId())) {
            return;
        }
        Instant now = Instant.now();
        if (me.getId().equals(c.getUserAId())) {
            c.setUserALastReadAt(now);
        } else {
            c.setUserBLastReadAt(now);
        }
        conversations.save(c);
    }

    private Optional<UserAccount> resolveCurrentUser(String principalKey) {
        String key = principalKey == null ? "" : principalKey.trim();
        if (key.isBlank()) {
            return Optional.empty();
        }
        return users.findByUsernameIgnoreCase(key).or(() -> users.findByMatriculeIgnoreCase(key));
    }

    private ConversationRow toConversationRow(UserAccount me, ChatConversation c) {
        Long peerId = me.getId().equals(c.getUserAId()) ? c.getUserBId() : c.getUserAId();
        String peer = users.findById(peerId).map(UserAccount::getUsername).orElse("?");
        Direction directionRow = directions.findById(c.getDirectionId()).orElse(null);
        Long sousDirectionId = directionRow == null ? null : directionRow.getSousDirectionId();
        String direction = directionRow == null ? "" : directionRow.getName();
        String lastSender = "";
        boolean lastFromPeer = false;
        String lastPreview = "";
        Optional<ChatMessage> lastMsg = messages.findFirstByConversationIdOrderByCreatedAtDesc(c.getId());
        boolean unreadFromPeer = false;
        if (lastMsg.isPresent()) {
            ChatMessage lm = lastMsg.get();
            lastSender = users.findById(lm.getSenderUserId()).map(UserAccount::getUsername).orElse("?");
            lastFromPeer = !lm.getSenderUserId().equals(me.getId());
            lastPreview = buildConversationLastPreview(lm);
            if (lastFromPeer) {
                Instant myRead = me.getId().equals(c.getUserAId()) ? c.getUserALastReadAt() : c.getUserBLastReadAt();
                unreadFromPeer = myRead == null || lm.getCreatedAt().isAfter(myRead);
            }
        }
        return new ConversationRow(
                c.getId(),
                peerId,
                peer,
                sousDirectionId,
                c.getDirectionId(),
                direction,
                c.getUpdatedAt() == null ? "" : c.getUpdatedAt().toString(),
                lastSender,
                lastFromPeer,
                lastPreview,
                unreadFromPeer);
    }

    private static String buildConversationLastPreview(ChatMessage m) {
        String t = m.getMessageText();
        if (t != null && !t.isBlank()) {
            String oneLine = t.trim().replaceAll("\\s+", " ");
            return oneLine.length() > 72 ? oneLine.substring(0, 69) + "…" : oneLine;
        }
        if (m.getAttachmentPath() != null && !m.getAttachmentPath().isBlank()) {
            return "Pièce jointe";
        }
        return "";
    }

    private List<DirectionOption> directionOptions(Long sousDirectionId) {
        if (sousDirectionId == null) {
            return List.of();
        }
        return directions.findAllBySousDirectionIdOrderByNameAsc(sousDirectionId).stream()
                .map(d -> new DirectionOption(d.getId(), d.getName()))
                .toList();
    }

    private List<UserOption> userIdOptions(UserAccount me, Long sousDirectionId, Long directionId) {
        if (sousDirectionId == null) {
            return List.of();
        }
        Map<Long, Direction> directionById =
                directions.findAll().stream().collect(Collectors.toMap(Direction::getId, d -> d, (a, b) -> a));
        return users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> !u.getId().equals(me.getId()))
                .filter(u -> {
                    Long esd = effectiveChatSousDirectionId(u, directionById);
                    return esd != null && esd.equals(sousDirectionId);
                })
                .filter(u -> directionId == null || (u.getDirectionId() != null && u.getDirectionId().equals(directionId)))
                .map(
                        u -> new UserOption(
                                u.getId(),
                                chatUserPickerLabel(u),
                                u.getDirectionId(),
                                effectiveChatSousDirectionId(u, directionById)))
                .sorted(Comparator.comparing(UserOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<UserOption> allActiveOrgChatUsers(UserAccount me) {
        Map<Long, Direction> directionById =
                directions.findAll().stream().collect(Collectors.toMap(Direction::getId, d -> d, (a, b) -> a));
        return users.findAll().stream()
                .filter(UserAccount::isActiveBool)
                .filter(u -> !u.getId().equals(me.getId()))
                .map(
                        u -> new UserOption(
                                u.getId(),
                                chatUserPickerLabel(u),
                                u.getDirectionId(),
                                effectiveChatSousDirectionId(u, directionById)))
                .sorted(Comparator.comparing(UserOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Long effectiveChatSousDirectionId(UserAccount u, Map<Long, Direction> directionById) {
        if (u.getSousDirectionId() != null) {
            return u.getSousDirectionId();
        }
        if (u.getDirectionId() == null) {
            return null;
        }
        Direction d = directionById.get(u.getDirectionId());
        return d == null ? null : d.getSousDirectionId();
    }

    private static String chatUserPickerLabel(UserAccount u) {
        String name = u.getUsername() == null ? "" : u.getUsername().trim();
        String mat = u.getMatricule() == null ? "" : u.getMatricule().trim();
        if (name.isEmpty()) {
            name = "user-" + u.getId();
        }
        return mat.isEmpty() ? name : name + " · " + mat;
    }

    public record ChatPage(
            List<SousDirectionOption> sousDirections,
            List<DirectionOption> directions,
            List<UserOption> usersInDirection,
            List<ConversationRow> conversations,
            Long selectedConversationId,
            List<MessageRow> messages,
            Long selectedSousDirectionId,
            Long selectedDirectionId,
            Long selectedUserId,
            String selectedPeerUsername,
            boolean hasUnreadPeerInSidebar,
            boolean allUsersMode) {}

    public record SousDirectionOption(Long id, String label) {}
    public record DirectionOption(Long id, String label) {}
    public record UserOption(Long id, String label, Long directionId, Long sousDirectionId) {}
    public record ConversationRow(
            Long id,
            Long peerUserId,
            String peerUsername,
            Long sousDirectionId,
            Long directionId,
            String directionLabel,
            String updatedAt,
            String lastSenderUsername,
            boolean lastMessageFromPeer,
            String lastMessagePreview,
            boolean unreadFromPeer) {}
    public record MessageRow(
            Long id,
            Long conversationId,
            Long senderUserId,
            String senderUsername,
            String text,
            String createdAt,
            String attachmentName,
            String attachmentPath,
            boolean attachmentImage,
            boolean edited) {}
    public record TargetResolution(
            Long userId,
            String username,
            String matricule,
            Long directionId,
            String directionName,
            Long sousDirectionId,
            String sousDirectionName,
            Long conversationId) {}
    public record TargetSuggestion(TargetResolution selected, List<TargetResolution> candidates) {}
    private record ScoredUser(UserAccount user, int score) {}
    public record AttachmentFile(Path path, String fileName) {}
}
