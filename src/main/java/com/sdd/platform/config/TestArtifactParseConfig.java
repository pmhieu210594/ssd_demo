package com.sdd.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.DocParsePersistencePort;
import com.sdd.platform.infrastructure.persistence.adapter.docparse.GenericDocParseJdbcAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class TestArtifactParseConfig {

    @Bean
    DocParsePersistencePort testPlanDocParse(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new GenericDocParseJdbcAdapter(jdbc, objectMapper, "TEST_PLAN");
    }

    @Bean
    DocParsePersistencePort testResultsDocParse(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        return new GenericDocParseJdbcAdapter(jdbc, objectMapper, "TEST_RESULTS");
    }
}
