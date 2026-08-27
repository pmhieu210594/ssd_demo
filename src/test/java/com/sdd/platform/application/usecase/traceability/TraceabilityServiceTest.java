package com.sdd.platform.application.usecase.traceability;

import com.sdd.platform.application.port.out.persistence.TraceabilityRepositoryPort;
import com.sdd.platform.domain.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TraceabilityServiceTest {

    private TraceabilityRepositoryPort repository;
    private TraceabilityService service;

    private final UUID ticketId = UUID.fromString("8bb0b3c7-90ce-4b1b-82c0-1bbf9c17ef61");

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TraceabilityRepositoryPort.class);
        service = new TraceabilityService(repository);
    }

    @Test
    void getTraceability_calculates_completeness_and_broken_links() {
        Mockito.when(repository.findTicket(ticketId))
                .thenReturn(Optional.of(new TraceabilityModels.TicketRow(
                        ticketId,
                        UUID.randomUUID(),
                        "ABC-123",
                        "Traceability ticket",
                        "OPEN"
                )));
        Mockito.when(repository.findArtifacts(ticketId)).thenReturn(List.of(
                artifact("SPEC_PACK", "spec-pack.md"),
                artifact("IMPL_PLAN", "impl-plan.md"),
                artifact("REVIEW_CHECKLIST", "review-checklist.md"),
                artifact("SELF_REVIEW", "self-review.md"),
                artifact("TEST_PLAN", "test-plan.md"),
                artifact("TEST_RESULTS", "test-results.md"),
                artifact("REPORT", "report.md")
        ));
        Mockito.when(repository.findPullRequests(ticketId)).thenReturn(List.of(
                new TraceabilityModels.PullRequestCoverageRow(
                        UUID.fromString("9d4c9a0d-b84d-4ad8-9f2d-6c5f3ed7f79d"),
                        "145",
                        "https://github.com/org/repo/pull/145",
                        "ABC-123 traceability",
                        "OPEN",
                        "feature/ABC-123",
                        "main",
                        OffsetDateTime.parse("2026-06-20T10:00:00Z"),
                        null,
                        null,
                        OffsetDateTime.parse("2026-06-20T10:05:00Z")
                )
        ));
        Mockito.when(repository.findCommits(ticketId)).thenReturn(List.of(
                new TraceabilityModels.CommitCoverageRow(
                        UUID.fromString("a1ab18d5-2474-4b0c-8e43-b42e9d2414e7"),
                        "0123456789abcdef0123456789abcdef01234567",
                        "feature/ABC-123",
                        "hash-1",
                        OffsetDateTime.parse("2026-06-20T11:00:00Z"),
                        OffsetDateTime.parse("2026-06-20T11:05:00Z")
                )
        ));
        Mockito.when(repository.findCiRuns(ticketId)).thenReturn(List.of(
                new TraceabilityModels.CiRunCoverageRow(
                        UUID.fromString("26c6f6f2-7ff2-4d3d-8b3c-5b8f93fcd0a8"),
                        "ci-1",
                        "https://github.com/org/repo/actions/runs/1",
                        "workflow",
                        "SUCCESS",
                        OffsetDateTime.parse("2026-06-20T12:00:00Z"),
                        OffsetDateTime.parse("2026-06-20T12:10:00Z"),
                        OffsetDateTime.parse("2026-06-20T12:11:00Z")
                )
        ));
        Mockito.when(repository.findTraceabilityLinks(ticketId)).thenReturn(List.of(
                new TraceabilityModels.TraceabilityLinkRow(
                        UUID.randomUUID(),
                        ticketId,
                        "TICKET",
                        ticketId.toString(),
                        "REPORT",
                        "report-1",
                        100.0,
                        "HIGH",
                        "ticket-inference",
                        "{}",
                        OffsetDateTime.parse("2026-06-20T13:00:00Z")
                )
        ));
        Mockito.when(repository.findParsedSections(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findEvidenceEvents(ticketId)).thenReturn(List.of(
                new TraceabilityModels.EvidenceEventRow(
                        UUID.randomUUID(),
                        "REPORT_CREATED",
                        "REPORT",
                        "report-1",
                        "PASS",
                        "Report created",
                        OffsetDateTime.parse("2026-06-20T13:05:00Z")
                )
        ));

        TraceabilityModels.TraceabilityView view = service.getTraceability(ticketId);

        assertEquals(100, view.summary().completenessPercent());
        assertEquals(9, view.summary().foundCount());
        assertEquals(9, view.summary().expectedCount());
        assertEquals(0, view.summary().brokenLinkCount());
        assertEquals(7, view.artifacts().size());
        assertEquals(1, view.pullRequests().size());
        assertEquals(1, view.commits().size());
        assertEquals(1, view.ciRuns().size());
        assertEquals(1, view.links().size());
        assertEquals(100.0, view.links().get(0).confidence());
        assertEquals("HIGH", view.links().get(0).confidenceLevel());
        assertEquals(1, view.timelineEvents().size());
    }

    @Test
    void getTraceability_marks_missing_items_visible() {
        Mockito.when(repository.findTicket(ticketId))
                .thenReturn(Optional.of(new TraceabilityModels.TicketRow(
                        ticketId,
                        UUID.randomUUID(),
                        "ABC-123",
                        "Traceability ticket",
                        "OPEN"
                )));
        Mockito.when(repository.findArtifacts(ticketId)).thenReturn(List.of(
                artifact("SPEC_PACK", "spec-pack.md")
        ));
        Mockito.when(repository.findPullRequests(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findCommits(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findCiRuns(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findTraceabilityLinks(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findParsedSections(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findEvidenceEvents(ticketId)).thenReturn(List.of());

        TraceabilityModels.TraceabilityView view = service.getTraceability(ticketId);

        assertEquals(1, view.summary().foundCount());
        assertEquals(11, view.summary().completenessPercent());
        assertEquals(8, view.summary().brokenLinkCount());
        assertEquals(7, view.artifacts().size());
        assertEquals(6, view.artifacts().stream().filter(item -> !item.existsFlag()).count());
        assertEquals(8, view.brokenLinks().size());
        assertEquals("WARNING", view.brokenLinks().stream()
                .filter(item -> "IMPL_PLAN".equals(item.code()))
                .findFirst()
                .orElseThrow()
                .severity());
        assertEquals("ERROR", view.brokenLinks().stream()
                .filter(item -> "PR".equals(item.code()))
                .findFirst()
                .orElseThrow()
                .severity());
        assertEquals("ERROR", view.brokenLinks().stream()
                .filter(item -> "CI".equals(item.code()))
                .findFirst()
                .orElseThrow()
                .severity());
    }

    @Test
    void getTraceability_marks_missing_sections_as_broken_links() {
        Mockito.when(repository.findTicket(ticketId))
                .thenReturn(Optional.of(new TraceabilityModels.TicketRow(
                        ticketId,
                        UUID.randomUUID(),
                        "ABC-123",
                        "Traceability ticket",
                        "OPEN"
                )));
        Mockito.when(repository.findArtifacts(ticketId)).thenReturn(List.of(
                artifact("SPEC_PACK", "spec-pack.md")
        ));
        Mockito.when(repository.findPullRequests(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findCommits(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findCiRuns(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findTraceabilityLinks(ticketId)).thenReturn(List.of());
        UUID artifactSnapshotId = UUID.fromString("f07e0dca-c612-4c90-9b12-baf3dab9c6a1");
        Mockito.when(repository.findParsedSections(ticketId)).thenReturn(List.of(
                parsedSection(
                        artifactSnapshotId,
                        "TEST_RESULTS",
                        "Test Results",
                        "additional_test_this_time",
                        "Missing acceptance criteria",
                        false,
                        false,
                        "REQUIRED_SECTION_MISSING"
                ),
                parsedSection(
                        artifactSnapshotId,
                        "TEST_RESULTS",
                        "Test Results",
                        "summary_of_results",
                        "Missing summary block",
                        false,
                        false,
                        "REQUIRED_SECTION_MISSING"
                )
        ));
        Mockito.when(repository.findEvidenceEvents(ticketId)).thenReturn(List.of());

        TraceabilityModels.TraceabilityView view = service.getTraceability(ticketId);

        assertEquals(1, view.summary().foundCount());
        assertEquals(11, view.summary().completenessPercent());
        assertEquals(9, view.summary().brokenLinkCount());
        assertEquals(9, view.brokenLinks().size());
        assertEquals("ERROR", view.brokenLinks().stream()
                .filter(item -> "TEST_RESULTS-PARSER".equals(item.code()))
                .findFirst()
                .orElseThrow()
                .severity());
        assertEquals("Test Results is missing required section(s): Additional Test This Time, Summary Of Results",
                view.brokenLinks().stream()
                        .filter(item -> "TEST_RESULTS-PARSER".equals(item.code()))
                        .findFirst()
                        .orElseThrow()
                        .message());
    }

    @Test
    void getTraceability_throws_not_found_for_missing_ticket() {
        Mockito.when(repository.findTicket(ticketId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getTraceability(ticketId));
    }

    @Test
    void getTraceability_sorts_timeline_events_by_timestamp_then_id() {
        Mockito.when(repository.findTicket(ticketId))
                .thenReturn(Optional.of(new TraceabilityModels.TicketRow(
                        ticketId,
                        UUID.randomUUID(),
                        "ABC-123",
                        "Traceability ticket",
                        "OPEN"
                )));
        Mockito.when(repository.findArtifacts(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findPullRequests(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findCommits(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findCiRuns(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findTraceabilityLinks(ticketId)).thenReturn(List.of());
        Mockito.when(repository.findParsedSections(ticketId)).thenReturn(List.of());
        UUID laterEventId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID earlierEventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Mockito.when(repository.findEvidenceEvents(ticketId)).thenReturn(List.of(
                new TraceabilityModels.EvidenceEventRow(
                        laterEventId,
                        "REPORT_CREATED",
                        "REPORT",
                        "report-2",
                        "PASS",
                        "Later report event",
                        OffsetDateTime.parse("2026-06-20T13:00:00Z")
                ),
                new TraceabilityModels.EvidenceEventRow(
                        earlierEventId,
                        "REPORT_CREATED",
                        "REPORT",
                        "report-1",
                        "PASS",
                        "Earlier report event",
                        OffsetDateTime.parse("2026-06-20T13:00:00Z")
                )
        ));

        TraceabilityModels.TraceabilityView view = service.getTraceability(ticketId);

        assertEquals(List.of(earlierEventId, laterEventId),
                view.timelineEvents().stream().map(TraceabilityModels.TimelineEvent::eventId).toList());
    }

    private static TraceabilityModels.ArtifactCoverageRow artifact(String code, String fileName) {
        return new TraceabilityModels.ArtifactCoverageRow(
                UUID.randomUUID(),
                code,
                switch (code) {
                    case "SPEC_PACK" -> "Spec Pack";
                    case "IMPL_PLAN" -> "Implementation Plan";
                    case "REVIEW_CHECKLIST" -> "Review Checklist";
                    case "SELF_REVIEW" -> "Self Review";
                    case "TEST_PLAN" -> "Test Plan";
                    case "TEST_RESULTS" -> "Test Results";
                    case "REPORT" -> "Report";
                    default -> code;
                },
                fileName,
                true,
                "/docs/" + fileName,
                true,
                OffsetDateTime.parse("2026-06-20T09:00:00Z")
        );
    }

    private static TraceabilityModels.ParsedSectionRow parsedSection(
            UUID artifactSnapshotId,
            String artifactTypeCode,
            String artifactName,
            String sectionKey,
            String sectionSummary,
            boolean presentFlag,
            Boolean validFlag,
            String parseWarning
    ) {
        return new TraceabilityModels.ParsedSectionRow(
                UUID.randomUUID(),
                artifactSnapshotId,
                artifactTypeCode,
                artifactName,
                sectionKey,
                sectionSummary,
                true,
                presentFlag,
                validFlag,
                parseWarning,
                OffsetDateTime.parse("2026-06-20T09:00:00Z")
        );
    }
}
