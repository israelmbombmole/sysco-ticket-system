package com.sysco.web.repo;

import com.sysco.web.domain.AgendaAbsence;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendaAbsenceRepository extends JpaRepository<AgendaAbsence, Long> {

    List<AgendaAbsence> findAllByOrderByStartDateDesc();

    @Query(
            """
            SELECT COUNT(a) FROM AgendaAbsence a WHERE a.userId = :uid
            AND (:excludeId IS NULL OR a.id <> :excludeId)
            AND a.startDate <= :end AND a.endDate >= :start
            """)
    long countOverlapping(
            @Param("uid") Long userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("excludeId") Long excludeId);

    @Query(
            """
            SELECT a FROM AgendaAbsence a WHERE a.endDate >= :from AND a.startDate <= :through
            ORDER BY a.startDate ASC, a.id ASC
            """)
    List<AgendaAbsence> findSpanningWindow(@Param("from") LocalDate from, @Param("through") LocalDate through);
}
