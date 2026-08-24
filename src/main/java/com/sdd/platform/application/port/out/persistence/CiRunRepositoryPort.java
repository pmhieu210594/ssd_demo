package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.ingestion.CiRunModels.ConnectorScope;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.CiRunMetadataView;
import com.sdd.platform.application.usecase.ingestion.CiRunModels.PullRequestScope;
import com.sdd.platform.domain.model.CiJob;
import com.sdd.platform.domain.model.CiRun;
import com.sdd.platform.domain.model.ConnectorRun;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CiRunRepositoryPort {

    Optional<ConnectorScope> findConnectorByType(String connectorType);

    Optional<PullRequestScope> findPullRequestByRepositoryAndExternalNumber(UUID repositoryId, int externalPrNumber);

    Optional<PullRequestScope> findPullRequestByRepositoryAndBranch(UUID repositoryId, String sourceBranch);

    Optional<PullRequestScope> findPullRequestByRepositoryAndCommit(UUID repositoryId, String commitSha);

    Optional<UUID> findTicketIdByProjectIdAndExternalKey(UUID projectId, String externalTicketKey);

    Optional<String> findTicketKeyByPullRequestId(UUID pullRequestId);

    Optional<UUID> findCiRunIdByIdentity(String ciProvider, UUID repositoryId, String externalRunId);

    CiJob upsertCiJob(CiJob job);

    String computeAggregateRunStatus(UUID ciRunId);

    void updateCiRunStatus(UUID ciRunId, String status);

    Optional<CiRunMetadataView> findLatestCiRunByRepositoryAndTicket(UUID repositoryId, UUID ticketId);

    Optional<CiRunMetadataView> findFirstCiRunByTicketId(UUID ticketId);

    Optional<PullRequestScope> findLatestPullRequestByTicket(UUID ticketId);

    List<CiRunMetadataView> findRecentCiRuns(int limit);

    CiRun insertCiRun(CiRun run);

    CiRun updateCiRun(CiRun run);

    ConnectorRun insertConnectorRun(ConnectorRun run);

    ConnectorRun updateConnectorRun(ConnectorRun run);
}
