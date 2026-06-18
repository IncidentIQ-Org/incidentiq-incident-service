package com.incidentiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionRequest {
    private String rootCause;
    private String resolutionSteps;
    private String resolutionSummary;
    private Integer actualResolutionMinutes;
    private Boolean slaMissed;
}
