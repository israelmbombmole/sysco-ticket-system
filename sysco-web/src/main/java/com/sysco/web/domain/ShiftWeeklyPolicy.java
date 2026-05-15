package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shift_weekly_policies")
public class ShiftWeeklyPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_monday", nullable = false)
    private LocalDate weekMonday;

    @Column(name = "sous_direction_id", nullable = false)
    private Long sousDirectionId;

    @Column(name = "arrival_allowed_from", nullable = false)
    private LocalTime arrivalAllowedFrom;

    @Column(name = "arrival_on_time_until", nullable = false)
    private LocalTime arrivalOnTimeUntil;

    @Column(name = "arrival_late_until", nullable = false)
    private LocalTime arrivalLateUntil;

    @Column(name = "departure_allowed_from", nullable = false)
    private LocalTime departureAllowedFrom;

    @Column(name = "departure_allowed_until", nullable = false)
    private LocalTime departureAllowedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
