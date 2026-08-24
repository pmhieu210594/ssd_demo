package com.sdd.platform.web.dto;

import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanMode;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanRequest;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanStatus;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactScanTriggerType;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ArtifactSnapshot;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ScanRun;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ArtifactScannerDtos {

    private ArtifactScannerDtos() {}

    public record ArtifactScanRequestDto(
            @NotNull UUID repositoryId,
            @NotBlank String branchOrRef,
            @NotNull ArtifactScanMode scanMode,
            List<String> ticketIds,
            @NotNull ArtifactScanTriggerType triggerType,
            @NotBlank String requestedBy,
            String traceId
    ) {
        public ArtifactScanRequest toRequest() {
            return new ArtifactScanRequest(repositoryId, branchOrRef, scanMode, ticketIds, triggerType, requestedBy, traceId);
        }
    }

    public record ArtifactScanRunDto(
            UUID connectorRunId,
            UUID connectorId,
            String status,
            int recordsRead,
            int recordsWritten,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String traceId
    ) {
        public static ArtifactScanRunDto from(ScanRun run) {
            return new ArtifactScanRunDto(
                    run.connectorRunId(),
                    run.connectorId(),
                    run.status(),
                    run.recordsRead(),
                    run.recordsWritten(),
                    run.errorMessage(),
                    run.startedAt(),
                    run.finishedAt(),
                    run.traceId()
            );
        }
    }

    public record ArtifactScanArtifactDto(
            UUID artifactSnapshotId,
            UUID connectorRunId,
            UUID repositoryId,
            UUID ticketId,
            String ticketExternalKey,
            String ticketStatus,
            OffsetDateTime ticketLastCommitAt,
            UUID artifactTypeId,
            String artifactTypeCode,
            String artifactName,
            String defaultFileName,
            boolean requiredFlag,
            String phaseCode,
            String sourcePath,
            boolean existsFlag,
            String contentHash,
            Long sizeBytes,
            OffsetDateTime sourceUpdatedAt,
            boolean templateEmptyFlag,
            boolean needParse,
            String scanStatus,
            String scanMessage,
            OffsetDateTime collectedAt
    ) {
        public static ArtifactScanArtifactDto from(ArtifactSnapshot snapshot) {
            return new ArtifactScanArtifactDto(
                    snapshot.artifactSnapshotId(),
                    snapshot.connectorRunId(),
                    snapshot.repositoryId(),
                    snapshot.ticketId(),
                    snapshot.ticketExternalKey(),
                    snapshot.ticketStatus(),
                    snapshot.ticketLastCommitAt(),
                    snapshot.artifactTypeId(),
                    snapshot.artifactTypeCode(),
                    snapshot.artifactName(),
                    snapshot.defaultFileName(),
                    snapshot.requiredFlag(),
                    snapshot.phaseCode(),
                    snapshot.sourcePath(),
                    snapshot.existsFlag(),
                    snapshot.contentHash(),
                    snapshot.sizeBytes(),
                    snapshot.sourceUpdatedAt(),
                    snapshot.templateEmptyFlag(),
                    snapshot.needParse(),
                    snapshot.scanStatus(),
                    snapshot.scanMessage(),
                    snapshot.collectedAt()
            );
        }

        public boolean statusIs(String status) {
            return scanStatus != null && scanStatus.equals(status);
        }
    }

    public record ArtifactScanResultDto(
            ArtifactScanRunDto run,
            List<ArtifactScanArtifactDto> artifacts
    ) {}
}
