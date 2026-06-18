package com.incidentiq.scheduler;

import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Runs every 5 minutes to check for incidents approaching their SLA deadline.
 * Fires a warning notification when 75% or more of the SLA window has elapsed,
 * and marks the incident so the alert is only sent once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlaAlertScheduler {

    private static final int ALERT_THRESHOLD_PERCENT = 75;

    private final IncidentRepository incidentRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    @Transactional
    public void checkSlaWarnings() {
        LocalDateTime now = LocalDateTime.now();
        List<Incident> candidates = incidentRepository.findActiveWithDueDateAndNoAlert(now);

        int alerted = 0;
        for (Incident incident : candidates) {
            long totalMs = ChronoUnit.MILLIS.between(incident.getCreatedAt(), incident.getDueDate());
            if (totalMs <= 0) continue;

            long elapsedMs = ChronoUnit.MILLIS.between(incident.getCreatedAt(), now);
            int percentElapsed = (int) ((elapsedMs * 100L) / totalMs);

            if (percentElapsed >= ALERT_THRESHOLD_PERCENT) {
                sendSlaWarning(incident, percentElapsed);
                incident.setSlaAlertSent(true);
                incidentRepository.save(incident);
                alerted++;
            }
        }

        if (alerted > 0) {
            log.info("SLA pre-alert: sent warnings for {} incident(s)", alerted);
        }
    }

    private void sendSlaWarning(Incident incident, int percentElapsed) {
        if (incident.getAssignedTo() != null) {
            notificationService.notifySlaWarning(
                    incident.getAssignedTo(), incident.getId(), incident.getTitle(), percentElapsed);
        }
        if (incident.getCreatedBy() != null && !incident.getCreatedBy().equals(incident.getAssignedTo())) {
            notificationService.notifySlaWarning(
                    incident.getCreatedBy(), incident.getId(), incident.getTitle(), percentElapsed);
        }
        notificationService.notifyManagers(
                incident.getId(),
                incident.getTitle(),
                String.format("SLA Warning: Incident #%d ('%s') is at %d%% of its SLA window. Priority: %s",
                        incident.getId(), incident.getTitle(), percentElapsed, incident.getPriority()));
    }
}
