package com.sysco.web.repo;

import com.sysco.web.domain.UserPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    List<UserPermission> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
