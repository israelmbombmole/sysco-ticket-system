package com.sysco.web.repo;

import com.sysco.web.domain.ChatConversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
    @Query(
            "select c from ChatConversation c where (c.userAId = :userId or c.userBId = :userId) order by c.updatedAt desc")
    List<ChatConversation> findByParticipant(@Param("userId") Long userId);

    @Query(
            "select c from ChatConversation c where ((c.userAId = :a and c.userBId = :b) or (c.userAId = :b and c.userBId = :a)) and c.directionId = :directionId")
    Optional<ChatConversation> findBetweenUsersAndDirection(
            @Param("a") Long a, @Param("b") Long b, @Param("directionId") Long directionId);

    /** Most recently active thread between two users (any direction). */
    @Query(
            "select c from ChatConversation c where (c.userAId = :a and c.userBId = :b) or (c.userAId = :b and c.userBId = :a) order by c.updatedAt desc")
    List<ChatConversation> findBetweenUsersOrderByUpdatedAtDesc(@Param("a") Long a, @Param("b") Long b);
}
