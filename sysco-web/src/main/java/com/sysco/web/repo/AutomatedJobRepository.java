package com.sysco.web.repo;

import com.sysco.web.domain.AutomatedJob;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomatedJobRepository extends JpaRepository<AutomatedJob, Long> {
    List<AutomatedJob> findByTicketId(Long ticketId);

    List<AutomatedJob> findByAssigneeUserId(Long assigneeUserId);

    List<AutomatedJob> findByTicketIdAndAssigneeUserId(Long ticketId, Long assigneeUserId);

    List<AutomatedJob> findByActive(Integer active);

    long countByTicketIdAndStatusIgnoreCase(Long ticketId, String status);

    @Query("SELECT j FROM AutomatedJob j WHERE j.createdAt >= :start AND j.createdAt < :endExcl ORDER BY j.createdAt DESC")
    List<AutomatedJob> findCreatedBetween(
            @Param("start") Instant start, @Param("endExcl") Instant endExcl, Pageable pageable);

    @Query(
            "SELECT j FROM AutomatedJob j WHERE j.closedAt IS NOT NULL AND j.closedAt >= :start AND j.closedAt < :endExcl ORDER BY j.closedAt DESC")
    List<AutomatedJob> findClosedBetween(
            @Param("start") Instant start, @Param("endExcl") Instant endExcl, Pageable pageable);
}
