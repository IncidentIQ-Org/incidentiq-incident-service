package com.incidentiq.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payloads for the AI Copilot endpoints.
 *
 * Every response carries an {@link AiMeta} so the UI can show which model answered, whether
 * the answer came from cache, and whether the Copilot fell back to a deterministic heuristic
 * (e.g. when OpenRouter is unreachable). Nothing here ever hard-fails the page.
 */
public final class AiResponses {

    private AiResponses() {}

    /** Shared provenance/quality metadata attached to every AI response. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiMeta {
        /** Model id that answered, "cache", or "heuristic" when degraded. */
        private String model;
        private boolean fromCache;
        /** True when served by the deterministic fallback rather than an LLM. */
        private boolean degraded;
        /** User-facing note explaining a degraded/fallback answer (nullable). */
        private String notice;

        public static AiMeta of(String model, boolean fromCache) {
            return AiMeta.builder().model(model).fromCache(fromCache).degraded(false).build();
        }

        public static AiMeta heuristic(String notice) {
            return AiMeta.builder().model("heuristic").degraded(true).notice(notice).build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncidentAssist {
        private String improvedTitle;
        private String improvedDescription;
        private String category;            // suggested IncidentCategory
        private String priority;            // suggested IncidentPriority
        private String complexity;          // suggested Complexity (EASY/MEDIUM/HARD/COMPLEX)
        private Integer complexityConfidence; // 0-100 confidence in the complexity suggestion
        private String complexityReason;    // short "why" for the complexity call
        private List<String> tags;
        private String rationale;           // one-line "why" for the suggestion
        private AiMeta meta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionAssist {
        private String rootCause;
        private String resolutionSteps;     // newline-separated steps, ready for the textarea
        private String resolutionSummary;
        private AiMeta meta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coaching {
        private List<String> troubleshootingSteps;
        private List<String> nextActions;
        private List<String> commonFixes;
        private String guidance;
        private Double confidence;          // 0-100
        private List<SimilarRef> basedOn;
        private AiMeta meta;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class SimilarRef {
            private Long incidentId;
            private String title;
            private Integer similarityScore;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KbArticle {
        private String title;
        private String problemStatement;
        private String rootCause;
        private List<String> resolutionSteps;
        private List<String> preventionTips;
        /** Rendered markdown version for one-click copy/export. */
        private String markdown;
        private AiMeta meta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManagerInsights {
        private String summary;
        private List<String> slaRisks;
        private List<String> categoryHotspots;
        private List<String> workloadObservations;
        private List<String> needsAttention;
        private AiMeta meta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarSummary {
        private String summary;
        private Double confidence;          // 0-100
        private int matchesConsidered;
        private List<Coaching.SimilarRef> references;
        private AiMeta meta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ask {
        private String answer;              // markdown
        private List<String> suggestions;   // optional follow-up prompts
        private AiMeta meta;
    }
}
