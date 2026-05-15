package com.sysco.web.repo;

import com.sysco.web.domain.TicketComment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {
    List<TicketComment> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    @Query("SELECT COUNT(c) FROM TicketComment c WHERE c.createdAt >= :start AND c.createdAt < :endExcl")
    long countCreatedBetween(@Param("start") Instant start, @Param("endExcl") Instant endExcl);

    @Query(
            "SELECT COUNT(c) FROM TicketComment c WHERE c.createdAt >= :start AND c.createdAt < :endExcl AND c.attachmentPath IS NOT NULL AND TRIM(c.attachmentPath) <> ''")
    long countWithAttachmentBetween(@Param("start") Instant start, @Param("endExcl") Instant endExcl);
}
