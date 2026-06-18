package com.incidentiq.controller;

import com.incidentiq.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final RestTemplate restTemplate;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Object>> getNotifications(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<Void> markAllAsRead(@PathVariable Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user-registration")
    public ResponseEntity<Void> notifyUserRegistration(@RequestBody Map<String, Object> payload) {
        Long newUserId = ((Number) payload.get("newUserId")).longValue();
        String username = (String) payload.get("username");
        String role = (String) payload.get("role");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object>[] users = restTemplate.getForObject("http://user-service/all", Map[].class);
            if (users != null) {
                for (Map<String, Object> user : users) {
                    if ("ROLE_ADMIN".equals(user.get("role"))) {
                        notificationService.notifyUserRegistration(
                            ((Number) user.get("id")).longValue(), newUserId, username, role);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to notify admins of user registration: " + e.getMessage());
        }
        return ResponseEntity.ok().build();
    }
}
