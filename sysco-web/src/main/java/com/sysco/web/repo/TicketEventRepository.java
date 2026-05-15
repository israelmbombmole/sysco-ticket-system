package com.sysco.web.repo;

import com.sysco.web.domain.TicketEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {
    List<TicketEvent> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    java.util.Optional<TicketEvent> findTopByTicketIdAndEventTypeOrderByCreatedAtDesc(Long ticketId, String eventType);

    @Query("SELECT e FROM TicketEvent e WHERE e.createdAt >= :start AND e.createdAt < :endExcl ORDER BY e.createdAt DESC")
    List<TicketEvent> findCreatedBetween(
            @Param("start") Instant start, @Param("endExcl") Instant endExcl, Pageable pageable);
}
