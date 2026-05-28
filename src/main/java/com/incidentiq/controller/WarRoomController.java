package com.incidentiq.controller;

import com.incidentiq.dto.request.WarRoomRequest;
import com.incidentiq.model.WarRoom;
import com.incidentiq.security.AuthorizationService;
import com.incidentiq.service.WarRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * War Room Controller — Major Incident Management.
 *
 * Inspired by PagerDuty, Opsgenie, and ServiceNow Major Incident Management.
 */
@RestController
@RequestMapping("/war-room")
@RequiredArgsConstructor
@Tag(name = "War Room", description = "Major incident war room management — coordinated response for critical outages")
public class WarRoomController {

    private final WarRoomService warRoomService;
    private final AuthorizationService authorizationService;

    @Operation(summary = "Create a War Room session",
               description = "MANAGER or ADMIN creates a war room to coordinate response for major incidents.")
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<WarRoom> createWarRoom(@Valid @RequestBody WarRoomRequest request) {
        Long userId = authorizationService.getCurrentUserId();
        return ResponseEntity.ok(warRoomService.createWarRoom(request, userId));
    }

    @Operation(summary = "List all active War Rooms")
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<WarRoom>> getActiveWarRooms() {
        return ResponseEntity.ok(warRoomService.getActiveWarRooms());
    }

    @Operation(summary = "Get all War Rooms (history)")
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<WarRoom>> getAllWarRooms() {
        return ResponseEntity.ok(warRoomService.getAllWarRooms());
    }

    @Operation(summary = "Get War Room by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<WarRoom> getWarRoomById(@PathVariable Long id) {
        return ResponseEntity.ok(warRoomService.getWarRoomById(id));
    }

    @Operation(summary = "Resolve a War Room",
               description = "Closes the War Room and records a resolution summary.")
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<WarRoom> resolveWarRoom(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long userId = authorizationService.getCurrentUserId();
        return ResponseEntity.ok(
            warRoomService.resolveWarRoom(id, body.getOrDefault("summary", ""), userId)
        );
    }
}
