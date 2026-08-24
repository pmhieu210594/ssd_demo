package com.sdd.platform.infrastructure.persistence.adapter.docparse;

import com.sdd.platform.application.port.out.persistence.TestEvidencePersistencePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class TestEvidenceJdbcAdapter implements TestEvidencePersistencePort {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final NamedParameterJdbcTemplate jdbc;

    public TestEvidenceJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replacePlannedCoverage(UUID snapshotId, UUID ticketId, String acTextHash, List<String> acKeys) {
        jdbc.update("""
                DELETE FROM tbl_fact_ac_test_coverage
                WHERE ticket_id = :ticketId
                  AND test_run_id IS NULL
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId));

        if (acKeys == null || acKeys.isEmpty()) {
            return;
        }

        Set<String> distinctAcKeys = new LinkedHashSet<>(acKeys);
        for (String acKey : distinctAcKeys) {
            // Try to update an existing PLANNED row that is not linked to a test case or
            // test run
            int updated = jdbc.update("""
                    UPDATE tbl_fact_ac_test_coverage
                    SET artifact_snapshot_id = :snapshotId,
                        ac_text_hash = :acTextHash,
                        coverage_status = 'PLANNED',
                        calculated_at = now(),
                        updated_at = now(),
                        updated_by = :actor
                    WHERE ticket_id = :ticketId
                      AND ac_key = :acKey
                      AND test_run_id IS NULL
                      AND test_case_id IS NULL
                    """,
                    new MapSqlParameterSource()
                            .addValue("snapshotId", snapshotId)
                            .addValue("acTextHash", acTextHash)
                            .addValue("ticketId", ticketId)
                            .addValue("acKey", acKey)
                            .addValue("actor", SYSTEM_ACTOR));

            if (updated == 0) {
                // No existing PLANNED unlinked row — insert a new PLANNED row
                jdbc.update(
                        """
                                INSERT INTO tbl_fact_ac_test_coverage (
                                    ticket_id,
                                    artifact_snapshot_id,
                                    ac_id,
                                    ac_key,
                                    ac_text_hash,
                                    coverage_status,
                                    calculated_at,
                                    created_at,
                                    created_by,
                                    updated_at,
                                    updated_by
                                )
                                VALUES (
                                    :ticketId,
                                    :snapshotId,
                                    (SELECT ac_id FROM tbl_fact_acceptance_criteria WHERE ticket_id = :ticketId AND ac_key = :acKey LIMIT 1),
                                    :acKey,
                                    :acTextHash,
                                    'PLANNED',
                                    now(),
                                    now(),
                                    :actor,
                                    now(),
                                    :actor
                                )
                                """,
                        new MapSqlParameterSource()
                                .addValue("ticketId", ticketId)
                                .addValue("snapshotId", snapshotId)
                                .addValue("acKey", acKey)
                                .addValue("acTextHash", acTextHash)
                                .addValue("actor", SYSTEM_ACTOR));
            }
        }
    }

    @Override
    public void upsertPlannedTestCases(List<PlannedTestCaseRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (PlannedTestCaseRecord r : records) {
            if (r.testCaseKey() == null || r.testCaseKey().isBlank() || r.ticketId() == null) {
                continue;
            }
            String basis = String.join("|", safe(r.ticketId()), safe(r.testCaseKey()));
            UUID testCaseId = UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));
            String nameHash = r.testCaseName() != null && !r.testCaseName().isBlank() ? sha256(r.testCaseName()) : null;
            jdbc.update("""
                    INSERT INTO tbl_fact_test_case (
                        test_case_id,
                        ticket_id,
                        test_case_key,
                        test_case_name_hash,
                        status,
                        created_at,
                        created_by,
                        updated_at,
                        updated_by
                    )
                    VALUES (
                        :testCaseId,
                        :ticketId,
                        :testCaseKey,
                        :nameHash,
                        'UNKNOWN',
                        COALESCE(:collectedAt, now()),
                        :actor,
                        COALESCE(:collectedAt, now()),
                        :actor
                    )
                    ON CONFLICT (ticket_id, test_case_key) DO UPDATE SET
                        test_case_name_hash = EXCLUDED.test_case_name_hash,
                        updated_at = now()
                    """,
                    new MapSqlParameterSource()
                            .addValue("testCaseId", testCaseId)
                            .addValue("ticketId", r.ticketId())
                            .addValue("testCaseKey", r.testCaseKey())
                            .addValue("nameHash", nameHash)
                            .addValue("collectedAt", r.collectedAt())
                            .addValue("actor", SYSTEM_ACTOR));
        }
    }

    @Override
    public void updateTestCaseResults(List<TestCaseResultRecord> records) {
        if (records == null || records.isEmpty())
            return;
        for (TestCaseResultRecord r : records) {
            if (r.ticketId() == null || r.testCaseKey() == null || r.testCaseKey().isBlank())
                continue;
            String basis = String.join("|", safe(r.ticketId()), safe(r.testCaseKey()));
            UUID testCaseId = UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));
            jdbc.update("""
                    INSERT INTO tbl_fact_test_case (
                        test_case_id,
                        test_run_id,
                        ticket_id,
                        test_case_key,
                        status,
                        failure_summary,
                        created_at,
                        created_by,
                        updated_at,
                        updated_by
                    )
                    VALUES (
                        :testCaseId,
                        :testRunId,
                        :ticketId,
                        :testCaseKey,
                        CAST(:status AS run_status),
                        :failureSummary,
                        COALESCE(:collectedAt, now()),
                        :actor,
                        COALESCE(:collectedAt, now()),
                        :actor
                    )
                    ON CONFLICT (ticket_id, test_case_key) DO UPDATE SET
                        test_run_id = EXCLUDED.test_run_id,
                        status = EXCLUDED.status,
                        failure_summary = EXCLUDED.failure_summary,
                        updated_at = EXCLUDED.updated_at,
                        updated_by = EXCLUDED.updated_by
                    """,
                    new MapSqlParameterSource()
                            .addValue("testCaseId", testCaseId)
                            .addValue("testRunId", r.testRunId())
                            .addValue("ticketId", r.ticketId())
                            .addValue("testCaseKey", r.testCaseKey())
                            .addValue("status", r.status())
                            .addValue("failureSummary", r.failureSummary())
                            .addValue("collectedAt", null)
                            .addValue("actor", SYSTEM_ACTOR));
        }
    }

    @Override
    public void upsertTestCaseAcMappings(List<TestCaseAcRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // Delete existing mappings per ticket then re-insert
        Set<UUID> ticketIds = new LinkedHashSet<>();
        for (TestCaseAcRecord r : records) {
            if (r.ticketId() != null)
                ticketIds.add(r.ticketId());
        }
        for (UUID ticketId : ticketIds) {
            jdbc.update("DELETE FROM tbl_fact_test_case_ac WHERE ticket_id = :ticketId",
                    new MapSqlParameterSource("ticketId", ticketId));
        }
        for (TestCaseAcRecord r : records) {
            if (r.testCaseId() == null || r.acKey() == null || r.acKey().isBlank() || r.ticketId() == null) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO tbl_fact_test_case_ac (
                        test_case_id,
                        ticket_id,
                        ac_key,
                        ac_id,
                        created_at,
                        created_by
                    )
                    VALUES (
                        :testCaseId,
                        :ticketId,
                        :acKey,
                        (SELECT ac_id FROM tbl_fact_acceptance_criteria
                          WHERE ticket_id = :ticketId AND ac_key = :acKey LIMIT 1),
                        now(),
                        :actor
                    )
                    """,
                    new MapSqlParameterSource()
                            .addValue("testCaseId", r.testCaseId())
                            .addValue("ticketId", r.ticketId())
                            .addValue("acKey", r.acKey())
                            .addValue("actor", SYSTEM_ACTOR));
        }
    }

    @Override
    public TestRunRecord upsertTestRun(TestRunRecord testRunRecord) {
        UUID id = resolveTestRunId(testRunRecord);
        jdbc.update("""
                INSERT INTO tbl_fact_test_run (
                    test_run_id,
                    repository_id,
                    ticket_id,
                    pr_id,
                    ci_run_id,
                    external_test_run_id,
                    test_type,
                    status,
                    test_count,
                    passed_count,
                    failed_count,
                    skipped_count,
                    duration_seconds,
                    coverage_percent,
                    started_at,
                    finished_at,
                    collected_at
                )
                VALUES (
                    :testRunId,
                    :repositoryId,
                    :ticketId,
                    :prId,
                    :ciRunId,
                    :externalTestRunId,
                    :testType,
                    CAST(:status AS run_status),
                    :testCount,
                    :passedCount,
                    :failedCount,
                    :skippedCount,
                    :durationSeconds,
                    :coveragePercent,
                    :startedAt,
                    :finishedAt,
                    COALESCE(:collectedAt, now())
                )
                ON CONFLICT (test_run_id) DO UPDATE SET
                    repository_id = EXCLUDED.repository_id,
                    ticket_id = EXCLUDED.ticket_id,
                    pr_id = EXCLUDED.pr_id,
                    ci_run_id = EXCLUDED.ci_run_id,
                    external_test_run_id = EXCLUDED.external_test_run_id,
                    test_type = EXCLUDED.test_type,
                    status = EXCLUDED.status,
                    test_count = EXCLUDED.test_count,
                    passed_count = EXCLUDED.passed_count,
                    failed_count = EXCLUDED.failed_count,
                    skipped_count = EXCLUDED.skipped_count,
                    duration_seconds = EXCLUDED.duration_seconds,
                    coverage_percent = EXCLUDED.coverage_percent,
                    started_at = EXCLUDED.started_at,
                    finished_at = EXCLUDED.finished_at,
                    collected_at = EXCLUDED.collected_at
                """,
                new MapSqlParameterSource()
                        .addValue("testRunId", id)
                        .addValue("repositoryId", testRunRecord.repositoryId())
                        .addValue("ticketId", testRunRecord.ticketId())
                        .addValue("prId", testRunRecord.prId())
                        .addValue("ciRunId", testRunRecord.ciRunId())
                        .addValue("externalTestRunId", testRunRecord.externalTestRunId())
                        .addValue("testType", testRunRecord.testType())
                        .addValue("status", testRunRecord.status())
                        .addValue("testCount", testRunRecord.testCount())
                        .addValue("passedCount", testRunRecord.passedCount())
                        .addValue("failedCount", testRunRecord.failedCount())
                        .addValue("skippedCount", testRunRecord.skippedCount())
                        .addValue("durationSeconds", testRunRecord.durationSeconds())
                        .addValue("coveragePercent", testRunRecord.coveragePercent())
                        .addValue("startedAt", testRunRecord.startedAt())
                        .addValue("finishedAt", testRunRecord.finishedAt())
                        .addValue("collectedAt", testRunRecord.collectedAt()));
        return new TestRunRecord(
                id,
                testRunRecord.repositoryId(),
                testRunRecord.ticketId(),
                testRunRecord.prId(),
                testRunRecord.ciRunId(),
                testRunRecord.externalTestRunId(),
                testRunRecord.testType(),
                testRunRecord.status(),
                testRunRecord.testCount(),
                testRunRecord.passedCount(),
                testRunRecord.failedCount(),
                testRunRecord.skippedCount(),
                testRunRecord.durationSeconds(),
                testRunRecord.coveragePercent(),
                testRunRecord.startedAt(),
                testRunRecord.finishedAt(),
                testRunRecord.collectedAt());
    }

    @Override
    public List<ExecutedCoverageRecord> upsertExecutedCoverageRows(List<ExecutedCoverageRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<ExecutedCoverageRecord> saved = new ArrayList<>();
        for (ExecutedCoverageRecord coverageRecord : records) {
            ExecutedCoverageRecord savedRecord = upsertExecutedCoverageRow(coverageRecord);
            if (savedRecord != null) {
                saved.add(savedRecord);
            }
        }
        return saved;
    }

    private ExecutedCoverageRecord upsertExecutedCoverageRow(ExecutedCoverageRecord coverageRecord) {
        if (coverageRecord == null || coverageRecord.testRunId() == null || coverageRecord.ticketId() == null) {
            return null;
        }

        UUID testCaseId = resolveTestCaseId(coverageRecord);
        jdbc.update("""
                INSERT INTO tbl_fact_test_case (
                    test_case_id,
                    test_run_id,
                    ticket_id,
                    test_case_key,
                    test_case_name_hash,
                    status,
                    duration_ms,
                    failure_summary,
                    flaky_candidate_flag,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by
                )
                VALUES (
                    :testCaseId,
                    :testRunId,
                    :ticketId,
                    :testCaseKey,
                    :testCaseNameHash,
                    CAST(:status AS run_status),
                    :durationMs,
                    :failureSummary,
                    :flakyCandidateFlag,
                    COALESCE(:collectedAt, now()),
                    :actor,
                    COALESCE(:collectedAt, now()),
                    :actor
                )
                ON CONFLICT (ticket_id, test_case_key) DO UPDATE SET
                    test_run_id = EXCLUDED.test_run_id,
                    test_case_name_hash = EXCLUDED.test_case_name_hash,
                    status = EXCLUDED.status,
                    duration_ms = EXCLUDED.duration_ms,
                    failure_summary = EXCLUDED.failure_summary,
                    flaky_candidate_flag = EXCLUDED.flaky_candidate_flag,
                    updated_at = EXCLUDED.updated_at,
                    updated_by = EXCLUDED.updated_by
                """,
                new MapSqlParameterSource()
                        .addValue("testCaseId", testCaseId)
                        .addValue("testRunId", coverageRecord.testRunId())
                        .addValue("ticketId", coverageRecord.ticketId())
                        .addValue("testCaseKey", coverageRecord.testCaseKey())
                        .addValue("testCaseNameHash", coverageRecord.testCaseNameHash())
                        .addValue("status", coverageRecord.testCaseStatus())
                        .addValue("durationMs", coverageRecord.durationMs())
                        .addValue("failureSummary", coverageRecord.failureSummary())
                        .addValue("flakyCandidateFlag", coverageRecord.flakyCandidateFlag())
                        .addValue("collectedAt", coverageRecord.collectedAt())
                        .addValue("actor", SYSTEM_ACTOR));

        return new ExecutedCoverageRecord(
                testCaseId,
                coverageRecord.testRunId(),
                coverageRecord.ticketId(),
                coverageRecord.repositoryId(),
                coverageRecord.ciRunId(),
                coverageRecord.artifactSnapshotId(),
                coverageRecord.testCaseKey(),
                coverageRecord.testCaseNameHash(),
                coverageRecord.acReference(),
                coverageRecord.testCaseStatus(),
                coverageRecord.coverageStatus(),
                coverageRecord.durationMs(),
                coverageRecord.failureSummary(),
                coverageRecord.flakyCandidateFlag(),
                coverageRecord.acTextHash(),
                coverageRecord.collectedAt());
    }

    @Override
    public void linkTestCasesToPlannedCoverage(UUID ticketId) {
        if (ticketId == null) {
            return;
        }
        jdbc.update("""
                UPDATE tbl_fact_ac_test_coverage cov
                SET test_case_id = m.test_case_id,
                    updated_at   = now(),
                    updated_by   = :actor
                FROM tbl_fact_test_case_ac m
                WHERE cov.ticket_id    = :ticketId
                  AND m.ticket_id      = :ticketId
                  AND cov.ac_key       = m.ac_key
                  AND cov.test_case_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM tbl_fact_ac_test_coverage existing
                      WHERE existing.ticket_id = cov.ticket_id
                        AND existing.ac_key = cov.ac_key
                        AND existing.test_case_id = m.test_case_id
                        AND existing.test_run_id IS NOT NULL
                  )
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("actor", SYSTEM_ACTOR));
    }

    @Override
    public void deleteTestEvidenceByTicketId(UUID ticketId) {
        if (ticketId == null) {
            return;
        }
        jdbc.update("""
                DELETE FROM tbl_fact_ac_test_coverage
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId));
        jdbc.update("""
                DELETE FROM tbl_fact_test_case_ac
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId));
        jdbc.update("""
                DELETE FROM tbl_fact_test_case
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId));
        jdbc.update("""
                DELETE FROM tbl_fact_test_run
                WHERE ticket_id = :ticketId
                """,
                new MapSqlParameterSource("ticketId", ticketId));
    }

    @Override
    public void updateExecutedCoverageFromJunction(UUID ticketId, UUID testRunId, UUID artifactSnapshotId) {
        if (ticketId == null || testRunId == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO tbl_fact_ac_test_coverage (
                    ticket_id,
                    artifact_snapshot_id,
                    ac_id,
                    ac_key,
                    test_case_id,
                    test_run_id,
                    coverage_status,
                    calculated_at,
                    created_at,
                    created_by,
                    updated_at,
                    updated_by
                )
                SELECT
                    tc.ticket_id,
                    :artifactSnapshotId,
                    m.ac_id,
                    m.ac_key,
                    m.test_case_id,
                    tc.test_run_id,
                    CASE tc.status::text
                        WHEN 'SUCCESS' THEN 'PASSED'
                        WHEN 'FAILED' THEN 'FAILED'
                        ELSE 'UNKNOWN'
                    END,
                    now(), now(), :actor, now(), :actor
                FROM tbl_fact_test_case tc
                JOIN tbl_fact_test_case_ac m ON m.test_case_id = tc.test_case_id
                WHERE tc.ticket_id = :ticketId
                  AND tc.test_run_id = :testRunId
                ON CONFLICT (ticket_id, ac_key, test_case_id) DO UPDATE SET
                    artifact_snapshot_id = EXCLUDED.artifact_snapshot_id,
                    ac_id                = EXCLUDED.ac_id,
                    test_run_id          = EXCLUDED.test_run_id,
                    coverage_status      = EXCLUDED.coverage_status,
                    calculated_at        = EXCLUDED.calculated_at,
                    updated_at           = EXCLUDED.updated_at,
                    updated_by           = EXCLUDED.updated_by
                """,
                new MapSqlParameterSource()
                        .addValue("ticketId", ticketId)
                        .addValue("testRunId", testRunId)
                        .addValue("artifactSnapshotId", artifactSnapshotId)
                        .addValue("actor", SYSTEM_ACTOR));
    }

    private UUID resolveTestCaseId(ExecutedCoverageRecord coverageRecord) {
        if (coverageRecord.testCaseId() != null) {
            return coverageRecord.testCaseId();
        }
        String basis = String.join("|", safe(coverageRecord.ticketId()), safe(coverageRecord.testCaseKey()));
        return UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8));
    }

    private UUID resolveTestRunId(TestRunRecord testRunRecord) {
        if (testRunRecord.testRunId() != null) {
            return testRunRecord.testRunId();
        }
        if (testRunRecord.ticketId() == null) {
            return UUID.randomUUID();
        }

        try {
            UUID existingTestRunId = jdbc.queryForObject("""
                    SELECT test_run_id
                    FROM tbl_fact_test_run
                    WHERE ticket_id = :ticketId
                    ORDER BY COALESCE(collected_at, now()) DESC, test_run_id DESC
                    LIMIT 1
                    """,
                    new MapSqlParameterSource("ticketId", testRunRecord.ticketId()),
                    UUID.class);
            if (existingTestRunId != null) {
                return existingTestRunId;
            }
        } catch (EmptyResultDataAccessException ignored) {
            // No existing test run for this ticket yet.
        }
        return UUID.randomUUID();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
