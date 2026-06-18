package com.incidentiq.service;

import com.incidentiq.dto.request.WarRoomRequest;
import com.incidentiq.model.WarRoom;

import java.util.List;

/**
 * Service interface for War Room management operations.
 */
public interface WarRoomService {

    /**
     * Auto-triggers a War Room when 3+ CRITICAL incidents occur within 1 hour.
     */
    void autoTriggerWarRoom();

    /**
     * Creates a new War Room.
     *
     * @param request the war room creation request
     * @param createdBy the user ID creating the war room
     * @return the created war room
     */
    WarRoom createWarRoom(WarRoomRequest request, Long createdBy);

    /**
     * Resolves an active War Room.
     *
     * @param warRoomId the war room ID
     * @param resolutionSummary the resolution summary
     * @param resolvedBy the user ID resolving the war room
     * @return the resolved war room
     */
    WarRoom resolveWarRoom(Long warRoomId, String resolutionSummary, Long resolvedBy);

    /**
     * Retrieves all active war rooms.
     *
     * @return list of active war rooms
     */
    List<WarRoom> getActiveWarRooms();

    /**
     * Retrieves a war room by ID.
     *
     * @param id the war room ID
     * @return the war room
     * @throws RuntimeException if war room not found
     */
    WarRoom getWarRoomById(Long id);

    /**
     * Retrieves all war rooms.
     *
     * @return list of all war rooms
     */
    List<WarRoom> getAllWarRooms();
}
