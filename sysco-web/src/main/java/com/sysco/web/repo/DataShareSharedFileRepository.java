package com.sysco.web.repo;

import com.sysco.web.domain.DataShareSharedFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataShareSharedFileRepository extends JpaRepository<DataShareSharedFile, Long> {

    List<DataShareSharedFile> findByRecipientUsernameIgnoreCaseOrderBySharedAtDesc(String recipientUsername);

    List<DataShareSharedFile> findBySharedByIgnoreCaseOrderBySharedAtDesc(String sharedBy);

    List<DataShareSharedFile> findAllByOrderBySharedAtDesc();

    List<DataShareSharedFile> findByFilePathAndSharedByIgnoreCase(String filePath, String sharedBy);

    long countByFilePath(String filePath);
}
