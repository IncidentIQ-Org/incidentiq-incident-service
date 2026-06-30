package com.incidentiq.controller;

import com.incidentiq.dto.SlaConfigDto;
import com.incidentiq.enums.Complexity;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.model.SlaConfig;
import com.incidentiq.repository.SlaConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional(readOnly = true)
    @Operation(summary = "Get the full Priority × Complexity SLA matrix")
    public ResponseEntity<List<SlaConfigDto>> getAllSlaConfigs() {
        List<SlaConfigDto> configs = slaConfigRepository.findAll().stream()
                .map(SlaConfigController::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(configs);
    }

    @PutMapping("/{priority}/{complexity}")
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = {"sla-configs", "sla-config-by-priority", "sla-config-by-pc"}, allEntries = true)
    @Operation(summary = "Update the SLA target for a (priority, complexity) cell")
    public ResponseEntity<SlaConfigDto> updateSlaConfig(
            @PathVariable IncidentPriority priority,
            @PathVariable Complexity complexity,
            @RequestBody SlaConfigDto request) {

        SlaConfig config = slaConfigRepository.findByPriorityAndComplexity(priority, complexity)
                .orElseGet(() -> SlaConfig.builder().priority(priority).complexity(complexity).build());

        config.setTargetHours(request.getTargetHours());
        SlaConfig saved = slaConfigRepository.save(config);

        return ResponseEntity.ok(toDto(saved));
    }

    private static SlaConfigDto toDto(SlaConfig config) {
        return SlaConfigDto.builder()
                .id(config.getId())
                .priority(config.getPriority())
                .complexity(config.getComplexity())
                .targetHours(config.getTargetHours())
                .build();
    }
}
