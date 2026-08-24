package com.sdd.platform.infrastructure.persistence.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHASE-DWELL-TIME AC-9 (spec-pack.md §6): the new table must be additive-only —
 * no ALTER/DROP against any existing table. Follows the same
 * read-the-raw-SQL-file pattern as {@link CiRunMetadataMigrationTest}.
 */
class ArtifactDocumentDateMigrationTest {

    @Test
    void v513_migration_isAdditiveOnly_andHasRescanIdempotencyConstraint() throws IOException {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V513__add_artifact_document_date.sql"));
        String upper = sql.toUpperCase(Locale.ROOT);

        // AC-9: additive-only, no ALTER/DROP on any existing table.
        assertFalse(upper.contains("ALTER TABLE"), "migration must not ALTER any existing table");
        assertFalse(upper.contains("DROP TABLE"), "migration must not DROP any existing table");
        assertFalse(upper.contains("DROP COLUMN"), "migration must not DROP any existing column");

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tbl_fact_artifact_document_date"));

        // Gate #1 (impl-plan.md): UNIQUE(artifact_snapshot_id) is the mechanism the
        // ArtifactScannerJdbcAdapter's "ON CONFLICT (artifact_snapshot_id) DO NOTHING" write
        // depends on for AC-6 (rescan doesn't change an already-computed dwell time) — if this
        // constraint is ever removed, that INSERT stops being idempotent and silently starts
        // inserting duplicate rows per rescan instead of failing loudly.
        assertTrue(sql.contains("UNIQUE (artifact_snapshot_id)"),
                "must keep UNIQUE(artifact_snapshot_id) backing the ON CONFLICT DO NOTHING upsert");
        assertTrue(sql.contains("REFERENCES tbl_fact_artifact_snapshot(artifact_snapshot_id)"));
    }
}
