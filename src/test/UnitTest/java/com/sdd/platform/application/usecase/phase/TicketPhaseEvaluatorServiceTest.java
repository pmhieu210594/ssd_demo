package com.sdd.platform.application.usecase.phase;

import com.sdd.platform.application.port.out.persistence.ArtifactScannerPersistencePort;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketArtifactEvidence;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketPhaseEvaluatorServiceTest {

    @Test
    void report_phase_wins_over_lower_phases() {
        ArtifactScannerPersistencePort persistence = mock(ArtifactScannerPersistencePort.class);
        TicketPhaseEvaluatorService service = new TicketPhaseEvaluatorService(persistence);
        UUID ticketId = UUID.randomUUID();
        UUID reportPhaseId = UUID.randomUUID();

        when(persistence.findCurrentArtifactEvidence(ticketId)).thenReturn(List.of(
                evidence("spec-pack.md", true),
                evidence("impl-plan.md", true),
                evidence("self-review.md", true),
                evidence("test-plan.md", true),
                evidence("test-results.md", true),
                evidence("report.md", true)));
        when(persistence.findLatestTicketPhaseId(ticketId)).thenReturn(Optional.empty());
        when(persistence.findPhaseIdByCode(anyString())).thenAnswer(invocation -> {
            String phaseCode = invocation.getArgument(0, String.class);
            return Optional.of(switch (phaseCode) {
                case "8" -> reportPhaseId;
                case "6" -> UUID.randomUUID();
                case "4" -> UUID.randomUUID();
                case "3" -> UUID.randomUUID();
                case "1" -> UUID.randomUUID();
                default -> UUID.randomUUID();
            });
        });

        var result = service.evaluateAndPersist(ticketId, "tester");

        assertEquals("8", result.phaseCode());
        assertEquals("DONE", result.status());
        assertTrue(result.persisted());
        verify(persistence).upsertTicketPhaseStatus(eq(ticketId), eq(reportPhaseId), eq("DONE"), any(), any(),
                eq(false), isNull());
    }

    @Test
    void draft_is_used_when_no_artifact_parses_successfully() {
        ArtifactScannerPersistencePort persistence = mock(ArtifactScannerPersistencePort.class);
        TicketPhaseEvaluatorService service = new TicketPhaseEvaluatorService(persistence);
        UUID ticketId = UUID.randomUUID();
        UUID draftPhaseId = UUID.randomUUID();

        when(persistence.findCurrentArtifactEvidence(ticketId)).thenReturn(List.of(
                evidence("spec-pack.md", false),
                evidence("impl-plan.md", false),
                evidence("self-review.md", false),
                evidence("test-plan.md", false),
                evidence("test-results.md", false),
                evidence("report.md", false)));
        when(persistence.findLatestTicketPhaseId(ticketId)).thenReturn(Optional.empty());
        when(persistence.findPhaseIdByCode("10")).thenReturn(Optional.of(draftPhaseId));

        var result = service.evaluateAndPersist(ticketId, "tester");

        assertEquals("10", result.phaseCode());
        assertEquals("NOT_STARTED", result.status());
        assertTrue(result.persisted());
        verify(persistence).upsertTicketPhaseStatus(eq(ticketId), eq(draftPhaseId), eq("NOT_STARTED"), any(), any(),
                eq(false), isNull());
    }

    @Test
    void repeated_evaluation_skips_persist_when_phase_is_unchanged() {
        ArtifactScannerPersistencePort persistence = mock(ArtifactScannerPersistencePort.class);
        TicketPhaseEvaluatorService service = new TicketPhaseEvaluatorService(persistence);
        UUID ticketId = UUID.randomUUID();
        UUID specPhaseId = UUID.randomUUID();

        when(persistence.findCurrentArtifactEvidence(ticketId)).thenReturn(List.of(
                evidence("spec-pack.md", true)));
        when(persistence.findLatestTicketPhaseId(ticketId)).thenReturn(Optional.of(specPhaseId));
        when(persistence.findPhaseIdByCode("1")).thenReturn(Optional.of(specPhaseId));

        var result = service.evaluateAndPersist(ticketId, "tester");

        assertEquals("1", result.phaseCode());
        assertFalse(result.persisted());
        verify(persistence, never()).upsertTicketPhaseStatus(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void any_existing_snapshot_for_a_file_can_drive_phase() {
        ArtifactScannerPersistencePort persistence = mock(ArtifactScannerPersistencePort.class);
        TicketPhaseEvaluatorService service = new TicketPhaseEvaluatorService(persistence);
        UUID ticketId = UUID.randomUUID();
        UUID reportPhaseId = UUID.randomUUID();

        when(persistence.findCurrentArtifactEvidence(ticketId)).thenReturn(List.of(
                evidence("report.md", false),
                evidence("report.md", true),
                evidence("spec-pack.md", true)));
        when(persistence.findLatestTicketPhaseId(ticketId)).thenReturn(Optional.empty());
        when(persistence.findPhaseIdByCode("8")).thenReturn(Optional.of(reportPhaseId));

        var result = service.evaluateAndPersist(ticketId, "tester");

        assertEquals("8", result.phaseCode());
        assertTrue(result.persisted());
        verify(persistence).upsertTicketPhaseStatus(eq(ticketId), eq(reportPhaseId), eq("DONE"), any(), any(),
                eq(false), isNull());
    }

    private static TicketArtifactEvidence evidence(String fileName, boolean existsFlag) {
        return new TicketArtifactEvidence(
                UUID.randomUUID(),
                fileNameToArtifactCode(fileName),
                fileName,
                existsFlag,
                existsFlag,
                existsFlag ? "FOUND" : "MISSING",
                OffsetDateTime.parse("2026-07-02T00:00:00Z"));
    }

    private static String fileNameToArtifactCode(String fileName) {
        return switch (fileName) {
            case "spec-pack.md" -> "SPEC_PACK";
            case "impl-plan.md" -> "IMPL_PLAN";
            case "self-review.md" -> "SELF_REVIEW";
            case "test-plan.md" -> "TEST_PLAN";
            case "test-results.md" -> "TEST_RESULTS";
            case "report.md" -> "REPORT";
            default -> "UNKNOWN";
        };
    }
}
