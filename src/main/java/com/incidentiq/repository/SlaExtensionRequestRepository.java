package com.incidentiq.repository;

import com.incidentiq.model.SlaExtensionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SlaExtensionRequestRepository extends JpaRepository<SlaExtensionRequest, Long> {
    List<SlaExtensionRequest> findByIncidentId(Long incidentId);
    List<SlaExtensionRequest> findByRequestedBy(Long userId);
    List<SlaExtensionRequest> findByStatus(SlaExtensionRequest.ExtensionStatus status);
}
