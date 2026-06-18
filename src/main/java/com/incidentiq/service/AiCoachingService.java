package com.incidentiq.service;

import com.incidentiq.dto.response.AiCoachingResponse;
import com.incidentiq.dto.response.SimilarIncidentResponse;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI Incident Autopilot — Real-Time Resolution Coaching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiCoachingService {

    private final IncidentRepository incidentRepository;
    private final SimilarityDetectionService similarityDetectionService;

    public AiCoachingResponse getCoachingAdvice(Long incidentId) {
        log.info("Generating AI coaching advice for incident ID: {}", incidentId);
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new RuntimeException("Incident not found"));

        // Fetch similar incidents
        List<SimilarIncidentResponse> similarIncidents = similarityDetectionService.findSimilar(
                incident.getTitle(), incident.getDescription(), incident.getCategory(), incidentId);

        // Filter to only RESOLVED or CLOSED incidents that have a root cause
        List<Incident> resolvedSimilar = similarIncidents.stream()
                .map(sim -> incidentRepository.findById(sim.getIncidentId()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(i -> (i.getStatus().name().equals("RESOLVED") || i.getStatus().name().equals("CLOSED")) && 
                            i.getRootCause() != null && !i.getRootCause().isEmpty())
                .limit(5)
                .collect(Collectors.toList());

        // Mocking AI response generation based on historical data
        return generateMockAiResponse(incident, resolvedSimilar);
    }

    private AiCoachingResponse generateMockAiResponse(Incident incident, List<Incident> historical) {
        if (historical.isEmpty()) {
            return AiCoachingResponse.builder()
                .rootCauseAnalysis("No similar historical incidents found. Unable to provide confident root cause analysis.")
                .resolutionSteps(List.of(
                    "Investigate system logs for errors", 
                    "Check recent deployments or configuration changes", 
                    "Verify network connectivity"
                ))
                .estimatedTimeMinutes(null)
                .similarIncidentsUsed(List.of())
                .confidenceScore(0.0)
                .build();
        }

        // Find most common root cause (simplified by picking the first one's root cause for the demo)
        String bestRootCause = historical.get(0).getRootCause();
        double confidence = Math.min(95.0, 50.0 + (historical.size() * 10.0)); // Mock confidence calculation

        // Calculate average resolution time
        double avgTime = historical.stream()
            .filter(i -> i.getActualResolutionMinutes() != null)
            .mapToInt(Incident::getActualResolutionMinutes)
            .average()
            .orElse(45.0);

        List<AiCoachingResponse.SimilarIncidentContext> contextList = historical.stream()
            .map(i -> AiCoachingResponse.SimilarIncidentContext.builder()
                .incidentId(i.getId())
                .title(i.getTitle())
                .rootCause(i.getRootCause())
                .resolutionTimeMinutes(i.getActualResolutionMinutes() != null ? i.getActualResolutionMinutes().longValue() : null)
                .build())
            .collect(Collectors.toList());

        // Construct steps
        String historicalStep = historical.get(0).getResolutionSteps();
        if (historicalStep == null || historicalStep.isEmpty()) {
            historicalStep = "Apply standard fix for " + incident.getCategory().name() + " issues based on historical patterns.";
        }

        return AiCoachingResponse.builder()
            .rootCauseAnalysis("Based on " + historical.size() + " similar incidents, this is most likely caused by: " + bestRootCause)
            .resolutionSteps(List.of(
                "Review the root cause identified in similar incident INC-" + historical.get(0).getId(),
                historicalStep,
                "Verify system stability after applying fix and update the ticket"
            ))
            .estimatedTimeMinutes((int) avgTime)
            .similarIncidentsUsed(contextList)
            .confidenceScore(confidence)
            .build();
    }
}
