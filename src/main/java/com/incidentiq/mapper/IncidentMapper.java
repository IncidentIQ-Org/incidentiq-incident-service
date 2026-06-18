package com.incidentiq.mapper;

import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.response.IncidentResponse;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.model.Incident;

import java.util.List;

/**
 * Maps between Incident entities and DTOs.
 * Utility class — all methods are static.
 */
public final class IncidentMapper {

    private IncidentMapper() {
        // Utility class — prevent instantiation
    }

    /**
     * Maps a create request DTO to a new Incident entity with OPEN status.
     */
    public static Incident toEntity(CreateIncidentRequest request) {
        return Incident.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .priority(request.getPriority())
                .status(IncidentStatus.OPEN)
                .build();
    }

    /**
     * Maps an Incident entity to a response DTO.
     */
    public static IncidentResponse toResponse(Incident incident) {
        return IncidentResponse.builder()
                .id(incident.getId())
                .title(incident.getTitle())
                .description(incident.getDescription())
                .category(incident.getCategory() != null ? incident.getCategory().name() : null)
                .priority(incident.getPriority() != null ? incident.getPriority().name() : null)
                .status(incident.getStatus() != null ? incident.getStatus().name() : null)
                .createdBy(incident.getCreatedBy())
                .assignedTo(incident.getAssignedTo())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .dueDate(incident.getDueDate())
                .slaBreached(incident.getDueDate() != null && incident.getDueDate().isBefore(java.time.LocalDateTime.now()) && incident.getStatus() != com.incidentiq.enums.IncidentStatus.CLOSED)
                .tags(incident.getTags())
                .rootCause(incident.getRootCause())
                .resolutionSteps(incident.getResolutionSteps())
                .resolutionSummary(incident.getResolutionSummary())
                .actualResolutionMinutes(incident.getActualResolutionMinutes())
                .resolvedAt(incident.getResolvedAt())
                .slaMissed(incident.getSlaMissed())
                .attachments(incident.getAttachments() != null ? incident.getAttachments().stream()
                        .map(att -> com.incidentiq.dto.AttachmentDto.builder()
                                .id(att.getId())
                                .fileName(att.getFileName())
                                .fileType(att.getFileType())
                                .fileSize(att.getFileSize())
                                .isSafe(att.getIsSafe())
                                .scanResult(att.getScanResult())
                                .uploadedBy(att.getUploadedBy())
                                .uploadedAt(att.getUploadedAt())
                                .build())
                        .toList() : java.util.Collections.emptyList())
                .build();
    }

    /**
     * Maps a list of Incident entities to response DTOs.
     */
    public static List<IncidentResponse> toResponseList(List<Incident> incidents) {
        return incidents.stream()
                .map(IncidentMapper::toResponse)
                .toList();
    }
}
