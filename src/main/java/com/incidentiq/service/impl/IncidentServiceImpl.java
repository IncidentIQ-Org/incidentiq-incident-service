package com.incidentiq.service.impl;

import com.incidentiq.constants.IncidentConstants;
import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.request.UpdateIncidentRequest;
import com.incidentiq.dto.response.IncidentResponse;
import com.incidentiq.dto.response.IncidentStatsResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.exception.IncidentNotFoundException;
import com.incidentiq.exception.InvalidStatusTransitionException;
import com.incidentiq.mapper.IncidentMapper;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.service.IncidentService;
import com.incidentiq.service.SimilarityDetectionService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IncidentServiceImpl implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceImpl.class);

    private final IncidentRepository incidentRepository;
    private final com.incidentiq.service.AuditService auditService;
    private final com.incidentiq.service.TimelineService timelineService;
    private final com.incidentiq.security.AuthorizationService authService;
    private final RestTemplate restTemplate;
    private final SimilarityDetectionService similarityDetectionService;

    public IncidentServiceImpl(IncidentRepository incidentRepository,
                               com.incidentiq.service.AuditService auditService,
                               com.incidentiq.service.TimelineService timelineService,
                               com.incidentiq.security.AuthorizationService authService,
                               RestTemplate restTemplate,
                               SimilarityDetectionService similarityDetectionService) {
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
        this.timelineService = timelineService;
        this.authService = authService;
        this.restTemplate = restTemplate;
        this.similarityDetectionService = similarityDetectionService;
    }

    @Data
    private static class ExternalUserResponse {
        private Long id;
        private String specialization;
        private Integer workloadScore;
    }

    @Override
    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Incident incident = IncidentMapper.toEntity(request);
        
        Long currentUserId = authService.getCurrentUserId();
        if (currentUserId != null) {
            incident.setCreatedBy(currentUserId);
        }

        // Auto-Assignment Logic
        incident.setAssignedTo(determineAssignee(incident.getCategory(), incident.getPriority()));

        // SLA Calculation
        incident.setDueDate(calculateDueDate(incident.getPriority()));

        // Auto-generate keyword tags for similarity detection
        String tags = String.join(",", similarityDetectionService.extractKeywords(
            incident.getTitle() + " " + incident.getDescription()));
        incident.setTags(tags);

        Incident saved = incidentRepository.save(incident);
        
        timelineService.logEvent(saved.getId(), "CREATED", "Incident created with status OPEN", currentUserId);
        auditService.logAction("CREATE_INCIDENT", currentUserId, "Incident", saved.getId(), "Title: " + saved.getTitle());

        log.info(IncidentConstants.LOG_INCIDENT_CREATED, saved.getId());
        return IncidentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getIncidentById(Long id) {
        Incident incident = findIncidentOrThrow(id);
        log.info(IncidentConstants.LOG_INCIDENT_FETCHED, id);
        return IncidentMapper.toResponse(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getAllIncidents(Pageable pageable) {
        return incidentRepository.findAll(pageable).map(IncidentMapper::toResponse);
    }

    @Override
    @Transactional
    public IncidentResponse updateIncident(Long id, UpdateIncidentRequest request) {
        Incident incident = findIncidentOrThrow(id);
        
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new RuntimeException("Cannot update a CLOSED incident");
        }

        Long userId = authService.getCurrentUserId();
        String oldStatus = incident.getStatus().name();
        Long oldAssignee = incident.getAssignedTo();
        applyUpdates(incident, request);
        Long newAssignee = incident.getAssignedTo();

        Incident updated = incidentRepository.save(incident);
        
        if (!oldStatus.equals(updated.getStatus().name())) {
            timelineService.logEvent(id, "STATUS_CHANGE", "Status changed from " + oldStatus + " to " + updated.getStatus(), userId);
            
            // If resolved or closed, decrement workload
            if (updated.getStatus() == IncidentStatus.RESOLVED || updated.getStatus() == IncidentStatus.CLOSED) {
                if (updated.getAssignedTo() != null) {
                    notifyWorkloadUpdate(updated.getAssignedTo(), -calculateWeight(updated.getPriority()), -1);
                }
            }
        }

        // Handle Reassignment Workload Sync
        if (oldAssignee != null && !oldAssignee.equals(newAssignee)) {
            // Decrement old
            notifyWorkloadUpdate(oldAssignee, -calculateWeight(updated.getPriority()), -1);
            // Increment new (if not null)
            if (newAssignee != null) {
                notifyWorkloadUpdate(newAssignee, calculateWeight(updated.getPriority()), 1);
            }
            timelineService.logEvent(id, "REASSIGNED", "Incident reassigned from " + oldAssignee + " to " + newAssignee, userId);
        }
        
        auditService.logAction("UPDATE_INCIDENT", userId, "Incident", id, "Updated status to: " + updated.getStatus());
        
        log.info(IncidentConstants.LOG_INCIDENT_UPDATED, id);
        return IncidentMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteIncident(Long id) {
        Incident incident = findIncidentOrThrow(id);
        Long userId = authService.getCurrentUserId();
        auditService.logAction("DELETE_INCIDENT", userId, "Incident", id, "Title: " + incident.getTitle());
        incidentRepository.delete(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentStatsResponse getIncidentStats() {
        long total = incidentRepository.count();
        long overdue = incidentRepository.countOverdue(LocalDateTime.now());

        return IncidentStatsResponse.builder()
                .totalIncidents(total)
                .overdueIncidents(overdue)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidentsByReporter(Long reporterId, Pageable pageable) {
        return incidentRepository.findByCreatedBy(reporterId, pageable).map(IncidentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getIncidentsByAssignee(Long assigneeId, Pageable pageable) {
        return incidentRepository.findByAssignedTo(assigneeId, pageable).map(IncidentMapper::toResponse);
    }

    private Incident findIncidentOrThrow(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with id: " + id));
    }

    private void applyUpdates(Incident incident, UpdateIncidentRequest request) {
        if (request.getStatus() != null) {
            if (!incident.getStatus().canTransitionTo(request.getStatus())) {
                throw new InvalidStatusTransitionException(
                        String.format("Invalid status transition from %s to %s.",
                                incident.getStatus(), request.getStatus()));
            }
            incident.setStatus(request.getStatus());
        }
        if (request.getAssignedTo() != null) {
            incident.setAssignedTo(request.getAssignedTo());
        }
    }

    private Long determineAssignee(IncidentCategory category, IncidentPriority priority) {
        try {
            String url = "http://user-service/technicians/best?specialization=" + category.name();
            ExternalUserResponse best = restTemplate.getForObject(url, ExternalUserResponse.class);
            if (best != null && best.getId() != null) {
                notifyWorkloadUpdate(best.getId(), calculateWeight(priority), 1);
                return best.getId();
            }
        } catch (Exception e) {
            log.error("Auto-assignment failed: {}", e.getMessage());
        }
        return 100L;
    }

    private void notifyWorkloadUpdate(Long userId, int deltaScore, int deltaCount) {
        try {
            String url = String.format("http://user-service/%d/workload?deltaScore=%d&deltaCount=%d", userId, deltaScore, deltaCount);
            restTemplate.postForObject(url, null, Void.class);
        } catch (Exception e) {
            log.error("Workload update failed: {}", e.getMessage());
        }
    }

    private int calculateWeight(IncidentPriority priority) {
        return switch (priority) {
            case CRITICAL -> 8;
            case HIGH -> 4;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    private LocalDateTime calculateDueDate(IncidentPriority priority) {
        LocalDateTime now = LocalDateTime.now();
        return switch (priority) {
            case CRITICAL -> now.plusHours(3);
            case HIGH -> now.plusHours(12);
            case MEDIUM -> now.plusHours(24);
            case LOW -> now.plusHours(48);
        };
    }
}
