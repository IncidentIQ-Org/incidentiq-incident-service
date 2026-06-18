package com.incidentiq.service;

import java.util.List;

/**
 * Service interface for notification management.
 */
public interface NotificationService {

    /**
     * Sends a notification for incident assignment.
     *
     * @param userId the user to notify
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     */
    void notifyAssignment(Long userId, Long incidentId, String incidentTitle);

    /**
     * Sends a notification for incident reassignment.
     *
     * @param fromUserId the previous assignee
     * @param toUserId the new assignee
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     */
    void notifyReassignment(Long fromUserId, Long toUserId, Long incidentId, String incidentTitle);

    /**
     * Sends a notification for status change.
     *
     * @param userId the user to notify
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     * @param oldStatus the old status
     * @param newStatus the new status
     */
    void notifyStatusChange(Long userId, Long incidentId, String incidentTitle, String oldStatus, String newStatus);

    /**
     * Sends a notification for SLA breach.
     *
     * @param userId the user to notify
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     */
    void notifySlaBreach(Long userId, Long incidentId, String incidentTitle);

    /**
     * Sends a notification for escalation.
     *
     * @param userId the user to notify
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     * @param escalationLevel the escalation level
     */
    void notifyEscalation(Long userId, Long incidentId, String incidentTitle, String escalationLevel);

    /**
     * Sends a notification for incident creation.
     *
     * @param userId the user to notify
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     * @param details the incident details
     */
    void notifyCreation(Long userId, Long incidentId, String incidentTitle, String details);

    /**
     * Sends a notification for new user registration.
     *
     * @param userId the user to notify (admin)
     * @param newUserId the newly registered user ID
     * @param username the new username
     * @param role the role of the new user
     */
    void notifyUserRegistration(Long userId, Long newUserId, String username, String role);

    /**
     * Retrieves notifications for a user.
     *
     * @param userId the user ID
     * @return list of notifications
     */
    List<Object> getNotifications(Long userId);

    /**
     * Marks a notification as read.
     *
     * @param notificationId the notification ID
     */
    void markAsRead(Long notificationId);

    /**
     * Marks all notifications as read for a user.
     *
     * @param userId the user ID
     */
    void markAllAsRead(Long userId);

    /**
     * Deletes a notification.
     *
     * @param notificationId the notification ID
     */
    void deleteNotification(Long notificationId);

    /**
     * Notifies all admins of an event.
     *
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     * @param message the notification message
     */
    void notifyAdmins(Long incidentId, String incidentTitle, String message);

    /**
     * Notifies all managers of an event.
     *
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     * @param message the notification message
     */
    void notifyManagers(Long incidentId, String incidentTitle, String message);

    /**
     * Notifies SLA extension request.
     *
     * @param userId the user to notify
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     */
    void notifySlaExtension(Long userId, Long incidentId, String incidentTitle);

    /**
     * Sends a pre-breach SLA warning when 75% of the SLA window has elapsed.
     *
     * @param userId the user to warn
     * @param incidentId the incident ID
     * @param incidentTitle the incident title
     * @param percentElapsed percentage of SLA time elapsed (e.g. 75)
     */
    void notifySlaWarning(Long userId, Long incidentId, String incidentTitle, int percentElapsed);
}
