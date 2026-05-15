package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Field mission (desktop SYSCO {@code field_missions}) — site, dates, lead, participants, official order, report.
 */
@Getter
@Setter
@Entity
@Table(name = "field_missions")
public class FieldMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_code", nullable = false, length = 64)
    private String missionCode;

    @Column(nullable = false, length = 4000)
    private String title;

    @Column(name = "site_location", length = 4000)
    private String siteLocation;

    @Column(name = "start_date", length = 32)
    private String startDate;

    @Column(name = "end_date", length = 32)
    private String endDate;

    @Lob
    private String description;

    @Lob
    private String objectives;

    @Column(nullable = false, length = 32)
    private String status = "PLANNED";

    @Lob
    @Column(name = "report_text")
    private String reportText;

    @Column(name = "report_submitted_at")
    private Instant reportSubmittedAt;

    @Column(name = "report_author_id")
    private Long reportAuthorId;

    /** If set, only this participant may edit {@link #reportText}; if null, only the lead may edit. */
    @Column(name = "report_assignee_user_id")
    private Long reportAssigneeUserId;

    @Column(name = "lead_user_id")
    private Long leadUserId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "order_reference", length = 4000)
    private String orderReference;

    @Column(name = "order_issue_date", length = 32)
    private String orderIssueDate;

    @Column(name = "order_issued_by", length = 4000)
    private String orderIssuedBy;

    @Lob
    @Column(name = "order_body")
    private String orderBody;

    @Column(name = "transport_detail", length = 4000)
    private String transportDetail;

    @Column(name = "duration_note", length = 512)
    private String durationNote;

    @Column(name = "expenses_note", length = 512)
    private String expensesNote;

    @Column(name = "departure_note", length = 512)
    private String departureNote;

    @Column(name = "return_note", length = 512)
    private String returnNote;

    @Column(name = "observations_note", length = 4000)
    private String observationsNote;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
