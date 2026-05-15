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
@Table(name = "monthly_reports")
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_key", nullable = false, unique = true)
    private String monthKey;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "total_tickets")
    private Integer totalTickets;

    @Column(name = "open_tickets")
    private Integer openTickets;

    @Column(name = "in_progress_tickets")
    private Integer inProgressTickets;

    @Column(name = "closed_tickets")
    private Integer closedTickets;
}
