package com.incidentiq.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for manually escalating an incident.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalationRequest {

    @NotNull(message = "Incident ID is required")
    private Long incidentId;

    @NotBlank(message = "Escalation reason is required")
    private String reason;
}
