package com.incidentiq.ai.client;

/**
 * Result of an OpenRouter completion attempt across the fallback chain.
 *
 * @param success    true if a model produced content
 * @param content    the model's text output (reasoning already stripped by the client)
 * @param modelUsed  the model id that actually answered (null on total failure)
 * @param attempts   how many model attempts were made
 * @param error      a human-readable failure reason when success is false
 * @param fromCache  true when served from the response cache (no network call)
 */
public record AiCompletion(
        boolean success,
        String content,
        String modelUsed,
        int attempts,
        String error,
        boolean fromCache
) {
    public static AiCompletion ok(String content, String modelUsed, int attempts) {
        return new AiCompletion(true, content, modelUsed, attempts, null, false);
    }

    public static AiCompletion cached(String content, String modelUsed) {
        return new AiCompletion(true, content, modelUsed, 0, null, true);
    }

    public static AiCompletion fail(String error, int attempts) {
        return new AiCompletion(false, null, null, attempts, error, false);
    }
}
