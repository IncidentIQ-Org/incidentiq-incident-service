package com.incidentiq.ai.util;

import java.util.regex.Pattern;

/**
 * Helpers for cleaning up raw LLM output.
 *
 * Reasoning models (e.g. DeepSeek R1) often wrap their internal monologue in {@code <think>}
 * tags and may fence JSON in ```json blocks. These helpers strip that noise so downstream
 * parsing is reliable regardless of which fallback model answered.
 */
public final class AiTextUtils {

    private static final Pattern THINK_BLOCK =
            Pattern.compile("<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_FENCE =
            Pattern.compile("```(?:json|markdown|md)?\\s*", Pattern.CASE_INSENSITIVE);

    private AiTextUtils() {}

    /** Remove reasoning blocks and surrounding whitespace. */
    public static String stripReasoning(String raw) {
        if (raw == null) return "";
        String cleaned = THINK_BLOCK.matcher(raw).replaceAll("");
        // Some models leave a dangling "</think>" if the opening tag was truncated.
        cleaned = cleaned.replaceAll("(?is)^.*?</think>", "");
        return cleaned.trim();
    }

    /** Strip reasoning + markdown code fences, returning clean prose/markdown. */
    public static String cleanText(String raw) {
        String cleaned = stripReasoning(raw);
        cleaned = CODE_FENCE.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("```", "");
        return cleaned.trim();
    }

    /**
     * Best-effort extraction of a single JSON object from arbitrary model output.
     * Returns the substring from the first '{' to its matching '}' (brace-balanced),
     * or null if none is found.
     */
    public static String extractJsonObject(String raw) {
        if (raw == null) return null;
        String cleaned = stripReasoning(raw);
        int start = cleaned.indexOf('{');
        if (start < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return cleaned.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** Truncate user-supplied context so prompts stay within a sensible token budget. */
    public static String truncate(String text, int maxChars) {
        if (text == null) return "";
        String t = text.strip();
        if (t.length() <= maxChars) return t;
        return t.substring(0, maxChars) + "…";
    }
}
