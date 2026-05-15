package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/** Maps {@code courier_packet_extra_directions} — visibility duplicates for courier lists. */
@Getter
@Setter
@Entity
@Table(name = "courier_packet_extra_directions")
@IdClass(CourierPacketExtraDirection.Pk.class)
public class CourierPacketExtraDirection {

    @Id
    @Column(name = "packet_id", nullable = false)
    private Long packetId;

    @Id
    @Column(name = "direction_id", nullable = false)
    private Long directionId;

    public static class Pk implements Serializable {
        public Long packetId;
        public Long directionId;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Pk pk = (Pk) o;
            return Objects.equals(packetId, pk.packetId) && Objects.equals(directionId, pk.directionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packetId, directionId);
        }
    }
}
