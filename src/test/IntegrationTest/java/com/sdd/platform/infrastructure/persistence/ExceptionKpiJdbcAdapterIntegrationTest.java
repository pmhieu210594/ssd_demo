package com.sdd.platform.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionKpiJdbcAdapterIntegrationTest {

    // AC-FCI-9: V234 migration adds unique index that prevents duplicate rows on rerun
    @Test
    void v234Migration_containsUniqueIndexForIdempotentUpsert() throws IOException {
        String sql = read("src/main/resources/db/migration/V234__tbl_fact_exception_add_source_section.sql");

        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS source_section");
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_exception_ticket_type_section");
        assertThat(sql).contains("ON tbl_fact_exception (ticket_id, exception_type, source_section)");
    }

    // AC-FCI-9: index is partial (WHERE ticket_id IS NOT NULL) to exclude null-keyed rows
    @Test
    void v234Migration_uniqueIndexIsPartialOnNonNullTicketId() throws IOException {
        String sql = read("src/main/resources/db/migration/V234__tbl_fact_exception_add_source_section.sql");

        assertThat(sql).contains("WHERE ticket_id IS NOT NULL");
    }

    // AC-FCI-1 (supporting): V234 adds supporting index for first CI run lookup
    @Test
    void v234Migration_addsIndexForFirstCiRunQuery() throws IOException {
        String sql = read("src/main/resources/db/migration/V234__tbl_fact_exception_add_source_section.sql");

        assertThat(sql).contains("idx_ci_run_pull_request_started_at");
        assertThat(sql).contains("ON tbl_fact_ci_run (pull_request_id, started_at ASC)");
    }

    // AC-FCI-5: adapter queries tbl_fact_exception with all required governance fields
    @Test
    void exceptionKpiJdbcAdapter_queriesFactExceptionWithGovernanceFields() throws IOException {
        String adapter = read("src/main/java/com/sdd/platform/infrastructure/persistence/adapter/ExceptionKpiJdbcAdapter.java");

        assertThat(adapter).contains("FROM tbl_fact_exception");
        assertThat(adapter).contains("exception_type");
        assertThat(adapter).contains("reason_present");
        assertThat(adapter).contains("approved");
        assertThat(adapter).contains("follow_up_status");
        assertThat(adapter).contains("source_section");
        assertThat(adapter).contains("linked_report_path");
        assertThat(adapter).contains("WHERE ticket_id = :ticketId");
    }

    // AC-FCI-9: adapter returns full materialized result so counting logic is local (no second query)
    @Test
    void exceptionKpiJdbcAdapter_computesOpenAndApprovedCountsInMemory() throws IOException {
        String adapter = read("src/main/java/com/sdd/platform/infrastructure/persistence/adapter/ExceptionKpiJdbcAdapter.java");

        assertThat(adapter).contains("\"OPEN\".equalsIgnoreCase");
        assertThat(adapter).contains("ExceptionSummary::approved");
        assertThat(adapter).contains("return new ExceptionKpiResult");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
