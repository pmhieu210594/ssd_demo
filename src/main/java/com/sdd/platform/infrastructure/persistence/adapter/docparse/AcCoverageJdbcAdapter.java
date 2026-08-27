package com.sdd.platform.infrastructure.persistence.adapter.docparse;

import com.sdd.platform.application.port.out.persistence.AcCoveragePort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class AcCoverageJdbcAdapter implements AcCoveragePort {

    private final NamedParameterJdbcTemplate jdbc;

    public AcCoverageJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> findActiveAcKeys(UUID ticketId) {
        return jdbc.queryForList("""
                SELECT ac_key
                FROM tbl_fact_acceptance_criteria
                WHERE ticket_id = :ticketId
                  AND status = 'ACTIVE'
                ORDER BY ac_key
                """,
                new MapSqlParameterSource("ticketId", ticketId),
                String.class);
    }
}
