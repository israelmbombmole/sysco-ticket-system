package com.sysco.web.repo;

import com.sysco.web.domain.FileShareMgmtAccessRequest;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileShareMgmtAccessRequestRepository extends JpaRepository<FileShareMgmtAccessRequest, Long> {

    List<FileShareMgmtAccessRequest> findByDirectionIdAndStatusInOrderByCreatedAtDesc(
            Long directionId, Collection<String> statuses);

    List<FileShareMgmtAccessRequest> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    Optional<FileShareMgmtAccessRequest> findFirstByRequesterUserIdAndStatusInOrderByCreatedAtDesc(
            Long requesterUserId, Collection<String> statuses);

    List<FileShareMgmtAccessRequest> findByRequesterUserIdAndStatusOrderByCreatedAtDesc(Long requesterUserId, String status);
}
