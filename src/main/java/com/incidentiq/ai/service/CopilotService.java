package com.incidentiq.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentiq.ai.client.AiCompletion;
import com.incidentiq.ai.client.OpenRouterClient;
import com.incidentiq.ai.dto.AiRequests;
import com.incidentiq.ai.dto.AiResponses;
import com.incidentiq.ai.dto.AiResponses.AiMeta;
import com.incidentiq.ai.prompt.PromptLibrary;
import com.incidentiq.ai.util.AiTextUtils;
import com.incidentiq.dto.response.SimilarIncidentResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.service.SeveritySuggestionService;
import com.incidentiq.service.SimilarityDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orchestrates every Copilot feature: gathers IncidentIQ context (incidents, the TF-IDF
 * similarity engine, aggregate stats), prompts the model via {@link OpenRouterClient}, parses
 * the structured output, and — crucially — falls back to deterministic heuristics whenever AI
 * is disabled, unconfigured, or unreachable. The UI therefore always gets a usable answer.
 */
@Service
@Slf4j
public class CopilotService {

    private final OpenRouterClient client;
    private final IncidentRepository incidentRepository;
    private final SimilarityDetectionService similarityService;
    private final SeveritySuggestionService severityService;
    private final RestTemplate lbRestTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final int SIMILAR_LIMIT = 5;
    private static final List<IncidentStatus> ACTIVE_STATUSES = List.of(
            IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS,
            IncidentStatus.ESCALATED, IncidentStatus.NEED_MORE_INFO);

    public CopilotService(OpenRouterClient client,
                          IncidentRepository incidentRepository,
                          SimilarityDetectionService similarityService,
                          SeveritySuggestionService severityService,
                          @Qualifier("restTemplate") RestTemplate lbRestTemplate) {
        this.client = client;
        this.incidentRepository = incidentRepository;
        this.similarityService = similarityService;
        this.severityService = severityService;
        this.lbRestTemplate = lbRestTemplate;
    }

    public boolean aiAvailable() {
        return client.isConfigured();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  1. Incident Creation Assistant
    // ═══════════════════════════════════════════════════════════════════════

    public AiResponses.IncidentAssist assistIncident(AiRequests.IncidentAssist req) {
        AiCompletion res = client.complete("incident-assist", PromptLibrary.incidentAssist(req), true);
        if (res.success()) {
            JsonNode json = parseJson(res.content());
            if (json != null) {
                IncidentCategory cat = validCategory(text(json, "category", req.getCategory()));
                // Complexity: trust the model when it returns a valid value, otherwise
                // back-fill from the deterministic estimator so the field is never blank.
                String rawComplexity = text(json, "complexity", null);
                ComplexityGuess fallbackCx = guessComplexity(
                        nz(req.getTitle()) + " " + nz(req.getDescription()), cat);
                com.incidentiq.enums.Complexity complexity = rawComplexity != null
                        ? validComplexity(rawComplexity) : fallbackCx.level();
                Double conf = number(json, "complexityConfidence", (double) fallbackCx.confidence());
                String cxReason = text(json, "complexityReason", fallbackCx.reason());
                return AiResponses.IncidentAssist.builder()
                        .improvedTitle(text(json, "improvedTitle", req.getTitle()))
                        .improvedDescription(text(json, "improvedDescription", req.getDescription()))
                        .category(cat.name())
                        .priority(validPriority(text(json, "priority", null)).name())
                        .complexity(complexity.name())
                        .complexityConfidence(conf != null ? (int) Math.round(conf) : fallbackCx.confidence())
                        .complexityReason(cxReason)
                        .tags(stringList(json, "tags"))
                        .rationale(text(json, "rationale", null))
                        .meta(meta(res))
                        .build();
            }
        }
        return heuristicIncidentAssist(req, degradeNotice(res));
    }

    private AiResponses.IncidentAssist heuristicIncidentAssist(AiRequests.IncidentAssist req, String notice) {
        IncidentPriority priority = severityService.suggestPriority(
                nz(req.getTitle()), nz(req.getDescription()));
        IncidentCategory category = guessCategory(nz(req.getTitle()) + " " + nz(req.getDescription()),
                validCategory(req.getCategory()));
        ComplexityGuess cx = guessComplexity(nz(req.getTitle()) + " " + nz(req.getDescription()), category);
        return AiResponses.IncidentAssist.builder()
                .improvedTitle(tidyTitle(req.getTitle()))
                .improvedDescription(nz(req.getDescription()))
                .category(category.name())
                .priority(priority.name())
                .complexity(cx.level().name())
                .complexityConfidence(cx.confidence())
                .complexityReason(cx.reason())
                .tags(keywordTags(nz(req.getTitle()) + " " + nz(req.getDescription())))
                .rationale("Heuristic suggestion based on keyword analysis.")
                .meta(AiMeta.heuristic(notice))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. Resolution Assistant
    // ═══════════════════════════════════════════════════════════════════════

    public AiResponses.ResolutionAssist assistResolution(AiRequests.ResolutionAssist req) {
        Incident inc = loadIncident(req.getIncidentId());
        List<Incident> similar = gatherSimilarResolved(inc);

        AiCompletion res = client.complete("resolution-assist",
                PromptLibrary.resolutionAssist(inc, req.getNotes(), similar), true);
        if (res.success()) {
            JsonNode json = parseJson(res.content());
            if (json != null) {
                return AiResponses.ResolutionAssist.builder()
                        .rootCause(text(json, "rootCause", null))
                        .resolutionSteps(text(json, "resolutionSteps", null))
                        .resolutionSummary(text(json, "resolutionSummary", null))
                        .meta(meta(res))
                        .build();
            }
        }
        return heuristicResolution(inc, similar, degradeNotice(res));
    }

    private AiResponses.ResolutionAssist heuristicResolution(Incident inc, List<Incident> similar, String notice) {
        Incident ref = similar.isEmpty() ? null : similar.get(0);
        String rootCause = ref != null && ref.getRootCause() != null
                ? "Likely similar to INC-" + ref.getId() + ": " + ref.getRootCause()
                : "Investigate logs and recent changes for the " + inc.getCategory() + " component.";
        String steps = ref != null && ref.getResolutionSteps() != null
                ? ref.getResolutionSteps()
                : String.join("\n",
                    "1. Reproduce and confirm the reported symptom.",
                    "2. Inspect relevant logs and recent deployments/config changes.",
                    "3. Apply the standard fix for " + inc.getCategory() + " issues.",
                    "4. Verify stability and update the ticket.");
        String summary = ref != null && ref.getResolutionSummary() != null
                ? "Resolved using the approach from INC-" + ref.getId() + "."
                : "Resolved after diagnosing the " + inc.getCategory() + " issue and applying the standard fix.";
        return AiResponses.ResolutionAssist.builder()
                .rootCause(rootCause).resolutionSteps(steps).resolutionSummary(summary)
                .meta(AiMeta.heuristic(notice)).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. AI Coaching
    // ═══════════════════════════════════════════════════════════════════════

    public AiResponses.Coaching coach(Long incidentId) {
        Incident inc = loadIncident(incidentId);
        List<SimilarIncidentResponse> scored = safeFindSimilar(inc, incidentId);
        List<Incident> similar = resolveSimilarEntities(scored);
        List<AiResponses.Coaching.SimilarRef> refs = scored.stream().limit(SIMILAR_LIMIT)
                .map(s -> AiResponses.Coaching.SimilarRef.builder()
                        .incidentId(s.getIncidentId()).title(s.getTitle())
                        .similarityScore(s.getSimilarityScore()).build())
                .collect(Collectors.toList());

        AiCompletion res = client.complete("coaching", PromptLibrary.coaching(inc, similar), true);
        if (res.success()) {
            JsonNode json = parseJson(res.content());
            if (json != null) {
                return AiResponses.Coaching.builder()
                        .troubleshootingSteps(stringList(json, "troubleshootingSteps"))
                        .nextActions(stringList(json, "nextActions"))
                        .commonFixes(stringList(json, "commonFixes"))
                        .guidance(text(json, "guidance", null))
                        .confidence(number(json, "confidence", similar.isEmpty() ? 35.0 : 75.0))
                        .basedOn(refs)
                        .meta(meta(res))
                        .build();
            }
        }
        return heuristicCoaching(inc, similar, refs, degradeNotice(res));
    }

    private AiResponses.Coaching heuristicCoaching(Incident inc, List<Incident> similar,
                                                   List<AiResponses.Coaching.SimilarRef> refs, String notice) {
        com.incidentiq.enums.Complexity cx = inc.getComplexity() != null
                ? inc.getComplexity() : com.incidentiq.enums.Complexity.MEDIUM;

        // Depth of guidance scales with complexity.
        List<String> steps = new ArrayList<>(List.of(
                "Confirm the symptom and its scope (who/what is affected).",
                "Check logs and metrics for the " + inc.getCategory() + " component."));
        if (cx != com.incidentiq.enums.Complexity.EASY) {
            steps.add("Review recent deployments or configuration changes.");
        }
        if (cx == com.incidentiq.enums.Complexity.HARD || cx == com.incidentiq.enums.Complexity.COMPLEX) {
            steps.add("Isolate the failing layer by reproducing in a controlled environment.");
            steps.add("Correlate across dependent systems (DB, network, upstream/downstream services).");
        }
        if (cx == com.incidentiq.enums.Complexity.COMPLEX) {
            steps.add("Open a working doc and capture findings as you go — this is a multi-step investigation.");
            steps.add("Pull in a specialist for the affected domain and notify your manager early.");
        }

        List<String> fixes = new ArrayList<>();
        for (Incident s : similar) {
            if (s.getResolutionSummary() != null && !s.getResolutionSummary().isBlank()) {
                fixes.add("INC-" + s.getId() + ": " + AiTextUtils.truncate(s.getResolutionSummary(), 160));
            }
        }
        if (fixes.isEmpty()) fixes.add("No similar resolved incidents on record yet.");

        List<String> nextActions = new ArrayList<>(List.of(
                "Assign/confirm ownership", "Update status to IN_PROGRESS once investigating"));
        if (cx == com.incidentiq.enums.Complexity.COMPLEX || cx == com.incidentiq.enums.Complexity.HARD) {
            nextActions.add("Review the knowledge base and similar incidents before changing anything");
        }
        if (cx == com.incidentiq.enums.Complexity.COMPLEX) {
            nextActions.add("Consider escalating to a manager / war room if impact is wide");
        }

        String guidance = switch (cx) {
            case EASY -> "This looks straightforward — apply the standard fix and verify.";
            case MEDIUM -> "Work the steps in order; compare against the similar incidents below.";
            case HARD -> "Treat this as a real investigation: gather evidence before acting, and lean on the similar incidents.";
            case COMPLEX -> "High-difficulty incident — follow a deliberate multi-step strategy, use similar-incident analysis and the KB, and loop in specialists/managers.";
        };

        return AiResponses.Coaching.builder()
                .troubleshootingSteps(steps)
                .nextActions(nextActions)
                .commonFixes(fixes)
                .guidance(guidance)
                .confidence(similar.isEmpty() ? 30.0 : 60.0)
                .basedOn(refs)
                .meta(AiMeta.heuristic(notice))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  4. Knowledge Base Article
    // ═══════════════════════════════════════════════════════════════════════

    public AiResponses.KbArticle generateKbArticle(Long incidentId) {
        Incident inc = loadIncident(incidentId);
        if (inc.getStatus() != IncidentStatus.RESOLVED && inc.getStatus() != IncidentStatus.CLOSED) {
            throw new IllegalStateException("Knowledge-base articles can only be generated from resolved or closed incidents.");
        }

        AiCompletion res = client.complete("kb-article", PromptLibrary.kbArticle(inc), true);
        if (res.success()) {
            JsonNode json = parseJson(res.content());
            if (json != null) {
                String title = text(json, "title", inc.getTitle());
                String problem = text(json, "problemStatement", inc.getDescription());
                String rootCause = text(json, "rootCause", inc.getRootCause());
                List<String> steps = stringList(json, "resolutionSteps");
                List<String> prevention = stringList(json, "preventionTips");
                return AiResponses.KbArticle.builder()
                        .title(title).problemStatement(problem).rootCause(rootCause)
                        .resolutionSteps(steps).preventionTips(prevention)
                        .markdown(renderKbMarkdown(title, problem, rootCause, steps, prevention))
                        .meta(meta(res)).build();
            }
        }
        return heuristicKbArticle(inc, degradeNotice(res));
    }

    private AiResponses.KbArticle heuristicKbArticle(Incident inc, String notice) {
        String title = tidyTitle(inc.getTitle());
        String problem = nz(inc.getDescription());
        String rootCause = nz(inc.getRootCause());
        List<String> steps = splitToList(inc.getResolutionSteps());
        if (steps.isEmpty() && inc.getResolutionSummary() != null) steps = List.of(inc.getResolutionSummary());
        List<String> prevention = List.of(
                "Add monitoring/alerting for early detection of this " + inc.getCategory() + " condition.",
                "Document this resolution in the runbook for faster future response.");
        return AiResponses.KbArticle.builder()
                .title(title).problemStatement(problem).rootCause(rootCause)
                .resolutionSteps(steps).preventionTips(prevention)
                .markdown(renderKbMarkdown(title, problem, rootCause, steps, prevention))
                .meta(AiMeta.heuristic(notice)).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  5. Manager Insights
    // ═══════════════════════════════════════════════════════════════════════

    public AiResponses.ManagerInsights managerInsights(String question) {
        ManagerData data = buildManagerData();

        AiCompletion res = client.complete("manager-insights",
                PromptLibrary.managerInsights(data.context, question),
                question == null || question.isBlank()); // cache only the default digest
        if (res.success()) {
            JsonNode json = parseJson(res.content());
            if (json != null) {
                return AiResponses.ManagerInsights.builder()
                        .summary(text(json, "summary", null))
                        .slaRisks(stringList(json, "slaRisks"))
                        .categoryHotspots(stringList(json, "categoryHotspots"))
                        .workloadObservations(stringList(json, "workloadObservations"))
                        .needsAttention(stringList(json, "needsAttention"))
                        .meta(meta(res)).build();
            }
        }
        return heuristicManagerInsights(data, degradeNotice(res));
    }

    private AiResponses.ManagerInsights heuristicManagerInsights(ManagerData d, String notice) {
        return AiResponses.ManagerInsights.builder()
                .summary(d.summaryLine)
                .slaRisks(d.slaRisks)
                .categoryHotspots(d.categoryHotspots)
                .workloadObservations(d.workload)
                .needsAttention(d.needsAttention)
                .meta(AiMeta.heuristic(notice))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  6. Similar-incident resolution summary
    // ═══════════════════════════════════════════════════════════════════════

    public AiResponses.SimilarSummary summarizeSimilar(AiRequests.SimilarSummary req) {
        IncidentCategory cat = validCategory(req.getCategory());
        List<SimilarIncidentResponse> scored = safeFindSimilarRaw(req.getTitle(), req.getDescription(), cat, null);
        List<Incident> similar = resolveSimilarEntities(scored);
        List<AiResponses.Coaching.SimilarRef> refs = scored.stream().limit(SIMILAR_LIMIT)
                .map(s -> AiResponses.Coaching.SimilarRef.builder()
                        .incidentId(s.getIncidentId()).title(s.getTitle())
                        .similarityScore(s.getSimilarityScore()).build())
                .collect(Collectors.toList());

        if (similar.isEmpty()) {
            return AiResponses.SimilarSummary.builder()
                    .summary("No similar resolved incidents found yet — this may be a new class of issue.")
                    .confidence(20.0).matchesConsidered(0).references(refs)
                    .meta(AiMeta.heuristic("No history available.")).build();
        }

        AiCompletion res = client.complete("similar-summary",
                PromptLibrary.similarSummary(req.getTitle(), req.getDescription(), similar), true);
        if (res.success()) {
            JsonNode json = parseJson(res.content());
            if (json != null) {
                return AiResponses.SimilarSummary.builder()
                        .summary(text(json, "summary", null))
                        .confidence(number(json, "confidence", 60.0))
                        .matchesConsidered(similar.size())
                        .references(refs)
                        .meta(meta(res)).build();
            }
        }
        // Heuristic: stitch the top resolutions together.
        String summary = "Similar incidents were typically resolved by: "
                + similar.stream().limit(3)
                    .map(s -> s.getResolutionSummary() != null ? s.getResolutionSummary() : s.getRootCause())
                    .filter(x -> x != null && !x.isBlank())
                    .collect(Collectors.joining("; "));
        return AiResponses.SimilarSummary.builder()
                .summary(summary.endsWith(": ") ? "Historical resolutions are available on the linked incidents." : summary)
                .confidence(55.0).matchesConsidered(similar.size()).references(refs)
                .meta(AiMeta.heuristic(degradeNotice(res))).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  7. Scoped Copilot chat
    // ═══════════════════════════════════════════════════════════════════════

    public AiResponses.Ask ask(AiRequests.Ask req) {
        String context = buildChatContext(req);
        AiCompletion res = client.complete("ask", PromptLibrary.ask(req, context), false);
        if (res.success()) {
            String answer = AiTextUtils.cleanText(res.content());
            return AiResponses.Ask.builder()
                    .answer(answer)
                    .suggestions(extractTrailingSuggestions(answer))
                    .meta(meta(res)).build();
        }
        // Degraded: point them at the relevant feature instead of failing.
        String fallback = client.isConfigured()
                ? "I couldn't reach the AI service just now. Please try again in a moment."
                : "The AI Copilot isn't configured yet (no OpenRouter key). I can still help via the "
                  + "built-in tools: similarity detection on the Create Incident page, and the "
                  + "AI Coach button on an incident.";
        return AiResponses.Ask.builder()
                .answer(fallback)
                .suggestions(List.of("Show incidents needing attention", "How do I resolve this incident?"))
                .meta(AiMeta.heuristic(degradeNotice(res))).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Context gathering
    // ═══════════════════════════════════════════════════════════════════════

    private String buildChatContext(AiRequests.Ask req) {
        StringBuilder sb = new StringBuilder();
        if (req.getPage() != null && !req.getPage().isBlank()) {
            sb.append("User is on page: ").append(req.getPage()).append('\n');
        }
        if (req.getIncidentId() != null) {
            incidentRepository.findById(req.getIncidentId()).ifPresent(inc -> {
                sb.append("Focused incident INC-").append(inc.getId()).append(":\n");
                sb.append("  Title: ").append(nz(inc.getTitle())).append('\n');
                sb.append("  Category/Priority/Status: ").append(inc.getCategory())
                  .append(" / ").append(inc.getPriority()).append(" / ").append(inc.getStatus()).append('\n');
                sb.append("  Description: ").append(AiTextUtils.truncate(inc.getDescription(), 500)).append('\n');
                if (inc.getRootCause() != null && !inc.getRootCause().isBlank()) {
                    sb.append("  Root cause: ").append(AiTextUtils.truncate(inc.getRootCause(), 300)).append('\n');
                }
            });
        }
        return sb.toString();
    }

    /** Aggregate data + ready-made deterministic bullets for manager insights. */
    private ManagerData buildManagerData() {
        ManagerData d = new ManagerData();
        StringBuilder ctx = new StringBuilder();

        // Status counts
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (IncidentStatus st : IncidentStatus.values()) {
            statusCounts.put(st.name(), incidentRepository.countByStatus(st));
        }
        ctx.append("Status counts: ").append(statusCounts).append('\n');

        // Category counts (hotspots)
        Map<String, Long> catCounts = new LinkedHashMap<>();
        for (IncidentCategory c : IncidentCategory.values()) {
            long n = incidentRepository.countByCategory(c);
            if (n > 0) catCounts.put(c.name(), n);
        }
        ctx.append("Category counts: ").append(catCounts).append('\n');
        d.categoryHotspots = catCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + " (" + e.getValue() + " incidents)")
                .collect(Collectors.toList());

        // Priority counts
        Map<String, Long> prCounts = new LinkedHashMap<>();
        for (IncidentPriority p : IncidentPriority.values()) {
            prCounts.put(p.name(), incidentRepository.countByPriority(p));
        }
        ctx.append("Priority counts: ").append(prCounts).append('\n');

        LocalDateTime now = LocalDateTime.now();
        long overdue = incidentRepository.countOverdue(now);
        ctx.append("Overdue active incidents: ").append(overdue).append('\n');

        // Active incidents for SLA risk, workload, needs-attention
        List<Incident> active = incidentRepository
                .findByStatusIn(ACTIVE_STATUSES, org.springframework.data.domain.PageRequest.of(0, 500))
                .getContent();

        // SLA risk
        List<String> slaRisks = new ArrayList<>();
        for (Incident i : active) {
            if (i.getDueDate() == null) continue;
            long hrs = Duration.between(now, i.getDueDate()).toHours();
            if (i.getDueDate().isBefore(now)) {
                slaRisks.add("INC-" + i.getId() + " OVERDUE by " + Math.abs(hrs) + "h — " + AiTextUtils.truncate(i.getTitle(), 60));
            } else if (hrs <= 8) {
                slaRisks.add("INC-" + i.getId() + " due in " + hrs + "h — " + AiTextUtils.truncate(i.getTitle(), 60));
            }
        }
        slaRisks = slaRisks.stream().limit(8).collect(Collectors.toList());
        d.slaRisks = slaRisks.isEmpty() ? List.of("No incidents are near or past their SLA deadline.") : slaRisks;
        ctx.append("SLA at-risk: ").append(d.slaRisks).append('\n');

        // Workload by assignee
        Map<Long, Integer> load = new LinkedHashMap<>();
        for (Incident i : active) {
            if (i.getAssignedTo() == null) continue;
            int w = i.getPriority() != null ? i.getPriority().getWorkloadWeight() : 1;
            load.merge(i.getAssignedTo(), w, Integer::sum);
        }
        d.workload = load.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> resolveUserName(e.getKey()) + " — workload score " + e.getValue())
                .collect(Collectors.toList());
        if (d.workload.isEmpty()) d.workload = List.of("No active assignments.");
        ctx.append("Top workloads: ").append(d.workload).append('\n');

        // Needs attention: critical/high active or escalated
        d.needsAttention = active.stream()
                .filter(i -> i.getPriority() == IncidentPriority.CRITICAL
                        || i.getPriority() == IncidentPriority.HIGH
                        || i.getStatus() == IncidentStatus.ESCALATED)
                .sorted(Comparator.comparingInt((Incident i) ->
                        i.getPriority() != null ? i.getPriority().getWorkloadWeight() : 0).reversed())
                .limit(6)
                .map(i -> "INC-" + i.getId() + " [" + i.getPriority() + "/" + i.getStatus() + "] "
                        + AiTextUtils.truncate(i.getTitle(), 60))
                .collect(Collectors.toList());
        if (d.needsAttention.isEmpty()) d.needsAttention = List.of("No high-priority or escalated incidents right now.");
        ctx.append("Needs attention: ").append(d.needsAttention).append('\n');

        long activeTotal = active.size();
        d.summaryLine = activeTotal + " active incident(s); " + overdue + " overdue; "
                + (d.categoryHotspots.isEmpty() ? "no dominant category" : "top category " + d.categoryHotspots.get(0)) + ".";
        d.context = ctx.toString();
        return d;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Similarity helpers
    // ═══════════════════════════════════════════════════════════════════════

    private List<SimilarIncidentResponse> safeFindSimilar(Incident inc, Long excludeId) {
        return safeFindSimilarRaw(inc.getTitle(), inc.getDescription(), inc.getCategory(), excludeId);
    }

    private List<SimilarIncidentResponse> safeFindSimilarRaw(String title, String desc, IncidentCategory cat, Long excludeId) {
        try {
            return similarityService.findSimilar(nz(title), nz(desc), cat, excludeId);
        } catch (Exception e) {
            log.warn("[AI] Similarity lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Incident> resolveSimilarEntities(List<SimilarIncidentResponse> scored) {
        List<Incident> out = new ArrayList<>();
        for (SimilarIncidentResponse s : scored) {
            incidentRepository.findById(s.getIncidentId())
                    .filter(i -> (i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED))
                    .ifPresent(out::add);
            if (out.size() >= SIMILAR_LIMIT) break;
        }
        return out;
    }

    private List<Incident> gatherSimilarResolved(Incident inc) {
        return resolveSimilarEntities(safeFindSimilar(inc, inc.getId()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Parsing + validation helpers
    // ═══════════════════════════════════════════════════════════════════════

    private JsonNode parseJson(String content) {
        String json = AiTextUtils.extractJsonObject(content);
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            log.warn("[AI] Failed to parse model JSON: {}", e.getMessage());
            return null;
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return fallback;
        String s = v.asText("").trim();
        return s.isEmpty() ? fallback : s;
    }

    private Double number(JsonNode node, String field, Double fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isNumber()) return fallback;
        return Math.max(0, Math.min(100, v.asDouble()));
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode arr = node.get(field);
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(n -> {
                String s = n.asText("").trim();
                if (!s.isEmpty()) out.add(s);
            });
        }
        return out;
    }

    private IncidentCategory validCategory(String raw) {
        if (raw != null) {
            for (IncidentCategory c : IncidentCategory.values()) {
                if (c.name().equalsIgnoreCase(raw.trim())) return c;
            }
        }
        return IncidentCategory.APPLICATION_SUPPORT;
    }

    private IncidentPriority validPriority(String raw) {
        if (raw != null) {
            for (IncidentPriority p : IncidentPriority.values()) {
                if (p.name().equalsIgnoreCase(raw.trim())) return p;
            }
        }
        return IncidentPriority.MEDIUM;
    }

    private com.incidentiq.enums.Complexity validComplexity(String raw) {
        if (raw != null) {
            for (com.incidentiq.enums.Complexity c : com.incidentiq.enums.Complexity.values()) {
                if (c.name().equalsIgnoreCase(raw.trim())) return c;
            }
        }
        return com.incidentiq.enums.Complexity.MEDIUM;
    }

    /**
     * Deterministic complexity estimate used when the LLM is unavailable. Scores
     * difficulty signals in the text plus a small per-category baseline, then maps
     * the score to a band with a rough confidence.
     */
    private ComplexityGuess guessComplexity(String text, IncidentCategory category) {
        String t = nz(text).toLowerCase();
        int score = 0;
        // Catastrophic scale — title alone is enough (all users / platform-wide outage)
        if (t.matches(".*(all users|entire platform|platform.wide|company.wide|full outage|complete outage|all services|everyone affected|nobody can).*")) score += 3;
        // Critical security breach signals (stronger than generic "security" keyword)
        if (t.matches(".*(exposed to public|exposed to internet|unauthorized access|data breach|credential leak|privilege escalat|data exfil).*")) score += 3;
        // Intermittent / hard-to-reproduce bugs
        if (t.matches(".*(intermittent|sometimes|random|cannot reproduce|hard to reproduce|peak|under load|race condition|deadlock|memory leak|corruption).*")) score += 3;
        // Performance / scale investigation
        if (t.matches(".*(performance|latency|slow|tuning|optimi|scal).*")) score += 2;
        // Multi-system / distributed signals
        if (t.matches(".*(multiple|several|across|distributed|cluster|microservice|integration|third.?party|upstream|downstream).*")) score += 2;
        // Deep investigation needed
        if (t.matches(".*(root cause|investigat|analy|unknown|unclear|not sure|mysterious).*")) score += 2;
        // General security / data signals
        if (t.matches(".*(security|breach|vulnerab|exploit|migration|data loss|firewall|misconfigur).*")) score += 2;
        // Infrastructure deep-dive: replication, failover, sync
        if (t.matches(".*(replication lag|replication fail|replica|failover|sync.lag|cascade fail|data sync).*")) score += 2;
        // Production environment multiplier
        if (t.matches(".*(production|prod .*fail|critical path).*")) score += 1;
        // Simplicity signals pull it back down
        if (t.matches(".*(typo|label|cosmetic|rename|config flag|toggle|reset password|clear cache|simple|trivial|one.?line|irresponsive|unresponsive button|broken link|missing icon).*")) score -= 2;
        // Category baseline difficulty
        if (category == IncidentCategory.DATABASE || category == IncidentCategory.SECURITY
                || category == IncidentCategory.NETWORK || category == IncidentCategory.CLOUD) score += 1;
        if (category == IncidentCategory.FRONTEND || category == IncidentCategory.APPLICATION_SUPPORT) score -= 1;

        com.incidentiq.enums.Complexity level;
        if (score <= 0)      level = com.incidentiq.enums.Complexity.EASY;
        else if (score <= 2) level = com.incidentiq.enums.Complexity.MEDIUM;
        else if (score <= 4) level = com.incidentiq.enums.Complexity.HARD;
        else                 level = com.incidentiq.enums.Complexity.COMPLEX;

        // Confidence: stronger signal (further from band edges) → higher confidence.
        int confidence = Math.max(55, Math.min(85, 60 + Math.abs(score) * 4));
        String reason = switch (level) {
            case EASY -> "Looks like a well-understood, single-component fix.";
            case MEDIUM -> "Needs structured troubleshooting within one component.";
            case HARD -> "Signals deep investigation and specialist knowledge across systems.";
            case COMPLEX -> "Multiple difficulty signals — likely multi-step, multi-team analysis.";
        };
        return new ComplexityGuess(level, confidence, reason);
    }

    private record ComplexityGuess(com.incidentiq.enums.Complexity level, int confidence, String reason) {}

    // ═══════════════════════════════════════════════════════════════════════
    //  Heuristic building blocks
    // ═══════════════════════════════════════════════════════════════════════

    private IncidentCategory guessCategory(String text, IncidentCategory fallback) {
        String t = text.toLowerCase();
        if (t.matches(".*(database|postgres|sql|query|deadlock).*")) return IncidentCategory.DATABASE;
        if (t.matches(".*(login|auth|token|password|jwt|session|api|endpoint|backend|server error|500).*")) return IncidentCategory.BACKEND;
        if (t.matches(".*(ui|page|button|css|render|frontend|browser|screen).*")) return IncidentCategory.FRONTEND;
        if (t.matches(".*(network|dns|firewall|timeout|connection|latency|vpn).*")) return IncidentCategory.NETWORK;
        if (t.matches(".*(security|breach|vulnerab|malware|phishing|unauthorized).*")) return IncidentCategory.SECURITY;
        if (t.matches(".*(deploy|pipeline|docker|kubernetes|k8s|ci/cd|build).*")) return IncidentCategory.DEVOPS;
        if (t.matches(".*(cloud|aws|azure|gcp|s3|ec2|lambda).*")) return IncidentCategory.CLOUD;
        return fallback != null ? fallback : IncidentCategory.APPLICATION_SUPPORT;
    }

    private List<String> keywordTags(String text) {
        List<String> tags = new ArrayList<>();
        String t = text.toLowerCase();
        String[][] map = {
                {"login", "auth"}, {"auth", "authentication"}, {"database", "database"}, {"timeout", "timeout"},
                {"deploy", "deployment"}, {"api", "api"}, {"network", "network"}, {"performance", "performance"},
                {"crash", "outage"}, {"security", "security"}, {"slow", "performance"}
        };
        for (String[] pair : map) {
            if (t.contains(pair[0]) && !tags.contains(pair[1])) tags.add(pair[1]);
            if (tags.size() >= 5) break;
        }
        if (tags.isEmpty()) tags.add("general");
        return tags;
    }

    private String tidyTitle(String title) {
        String t = nz(title).trim();
        if (t.isEmpty()) return "Untitled incident";
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private List<String> splitToList(String text) {
        if (text == null || text.isBlank()) return new ArrayList<>();
        return java.util.Arrays.stream(text.split("\\r?\\n"))
                .map(s -> s.replaceFirst("^\\s*\\d+[.)]\\s*", "").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String renderKbMarkdown(String title, String problem, String rootCause,
                                    List<String> steps, List<String> prevention) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(nz(title)).append("\n\n");
        md.append("## Problem Statement\n").append(nz(problem)).append("\n\n");
        md.append("## Root Cause\n").append(nz(rootCause)).append("\n\n");
        md.append("## Resolution Steps\n");
        if (steps != null && !steps.isEmpty()) {
            int i = 1;
            for (String s : steps) md.append(i++).append(". ").append(s).append('\n');
        } else {
            md.append("_No steps recorded._\n");
        }
        md.append("\n## Prevention Tips\n");
        if (prevention != null && !prevention.isEmpty()) {
            for (String p : prevention) md.append("- ").append(p).append('\n');
        } else {
            md.append("_None._\n");
        }
        return md.toString();
    }

    private List<String> extractTrailingSuggestions(String answer) {
        if (answer == null) return List.of();
        List<String> out = new ArrayList<>();
        String[] lines = answer.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0 && out.size() < 3; i--) {
            String line = lines[i].trim();
            if (line.startsWith("- ")) {
                out.add(0, line.substring(2).trim());
            } else if (!line.isEmpty()) {
                break; // suggestions are a contiguous trailing block
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private String resolveUserName(Long userId) {
        if (userId == null) return "Unassigned";
        try {
            Map<String, Object> user = lbRestTemplate.getForObject("http://user-service/" + userId, Map.class);
            if (user != null) {
                String first = String.valueOf(user.getOrDefault("firstName", "")).trim();
                String last = String.valueOf(user.getOrDefault("lastName", "")).trim();
                String name = (first + " " + last).trim();
                if (!name.isEmpty()) return name;
                Object uname = user.get("username");
                if (uname != null) return uname.toString();
            }
        } catch (Exception e) {
            log.debug("[AI] Could not resolve user {}: {}", userId, e.getMessage());
        }
        return "User #" + userId;
    }

    private Incident loadIncident(Long id) {
        Optional<Incident> inc = (id == null) ? Optional.empty() : incidentRepository.findById(id);
        return inc.orElseThrow(() -> new com.incidentiq.exception.IncidentNotFoundException(
                "Incident not found: " + id));
    }

    private AiMeta meta(AiCompletion res) {
        return AiMeta.of(res.fromCache() ? "cache" : res.modelUsed(), res.fromCache());
    }

    private String degradeNotice(AiCompletion res) {
        if (!client.isConfigured()) return "AI not configured — showing heuristic suggestion.";
        return "AI unavailable (" + (res != null ? res.error() : "unknown") + ") — showing heuristic suggestion.";
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    /** Container for manager-insight aggregates + their deterministic bullet renderings. */
    private static final class ManagerData {
        String context = "";
        String summaryLine = "";
        List<String> slaRisks = new ArrayList<>();
        List<String> categoryHotspots = new ArrayList<>();
        List<String> workload = new ArrayList<>();
        List<String> needsAttention = new ArrayList<>();
    }
}
