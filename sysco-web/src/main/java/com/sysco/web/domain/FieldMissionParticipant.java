package com.sysco.web.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "field_mission_participants")
@IdClass(FieldMissionParticipantPk.class)
@NoArgsConstructor
@AllArgsConstructor
public class FieldMissionParticipant {

    @Id
    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** {@code M} = Messieurs, {@code F} = Mesdames (official order lists). */
    @Column(name = "salutation", length = 1)
    private String salutation;
}
