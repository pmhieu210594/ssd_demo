package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.ProjectService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Project;
import com.sdd.platform.domain.model.ProjectTeamAssignment;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerTest {

    private static final UUID PROJECT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ProjectService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ProjectService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProjectController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(adminUser()))
                .build();
    }

    @Test
    void list_returnsPagedProjects() throws Exception {
        when(service.search(eq(CUSTOMER_ID), eq("Data"), eq("ACTIVE"), eq(0), eq(20), any(AppUser.class)))
                .thenReturn(new PageResult<>(List.of(activeProject()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/projects")
                        .param("customerId", CUSTOMER_ID.toString())
                        .param("keyword", "Data")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.items[0].projectAlias").value("Project Alpha"))
                .andExpect(jsonPath("$.items[0].customerName").value("Customer Demo"))
                .andExpect(jsonPath("$.items[0].riskLevel").value("MEDIUM"));
    }

    @Test
    void get_returnsDetailWithTeamAssignments() throws Exception {
        when(service.get(eq(PROJECT_ID), any(AppUser.class))).thenReturn(activeProject());

        mockMvc.perform(get("/api/v1/projects/{id}", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.teamAssignments[0].teamId").value(TEAM_ID.toString()));
    }

    @Test
    void create_returnsCreatedProjectWithoutVersion() throws Exception {
        when(service.create(eq(CUSTOMER_ID), eq("Project Alpha"), eq("Internal"), eq("LOW"), eq(List.of(TEAM_ID)), any(AppUser.class)))
                .thenReturn(activeProject());

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", CUSTOMER_ID,
                                "projectAlias", "Project Alpha",
                                "projectType", "Internal",
                                "riskLevel", "LOW",
                                "teamIds", List.of(TEAM_ID)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectAlias").value("Project Alpha"))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.teamAssignments[0].teamId").value(TEAM_ID.toString()));
    }

    @Test
    void update_usesPutWithoutVersion() throws Exception {
        when(service.update(eq(PROJECT_ID), eq(CUSTOMER_ID), eq("Project Beta"), eq("Customer Facing"), eq("INFO"), eq(List.of()), any(AppUser.class)))
                .thenReturn(activeProject());

        mockMvc.perform(put("/api/v1/projects/{id}", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", CUSTOMER_ID,
                                "projectAlias", "Project Beta",
                                "projectType", "Customer Facing",
                                "riskLevel", "INFO",
                                "teamIds", List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()));
    }

    @Test
    void softDelete_usesPutDeleteEndpoint() throws Exception {
        Project deleted = activeProject();
        deleted.setStatus(Project.ProjectStatus.DELETED);
        deleted.setDeleteFlag(true);
        deleted.setDeletedAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"));
        when(service.softDelete(eq(PROJECT_ID), any(AppUser.class))).thenReturn(deleted);

        mockMvc.perform(put("/api/v1/projects/{id}/delete", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.deleteFlag").value(true));
    }

    @Test
    void exceptions_mapToExpectedStatus() throws Exception {
        when(service.get(eq(PROJECT_ID), any(AppUser.class)))
                .thenThrow(new NotFoundException("Pages.Project.NotFound"));

        mockMvc.perform(get("/api/v1/projects/{id}", PROJECT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pages.Project.NotFound"));

        when(service.create(eq(CUSTOMER_ID), eq("Project Alpha"), eq("Internal"), eq("LOW"), eq(List.of()), any(AppUser.class)))
                .thenThrow(new BusinessRuleException("Pages.Project.Alias.Duplicate"));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", CUSTOMER_ID,
                                "projectAlias", "Project Alpha",
                                "projectType", "Internal",
                                "riskLevel", "LOW",
                                "teamIds", List.of()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pages.Project.Alias.Duplicate"));

        when(service.get(eq(PROJECT_ID), any(AppUser.class)))
                .thenThrow(new ForbiddenException("Pages.Project.Error.Forbidden"));
        mockMvc.perform(get("/api/v1/projects/{id}", PROJECT_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Pages.Project.Error.Forbidden"));
    }

    private Project activeProject() {
        return Project.builder()
                .projectId(PROJECT_ID)
                .customerId(CUSTOMER_ID)
                .customerName("Customer Demo")
                .projectAlias("Project Alpha")
                .projectType("Internal")
                .riskLevel(Project.RiskLevel.MEDIUM)
                .status(Project.ProjectStatus.ACTIVE)
                .deleteFlag(false)
                .createdAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                .createdBy("admin@example.com")
                .updatedAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                .updatedBy("admin@example.com")
                .teamAssignments(List.of(ProjectTeamAssignment.builder()
                        .projectTeamId(UUID.randomUUID())
                        .projectId(PROJECT_ID)
                        .teamId(TEAM_ID)
                        .teamCode("TEAM-001")
                        .teamName("Team 001")
                        .status(Project.ProjectStatus.ACTIVE)
                        .createdAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                        .createdBy("admin@example.com")
                        .updatedAt(OffsetDateTime.parse("2026-06-16T00:00:00Z"))
                        .updatedBy("admin@example.com")
                        .build()))
                .build();
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .email("admin@example.com")
                .displayName("Admin")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }

    private static final class FixedCurrentUserResolver implements HandlerMethodArgumentResolver {
        private final AppUser user;

        private FixedCurrentUserResolver(AppUser user) {
            this.user = user;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(AppUser.class)
                    && parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return user;
        }
    }
}
