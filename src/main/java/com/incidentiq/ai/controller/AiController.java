package com.incidentiq.ai.controller;

import com.incidentiq.ai.cache.AiResponseCache;
import com.incidentiq.ai.config.AiProperties;
import com.incidentiq.ai.dto.AiRequests;
import com.incidentiq.ai.dto.AiResponses;
import com.incidentiq.ai.health.ModelHealthRegistry;
import com.incidentiq.ai.service.CopilotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST surface for the IncidentIQ AI Copilot.
 *
 * Mounted at {@code /ai} and reached through the gateway at {@code /api/ai/**}. Every endpoint
 * requires authentication (see SecurityConfig); manager/admin-only features are annotated.
 *
 * These endpoints are intentionally task-scoped (incident creation, resolution, coaching, KB,
 * manager insights, scoped chat) — this is an embedded copilot, not a general chatbot.
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI Copilot", description = "IncidentIQ AI Copilot — OpenRouter-backed, with model fallback and heuristic degradation")
public class AiController {

    private final CopilotService copilot;
    private final ModelHealthRegistry health;
    private final AiResponseCache cache;
    private final AiProperties props;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Copilot status", description = "Whether AI is enabled and configured (UI uses this to badge availability)")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", props.isEnabled());
        body.put("configured", copilot.aiAvailable());
        body.put("models", props.getModels());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/incident-assist")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Incident creation assistant", description = "Improves title/description and suggests category, priority and tags")
    public ResponseEntity<AiResponses.IncidentAssist> incidentAssist(@RequestBody AiRequests.IncidentAssist req) {
        return ResponseEntity.ok(copilot.assistIncident(req));
    }

    @PostMapping("/resolution-assist")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Resolution assistant", description = "Drafts root cause, resolution steps and summary for an incident")
    public ResponseEntity<AiResponses.ResolutionAssist> resolutionAssist(@RequestBody AiRequests.ResolutionAssist req) {
        return ResponseEntity.ok(copilot.assistResolution(req));
    }

    @GetMapping("/coaching/{incidentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "AI coaching", description = "Troubleshooting steps, next actions and common fixes grounded in similar incidents")
    public ResponseEntity<AiResponses.Coaching> coaching(@PathVariable Long incidentId) {
        return ResponseEntity.ok(copilot.coach(incidentId));
    }

    @PostMapping("/kb-article/{incidentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Generate knowledge-base article", description = "Creates a reusable KB article from a resolved incident")
    public ResponseEntity<AiResponses.KbArticle> kbArticle(@PathVariable Long incidentId) {
        return ResponseEntity.ok(copilot.generateKbArticle(incidentId));
    }

    @PostMapping("/similar-summary")
    @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Summarize similar resolutions", description = "Summarizes how historically similar incidents were resolved")
    public ResponseEntity<AiResponses.SimilarSummary> similarSummary(@RequestBody AiRequests.SimilarSummary req) {
        return ResponseEntity.ok(copilot.summarizeSimilar(req));
    }

    @GetMapping("/manager-insights")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Manager insights", description = "SLA risks, category hotspots, workload and what needs attention — manager/admin only")
    public ResponseEntity<AiResponses.ManagerInsights> managerInsights(
            @RequestParam(required = false) String question) {
        return ResponseEntity.ok(copilot.managerInsights(question));
    }

    @PostMapping("/ask")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Scoped Copilot chat", description = "Context-aware Q&A constrained to IncidentIQ workflows")
    public ResponseEntity<AiResponses.Ask> ask(@RequestBody AiRequests.Ask req) {
        return ResponseEntity.ok(copilot.ask(req));
    }

    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Model health", description = "Per-model health, failure counts and cache stats — admin only")
    public ResponseEntity<Map<String, Object>> healthStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configured", copilot.aiAvailable());
        body.put("models", health.snapshot());
        body.put("cache", cache.stats());
        return ResponseEntity.ok(body);
    }
}
