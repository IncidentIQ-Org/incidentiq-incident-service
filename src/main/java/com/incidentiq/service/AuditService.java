package com.incidentiq.service;

import com.incidentiq.model.AuditLog;

import java.util.List;

/**
 * Service interface for audit logging operations.
 */
public interface AuditService {

    /**
     * Logs an audit action to the database.
     *
     * @param action the action performed (e.g., "CREATE_INCIDENT", "UPDATE_STATUS")
     * @param userId the ID of the user who performed the action
     * @param entityName the name of the entity affected (e.g., "Incident", "User")
     * @param entityId the ID of the entity affected
     * @param details additional details about the action
     */
    void logAction(String action, Long userId, String entityName, Long entityId, String details);

    /**
     * Retrieves all audit logs.
     *
     * @return list of all audit logs
     */
    List<AuditLog> getAuditLogs();
}
