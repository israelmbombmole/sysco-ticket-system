package com.sysco.web.repo;

import com.sysco.web.domain.Ticket;
import com.sysco.web.web.dto.TicketRow;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query(
            "SELECT t FROM Ticket t WHERE t.externalEscalationTargetDirectionId = :dirId"
                    + " AND UPPER(TRIM(COALESCE(t.status,''))) = 'ESCALATED'"
                    + " AND t.mergedIntoTicketId IS NULL ORDER BY t.updatedAt DESC")
    List<Ticket> findExternalEscalationInbox(@Param("dirId") Long dirId);

    /** Used to allocate the next {@code AUTO-TCK-{year}-#####} planner reference. */
    List<Ticket> findByTicketNumberStartingWith(String prefix);

    long countByStatusIgnoreCase(String status);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE upper(trim(t.status)) IN :statuses")
    long countByNormalizedStatuses(@Param("statuses") Set<String> statuses);

    @Query(
            "SELECT new com.sysco.web.web.dto.TicketRow(t.id, t.ticketNumber, t.title, t.status, t.priority,"
                    + " u.username) FROM Ticket t LEFT JOIN UserAccount u ON u.id = t.createdBy ORDER BY t.createdAt"
                    + " DESC")
    List<TicketRow> loadRecentTicketRows(Pageable pageable);

    @Query(
            "SELECT new com.sysco.web.web.dto.TicketRow(t.id, t.ticketNumber, t.title, t.status, t.priority,"
                    + " u.username) FROM Ticket t LEFT JOIN UserAccount u ON u.id = t.createdBy WHERE t.createdBy"
                    + " = :createdBy ORDER BY t.createdAt DESC")
    List<TicketRow> loadRecentTicketRowsByCreator(@Param("createdBy") Long createdBy, Pageable pageable);
}
