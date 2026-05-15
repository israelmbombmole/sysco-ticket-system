package com.sysco.web.repo;

import com.sysco.web.domain.AgendaPublicHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendaPublicHolidayRepository extends JpaRepository<AgendaPublicHoliday, Long> {

    List<AgendaPublicHoliday> findAllByOrderByStartDateAsc();

    @Query(
            """
            SELECT h FROM AgendaPublicHoliday h WHERE h.startDate <= :d AND h.endDate >= :d
            """)
    List<AgendaPublicHoliday> findCovering(@Param("d") LocalDate d);

    @Query(
            """
            SELECT h FROM AgendaPublicHoliday h WHERE h.endDate >= :from AND h.startDate <= :through
            ORDER BY h.startDate ASC, h.id ASC
            """)
    List<AgendaPublicHoliday> findSpanningWindow(@Param("from") LocalDate from, @Param("through") LocalDate through);
}
