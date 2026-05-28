package com.incidentiq.controller;

import java.util.List;
import com.incidentiq.constants.IncidentConstants;
import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.request.UpdateIncidentRequest;
import com.incidentiq.dto.response.ApiErrorResponse;
import com.incidentiq.dto.response.IncidentResponse;
import com.incidentiq.dto.response.IncidentStatsResponse;
import com.incidentiq.dto.response.SimilarIncidentResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.service.IncidentService;
import com.incidentiq.service.SimilarityDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for incident management.
 * Provides CRUD endpoints with pagination, sorting, and validation.
 */
@RestController
@RequestMapping("/")
@Tag(name = "Incidents", description = "Incident lifecycle management APIs")
public class IncidentController {

        private final IncidentService incidentService;
        private final com.incidentiq.service.TimelineService timelineService;
        private final com.incidentiq.service.AuditService auditService;
        private final com.incidentiq.service.SeveritySuggestionService severitySuggestionService;
        private final com.incidentiq.service.DuplicateDetectionService duplicateDetectionService;
        private final SimilarityDetectionService similarityDetectionService;

        public IncidentController(IncidentService incidentService,
                               com.incidentiq.service.TimelineService timelineService,
                               com.incidentiq.service.AuditService auditService,
                               com.incidentiq.service.SeveritySuggestionService severitySuggestionService,
                               com.incidentiq.service.DuplicateDetectionService duplicateDetectionService,
                               SimilarityDetectionService similarityDetectionService) {
                this.incidentService = incidentService;
                this.timelineService = timelineService;
                this.auditService = auditService;
                this.severitySuggestionService = severitySuggestionService;
                this.duplicateDetectionService = duplicateDetectionService;
                this.similarityDetectionService = similarityDetectionService;
        }

        /**
         * Creates a new incident.
         */
        @PostMapping("/create")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
        @Operation(summary = "Create a new incident", description = "Creates a new incident with OPEN status")
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Incident created successfully"),
                        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        public ResponseEntity<IncidentResponse> createIncident(
                        @Valid @RequestBody CreateIncidentRequest request) {

                IncidentResponse response = incidentService.createIncident(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        /**
         * Retrieves an incident by ID.
         */
        @GetMapping("/view/{id}")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or @authService.isOwner(#id)")
        @Operation(summary = "Get incident by ID", description = "Retrieves a single incident by its unique ID")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Incident found"),
                        @ApiResponse(responseCode = "404", description = "Incident not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        public ResponseEntity<IncidentResponse> getIncidentById(
                        @PathVariable Long id) {

                IncidentResponse response = incidentService.getIncidentById(id);
                return ResponseEntity.ok(response);
        }

        /**
         * Retrieves all incidents with pagination and sorting.
         */
        @GetMapping("/all")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Get all incidents (paginated)", description = "Returns a paginated list of incidents, sortable by any valid field")
        @ApiResponse(responseCode = "200", description = "Incidents retrieved successfully")
        public ResponseEntity<Page<IncidentResponse>> getAllIncidents(
                        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = IncidentConstants.DEFAULT_PAGE) int page,

                        @Parameter(description = "Page size") @RequestParam(defaultValue = IncidentConstants.DEFAULT_SIZE) int size,

                        @Parameter(description = "Sort field") @RequestParam(defaultValue = IncidentConstants.DEFAULT_SORT) String sort,

                        @Parameter(description = "Sort direction (asc/desc)") @RequestParam(defaultValue = IncidentConstants.DEFAULT_DIRECTION) String direction) {

                Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
                Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

                Page<IncidentResponse> incidents = incidentService.getAllIncidents(pageable);
                return ResponseEntity.ok(incidents);
        }

        /**
         * Updates an existing incident.
         */
        @PutMapping("/update/{id}")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Update an incident", description = "Updates an existing incident. Only non-null fields are applied. Status transitions are validated.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Incident updated successfully"),
                        @ApiResponse(responseCode = "400", description = "Validation or status transition error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Incident not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        public ResponseEntity<IncidentResponse> updateIncident(
                        @PathVariable Long id,
                        @Valid @RequestBody UpdateIncidentRequest request) {

                IncidentResponse response = incidentService.updateIncident(id, request);
                return ResponseEntity.ok(response);
        }

        /**
         * Deletes an incident by ID.
         */
        @DeleteMapping("/delete/{id}")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Delete an incident", description = "Permanently deletes an incident by ID")
        @ApiResponses({
                        @ApiResponse(responseCode = "204", description = "Incident deleted successfully"),
                        @ApiResponse(responseCode = "404", description = "Incident not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        public ResponseEntity<Void> deleteIncident(@PathVariable Long id) {
                incidentService.deleteIncident(id);
                return ResponseEntity.noContent().build();
        }

        /**
         * Retrieves statistics for all incidents.
         */
        @GetMapping("/stats")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Get incident statistics", description = "Returns summary statistics including status counts and overdue tickets")
        public ResponseEntity<IncidentStatsResponse> getStats() {
                return ResponseEntity.ok(incidentService.getIncidentStats());
        }

        /**
         * Retrieves incidents reported by a specific user.
         */
        @GetMapping("/reported-by/{userId}")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or #userId == @authService.getCurrentUserId()")
        @Operation(summary = "Get incidents by reporter", description = "Returns a paginated list of incidents reported by the specified user")
        public ResponseEntity<Page<IncidentResponse>> getByReporter(
                        @PathVariable Long userId,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_PAGE) int page,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_SIZE) int size) {
                
                Pageable pageable = PageRequest.of(page, size);
                return ResponseEntity.ok(incidentService.getIncidentsByReporter(userId, pageable));
        }

        /**
         * Retrieves incidents assigned to a specific user.
         */
        @GetMapping("/assigned-to/{userId}")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Get incidents by assignee", description = "Returns a paginated list of incidents assigned to the specified user")
        public ResponseEntity<Page<IncidentResponse>> getByAssignee(
                        @PathVariable Long userId,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_PAGE) int page,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_SIZE) int size) {
                
                Pageable pageable = PageRequest.of(page, size);
                return ResponseEntity.ok(incidentService.getIncidentsByAssignee(userId, pageable));
        }

        @GetMapping("/{id}/timeline")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or @authService.isOwner(#id)")
        @Operation(summary = "Get incident timeline", description = "Returns the history of events for an incident")
        public ResponseEntity<List<com.incidentiq.model.IncidentTimeline>> getTimeline(@PathVariable Long id) {
                return ResponseEntity.ok(timelineService.getTimeline(id));
        }

        @PostMapping("/suggest-severity")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Suggest incident severity", description = "Suggests a priority level based on title and description")
        public ResponseEntity<com.incidentiq.enums.IncidentPriority> suggestSeverity(@RequestBody com.incidentiq.dto.request.CreateIncidentRequest request) {
                return ResponseEntity.ok(severitySuggestionService.suggestPriority(request.getTitle(), request.getDescription()));
        }

        @PostMapping("/check-duplicates")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Check for duplicates", description = "Finds potential duplicate incidents based on title")
        public ResponseEntity<List<com.incidentiq.model.Incident>> checkDuplicates(@RequestParam String title) {
                return ResponseEntity.ok(duplicateDetectionService.findPotentialDuplicates(title));
        }

        @GetMapping("/audit-logs")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Get audit logs", description = "Returns all system audit logs (Admin only)")
        public ResponseEntity<List<com.incidentiq.model.AuditLog>> getAuditLogs() {
                return ResponseEntity.ok(auditService.getAuditLogs());
        }

        @GetMapping("/similar")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Find similar incidents",
                   description = "Searches for similar or duplicate incidents using keyword extraction and category matching. Returns ranked results with actionable suggestions.")
        public ResponseEntity<List<SimilarIncidentResponse>> findSimilarIncidents(
                @RequestParam String title,
                @RequestParam(required = false, defaultValue = "") String description,
                @RequestParam IncidentCategory category,
                @RequestParam(required = false) Long excludeId) {
                return ResponseEntity.ok(
                        similarityDetectionService.findSimilar(title, description, category, excludeId)
                );
        }
}
