package com.instagram.adapter.out.persistence;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.AuditLogJpaEntity;
import com.instagram.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.instagram.domain.port.out.AuditLogRepository;

@Component
public class AuditLogPersistenceAdapter implements AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPersistenceAdapter.class);

    private final AuditLogJpaRepository auditLogJpaRepository;

    public AuditLogPersistenceAdapter(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    @Override
    public void log(UUID actorId, String action, String entityType, UUID entityId, String metadata, String ipAddress) {
        try {
            auditLogJpaRepository.save(
                    AuditLogJpaEntity.builder()
                            .userId(actorId)
                            .action(action)
                            .entityType(entityType)
                            .entityId(entityId)
                            .metadata(metadata)
                            .ipAddress(ipAddress)
                            .build());

        } catch (Exception e) {
            log.warn("Failed to persist audit log [action={}]: {}", action, e.getMessage(), e);
        }
    }

}
