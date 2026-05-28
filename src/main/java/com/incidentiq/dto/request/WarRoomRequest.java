package com.incidentiq.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a War Room session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarRoomRequest {

    @NotBlank(message = "War Room name is required")
    private String name;

    private String description;

    /** IDs of incidents to link to this war room */
    private List<Long> incidentIds;

    /** IDs of users assigned as responders */
    private List<Long> responderIds;
}
