package com.sysco.web.repo;

import com.sysco.web.domain.FieldMission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldMissionRepository extends JpaRepository<FieldMission, Long>, JpaSpecificationExecutor<FieldMission> {

    Optional<FieldMission> findByMissionCode(String missionCode);

    @Query(
            """
            SELECT m FROM FieldMission m WHERE m.leadUserId = :uid
            OR EXISTS (SELECT 1 FROM FieldMissionParticipant p WHERE p.missionId = m.id AND p.userId = :uid)
            """)
    List<FieldMission> findForMyWork(@Param("uid") Long uid);
}
