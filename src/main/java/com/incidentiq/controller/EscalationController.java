package com.incidentiq.controller;

import com.incidentiq.dto.request.EscalationRequest;
import com.incidentiq.model.Incident;
import com.incidentiq.security.AuthorizationService;
import com.incidentiq.service.EscalationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for the Incident Escalation Engine.
 * Supports manual escalation by managers/admins.
 */
@RestController
@RequestMapping("/escalation")
@RequiredArgsConstructor
@Tag(name = "Escalation Engine", description = "Manual escalation control for incidents")
public class EscalationController {

    private final EscalationService escalationService;
    private final AuthorizationService authorizationService;

    @Operation(summary = "Manually escalate an incident",
               description = "MANAGER or ADMIN can manually escalate any incident to the next escalation level.")
    @PostMapping("/escalate")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<Incident> escalate(@Valid @RequestBody EscalationRequest request) {
        Long userId = authorizationService.getCurrentUserId();
        Incident escalated = escalationService.manualEscalate(request.getIncidentId(), userId, request.getReason());
        return ResponseEntity.ok(escalated);
    }
}
