package com.incidentiq.service;

import com.incidentiq.enums.EscalationLevel;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Incident Escalation Engine
 *
 * Runs on a schedule and automatically escalates incidents based on policies:
 *
 * Policy 1 — Unacknowledged CRITICAL:
 *   If a CRITICAL incident remains OPEN (unacknowledged) for >10 minutes → escalate to MANAGER
 *
 * Policy 2 — SLA Breach → MANAGER not resolved after grace → ADMIN:
 *   If any incident breaches its SLA due date AND is not CLOSED/RESOLVED → escalate to MANAGER
 *   If already at MANAGER level and still unresolved after 30 min grace → escalate to ADMIN
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationService {

    private final IncidentRepository incidentRepository;
    private final TimelineService timelineService;
    private final AuditService auditService;

    /** Policy 1: Runs every 5 minutes — escalate unacknowledged CRITICAL incidents */
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
                String.format("AUTO-ESCALATED to MANAGER: CRITICAL incident unacknowledged for >10 minutes. Escalation #%d.", incident.getEscalationCount()),
                null
            );
            auditService.logAction("AUTO_ESCALATE", null, "Incident", incident.getId(),
                "CRITICAL unacknowledged escalation to MANAGER");

            log.warn("Escalated CRITICAL incident #{} to MANAGER (unacknowledged).", incident.getId());
        }
    }

    /** Policy 2a: Runs every 10 minutes — escalate SLA-breached incidents to MANAGER */
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
                    null
                );
                auditService.logAction("AUTO_ESCALATE", null, "Incident", incident.getId(),
                    "SLA breach escalation to MANAGER");

                log.warn("Escalated incident #{} to MANAGER due to SLA breach.", incident.getId());
            }
        }
    }

    /** Policy 2b: Runs every 15 minutes — escalate MANAGER-level incidents to ADMIN after grace period */
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
                String.format("AUTO-ESCALATED to ADMIN: Unresolved after MANAGER escalation (30-min grace expired). Escalation #%d.",
                    incident.getEscalationCount()),
                null
            );
            auditService.logAction("AUTO_ESCALATE", null, "Incident", incident.getId(),
                "Grace period expired — escalation from MANAGER to ADMIN");

            log.error("Incident #{} escalated to ADMIN level — requires immediate executive attention.", incident.getId());
        }
    }

    /**
     * Manually escalate an incident.
     * @param incidentId the incident to escalate
     * @param performedBy the user triggering the escalation
     * @param reason the reason for manual escalation
     */
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
            String.format("MANUAL ESCALATION to %s. Reason: %s. Escalation #%d.", next, reason, incident.getEscalationCount()),
            performedBy
        );
        auditService.logAction("MANUAL_ESCALATE", performedBy, "Incident", incidentId,
            "Manual escalation to " + next + " — " + reason);

        return incident;
    }

    private EscalationLevel nextLevel(EscalationLevel current) {
        return switch (current) {
            case NONE -> EscalationLevel.MANAGER;
            case MANAGER -> EscalationLevel.ADMIN;
            case ADMIN -> EscalationLevel.ADMIN; // Already at max
        };
    }
}
