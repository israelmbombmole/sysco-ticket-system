package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shift_punch_day_overrides")
public class ShiftPunchDayOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "for_date", nullable = false)
    private LocalDate forDate;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "granted_by_user_id", nullable = false)
    private Long grantedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When true, arrival punch may bypass the configured arrival window after tolerance. */
    @Column(name = "allow_arrival_bypass", nullable = false)
    private boolean allowArrivalBypass = true;

    /** When true, departure punch may bypass the configured departure window. */
    @Column(name = "allow_departure_bypass", nullable = false)
    private boolean allowDepartureBypass = true;
}
