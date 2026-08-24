package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.scanner.ArtifactDocumentDateService;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactTypeScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketArtifactEvidence;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedAcceptanceCriteria;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedSection;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedDecision;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedIssue;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedRisk;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.EvidenceEvent;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.DataQualityRecord;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedSummaryPatch;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactScannerPersistencePort {

    Optional<RepositoryScope> findRepository(UUID repositoryId);

    Optional<RepositoryScope> findRepositoryByMaskedName(String repoNameMasked);

    Optional<TicketScope> findTicketByProjectIdAndExternalKey(UUID projectId, String externalTicketKey);

    default TicketScope upsertMinimalTicket(UUID projectId, String externalTicketKey, String title) {
        return upsertMinimalTicket(projectId, externalTicketKey, title, null, null);
    }

    TicketScope upsertMinimalTicket(UUID projectId, String externalTicketKey, String title, String status,
            OffsetDateTime lastCommitAt);

    List<ArtifactTypeScope> findArtifactTypes();

    Optional<ConnectorScope> findConnectorByType(String connectorType);

    Optional<ArtifactSnapshot> findLatestSnapshot(UUID repositoryId, String sourcePath, UUID artifactTypeId);

    ScanRun insertRun(ScanRun run);

    ScanRun updateRun(ScanRun run);

    Optional<ScanRun> findRun(UUID connectorRunId);

    ArtifactSnapshot insertSnapshot(ArtifactSnapshot snapshot);

    ArtifactSnapshot updateSnapshot(ArtifactSnapshot snapshot, UUID artifactSnapshotId);

    void updateSnapshotParsedSummary(ParsedSummaryPatch patch);

    List<ArtifactSnapshot> findRunArtifacts(UUID connectorRunId);

    List<ArtifactSnapshot> findCurrentInventory(UUID repositoryId);

    List<TicketArtifactEvidence> findCurrentArtifactEvidence(UUID ticketId);

    Optional<UUID> findLatestTicketPhaseId(UUID ticketId);

    Optional<UUID> findPhaseIdByCode(String phaseCode);

    void upsertTicketPhaseStatus(UUID ticketId,
                                 UUID phaseId,
                                 String status,
                                 OffsetDateTime startedAt,
                                 OffsetDateTime completedAt,
                                 boolean blockedFlag,
                                 String blockReason);

    void deactivateAcceptanceCriteriaByTicketId(UUID ticketId);

    void updateTicketStartedAt(UUID ticketId, OffsetDateTime startedAt);

    void updateTicketCompletedAt(UUID ticketId, OffsetDateTime completedAt);

    void insertParsedAcceptanceCriteria(ParsedAcceptanceCriteria ac);

    void deleteParsedSectionsByTicketIdAndSectionType(UUID ticketId, String sectionType);

    void insertParsedSection(ParsedSection section);

    void insertParsedDecision(ParsedDecision decision);

    void deleteParsedRisksByTicketId(UUID ticketId);

    void insertParsedRisk(ParsedRisk risk);

    void deleteParsedIssuesByTicketIdAndSourceType(UUID ticketId, String sourceType);

    void insertParsedIssue(ParsedIssue issue);

    void insertEvidenceEvent(EvidenceEvent event);

    void insertDataQualityRecord(DataQualityRecord quality);

    void upsertParsedExceptions(List<ParsedException> exceptions);

    Optional<UUID> findRoleIdByName(String roleName);

    void deleteParsedExceptionsByTicketId(UUID ticketId);

    void upsertArtifactDocumentDates(List<ArtifactDocumentDateService.DocumentDate> documentDates);
}
