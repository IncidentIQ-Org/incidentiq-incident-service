package com.incidentiq.service;

import com.incidentiq.enums.IncidentPriority;

/**
 * Service interface for suggesting incident priority based on keywords.
 */
public interface SeveritySuggestionService {

    /**
     * Suggests a priority level based on incident title and description keywords.
     *
     * @param title the incident title
     * @param description the incident description
     * @return the suggested priority
     */
    IncidentPriority suggestPriority(String title, String description);
}
