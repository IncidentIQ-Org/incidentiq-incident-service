package com.incidentiq.service.impl;

import com.incidentiq.enums.EscalationLevel;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.service.AuditService;
import com.incidentiq.service.EscalationService;
import com.incidentiq.service.TimelineService;
import com.incidentiq.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationServiceImpl implements EscalationService {

    private final IncidentRepository incidentRepository;
    private final TimelineService timelineService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate;

    private static final String USER_SERVICE_ALL = "http://user-service/all";

    @lombok.Data
    private static class ExternalUserResponse {
        private Long id;
        private String role;
    }

    @Override
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void escalateCriticalUnacknowledged() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);
        List<Incident> incidents = incidentRepository.findCriticalUnacknowledged(threshold);

        for (Incident incident : incidents) {
            incident.setEscalationLevel(EscalationLevel.MANAGER);
            incident.setEscalatedAt(LocalDateTime.now());
            incident.setEscalationCount(incident.getEscalationCount() + 1);
            incident.setStatus(IncidentStatus.ESCALATED);
            incidentRepository.save(incident);

            timelineService.logEvent(
                    incident.getId(),
                    "ESCALATED",
                    String.format(
                            "AUTO-ESCALATED to MANAGER: CRITICAL incident unacknowledged for >10 minutes. Escalation #%d.",
                            incident.getEscalationCount()),
                    null);
            auditService.logAction("AUTO_ESCALATE", null, "Incident", incident.getId(),
                    "CRITICAL unacknowledged escalation to MANAGER");

            log.warn("Escalated CRITICAL incident #{} to MANAGER (unacknowledged).", incident.getId());
            notifyAssigneeOfEscalation(incident);
            notifyManagersAndAdmins(incident.getId(), incident.getTitle(), "MANAGER");
        }
    }

    @Override
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void escalateSlaBreached() {
        List<Incident> breached = incidentRepository.findSlaBreachedNotFullyEscalated(LocalDateTime.now());

        for (Incident incident : breached) {
            if (incident.getEscalationLevel() == EscalationLevel.NONE) {
                incident.setEscalationLevel(EscalationLevel.MANAGER);
                incident.setEscalatedAt(LocalDateTime.now());
                incident.setEscalationCount(incident.getEscalationCount() + 1);
                incident.setStatus(IncidentStatus.ESCALATED);
                incidentRepository.save(incident);

                timelineService.logEvent(
                        incident.getId(),
                        "ESCALATED",
                        String.format("AUTO-ESCALATED to MANAGER: SLA breached. Due was %s. Escalation #%d.",
                                incident.getDueDate(), incident.getEscalationCount()),
                        null);
                auditService.logAction("AUTO_ESCALATE", null, "Incident", incident.getId(),
                        "SLA breach escalation to MANAGER");

                log.warn("Escalated incident #{} to MANAGER due to SLA breach.", incident.getId());
                notifyAssigneeOfEscalation(incident);
                notifyManagersAndAdmins(incident.getId(), incident.getTitle(), "MANAGER");

                // Notify assigned user about SLA breach
                if (incident.getAssignedTo() != null) {
                    notificationService.notifySlaBreach(incident.getAssignedTo(), incident.getId(), incident.getTitle());
                }
            }
        }
    }

    @Override
    @Scheduled(fixedRate = 900_000)
    @Transactional
    public void escalateToAdmin() {
        LocalDateTime graceThreshold = LocalDateTime.now().minusMinutes(30);
        List<Incident> incidents = incidentRepository.findManagerEscalatedPastGrace(graceThreshold);

        for (Incident incident : incidents) {
            incident.setEscalationLevel(EscalationLevel.ADMIN);
            incident.setEscalatedAt(LocalDateTime.now());
            incident.setEscalationCount(incident.getEscalationCount() + 1);
            incidentRepository.save(incident);

            timelineService.logEvent(
                    incident.getId(),
                    "ESCALATED",
                    String.format(
                            "AUTO-ESCALATED to ADMIN: Unresolved after MANAGER escalation (30-min grace expired). Escalation #%d.",
                            incident.getEscalationCount()),
                    null);
            auditService.logAction("AUTO_ESCALATE", null, "Incident", incident.getId(),
                    "Grace period expired — escalation from MANAGER to ADMIN");

            log.error("Incident #{} escalated to ADMIN level — requires immediate executive attention.",
                    incident.getId());
            notifyAssigneeOfEscalation(incident);
            notifyManagersAndAdmins(incident.getId(), incident.getTitle(), "ADMIN");
        }
    }

    @Override
    @Transactional
    public Incident manualEscalate(Long incidentId, Long performedBy, String reason) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found: " + incidentId));

        EscalationLevel next = nextLevel(incident.getEscalationLevel());
        incident.setEscalationLevel(next);
        incident.setEscalatedAt(LocalDateTime.now());
        incident.setEscalationCount(incident.getEscalationCount() + 1);
        if (incident.getStatus() != IncidentStatus.ESCALATED) {
            incident.setStatus(IncidentStatus.ESCALATED);
        }

        incidentRepository.save(incident);

        timelineService.logEvent(
                incidentId, "ESCALATED",
                String.format("MANUAL ESCALATION to %s. Reason: %s. Escalation #%d.", next, reason,
                        incident.getEscalationCount()),
                performedBy);
        auditService.logAction("MANUAL_ESCALATE", performedBy, "Incident", incidentId,
                "Manual escalation to " + next + " — " + reason);

        notifyAssigneeOfEscalation(incident);
        notifyManagersAndAdmins(incident.getId(), incident.getTitle(), next.name());

        return incident;
    }

    private void notifyManagersAndAdmins(Long incidentId, String title, String escalationLevel) {
        try {
            String url = USER_SERVICE_ALL;
            ExternalUserResponse[] users = restTemplate.getForObject(url, ExternalUserResponse[].class);
            if (users != null) {
                for (ExternalUserResponse u : users) {
                    if ("ROLE_MANAGER".equals(u.getRole()) || "ROLE_ADMIN".equals(u.getRole())) {
                        notificationService.notifyEscalation(u.getId(), incidentId, title, escalationLevel);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify managers/admins of escalation: {}", e.getMessage());
        }
    }

    private void notifyAssigneeOfEscalation(Incident incident) {
        if (incident.getAssignedTo() != null) {
            try {
                notificationService.notifyEscalation(
                        incident.getAssignedTo(),
                        incident.getId(),
                        incident.getTitle(),
                        incident.getEscalationLevel().name());
            } catch (Exception e) {
                log.error("Failed to notify assignee of escalation: {}", e.getMessage());
            }
        }
    }

    private EscalationLevel nextLevel(EscalationLevel current) {
        return switch (current) {
            case NONE -> EscalationLevel.MANAGER;
            case MANAGER -> EscalationLevel.ADMIN;
            case ADMIN -> EscalationLevel.ADMIN;
        };
    }
}
