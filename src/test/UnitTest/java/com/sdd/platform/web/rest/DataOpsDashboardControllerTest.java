package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardModels;
import com.sdd.platform.application.usecase.dataopsdashboard.DataOpsDashboardService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DataOpsDashboardControllerTest {

    private MockMvc mockMvc;
    private DataOpsDashboardService service;
    private AuthUserContext caller;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(DataOpsDashboardService.class);
        caller = AuthUserContext.builder().username("dataops01").role("DATA_OPS").build();
        mockMvc = MockMvcBuilders.standaloneSetup(new DataOpsDashboardController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(caller))
                .build();
    }

    @Test
    void summary_returnsDashboardSummaryDto() throws Exception {
        when(service.summary(isNull(), isNull(), isNull(), isNull(), isNull(), eq(caller)))
                .thenReturn(new DataOpsDashboardModels.DataOpsDashboardSummary(
                        1, 2, 3, 4, 5, OffsetDateTime.parse("2026-07-02T00:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/data-ops/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorFailureCount").value(1))
                .andExpect(jsonPath("$.parseErrorCount").value(2))
                .andExpect(jsonPath("$.missingEvidenceCount").value(3))
                .andExpect(jsonPath("$.staleFreshnessCount").value(4))
                .andExpect(jsonPath("$.brokenLinkCount").value(5));

        verify(service).summary(isNull(), isNull(), isNull(), isNull(), isNull(), eq(caller));
    }

    @Test
    void summary_zeroKpisWhenNoData() throws Exception {
        when(service.summary(isNull(), isNull(), isNull(), isNull(), isNull(), eq(caller)))
                .thenReturn(new DataOpsDashboardModels.DataOpsDashboardSummary(
                        0, 0, 0, 0, 0, OffsetDateTime.parse("2026-07-02T00:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/data-ops/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorFailureCount").value(0))
                .andExpect(jsonPath("$.brokenLinkCount").value(0));
    }

    @Test
    void connectors_returnsPageDto() throws Exception {
        when(service.connectors(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq(caller)))
                .thenReturn(new DataOpsDashboardModels.DataOpsDashboardPage(List.of(), 1, 20, 0L, 0));

        mockMvc.perform(get("/api/v1/data-ops/dashboard/connectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(service).connectors(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq(caller));
    }

    @Test
    void detail_returnsConnectorDetailDto() throws Exception {
        UUID connectorId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        DataOpsDashboardModels.DataOpsConnectorRow row = new DataOpsDashboardModels.DataOpsConnectorRow(
                connectorId, projectId, "acme-project", repositoryId, "acme-repo",
                "github-connector", "GITHUB", "SUCCESS",
                OffsetDateTime.parse("2026-07-01T00:00:00Z"), 0, 0, 0, 10
        );
        when(service.connectorDetail(eq(connectorId), eq(caller))).thenReturn(
                new DataOpsDashboardModels.DataOpsConnectorDetail(row, List.of(), List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/data-ops/dashboard/connectors/" + connectorId + "/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.connectorName").value("github-connector"))
                .andExpect(jsonPath("$.recentRuns").isArray())
                .andExpect(jsonPath("$.dataQualityChecks").isArray());
    }

    @Test
    void options_returnsOptionsDto() throws Exception {
        when(service.options(isNull(), isNull(), eq(caller))).thenReturn(
                new DataOpsDashboardModels.DataOpsDashboardOptions(List.of(), List.of(), List.of())
        );

        mockMvc.perform(get("/api/v1/data-ops/dashboard/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects").isArray())
                .andExpect(jsonPath("$.repositories").isArray())
                .andExpect(jsonPath("$.connectors").isArray());
    }

    @Test
    void options_acceptsRepositoryIdFilter() throws Exception {
        when(service.options(isNull(), eq(UUID.fromString("00000000-0000-0000-0000-000000000111")), eq(caller)))
                .thenReturn(new DataOpsDashboardModels.DataOpsDashboardOptions(List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/v1/data-ops/dashboard/options")
                        .param("repositoryId", "00000000-0000-0000-0000-000000000111"))
                .andExpect(status().isOk());

        verify(service).options(isNull(), eq(UUID.fromString("00000000-0000-0000-0000-000000000111")), eq(caller));
    }

    private static final class FixedCurrentUserResolver implements HandlerMethodArgumentResolver {
        private final AuthUserContext caller;

        private FixedCurrentUserResolver(AuthUserContext caller) {
            this.caller = caller;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(AuthUserContext.class)
                    && parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            return caller;
        }
    }
}
