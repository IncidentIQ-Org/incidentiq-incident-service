package com.incidentiq.dto.request;

import com.incidentiq.constants.IncidentConstants;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for creating a new incident.
 * createdBy is automatically resolved from the authenticated user's JWT token.
 * assignedTo is automatically determined by the workload-balancing algorithm.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateIncidentRequest {

    @NotBlank(message = IncidentConstants.TITLE_REQUIRED)
    @Size(max = 200, message = IncidentConstants.TITLE_MAX_LENGTH)
    private String title;

    @NotBlank(message = IncidentConstants.DESCRIPTION_REQUIRED)
    @Size(max = 2000, message = IncidentConstants.DESCRIPTION_MAX_LENGTH)
    private String description;

    @NotNull(message = IncidentConstants.CATEGORY_REQUIRED)
    private IncidentCategory category;

    @NotNull(message = IncidentConstants.PRIORITY_REQUIRED)
    private IncidentPriority priority;

    // Optional fields - backend can override these
    private IncidentStatus status;
    private Long createdBy;
    private String tags;
}
