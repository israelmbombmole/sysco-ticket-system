package com.sysco.web.repo;

import com.sysco.web.domain.NotificationItem;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationItem, Long> {
    List<NotificationItem> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndIsRead(Long userId, Integer isRead);

    @Query("SELECT COUNT(n) FROM NotificationItem n WHERE n.createdAt >= :start AND n.createdAt < :endExcl")
    long countCreatedBetween(@Param("start") Instant start, @Param("endExcl") Instant endExcl);
}
