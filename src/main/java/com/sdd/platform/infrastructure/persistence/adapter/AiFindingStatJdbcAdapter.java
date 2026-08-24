package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.AiFindingStatPort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiFindingStatJdbcAdapter implements AiFindingStatPort {

    private final NamedParameterJdbcTemplate jdbc;

    public AiFindingStatJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(AiFindingStatRecord record) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", record.projectId())
                .addValue("repositoryId", record.repositoryId())
                .addValue("ticketId", record.ticketId())
                .addValue("blockerMajorResolvedCount", record.blockerMajorResolvedCount())
                .addValue("blockerMajorTotalCount", record.blockerMajorTotalCount())
                .addValue("aiReviewAdoptedCount", record.aiReviewAdoptedCount())
                .addValue("aiReviewFindingTotalCount", record.aiReviewFindingTotalCount())
                .addValue("aiReviewValidCount", record.aiReviewValidCount())
                .addValue("aiReviewFalsePositiveCount", record.aiReviewFalsePositiveCount())
                .addValue("aiReviewResolvedCount", record.aiReviewResolvedCount());

        jdbc.update("""
                INSERT INTO tbl_fact_ai_finding_stat (
                    project_id, repository_id, ticket_id,
                    blocker_major_resolved_count, blocker_major_total_count,
                    ai_review_adopted_count, ai_review_finding_total_count,
                    ai_review_valid_count, ai_review_false_positive_count, ai_review_resolved_count
                ) VALUES (
                    :projectId, :repositoryId, :ticketId,
                    :blockerMajorResolvedCount, :blockerMajorTotalCount,
                    :aiReviewAdoptedCount, :aiReviewFindingTotalCount,
                    :aiReviewValidCount, :aiReviewFalsePositiveCount, :aiReviewResolvedCount
                )
                ON CONFLICT (ticket_id) DO UPDATE SET
                    blocker_major_resolved_count = EXCLUDED.blocker_major_resolved_count,
                    blocker_major_total_count = EXCLUDED.blocker_major_total_count,
                    ai_review_adopted_count = EXCLUDED.ai_review_adopted_count,
                    ai_review_finding_total_count = EXCLUDED.ai_review_finding_total_count,
                    ai_review_valid_count = EXCLUDED.ai_review_valid_count,
                    ai_review_false_positive_count = EXCLUDED.ai_review_false_positive_count,
                    ai_review_resolved_count = EXCLUDED.ai_review_resolved_count,
                    updated_at = now()
                """, params);
    }
}
