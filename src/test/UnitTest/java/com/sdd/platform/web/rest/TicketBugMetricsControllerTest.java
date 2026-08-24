package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ConflictException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsModels.TicketBugMetricsOption;
import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsModels.TicketBugMetricsOptions;
import com.sdd.platform.application.usecase.ticketbugmetrics.TicketBugMetricsService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.domain.model.TicketBugMetricsModel;
import com.sdd.platform.domain.model.TicketOption;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketBugMetricsControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REPOSITORY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID TICKET_BUG_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TicketBugMetricsService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(TicketBugMetricsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketBugMetricsController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCallerResolver(callerWithRole("ADMIN")))
                .build();
    }

    @Test
    void list_returnsPagedItems() throws Exception {
        when(service.search(eq(PROJECT_ID), eq((UUID) null), isNull(), eq(0), eq(20), any(AuthUserContext.class)))
                .thenReturn(new PageResult<>(List.of(activeMetric()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/ticket-bug-metrics").param("projectId", PROJECT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticketBugId").value(TICKET_BUG_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void get_returnsDetail() throws Exception {
        when(service.get(eq(TICKET_BUG_ID), any(AuthUserContext.class))).thenReturn(activeMetric());

        mockMvc.perform(get("/api/v1/ticket-bug-metrics/{id}", TICKET_BUG_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketBugId").value(TICKET_BUG_ID.toString()));
    }

    @Test
    void create_returns201WithoutVersionField() throws Exception {
        when(service.create(eq(PROJECT_ID), eq(REPOSITORY_ID), eq(TICKET_ID), eq(1), eq(0), eq((String) null), any(AuthUserContext.class)))
                .thenReturn(activeMetric());

        mockMvc.perform(post("/api/v1/ticket-bug-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "repositoryId", REPOSITORY_ID,
                                "ticketId", TICKET_ID,
                                "internalBugCount", 1,
                                "customerBugCount", 0
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketBugId").value(TICKET_BUG_ID.toString()))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void update_usesPut() throws Exception {
        when(service.update(eq(TICKET_BUG_ID), eq(2), eq(1), eq((String) null), any(AuthUserContext.class)))
                .thenReturn(activeMetric());

        mockMvc.perform(put("/api/v1/ticket-bug-metrics/{id}", TICKET_BUG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "internalBugCount", 2,
                                "customerBugCount", 1
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void softDelete_usesPutDeleteEndpoint_neverHardDelete() throws Exception {
        TicketBugMetricsModel deleted = activeMetric();
        deleted.setStatus(TicketBugMetricsModel.Status.DELETED);
        deleted.setDeleteFlag(true);
        deleted.setDeletedAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"));
        when(service.softDelete(eq(TICKET_BUG_ID), any(AuthUserContext.class))).thenReturn(deleted);

        mockMvc.perform(put("/api/v1/ticket-bug-metrics/{id}/delete", TICKET_BUG_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.deleteFlag").value(true));
    }

    @Test
    void exceptions_mapToExpectedEnvelope() throws Exception {
        when(service.get(eq(TICKET_BUG_ID), any(AuthUserContext.class)))
                .thenThrow(new NotFoundException("Pages.TicketBugMetrics.NotFound"));
        mockMvc.perform(get("/api/v1/ticket-bug-metrics/{id}", TICKET_BUG_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pages.TicketBugMetrics.NotFound"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());

        when(service.get(eq(TICKET_BUG_ID), any(AuthUserContext.class)))
                .thenThrow(new ForbiddenException("Component.Permission.Denied"));
        mockMvc.perform(get("/api/v1/ticket-bug-metrics/{id}", TICKET_BUG_ID))
                .andExpect(status().isForbidden());

        when(service.create(any(), any(), any(), any(), any(), any(), any(AuthUserContext.class)))
                .thenThrow(new ConflictException("Pages.TicketBugMetrics.Ticket.AlreadyExists"));
        mockMvc.perform(post("/api/v1/ticket-bug-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "repositoryId", REPOSITORY_ID,
                                "ticketId", TICKET_ID,
                                "internalBugCount", 1,
                                "customerBugCount", 0
                        ))))
                .andExpect(status().isConflict());

        when(service.create(any(), any(), any(), any(), any(), any(), any(AuthUserContext.class)))
                .thenThrow(new BusinessRuleException("Pages.TicketBugMetrics.InternalBugCount.Invalid"));
        mockMvc.perform(post("/api/v1/ticket-bug-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectId", PROJECT_ID,
                                "repositoryId", REPOSITORY_ID,
                                "ticketId", TICKET_ID,
                                "internalBugCount", -1,
                                "customerBugCount", 0
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAccess_returnsNoContent_whenCallerHasAccess() throws Exception {
        mockMvc.perform(get("/api/v1/ticket-bug-metrics/access").param("projectId", PROJECT_ID.toString()))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(service).requireAccess(eq(PROJECT_ID), any(AuthUserContext.class));
    }

    @Test
    void getTicketOptions_returnsList() throws Exception {
        when(service.ticketOptions(eq(PROJECT_ID), eq(REPOSITORY_ID), any(AuthUserContext.class)))
                .thenReturn(List.of(new TicketOption(TICKET_ID, "PROJ-1", "Fix bug")));

        mockMvc.perform(get("/api/v1/ticket-bug-metrics/ticket-options")
                        .param("projectId", PROJECT_ID.toString())
                        .param("repositoryId", REPOSITORY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(TICKET_ID.toString()))
                .andExpect(jsonPath("$[0].externalTicketKey").value("PROJ-1"));
    }

    @Test
    void getOptions_returnsProjectsRepositoriesTickets() throws Exception {
        when(service.options(eq(PROJECT_ID), eq((UUID) null), any(AuthUserContext.class)))
                .thenReturn(new TicketBugMetricsOptions(
                        List.of(new TicketBugMetricsOption(PROJECT_ID.toString(), "Project 1", "PM")),
                        List.of(new TicketBugMetricsOption(REPOSITORY_ID.toString(), "Repo 1", null)),
                        List.of(new TicketBugMetricsOption(TICKET_ID.toString(), "PROJ-1", null))
                ));

        mockMvc.perform(get("/api/v1/ticket-bug-metrics/options").param("projectId", PROJECT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].value").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.repositories[0].value").value(REPOSITORY_ID.toString()))
                .andExpect(jsonPath("$.tickets[0].value").value(TICKET_ID.toString()));
    }

    private TicketBugMetricsModel activeMetric() {
        return TicketBugMetricsModel.builder()
                .ticketBugId(TICKET_BUG_ID)
                .projectId(PROJECT_ID)
                .repositoryId(REPOSITORY_ID)
                .ticketId(TICKET_ID)
                .internalBugCount(1)
                .customerBugCount(0)
                .status(TicketBugMetricsModel.Status.ACTIVE)
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
