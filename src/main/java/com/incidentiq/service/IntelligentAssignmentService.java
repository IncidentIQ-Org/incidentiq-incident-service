package com.incidentiq.service;

import com.incidentiq.enums.Complexity;
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
        // Active form categories
        map.put("BACKEND", List.of("Backend Development", "Full Stack Development"));
        map.put("FRONTEND", List.of("Frontend Development", "Full Stack Development"));
        map.put("DEVOPS", List.of("DevOps", "Cloud Infrastructure"));
        map.put("CLOUD", List.of("Cloud Infrastructure", "DevOps", "IT Support"));
        map.put("APPLICATION_SUPPORT", List.of("IT Support", "Backend Development", "Full Stack Development"));
        CATEGORY_EXPERTISE_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Finds the best user to assign an incident to, excluding the reporter.
     */
    public Long findBestAssignee(Incident incident) {
        return findBestAssignee(incident, incident.getCreatedBy());
    }

    public Long findBestAssignee(Incident incident, Long excludeUserId) {
        Complexity complexity = incident.getComplexity() != null ? incident.getComplexity() : Complexity.MEDIUM;
        log.info("Finding best assignee for incident: {} (category: {}, priority: {}, complexity: {}, excluding: {})",
            incident.getId(), incident.getCategory(), incident.getPriority(), complexity, excludeUserId);

        // Get all available technicians via API
        List<Map<String, Object>> technicians = fetchTechnicians();
        if (technicians == null || technicians.isEmpty()) {
            log.warn("No available technicians found");
            return null;
        }

        // Exclude the reporter so they aren't assigned to their own incident
        if (excludeUserId != null) {
            final Long exclude = excludeUserId;
            technicians = technicians.stream()
                .filter(t -> {
                    Object idObj = t.get("id");
                    if (idObj == null) return true;
                    return !exclude.equals(((Number) idObj).longValue());
                })
                .collect(Collectors.toList());
        }

        if (technicians.isEmpty()) {
            log.warn("No assignable technicians after excluding reporter {}", excludeUserId);
            return null;
        }

        // Filter out users who are on leave, out of office, or offline — they must never receive new incidents
        List<Map<String, Object>> assignable = filterByAvailability(technicians);
        if (assignable.isEmpty()) {
            log.warn("No assignable technicians after availability filter (all on leave/OOO/offline)");
            assignable = technicians; // last resort: fall back to all so incidents are never orphaned
        }
        technicians = assignable;

        // Filter by expertise match
        List<Map<String, Object>> expertiseMatched = filterByExpertise(technicians, incident.getCategory());
        if (expertiseMatched.isEmpty()) {
            log.warn("No technicians with matching expertise for category: {}. Falling back to all.", incident.getCategory());
            expertiseMatched = technicians;
        }

        log.info("Found {} technicians with matching expertise", expertiseMatched.size());

        // Complexity gate: prefer candidates whose experience meets the complexity
        // requirement (Easy→junior ok, Medium→experienced, Hard→specialist,
        // Complex→highly experienced). Fall back to the full pool if nobody qualifies.
        int minYears = complexity.getMinExperienceYears();
        List<Map<String, Object>> qualified = expertiseMatched.stream()
            .filter(u -> experienceYears(u) >= minYears)
            .collect(Collectors.toList());

        List<Map<String, Object>> pool;
        if (!qualified.isEmpty()) {
            pool = qualified;
        } else {
            pool = expertiseMatched;
            if (complexity == Complexity.COMPLEX || complexity == Complexity.HARD) {
                log.warn("No technician meets the {}-complexity experience bar ({}+ yrs). "
                    + "Assigning the most experienced available — manager escalation recommended.", complexity, minYears);
            }
        }

        // Rank by experience-fit + workload, weighted by complexity and priority.
        List<Map<String, Object>> ranked = rankUsers(pool, incident.getPriority(), complexity);

        Map<String, Object> bestAssignee = ranked.get(0);
        Long bestId = ((Number) bestAssignee.get("id")).longValue();
        log.info("Best assignee ID: {} (workload: {}, experience: {}, complexity: {})",
            bestId, bestAssignee.get("workloadScore"), bestAssignee.get("experienceYears"), complexity);

        return bestId;
    }

    private int experienceYears(Map<String, Object> user) {
        Object v = user.get("experienceYears");
        return v != null ? ((Number) v).intValue() : 0;
    }

    // Adds a scheduling penalty to the raw workload so BUSY/IN_TRAINING users rank below AVAILABLE ones
    private int effectiveWorkload(Map<String, Object> user) {
        int base = ((Number) user.getOrDefault("workloadScore", 0)).intValue();
        Object s = user.get("availabilityStatus");
        if (s == null) return base;
        return switch (s.toString()) {
            case "BUSY"        -> base + 10;
            case "IN_TRAINING" -> base + 5;
            default            -> base;
        };
    }

    /**
     * Fetches technicians from user-service via API Gateway
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchTechnicians() {
        try {
            // Call user-service directly to avoid gateway AuthenticationFilter
            String url = "http://user-service/technicians";
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
     * Filters out users whose availabilityStatus blocks assignment.
     * ON_LEAVE, OUT_OF_OFFICE, OFFLINE → never assignable.
     * AVAILABLE, BUSY, IN_TRAINING → still eligible (BUSY/IN_TRAINING penalised in ranking).
     */
    private List<Map<String, Object>> filterByAvailability(List<Map<String, Object>> users) {
        Set<String> blocked = Set.of("ON_LEAVE", "OUT_OF_OFFICE", "OFFLINE");
        return users.stream()
            .filter(u -> {
                Object s = u.get("availabilityStatus");
                if (s == null) return true; // legacy record with no status — keep
                return !blocked.contains(s.toString());
            })
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
     * Ranks candidates by a composite fitness score so that capacity, expertise
     * depth and complexity-appropriate experience all matter:
     *
     *  - Lower current workload is always preferred (capacity).
     *  - For HARD/COMPLEX work, or CRITICAL/HIGH priority, higher experience is
     *    rewarded so difficult/urgent incidents go to seniors.
     *  - For EASY work, lower experience is gently preferred so juniors get the
     *    simple incidents and seniors stay free for the hard ones.
     */
    private List<Map<String, Object>> rankUsers(List<Map<String, Object>> users,
                                                IncidentPriority priority, Complexity complexity) {
        boolean favorSenior = complexity == Complexity.HARD || complexity == Complexity.COMPLEX
                || priority == IncidentPriority.CRITICAL || priority == IncidentPriority.HIGH;
        boolean favorJunior = complexity == Complexity.EASY
                && priority != IncidentPriority.CRITICAL && priority != IncidentPriority.HIGH;

        Comparator<Map<String, Object>> comparator = Comparator.comparingInt(
            u -> effectiveWorkload(u)
        );

        if (favorSenior) {
            comparator = comparator.thenComparing(this::experienceYears, Comparator.reverseOrder());
        } else if (favorJunior) {
            comparator = comparator.thenComparing(this::experienceYears, Comparator.naturalOrder());
        }

        return users.stream()
            .sorted(comparator)
            .collect(Collectors.toList());
    }

    /**
     * Gets assignment ranking for manual assignment dropdown
     */
    public List<Map<String, Object>> getRankedAssignees(IncidentCategory category, IncidentPriority priority, Complexity complexity) {
        List<Map<String, Object>> technicians = fetchTechnicians();
        List<Map<String, Object>> available = filterByAvailability(technicians);
        List<Map<String, Object>> filtered = filterByExpertise(available.isEmpty() ? technicians : available, category);
        return rankUsers(filtered, priority, complexity);
    }
}
