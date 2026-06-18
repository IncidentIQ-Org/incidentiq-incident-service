package com.incidentiq.service;

import com.incidentiq.dto.SlaExtensionRequestDto;
import com.incidentiq.model.Incident;
import com.incidentiq.model.SlaExtensionRequest;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.repository.SlaExtensionRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlaExtensionService {

    private final SlaExtensionRequestRepository extensionRepository;
    private final IncidentRepository incidentRepository;
    private final com.incidentiq.service.AuditService auditService;
    private final com.incidentiq.service.TimelineService timelineService;
    private final com.incidentiq.security.AuthorizationService authService;
    private final com.incidentiq.service.NotificationService notificationService;

    /**
     * Creates a new SLA extension request for an incident.
     */
    @Transactional
    public SlaExtensionRequest createExtensionRequest(Long incidentId, SlaExtensionRequestDto request) {
        Long currentUserId = authService.getCurrentUserId();
        
        Incident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));

        LocalDateTime expectedDate = null;
        if (request.getExpectedCompletionDate() != null && !request.getExpectedCompletionDate().isBlank()) {
            try {
                // Handle both "yyyy-MM-ddTHH:mm" (datetime-local) and full ISO formats
                String dateStr = request.getExpectedCompletionDate();
                if (dateStr.length() == 16) {
                    expectedDate = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
                } else {
                    expectedDate = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            } catch (DateTimeParseException e) {
                log.warn("Could not parse expectedCompletionDate '{}': {}", request.getExpectedCompletionDate(), e.getMessage());
            }
        }

        if (expectedDate == null) {
            LocalDateTime baseDate = incident.getDueDate() != null ? incident.getDueDate() : LocalDateTime.now();
            expectedDate = baseDate.plusHours(request.getAdditionalHoursRequested());
            log.info("Fallback expectedCompletionDate set to {} (base + {} hours)", expectedDate, request.getAdditionalHoursRequested());
        }

        SlaExtensionRequest extension = SlaExtensionRequest.builder()
            .incidentId(incidentId)
            .requestedBy(currentUserId)
            .delayReason(request.getDelayReason())
            .additionalHoursRequested(request.getAdditionalHoursRequested())
            .expectedCompletionDate(expectedDate)
            .originalDueDate(incident.getDueDate())
            .status(SlaExtensionRequest.ExtensionStatus.PENDING)
            .build();

        SlaExtensionRequest saved = extensionRepository.save(extension);

        timelineService.logEvent(incidentId, "SLA_EXTENSION_REQUESTED",
            String.format("SLA extension requested for %d hours. Reason: %s",
                request.getAdditionalHoursRequested(), request.getDelayReason()),
            currentUserId);
        auditService.logAction("SLA_EXTENSION_REQUEST", currentUserId, "Incident", incidentId,
            String.format("Extension requested: %d hours, Reason: %s",
                request.getAdditionalHoursRequested(), request.getDelayReason()));

        // Notify managers about SLA extension request
        notificationService.notifyManagers(incidentId, incident.getTitle(),
            String.format("SLA extension requested for %d hours. Reason: %s",
                request.getAdditionalHoursRequested(), request.getDelayReason()));

        log.info("SLA extension request created for incident {} by user {}", incidentId, currentUserId);
        return saved;
    }

    /**
     * Approves an SLA extension request.
     */
    @Transactional
    public SlaExtensionRequest approveExtension(Long extensionId, String approvalReason) {
        Long currentUserId = authService.getCurrentUserId();
        
        SlaExtensionRequest extension = extensionRepository.findById(extensionId)
            .orElseThrow(() -> new RuntimeException("Extension request not found: " + extensionId));

        if (extension.getStatus() != SlaExtensionRequest.ExtensionStatus.PENDING) {
            throw new RuntimeException("Extension request is not pending");
        }

        extension.setStatus(SlaExtensionRequest.ExtensionStatus.APPROVED);
        extension.setApprovedBy(currentUserId);
        extension.setApprovalReason(approvalReason);
        extension.setReviewedAt(LocalDateTime.now());

        // Update incident due date
        Incident incident = incidentRepository.findById(extension.getIncidentId())
            .orElseThrow(() -> new RuntimeException("Incident not found: " + extension.getIncidentId()));
        
        LocalDateTime newDueDate = incident.getDueDate().plusHours(extension.getAdditionalHoursRequested());
        extension.setNewDueDate(newDueDate);
        incident.setDueDate(newDueDate);
        incidentRepository.save(incident);

        SlaExtensionRequest saved = extensionRepository.save(extension);

        timelineService.logEvent(extension.getIncidentId(), "SLA_EXTENSION_APPROVED",
            String.format("SLA extension approved. New deadline: %s. Reason: %s",
                newDueDate, approvalReason),
            currentUserId);
        auditService.logAction("SLA_EXTENSION_APPROVE", currentUserId, "Incident", extension.getIncidentId(),
            String.format("Extension approved. New deadline: %s", newDueDate));

        // Notify the requester about approval
        notificationService.notifySlaExtension(extension.getRequestedBy(), extension.getIncidentId(),
            incident.getTitle() + " - SLA extension approved for " + extension.getAdditionalHoursRequested() + " hours");

        // Notify the assigned user about the new deadline
        if (incident.getAssignedTo() != null) {
            notificationService.notifySlaExtension(incident.getAssignedTo(), extension.getIncidentId(),
                incident.getTitle() + " - SLA extended to " + newDueDate);
        }

        log.info("SLA extension {} approved by user {}", extensionId, currentUserId);
        return saved;
    }

    /**
     * Rejects an SLA extension request.
     */
    @Transactional
    public SlaExtensionRequest rejectExtension(Long extensionId, String rejectionReason) {
        Long currentUserId = authService.getCurrentUserId();
        
        SlaExtensionRequest extension = extensionRepository.findById(extensionId)
            .orElseThrow(() -> new RuntimeException("Extension request not found: " + extensionId));

        if (extension.getStatus() != SlaExtensionRequest.ExtensionStatus.PENDING) {
            throw new RuntimeException("Extension request is not pending");
        }

        extension.setStatus(SlaExtensionRequest.ExtensionStatus.REJECTED);
        extension.setApprovedBy(currentUserId);
        extension.setApprovalReason(rejectionReason);
        extension.setReviewedAt(LocalDateTime.now());

        SlaExtensionRequest saved = extensionRepository.save(extension);

        timelineService.logEvent(extension.getIncidentId(), "SLA_EXTENSION_REJECTED",
            String.format("SLA extension rejected. Reason: %s", rejectionReason),
            currentUserId);
        auditService.logAction("SLA_EXTENSION_REJECT", currentUserId, "Incident", extension.getIncidentId(),
            String.format("Extension rejected. Reason: %s", rejectionReason));

        // Notify the requester about rejection
        Incident incident = incidentRepository.findById(extension.getIncidentId())
            .orElseThrow(() -> new RuntimeException("Incident not found: " + extension.getIncidentId()));
        notificationService.notifySlaExtension(extension.getRequestedBy(), extension.getIncidentId(),
            incident.getTitle() + " - SLA extension rejected. Reason: " + rejectionReason);

        log.info("SLA extension {} rejected by user {}", extensionId, currentUserId);
        return saved;
    }

    /**
     * Gets all extension requests for an incident.
     */
    public List<SlaExtensionRequest> getExtensionsForIncident(Long incidentId) {
        return extensionRepository.findByIncidentId(incidentId);
    }

    /**
     * Gets all pending extension requests.
     */
    public List<SlaExtensionRequest> getPendingExtensions() {
        return extensionRepository.findByStatus(SlaExtensionRequest.ExtensionStatus.PENDING);
    }
}
