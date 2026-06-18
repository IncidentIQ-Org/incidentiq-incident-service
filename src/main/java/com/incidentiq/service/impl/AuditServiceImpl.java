package com.incidentiq.service.impl;

import com.incidentiq.model.AuditLog;
import com.incidentiq.repository.AuditLogRepository;
import com.incidentiq.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void logAction(String action, Long userId, String entityName, Long entityId, String details) {
        log.info("Audit log - Action: {}, User: {}, Entity: {}:{}, Details: {}",
                action, userId, entityName, entityId, details);
        
        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .performedBy(userId)
                .entityName(entityName)
                .entityId(entityId)
                .details(details)
                .build();
        
        auditLogRepository.save(auditLog);
    }

    @Override
    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findAll();
    }
}
