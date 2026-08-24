package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.ExceptionCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SafetyPackCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SastScaCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecretScanCounts;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityDashboardOptions;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityFilter;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketDetail;
import com.sdd.platform.application.usecase.securitydashboard.SecurityDashboardModels.SecurityTicketPage;
import com.sdd.platform.domain.model.AuthUserContext;

import java.util.Optional;
import java.util.UUID;

public interface SecurityDashboardRepositoryPort {

    SafetyPackCounts findSafetyPackCounts(SecurityFilter filter);

    SecretScanCounts findSecretScanCounts(SecurityFilter filter);

    SastScaCounts findSastScaCounts(SecurityFilter filter);

    ExceptionCounts findExceptionCounts(SecurityFilter filter);

    SecurityTicketPage findTicketRows(SecurityFilter filter);

    Optional<SecurityTicketDetail> findTicketDetail(UUID ticketId);

    Optional<UUID> findProjectIdByTicketId(UUID ticketId);

    SecurityDashboardOptions findOptions(UUID projectId, UUID repositoryId, AuthUserContext caller);

    boolean hasDashboardAccess(AuthUserContext caller);

    String findProjectRole(AuthUserContext caller, UUID projectId);
}
