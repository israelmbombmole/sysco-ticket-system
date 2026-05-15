package com.sysco.web.repo;

import com.sysco.web.domain.FieldMissionAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FieldMissionAttachmentRepository extends JpaRepository<FieldMissionAttachment, Long> {

    List<FieldMissionAttachment> findByMissionIdOrderByUploadedAtDesc(Long missionId);

    long countByMissionId(Long missionId);
}
