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
@Table(name = "courier_packets")
public class CourierPacket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ref_code", nullable = false, unique = true)
    private String refCode;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String status;

    @Column(name = "target_direction_id")
    private Long targetDirectionId;

    @Column(name = "target_sous_direction_id")
    private Long targetSousDirectionId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "assigned_sous_directeur_id")
    private Long assignedSousDirecteurId;

    @Column(name = "assigned_inspecteur_id")
    private Long assignedInspecteurId;

    @Column(name = "assigned_controleur_id")
    private Long assignedControleurId;

    @Column(name = "assigned_verificateur_id")
    private Long assignedVerificateurId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    private String sender;

    private String priority;

    @Column(name = "registration_date")
    private String registrationDate;

    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "secretaire_can_route_sous")
    private Integer secretaireCanRouteSous = 0;

    @Column(name = "linked_ticket_id")
    private Long linkedTicketId;
}
