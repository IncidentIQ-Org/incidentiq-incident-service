package com.incidentiq.service;

import com.incidentiq.model.IncidentTimeline;

import java.util.List;

/**
 * Service interface for managing incident timeline events.
 */
public interface TimelineService {

    /**
     * Logs a timeline event for an incident.
     *
     * @param incidentId the incident ID
     * @param eventType the type of event (CREATED, STATUS_CHANGED, ASSIGNED, etc.)
     * @param description human-readable description
     * @param performedBy the user ID who performed the action
     */
    void logEvent(Long incidentId, String eventType, String description, Long performedBy);

    /**
     * Retrieves all timeline events for an incident.
     *
     * @param incidentId the incident ID
     * @return list of timeline events in chronological order
     */
    List<IncidentTimeline> getTimeline(Long incidentId);
}
