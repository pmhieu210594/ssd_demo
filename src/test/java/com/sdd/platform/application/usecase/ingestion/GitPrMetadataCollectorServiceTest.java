package com.sdd.platform.application.usecase.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.integration.GithubPullRequestMetadataPort;
import com.sdd.platform.application.port.out.persistence.GitPrMetadataCollectorPersistencePort;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ChangedFileSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CommitSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.CollectorRun;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.PullRequestGraph;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewCommentSnapshot;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewUpsert;
import com.sdd.platform.application.usecase.ingestion.GitPrMetadataCollectorModels.ReviewCommentUpsert;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreService;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.ConnectorScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.RepositoryScope;
import com.sdd.platform.application.usecase.scanner.ArtifactScannerModels.TicketScope;
import com.sdd.platform.domain.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitPrMetadataCollectorServiceTest {

    private GitPrMetadataCollectorService service;
    private GitPrMetadataCollectorPersistencePort persistence;
    private GithubPullRequestMetadataPort githubPort;
    private EvidenceQualityScoreService evidenceQualityScoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID repositoryId = UUID.fromString("99e85aef-8cf4-4b3c-8863-90e2740e7549");
    private final UUID projectId = UUID.fromString("578a0d3b-341c-4b8a-905d-3cb5507ea70c");
    private final UUID connectorId = UUID.fromString("3a3d15f0-0b42-4f32-bd4d-fce6b52fd7a1");
    private final UUID runId = UUID.fromString("1c59056b-54c9-49cf-8c4d-2a89d503ed9f");

    @BeforeEach
    void setUp() {
        persistence = Mockito.mock(GitPrMetadataCollectorPersistencePort.class);
        githubPort = Mockito.mock(GithubPullRequestMetadataPort.class);
        evidenceQualityScoreService = Mockito.mock(EvidenceQualityScoreService.class);
        service = new GitPrMetadataCollectorService(persistence, githubPort, objectMapper, evidenceQualityScoreService);

        Mockito.when(persistence.findActiveRepository(repositoryId))
                .thenReturn(Optional.of(new RepositoryScope(repositoryId, projectId, "acme/widget", "main")));
        Mockito.when(persistence.ensureConnector(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new ConnectorScope(connectorId, "GIT_PR_METADATA_COLLECTOR", "Git PR Metadata Collector"));
        Mockito.when(persistence.insertRun(Mockito.any(CollectorRun.class)))
                .thenAnswer(invocation -> {
                    CollectorRun run = invocation.getArgument(0);
                    return new CollectorRun(runId, run.connectorId(), run.startedAt(), run.finishedAt(), run.status(), run.recordsRead(), run.recordsWritten(), run.failureCount(), run.errorMessage(), run.traceId());
                });
        Mockito.when(persistence.updateRun(Mockito.any(CollectorRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.when(persistence.findMemberKeyByExternalUserHash(Mockito.anyString()))
                .thenReturn(Optional.empty());
        Mockito.when(persistence.findFullnameByMemberKey(Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(persistence.upsertCommit(Mockito.any())).thenReturn(UUID.randomUUID());
        Mockito.when(persistence.upsertPullRequestChangedFile(Mockito.any())).thenReturn(UUID.randomUUID());
        Mockito.doNothing().when(persistence).upsertPullRequestCommit(Mockito.any(), Mockito.any());
        Mockito.doNothing().when(persistence).upsertTraceabilityLink(Mockito.any());
        Mockito.doNothing().when(persistence).deleteReviewsByPrId(Mockito.any());
        Mockito.when(persistence.insertReview(Mockito.any())).thenReturn(UUID.randomUUID());
        Mockito.doNothing().when(persistence).insertReviewComment(Mockito.any());
    }

    @Test
    void collect_pull_request_infers_ticket_from_branch_and_persists_metadata() {
        UUID ticketId = UUID.fromString("f5b8aa42-7ef1-49b2-b5de-d07b3cf5a6db");
        UUID authorMemberKey = UUID.fromString("f2eb8aa0-5d61-4a6f-a12e-2df0b1d0d111");
        Mockito.when(persistence.findTicketByProjectIdAndExternalKey(projectId, "ABC-123"))
                .thenReturn(Optional.of(new TicketScope(ticketId, "ABC-123")));
        Mockito.when(persistence.findMemberKeyByExternalUserHash("a6658157f0df83900a6c8f3b34a7c739c66455d34b142846c96dedcacda08a3c"))
                .thenReturn(Optional.of(authorMemberKey));
        Mockito.when(persistence.findFullnameByMemberKey(authorMemberKey))
                .thenReturn(Optional.of("Octo Cat"));
        Mockito.when(persistence.upsertMinimalTicket(
                        Mockito.eq(projectId),
                        Mockito.eq("ABC-123"),
                        Mockito.eq("ABC-123 Improve stock shortage error"),
                        Mockito.eq("OPEN"),
                        Mockito.eq(OffsetDateTime.parse("2026-06-17T00:30:00Z")),
                        Mockito.anyString()))
                .thenReturn(new TicketScope(ticketId, "ABC-123"));
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 123))
                .thenReturn(new PullRequestGraph(
                        123,
                        "ABC-123 Improve stock shortage error",
                        "Do not persist this raw body",
                        "https://github.com/acme/widget/pull/123",
                        "open",
                        false,
                        "feature/ABC-123-stock-error",
                        "1111111111111111111111111111111111111111",
                        "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null,
                        null,
                        "octocat",
                        List.of("bug"),
                        "APPROVED",
                        List.of(new CommitSnapshot(
                                "1111111111111111111111111111111111111111",
                                "cleanup",
                                "octocat",
                                "Octo Cat",
                                OffsetDateTime.parse("2026-06-17T00:30:00Z"),
                                "https://github.com/acme/widget/commit/1111111111111111111111111111111111111111"
                        )),
                        List.of(new ChangedFileSnapshot(
                                "docs/changes/ABC-123/spec-pack.md",
                                "modified",
                                10,
                                2,
                                "aaaa"
                        )),
                        List.of(),
                        List.of()
                ));

        var result = service.collectPullRequest(repositoryId, 123, "tester@example.com");

        assertEquals("SUCCESS", result.status());
        assertEquals(1, result.processedPrCount());
        assertEquals(1, result.processedCommitCount());
        assertEquals(1, result.processedChangedFileCount());
        ArgumentCaptor<GitPrMetadataCollectorModels.PullRequestUpsert> prCaptor = ArgumentCaptor.forClass(GitPrMetadataCollectorModels.PullRequestUpsert.class);
        Mockito.verify(persistence).upsertPullRequest(prCaptor.capture());
        assertEquals(repositoryId, prCaptor.getValue().repositoryId());
        assertEquals(ticketId, prCaptor.getValue().ticketId());
        assertEquals("ABC-123", prCaptor.getValue().linkedIssueKey());
        assertEquals("OPEN", prCaptor.getValue().status());
        assertEquals("APPROVED", prCaptor.getValue().reviewState());
        assertEquals(authorMemberKey, prCaptor.getValue().authorMemberKey());
        assertEquals("Octo Cat", prCaptor.getValue().authorDisplayName());
        ArgumentCaptor<String> createdByCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(persistence).upsertMinimalTicket(
                Mockito.eq(projectId),
                Mockito.eq("ABC-123"),
                Mockito.eq("ABC-123 Improve stock shortage error"),
                Mockito.eq("OPEN"),
                Mockito.eq(OffsetDateTime.parse("2026-06-17T00:30:00Z")),
                createdByCaptor.capture());
        assertEquals("Octo Cat", createdByCaptor.getValue());
        Mockito.verify(evidenceQualityScoreService).recalculateFromSourceChange(Mockito.eq(UUID.fromString("f5b8aa42-7ef1-49b2-b5de-d07b3cf5a6db")), Mockito.isNull(), Mockito.anyString());
    }

    @Test
    void collect_pull_request_prefers_docs_changes_path_over_branch_name() {
        UUID parserTicketId = UUID.fromString("132bcedf-41e6-4ce7-96b2-4b06511c85fe");
        Mockito.when(persistence.findTicketByProjectIdAndExternalKey(projectId, "PARSER-SPEC-PACK"))
                .thenReturn(Optional.empty());
        Mockito.when(persistence.upsertMinimalTicket(Mockito.eq(projectId), Mockito.eq("PARSER-SPEC-PACK"), Mockito.eq("commit"), Mockito.eq("OPEN"),
                        Mockito.eq(OffsetDateTime.parse("2026-06-17T00:30:00Z")),
                        Mockito.anyString()))
                .thenReturn(new TicketScope(parserTicketId, "PARSER-SPEC-PACK"));
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 50))
                .thenReturn(new PullRequestGraph(
                        50,
                        "commit",
                        null,
                        "https://github.com/acme/widget/pull/50",
                        "open",
                        false,
                        "demo-pr-10",
                        "5555555555555555555555555555555555555555",
                        "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null,
                        null,
                        "octocat",
                        List.of(),
                        "UNKNOWN",
                        List.of(new CommitSnapshot(
                                "5555555555555555555555555555555555555555",
                                "minor docs update",
                                "octocat",
                                "Octo Cat",
                                OffsetDateTime.parse("2026-06-17T00:30:00Z"),
                                "https://github.com/acme/widget/commit/5555555555555555555555555555555555555555"
                        )),
                        List.of(new ChangedFileSnapshot(
                                "docs/changes/PARSER-SPEC-PACK/spec-pack.md",
                                "modified",
                                12,
                                0,
                                "cccc"
                        )),
                        List.of(),
                        List.of()
                ));

        var result = service.collectPullRequest(repositoryId, 50, "tester@example.com");

        assertEquals("SUCCESS", result.status());
        ArgumentCaptor<GitPrMetadataCollectorModels.PullRequestUpsert> prCaptor =
                ArgumentCaptor.forClass(GitPrMetadataCollectorModels.PullRequestUpsert.class);
        Mockito.verify(persistence).upsertPullRequest(prCaptor.capture());
        assertEquals(parserTicketId, prCaptor.getValue().ticketId());
        assertEquals("PARSER-SPEC-PACK", prCaptor.getValue().linkedIssueKey());
    }

    @Test
    void collect_pull_request_prefers_matching_ticket_key_from_body() {
        UUID loginTicketId = UUID.fromString("f5b8aa42-7ef1-49b2-b5de-d07b3cf5a6db");
        Mockito.when(persistence.findTicketByProjectIdAndExternalKey(projectId, "LOGIN"))
                .thenReturn(Optional.of(new TicketScope(loginTicketId, "LOGIN")));
        Mockito.when(persistence.upsertMinimalTicket(Mockito.eq(projectId), Mockito.eq("LOGIN"), Mockito.eq("PDKHOA2505"), Mockito.eq("OPEN"), Mockito.isNull(), Mockito.anyString()))
                .thenReturn(new TicketScope(loginTicketId, "LOGIN"));
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 124))
                .thenReturn(new PullRequestGraph(
                        124,
                        "PDKHOA2505",
                        "Please link LOGIN for this PR",
                        "https://github.com/acme/widget/pull/124",
                        "open",
                        false,
                        "feature/PDKHOA2505",
                        "1111111111111111111111111111111111111111",
                        "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null,
                        null,
                        "octocat",
                        List.of(),
                        "APPROVED",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        var result = service.collectPullRequest(repositoryId, 124, "tester@example.com");

        assertEquals("SUCCESS", result.status());
        ArgumentCaptor<GitPrMetadataCollectorModels.PullRequestUpsert> prCaptor =
                ArgumentCaptor.forClass(GitPrMetadataCollectorModels.PullRequestUpsert.class);
        Mockito.verify(persistence).upsertPullRequest(prCaptor.capture());
        assertEquals(loginTicketId, prCaptor.getValue().ticketId());
        assertEquals("LOGIN", prCaptor.getValue().linkedIssueKey());
        Mockito.verify(persistence).upsertMinimalTicket(Mockito.eq(projectId), Mockito.eq("LOGIN"), Mockito.eq("PDKHOA2505"), Mockito.eq("OPEN"), Mockito.isNull(), Mockito.anyString());
        Mockito.verify(persistence).upsertTraceabilityLink(Mockito.argThat(link ->
                loginTicketId.equals(link.ticketId())
                        && "LOGIN".equals(link.sourceId())
                        && "PULL_REQUEST".equals(link.targetType())
        ));
    }

    @Test
    void collect_pull_request_without_ticket_still_persists_traceability_links() {
        Mockito.when(persistence.findTicketByProjectIdAndExternalKey(projectId, "ABC-123")).thenReturn(Optional.empty());
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 55))
                .thenReturn(new PullRequestGraph(
                        55,
                        "Refactor service layer",
                        null,
                        "https://github.com/acme/widget/pull/55",
                        "closed",
                        false,
                        "feature/refactor-service",
                        "2222222222222222222222222222222222222222",
                        "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null,
                        OffsetDateTime.parse("2026-06-17T02:00:00Z"),
                        "octocat",
                        List.of(),
                        "UNKNOWN",
                        List.of(new CommitSnapshot(
                                "2222222222222222222222222222222222222222",
                                "cleanup",
                                "octocat",
                                "Octo Cat",
                                OffsetDateTime.parse("2026-06-17T00:30:00Z"),
                                "https://github.com/acme/widget/commit/2222222222222222222222222222222222222222"
                        )),
                        List.of(new ChangedFileSnapshot(
                                "src/main/java/com/example/App.java",
                                "modified",
                                1,
                                1,
                                "bbbb"
                        )),
                        List.of(),
                        List.of()
                ));

        var result = service.collectPullRequest(repositoryId, 55, "tester@example.com");

        assertEquals("SUCCESS", result.status());
        ArgumentCaptor<GitPrMetadataCollectorModels.PullRequestUpsert> prCaptor = ArgumentCaptor.forClass(GitPrMetadataCollectorModels.PullRequestUpsert.class);
        Mockito.verify(persistence).upsertPullRequest(prCaptor.capture());
        assertNull(prCaptor.getValue().ticketId());
        assertNull(prCaptor.getValue().linkedIssueKey());
        Mockito.verify(persistence, Mockito.times(2)).upsertTraceabilityLink(Mockito.any());
        Mockito.verify(persistence).findTicketIdByPullRequestId(Mockito.any());
    }

    @Test
    void manual_collect_requires_admin() {
        assertThrows(ForbiddenException.class, () -> service.collectManual(
                new GitPrMetadataCollectorModels.RepositoryScanRequest(repositoryId, null, null, null, false, "tester", "trace"),
                AppUser.builder().role(AppUser.Role.EDITOR).build()));
    }

    // Note: Additional test methods for status normalization, ticket inference, etc.
    // are covered in separate focused test classes:
    // - PrStatusNormalizerTest.java
    // - TicketInferenceHelperTest.java
    // - GitPrMetadataCollectorJdbcAdapterTest.java

    @Test
    void collect_idempotency_rerun_same_pr_no_duplicates() {
        UUID ticketId = UUID.randomUUID();
        Mockito.when(persistence.findTicketByProjectIdAndExternalKey(projectId, "ABC-123"))
                .thenReturn(Optional.of(new TicketScope(ticketId, "ABC-123")));
        Mockito.when(persistence.upsertMinimalTicket(Mockito.eq(projectId), Mockito.eq("ABC-123"), Mockito.eq("ABC-123 Feature"), Mockito.eq("OPEN"), Mockito.isNull(), Mockito.anyString()))
                .thenReturn(new TicketScope(ticketId, "ABC-123"));
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 80))
                .thenReturn(new PullRequestGraph(
                        80, "ABC-123 Feature", null, "https://github.com/acme/widget/pull/80",
                        "open", false, "feature/abc", "hhhh", "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null, null, "user", List.of(), "UNKNOWN",
                        List.of(), List.of(), List.of(), List.of()
                ));

        // Run 1
        var result1 = service.collectPullRequest(repositoryId, 80, "tester@example.com");
        assertEquals("SUCCESS", result1.status());
        assertEquals(1, result1.processedPrCount());

        // Run 2 (same PR) - should still be idempotent
        var result2 = service.collectPullRequest(repositoryId, 80, "tester@example.com");
        assertEquals("SUCCESS", result2.status());
        // Verify that upsertPullRequest was called twice but with same identity
        Mockito.verify(persistence, Mockito.times(2)).upsertPullRequest(Mockito.any());
        Mockito.verify(persistence, Mockito.times(2)).upsertMinimalTicket(Mockito.eq(projectId), Mockito.eq("ABC-123"), Mockito.eq("ABC-123 Feature"), Mockito.eq("OPEN"), Mockito.isNull(), Mockito.anyString());
    }

    @Test
    void collect_reuse_existing_artifact_scanner_ticket() {
        UUID existingTicketId = UUID.randomUUID();
        Mockito.when(persistence.findTicketByProjectIdAndExternalKey(projectId, "ARTIFACT-SCANNER"))
                .thenReturn(Optional.of(new TicketScope(existingTicketId, "ARTIFACT-SCANNER")));
        Mockito.when(persistence.upsertMinimalTicket(
                        Mockito.eq(projectId),
                        Mockito.eq("ARTIFACT-SCANNER"),
                        Mockito.eq("ARTIFACT-SCANNER: Implement scanner"),
                        Mockito.eq("OPEN"),
                        Mockito.isNull(),
                        Mockito.anyString()))
                .thenReturn(new TicketScope(existingTicketId, "ARTIFACT-SCANNER"));
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 90))
                .thenReturn(new PullRequestGraph(
                        90, "ARTIFACT-SCANNER: Implement scanner", null, "https://github.com/acme/widget/pull/90",
                        "open", false, "feature/ARTIFACT-SCANNER", "iiii", "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null, null, "user", List.of(), "UNKNOWN",
                        List.of(), List.of(), List.of(), List.of()
                ));

        var result = service.collectPullRequest(repositoryId, 90, "tester@example.com");

        assertEquals("SUCCESS", result.status());
        ArgumentCaptor<GitPrMetadataCollectorModels.PullRequestUpsert> prCaptor = 
            ArgumentCaptor.forClass(GitPrMetadataCollectorModels.PullRequestUpsert.class);
        Mockito.verify(persistence).upsertPullRequest(prCaptor.capture());
        // Verify that it reused the existing ticket ID
        assertEquals(existingTicketId, prCaptor.getValue().ticketId());
        Mockito.verify(persistence).upsertMinimalTicket(
                Mockito.eq(projectId),
                Mockito.eq("ARTIFACT-SCANNER"),
                Mockito.eq("ARTIFACT-SCANNER: Implement scanner"),
                Mockito.eq("OPEN"),
                Mockito.isNull(),
                Mockito.anyString());
    }

    @Test
    void collect_pull_request_normalizes_comment_only_review_to_review_required() {
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 91))
                .thenReturn(new PullRequestGraph(
                        91,
                        "Minor doc update",
                        null,
                        "https://github.com/acme/widget/pull/91",
                        "open",
                        false,
                        "feature/doc-update",
                        "3333333333333333333333333333333333333333",
                        "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null,
                        null,
                        "octocat",
                        List.of(),
                        "COMMENTED",
                        List.of(new CommitSnapshot(
                                "3333333333333333333333333333333333333333",
                                "doc update",
                                "octocat",
                                "Octo Cat",
                                OffsetDateTime.parse("2026-06-17T00:30:00Z"),
                                "https://github.com/acme/widget/commit/3333333333333333333333333333333333333333"
                        )),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        var result = service.collectPullRequest(repositoryId, 91, "tester@example.com");

        assertEquals("SUCCESS", result.status());
        assertTrue(result.processedPrCount() > 0);
        ArgumentCaptor<GitPrMetadataCollectorModels.PullRequestUpsert> prCaptor =
                ArgumentCaptor.forClass(GitPrMetadataCollectorModels.PullRequestUpsert.class);
        Mockito.verify(persistence).upsertPullRequest(prCaptor.capture());
        assertEquals("REVIEW_REQUIRED", prCaptor.getValue().reviewState());
    }

    @Test
    void collect_pull_request_persists_review_rounds_and_comments_with_submitted_by() {
        Mockito.when(persistence.insertReview(Mockito.any())).thenReturn(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"));
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 92))
                .thenReturn(new PullRequestGraph(
                        92,
                        "Add feature",
                        null,
                        "https://github.com/acme/widget/pull/92",
                        "open",
                        false,
                        "feature/add",
                        "4444444444444444444444444444444444444444",
                        "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null,
                        null,
                        "octocat",
                        List.of(),
                        "APPROVED",
                        List.of(),
                        List.of(),
                        List.of(
                                new ReviewSnapshot(
                                        "review-1",
                                        "octocat",
                                        "APPROVED",
                                        "Looks good to me",
                                        null,
                                        OffsetDateTime.parse("2026-06-17T02:00:00Z"),
                                        "octocat"
                                ),
                                new ReviewSnapshot(
                                        "review-2",
                                        "reviewer2",
                                        "CHANGES_REQUESTED",
                                        null,
                                        null,
                                        OffsetDateTime.parse("2026-06-17T02:30:00Z"),
                                        "reviewer2"
                                )
                        ),
                        List.of(
                                new ReviewCommentSnapshot(
                                        "comment-1",
                                        "review-1",
                                        "octocat",
                                        "nit: rename variable",
                                        "src/main/java/com/example/App.java",
                                        12,
                                        OffsetDateTime.parse("2026-06-17T02:05:00Z")
                                )
                        )
                ));

        var result = service.collectPullRequest(repositoryId, 92, "tester@example.com");

        assertEquals("SUCCESS", result.status());

        ArgumentCaptor<ReviewUpsert> reviewCaptor = ArgumentCaptor.forClass(ReviewUpsert.class);
        Mockito.verify(persistence, Mockito.times(2)).insertReview(reviewCaptor.capture());
        List<ReviewUpsert> reviews = reviewCaptor.getAllValues();
        assertEquals("octocat", reviews.get(0).submittedBy());
        assertEquals(1, reviews.get(0).commentCount());
        assertEquals("reviewer2", reviews.get(1).submittedBy());
        assertEquals(0, reviews.get(1).commentCount());

        ArgumentCaptor<ReviewCommentUpsert> commentCaptor = ArgumentCaptor.forClass(ReviewCommentUpsert.class);
        Mockito.verify(persistence, Mockito.times(2)).insertReviewComment(commentCaptor.capture());
        List<ReviewCommentUpsert> comments = commentCaptor.getAllValues();

        // First insertReviewComment call is for review-1's own body text (only review-1 has a non-blank body).
        assertEquals("Looks good to me", comments.get(0).commentHash());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), comments.get(0).reviewId());

        // Second call is the inline review comment, resolved to review-1's DB id via externalReviewId.
        assertEquals("nit: rename variable", comments.get(1).commentHash());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), comments.get(1).reviewId());
        assertEquals(12, comments.get(1).lineNumber());

        // review-2 has a null body: must NOT produce an extra insertReviewComment call (regression guard).
        assertEquals(2, comments.size());
    }

    @Test
    void collect_pull_request_review_comment_with_unmatched_external_review_id_has_null_review_id() {
        Mockito.when(githubPort.fetchPullRequest("acme/widget", 93))
                .thenReturn(new PullRequestGraph(
                        93,
                        "Add feature",
                        null,
                        "https://github.com/acme/widget/pull/93",
                        "open",
                        false,
                        "feature/add-2",
                        "6666666666666666666666666666666666666666",
                        "main",
                        OffsetDateTime.parse("2026-06-17T00:00:00Z"),
                        OffsetDateTime.parse("2026-06-17T01:00:00Z"),
                        null,
                        null,
                        "octocat",
                        List.of(),
                        "UNKNOWN",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new ReviewCommentSnapshot(
                                        "comment-orphan",
                                        "review-does-not-exist",
                                        "octocat",
                                        "orphan inline comment",
                                        "src/main/java/com/example/App.java",
                                        5,
                                        OffsetDateTime.parse("2026-06-17T02:05:00Z")
                                )
                        )
                ));

        var result = service.collectPullRequest(repositoryId, 93, "tester@example.com");

        assertEquals("SUCCESS", result.status());
        ArgumentCaptor<ReviewCommentUpsert> commentCaptor = ArgumentCaptor.forClass(ReviewCommentUpsert.class);
        Mockito.verify(persistence).insertReviewComment(commentCaptor.capture());
        assertNull(commentCaptor.getValue().reviewId());
        assertEquals("orphan inline comment", commentCaptor.getValue().commentHash());
    }
}
