package com.incidentiq.service;

import com.incidentiq.dto.response.SimilarIncidentResponse;
import com.incidentiq.enums.IncidentCategory;

import java.util.List;

/**
 * Service interface for incident similarity detection.
 */
public interface SimilarityDetectionService {

    /**
     * Finds similar incidents based on title, description, and category.
     *
     * @param title the incident title
     * @param description the incident description
     * @param category the incident category
     * @param excludeId the incident ID to exclude (usually the current incident)
     * @return list of similar incident responses
     */
    List<SimilarIncidentResponse> findSimilar(String title, String description, IncidentCategory category, Long excludeId);

    /**
     * Extracts keywords from a text string.
     *
     * @param text the text to analyze
     * @return list of keywords
     */
    List<String> extractKeywords(String text);
}
