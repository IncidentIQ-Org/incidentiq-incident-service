package com.incidentiq.dto;

import com.incidentiq.enums.Complexity;
import com.incidentiq.enums.IncidentPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaConfigDto {
    private Long id;
    private IncidentPriority priority;
    private Complexity complexity;
    private Integer targetHours;
}
