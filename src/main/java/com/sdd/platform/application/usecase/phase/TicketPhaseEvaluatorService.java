package com.sdd.platform.application.usecase.phase;

import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketArtifactEvidence;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketPhaseEvaluation;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketPhaseEvaluatorService {

    private static final String DRAFT_PHASE_CODE = "10";

    private final ArtifactScannerPersistencePort persistence;

    public TicketPhaseEvaluatorService(ArtifactScannerPersistencePort persistence) {
        this.persistence = persistence;
    }

    public TicketPhaseEvaluation evaluateAndPersist(UUID ticketId, String updatedBy) {
        if (ticketId == null) {
            throw new IllegalArgumentException("ticketId is required");
        }
        List<TicketArtifactEvidence> evidence = persistence.findCurrentArtifactEvidence(ticketId);
        Map<String, TicketArtifactEvidence> evidenceByFile = evidence.stream()
                .collect(Collectors.groupingBy(
                        TicketArtifactEvidence::defaultFileName,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::pickAnyExistsEvidence)));

        String phaseCode = resolvePhaseCode(evidenceByFile);
        UUID phaseId = persistence.findPhaseIdByCode(phaseCode)
                .orElseThrow(() -> new IllegalStateException("PHASE_NOT_FOUND:" + phaseCode));

        Optional<UUID> latestPhaseId = persistence.findLatestTicketPhaseId(ticketId);
        if (latestPhaseId.isPresent() && latestPhaseId.get().equals(phaseId)) {
            return new TicketPhaseEvaluation(ticketId, phaseId, phaseCode, statusForPhaseCode(phaseCode), false);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        persistence.upsertTicketPhaseStatus(
                ticketId,
                phaseId,
                statusForPhaseCode(phaseCode),
                now,
                "8".equals(phaseCode) ? now : null,
                false,
                null);
        return new TicketPhaseEvaluation(ticketId, phaseId, phaseCode, statusForPhaseCode(phaseCode), true);
    }

    private String resolvePhaseCode(Map<String, TicketArtifactEvidence> evidenceByFile) {
        if (exists(evidenceByFile.get("report.md"))) {
            return "8";
        }
        if (exists(evidenceByFile.get("test-plan.md")) && exists(evidenceByFile.get("test-results.md"))) {
            return "6";
        }
        if (exists(evidenceByFile.get("self-review.md"))) {
            return "4";
        }
        if (exists(evidenceByFile.get("impl-plan.md"))) {
            return "3";
        }
        if (exists(evidenceByFile.get("spec-pack.md"))) {
            return "1";
        }
        return DRAFT_PHASE_CODE;
    }

    private boolean exists(TicketArtifactEvidence evidence) {
        return evidence != null && evidence.existsFlag();
    }

    private TicketArtifactEvidence pickAnyExistsEvidence(List<TicketArtifactEvidence> evidenceItems) {
        if (evidenceItems == null || evidenceItems.isEmpty()) {
            return null;
        }
        return evidenceItems.stream()
                .filter(TicketArtifactEvidence::existsFlag)
                .findFirst()
                .orElse(evidenceItems.get(evidenceItems.size() - 1));
    }

    private String statusForPhaseCode(String phaseCode) {
        return switch (phaseCode) {
            case "8" -> "DONE";
            case "4", "6" -> "IN_REVIEW";
            case "1", "3" -> "IN_PROGRESS";
            default -> "NOT_STARTED";
        };
    }
}
