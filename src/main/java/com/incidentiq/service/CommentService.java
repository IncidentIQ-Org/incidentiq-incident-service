package com.incidentiq.service;

import com.incidentiq.dto.request.CommentRequest;
import com.incidentiq.dto.response.CommentResponse;

import java.util.List;

public interface CommentService {
    CommentResponse addComment(Long incidentId, CommentRequest request);
    List<CommentResponse> getCommentsByIncident(Long incidentId);
    CommentResponse updateComment(Long commentId, CommentRequest request);
}
