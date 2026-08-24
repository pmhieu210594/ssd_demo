package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogDetail;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogEntry;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogFilter;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogListItem;

import java.util.Optional;
import java.util.UUID;

public interface AdminAuditLogPersistencePort {

    void insert(AdminAuditLogEntry entry);

    PageResult<AdminAuditLogListItem> search(AdminAuditLogFilter filter, int page, int size);

    Optional<AdminAuditLogDetail> findById(UUID id);
}
