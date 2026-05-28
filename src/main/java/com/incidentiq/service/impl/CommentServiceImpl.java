package com.incidentiq.service.impl;

import com.incidentiq.dto.request.CommentRequest;
import com.incidentiq.dto.response.CommentResponse;
import com.incidentiq.model.Comment;
import com.incidentiq.repository.CommentRepository;
import com.incidentiq.service.CommentService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final com.incidentiq.service.TimelineService timelineService;
    private final com.incidentiq.service.AuditService auditService;
    private final com.incidentiq.security.AuthorizationService authService;

    @Override
    @Transactional
    public CommentResponse addComment(Long incidentId, CommentRequest request) {
        log.info("Adding comment to incident: {}", incidentId);
        Long userId = authService.getCurrentUserId();
        
        Comment comment = Comment.builder()
                .incidentId(incidentId)
                .content(request.getContent())
                .userId(userId)
                .build();
        
        Comment saved = commentRepository.save(comment);
        
        timelineService.logEvent(incidentId, "COMMENT_ADDED", "User added a comment", userId);
        auditService.logAction("ADD_COMMENT", userId, "Comment", saved.getId(), "Incident: " + incidentId);
        
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByIncident(Long incidentId) {
        log.info("Fetching comments for incident: {}", incidentId);
        return commentRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, CommentRequest request) {
        log.info("Updating comment: {}", commentId);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found with id: " + commentId));
        
        comment.setContent(request.getContent());
        Comment updated = commentRepository.save(comment);
        
        Long userId = authService.getCurrentUserId();
        auditService.logAction("UPDATE_COMMENT", userId, "Comment", commentId, "Updated content");
        
        return mapToResponse(updated);
    }

    private CommentResponse mapToResponse(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .incidentId(comment.getIncidentId())
                .content(comment.getContent())
                .userId(comment.getUserId())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
