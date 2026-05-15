package com.sysco.web.domain;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldMissionParticipantPk implements Serializable {
    private Long missionId;
    private Long userId;
}
