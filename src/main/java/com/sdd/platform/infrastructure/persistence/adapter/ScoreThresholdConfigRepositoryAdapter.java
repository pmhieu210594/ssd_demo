package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.ScoreThresholdConfigRepositoryPort;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.ScoreThreshold;
import com.sdd.platform.application.usecase.quality.ScoreThresholdConfigModels.UpsertScoreThreshold;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ScoreThresholdConfigRepositoryAdapter implements ScoreThresholdConfigRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public ScoreThresholdConfigRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ScoreThreshold> findActiveOrderedByMinScore() {
        return jdbc.query("""
                SELECT id, code, label, min_score, max_score, color, created_at, created_by, updated_at, updated_by
                FROM tbl_dim_score_threshold
                WHERE delete_flag = '0'
                ORDER BY min_score ASC
                """,
                ScoreThresholdConfigRepositoryAdapter::mapRow);
    }

    @Override
    public ScoreThreshold insert(UpsertScoreThreshold row, String actor, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tbl_dim_score_threshold (
                    id, code, label, min_score, max_score, color, delete_flag, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    :id, :code, :label, :minScore, :maxScore, :color, '0', :createdAt, :actor, :updatedAt, :actor
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("code", row.code().trim().toUpperCase(java.util.Locale.ROOT))
                        .addValue("label", row.label().trim())
                        .addValue("minScore", row.minScore())
                        .addValue("maxScore", row.maxScore())
                        .addValue("color", row.color().trim())
                        .addValue("actor", actor)
                        .addValue("createdAt", createdAt)
                        .addValue("updatedAt", createdAt)
                    );
        return new ScoreThreshold(id, row.code().trim().toUpperCase(java.util.Locale.ROOT), row.label().trim(),
                row.minScore(), row.maxScore(), row.color().trim(), createdAt, actor, createdAt, actor);
    }

    @Override
    public int update(UpsertScoreThreshold row, String actor, OffsetDateTime updatedAt) {
        return jdbc.update("""
                UPDATE tbl_dim_score_threshold
                SET code = :code,
                    label = :label,
                    min_score = :minScore,
                    max_score = :maxScore,
                    color = :color,
                    updated_at = :updatedAt,
                    updated_by = :actor
                WHERE id = :id
                  AND delete_flag = '0'
                """,
                new MapSqlParameterSource()
                        .addValue("id", row.id())
                        .addValue("code", row.code().trim().toUpperCase(java.util.Locale.ROOT))
                        .addValue("label", row.label().trim())
                        .addValue("minScore", row.minScore())
                        .addValue("maxScore", row.maxScore())
                        .addValue("color", row.color().trim())
                        .addValue("updatedAt", updatedAt)
                        .addValue("actor", actor));
    }

    @Override
    public int[] updateBatch(List<UpsertScoreThreshold> updateList, String actor, OffsetDateTime updatedAt) {
        SqlParameterSource[] tempParams = new SqlParameterSource[updateList.size()];

        // Change all lines to TEMP values ​​to completely release UNIQUE.
        for (int i = 0; i < updateList.size(); i++) {
            String tempPrefix = "TEMP_" + UUID.randomUUID().toString().replace("-", "") + "_";
            tempParams[i] = new MapSqlParameterSource()
                .addValue("id", updateList.get(i).id())
                .addValue("code", tempPrefix + i) // Ví dụ: TEMP_a1b2c3_0
                .addValue("updatedAt", updatedAt)
                .addValue("actor", actor);
        }

        jdbc.batchUpdate("""
                UPDATE tbl_dim_score_threshold
                SET code = :code,
                    updated_at = :updatedAt,
                    updated_by = :actor
                WHERE id = :id
                  AND delete_flag = '0'
                """,
                tempParams
            );

        // update final
        SqlParameterSource[] finalParams = new SqlParameterSource[updateList.size()];
        for (int i = 0; i < updateList.size(); i++) {
            finalParams[i] = new MapSqlParameterSource()
                    .addValue("id", updateList.get(i).id())
                    .addValue("code", updateList.get(i).code().trim().toUpperCase(java.util.Locale.ROOT))
                    .addValue("label", updateList.get(i).label().trim())
                    .addValue("minScore", updateList.get(i).minScore())
                    .addValue("maxScore", updateList.get(i).maxScore())
                    .addValue("color", updateList.get(i).color().trim())
                    .addValue("updatedAt", updatedAt)
                    .addValue("actor", actor);
        }

        return jdbc.batchUpdate("""
                UPDATE tbl_dim_score_threshold
                SET code = :code,
                    label = :label,
                    min_score = :minScore,
                    max_score = :maxScore,
                    color = :color,
                    updated_at = :updatedAt,
                    updated_by = :actor
                WHERE id = :id
                  AND delete_flag = '0'
                """,
                finalParams
            );
    }

    @Override
    public int softDelete(UUID id, String actor, OffsetDateTime updatedAt) {
        return jdbc.update("""
                UPDATE tbl_dim_score_threshold
                SET delete_flag = '1',
                    updated_at = :updatedAt,
                    updated_by = :actor
                WHERE id = :id
                  AND delete_flag = '0'
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("updatedAt", updatedAt)
                        .addValue("actor", actor));
    }

    private static ScoreThreshold mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ScoreThreshold(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("label"),
                rs.getInt("min_score"),
                rs.getInt("max_score"),
                rs.getString("color"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("created_by"),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getString("updated_by"));
    }
}
