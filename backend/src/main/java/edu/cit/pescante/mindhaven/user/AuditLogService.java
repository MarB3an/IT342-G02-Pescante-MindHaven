package edu.cit.pescante.mindhaven.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(String actorRole, String actorLabel, String action, String details) {
        AuditLog log = new AuditLog();
        log.setActorRole(actorRole);
        log.setActorLabel(actorLabel);
        log.setAction(action);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
