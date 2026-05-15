package com.sysco.web.repo;

import com.sysco.web.domain.CourierPacketExtraDirection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierPacketExtraDirectionRepository extends JpaRepository<CourierPacketExtraDirection, CourierPacketExtraDirection.Pk> {

    List<CourierPacketExtraDirection> findByPacketId(Long packetId);

    boolean existsByPacketIdAndDirectionId(Long packetId, Long directionId);

    void deleteByPacketIdAndDirectionId(Long packetId, Long directionId);
}
