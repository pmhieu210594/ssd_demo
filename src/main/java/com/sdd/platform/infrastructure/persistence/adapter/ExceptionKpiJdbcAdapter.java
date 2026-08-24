package com.sdd.platform.infrastructure.persistence.adapter;

import com.sdd.platform.application.port.out.persistence.ExceptionKpiRepositoryPort;
import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionKpiResult;
import com.sdd.platform.application.usecase.governance.ExceptionKpiService.ExceptionSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ExceptionKpiJdbcAdapter implements ExceptionKpiRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public ExceptionKpiJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ExceptionKpiResult findExceptionKpi(UUID ticketId) {
        List<ExceptionSummary> rows = jdbc.query("""
                SELECT
                    exception_id,
                    exception_type,
                    reason_present,
                    approved,
                    follow_up_status,
                    source_section,
                    linked_report_path
                FROM tbl_fact_exception
                WHERE ticket_id = :ticketId
                ORDER BY created_at DESC, exception_id DESC
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                (rs, rowNum) -> new ExceptionSummary(
                        rs.getObject("exception_id", UUID.class),
                        rs.getString("exception_type"),
                        rs.getBoolean("reason_present"),
                        rs.getBoolean("approved"),
                        rs.getString("follow_up_status"),
                        rs.getString("source_section"),
                        rs.getString("linked_report_path")));

        int total = rows.size();
        int open = (int) rows.stream().filter(r -> "OPEN".equalsIgnoreCase(r.followUpStatus())).count();
        int approved = (int) rows.stream().filter(ExceptionSummary::approved).count();

        return new ExceptionKpiResult(ticketId, total, open, approved, total > 0, rows);
    }
}
