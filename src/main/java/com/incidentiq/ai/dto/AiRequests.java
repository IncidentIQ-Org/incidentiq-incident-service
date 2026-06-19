package com.incidentiq.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request payloads for the AI Copilot endpoints, grouped as nested types for tidiness.
 */
public final class AiRequests {

    private AiRequests() {}

    /** Incident Creation Assistant — refine a rough draft. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncidentAssist {
        private String title;
        private String description;
        /** Optional current category hint (FRONTEND, DATABASE, ...). */
        private String category;
    }

    /** Resolution Assistant — draft root cause / steps / summary. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolutionAssist {
        /** When set, the incident is loaded server-side for full context. */
        private Long incidentId;
        /** Optional free-form notes the technician has already jotted down. */
        private String notes;
    }

    /** Summarize a set of historically similar resolutions. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarSummary {
        private String title;
        private String description;
        private String category;
    }

    /** Scoped Copilot chat turn. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ask {
        private String message;
        /** Current app route, used to make answers context-aware (e.g. "/incidents/42"). */
        private String page;
        /** Optional incident in focus. */
        private Long incidentId;
        /** Prior turns for short conversational memory. */
        private List<Turn> history;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Turn {
            private String role;   // "user" | "assistant"
            private String content;
        }
    }
}
