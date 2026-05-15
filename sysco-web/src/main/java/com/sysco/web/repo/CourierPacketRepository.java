package com.sysco.web.repo;

import com.sysco.web.domain.CourierPacket;
import com.sysco.web.web.dto.CourierRow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourierPacketRepository extends JpaRepository<CourierPacket, Long>, JpaSpecificationExecutor<CourierPacket> {

    long countByStatus(String status);

    @Query("SELECT COUNT(c) FROM CourierPacket c WHERE c.status <> :resolved")
    long countWhereStatusNot(@Param("resolved") String resolved);

    @Query(
            "SELECT new com.sysco.web.web.dto.CourierRow(c.id, c.refCode, c.title, c.sender, c.status, c.priority,"
                    + " c.createdAt) FROM CourierPacket c ORDER BY c.createdAt DESC")
    List<CourierRow> loadRecentCourierRows(Pageable pageable);

    List<CourierPacket> findTop10ByLinkedTicketIdOrderByCreatedAtDesc(Long linkedTicketId);

    @Modifying
    @Query("UPDATE CourierPacket c SET c.linkedTicketId = :survivorId WHERE c.linkedTicketId = :absorbedId")
    int relinkPacketsFromAbsorbedTicket(@Param("absorbedId") Long absorbedId, @Param("survivorId") Long survivorId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CourierPacket c SET c.linkedTicketId = NULL WHERE c.linkedTicketId = :ticketId")
    int unlinkCourierPacketsFromTicket(@Param("ticketId") Long ticketId);

    @Query(
            "SELECT c FROM CourierPacket c WHERE (c.createdAt >= :start AND c.createdAt < :endExcl) OR (c.resolvedAt IS NOT NULL AND"
                    + " c.resolvedAt >= :start AND c.resolvedAt < :endExcl) ORDER BY c.createdAt DESC")
    List<CourierPacket> findTouchedBetween(
            @Param("start") Instant start, @Param("endExcl") Instant endExcl, Pageable pageable);

    List<CourierPacket> findTop50ByCreatedByOrderByCreatedAtDesc(Long createdBy);
}
