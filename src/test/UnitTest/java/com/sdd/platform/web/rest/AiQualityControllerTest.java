package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ConflictException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.usecase.aiquality.AiQualityService;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AiQualityModel;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiQualityControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPOSITORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID AI_QUALITY_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private AiQualityService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AiQualityService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiQualityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCallerResolver(callerWithRole("ADMIN")))
                .build();
    }

    @Test
    void list_returnsPagedItems() throws Exception {
        when(service.search(eq(PROJECT_ID), eq((UUID) null), eq((UUID) null), eq((String) null), eq(0), eq(20), any(AuthUserContext.class)))
                .thenReturn(new PageResult<>(List.of(activeRow()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/ai-qualities").param("projectId", PROJECT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticketAiQualityId").value(AI_QUALITY_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_forwardsSearchParam_andCombinedWithOtherFilters() throws Exception {
        when(service.search(eq(PROJECT_ID), eq(REPOSITORY_ID), eq(TICKET_ID), eq("AI-123"), eq(0), eq(20), any(AuthUserContext.class)))
                .thenReturn(new PageResult<>(List.of(activeRow()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/ai-qualities")
                        .param("projectId", PROJECT_ID.toString())
                        .param("repositoryId", REPOSITORY_ID.toString())
                        .param("ticketId", TICKET_ID.toString())
                        .param("search", "AI-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void get_returnsDetail() throws Exception {
        when(service.get(eq(AI_QUALITY_ID), any(AuthUserContext.class))).thenReturn(activeRow());

        mockMvc.perform(get("/api/v1/ai-qualities/{id}", AI_QUALITY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketAiQualityId").value(AI_QUALITY_ID.toString()));
    }

    @Test
    void byTicket_returns200WithQuality() throws Exception {
        when(service.getByTicketId(eq(TICKET_ID), any(AuthUserContext.class)))
                .thenReturn(java.util.Optional.of(activeRow()));

        mockMvc.perform(get("/api/v1/ai-qualities/by-ticket/{ticketId}", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketAiQualityId").value(AI_QUALITY_ID.toString()));
    }

    @Test
    void byTicket_returns200WithoutQuality() throws Exception {
        when(service.getByTicketId(eq(TICKET_ID), any(AuthUserContext.class)))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/ai-qualities/by-ticket/{ticketId}", TICKET_ID))
                .andExpect(status().isOk());
    }

    @Test
    void create_returns201() throws Exception {
        when(service.create(eq(PROJECT_ID), eq(REPOSITORY_ID), eq(TICKET_ID), eq(new BigDecimal("92.50")), any(AuthUserContext.class)))
                .thenReturn(activeRow());

        mockMvc.perform(post("/api/v1/ai-qualities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "repositoryId", REPOSITORY_ID,
                                "ticketId", TICKET_ID,
                                "aiQualityRate", new BigDecimal("92.50")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketAiQualityId").value(AI_QUALITY_ID.toString()));
    }

    @Test
    void update_usesPut() throws Exception {
        when(service.update(eq(AI_QUALITY_ID), eq(new BigDecimal("70.00")), any(AuthUserContext.class)))
                .thenReturn(activeRow());

        mockMvc.perform(put("/api/v1/ai-qualities/{id}", AI_QUALITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiQualityRate", new BigDecimal("70.00")))))
                .andExpect(status().isOk());
    }

    @Test
    void softDelete_usesPutDeleteEndpoint_neverHardDelete() throws Exception {
        AiQualityModel deleted = activeRow();
        deleted.setStatus(AiQualityModel.Status.DELETED);
        deleted.setDeleteFlag(true);
        when(service.softDelete(eq(AI_QUALITY_ID), any(AuthUserContext.class))).thenReturn(deleted);

        mockMvc.perform(put("/api/v1/ai-qualities/{id}/delete", AI_QUALITY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.deleteFlag").value(true));
    }

    @Test
    void exceptions_mapToExpectedEnvelope() throws Exception {
        when(service.get(eq(AI_QUALITY_ID), any(AuthUserContext.class)))
                .thenThrow(new NotFoundException("Pages.AiQuality.NotFound"));
        mockMvc.perform(get("/api/v1/ai-qualities/{id}", AI_QUALITY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pages.AiQuality.NotFound"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());

        when(service.get(eq(AI_QUALITY_ID), any(AuthUserContext.class)))
                .thenThrow(new ForbiddenException("Component.Permission.Denied"));
        mockMvc.perform(get("/api/v1/ai-qualities/{id}", AI_QUALITY_ID))
                .andExpect(status().isForbidden());

        when(service.create(any(), any(), any(), any(), any(AuthUserContext.class)))
                .thenThrow(new ConflictException("Pages.AiQuality.Ticket.AlreadyExists"));
        mockMvc.perform(post("/api/v1/ai-qualities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "repositoryId", REPOSITORY_ID,
                                "ticketId", TICKET_ID,
                                "aiQualityRate", new BigDecimal("50.00")
                        ))))
                .andExpect(status().isConflict());

        when(service.create(any(), any(), any(), any(), any(AuthUserContext.class)))
                .thenThrow(new BusinessRuleException("Pages.AiQuality.Rate.Invalid"));
        mockMvc.perform(post("/api/v1/ai-qualities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "repositoryId", REPOSITORY_ID,
                                "ticketId", TICKET_ID,
                                "aiQualityRate", new BigDecimal("100.01")
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAccess_returnsNoContent_whenCallerHasAccess() throws Exception {
        mockMvc.perform(get("/api/v1/ai-qualities/access").param("projectId", PROJECT_ID.toString()))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(service).requireAccess(eq(PROJECT_ID), any(AuthUserContext.class));
    }

    @Test
    void getAccess_withNoProjectId_returnsNoContent_forAnyAuthenticatedCaller() throws Exception {
        // No ?projectId param — mirrors TicketBugMetricsController.access's fallback to
        // requireAnyAccess, used by the FE guard before a project has been selected/synced
        // into the URL (e.g. right after RoleTabs navigates here without carrying it over).
        mockMvc.perform(get("/api/v1/ai-qualities/access"))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(service).requireAnyAccess(any(AuthUserContext.class));
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
                .requireAccess(any(), any(AuthUserContext.class));
    }

    @Test
    void getAccess_returnsForbidden_whenCallerHasNoAccess() throws Exception {
        org.mockito.Mockito.doThrow(new ForbiddenException("Component.Permission.Denied"))
                .when(service).requireAccess(eq(PROJECT_ID), any(AuthUserContext.class));

        mockMvc.perform(get("/api/v1/ai-qualities/access").param("projectId", PROJECT_ID.toString()))
                .andExpect(status().isForbidden());
    }

    private AiQualityModel activeRow() {
        return AiQualityModel.builder()
                .ticketAiQualityId(AI_QUALITY_ID)
                .projectId(PROJECT_ID)
                .repositoryId(REPOSITORY_ID)
                .ticketId(TICKET_ID)
                .aiQualityRate(new BigDecimal("85.50"))
                .status(AiQualityModel.Status.ACTIVE)
                .deleteFlag(false)
                .build();
    }

    private AuthUserContext callerWithRole(String role) {
        return AuthUserContext.builder().userAccountId(UUID.randomUUID()).role(role).build();
    }

    private static final class FixedCallerResolver implements HandlerMethodArgumentResolver {
        private final AuthUserContext caller;

        private FixedCallerResolver(AuthUserContext caller) {
            this.caller = caller;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(AuthUserContext.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return caller;
        }
    }
}
