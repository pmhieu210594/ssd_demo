package com.sdd.platform.application.port.out.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TestEvidencePersistencePort {

        record PlannedTestCaseRecord(
                        UUID artifactSnapshotId,
                        UUID ticketId,
                        String testCaseKey,
                        String testCaseName,
                        OffsetDateTime collectedAt) {
        }

        record TestCaseResultRecord(
                        UUID ticketId,
                        String testCaseKey,
                        String status,
                        String failureSummary,
                        UUID testRunId) {
        }

        record TestCaseAcRecord(
                        UUID testCaseId,
                        UUID ticketId,
                        String acKey) {
        }

        void replacePlannedCoverage(UUID snapshotId, UUID ticketId, String acTextHash, List<String> acKeys);

        void upsertPlannedTestCases(List<PlannedTestCaseRecord> records);

        void updateTestCaseResults(List<TestCaseResultRecord> records);

        void upsertTestCaseAcMappings(List<TestCaseAcRecord> records);

        void linkTestCasesToPlannedCoverage(UUID ticketId);

        void updateExecutedCoverageFromJunction(UUID ticketId, UUID testRunId, UUID artifactSnapshotId);

        void deleteTestEvidenceByTicketId(UUID ticketId);

        TestRunRecord upsertTestRun(TestRunRecord testRunRecord);

        List<ExecutedCoverageRecord> upsertExecutedCoverageRows(List<ExecutedCoverageRecord> records);

        record TestRunRecord(
                        UUID testRunId,
                        UUID repositoryId,
                        UUID ticketId,
                        UUID prId,
                        UUID ciRunId,
                        String externalTestRunId,
                        String testType,
                        String status,
                        int testCount,
                        int passedCount,
                        int failedCount,
                        int skippedCount,
                        Integer durationSeconds,
                        BigDecimal coveragePercent,
                        OffsetDateTime startedAt,
                        OffsetDateTime finishedAt,
                        OffsetDateTime collectedAt) {
        }

        record ExecutedCoverageRecord(
                        UUID testCaseId,
                        UUID testRunId,
                        UUID ticketId,
                        UUID repositoryId,
                        UUID ciRunId,
                        UUID artifactSnapshotId,
                        String testCaseKey,
                        String testCaseNameHash,
                        String acReference,
                        String testCaseStatus,
                        String coverageStatus,
                        Integer durationMs,
                        String failureSummary,
                        boolean flakyCandidateFlag,
                        String acTextHash,
                        OffsetDateTime collectedAt) {
        }
}
