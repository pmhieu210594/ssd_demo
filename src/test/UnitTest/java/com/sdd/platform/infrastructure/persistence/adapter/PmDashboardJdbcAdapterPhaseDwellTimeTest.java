package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketDetail;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.DashboardTicketRow;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels.PhaseDwellTimeItem;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PHASE-DWELL-TIME: covers the {@code findPhaseDwellTime} subquery wired into
 * {@code findDetail} (spec-pack.md AC-1, AC-3, AC-4, AC-10). Uses the same
 * mock-{@code NamedParameterJdbcTemplate} + {@code ArgumentCaptor<String>} SQL
 * pattern as {@link PmDashboardJdbcAdapterFindTemplateUsageTest} — no real
 * Postgres is available in this repo's test suite (test-plan.md § Mục tiêu),
 * so SQL-shape assertions and captured-RowMapper execution are the highest
 * signal available without new infra.
 */
class PmDashboardJdbcAdapterPhaseDwellTimeTest {

    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-00000000f001");

    private NamedParameterJdbcTemplate jdbc;
    private PmDashboardJdbcAdapter adapter;

    private void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardProjectAccessJdbcAdapter projectAccess = mock(DashboardProjectAccessJdbcAdapter.class);
        adapter = new PmDashboardJdbcAdapter(jdbc, projectAccess);
    }

    /** Minimal row so findDetail() proceeds past its `rows.isEmpty()` early return. */
    private void stubMainTicketRow() {
        DashboardTicketRow row = new DashboardTicketRow(
                TICKET_ID, UUID.randomUUID(), "Project Alpha", UUID.randomUUID(), "Repo Alpha",
                "PM-1", "Title", "OPEN", UUID.randomUUID(), "1", "Spec Pack", "Spec Pack",
                OffsetDateTime.parse("2026-06-20T00:00:00Z"), 1,
                false, false, 0, 0, 0, 0, 0, 0, null,
                new BigDecimal("88"), "v1", 1, "Owner", "2026-06",
                OffsetDateTime.parse("2026-06-20T00:00:00Z"), OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                OffsetDateTime.parse("2026-06-20T00:00:00Z"), null, null);
        when(jdbc.query(contains("FROM tbl_fact_ticket_dashboard_snapshot"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));
    }

    private String capturePhaseDwellTimeSql() {
        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce())
                .query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        return sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("FROM tbl_dim_phase ph"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a query anchored on tbl_dim_phase for phase dwell time"));
    }

    // --- AC-1: exactly the 7 displayed phases, in phase_order, from a tbl_dim_phase anchor ---

    @Test
    void findPhaseDwellTime_query_isAnchoredOnAllPhasesAndFiltersToTheSevenDisplayedOnes() {
        setUp();
        stubMainTicketRow();

        adapter.findDetail(TICKET_ID);

        String sql = capturePhaseDwellTimeSql();
        // Anchor must be tbl_dim_phase (the universe of all phases), not the snapshot/document
        // table — otherwise a phase with zero matching files would be silently dropped from the
        // result set instead of surfacing as "-" (AC-3). This mirrors the LEFT JOIN regression
        // this codebase already guards against in findTemplateUsage_scopesTenantInsideLeftJoinOnClause.
        assertTrue(sql.contains("FROM tbl_dim_phase ph"), "must anchor on tbl_dim_phase, not on the snapshot/document tables");
        assertFalse(sql.contains("INNER JOIN"), "no inner join must be introduced that could drop phases with zero files");

        for (String phaseCode : new String[] {"'1'", "'3'", "'4'", "'5'", "'6'", "'7'", "'8'"}) {
            assertTrue(sql.contains(phaseCode), "expected phase_code " + phaseCode + " to be in scope");
        }
        // Excluded phases per spec-pack.md §3/§4: 0-A, 0-B, 2, 9.
        assertFalse(sql.contains("'0-A'"));
        assertFalse(sql.contains("'0-B'"));
        assertFalse(sql.contains("'2'"));
        assertFalse(sql.contains("'9'"));
        assertTrue(sql.contains("ORDER BY ph.phase_order ASC"));
    }

    // --- AC-4: a file with create_date but no update_date must not contribute to the SUM ---

    @Test
    void findPhaseDwellTime_query_excludesUnpairedFilesInsideTheJoinOnClause_notInWhere() {
        setUp();
        stubMainTicketRow();

        adapter.findDetail(TICKET_ID);

        String sql = capturePhaseDwellTimeSql();
        int whereIndex = sql.indexOf("WHERE");
        assertTrue(whereIndex >= 0, "expected a WHERE clause scoping phase_code");
        String beforeWhere = sql.substring(0, whereIndex);

        // The NOT-NULL pairing filter must live inside the LEFT JOIN's ON clause. Moving it into
        // WHERE would silently turn the LEFT JOIN into an inner join for document_date, which
        // would make a phase with only unpaired files vanish from the result set entirely
        // instead of surfacing as "-" (AC-3/AC-4) once the SUM(...) over zero matching rows is
        // computed correctly — a subtle regression a future "simplify this filter" refactor
        // could introduce without a single-phase-with-full-pair test noticing.
        assertTrue(beforeWhere.contains("d.document_create_at IS NOT NULL"),
                "pairing filter on create_at must be inside the JOIN ON clause");
        assertTrue(beforeWhere.contains("d.document_update_at IS NOT NULL"),
                "pairing filter on update_at must be inside the JOIN ON clause");
    }

    // --- ai-review.md F1 fix: a file with a reversed header (update_at < create_at) must be
    // --- excluded from the SUM entirely, inside the JOIN ON clause — not just caught after the
    // --- fact by the aggregate-level negative check, which a second valid file in the same phase
    // --- could mask (the negative contribution nets into an otherwise-positive total). ---

    @Test
    void findPhaseDwellTime_query_excludesFilesWithReversedDatesInsideTheJoinOnClause() {
        setUp();
        stubMainTicketRow();

        adapter.findDetail(TICKET_ID);

        String sql = capturePhaseDwellTimeSql();
        int whereIndex = sql.indexOf("WHERE");
        assertTrue(whereIndex >= 0, "expected a WHERE clause scoping phase_code");
        String beforeWhere = sql.substring(0, whereIndex);

        assertTrue(beforeWhere.contains("d.document_update_at >= d.document_create_at"),
                "a file with update_at < create_at (reversed/bad header data) must be excluded "
                        + "from the SUM inside the JOIN ON clause, so it cannot net a negative "
                        + "contribution into an otherwise-valid total from another file in the "
                        + "same phase (ai-review.md F1)");
    }

    // --- AC-10: a failure computing dwell time must not fail the rest of /detail ---

    @Test
    void findDetail_whenPhaseDwellTimeQueryThrows_stillReturnsDetailWithEmptyPhaseDwellTime() {
        setUp();
        stubMainTicketRow();
        when(jdbc.query(contains("FROM tbl_dim_phase ph"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("simulated timeout"));

        Optional<DashboardTicketDetail> result = adapter.findDetail(TICKET_ID);

        assertTrue(result.isPresent(), "a failure in the dwell-time subquery must not fail the whole /detail response");
        assertEquals(List.of(), result.get().phaseDwellTime());
    }

    // --- BUG-PHASE-DWELL-TIME-1 fix (OI-PHASE-DWELL-TIME-14, resolved): a negative sum (a file's
    // --- document_update_at < document_create_at — reversed/bad self-declared header data, per
    // --- spec-pack.md §12/§16 A-4) must render as "-" (null), not a malformed negative string. ---

    @Test
    void findPhaseDwellTime_rowMapper_negativeSumRendersAsDash_notAMalformedNegativeString() throws SQLException {
        setUp();
        var mapperCaptor = org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        stubMainTicketRow();

        adapter.findDetail(TICKET_ID);

        verify(jdbc, org.mockito.Mockito.atLeastOnce())
                .query(sqlCaptor.capture(), any(MapSqlParameterSource.class), mapperCaptor.capture());
        int dwellTimeCallIndex = sqlCaptor.getAllValues().indexOf(
                sqlCaptor.getAllValues().stream()
                        .filter(sql -> sql.contains("FROM tbl_dim_phase ph"))
                        .findFirst()
                        .orElseThrow());
        @SuppressWarnings("unchecked")
        RowMapper<PhaseDwellTimeItem> rowMapper = mapperCaptor.getAllValues().get(dwellTimeCallIndex);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("phase_code")).thenReturn("1");
        when(rs.getInt("phase_order")).thenReturn(1);
        when(rs.getString("phase_name")).thenReturn("Spec Pack");
        when(rs.getObject("dwell_seconds", Double.class)).thenReturn(-3600.0);

        PhaseDwellTimeItem item = rowMapper.mapRow(rs, 0);

        // A negative sum is treated as "not enough valid data to compute a dwell time", exactly
        // like the AC-3/AC-4 "-" fallback for an unpaired/absent file — not exposed to the user as
        // a nonsensical negative duration.
        assertEquals(null, item.dwellTime());
    }
}
