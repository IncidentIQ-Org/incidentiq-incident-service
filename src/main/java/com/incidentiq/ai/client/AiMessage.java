package com.incidentiq.ai.client;

/**
 * A single chat message in an OpenRouter request.
 * role is one of: "system", "user", "assistant".
 */
public record AiMessage(String role, String content) {

    public static AiMessage system(String content) {
        return new AiMessage("system", content);
    }

    public static AiMessage user(String content) {
        return new AiMessage("user", content);
    }
}
