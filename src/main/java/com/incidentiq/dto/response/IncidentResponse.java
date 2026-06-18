package com.incidentiq.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Response DTO for incident data.
 * Decouples the API contract from the JPA entity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentResponse {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String priority;
    private String status;
    private Long createdBy;
    private Long assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime dueDate;
    private boolean slaBreached;
    private String tags;
    private String rootCause;
    private String resolutionSteps;
    private String resolutionSummary;
    private Integer actualResolutionMinutes;
    private LocalDateTime resolvedAt;
    private Boolean slaMissed;
    private java.util.List<com.incidentiq.dto.AttachmentDto> attachments;
}
