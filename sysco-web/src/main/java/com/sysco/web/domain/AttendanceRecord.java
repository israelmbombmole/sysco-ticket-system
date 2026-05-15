package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "direction_name", nullable = false, length = 512)
    private String directionName;

    @Column(name = "arrival_at", nullable = false)
    private Instant arrivalAt;

    @Column(name = "departure_at")
    private Instant departureAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "late_arrival", nullable = false)
    private boolean lateArrival;

    @Column(name = "outside_window_override", nullable = false)
    private boolean outsideWindowOverride;
}
