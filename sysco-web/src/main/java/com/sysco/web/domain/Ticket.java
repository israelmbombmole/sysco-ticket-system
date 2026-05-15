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

@Getter
@Setter
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    private String priority;

    private String status;

    @Column(name = "ticket_type", nullable = false)
    private String ticketType;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Last user who closed the ticket (reopen eligibility vs hierarchy). */
    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "ticket_number")
    private String ticketNumber;

    @Column(name = "close_requested_at")
    private Instant closeRequestedAt;

    @Column(name = "close_requested_by")
    private Long closeRequestedBy;

    /** Normalized role key ({@link com.sysco.web.security.RoleKeys}); null = senior assignee may finalize. */
    @Column(name = "close_review_role")
    private String closeReviewRole;

    /** JSON array of user ids that may finalize closure (task creators other than requester); null = role-based. */
    @Column(name = "close_review_user_ids")
    private String closeReviewUserIds;

    /** When set, this ticket row was absorbed into {@link #mergedIntoTicketId} (survivor). */
    @Column(name = "merged_into_ticket_id")
    private Long mergedIntoTicketId;

    /** Direction that sent an external escalation (inbox metadata). */
    @Column(name = "external_escalation_source_direction_id")
    private Long externalEscalationSourceDirectionId;

    /** Direction pool that must receive / triage the escalated ticket. */
    @Column(name = "external_escalation_target_direction_id")
    private Long externalEscalationTargetDirectionId;

    /** Direction (organizational row) the reporter belongs to when opening the ticket. */
    @Column(name = "reporter_direction_id")
    private Long reporterDirectionId;

    @Column(name = "reporter_sous_direction_id")
    private Long reporterSousDirectionId;

    /** Office / service name where the reporter is located. */
    @Column(name = "reporting_office")
    private String reportingOffice;

    /** Preset issue code from create-ticket wizard; {@code __OTHER__} = free-text summary only. */
    @Column(name = "issue_preset_code")
    private String issuePresetCode;

    /** Direction responsible for triage (SOUS-DIRECTEUR notifications). */
    @Column(name = "handling_direction_id")
    private Long handlingDirectionId;

    /**
     * When non-null, this ticket was created by the job planner for a future window and must stay out of operational
     * lists until this instant (inclusive visibility afterward).
     */
    @Column(name = "planner_visible_after")
    private Instant plannerVisibleAfter;
}
