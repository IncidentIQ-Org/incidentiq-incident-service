package com.incidentiq.repository;

import com.incidentiq.enums.WarRoomStatus;
import com.incidentiq.model.WarRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WarRoomRepository extends JpaRepository<WarRoom, Long> {
    List<WarRoom> findByStatus(WarRoomStatus status);
    List<WarRoom> findByCreatedBy(Long createdBy);
}
