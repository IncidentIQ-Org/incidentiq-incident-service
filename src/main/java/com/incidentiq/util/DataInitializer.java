package com.incidentiq.util;

import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the database with sample incidents on application startup.
 * Only runs if the incidents table is empty (idempotent).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final IncidentRepository incidentRepository;
    private final com.incidentiq.repository.SlaConfigRepository slaConfigRepository;

    public DataInitializer(IncidentRepository incidentRepository, com.incidentiq.repository.SlaConfigRepository slaConfigRepository) {
        this.incidentRepository = incidentRepository;
        this.slaConfigRepository = slaConfigRepository;
    }

    @Override
    public void run(String... args) {
        if (slaConfigRepository.count() == 0) {
            log.info("Seeding SLA Configs...");
            slaConfigRepository.saveAll(List.of(
                com.incidentiq.model.SlaConfig.builder().priority(IncidentPriority.CRITICAL).targetHours(3).build(),
                com.incidentiq.model.SlaConfig.builder().priority(IncidentPriority.HIGH).targetHours(12).build(),
                com.incidentiq.model.SlaConfig.builder().priority(IncidentPriority.MEDIUM).targetHours(24).build(),
                com.incidentiq.model.SlaConfig.builder().priority(IncidentPriority.LOW).targetHours(48).build()
            ));
        }

        // Seeding disabled: incidents are created exclusively by real users.
        log.info("Seed data disabled — incidents are created only by authenticated users");
        if (incidentRepository.count() > 0) {
            log.info("Incidents already seeded.");
            return;
        }

        List<Incident> seedIncidents = List.of(
                Incident.builder()
                        .title("Production Server Unresponsive")
                        .description("Main production server at us-east-1 is not responding to health checks. All services affected.")
                        .category(IncidentCategory.CLOUD)
                        .priority(IncidentPriority.HIGH)
                        .status(IncidentStatus.OPEN)
                        .createdBy(1L)
                        .build(),

                Incident.builder()
                        .title("Login Page CSS Broken on Safari")
                        .description("Users on Safari 17+ report that the login page layout is misaligned and submit button is not clickable.")
                        .category(IncidentCategory.FRONTEND)
                        .priority(IncidentPriority.MEDIUM)
                        .status(IncidentStatus.OPEN)
                        .createdBy(2L)
                        .build(),

                Incident.builder()
                        .title("Order API Returning 500 Errors")
                        .description("The /api/orders endpoint intermittently returns 500 errors under load. Suspected connection pool exhaustion.")
                        .category(IncidentCategory.BACKEND)
                        .priority(IncidentPriority.HIGH)
                        .status(IncidentStatus.IN_PROGRESS)
                        .createdBy(1L)
                        .assignedTo(4L) // tech_backend
                        .build(),

                Incident.builder()
                        .title("Database Replication Lag Exceeds 30s")
                        .description("Read replica in eu-west-1 showing replication lag above 30 seconds during peak hours.")
                        .category(IncidentCategory.DATABASE)
                        .priority(IncidentPriority.MEDIUM)
                        .status(IncidentStatus.OPEN)
                        .createdBy(3L)
                        .build(),

                Incident.builder()
                        .title("VPN Connectivity Issues for Remote Team")
                        .description("Multiple remote employees unable to connect to corporate VPN since network upgrade last night.")
                        .category(IncidentCategory.NETWORK)
                        .priority(IncidentPriority.LOW)
                        .status(IncidentStatus.RESOLVED)
                        .createdBy(3L)
                        .assignedTo(8L) // tech_network
                        .build()
        );

        incidentRepository.saveAll(seedIncidents);
        log.info("Seeded {} sample incidents into the database", seedIncidents.size());
    }
}
