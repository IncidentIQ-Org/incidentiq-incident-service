package com.incidentiq.service;

import com.incidentiq.dto.response.SimilarIncidentResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.mapper.IncidentMapper;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Incident Similarity Detection Service
 *
 * When a new incident is reported, this service:
 * 1. Extracts keywords from the title and description
 * 2. Searches for similar past incidents by keyword match
 * 3. Searches for incidents in the same category (last 30 days)
 * 4. Scores and ranks matches by relevance
 * 5. Returns similar incidents with suggested fixes (from their resolution data)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarityDetectionService {

    private final IncidentRepository incidentRepository;

    // Common words to exclude from keyword extraction
    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "is", "in", "on", "at", "to", "for", "of", "and", "or",
        "not", "with", "from", "by", "has", "have", "was", "were", "are", "be",
        "this", "that", "it", "its", "we", "i", "you", "my", "our", "their"
    );

    /**
     * Find similar incidents based on title and description keywords.
     * Returns a ranked list of similar incidents.
     */
    @Transactional(readOnly = true)
    public List<SimilarIncidentResponse> findSimilar(String title, String description, IncidentCategory category, Long excludeId) {
        List<String> keywords = extractKeywords(title + " " + description);
        log.debug("Similarity search keywords: {}", keywords);

        Map<Long, Integer> scoreMap = new HashMap<>();
        Map<Long, Incident> incidentMap = new HashMap<>();

        // Search by each keyword and accumulate scores
        for (String keyword : keywords) {
            List<Incident> matches = incidentRepository.findSimilarByKeyword(keyword);
            for (Incident match : matches) {
                if (excludeId != null && match.getId().equals(excludeId)) continue;
                scoreMap.merge(match.getId(), 1, Integer::sum);
                incidentMap.put(match.getId(), match);
            }
        }

        // Also include recent same-category incidents
        List<Incident> categoryMatches = incidentRepository.findRecentByCategory(
            category, LocalDateTime.now().minusDays(30)
        );
        for (Incident match : categoryMatches) {
            if (excludeId != null && match.getId().equals(excludeId)) continue;
            scoreMap.merge(match.getId(), 2, Integer::sum); // category match = 2 points
            incidentMap.put(match.getId(), match);
        }

        // Sort by score descending and take top 5
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
            .limit(5)
            .map(entry -> {
                Incident incident = incidentMap.get(entry.getKey());
                return SimilarIncidentResponse.builder()
                    .incidentId(incident.getId())
                    .title(incident.getTitle())
                    .category(incident.getCategory().name())
                    .priority(incident.getPriority().name())
                    .status(incident.getStatus().name())
                    .similarityScore(entry.getValue())
                    .createdAt(incident.getCreatedAt())
                    .isDuplicate(entry.getValue() >= 4)
                    .suggestion(generateSuggestion(incident))
                    .build();
            })
            .collect(Collectors.toList());
    }

    /** Extract meaningful keywords from a text string */
    public List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.toLowerCase().split("[\\s,\\.;:!?\\-\\/]+"))
            .filter(w -> w.length() > 3)
            .filter(w -> !STOP_WORDS.contains(w))
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }

    /** Generate a suggested action based on the matched incident's status */
    private String generateSuggestion(Incident incident) {
        return switch (incident.getStatus()) {
            case RESOLVED, CLOSED ->
                "This issue was previously resolved. Check incident #" + incident.getId() + " for resolution steps.";
            case IN_PROGRESS ->
                "Incident #" + incident.getId() + " is currently being worked on. Consider linking to this ticket.";
            case ESCALATED ->
                "A similar issue #" + incident.getId() + " is escalated. Coordinate with the escalation team.";
            default ->
                "Similar open incident #" + incident.getId() + " exists. Check for possible duplication.";
        };
    }
}
