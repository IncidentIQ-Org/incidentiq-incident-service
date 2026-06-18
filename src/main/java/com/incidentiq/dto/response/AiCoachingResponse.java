package com.incidentiq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCoachingResponse {
    private String rootCauseAnalysis;
    private List<String> resolutionSteps;
    private Integer estimatedTimeMinutes;
    private List<SimilarIncidentContext> similarIncidentsUsed;
    private Double confidenceScore;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarIncidentContext {
        private Long incidentId;
        private String title;
        private String rootCause;
        private Long resolutionTimeMinutes;
    }
}
