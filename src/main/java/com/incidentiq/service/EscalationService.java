package com.incidentiq.service;

import com.incidentiq.model.Incident;

/**
 * Service interface for incident escalation operations.
 */
public interface EscalationService {

    /**
     * Escalates unacknowledged CRITICAL incidents to MANAGER.
     * Runs on a schedule.
     */
    void escalateCriticalUnacknowledged();

    /**
     * Escalates SLA-breached incidents to MANAGER.
     * Runs on a schedule.
     */
    void escalateSlaBreached();

    /**
     * Escalates MANAGER-level incidents to ADMIN after grace period.
     * Runs on a schedule.
     */
    void escalateToAdmin();

    /**
     * Manually escalates an incident to the next level.
     *
     * @param incidentId the incident to escalate
     * @param performedBy the user triggering the escalation
     * @param reason the reason for manual escalation
     * @return the escalated incident
     * @throws RuntimeException if incident not found
     */
    Incident manualEscalate(Long incidentId, Long performedBy, String reason);
}
