package com.sdd.platform.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactScannerPersistenceIntegrationTest {

    @Test
    void scannerJdbcAdapter_uses_v4_run_snapshot_and_current_inventory_view_without_full_content_columns()
            throws IOException {
        String adapter = read(
                "src/main/java/com/sdd/platform/infrastructure/persistence/adapter/scanner/ArtifactScannerJdbcAdapter.java");

        assertThat(adapter).contains("INSERT INTO tbl_connector_run");
        assertThat(adapter).contains("INSERT INTO tbl_fact_artifact_snapshot");
        assertThat(adapter).contains("FROM vw_artifact_inventory_current");
        assertThat(adapter).contains("WHERE repository_id = :repositoryId");
        assertThat(adapter).contains("WHERE connector_run_id = :connectorRunId");
        assertThat(adapter).doesNotContain("markdown_content");
        assertThat(adapter).doesNotContain("full_content");
        assertThat(adapter).doesNotContain("legacy");
    }

    @Test
    void scannerMigrations_add_snapshot_columns_phase0_seed_and_current_inventory_view() throws IOException {
        String v160 = read("src/main/resources/db/migration/V160__artifact_scanner.sql");
        String v161 = read("src/main/resources/db/migration/V161__artifact_scanner_ticket_status.sql");

        assertThat(v160).contains("ALTER TABLE tbl_fact_artifact_snapshot");
        assertThat(v160).contains("ADD COLUMN IF NOT EXISTS size_bytes BIGINT");
        assertThat(v160).contains("ADD COLUMN IF NOT EXISTS scan_status VARCHAR(32)");
        assertThat(v160).contains("ADD COLUMN IF NOT EXISTS scan_message TEXT");
        assertThat(v160).contains("ADD COLUMN IF NOT EXISTS need_parse BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(v160).contains("'PHASE0_README'");
        assertThat(v160).contains("'BLACKBOX_TESTCASES'");
        assertThat(v160).contains("INSERT INTO tbl_source_connector");
        assertThat(v160).contains("CREATE OR REPLACE VIEW vw_artifact_inventory_current AS");

        assertThat(v161).contains("ALTER TYPE ticket_status ADD VALUE IF NOT EXISTS 'MERGED'");
        assertThat(v161).contains("ALTER TYPE ticket_status ADD VALUE IF NOT EXISTS 'DRAFT'");
        assertThat(v161).contains("DROP VIEW IF EXISTS vw_artifact_inventory_current");
        assertThat(v161).contains("CREATE VIEW vw_artifact_inventory_current AS");
        assertThat(v161).contains("ticket.status AS pr_status");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
