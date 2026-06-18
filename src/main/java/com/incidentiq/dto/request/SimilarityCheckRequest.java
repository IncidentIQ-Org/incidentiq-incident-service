package com.incidentiq.dto.request;

import com.incidentiq.enums.IncidentCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for checking similar incidents before creation.
 * Used by the POST /similar endpoint to detect potential duplicates
 * and surface suggested resolution steps.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimilarityCheckRequest {

    private String title;

    private String description;

    private IncidentCategory category;
}
