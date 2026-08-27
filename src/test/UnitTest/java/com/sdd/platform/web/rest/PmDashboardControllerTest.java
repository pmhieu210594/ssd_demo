package com.sdd.platform.web.rest;

import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardModels;
import com.sdd.platform.application.usecase.pmdashboard.PmDashboardService;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PmDashboardControllerTest {

    private MockMvc mockMvc;
    private PmDashboardService service;
    private AuthUserContext pm;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(PmDashboardService.class);
        pm = AuthUserContext.builder().username("pm01").role("PM").build();
        mockMvc = MockMvcBuilders.standaloneSetup(new PmDashboardController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(pm))
                .build();
    }

    @Test
    void summary_returnsDashboardSummaryDto() throws Exception {
        when(service.summary(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pm)))
                .thenReturn(new PmDashboardModels.DashboardSummary(
                        1, 2, 3, 4, 5, 6, 7, 8,
                        new BigDecimal("82.5"), "GOOD", "PLAN", "Plan", 2L,
                        OffsetDateTime.parse("2026-06-25T00:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/pm/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedTicketCount").value(1))
                .andExpect(jsonPath("$.missingTraceabilitySectionTicketCount").value(3))
                .andExpect(jsonPath("$.averageScoreBand").value("GOOD"))
                .andExpect(jsonPath("$.phaseBottleneckPhaseCode").value("PLAN"));

        verify(service).summary(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pm));
    }

    @Test
    void insights_returnsInsightsDto() throws Exception {
        when(service.insights(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pm)))
                .thenReturn(new PmDashboardModels.DashboardInsights(List.of(), List.of()));

        mockMvc.perform(get("/api/v1/pm/dashboard/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attentionItems").isArray())
                .andExpect(jsonPath("$.evidenceBottleneckBuckets").isArray());

        verify(service).insights(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pm));
    }

    @Test
    void tickets_returnsPageDto() throws Exception {
        when(service.tickets(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq(pm)))
                .thenReturn(new PageResult<>(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/api/v1/pm/dashboard/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detail_returns404WhenServiceRejectsMissingTicket() throws Exception {
        UUID ticketId = UUID.fromString("90000000-0000-0000-0000-000000000003");
        when(service.detail(eq(ticketId), eq(pm))).thenThrow(new NotFoundException("Pages.PmDashboard.NotFound"));

        mockMvc.perform(get("/api/v1/pm/dashboard/tickets/{ticketId}/detail", ticketId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pages.PmDashboard.NotFound"));
    }

    @Test
    void export_returnsCsvAttachment() throws Exception {
        when(service.exportCsv(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pm)))
                .thenReturn("ticket_id\n".getBytes());

        mockMvc.perform(post("/api/v1/pm/dashboard/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/csv;charset=UTF-8")))
                .andExpect(content().string("ticket_id\n"));
    }

    @Test
    void refresh_returnsPayload() throws Exception {
        when(service.refresh(eq(pm)))
                .thenReturn(new PmDashboardModels.DashboardRefreshResult(5L, OffsetDateTime.parse("2026-06-25T00:00:00Z")));

        mockMvc.perform(post("/api/v1/pm/dashboard/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshedRows").value(5));
    }

    @Test
    void summary_mapsForbidden() throws Exception {
        when(service.summary(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pm)))
                .thenThrow(new ForbiddenException("Component.Permission.Denied"));

        mockMvc.perform(get("/api/v1/pm/dashboard/summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Component.Permission.Denied"));
    }

    @Test
    void detailReturns200WithEqsAndScoreBreakdown() throws Exception {
        UUID ticketId = UUID.fromString("90000000-0000-0000-0000-000000000003");
        PmDashboardModels.DashboardTicketRow row = new PmDashboardModels.DashboardTicketRow(
                ticketId, UUID.randomUUID(), "PROJ", UUID.randomUUID(), "repo-x",
                "PROJ-1", "Implement auth module",
                UUID.randomUUID(), "PLAN", "Plan", 2,
                true, false, 2, 1, 0, 1, 0, 0, "HIGH",
                new BigDecimal("68.0"), "WARNING", "v1",
                4, "Backend Role", "2026-06",
                OffsetDateTime.parse("2026-06-25T00:00:00Z"),
                OffsetDateTime.parse("2026-06-25T01:00:00Z")
        );
        PmDashboardModels.ScoreBreakdown breakdown = new PmDashboardModels.ScoreBreakdown(
                new BigDecimal("12"), new BigDecimal("5"), new BigDecimal("8"),
                new BigDecimal("7"), new BigDecimal("9"), new BigDecimal("10"),
                new BigDecimal("8"), new BigDecimal("9")
        );
        PmDashboardModels.DashboardTicketDetail detail = new PmDashboardModels.DashboardTicketDetail(
                row, List.of(), List.of(), List.of(), breakdown, "/traceability/PROJ-1"
        );
        when(service.detail(eq(ticketId), eq(pm))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/pm/dashboard/tickets/{ticketId}/detail", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row.evidenceQualityScore").value(68.0))
                .andExpect(jsonPath("$.row.scoreBand").value("WARNING"))
                .andExpect(jsonPath("$.row.missingEvidenceCount").value(2))
                .andExpect(jsonPath("$.row.riskCount").value(1))
                .andExpect(jsonPath("$.scoreBreakdown.specScore").value(12))
                .andExpect(jsonPath("$.scoreBreakdown.planScore").value(5))
                .andExpect(jsonPath("$.traceabilityUrl").value("/traceability/PROJ-1"));
    }

    @Test
    void ticketsPassesSearchParamToService() throws Exception {
        when(service.tickets(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("abc"), eq(1), eq(20), eq(pm)))
                .thenReturn(new PageResult<>(List.of(), 1, 20, 0, 0));

        mockMvc.perform(get("/api/v1/pm/dashboard/tickets").param("search", "abc"))
                .andExpect(status().isOk());

        verify(service).tickets(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq("abc"), eq(1), eq(20), eq(pm));
    }

    @Test
    void refreshMapsForbidden() throws Exception {
        when(service.refresh(eq(pm))).thenThrow(new ForbiddenException("Component.Permission.Denied"));

        mockMvc.perform(post("/api/v1/pm/dashboard/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Component.Permission.Denied"));
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
