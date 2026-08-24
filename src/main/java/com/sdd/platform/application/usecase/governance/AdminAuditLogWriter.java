package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.port.out.persistence.AdminAuditLogPersistencePort;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogEntry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated insert boundary so audit-log write failures never abort the caller's
 * transaction. Uses REQUIRES_NEW to spawn independent transactions — audit inserts
 * are not blocked by parent transaction's read-only flag, and always commit/fail
 * independently from the caller. A separate Spring bean is required here so the
 * {@code @Transactional} annotations apply — calling these methods from within
 * {@link AdminAuditLogService} itself would bypass the proxy (self-invocation).
 */
@Component
public class AdminAuditLogWriter {

    private final AdminAuditLogPersistencePort persistencePort;

    public AdminAuditLogWriter(AdminAuditLogPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertWithinCallerTransaction(AdminAuditLogEntry entry) {
        persistencePort.insert(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertIndependently(AdminAuditLogEntry entry) {
        persistencePort.insert(entry);
    }
}
