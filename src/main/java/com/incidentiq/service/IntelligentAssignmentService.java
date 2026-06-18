package com.incidentiq.service;

import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intelligent Auto-Assignment Engine
 * Assigns incidents based on:
 * 1. Category/Expertise match (mandatory)
 * 2. Lowest workload score
 * 3. Experience level (for critical incidents)
 * 4. Availability status
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntelligentAssignmentService {

    private final RestTemplate restTemplate;

    // Map incident categories to expertise areas
    private static final Map<String, List<String>> CATEGORY_EXPERTISE_MAP;

    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("HARDWARE", List.of("IT Support", "Infrastructure", "Network Administration"));
        map.put("SERVER", List.of("IT Support", "DevOps", "Cloud Infrastructure", "Network Administration"));
        map.put("NETWORK", List.of("Network Administration", "IT Support", "Security"));
        map.put("SOFTWARE", List.of("Backend Development", "Frontend Development", "Full Stack Development"));
        map.put("APPLICATION", List.of("Backend Development", "Frontend Development", "Full Stack Development"));
        map.put("BUG", List.of("Backend Development", "Frontend Development", "Full Stack Development", "QA Engineering"));
        map.put("SECURITY", List.of("Security", "Network Administration"));
        map.put("BREACH", List.of("Security", "Network Administration"));
        map.put("VULNERABILITY", List.of("Security", "QA Engineering"));
        map.put("DATABASE", List.of("Database Administration", "Backend Development"));
        map.put("DATA", List.of("Database Administration", "Backend Development"));
        map.put("INFRASTRUCTURE", List.of("DevOps", "Cloud Infrastructure", "IT Support"));
        map.put("PERFORMANCE", List.of("Backend Development", "Database Administration", "DevOps"));
        map.put("UI_UX", List.of("Frontend Development", "Full Stack Development"));
        map.put("API", List.of("Backend Development", "Full Stack Development"));
        map.put("MOBILE", List.of("Mobile Development", "Full Stack Development"));
        map.put("TESTING", List.of("QA Engineering", "Full Stack Development"));
        map.put("DEPLOYMENT", List.of("DevOps", "Cloud Infrastructure"));
        map.put("GENERAL", List.of("IT Support", "Backend Development", "Frontend Development"));
        CATEGORY_EXPERTISE_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Finds the best user to assign an incident to
     */
    public Long findBestAssignee(Incident incident) {
        log.info("Finding best assignee for incident: {} (category: {}, priority: {})", 
            incident.getId(), incident.getCategory(), incident.getPriority());

        // Get all available technicians via API
        List<Map<String, Object>> technicians = fetchTechnicians();
        if (technicians == null || technicians.isEmpty()) {
            log.warn("No available technicians found");
            return null;
        }

        // Filter by expertise match
        List<Map<String, Object>> expertiseMatched = filterByExpertise(technicians, incident.getCategory());
        if (expertiseMatched.isEmpty()) {
            log.warn("No technicians with matching expertise for category: {}", incident.getCategory());
            // Fallback to all technicians if no expertise match
            expertiseMatched = technicians;
        }

        log.info("Found {} technicians with matching expertise", expertiseMatched.size());

        // Sort by workload score (ascending) and experience (descending for critical incidents)
        List<Map<String, Object>> ranked = rankUsers(expertiseMatched, incident.getPriority());

        Map<String, Object> bestAssignee = ranked.get(0);
        Long bestId = ((Number) bestAssignee.get("id")).longValue();
        log.info("Best assignee ID: {} (workload: {}, experience: {})", 
            bestId, bestAssignee.get("workloadScore"), bestAssignee.get("experienceYears"));

        return bestId;
    }

    /**
     * Fetches technicians from user-service via API Gateway
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTechnicians() {
        try {
            String url = "http://api-gateway/api/users/technicians";
            Map<String, Object>[] response = restTemplate.getForObject(url, Map[].class);
            return response != null ? Arrays.asList(response) : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch technicians", e);
            return List.of();
        }
    }

    /**
     * Filters users by expertise matching the incident category
     */
    private List<Map<String, Object>> filterByExpertise(List<Map<String, Object>> users, IncidentCategory category) {
        if (category == null) {
            return users;
        }

        String categoryStr = category.name();
        List<String> requiredExpertise = CATEGORY_EXPERTISE_MAP.getOrDefault(categoryStr, List.of("IT Support"));

        return users.stream()
            .filter(user -> hasExpertiseMatch(user, requiredExpertise))
            .collect(Collectors.toList());
    }

    /**
     * Checks if user has any of the required expertise areas
     */
    private boolean hasExpertiseMatch(Map<String, Object> user, List<String> requiredExpertise) {
        String expertiseJson = (String) user.get("expertise");
        if (expertiseJson == null || expertiseJson.isEmpty()) {
            return false;
        }

        try {
            // Parse JSON array string
            expertiseJson = expertiseJson.replace("[", "").replace("]", "").replace("\"", "");
            String[] userExpertise = expertiseJson.split(",");
            List<String> expertiseList = Arrays.asList(userExpertise);
            
            return expertiseList.stream()
                .anyMatch(exp -> requiredExpertise.stream()
                    .anyMatch(req -> req.trim().equalsIgnoreCase(exp.trim())));
        } catch (Exception e) {
            log.error("Failed to parse expertise for user: {}", user.get("username"), e);
            return false;
        }
    }

    /**
     * Ranks users by workload and experience
     */
    private List<Map<String, Object>> rankUsers(List<Map<String, Object>> users, IncidentPriority priority) {
        Comparator<Map<String, Object>> comparator = Comparator.comparing(
            u -> ((Number) u.getOrDefault("workloadScore", 0)).intValue()
        );

        // For critical/high priority incidents, prioritize higher experience
        if (priority == IncidentPriority.CRITICAL || priority == IncidentPriority.HIGH) {
            comparator = comparator.thenComparing(
                (Map<String, Object> u) -> u.get("experienceYears") != null 
                    ? ((Number) u.get("experienceYears")).intValue() 
                    : 0,
                Comparator.reverseOrder()
            );
        }

        return users.stream()
            .sorted(comparator)
            .collect(Collectors.toList());
    }

    /**
     * Gets assignment ranking for manual assignment dropdown
     */
    public List<Map<String, Object>> getRankedAssignees(IncidentCategory category, IncidentPriority priority) {
        List<Map<String, Object>> technicians = fetchTechnicians();
        List<Map<String, Object>> filtered = filterByExpertise(technicians, category);
        return rankUsers(filtered, priority);
    }
}
