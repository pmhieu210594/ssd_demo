package com.sdd.platform.application.usecase.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.EvidenceQualityScoreRepositoryPort;
import com.sdd.platform.application.port.out.persistence.ScoreThresholdConfigRepositoryPort;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ArtifactSignal;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.ScoreThreshold;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.CiSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ReviewSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreCriterion;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.ScoreResult;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.SourceSnapshot;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TestSignal;
import com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.TraceabilitySignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceQualityScoreServiceTest {

        private EvidenceQualityScoreRepositoryPort repository;
        private EvidenceQualityScoreService service;

        @BeforeEach
        void setUp() {
                repository = Mockito.mock(EvidenceQualityScoreRepositoryPort.class);
                ScoreThresholdConfigRepositoryPort thresholdRepository = Mockito.mock(ScoreThresholdConfigRepositoryPort.class);
                OffsetDateTime now = OffsetDateTime.now();
                Mockito.when(thresholdRepository.findActiveOrderedByMinScore()).thenReturn(List.of(
                                new ScoreThreshold(UUID.randomUUID(), "CRITICAL", "Critical", 0, 39, "#F43F5E", now, "SYSTEM", now, "SYSTEM"),
                                new ScoreThreshold(UUID.randomUUID(), "RISKY", "Risky", 40, 59, "#F97316", now, "SYSTEM", now, "SYSTEM"),
                                new ScoreThreshold(UUID.randomUUID(), "WARNING", "Warning", 60, 74, "#F59E0B", now, "SYSTEM", now, "SYSTEM"),
                                new ScoreThreshold(UUID.randomUUID(), "GOOD", "Good", 75, 89, "#0EA5E9", now, "SYSTEM", now, "SYSTEM"),
                                new ScoreThreshold(UUID.randomUUID(), "EXCELLENT", "Excellent", 90, 100, "#10B981", now, "SYSTEM", now, "SYSTEM")));
                ScoreThresholdConfigService scoreThresholdConfigService = new ScoreThresholdConfigService(
                                thresholdRepository, Mockito.mock(AdminAuditLogService.class));
                service = new EvidenceQualityScoreService(repository, new ObjectMapper(), scoreThresholdConfigService);
        }

        @Test
        void recalculate_full_snapshot_returns_excellent_and_final() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001001");
                SourceSnapshot source = fullSnapshot(ticketId);
                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(source);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                assertEquals(ticketId, result.ticketId());
                assertEquals(new BigDecimal("100.00"), result.score());
                assertEquals(new BigDecimal("10.00"), result.planScore());
                assertEquals("final", result.snapshotState());
                assertThat(result.missing()).isEmpty();
                assertThat(result.parseErrors()).isEmpty();
                assertThat(result.breakdown()).hasSize(10);
                assertThat(result.breakdown()).extracting(ScoreCriterion::criterionId)
                                .contains("test_plan_results_ac_linkage", "ci_link_present");

                ArgumentCaptor<ScoreResult> captor = ArgumentCaptor.forClass(ScoreResult.class);
                Mockito.verify(repository).save(captor.capture(), Mockito.eq("tester@example.com"));
                assertEquals("v0", captor.getValue().scoreRuleVersion());
        }

        @Test
        void recalculate_impl_plan_missing_ac_mapping_scores_that_section_zero() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001008");
                SourceSnapshot source = fullSnapshot(ticketId);
                ArtifactSignal implPlanWithoutFullMapping = new ArtifactSignal(
                                source.implPlan().artifactSnapshotId(),
                                source.implPlan().artifactTypeCode(),
                                source.implPlan().fileName(),
                                source.implPlan().existsFlag(),
                                source.implPlan().templateEmptyFlag(),
                                source.implPlan().parseStatus(),
                                source.implPlan().parserVersion(),
                                source.implPlan().collectedAt(),
                                source.implPlan().sectionCount(),
                                source.implPlan().tableCount(),
                                source.implPlan().acCount(),
                                source.implPlan().acValidFormatCount(),
                                source.implPlan().finalVerdict(),
                                source.implPlan().requiredFieldsMissing(),
                                source.implPlan().parseErrors(),
                                source.implPlan().sectionPresence(),
                                Map.of(
                                                "parseStatus", "SUCCESS",
                                                "correspondingAcTableMappedAcCount", 4),
                                source.implPlan().contentHash());
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                implPlanWithoutFullMapping,
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                source.ci(),
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                ScoreCriterion implPlan = result.breakdown().stream()
                                .filter(criterion -> "impl_plan_impact_rollback_ac".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();

                assertEquals(new BigDecimal("8.00"), implPlan.score());
                assertEquals("partial", implPlan.status());
                assertEquals(new BigDecimal("98.00"), result.score());
        }

        @Test
        void ci_scoring_no_run_yields_zero() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001050");
                SourceSnapshot source = fullSnapshot(ticketId);
                // replace ci with no run
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                source.implPlan(),
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                null, // ci absent
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                ScoreCriterion ci = result.breakdown().stream()
                                .filter(c -> "ci_link_present".equals(c.criterionId()))
                                .findFirst().orElseThrow();
                assertEquals(new BigDecimal("0.00"), ci.score());
        }

        @Test
        void ci_scoring_failure_run_yields_half_points() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001051");
                SourceSnapshot source = fullSnapshot(ticketId);
                // replace ci with failure status
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                source.implPlan(),
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                new com.sdd.platform.application.usecase.quality.EvidenceQualityScoreModels.CiSignal(
                                                UUID.fromString("00000000-0000-0000-0000-000000009201"),
                                                true,
                                                "FAILURE",
                                                "https://ci.example.test/runs/2",
                                                "job-2",
                                                1,
                                                0,
                                                OffsetDateTime.parse("2026-06-24T00:00:00Z"),
                                                List.of("tbl_fact_ci_run:00000000-0000-0000-0000-000000009201")),
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                ScoreCriterion ci = result.breakdown().stream()
                                .filter(c -> "ci_link_present".equals(c.criterionId()))
                                .findFirst().orElseThrow();
                assertEquals(new BigDecimal("2.50"), ci.score());
        }

        @Test
        void recalculate_impl_plan_header_only_scores_that_section_zero() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001009");
                SourceSnapshot source = fullSnapshot(ticketId);
                ArtifactSignal implPlanHeaderOnly = new ArtifactSignal(
                                source.implPlan().artifactSnapshotId(),
                                source.implPlan().artifactTypeCode(),
                                source.implPlan().fileName(),
                                source.implPlan().existsFlag(),
                                source.implPlan().templateEmptyFlag(),
                                source.implPlan().parseStatus(),
                                source.implPlan().parserVersion(),
                                source.implPlan().collectedAt(),
                                source.implPlan().sectionCount(),
                                source.implPlan().tableCount(),
                                source.implPlan().acCount(),
                                source.implPlan().acValidFormatCount(),
                                source.implPlan().finalVerdict(),
                                source.implPlan().requiredFieldsMissing(),
                                source.implPlan().parseErrors(),
                                source.implPlan().sectionPresence(),
                                Map.of(
                                                "parseStatus", "SUCCESS",
                                                "correspondingAcTableMappedAcCount", 0),
                                source.implPlan().contentHash());
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                implPlanHeaderOnly,
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                source.ci(),
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                ScoreCriterion implPlan = result.breakdown().stream()
                                .filter(criterion -> "impl_plan_impact_rollback_ac".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();

                assertEquals(new BigDecimal("8.00"), implPlan.score());
                assertEquals("partial", implPlan.status());
                assertEquals(new BigDecimal("98.00"), result.score());
        }

        @Test
        void recalculate_spec_pack_open_issue_row_removes_scope_score() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001007");
                SourceSnapshot source = fullSnapshot(ticketId);
                ArtifactSignal specWithOpenIssue = new ArtifactSignal(
                                source.specPack().artifactSnapshotId(),
                                source.specPack().artifactTypeCode(),
                                source.specPack().fileName(),
                                source.specPack().existsFlag(),
                                source.specPack().templateEmptyFlag(),
                                source.specPack().parseStatus(),
                                source.specPack().parserVersion(),
                                source.specPack().collectedAt(),
                                source.specPack().sectionCount(),
                                source.specPack().tableCount(),
                                source.specPack().acCount(),
                                source.specPack().acValidFormatCount(),
                                source.specPack().finalVerdict(),
                                source.specPack().requiredFieldsMissing(),
                                source.specPack().parseErrors(),
                                source.specPack().sectionPresence(),
                                Map.of(
                                                "parseStatus", "SUCCESS",
                                                "open_issue_total_count", 1,
                                                "open_issue_open_count", 1,
                                                "open_issue_closed_count", 0),
                                source.specPack().contentHash());
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                specWithOpenIssue,
                                source.implPlan(),
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                source.ci(),
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                ScoreCriterion specScope = result.breakdown().stream()
                                .filter(criterion -> "spec_pack_scope".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(new BigDecimal("8.00"), specScope.score());
                assertEquals("partial", specScope.status());
                assertThat(result.missing()).contains("spec_pack_scope");
        }

        @Test
        void recalculate_partial_test_coverage_scores_plan_and_results_but_not_full_linkage() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001004");
                SourceSnapshot source = testLinkageSnapshot(ticketId, 10, 0);
                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(source);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                assertEquals(new BigDecimal("86.50"), result.score());
                assertEquals(new BigDecimal("4.00"), result.testScore());

                ScoreCriterion testLinkage = result.breakdown().stream()
                                .filter(criterion -> "test_plan_results_ac_linkage".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(new BigDecimal("4.00"), testLinkage.score());
                assertEquals("partial", testLinkage.status());
        }

        @Test
        void recalculate_full_ac_count_but_failed_tests_still_does_not_get_full_test_linkage() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001006");
                SourceSnapshot source = testLinkageSnapshot(ticketId, OffsetDateTime.parse("2026-06-24T00:00:00Z"), 5,
                                5, 3, 1, 0);
                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(source);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                assertEquals(new BigDecimal("94.00"), result.score());
                assertEquals(new BigDecimal("4.00"), result.testScore());
        }

        @Test
        void recalculate_review_checklist_with_sections_checked_scores_full() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001007");
                SourceSnapshot source = fullSnapshot(ticketId);
                ArtifactSignal reviewChecklist = artifact("REVIEW_CHECKLIST", "review-checklist.md", true, false,
                                "SUCCESS", "v1", source.latestSourceAt(),
                                0, 0, 0, 0, null,
                                List.of(), List.of(),
                                Map.of(),
                                Map.of(
                                                "security_review_checked", true,
                                                "test_review_checked", true),
                                "hash-review-checklist");
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                source.implPlan(),
                                reviewChecklist,
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                source.ci(),
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");
                ScoreCriterion reviewChecklistCriterion = result.breakdown().stream()
                                .filter(criterion -> "review_checklist_security_test".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();

                assertEquals(new BigDecimal("10.00"), reviewChecklistCriterion.score());
        }

        @Test
        void recalculate_review_checklist_without_checked_sections_scores_partial() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001008");
                SourceSnapshot source = fullSnapshot(ticketId);
                ArtifactSignal reviewChecklist = artifact("REVIEW_CHECKLIST", "review-checklist.md", true, false,
                                "SUCCESS", "v1", source.latestSourceAt(),
                                0, 0, 0, 0, null,
                                List.of(), List.of(),
                                Map.of(),
                                Map.of(
                                                "security_review_checked", false,
                                                "test_review_checked", false),
                                "hash-review-checklist");
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                source.implPlan(),
                                reviewChecklist,
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                source.ci(),
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");
                ScoreCriterion reviewChecklistCriterion = result.breakdown().stream()
                                .filter(criterion -> "review_checklist_security_test".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();

                assertEquals(new BigDecimal("4.00"), reviewChecklistCriterion.score());
        }

        @Test
        void recalculate_ci_unknown_status_scores_by_linked_job_ratio() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001009");
                SourceSnapshot source = fullSnapshot(ticketId);
                CiSignal ciSignal = new CiSignal(
                                UUID.fromString("00000000-0000-0000-0000-000000009203"),
                                true,
                                "RUNNING",
                                "https://ci.example.test/runs/2",
                                "job-1",
                                2,
                                1,
                                OffsetDateTime.parse("2026-06-24T00:00:00Z"),
                                List.of("tbl_fact_ci_run:00000000-0000-0000-0000-000000009203"));
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                source.implPlan(),
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                ciSignal,
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");
                ScoreCriterion ciLink = result.breakdown().stream()
                                .filter(criterion -> "ci_link_present".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();

                assertEquals(new BigDecimal("5.00"), ciLink.score());
        }

        @Test
        void recalculate_ci_external_run_id_only_scores_full_ci_link() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001005");
                SourceSnapshot source = fullSnapshot(ticketId);
                CiSignal externalOnlyCi = new CiSignal(
                                UUID.fromString("00000000-0000-0000-0000-000000009201"),
                                true,
                                "SUCCESS",
                                "run-1",
                                "job-1",
                                1,
                                1,
                                OffsetDateTime.parse("2026-06-24T00:00:00Z"),
                                List.of("tbl_fact_ci_run:00000000-0000-0000-0000-000000009201"));
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                source.implPlan(),
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                externalOnlyCi,
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                ScoreCriterion ciLink = result.breakdown().stream()
                                .filter(criterion -> "ci_link_present".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(new BigDecimal("5.00"), ciLink.score());
                assertEquals("complete", ciLink.status());
        }

        @Test
        void recalculate_ci_failed_job_counts_as_zero_point() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001006");
                SourceSnapshot source = fullSnapshot(ticketId);
                CiSignal multiJobCi = new CiSignal(
                                UUID.fromString("00000000-0000-0000-0000-000000009202"),
                                true,
                                "SUCCESS",
                                "https://ci.example.test/runs/2",
                                "job-3",
                                5,
                                4,
                                OffsetDateTime.parse("2026-06-24T00:00:00Z"),
                                List.of(
                                                "tbl_fact_ci_run:00000000-0000-0000-0000-000000009202",
                                                "tbl_fact_ci_run:00000000-0000-0000-0000-000000009203",
                                                "tbl_fact_ci_run:00000000-0000-0000-0000-000000009204",
                                                "tbl_fact_ci_run:00000000-0000-0000-0000-000000009205"));
                SourceSnapshot adjusted = new SourceSnapshot(
                                source.ticketId(),
                                source.projectId(),
                                source.repositoryId(),
                                source.specPack(),
                                source.implPlan(),
                                source.reviewChecklist(),
                                source.selfReview(),
                                source.testPlan(),
                                source.testResults(),
                                source.blackboxTestcases(),
                                source.report(),
                                source.review(),
                                multiJobCi,
                                source.test(),
                                source.traceability(),
                                source.latestSourceAt());

                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(adjusted);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculate(ticketId, null, "tester@example.com");

                ScoreCriterion ciLink = result.breakdown().stream()
                                .filter(criterion -> "ci_link_present".equals(criterion.criterionId()))
                                .findFirst()
                                .orElseThrow();
                assertEquals(new BigDecimal("5.00"), ciLink.score());
                assertEquals("complete", ciLink.status());
        }

        @Test
        void recalculate_missing_evidence_reports_missing_and_partial() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001002");
                SourceSnapshot source = partialSnapshot(ticketId);
                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(source);
                Mockito.when(repository.save(Mockito.any(), Mockito.anyString()))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ScoreResult result = service.recalculateFromParser(ticketId, null, "tester@example.com");

                assertThat(result.score()).isLessThan(BigDecimal.valueOf(100));
                assertEquals("partial", result.snapshotState());
                assertThat(result.missing()).contains("spec-pack.md", "impl-plan.md", "review-checklist.md", "ci_run");
                assertThat(result.parseErrors()).contains("review_source_unavailable");
        }

        @Test
        void latest_becomes_stale_when_source_changes_after_persistence() {
                UUID ticketId = UUID.fromString("00000000-0000-0000-0000-000000001003");
                ScoreResult persisted = fullResult(ticketId, "final", OffsetDateTime.parse("2026-06-24T00:00:00Z"));
                SourceSnapshot current = fullSnapshot(ticketId, OffsetDateTime.parse("2026-06-24T01:00:00Z"));

                Mockito.when(repository.findLatest(ticketId)).thenReturn(java.util.Optional.of(persisted));
                Mockito.when(repository.loadSourceSnapshot(ticketId)).thenReturn(current);

                ScoreResult result = service.latest(ticketId);

                assertEquals("stale", result.snapshotState());
        }

        @Test
        void recalculate_rejects_invalid_ticket_id() {
                assertThrows(IllegalArgumentException.class,
                                () -> service.recalculate("not-a-uuid", null, "tester@example.com", true));
        }

        private SourceSnapshot fullSnapshot(UUID ticketId) {
                return testLinkageSnapshot(ticketId, OffsetDateTime.parse("2026-06-24T00:00:00Z"), 5, 5);
        }

        private SourceSnapshot fullSnapshot(UUID ticketId, int distinctCoveredAcCount) {
                return testLinkageSnapshot(ticketId, OffsetDateTime.parse("2026-06-24T00:00:00Z"), 5,
                                distinctCoveredAcCount);
        }

        private SourceSnapshot fullSnapshot(UUID ticketId, OffsetDateTime latestAt) {
                return testLinkageSnapshot(ticketId, latestAt, 5, 5);
        }

        private SourceSnapshot testLinkageSnapshot(UUID ticketId, int specPackAcCount, int distinctCoveredAcCount) {
                return testLinkageSnapshot(ticketId, OffsetDateTime.parse("2026-06-24T00:00:00Z"), specPackAcCount,
                                distinctCoveredAcCount);
        }

        private SourceSnapshot testLinkageSnapshot(UUID ticketId, OffsetDateTime latestAt, int specPackAcCount,
                        int distinctCoveredAcCount) {
                return testLinkageSnapshot(ticketId, latestAt, specPackAcCount, distinctCoveredAcCount, 3, 0, 0);
        }

        private SourceSnapshot testLinkageSnapshot(UUID ticketId, OffsetDateTime latestAt, int specPackAcCount,
                        int distinctCoveredAcCount,
                        int passedCount, int failedCount, int skippedCount) {
                return new SourceSnapshot(
                                ticketId,
                                UUID.fromString("00000000-0000-0000-0000-000000009001"),
                                UUID.fromString("00000000-0000-0000-0000-000000009002"),
                                artifact("SPEC_PACK", "spec-pack.md", true, false, "SUCCESS", "v1", latestAt,
                                                6, 1, specPackAcCount, 5, null,
                                                List.of(), List.of(),
                                                Map.of(
                                                                "SCOPE", true,
                                                                "SCOPE_WITHIN_RANGE", true,
                                                                "SCOPE_OUT_OF_RANGE", true,
                                                                "OPEN_ISSUES", true,
                                                                "SECURITY_PRIVACY_IMPACT", true,
                                                                "OPERATION_MAINTENANCE_IMPACT", true),
                                                Map.of("parseStatus", "SUCCESS"),
                                                "hash-spec"),
                                artifact("IMPL_PLAN", "impl-plan.md", true, false, "SUCCESS", "v1", latestAt,
                                                14, 1, 0, 0, null,
                                                List.of(), List.of(),
                                                Map.of(
                                                                "IMPLEMENTATION_PRINCIPLE", true,
                                                                "ALTERNATIVE_PLAN", true,
                                                                "MIGRATION_ROLLBACK_POLICY", true,
                                                                "CORRESPONDING_AC_TABLE", true,
                                                                "STEP_IMPLEMENTATION", true),
                                                Map.of("parseStatus", "SUCCESS", "correspondingAcTableMappedAcCount",
                                                                specPackAcCount),
                                                "hash-impl"),
                                artifact("REVIEW_CHECKLIST", "review-checklist.md", true, false, null, null, latestAt,
                                                0, 0, 0, 0, null, List.of(), List.of(), Map.of(), Map.of(
                                                                "security_review_checked", true,
                                                                "test_review_checked", true),
                                                "hash-review-checklist"),
                                artifact("SELF_REVIEW", "self-review.md", true, false, "SUCCESS", "v1", latestAt,
                                                11, 6, 0, 0, "PASS",
                                                List.of(), List.of(),
                                                Map.of(
                                                                "CÁC_LỆNH_ĐÃ_CHẠY", true,
                                                                "SELF_CHECK_USING_REVIEW_CHECKLIST", true,
                                                                "KNOWN_RISKS", true,
                                                                "ITEMS_REVIEWED_BY_HUMANS", true,
                                                                "FINAL_SELF_VERDICT", true),
                                                Map.of("parseStatus", "SUCCESS", "final_verdict", "PASS"),
                                                "hash-self"),
                                artifact("TEST_PLAN", "test-plan.md", true, false, "SUCCESS", "v1", latestAt,
                                                10, 3, 0, 0, null,
                                                List.of(), List.of(),
                                                Map.of(
                                                                "AC_MATRIX_TEST_TYPE", true,
                                                                "ADDITIONAL_TEST_THIS_TIME", true,
                                                                "EXECUTION_COMMAND", true,
                                                                "STOP_CONDITION", true),
                                                Map.of("parseStatus", "SUCCESS"),
                                                "hash-test-plan"),
                                artifact("TEST_RESULTS", "test-results.md", true, false, "SUCCESS", "v1", latestAt,
                                                10, 3, 0, 0, null,
                                                List.of(), List.of(),
                                                Map.of(
                                                                "EXECUTION_ENVIRONMENT", true,
                                                                "EXECUTED_COMMAND", true,
                                                                "SUMMARY_OF_RESULTS", true,
                                                                "FINAL_TEST_VERDICT", true),
                                                Map.of("parseStatus", "SUCCESS"),
                                                "hash-test-results"),
                                artifact("BLACKBOX_TESTCASES", "blackbox-testcases.md", true, false, null, null,
                                                latestAt,
                                                0, 0, 0, 0, null, List.of(), List.of(), Map.of(), Map.of(),
                                                "hash-blackbox"),
                                artifact("REPORT", "report.md", true, false, null, null, latestAt,
                                                0, 0, 0, 0, null, List.of(), List.of(),
                                                Map.of(
                                                                "EDITED_SUMMARY", true,
                                                                "SCOPE_OF_INFLUENCE", true,
                                                                "REVIEW_RESULTS", true,
                                                                "TEST_RESULTS", true,
                                                                "ACCEPTED_RISK", true,
                                                                "OPEN_ISSUES", true),
                                                Map.of("parseStatus", "SUCCESS"),
                                                "hash-report"),
                                new ReviewSignal(
                                                UUID.fromString("00000000-0000-0000-0000-000000009101"),
                                                true,
                                                "APPROVED",
                                                2,
                                                0,
                                                latestAt,
                                                List.of("tbl_fact_review:00000000-0000-0000-0000-000000009101")),
                                new CiSignal(
                                                UUID.fromString("00000000-0000-0000-0000-000000009201"),
                                                true,
                                                "SUCCESS",
                                                "https://ci.example.test/runs/1",
                                                "job-1",
                                                1,
                                                1,
                                                latestAt,
                                                List.of("tbl_fact_ci_run:00000000-0000-0000-0000-000000009201")),
                                new TestSignal(1, 3, passedCount, failedCount, skippedCount, distinctCoveredAcCount,
                                                distinctCoveredAcCount,
                                                failedCount > 0 ? "FAILED" : "SUCCESS",
                                                true, latestAt,
                                                List.of("tbl_fact_test_run:00000000-0000-0000-0000-000000009301")),
                                new TraceabilitySignal(5, 5, 5, true,
                                                List.of("ticket->spec", "spec->pr", "pr->ci", "ci->test",
                                                                "test->report"),
                                                List.of("tbl_fact_traceability_link:00000000-0000-0000-0000-000000009401"),
                                                latestAt),
                                latestAt);
        }

        private SourceSnapshot partialSnapshot(UUID ticketId) {
                return new SourceSnapshot(
                                ticketId,
                                UUID.fromString("00000000-0000-0000-0000-000000009001"),
                                UUID.fromString("00000000-0000-0000-0000-000000009002"),
                                null,
                                null,
                                null,
                                artifact("SELF_REVIEW", "self-review.md", true, false, "FAILED", "v1",
                                                OffsetDateTime.parse("2026-06-24T00:00:00Z"), 0, 0, 0, 0, null,
                                                List.of("section:RUN_COMMAND_AND_RESULTS"),
                                                List.of("SELF_REVIEW:FAILED"), Map.of(), Map.of(), "hash-self"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                new TraceabilitySignal(0, 5, 0, false, List.of(), List.of(), null),
                                OffsetDateTime.parse("2026-06-24T00:00:00Z"));
        }

        private ScoreResult fullResult(UUID ticketId, String snapshotState, OffsetDateTime calculatedAt) {
                return new ScoreResult(
                                UUID.fromString("00000000-0000-0000-0000-00000000f001"),
                                UUID.fromString("00000000-0000-0000-0000-00000000f002"),
                                ticketId,
                                new BigDecimal("100.00"),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                "v0",
                                snapshotState,
                                calculatedAt,
                                new BigDecimal("25.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("15.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("5.00"),
                                new BigDecimal("10.00"),
                                List.of());
        }

        private ArtifactSignal artifact(String typeCode,
                        String fileName,
                        boolean existsFlag,
                        boolean templateEmptyFlag,
                        String parseStatus,
                        String parserVersion,
                        OffsetDateTime collectedAt,
                        int sectionCount,
                        int tableCount,
                        int acCount,
                        int acValidFormatCount,
                        String finalVerdict,
                        List<String> requiredFieldsMissing,
                        List<String> parseErrors,
                        Map<String, Boolean> sectionPresence,
                        Map<String, Object> parsedSummary,
                        String contentHash) {
                return new ArtifactSignal(
                                UUID.randomUUID(),
                                typeCode,
                                fileName,
                                existsFlag,
                                templateEmptyFlag,
                                parseStatus,
                                parserVersion,
                                collectedAt,
                                sectionCount,
                                tableCount,
                                acCount,
                                acValidFormatCount,
                                finalVerdict,
                                requiredFieldsMissing,
                                parseErrors,
                                sectionPresence,
                                parsedSummary,
                                contentHash);
        }
}
