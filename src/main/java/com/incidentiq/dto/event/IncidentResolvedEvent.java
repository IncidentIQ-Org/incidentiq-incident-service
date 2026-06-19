package com.incidentiq.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResolvedEvent {
    private Long incidentId;
    private String incidentTitle;
    private String status;
    private Long assignedTo;
    private String assignedToUsername;
    private String assignedToFirstName;
    private String assignedToLastName;
    private Long createdBy;
    private String category;
    private String priority;
    private boolean slaMet;
    private Integer resolutionTimeMinutes;
    private LocalDateTime resolvedAt;
    private LocalDateTime dueDate;
}
