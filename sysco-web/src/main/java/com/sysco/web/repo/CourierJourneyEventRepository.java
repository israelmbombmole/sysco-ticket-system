package com.sysco.web.repo;

import com.sysco.web.domain.CourierJourneyEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierJourneyEventRepository extends JpaRepository<CourierJourneyEvent, Long> {

    List<CourierJourneyEvent> findByPacketIdOrderByIdAsc(Long packetId);
}
