package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.devdashboard.DevDashboardModels;
import com.sdd.platform.domain.model.AuthUserContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevDashboardRepositoryPort {

    DevDashboardModels.DevDashboardSummary findSummary(DevDashboardModels.DevDashboardFilter filter);

    DevDashboardModels.DevDashboardPage findTickets(DevDashboardModels.DevDashboardFilter filter);

    List<DevDashboardModels.DevTicketRow> findAllTickets(DevDashboardModels.DevDashboardFilter filter);

    Optional<DevDashboardModels.DevTicketDetail> findDetail(UUID ticketId);

    DevDashboardModels.DevDashboardOptions findOptions(UUID projectId, AuthUserContext caller);

    boolean hasDashboardAccess(AuthUserContext caller);

    String findProjectRole(AuthUserContext caller, UUID projectId);
}
