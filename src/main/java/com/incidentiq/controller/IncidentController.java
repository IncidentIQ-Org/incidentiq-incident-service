package com.incidentiq.controller;

import java.util.List;
import com.incidentiq.constants.IncidentConstants;
import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.request.SimilarityCheckRequest;
import com.incidentiq.dto.request.UpdateIncidentRequest;
import com.incidentiq.dto.ResolutionRequest;
import com.incidentiq.dto.SlaExtensionRequestDto;
import com.incidentiq.dto.response.ApiErrorResponse;
import com.incidentiq.dto.response.IncidentResponse;
import com.incidentiq.dto.response.IncidentStatsResponse;
import com.incidentiq.dto.response.SimilarIncidentResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.service.IncidentService;
import com.incidentiq.service.SlaExtensionService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import com.incidentiq.dto.request.StatusChangeRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
        private final SlaExtensionService slaExtensionService;
        private final com.incidentiq.service.AiCoachingService aiCoachingService;
        private final com.incidentiq.service.AttachmentService attachmentService;

        public IncidentController(IncidentService incidentService,
                               com.incidentiq.service.TimelineService timelineService,
                               com.incidentiq.service.AuditService auditService,
                               com.incidentiq.service.SeveritySuggestionService severitySuggestionService,
                               com.incidentiq.service.DuplicateDetectionService duplicateDetectionService,
                               SimilarityDetectionService similarityDetectionService,
                               SlaExtensionService slaExtensionService,
                               com.incidentiq.service.AiCoachingService aiCoachingService,
                               com.incidentiq.service.AttachmentService attachmentService) {
                this.incidentService = incidentService;
                this.timelineService = timelineService;
                this.auditService = auditService;
                this.severitySuggestionService = severitySuggestionService;
                this.duplicateDetectionService = duplicateDetectionService;
                this.similarityDetectionService = similarityDetectionService;
                this.slaExtensionService = slaExtensionService;
                this.aiCoachingService = aiCoachingService;
                this.attachmentService = attachmentService;
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
         * Check for similar incidents before creating.
         */
        @PostMapping("/similar")
        @PreAuthorize("hasAnyRole('USER', 'MANAGER', 'ADMIN')")
        @Operation(summary = "Check for similar incidents", description = "Finds similar incidents based on title/description")
        public ResponseEntity<List<SimilarIncidentResponse>> checkSimilarIncidents(
                        @RequestBody SimilarityCheckRequest request) {
                List<SimilarIncidentResponse> similar = similarityDetectionService.findSimilar(
                        request.getTitle(), request.getDescription(), request.getCategory(), null);
                return ResponseEntity.ok(similar);
        }

        @PostMapping("/{id}/attachments")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Upload attachment", description = "Upload a file for an incident (scanned by ClamAV)")
        public ResponseEntity<com.incidentiq.dto.AttachmentDto> uploadAttachment(
                        @PathVariable Long id,
                        @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
                return ResponseEntity.ok(attachmentService.uploadAttachment(id, file));
        }

        @GetMapping("/attachments/{id}/download")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Download attachment", description = "Download a safe attachment")
        public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id) {
                com.incidentiq.dto.AttachmentDto dto = attachmentService.getAttachmentDetails(id);
                byte[] data = attachmentService.downloadAttachment(id);
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.getFileName() + "\"")
                        .contentType(org.springframework.http.MediaType.parseMediaType(dto.getFileType()))
                        .body(data);
        }

        /**
         * Retrieves an incident by ID.
         */
        @GetMapping("/view/{id}")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
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
         * Retrieves all resolved incidents for the Knowledge Base.
         * Accessible by all authenticated users.
         */
        @GetMapping("/knowledge-base")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Get knowledge base (resolved incidents)", description = "Returns a paginated list of all resolved incidents for the knowledge base")
        public ResponseEntity<Page<IncidentResponse>> getKnowledgeBase(
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_PAGE) int page,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_SIZE) int size,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_SORT) String sort,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_DIRECTION) String direction) {
                
                Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
                Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

                Page<IncidentResponse> incidents = incidentService.getKnowledgeBase(pageable);
                return ResponseEntity.ok(incidents);
        }

        /**
         * Retrieves incidents created by the currently authenticated user.
         * Accessible by any authenticated user (USER, MANAGER, ADMIN).
         */
        @GetMapping("/my")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Get my incidents (paginated)", description = "Returns a paginated list of incidents created by the current user")
        @ApiResponse(responseCode = "200", description = "Incidents retrieved successfully")
        public ResponseEntity<Page<IncidentResponse>> getMyIncidents(
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_PAGE) int page,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_SIZE) int size,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_SORT) String sort,
                        @RequestParam(defaultValue = IncidentConstants.DEFAULT_DIRECTION) String direction) {

                Sort.Direction sortDirection = Sort.Direction.fromString(direction.toUpperCase());
                Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

                return ResponseEntity.ok(incidentService.getMyIncidents(pageable));
        }

        /**
         * Retrieves all incidents assigned to the currently authenticated user.
         * Any authenticated user can call this endpoint.
         */
        @GetMapping("/my-assigned")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Get my assigned incidents", description = "Returns all incidents assigned to the current user")
        @ApiResponse(responseCode = "200", description = "Assigned incidents retrieved successfully")
        public ResponseEntity<java.util.List<IncidentResponse>> getMyAssignedIncidents() {
                return ResponseEntity.ok(incidentService.getMyAssignedIncidents());
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
         * Resolves an incident with detailed resolution information. Available to assignees to resolve their assigned incidents,
         * and to managers/admins to resolve any incident.
         */
        @PutMapping("/resolve/{id}")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN') or @authService.isAssignee(#id)")
        @Operation(summary = "Resolve an incident with details", description = "Marks an incident as RESOLVED with root cause, resolution steps, and summary. Assignees can only resolve incidents assigned to them.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Incident resolved successfully"),
                        @ApiResponse(responseCode = "400", description = "Validation or status transition error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Incident not found", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
        })
        public ResponseEntity<IncidentResponse> resolveIncident(
                        @PathVariable Long id,
                        @RequestBody ResolutionRequest resolutionRequest) {
                IncidentResponse response = incidentService.resolveIncidentWithDetails(id, resolutionRequest);
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
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
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

        @PostMapping("/{id}/sla-extension")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Request SLA extension", description = "Creates an SLA extension request for an incident.")
        public ResponseEntity<com.incidentiq.model.SlaExtensionRequest> requestSlaExtension(
                @PathVariable Long id,
                @RequestBody SlaExtensionRequestDto request) {
                com.incidentiq.model.SlaExtensionRequest extension = slaExtensionService.createExtensionRequest(id, request);
                return ResponseEntity.ok(extension);
        }

        @PutMapping("/sla-extension/{extensionId}/approve")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Approve SLA extension", description = "Approves an SLA extension request and updates the incident due date.")
        public ResponseEntity<com.incidentiq.model.SlaExtensionRequest> approveSlaExtension(
                @PathVariable Long extensionId,
                @RequestParam String reason) {
                com.incidentiq.model.SlaExtensionRequest extension = slaExtensionService.approveExtension(extensionId, reason);
                return ResponseEntity.ok(extension);
        }

        @PutMapping("/sla-extension/{extensionId}/reject")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Reject SLA extension", description = "Rejects an SLA extension request.")
        public ResponseEntity<com.incidentiq.model.SlaExtensionRequest> rejectSlaExtension(
                @PathVariable Long extensionId,
                @RequestParam String reason) {
                com.incidentiq.model.SlaExtensionRequest extension = slaExtensionService.rejectExtension(extensionId, reason);
                return ResponseEntity.ok(extension);
        }

        @GetMapping("/{id}/sla-extensions")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN') or @authService.isAssignee(#id) or @authService.isOwner(#id)")
        @Operation(summary = "Get SLA extensions for incident", description = "Returns all SLA extension requests for a specific incident.")
        public ResponseEntity<List<com.incidentiq.model.SlaExtensionRequest>> getSlaExtensions(
                @PathVariable Long id) {
                return ResponseEntity.ok(slaExtensionService.getExtensionsForIncident(id));
        }

        @GetMapping("/sla-extensions/pending")
        @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Get pending SLA extensions", description = "Returns all pending SLA extension requests awaiting approval.")
        public ResponseEntity<List<com.incidentiq.model.SlaExtensionRequest>> getPendingSlaExtensions() {
                return ResponseEntity.ok(slaExtensionService.getPendingExtensions());
        }

        @GetMapping("/{id}/ai-coaching")
        @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
        @Operation(summary = "Get AI Coaching Advice", description = "Returns AI-driven real-time resolution coaching based on historical similar incidents.")
        public ResponseEntity<com.incidentiq.dto.response.AiCoachingResponse> getAiCoachingAdvice(@PathVariable Long id) {
                return ResponseEntity.ok(aiCoachingService.getCoachingAdvice(id));
        }

        /**
         * Changes an incident's status through the lifecycle.
         * Accessible to the assigned technician, managers, and admins.
         * Broadcasts the change via WebSocket so all open viewers see it instantly.
         */
        @PatchMapping("/{id}/status")
        @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN') or @authService.isAssignee(#id)")
        @Operation(summary = "Change incident status", description = "Moves the incident to the next lifecycle status. Only the assigned technician, manager, or admin can call this. Change is broadcast via WebSocket to all open viewers.")
        @ApiResponses({
                @ApiResponse(responseCode = "200", description = "Status updated successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid status transition", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
                @ApiResponse(responseCode = "403", description = "Not authorized to change this incident's status")
        })
        public ResponseEntity<IncidentResponse> changeIncidentStatus(
                @PathVariable Long id,
                @Valid @RequestBody StatusChangeRequest request) {
                IncidentResponse response = incidentService.changeStatus(id, request.getStatus());
                return ResponseEntity.ok(response);
        }

        @GetMapping("/export/csv")
        @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
        @Operation(summary = "Export incidents as CSV", description = "Downloads all incidents as a CSV file. Manager/Admin only.")
        public ResponseEntity<byte[]> exportIncidentsCsv() {
                List<IncidentResponse> incidents = incidentService.getAllIncidentsForExport();

                StringBuilder csv = new StringBuilder();
                csv.append("ID,Title,Category,Priority,Status,Created By,Assigned To,Created At,Due Date,SLA Breached,Tags,Resolved At,Resolution Summary\n");

                for (IncidentResponse i : incidents) {
                        csv.append(csvField(String.valueOf(i.getId()))).append(',')
                           .append(csvField(i.getTitle())).append(',')
                           .append(csvField(i.getCategory())).append(',')
                           .append(csvField(i.getPriority())).append(',')
                           .append(csvField(i.getStatus())).append(',')
                           .append(csvField(i.getCreatedBy() != null ? i.getCreatedBy().toString() : "")).append(',')
                           .append(csvField(i.getAssignedTo() != null ? i.getAssignedTo().toString() : "")).append(',')
                           .append(csvField(i.getCreatedAt() != null ? i.getCreatedAt().toString() : "")).append(',')
                           .append(csvField(i.getDueDate() != null ? i.getDueDate().toString() : "")).append(',')
                           .append(csvField(String.valueOf(i.isSlaBreached()))).append(',')
                           .append(csvField(i.getTags())).append(',')
                           .append(csvField(i.getResolvedAt() != null ? i.getResolvedAt().toString() : "")).append(',')
                           .append(csvField(i.getResolutionSummary()))
                           .append('\n');
                }

                byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"incidents.csv\"")
                        .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                        .body(bytes);
        }

        private static String csvField(String value) {
                if (value == null) return "";
                if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                        return "\"" + value.replace("\"", "\"\"") + "\"";
                }
                return value;
        }
}
