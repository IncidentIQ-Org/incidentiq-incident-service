package com.incidentiq.service.impl;

import com.incidentiq.dto.request.WarRoomRequest;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.WarRoomStatus;
import com.incidentiq.model.Incident;
import com.incidentiq.model.WarRoom;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.repository.WarRoomRepository;
import com.incidentiq.service.AuditService;
import com.incidentiq.service.TimelineService;
import com.incidentiq.service.WarRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarRoomServiceImpl implements WarRoomService {

    private final WarRoomRepository warRoomRepository;
    private final IncidentRepository incidentRepository;
    private final TimelineService timelineService;
    private final AuditService auditService;

    @Override
    @Scheduled(fixedRate = 600_000)
    @Transactional
    public void autoTriggerWarRoom() {
        long criticalCount = incidentRepository.countRecentCriticalIncidents(LocalDateTime.now().minusHours(1));

        if (criticalCount >= 3) {
            boolean alreadyActive = !warRoomRepository.findByStatus(WarRoomStatus.ACTIVE).isEmpty();
            if (!alreadyActive) {
                WarRoom warRoom = WarRoom.builder()
                    .name("AUTO War Room — " + LocalDateTime.now())
                    .description(criticalCount + " CRITICAL incidents detected in the last hour. Auto-triggered War Room session.")
                    .status(WarRoomStatus.ACTIVE)
                    .build();

                warRoomRepository.save(warRoom);

                List<Incident> criticals = incidentRepository.findAll().stream()
                    .filter(i -> i.getPriority() == IncidentPriority.CRITICAL
                              && i.getCreatedAt().isAfter(LocalDateTime.now().minusHours(1)))
                    .collect(Collectors.toList());

                String ids = criticals.stream().map(i -> i.getId().toString()).collect(Collectors.joining(","));
                warRoom.setLinkedIncidentIds(ids);
                warRoomRepository.save(warRoom);

                criticals.forEach(incident -> {
                    incident.setInWarRoom(true);
                    incident.setWarRoomId(warRoom.getId());
                    incidentRepository.save(incident);
                    timelineService.logEvent(incident.getId(), "WAR_ROOM",
                        "Incident linked to auto-triggered War Room #" + warRoom.getId(), null);
                });

                log.error("WAR ROOM AUTO-TRIGGERED: {} critical incidents in 1 hour. War Room #{}", criticalCount, warRoom.getId());
            }
        }
    }

    @Override
    @Transactional
    public WarRoom createWarRoom(WarRoomRequest request, Long createdBy) {
        WarRoom warRoom = WarRoom.builder()
            .name(request.getName())
            .description(request.getDescription())
            .status(WarRoomStatus.ACTIVE)
            .createdBy(createdBy)
            .build();

        if (request.getIncidentIds() != null && !request.getIncidentIds().isEmpty()) {
            warRoom.setLinkedIncidentIds(
                request.getIncidentIds().stream().map(String::valueOf).collect(Collectors.joining(","))
            );
            warRoom.setResponderIds(
                request.getResponderIds() != null
                    ? request.getResponderIds().stream().map(String::valueOf).collect(Collectors.joining(","))
                    : null
            );
        }

        WarRoom saved = warRoomRepository.save(warRoom);

        if (request.getIncidentIds() != null) {
            request.getIncidentIds().forEach(incidentId -> {
                incidentRepository.findById(incidentId).ifPresent(incident -> {
                    incident.setInWarRoom(true);
                    incident.setWarRoomId(saved.getId());
                    incidentRepository.save(incident);
                    timelineService.logEvent(incidentId, "WAR_ROOM",
                        "Incident added to War Room #" + saved.getId() + " by user " + createdBy, createdBy);
                });
            });
        }

        auditService.logAction("CREATE_WAR_ROOM", createdBy, "WarRoom", saved.getId(), "War Room created: " + saved.getName());
        return saved;
    }

    @Override
    @Transactional
    public WarRoom resolveWarRoom(Long warRoomId, String resolutionSummary, Long resolvedBy) {
        WarRoom warRoom = warRoomRepository.findById(warRoomId)
            .orElseThrow(() -> new RuntimeException("War Room not found: " + warRoomId));

        warRoom.setStatus(WarRoomStatus.RESOLVED);
        warRoom.setResolutionSummary(resolutionSummary);
        warRoom.setResolvedAt(LocalDateTime.now());

        if (warRoom.getLinkedIncidentIds() != null) {
            Arrays.stream(warRoom.getLinkedIncidentIds().split(","))
                .map(Long::parseLong)
                .forEach(id -> incidentRepository.findById(id).ifPresent(incident -> {
                    incident.setInWarRoom(false);
                    incidentRepository.save(incident);
                    timelineService.logEvent(id, "WAR_ROOM",
                        "War Room #" + warRoomId + " resolved. Summary: " + resolutionSummary, resolvedBy);
                }));
        }

        auditService.logAction("RESOLVE_WAR_ROOM", resolvedBy, "WarRoom", warRoomId, "Resolved: " + resolutionSummary);
        return warRoomRepository.save(warRoom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarRoom> getActiveWarRooms() {
        return warRoomRepository.findByStatus(WarRoomStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public WarRoom getWarRoomById(Long id) {
        return warRoomRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("War Room not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarRoom> getAllWarRooms() {
        return warRoomRepository.findAll();
    }
}
