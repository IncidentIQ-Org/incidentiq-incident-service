package com.incidentiq.service;

import com.incidentiq.model.AuditLog;
import com.incidentiq.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void logAction(String action, Long userId, String entityName, Long entityId, String details) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .performedBy(userId)
                .entityName(entityName)
                .entityId(entityId)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }

    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findAll();
    }
}
