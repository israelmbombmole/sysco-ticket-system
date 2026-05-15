package com.sysco.web.repo;

import com.sysco.web.domain.AttendanceRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findFirstByUserIdAndDepartureAtIsNullOrderByArrivalAtDesc(Long userId);

    @Query(
            "SELECT a FROM AttendanceRecord a WHERE a.arrivalAt >= :start AND a.arrivalAt < :endExclusive ORDER BY a.arrivalAt DESC")
    List<AttendanceRecord> findByArrivalAtInRange(
            @Param("start") Instant start, @Param("endExclusive") Instant endExclusive);

    @Query("SELECT a FROM AttendanceRecord a WHERE a.departureAt IS NULL AND a.arrivalAt >= :startOfDay AND a.arrivalAt < :endOfDay")
    List<AttendanceRecord> findCurrentlyPresentForDay(
            @Param("startOfDay") Instant startOfDay, @Param("endOfDay") Instant endOfDay);

    @Query(
            "SELECT a FROM AttendanceRecord a WHERE a.userId = :userId AND a.arrivalAt >= :fromInc AND a.arrivalAt < :untilExc ORDER BY a.arrivalAt ASC")
    List<AttendanceRecord> findByUserIdAndArrivalAtRange(
            @Param("userId") long userId, @Param("fromInc") Instant fromInc, @Param("untilExc") Instant untilExc);
}
