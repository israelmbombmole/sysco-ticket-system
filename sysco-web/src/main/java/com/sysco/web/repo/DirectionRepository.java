package com.sysco.web.repo;

import com.sysco.web.domain.Direction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectionRepository extends JpaRepository<Direction, Long> {

    boolean existsByNameContainingIgnoreCase(String fragment);

    boolean existsByNameIgnoreCase(String name);

    List<Direction> findAllBySousDirectionIdOrderByNameAsc(Long sousDirectionId);
}
