package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "file_share_mgmt_access_requests")
public class FileShareMgmtAccessRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_OTP_ISSUED = "OTP_ISSUED";
    public static final String STATUS_CONSUMED = "CONSUMED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Column(name = "direction_id")
    private Long directionId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "otp_code", length = 32)
    private String otpCode;

    @Column(name = "otp_valid_until")
    private LocalDateTime otpValidUntil;

    @Column(name = "session_minutes", nullable = false)
    private int sessionMinutes = 5;

    @Column(name = "issued_by_user_id")
    private Long issuedByUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
