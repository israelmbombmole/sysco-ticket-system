package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "courier_journey_events")
public class CourierJourneyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "packet_id", nullable = false)
    private Long packetId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "at_time")
    private Instant atTime;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "related_user_id")
    private Long relatedUserId;

    @Column(name = "direction_id")
    private Long directionId;

    @Column(name = "sous_direction_id")
    private Long sousDirectionId;

    @Lob
    private String note;
}
