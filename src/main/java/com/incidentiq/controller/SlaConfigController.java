package com.incidentiq.controller;

import com.incidentiq.dto.SlaConfigDto;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.model.SlaConfig;
import com.incidentiq.repository.SlaConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sla-configs")
@Tag(name = "SLA Configuration", description = "Endpoints for managing dynamic SLAs")
@RequiredArgsConstructor
public class SlaConfigController {

    private final SlaConfigRepository slaConfigRepository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all SLA configurations")
    public ResponseEntity<List<SlaConfigDto>> getAllSlaConfigs() {
        List<SlaConfigDto> configs = slaConfigRepository.findAll().stream()
                .map(config -> SlaConfigDto.builder()
                        .id(config.getId())
                        .priority(config.getPriority())
                        .targetHours(config.getTargetHours())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(configs);
    }

    @PutMapping("/{priority}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update SLA configuration for a priority")
    public ResponseEntity<SlaConfigDto> updateSlaConfig(
            @PathVariable IncidentPriority priority,
            @RequestBody SlaConfigDto request) {
        
        SlaConfig config = slaConfigRepository.findByPriority(priority)
                .orElseGet(() -> SlaConfig.builder().priority(priority).build());
        
        config.setTargetHours(request.getTargetHours());
        SlaConfig saved = slaConfigRepository.save(config);
        
        return ResponseEntity.ok(SlaConfigDto.builder()
                .id(saved.getId())
                .priority(saved.getPriority())
                .targetHours(saved.getTargetHours())
                .build());
    }
}
