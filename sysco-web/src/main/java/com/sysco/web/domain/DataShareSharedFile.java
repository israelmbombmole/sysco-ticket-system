package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "data_share_shared_file")
public class DataShareSharedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "shared_by", nullable = false, length = 255)
    private String sharedBy;

    @Column(name = "shared_by_role", length = 255)
    private String sharedByRole;

    @Column(name = "recipient_username", nullable = false, length = 255)
    private String recipientUsername;

    @Column(name = "shared_at", nullable = false)
    private LocalDateTime sharedAt;

    @Column(name = "visible_until")
    private LocalDateTime visibleUntil;

    @Column(name = "viewed", nullable = false)
    private boolean viewed;

    @Column(name = "file_path", nullable = false, length = 2048)
    private String filePath;

    @Column(name = "otp_code", nullable = false, length = 64)
    private String otpCode;
}
