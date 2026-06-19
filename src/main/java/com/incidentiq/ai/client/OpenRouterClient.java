package com.incidentiq.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentiq.ai.cache.AiResponseCache;
import com.incidentiq.ai.config.AiProperties;
import com.incidentiq.ai.health.ModelHealthRegistry;
import com.incidentiq.ai.util.AiTextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single gateway to OpenRouter for the whole application — the "centralized AI service layer".
 *
 * Responsibilities:
 *   • Model fallback   — walks {@link AiProperties#getModels()} in order until one answers.
 *   • Retry            — retries the same model up to maxRetriesPerModel before falling through.
 *   • Timeout handling — uses the dedicated aiRestTemplate (connect/read timeouts) and treats
 *                        a timeout like any other failure that advances the fallback chain.
 *   • Health           — records success/failure with {@link ModelHealthRegistry} and skips
 *                        models that are currently in cooldown.
 *   • Caching          — identical (feature + prompt) requests are served from {@link AiResponseCache}.
 *
 * It never throws on an LLM failure: callers get an {@link AiCompletion} and decide how to degrade.
 */
@Component
@Slf4j
public class OpenRouterClient {

    private final RestTemplate http;
    private final AiProperties props;
    private final ModelHealthRegistry health;
    private final AiResponseCache cache;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenRouterClient(@Qualifier("aiRestTemplate") RestTemplate http,
                            AiProperties props,
                            ModelHealthRegistry health,
                            AiResponseCache cache) {
        this.http = http;
        this.props = props;
        this.health = health;
        this.cache = cache;
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    /**
     * Run a chat completion, walking the configured model fallback chain.
     *
     * @param feature   short label used for caching + logging (e.g. "incident-assist")
     * @param messages  system + user messages
     * @param cacheable whether an identical request may be served from cache
     */
    public AiCompletion complete(String feature, List<AiMessage> messages, boolean cacheable) {
        if (!props.isConfigured()) {
            return AiCompletion.fail("AI is not configured (missing OPENROUTER_API_KEY or disabled).", 0);
        }

        String cacheKey = cacheable ? cache.key(feature, serializeForKey(messages)) : null;
        if (cacheKey != null) {
            String hit = cache.get(cacheKey);
            if (hit != null) return AiCompletion.cached(hit, "cache");
        }

        int attempts = 0;
        String lastError = "No models configured.";

        for (String model : props.getModels()) {
            if (model == null || model.isBlank()) continue;
            if (!health.isAvailable(model)) {
                log.debug("[AI] Skipping '{}' — in cooldown.", model);
                continue;
            }

            int tries = Math.max(1, props.getMaxRetriesPerModel() + 1);
            for (int attempt = 1; attempt <= tries; attempt++) {
                attempts++;
                try {
                    String content = callModel(model, messages);
                    if (content == null || content.isBlank()) {
                        lastError = "Empty response from " + model;
                        health.recordFailure(model, "empty response");
                        continue;
                    }
                    health.recordSuccess(model);
                    log.info("[AI] feature='{}' answered by '{}' (attempt {} of chain).", feature, model, attempts);
                    if (cacheKey != null) cache.put(cacheKey, content);
                    return AiCompletion.ok(content, model, attempts);
                } catch (RetryableModelException e) {
                    lastError = e.getMessage();
                    health.recordFailure(model, e.getMessage());
                    log.warn("[AI] '{}' attempt {}/{} failed: {}", model, attempt, tries, e.getMessage());
                    // loop retries the same model
                } catch (Exception e) {
                    lastError = e.getMessage();
                    health.recordFailure(model, e.getMessage());
                    log.warn("[AI] '{}' failed (non-retryable): {}", model, e.getMessage());
                    break; // fall through to the next model
                }
            }
        }

        log.error("[AI] feature='{}' exhausted all {} model attempts. Last error: {}", feature, attempts, lastError);
        return AiCompletion.fail(lastError, attempts);
    }

    // ── HTTP call to one model ───────────────────────────────────────────────

    private String callModel(String model, List<AiMessage> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getApiKey());
        // OpenRouter attribution headers (optional but recommended).
        if (props.getReferer() != null) headers.set("HTTP-Referer", props.getReferer());
        if (props.getAppTitle() != null) headers.set("X-Title", props.getAppTitle());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());
        List<Map<String, String>> msgs = new ArrayList<>();
        for (AiMessage m : messages) {
            msgs.add(Map.of("role", m.role(), "content", m.content()));
        }
        body.put("messages", msgs);

        String url = props.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        try {
            ResponseEntity<String> resp =
                    http.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return parseContent(resp.getBody());
        } catch (ResourceAccessException e) {
            // Connect/read timeout or network error — worth retrying / falling through.
            throw new RetryableModelException("timeout/network: " + rootMessage(e));
        } catch (HttpServerErrorException e) {
            // 5xx — provider-side, retryable.
            throw new RetryableModelException("HTTP " + e.getStatusCode().value());
        } catch (HttpClientErrorException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RetryableModelException("rate-limited (429)");
            }
            // 400/401/403/404 — config/model problem; don't retry the same model.
            throw new RuntimeException("HTTP " + e.getStatusCode().value() + " " + AiTextUtils.truncate(e.getResponseBodyAsString(), 180));
        }
    }

    private String parseContent(String json) {
        if (json == null) return null;
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode err = root.get("error");
            if (err != null && !err.isNull()) {
                String msg = err.path("message").asText("provider error");
                throw new RetryableModelException("provider error: " + AiTextUtils.truncate(msg, 160));
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) return null;
            JsonNode message = choices.get(0).path("message");
            String content = message.path("content").asText("");
            // Some providers stream reasoning into a separate field; prefer visible content.
            if (content.isBlank()) {
                content = message.path("reasoning").asText("");
            }
            return AiTextUtils.stripReasoning(content);
        } catch (RetryableModelException e) {
            throw e;
        } catch (Exception e) {
            throw new RetryableModelException("unparseable response: " + e.getMessage());
        }
    }

    private String serializeForKey(List<AiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiMessage m : messages) sb.append(m.role()).append('|').append(m.content()).append('\n');
        return sb.toString();
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }

    /** Marks a failure that should retry the same model before advancing the chain. */
    private static final class RetryableModelException extends RuntimeException {
        RetryableModelException(String msg) {
            super(msg);
        }
    }
}
