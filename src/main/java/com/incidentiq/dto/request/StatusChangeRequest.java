package com.incidentiq.dto.request;

import com.incidentiq.enums.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusChangeRequest {
    @NotNull(message = "Status is required")
    private IncidentStatus status;
}
