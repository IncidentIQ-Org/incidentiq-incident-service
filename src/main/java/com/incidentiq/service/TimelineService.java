package com.incidentiq.service;

import com.incidentiq.model.IncidentTimeline;
import com.incidentiq.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TimelineService {

    private final TimelineRepository timelineRepository;

    public void logEvent(Long incidentId, String eventType, String description, Long performedBy) {
        IncidentTimeline timeline = IncidentTimeline.builder()
                .incidentId(incidentId)
                .eventType(eventType)
                .description(description)
                .performedBy(performedBy)
                .build();
        timelineRepository.save(timeline);
    }

    public List<IncidentTimeline> getTimeline(Long incidentId) {
        return timelineRepository.findByIncidentIdOrderByTimestampAsc(incidentId);
    }
}
