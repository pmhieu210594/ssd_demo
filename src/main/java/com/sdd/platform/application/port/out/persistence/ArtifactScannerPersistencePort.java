package com.sdd.platform.application.port.out.persistence;

import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactTypeScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedAcceptanceCriteria;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedSection;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedDecision;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedRisk;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.EvidenceEvent;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.DataQualityRecord;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ParsedSummaryPatch;

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

    TicketScope upsertMinimalTicket(UUID projectId, String externalTicketKey, String title, String status, OffsetDateTime lastCommitAt);

    List<ArtifactTypeScope> findArtifactTypes();

    Optional<ConnectorScope> findConnectorByType(String connectorType);

    Optional<ArtifactSnapshot> findLatestSnapshot(UUID repositoryId, String sourcePath, UUID artifactTypeId);

    ScanRun insertRun(ScanRun run);

    ScanRun updateRun(ScanRun run);

    Optional<ScanRun> findRun(UUID connectorRunId);

    ArtifactSnapshot insertSnapshot(ArtifactSnapshot snapshot);

    void updateSnapshotParsedSummary(ParsedSummaryPatch patch);

    List<ArtifactSnapshot> findRunArtifacts(UUID connectorRunId);

    List<ArtifactSnapshot> findCurrentInventory(UUID repositoryId);

    void deactivateAcceptanceCriteriaByTicketId(UUID ticketId);

    void insertParsedAcceptanceCriteria(ParsedAcceptanceCriteria ac);

    void deleteParsedSectionsByTicketIdAndSectionType(UUID ticketId, String sectionType);

    void insertParsedSection(ParsedSection section);

    void insertParsedDecision(ParsedDecision decision);

    void insertParsedRisk(ParsedRisk risk);

    void insertEvidenceEvent(EvidenceEvent event);

    void insertDataQualityRecord(DataQualityRecord quality);
}
