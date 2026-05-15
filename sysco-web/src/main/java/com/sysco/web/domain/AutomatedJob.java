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
@Table(name = "automated_jobs")
public class AutomatedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "job_description")
    private String jobDescription;

    @Column(name = "due_at", nullable = false)
    private String dueAt;

    @Column(name = "reminder_minutes")
    private Integer reminderMinutes = 60;

    @Column(name = "assignee_user_id")
    private Long assigneeUserId;

    @Column(name = "ticket_id")
    private Long ticketId;

    private String priority = "MEDIUM";

    @Column(name = "attachment_paths")
    private String attachmentPaths;

    private String status = "OPEN";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by")
    private Long createdBy;

    private String recurrence = "ONCE";

    private Integer active = 1;

    @Column(name = "last_reminder_at")
    private String lastReminderAt;

    @Column(name = "last_ticket_created_at")
    private String lastTicketCreatedAt;

    /** Snapshot of {@link #dueAt} for which the "due now" notification was already processed (avoids duplicates). */
    @Column(name = "last_due_notified_for")
    private String lastDueNotifiedFor;

    @Column(name = "created_at")
    private Instant createdAt;
}
