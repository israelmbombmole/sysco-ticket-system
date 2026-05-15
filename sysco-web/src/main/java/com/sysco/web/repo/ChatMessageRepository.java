package com.sysco.web.repo;

import com.sysco.web.domain.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    List<ChatMessage> findTop200ByConversationIdOrderByCreatedAtAsc(Long conversationId);
    List<ChatMessage> findTop1ByConversationIdAndSenderUserIdOrderByCreatedAtDesc(Long conversationId, Long senderUserId);

    @Query(
            """
            SELECT COUNT(m) FROM ChatMessage m
            JOIN ChatConversation c ON m.conversationId = c.id
            WHERE (
              (c.userAId = :uid AND m.senderUserId <> :uid
               AND (c.userALastReadAt IS NULL OR m.createdAt > c.userALastReadAt))
              OR
              (c.userBId = :uid AND m.senderUserId <> :uid
               AND (c.userBLastReadAt IS NULL OR m.createdAt > c.userBLastReadAt))
            )
            """)
    long countUnreadIncomingPerConversation(@Param("uid") Long uid);
}
