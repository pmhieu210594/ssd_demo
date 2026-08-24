package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageCounts;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoveragePage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.AcCoverageRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaAcTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaDashboardOptions;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaFilter;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketFilter;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketPage;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.QaTicketRow;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TestRunCounts;
import com.sdd.platform.application.usecase.qadashboard.QaDashboardModels.TrendPoint;
import com.sdd.platform.domain.model.AuthUserContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QaDashboardRepositoryPort {

    AcCoverageCounts findAcCoverageCounts(QaFilter filter);

    int findDefectLeakageCount(QaFilter filter);

    TestRunCounts findTestRunCounts(QaFilter filter);

    AcCoveragePage findAcCoverageRows(QaFilter filter);

    List<TrendPoint> findCoverageTrendPoints(QaFilter filter);

    List<AcCoverageRow> findAllForExport(QaFilter filter);

    QaDashboardOptions findOptions(UUID projectId, UUID repositoryId, AuthUserContext caller);

    boolean hasDashboardAccess(AuthUserContext caller);

    String findProjectRole(AuthUserContext caller, UUID projectId);

    QaTicketPage findTickets(QaTicketFilter filter);

    Optional<QaTicketRow> findTicketDetail(UUID ticketId);

    Optional<String> findLatestCiRunUrl(UUID ticketId);

    QaAcTicketPage findTicketAcceptanceCriteria(UUID ticketId, String search, int page, int size);

    List<TrendPoint> findTicketCoverageTrend(UUID ticketId);
}
