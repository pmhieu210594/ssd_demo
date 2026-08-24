package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.traceability.TraceabilityModels;
import com.sdd.platform.application.usecase.traceability.TraceabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TraceabilityControllerTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TraceabilityControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @Import(TraceabilityController.class)
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TraceabilityService service;

    @Test
    void get_traceability_returns_response_payload() throws Exception {
        UUID ticketId = UUID.fromString("8bb0b3c7-90ce-4b1b-82c0-1bbf9c17ef61");
        when(service.getTraceability(ticketId)).thenReturn(new TraceabilityModels.TraceabilityView(
                new TraceabilityModels.Summary(
                        ticketId,
                        "ABC-123",
                        "Traceability ticket",
                        100,
                        9,
                        9,
                        7,
                        1,
                        1,
                        1,
                        0,
                        0
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ));

        mockMvc.perform(get("/api/v1/traceability/{ticketId}", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.ticketId").value(ticketId.toString()))
                .andExpect(jsonPath("$.summary.completenessPercent").value(100));
    }
}
