package com.incidentiq.service.impl;

import com.incidentiq.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Delegates all notification operations to the dedicated notification-service
 * via internal REST calls over Eureka service discovery.
 * All fire-and-forget notify* methods are @Async — callers are never blocked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final RestTemplate restTemplate;

    private static final String NOTIF_BASE = "http://notification-service/internal/create";

    private void send(Long userId, String type, String title, String message, Long incidentId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("type", type);
            payload.put("title", title);
            payload.put("message", message);
            payload.put("incidentId", incidentId);
            restTemplate.postForObject(NOTIF_BASE, payload, Object.class);
        } catch (Exception e) {
            log.warn("Failed to send notification to user {}: {}", userId, e.getMessage());
        }
    }

    @lombok.Data
    private static class ExternalUserResponse {
        private Long id;
        private String role;
    }

    @Async
    @Override
    public void notifyAssignment(Long userId, Long incidentId, String incidentTitle) {
        send(userId, "assignment",
                "Incident Assigned — INC-" + incidentId,
                "You have been assigned to incident: " + incidentTitle,
                incidentId);
    }

    @Async
    @Override
    public void notifyReassignment(Long fromUserId, Long toUserId, Long incidentId, String incidentTitle) {
        send(fromUserId, "warning",
                "Incident Transferred — INC-" + incidentId,
                "Incident has been reassigned from you: " + incidentTitle,
                incidentId);
        send(toUserId, "assignment",
                "Incident Transferred to You — INC-" + incidentId,
                "Incident has been reassigned to you: " + incidentTitle,
                incidentId);
    }

    @Async
    @Override
    public void notifyStatusChange(Long userId, Long incidentId, String incidentTitle, String oldStatus, String newStatus) {
        send(userId, "success",
                "Status Updated — INC-" + incidentId,
                String.format("Incident '%s' status changed: %s → %s", incidentTitle, oldStatus, newStatus),
                incidentId);
    }

    @Async
    @Override
    public void notifySlaBreach(Long userId, Long incidentId, String incidentTitle) {
        send(userId, "critical",
                "SLA Breach — INC-" + incidentId,
                "URGENT: SLA breach for incident: " + incidentTitle,
                incidentId);
    }

    @Async
    @Override
    public void notifyEscalation(Long userId, Long incidentId, String incidentTitle, String escalationLevel) {
        send(userId, "critical",
                "Escalation — INC-" + incidentId,
                "Incident escalated to " + escalationLevel + ": " + incidentTitle,
                incidentId);
    }

    @Async
    @Override
    public void notifyCreation(Long userId, Long incidentId, String incidentTitle, String details) {
        send(userId, "assignment",
                "New Incident Created — INC-" + incidentId,
                "New incident created: " + incidentTitle + ". " + details,
                incidentId);
    }

    @Async
    @Override
    public void notifyUserRegistration(Long userId, Long newUserId, String username, String role) {
        send(userId, "success",
                "New User Registered — " + username,
                "New user registered: " + username + " with role: " + role,
                null);
    }

    @Async
    @Override
    public void notifyAdmins(Long incidentId, String incidentTitle, String message) {
        try {
            ExternalUserResponse[] users = restTemplate.getForObject(
                    "http://user-service/all", ExternalUserResponse[].class);
            if (users != null) {
                for (ExternalUserResponse u : users) {
                    if ("ROLE_ADMIN".equals(u.getRole())) {
                        send(u.getId(), "info",
                                "[Admin] INC-" + incidentId + " — " + incidentTitle, message, incidentId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify admins: {}", e.getMessage());
        }
    }

    @Async
    @Override
    public void notifyManagers(Long incidentId, String incidentTitle, String message) {
        try {
            ExternalUserResponse[] users = restTemplate.getForObject(
                    "http://user-service/all", ExternalUserResponse[].class);
            if (users != null) {
                for (ExternalUserResponse u : users) {
                    if ("ROLE_MANAGER".equals(u.getRole())) {
                        send(u.getId(), "info",
                                "[Manager] INC-" + incidentId + " — " + incidentTitle, message, incidentId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify managers: {}", e.getMessage());
        }
    }

    @Async
    @Override
    public void notifySlaExtension(Long userId, Long incidentId, String incidentTitle) {
        send(userId, "warning",
                "SLA Extension — INC-" + incidentId,
                incidentTitle,
                incidentId);
    }

    @Async
    @Override
    public void notifySlaWarning(Long userId, Long incidentId, String incidentTitle, int percentElapsed) {
        send(userId, "sla",
                "SLA Warning (" + percentElapsed + "%) — INC-" + incidentId,
                String.format("Incident '%s' has used %d%% of its SLA window. Act now to avoid a breach.", incidentTitle, percentElapsed),
                incidentId);
    }

    // --- Sync methods: callers wait for the result ---

    @Override
    public List<Object> getNotifications(Long userId) {
        try {
            Object[] results = restTemplate.getForObject(
                    "http://notification-service/user/" + userId, Object[].class);
            return results != null ? List.of(results) : List.of();
        } catch (Exception e) {
            log.warn("Failed to fetch notifications for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void markAsRead(Long notificationId) {
        try {
            restTemplate.put("http://notification-service/" + notificationId + "/read", null);
        } catch (Exception e) {
            log.warn("Failed to mark notification {} as read: {}", notificationId, e.getMessage());
        }
    }

    @Override
    public void markAllAsRead(Long userId) {
        try {
            restTemplate.put("http://notification-service/user/" + userId + "/read-all", null);
        } catch (Exception e) {
            log.warn("Failed to mark all notifications as read for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void deleteNotification(Long notificationId) {
        try {
            restTemplate.delete("http://notification-service/" + notificationId);
        } catch (Exception e) {
            log.warn("Failed to delete notification {}: {}", notificationId, e.getMessage());
        }
    }
}
