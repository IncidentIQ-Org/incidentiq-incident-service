package com.incidentiq.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Central configuration for the IncidentIQ AI Copilot.
 *
 * Every value is sourced from environment variables (see application.yml), so models,
 * the OpenRouter key, timeouts and the fallback order are all configurable without
 * a rebuild and are never hard-coded in the UI.
 *
 * Example env:
 *   OPENROUTER_API_KEY=sk-or-...
 *   OPENROUTER_MODELS=openai/gpt-oss-20b:free,google/gemma-4-31b-it:free,...
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** Master switch — when false the Copilot serves deterministic heuristic fallbacks only. */
    private boolean enabled = true;

    /** OpenRouter API key. Blank => AI calls are skipped and heuristic fallbacks are used. */
    private String apiKey = "";

    /** OpenRouter-compatible base URL (no trailing slash). */
    private String baseUrl = "https://openrouter.ai/api/v1";

    /**
     * Ordered model fallback list. The client tries each in turn; the first that responds
     * wins. Lightweight free models are listed first for cost efficiency.
     */
    private List<String> models = new ArrayList<>(List.of(
            "openai/gpt-oss-20b:free",
            "google/gemma-4-31b-it:free",
            "nvidia/nemotron-nano-9b-v2:free",
            "nvidia/nemotron-3-nano-30b-a3b:free",
            "openai/gpt-oss-120b:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "qwen/qwen3-next-80b-a3b-instruct:free"
    ));

    /** Optional ranking headers OpenRouter uses to attribute traffic. */
    private String referer = "https://incidentiq.app";
    private String appTitle = "IncidentIQ Copilot";

    /** TCP connect timeout (ms). */
    private int connectTimeoutMs = 8000;

    /** Socket read timeout (ms) — reasoning models can be slow, so allow headroom. */
    private int readTimeoutMs = 45000;

    /** Retry attempts on the SAME model before falling through to the next model. */
    private int maxRetriesPerModel = 1;

    /** Sampling temperature — low for consistent, structured operational output. */
    private double temperature = 0.3;

    /** Hard cap on generated tokens per request (cost control). */
    private int maxTokens = 900;

    /** How long a successful response stays cached (cost control / response reuse). */
    private long cacheTtlSeconds = 900;

    /** Cooldown before a model marked unhealthy is retried. */
    private long unhealthyCooldownSeconds = 120;

    /** Consecutive failures before a model is flagged unhealthy and skipped during cooldown. */
    private int failureThreshold = 3;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
