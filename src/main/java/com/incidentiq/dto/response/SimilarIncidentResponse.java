package com.incidentiq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for similar/duplicate incident suggestions.
 * Includes full resolution details for enterprise-grade self-service resolution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarIncidentResponse {
    // --- Incident Identity ---
    private Long incidentId;
    private String title;
    private String category;
    private String priority;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    // --- Similarity Scoring ---
    private int similarityScore;         // 0-100 overall score
    private int categoryScore;           // 0-100 category component
    private int titleScore;              // 0-100 title component
    private int descriptionScore;        // 0-100 description component
    private boolean isDuplicate;         // true if score >= 85

    // --- Resolution Details (for self-service) ---
    private String rootCause;
    private String resolutionSteps;
    private String resolutionSummary;

    // --- Resolver Info ---
    private Long resolvedBy;
    private String resolvedByName;       // fetched from user-service or stored name

    // --- Actionable Suggestion ---
    private String suggestion;
}
