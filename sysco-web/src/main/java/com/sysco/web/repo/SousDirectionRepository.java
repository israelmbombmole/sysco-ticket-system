package com.sysco.web.repo;

import com.sysco.web.domain.SousDirection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SousDirectionRepository extends JpaRepository<SousDirection, Long> {

    Optional<SousDirection> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
