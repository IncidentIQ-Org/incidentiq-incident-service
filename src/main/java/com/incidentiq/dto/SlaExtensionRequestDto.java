package com.incidentiq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlaExtensionRequestDto {
    private Long incidentId;
    private String delayReason;
    private Integer additionalHoursRequested;
    private String expectedCompletionDate;
}
