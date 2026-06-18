package com.incidentiq.repository;

import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.model.SlaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SlaConfigRepository extends JpaRepository<SlaConfig, Long> {
    Optional<SlaConfig> findByPriority(IncidentPriority priority);
}
