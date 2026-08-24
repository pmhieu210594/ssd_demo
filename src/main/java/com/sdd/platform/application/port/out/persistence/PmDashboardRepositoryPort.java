package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardFilter;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardInsights;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardRefreshResult;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardSummary;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardOptions;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardIssueItem;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketDetail;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketRow;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.TemplateUsageRow;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.AiFindingStatsRow;
import com.sdd.platform.domain.model.AuthUserContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PmDashboardRepositoryPort {

    DashboardRefreshResult rebuildSnapshot();

    DashboardOptions findOptions(UUID projectId, AuthUserContext caller);

    boolean hasDashboardAccess(AuthUserContext caller);

    String findProjectRole(AuthUserContext caller, UUID projectId);

    DashboardSummary findSummary(DashboardFilter filter);

    PageResult<DashboardTicketRow> findTickets(DashboardFilter filter);

    List<DashboardTicketRow> findAllTickets(DashboardFilter filter);

    DashboardInsights findInsights(DashboardFilter filter);

    Optional<DashboardTicketDetail> findDetail(UUID ticketId);

    List<DashboardIssueItem> findIssueItems(UUID ticketId);

    List<TemplateUsageRow> findTemplateUsage(UUID projectId, UUID repositoryId);

    Optional<AiFindingStatsRow> findAiFindingStats(UUID projectId, UUID repositoryId);
}
