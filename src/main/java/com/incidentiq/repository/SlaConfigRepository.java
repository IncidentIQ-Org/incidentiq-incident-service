package com.incidentiq.repository;

import com.incidentiq.enums.Complexity;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.model.SlaConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaConfigRepository extends JpaRepository<SlaConfig, Long> {

    @Override
    @Cacheable("sla-configs")
    List<SlaConfig> findAll();

    @Cacheable(value = "sla-config-by-priority", key = "#priority")
    Optional<SlaConfig> findByPriority(IncidentPriority priority);

    @Cacheable(value = "sla-config-by-pc", key = "#priority + '-' + #complexity")
    Optional<SlaConfig> findByPriorityAndComplexity(IncidentPriority priority, Complexity complexity);
}
