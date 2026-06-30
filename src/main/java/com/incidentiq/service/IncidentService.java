package com.incidentiq.service;

import com.incidentiq.dto.ResolutionRequest;
import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.request.UpdateIncidentRequest;
import com.incidentiq.dto.response.IncidentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for incident management operations.
 * Defines the contract for business logic, decoupled from implementation.
 */
public interface IncidentService {

    /**
     * Creates a new incident with OPEN status.
     *
     * @param request the creation payload
     * @return the created incident response
     */
    IncidentResponse createIncident(CreateIncidentRequest request);

    /**
     * Retrieves an incident by its ID.
     *
     * @param id the incident ID
     * @return the incident response
     * @throws com.incidentiq.exception.IncidentNotFoundException if not found
     */
    IncidentResponse getIncidentById(Long id);

    /**
     * Retrieves all incidents with pagination and sorting.
     *
     * @param pageable pagination parameters
     * @return a page of incident responses
     */
    Page<IncidentResponse> getAllIncidents(@org.springframework.lang.NonNull Pageable pageable);

    /**
     * Updates an existing incident. Only non-null fields in the request are applied.
     * Validates status transitions against lifecycle rules.
     *
     * @param id      the incident ID
     * @param request the update payload
     * @return the updated incident response
     * @throws com.incidentiq.exception.IncidentNotFoundException         if not found
     * @throws com.incidentiq.exception.InvalidStatusTransitionException  if status change is invalid
     */
    IncidentResponse updateIncident(Long id, UpdateIncidentRequest request);

    /**
     * Deletes an incident by ID.
     *
     * @param id the incident ID
     * @throws com.incidentiq.exception.IncidentNotFoundException if not found
     */
    void deleteIncident(Long id);

    /**
     * Retrieves statistics summary for incidents.
     */
    com.incidentiq.dto.response.IncidentStatsResponse getIncidentStats();

    /**
     * Retrieves incidents reported by a specific user.
     */
    Page<IncidentResponse> getIncidentsByReporter(Long reporterId, Pageable pageable);

    /**
     * Retrieves incidents created by the currently authenticated user.
     */
    Page<IncidentResponse> getMyIncidents(Pageable pageable);

    Page<IncidentResponse> getKnowledgeBase(Pageable pageable);

    /**
     * Retrieves incidents assigned to a specific user.
     */
    Page<IncidentResponse> getIncidentsByAssignee(Long assigneeId, Pageable pageable);

    /**
     * Resolves an incident with detailed resolution information.
     *
     * @param id the incident ID
     * @param resolutionRequest the resolution details
     * @return the updated incident response
     */
    IncidentResponse resolveIncidentWithDetails(Long id, ResolutionRequest resolutionRequest);

    /**
     * Retrieves all incidents assigned to the currently authenticated user.
     */
    java.util.List<IncidentResponse> getMyAssignedIncidents();

    /**
     * Retrieves all incidents as a flat list for CSV export (no pagination).
     */
    java.util.List<IncidentResponse> getAllIncidentsForExport();

    /**
     * Changes the status of an incident through the lifecycle.
     * Only the assigned technician, manager, or admin may call this.
     * Validates the transition against IncidentStatus rules and broadcasts
     * the change via WebSocket to all viewers of the incident.
     */
    IncidentResponse changeStatus(Long id, com.incidentiq.enums.IncidentStatus newStatus);
}
