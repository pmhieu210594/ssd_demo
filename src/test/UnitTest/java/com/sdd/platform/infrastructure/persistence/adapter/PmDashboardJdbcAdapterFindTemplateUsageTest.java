package com.sdd.platform.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PmDashboardJdbcAdapterFindTemplateUsageTest {

    @Test
    void findTemplateUsage_scopesToInScopePhaseCodesOnly() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardProjectAccessJdbcAdapter projectAccess = mock(DashboardProjectAccessJdbcAdapter.class);
        PmDashboardJdbcAdapter adapter = new PmDashboardJdbcAdapter(jdbc, projectAccess);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000e001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000e002");

        adapter.findTemplateUsage(projectId, repositoryId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("FROM tbl_dim_phase ph"));
        // Phase scoping per spec-pack.md Terminology (backed by tbl_dim_phase codes '1'..'8'):
        // an EXISTS(...) join against tbl_dim_artifact_type is NOT sufficient here, because that
        // table is shared with unrelated features (e.g. phase '0-A' has artifact_type rows for
        // AI-safety file scanning), so it would incorrectly let '0-A' leak into this response.
        // The in-scope phase set for this feature is an explicit business boundary already fixed
        // in the spec, so a literal allow-list is the correct scoping mechanism here.
        assertTrue(sql.contains("phase_code IN"));
        for (String phaseCode : new String[] {"'1'", "'2'", "'3'", "'4'", "'5'", "'6'", "'7'", "'8'"}) {
            assertTrue(sql.contains(phaseCode), "expected sql to contain phase code " + phaseCode);
        }
        assertFalse(sql.contains("0-A"));
        assertFalse(sql.contains("EXISTS"));

        MapSqlParameterSource params = paramsCaptor.getValue();
        assertEquals(projectId, params.getValue("projectId"));
        assertEquals(repositoryId, params.getValue("repositoryId"));
    }

    @Test
    void findTemplateUsage_scopesTenantInsideLeftJoinOnClause_notInWhereClause() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DashboardProjectAccessJdbcAdapter projectAccess = mock(DashboardProjectAccessJdbcAdapter.class);
        PmDashboardJdbcAdapter adapter = new PmDashboardJdbcAdapter(jdbc, projectAccess);
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-00000000e001");
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-00000000e002");

        adapter.findTemplateUsage(projectId, repositoryId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        String sql = sqlCaptor.getValue();

        // Data-inconsistency / tenant-isolation regression guard: per spec-pack.md's
        // boundary example (a phase with zero checks must still be returned with
        // totalCheckCount=0/templateMatchCount=0/usageRate=null, not omitted), the
        // project/repository scoping MUST live in the LEFT JOIN's ON clause, not in
        // a WHERE clause. Moving "st.project_id = :projectId" /
        // "st.repository_id = :repositoryId" into WHERE would silently turn the LEFT
        // JOIN into an inner join in practice (WHERE runs after the join and rejects
        // NULL st.project_id rows), so phases this project/repo never checked would
        // disappear from the response entirely instead of showing as zero -- a subtle
        // regression a future "simplify this query" refactor could easily introduce
        // without any single-tenant test noticing (the row count would still look
        // correct for a project/repo that HAS activity on every phase).
        int whereIndex = sql.indexOf("WHERE");
        assertTrue(whereIndex >= 0, "expected a WHERE clause scoping phase_code");
        String beforeWhere = sql.substring(0, whereIndex);
        String fromWhere = sql.substring(whereIndex);
        assertTrue(beforeWhere.contains("st.project_id = :projectId"),
                "tenant scoping by project_id must be inside the JOIN ON clause");
        assertTrue(beforeWhere.contains("st.repository_id = :repositoryId"),
                "tenant scoping by repository_id must be inside the JOIN ON clause");
        assertFalse(fromWhere.contains("projectId"),
                "tenant scoping must not be duplicated/moved into the WHERE clause");
        assertFalse(fromWhere.contains("repositoryId"),
                "tenant scoping must not be duplicated/moved into the WHERE clause");
    }
}
