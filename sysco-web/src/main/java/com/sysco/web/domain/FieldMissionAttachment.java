package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Mission file attachment (shared with desktop SYSCO {@code field_mission_attachments}). */
@Getter
@Setter
@Entity
@Table(name = "field_mission_attachments")
public class FieldMissionAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "file_name", nullable = false, length = 4000)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 4000)
    private String filePath;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;
}
