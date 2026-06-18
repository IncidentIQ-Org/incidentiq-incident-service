package com.incidentiq.service.impl;

import com.incidentiq.model.IncidentTimeline;
import com.incidentiq.repository.TimelineRepository;
import com.incidentiq.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineServiceImpl implements TimelineService {

    private final TimelineRepository timelineRepository;

    @Override
    public void logEvent(Long incidentId, String eventType, String description, Long performedBy) {
        log.info("Logging timeline event for incident {}: {} - {}", incidentId, eventType, description);
        
        IncidentTimeline timeline = IncidentTimeline.builder()
                .incidentId(incidentId)
                .eventType(eventType)
                .description(description)
                .performedBy(performedBy)
                .build();
        
        timelineRepository.save(timeline);
        log.debug("Timeline event saved successfully for incident {}", incidentId);
    }

    @Override
    public List<IncidentTimeline> getTimeline(Long incidentId) {
        log.info("Fetching timeline for incident {}", incidentId);
        return timelineRepository.findByIncidentIdOrderByTimestampAsc(incidentId);
    }
}
