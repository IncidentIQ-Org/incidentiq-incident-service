package com.incidentiq.ai.health;

import com.incidentiq.ai.config.AiProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight model health monitor (circuit-breaker-lite).
 *
 * Tracks consecutive failures per model. Once a model crosses the failure threshold it is
 * marked unhealthy and skipped for a cooldown window, so the fallback chain doesn't keep
 * wasting time on a model that's currently rate-limited or down. A single success resets it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModelHealthRegistry {

    private final AiProperties props;
    private final Map<String, ModelStat> stats = new ConcurrentHashMap<>();

    public boolean isAvailable(String model) {
        ModelStat s = stats.get(model);
        if (s == null) return true;
        if (s.unhealthyUntilEpochMs.get() == 0) return true;
        if (Instant.now().toEpochMilli() >= s.unhealthyUntilEpochMs.get()) {
            // Cooldown elapsed — give it another chance.
            s.unhealthyUntilEpochMs.set(0);
            log.info("[AI-health] Model '{}' cooldown elapsed; re-enabling.", model);
            return true;
        }
        return false;
    }

    public void recordSuccess(String model) {
        ModelStat s = stats.computeIfAbsent(model, k -> new ModelStat());
        s.consecutiveFailures.set(0);
        s.unhealthyUntilEpochMs.set(0);
        s.totalSuccess.incrementAndGet();
        s.lastUsedEpochMs.set(Instant.now().toEpochMilli());
    }

    public void recordFailure(String model, String reason) {
        ModelStat s = stats.computeIfAbsent(model, k -> new ModelStat());
        s.totalFailure.incrementAndGet();
        s.lastError = reason;
        s.lastUsedEpochMs.set(Instant.now().toEpochMilli());
        int fails = s.consecutiveFailures.incrementAndGet();
        if (fails >= props.getFailureThreshold()) {
            long until = Instant.now().toEpochMilli() + props.getUnhealthyCooldownSeconds() * 1000L;
            s.unhealthyUntilEpochMs.set(until);
            log.warn("[AI-health] Model '{}' marked UNHEALTHY after {} consecutive failures (last: {}). Cooling down {}s.",
                    model, fails, reason, props.getUnhealthyCooldownSeconds());
        }
    }

    /** Snapshot for the admin health endpoint. */
    public List<ModelHealth> snapshot() {
        List<ModelHealth> out = new ArrayList<>();
        long now = Instant.now().toEpochMilli();
        for (String model : props.getModels()) {
            ModelStat s = stats.get(model);
            if (s == null) {
                out.add(new ModelHealth(model, true, 0, 0, 0, null, null));
                continue;
            }
            boolean healthy = s.unhealthyUntilEpochMs.get() == 0 || now >= s.unhealthyUntilEpochMs.get();
            Long cooldownRemaining = healthy ? null : (s.unhealthyUntilEpochMs.get() - now) / 1000L;
            out.add(new ModelHealth(
                    model, healthy,
                    s.totalSuccess.get(), s.totalFailure.get(),
                    s.consecutiveFailures.get(), s.lastError, cooldownRemaining));
        }
        return out;
    }

    private static final class ModelStat {
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicLong unhealthyUntilEpochMs = new AtomicLong(0);
        final AtomicLong totalSuccess = new AtomicLong(0);
        final AtomicLong totalFailure = new AtomicLong(0);
        final AtomicLong lastUsedEpochMs = new AtomicLong(0);
        volatile String lastError;
    }

    @Getter
    public static final class ModelHealth {
        private final String model;
        private final boolean healthy;
        private final long totalSuccess;
        private final long totalFailure;
        private final int consecutiveFailures;
        private final String lastError;
        private final Long cooldownSecondsRemaining;

        public ModelHealth(String model, boolean healthy, long totalSuccess, long totalFailure,
                           int consecutiveFailures, String lastError, Long cooldownSecondsRemaining) {
            this.model = model;
            this.healthy = healthy;
            this.totalSuccess = totalSuccess;
            this.totalFailure = totalFailure;
            this.consecutiveFailures = consecutiveFailures;
            this.lastError = lastError;
            this.cooldownSecondsRemaining = cooldownSecondsRemaining;
        }
    }
}
