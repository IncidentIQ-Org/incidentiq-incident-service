package com.incidentiq.ai.cache;

import com.incidentiq.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tiny in-memory TTL cache for AI responses.
 *
 * Deterministic, low-temperature prompts (e.g. "improve this title") produce stable output,
 * so caching identical requests avoids repeat OpenRouter calls — a direct cost saving and a
 * latency win. Intentionally dependency-free (no Caffeine) to keep the POC build lightweight.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiResponseCache {

    private final AiProperties props;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    /** Build a stable cache key from a feature name + the full prompt payload. */
    public String key(String feature, String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((feature + "::" + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(feature).append(':');
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", digest[i]));
            return sb.toString();
        } catch (Exception e) {
            return feature + ":" + Integer.toHexString((feature + payload).hashCode());
        }
    }

    public String get(String key) {
        Entry e = store.get(key);
        if (e == null) {
            misses.incrementAndGet();
            return null;
        }
        if (Instant.now().toEpochMilli() > e.expiresAtEpochMs) {
            store.remove(key);
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        log.debug("[AI-cache] HIT {}", key);
        return e.value;
    }

    public void put(String key, String value) {
        // Opportunistic cleanup so the map can't grow unbounded in a long-running POC.
        if (store.size() > 500) evictExpired();
        long ttl = props.getCacheTtlSeconds() * 1000L;
        store.put(key, new Entry(value, Instant.now().toEpochMilli() + ttl));
    }

    public void clear() {
        store.clear();
    }

    public Map<String, Long> stats() {
        return Map.of("entries", (long) store.size(), "hits", hits.get(), "misses", misses.get());
    }

    private void evictExpired() {
        long now = Instant.now().toEpochMilli();
        store.entrySet().removeIf(en -> now > en.getValue().expiresAtEpochMs);
    }

    private record Entry(String value, long expiresAtEpochMs) {}
}
