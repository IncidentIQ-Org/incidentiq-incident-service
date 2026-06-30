package com.incidentiq.util;

import com.incidentiq.enums.Complexity;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.model.SlaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final com.incidentiq.repository.SlaConfigRepository slaConfigRepository;

    public DataInitializer(com.incidentiq.repository.SlaConfigRepository slaConfigRepository) {
        this.slaConfigRepository = slaConfigRepository;
    }

    /**
     * Default Priority × Complexity SLA matrix (target hours). Admins can override
     * any cell via the SLA Configuration Center; this only seeds an empty table.
     * Kept in sync with Flyway migration V4__sla_complexity_matrix.sql.
     */
    private static final Map<IncidentPriority, Map<Complexity, Integer>> DEFAULT_MATRIX = Map.of(
        IncidentPriority.CRITICAL, Map.of(Complexity.EASY, 1, Complexity.MEDIUM, 2,  Complexity.HARD, 4,  Complexity.COMPLEX, 6),
        IncidentPriority.HIGH,     Map.of(Complexity.EASY, 2, Complexity.MEDIUM, 4,  Complexity.HARD, 8,  Complexity.COMPLEX, 12),
        IncidentPriority.MEDIUM,   Map.of(Complexity.EASY, 4, Complexity.MEDIUM, 8,  Complexity.HARD, 16, Complexity.COMPLEX, 24),
        IncidentPriority.LOW,      Map.of(Complexity.EASY, 8, Complexity.MEDIUM, 16, Complexity.HARD, 24, Complexity.COMPLEX, 48)
    );

    @Override
    public void run(String... args) {
        if (slaConfigRepository.count() == 0) {
            log.info("Seeding Priority x Complexity SLA matrix (16 cells)...");
            List<SlaConfig> rows = new ArrayList<>();
            DEFAULT_MATRIX.forEach((priority, byComplexity) ->
                byComplexity.forEach((complexity, hours) ->
                    rows.add(SlaConfig.builder()
                            .priority(priority)
                            .complexity(complexity)
                            .targetHours(hours)
                            .build())));
            slaConfigRepository.saveAll(rows);
        }
        log.info("Incident seeding disabled — incidents are created only by authenticated users");
    }
}
