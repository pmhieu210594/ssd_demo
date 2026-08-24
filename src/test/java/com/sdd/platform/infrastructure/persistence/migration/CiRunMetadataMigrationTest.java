package com.sdd.platform.infrastructure.persistence.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CiRunMetadataMigrationTest {

    @Test
    void v200_migration_contains_job_level_grain_and_connector_counters() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V200__ci_run_metadata.sql"));

        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS external_job_id"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS records_received"));
        assertTrue(sql.contains("uq_tbl_fact_ci_run_provider_repo_run_job"));
        assertTrue(sql.contains("CHECK (completed_at IS NULL OR started_at IS NULL OR started_at <= completed_at)"));
    }
}
