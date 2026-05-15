package com.sysco.web.repo;

import com.sysco.web.domain.TicketAssignment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketAssignmentRepository extends JpaRepository<TicketAssignment, Long> {
    List<TicketAssignment> findByTicketId(Long ticketId);

    List<TicketAssignment> findByTicketIdIn(Collection<Long> ticketIds);

    List<TicketAssignment> findByUserId(Long userId);

    boolean existsByTicketIdAndUserId(Long ticketId, Long userId);

    /**
     * Bulk-delete assignments for a ticket. Flush/clear so a new assignment row can be inserted immediately in the same
     * transaction (avoids unique constraint races with derived deletes).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TicketAssignment a WHERE a.ticketId = :ticketId")
    void deleteByTicketId(@Param("ticketId") Long ticketId);

    @Query(
            "SELECT a FROM TicketAssignment a WHERE a.assignedAt IS NOT NULL AND a.assignedAt >= :start AND a.assignedAt < :endExcl ORDER BY a.assignedAt DESC")
    List<TicketAssignment> findAssignedBetween(
            @Param("start") Instant start, @Param("endExcl") Instant endExcl, Pageable pageable);
}
