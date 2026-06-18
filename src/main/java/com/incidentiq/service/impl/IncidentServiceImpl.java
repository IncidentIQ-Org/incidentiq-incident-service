package com.incidentiq.service.impl;

import com.incidentiq.constants.IncidentConstants;
import com.incidentiq.dto.ResolutionRequest;
import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.request.UpdateIncidentRequest;
import com.incidentiq.dto.response.IncidentResponse;
import com.incidentiq.dto.response.IncidentStatsResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.exception.IncidentNotFoundException;
import com.incidentiq.exception.InvalidStatusTransitionException;
import com.incidentiq.dto.event.IncidentResolvedEvent;
import com.incidentiq.mapper.IncidentMapper;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.repository.SlaConfigRepository;
import com.incidentiq.service.IncidentService;
import com.incidentiq.service.SimilarityDetectionService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final com.incidentiq.service.NotificationService notificationService;
    private final SlaConfigRepository slaConfigRepository;
    private final com.incidentiq.repository.SlaExtensionRequestRepository slaExtensionRequestRepository;

    private final ApplicationEventPublisher eventPublisher;

    public IncidentServiceImpl(IncidentRepository incidentRepository,
                               com.incidentiq.service.AuditService auditService,
                               com.incidentiq.service.TimelineService timelineService,
                               com.incidentiq.security.AuthorizationService authService,
                               RestTemplate restTemplate,
                               SimilarityDetectionService similarityDetectionService,
                               com.incidentiq.service.NotificationService notificationService,
                               SlaConfigRepository slaConfigRepository,
                               com.incidentiq.repository.SlaExtensionRequestRepository slaExtensionRequestRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
        this.timelineService = timelineService;
        this.authService = authService;
        this.restTemplate = restTemplate;
        this.similarityDetectionService = similarityDetectionService;
        this.notificationService = notificationService;
        this.slaConfigRepository = slaConfigRepository;
        this.slaExtensionRequestRepository = slaExtensionRequestRepository;
        this.eventPublisher = eventPublisher;
    }

    @Data
    private static class ExternalUserResponse {
        private Long id;
        private String specialization;
        private Integer workloadScore;
        private String role;
    }

    @Override
    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Long currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new RuntimeException("User must be authenticated to create an incident");
        }

        Incident incident = IncidentMapper.toEntity(request);
        incident.setCreatedBy(currentUserId);

        // Auto-Assignment Logic based on workload score
        incident.setAssignedTo(determineAssignee(incident.getCategory(), incident.getPriority()));

        // SLA Calculation based on priority
        incident.setDueDate(calculateDueDate(incident.getPriority()));

        // Combine user-provided tags with auto-generated keywords
        java.util.Set<String> tagSet = new java.util.HashSet<>();
        if (incident.getTags() != null && !incident.getTags().isEmpty()) {
            for (String t : incident.getTags().split(",")) {
                if (t != null && !t.trim().isEmpty()) {
                    tagSet.add(t.trim());
                }
            }
        }
        for (String kw : similarityDetectionService.extractKeywords(incident.getTitle() + " " + incident.getDescription())) {
            tagSet.add(kw);
        }
        incident.setTags(String.join(",", tagSet));

        Incident saved = incidentRepository.save(incident);
        
        timelineService.logEvent(saved.getId(), "CREATED", "Incident created with status OPEN", currentUserId);
        auditService.logAction("CREATE_INCIDENT", currentUserId, "Incident", saved.getId(), "Title: " + saved.getTitle());

        // Notify the auto-assigned technician
        if (saved.getAssignedTo() != null) {
            notificationService.notifyAssignment(saved.getAssignedTo(), saved.getId(), saved.getTitle());
            timelineService.logEvent(saved.getId(), "AUTO_ASSIGNED",
                "Incident auto-assigned to technician #" + saved.getAssignedTo() + " based on workload balancing", currentUserId);
        }

        // Notify the creator (employee) about the incident creation
        if (!currentUserId.equals(saved.getAssignedTo())) {
            notificationService.notifyCreation(
                currentUserId,
                saved.getId(),
                saved.getTitle(),
                "Your incident has been created and assigned to technician #" + saved.getAssignedTo()
            );
        }

        // Notify managers and admins about the new incident
        try {
            String url = "http://user-service/all";
            ExternalUserResponse[] users = restTemplate.getForObject(url, ExternalUserResponse[].class);
            if (users != null) {
                for (ExternalUserResponse u : users) {
                    if ("ROLE_MANAGER".equals(u.getRole()) || "ROLE_ADMIN".equals(u.getRole())) {
                        notificationService.notifyCreation(
                            u.getId(), 
                            saved.getId(), 
                            saved.getTitle(), 
                            String.format("Priority: %s, Category: %s", saved.getPriority(), saved.getCategory())
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify managers/admins of incident creation: {}", e.getMessage());
        }

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
            
            // Notify the assigned user about status change
            if (updated.getAssignedTo() != null) {
                notificationService.notifyStatusChange(updated.getAssignedTo(), id, updated.getTitle(), oldStatus, updated.getStatus().name());
            }
            // Also notify the reporter
            if (updated.getCreatedBy() != null && !updated.getCreatedBy().equals(updated.getAssignedTo())) {
                notificationService.notifyStatusChange(updated.getCreatedBy(), id, updated.getTitle(), oldStatus, updated.getStatus().name());
            }
            // Notify managers and admins of status change
            try {
                String url = "http://user-service/all";
                ExternalUserResponse[] users = restTemplate.getForObject(url, ExternalUserResponse[].class);
                if (users != null) {
                    for (ExternalUserResponse u : users) {
                        if ("ROLE_MANAGER".equals(u.getRole()) || "ROLE_ADMIN".equals(u.getRole())) {
                            notificationService.notifyStatusChange(u.getId(), id, updated.getTitle(), oldStatus, updated.getStatus().name());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to notify managers/admins of status change: {}", e.getMessage());
            }
            
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
            
            // Notify both old and new assignees about reassignment
            notificationService.notifyReassignment(oldAssignee, newAssignee, id, updated.getTitle());
        } else if (newAssignee != null && oldAssignee == null) {
            // First assignment (was unassigned before)
            notificationService.notifyAssignment(newAssignee, id, updated.getTitle());
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
        // Delete Hibernate-managed child records that don't have DB-level CASCADE
        slaExtensionRequestRepository.deleteAll(slaExtensionRequestRepository.findByIncidentId(id));
        // incident_comments and incident_timeline are covered by V3 Flyway ON DELETE CASCADE
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
    public Page<IncidentResponse> getMyIncidents(Pageable pageable) {
        Long currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new RuntimeException("User must be authenticated to retrieve their incidents");
        }
        Page<Incident> incidents = incidentRepository.findByCreatedBy(currentUserId, pageable);
        return incidents.map(IncidentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> getKnowledgeBase(Pageable pageable) {
        Page<Incident> incidents = incidentRepository.findByStatusIn(java.util.List.of(com.incidentiq.enums.IncidentStatus.RESOLVED, com.incidentiq.enums.IncidentStatus.CLOSED), pageable);
        return incidents.map(IncidentMapper::toResponse);
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
        if (request.getTitle() != null) {
            incident.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            incident.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            incident.setCategory(request.getCategory());
        }
        if (request.getPriority() != null) {
            incident.setPriority(request.getPriority());
        }
        if (request.getStatus() != null) {
            if (!incident.getStatus().canTransitionTo(request.getStatus())) {
                throw new InvalidStatusTransitionException(
                        String.format("Invalid status transition from %s to %s.",
                                incident.getStatus(), request.getStatus()));
            }
            incident.setStatus(request.getStatus());
        }
        if (request.getAssignedTo() != null) {
            Long oldAssignee = incident.getAssignedTo();
            Long newAssignee = request.getAssignedTo();
            incident.setAssignedTo(newAssignee);
            
            // Send notification for assignment
            if (newAssignee != null && !newAssignee.equals(oldAssignee)) {
                notificationService.notifyAssignment(newAssignee, incident.getId(), incident.getTitle());
                if (oldAssignee != null) {
                    notificationService.notifyReassignment(oldAssignee, newAssignee, incident.getId(), incident.getTitle());
                }
            }
        }
        if (request.getDueDate() != null) {
            incident.setDueDate(request.getDueDate());
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
            log.warn("Auto-assignment failed for category {}: {}", category, e.getMessage());
        }
        log.info("No available technician for category {}. Incident will remain unassigned.", category);
        return null;
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
        int targetHours = slaConfigRepository.findByPriority(priority)
                .map(com.incidentiq.model.SlaConfig::getTargetHours)
                .orElseGet(() -> switch (priority) {
                    case CRITICAL -> 3;
                    case HIGH -> 12;
                    case MEDIUM -> 24;
                    case LOW -> 48;
                });
        return now.plusHours(targetHours);
    }

    @Override
    @Transactional
    public IncidentResponse resolveIncidentWithDetails(Long id, ResolutionRequest resolutionRequest) {
        Incident incident = findIncidentOrThrow(id);
        Long currentUserId = authService.getCurrentUserId();
        String oldStatus = incident.getStatus().name();

        // Validate status transition
        if (!incident.getStatus().canTransitionTo(IncidentStatus.RESOLVED)) {
            throw new InvalidStatusTransitionException(
                String.format("Invalid status transition from %s to RESOLVED.", incident.getStatus()));
        }

        // SLA Deadline Validation
        LocalDateTime now = LocalDateTime.now();
        boolean willMissSLA = incident.getDueDate() != null && now.isAfter(incident.getDueDate());
        
        // If the request doesn't specify SLA status, auto-detect based on deadline
        if (resolutionRequest.getSlaMissed() == null && willMissSLA) {
            log.warn("Incident {} is being resolved after SLA deadline. Due date was: {}, Current time: {}", 
                id, incident.getDueDate(), now);
            // Auto-mark as SLA missed if not explicitly set
            resolutionRequest.setSlaMissed(true);
        }

        // Set resolution details
        incident.setRootCause(resolutionRequest.getRootCause());
        incident.setResolutionSteps(resolutionRequest.getResolutionSteps());
        incident.setResolutionSummary(resolutionRequest.getResolutionSummary());
        incident.setActualResolutionMinutes(resolutionRequest.getActualResolutionMinutes());
        incident.setResolvedAt(now);
        incident.setSlaMissed(resolutionRequest.getSlaMissed() != null ? resolutionRequest.getSlaMissed() : false);
        incident.setStatus(IncidentStatus.RESOLVED);

        Incident saved = incidentRepository.save(incident);

        // Update assignee workload
        if (incident.getAssignedTo() != null) {
            notifyWorkloadUpdate(incident.getAssignedTo(), -calculateWeight(incident.getPriority()), -1);
        }

        // Notify assignee about resolution
        if (saved.getAssignedTo() != null) {
            notificationService.notifyStatusChange(saved.getAssignedTo(), saved.getId(), saved.getTitle(), oldStatus, IncidentStatus.RESOLVED.name());
        }
        // Notify reporter about resolution
        if (saved.getCreatedBy() != null && !saved.getCreatedBy().equals(saved.getAssignedTo())) {
            notificationService.notifyStatusChange(saved.getCreatedBy(), saved.getId(), saved.getTitle(), oldStatus, IncidentStatus.RESOLVED.name());
        }
        // Notify managers and admins about resolution
        try {
            String url = "http://user-service/all";
            ExternalUserResponse[] users = restTemplate.getForObject(url, ExternalUserResponse[].class);
            if (users != null) {
                for (ExternalUserResponse u : users) {
                    if ("ROLE_MANAGER".equals(u.getRole()) || "ROLE_ADMIN".equals(u.getRole())) {
                        notificationService.notifyStatusChange(u.getId(), saved.getId(), saved.getTitle(), oldStatus, IncidentStatus.RESOLVED.name());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify managers/admins of resolution: {}", e.getMessage());
        }

        String slaStatus = saved.getSlaMissed() != null && saved.getSlaMissed() ? "SLA MISSED" : "SLA MET";
        timelineService.logEvent(saved.getId(), "RESOLVED", 
            String.format("Incident resolved. %s. Resolution: %s", slaStatus, resolutionRequest.getResolutionSummary()), 
            currentUserId);
        auditService.logAction("RESOLVE_INCIDENT", currentUserId, "Incident", saved.getId(), 
            String.format("Resolution summary: %s, SLA Status: %s", resolutionRequest.getResolutionSummary(), slaStatus));

        log.info("Incident {} resolved with details. SLA Status: {}", id, slaStatus);

        // Publish gamification event
        publishIncidentResolvedEvent(saved, resolutionRequest);

        return IncidentMapper.toResponse(saved);
    }

    private void publishIncidentResolvedEvent(Incident incident, ResolutionRequest resolutionRequest) {
        if (incident.getAssignedTo() != null) {
            boolean slaMet = incident.getDueDate() == null || 
                    incident.getResolvedAt().isBefore(incident.getDueDate()) || 
                    incident.getResolvedAt().isEqual(incident.getDueDate());

            Integer resolutionTime = null;
            if (incident.getResolvedAt() != null && incident.getCreatedAt() != null) {
                resolutionTime = (int) java.time.Duration.between(
                        incident.getCreatedAt(), incident.getResolvedAt()
                ).toMinutes();
            }

            com.incidentiq.dto.event.IncidentResolvedEvent event = com.incidentiq.dto.event.IncidentResolvedEvent.builder()
                    .incidentId(incident.getId())
                    .incidentTitle(incident.getTitle())
                    .assignedTo(incident.getAssignedTo())
                    .createdBy(incident.getCreatedBy())
                    .category(incident.getCategory() != null ? incident.getCategory().name() : null)
                    .priority(incident.getPriority() != null ? incident.getPriority().name() : null)
                    .slaMet(slaMet)
                    .resolutionTimeMinutes(resolutionTime)
                    .resolvedAt(incident.getResolvedAt())
                    .dueDate(incident.getDueDate())
                    .build();

            // Make REST call to gamification-service instead of local event publishing
            try {
                org.springframework.http.ResponseEntity<Void> response = this.restTemplate.postForEntity(
                    "http://GAMIFICATION-SERVICE/gamification/internal/resolve", event, Void.class);
                log.info("Sent resolution event to Gamification Service for incident {}: status {}", incident.getId(), response.getStatusCode());
            } catch (Exception e) {
                log.error("Failed to send resolution event to Gamification Service: {}", e.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<IncidentResponse> getMyAssignedIncidents() {
        Long currentUserId = authService.getCurrentUserId();
        if (currentUserId == null) {
            throw new RuntimeException("User must be authenticated to view assigned incidents");
        }
        return incidentRepository.findAllByAssignedTo(currentUserId).stream()
                .map(IncidentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<IncidentResponse> getAllIncidentsForExport() {
        return incidentRepository.findAll().stream()
                .map(IncidentMapper::toResponse)
                .toList();
    }
}
