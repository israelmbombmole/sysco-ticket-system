package com.sysco.web.repo;

import com.sysco.web.domain.ShiftWeeklyPolicy;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftWeeklyPolicyRepository extends JpaRepository<ShiftWeeklyPolicy, Long> {

    Optional<ShiftWeeklyPolicy> findByWeekMondayAndSousDirectionId(LocalDate weekMonday, Long sousDirectionId);

    List<ShiftWeeklyPolicy> findBySousDirectionIdAndWeekMondayBetween(
            Long sousDirectionId, LocalDate startWeekMondayInclusive, LocalDate endWeekMondayInclusive);

    /** Ascending by week — used to resolve effective windows for calendar weeks without an explicit row. */
    List<ShiftWeeklyPolicy> findBySousDirectionIdOrderByWeekMondayAsc(Long sousDirectionId);
}
