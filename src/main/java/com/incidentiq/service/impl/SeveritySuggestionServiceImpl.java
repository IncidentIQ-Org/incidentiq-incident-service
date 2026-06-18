package com.incidentiq.service.impl;

import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.service.SeveritySuggestionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeveritySuggestionServiceImpl implements SeveritySuggestionService {

    private static final List<String> HIGH_KEYWORDS = List.of("critical", "down", "blocker", "emergency", "fatal", "outage", "security breach");
    private static final List<String> MEDIUM_KEYWORDS = List.of("slow", "issue", "error", "bug", "unavailable", "malfunctioning");

    @Override
    public IncidentPriority suggestPriority(String title, String description) {
        String content = (title + " " + description).toLowerCase();
        
        if (HIGH_KEYWORDS.stream().anyMatch(content::contains)) {
            return IncidentPriority.HIGH;
        } else if (MEDIUM_KEYWORDS.stream().anyMatch(content::contains)) {
            return IncidentPriority.MEDIUM;
        }
        return IncidentPriority.LOW;
    }
}
