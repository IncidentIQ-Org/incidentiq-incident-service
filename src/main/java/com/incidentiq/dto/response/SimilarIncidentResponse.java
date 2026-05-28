package com.incidentiq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for similar/duplicate incident suggestions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarIncidentResponse {
    private Long incidentId;
    private String title;
    private String category;
    private String priority;
    private String status;
    private int similarityScore;
    private boolean isDuplicate;
    private LocalDateTime createdAt;
    /** Actionable suggestion based on matched incident's history */
    private String suggestion;
}
