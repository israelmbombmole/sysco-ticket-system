package com.sysco.web.repo;

import com.sysco.web.domain.ShiftPunchDayOverride;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftPunchDayOverrideRepository extends JpaRepository<ShiftPunchDayOverride, Long> {

    boolean existsByForDateAndUserId(LocalDate forDate, Long userId);

    Optional<ShiftPunchDayOverride> findByForDateAndUserId(LocalDate forDate, Long userId);

    List<ShiftPunchDayOverride> findByForDate(LocalDate forDate);

    void deleteByForDateAndUserId(LocalDate forDate, Long userId);
}
