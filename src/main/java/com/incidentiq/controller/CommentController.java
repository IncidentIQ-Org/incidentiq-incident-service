package com.incidentiq.controller;

import com.incidentiq.dto.request.CommentRequest;
import com.incidentiq.dto.response.CommentResponse;
import com.incidentiq.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/incidents/{incidentId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Incident discussion and comment APIs")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/add")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or @authService.isOwner(#incidentId)")
    @Operation(summary = "Add a comment", description = "Adds a new comment to the specified incident")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long incidentId,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.addComment(incidentId, request));
    }

    @GetMapping("/all")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or @authService.isOwner(#incidentId)")
    @Operation(summary = "Get all comments", description = "Retrieves all comments for the specified incident")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long incidentId) {
        return ResponseEntity.ok(commentService.getCommentsByIncident(incidentId));
    }

    @PutMapping("/update/{commentId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or @authService.isOwner(#incidentId)")
    @Operation(summary = "Update a comment", description = "Updates an existing comment's content")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long incidentId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.ok(commentService.updateComment(commentId, request));
    }
}



