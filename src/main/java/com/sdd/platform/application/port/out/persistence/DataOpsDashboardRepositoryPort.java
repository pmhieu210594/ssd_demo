package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardModels;
import com.sdd.platform.domain.model.AuthUserContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataOpsDashboardRepositoryPort {

    DataOpsDashboardModels.DataOpsDashboardSummary findSummary(DataOpsDashboardModels.DataOpsDashboardFilter filter);

    DataOpsDashboardModels.DataOpsDashboardPage findConnectors(DataOpsDashboardModels.DataOpsDashboardFilter filter);

    Optional<DataOpsDashboardModels.DataOpsConnectorDetail> findConnectorDetail(UUID connectorId);

    List<DataOpsDashboardModels.DataOpsMissingEvidenceItem> findRepositoryMissingEvidence(UUID repositoryId);

    DataOpsDashboardModels.DataOpsDashboardOptions findOptions(UUID projectId, UUID repositoryId,
            AuthUserContext caller);

    boolean hasDashboardAccess(AuthUserContext caller);

    String findProjectRole(AuthUserContext caller, UUID projectId);
}
