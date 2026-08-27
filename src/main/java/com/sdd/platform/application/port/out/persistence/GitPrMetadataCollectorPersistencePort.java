package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRun;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CommitUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestChangedFileUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewCommentUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.TraceabilityLinkUpsert;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;

import java.util.Optional;
import java.util.UUID;

public interface GitPrMetadataCollectorPersistencePort {

    Optional<RepositoryScope> findActiveRepository(UUID repositoryId);

    Optional<TicketScope> findTicketByProjectIdAndExternalKey(UUID projectId, String externalTicketKey);

    TicketScope upsertMinimalTicket(UUID projectId, String externalTicketKey, String title, String status, java.time.OffsetDateTime lastCommitAt);

    ConnectorScope ensureConnector(String connectorType, String connectorName);

    CollectorRun insertRun(CollectorRun run);

    CollectorRun updateRun(CollectorRun run);

    Optional<UUID> findMemberKeyByPseudonym(String pseudonym);

    Optional<UUID> findMemberKeyByExternalUserHash(String externalUserHash);

    UUID upsertPullRequest(PullRequestUpsert request);

    UUID upsertCommit(CommitUpsert request);

    void upsertPullRequestCommit(UUID prId, UUID commitId);

    UUID upsertPullRequestChangedFile(PullRequestChangedFileUpsert request);

    void upsertTraceabilityLink(TraceabilityLinkUpsert request);

    void deleteReviewsByPrId(UUID prId);

    UUID insertReview(ReviewUpsert request);

    void insertReviewComment(ReviewCommentUpsert request);

    Optional<UUID> findTicketIdByPullRequestId(UUID prId);
}
