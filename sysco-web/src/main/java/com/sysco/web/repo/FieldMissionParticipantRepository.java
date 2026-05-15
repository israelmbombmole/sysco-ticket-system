package com.sysco.web.repo;

import com.sysco.web.domain.FieldMissionParticipant;
import com.sysco.web.domain.FieldMissionParticipantPk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FieldMissionParticipantRepository extends JpaRepository<FieldMissionParticipant, FieldMissionParticipantPk> {

    List<FieldMissionParticipant> findByMissionId(Long missionId);

    @Transactional
    @Modifying
    @Query("DELETE FROM FieldMissionParticipant p WHERE p.missionId = :mid")
    void deleteByMissionId(@Param("mid") Long missionId);
}
