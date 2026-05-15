package com.sysco.web.repo;

import com.sysco.web.domain.MonthlyReport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findTopByOrderByGeneratedAtDesc();

    Optional<MonthlyReport> findByMonthKey(String monthKey);
}
