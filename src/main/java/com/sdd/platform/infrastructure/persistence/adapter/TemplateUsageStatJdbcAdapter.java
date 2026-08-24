package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.TemplateUsageStatPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class TemplateUsageStatJdbcAdapter implements TemplateUsageStatPort {

    private final NamedParameterJdbcTemplate jdbc;

    public TemplateUsageStatJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void recordCheck(UUID projectId, UUID repositoryId, UUID phaseId, boolean matched) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("repositoryId", repositoryId)
                .addValue("phaseId", phaseId)
                .addValue("matchIncrement", matched ? 1 : 0);

        jdbc.update("""
                INSERT INTO tbl_fact_template_usage_stat (
                    project_id, repository_id, phase_id, total_check_count, template_match_count
                ) VALUES (:projectId, :repositoryId, :phaseId, 1, :matchIncrement)
                ON CONFLICT (project_id, repository_id, phase_id) DO UPDATE SET
                    total_check_count = tbl_fact_template_usage_stat.total_check_count + 1,
                    template_match_count = tbl_fact_template_usage_stat.template_match_count + EXCLUDED.template_match_count,
                    updated_at = now()
                """, params);
    }
}
