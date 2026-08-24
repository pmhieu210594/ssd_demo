package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.OrganizationService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.exception.NotFoundException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Organization;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationControllerIntegrationTest {

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;
        private OrganizationService service;
        private AppUser caller;

        @BeforeEach
        void setUp() {
                service = Mockito.mock(OrganizationService.class);
                objectMapper = new ObjectMapper().findAndRegisterModules();
                caller = AppUser.builder()
                                .id(1L)
                                .provider("google")
                                .providerUid("phase6-admin")
                                .email("phase6-admin@example.com")
                                .displayName("Phase 6 Admin")
                                .role(AppUser.Role.ADMIN)
                                .active(true)
                                .build();

                mockMvc = MockMvcBuilders
                                .standaloneSetup(new OrganizationController(service))
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .setCustomArgumentResolvers(new Phase6CurrentUserArgumentResolver(caller))
                                .build();
        }

        @Test
        void list_returnsPagedOrganizationsAndPassesSearchParameters() throws Exception {
                Organization organization = organization(UUID.randomUUID(), "ORG-001", "Acme",
                                Organization.OrganizationStatus.ACTIVE, 0L);
                when(service.search(eq("Acme"), eq("ACTIVE"), eq(1), eq(10), eq(caller)))
                                .thenReturn(new PageResult<>(List.of(organization), 1, 10, 11, 2));

                mockMvc.perform(get("/api/v1/organizations")
                                .param("keyword", "Acme")
                                .param("status", "ACTIVE")
                                .param("page", "1")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[0].organizationCode").value("ORG-001"))
                                .andExpect(jsonPath("$.items[0].organizationName").value("Acme"))
                                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                                .andExpect(jsonPath("$.page").value(1))
                                .andExpect(jsonPath("$.size").value(10))
                                .andExpect(jsonPath("$.totalElements").value(11))
                                .andExpect(jsonPath("$.totalPages").value(2));

                verify(service).search(eq("Acme"), eq("ACTIVE"), eq(1), eq(10), eq(caller));
        }

        @Test
        void get_returnsOrganizationDetail() throws Exception {
                UUID id = UUID.randomUUID();
                when(service.get(eq(id), eq(caller)))
                                .thenReturn(organization(id, "ORG-DETAIL", "Detail",
                                                Organization.OrganizationStatus.ACTIVE, 7L));

                mockMvc.perform(get("/api/v1/organizations/{id}", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.organizationId").value(id.toString()))
                                .andExpect(jsonPath("$.organizationCode").value("ORG-DETAIL"))
                                .andExpect(jsonPath("$.organizationName").value("Detail"))
                                .andExpect(jsonPath("$.version").value(7));
        }

        @Test
        void get_returns404WhenMissing() throws Exception {
                UUID id = UUID.randomUUID();
                when(service.get(eq(id), eq(caller))).thenThrow(new NotFoundException("Pages.Organization.NotFound"));

                mockMvc.perform(get("/api/v1/organizations/{id}", id))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                                .andExpect(jsonPath("$.message").value("Pages.Organization.NotFound"));
        }

        @Test
        void create_returns201AndOrganizationBody() throws Exception {
                UUID id = UUID.randomUUID();
                when(service.create(eq("ORG-002"), eq("Beta"), eq("Description"), eq(caller)))
                                .thenReturn(organization(id, "ORG-002", "Beta", Organization.OrganizationStatus.ACTIVE,
                                                0L));

                mockMvc.perform(post("/api/v1/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationCode", "ORG-002",
                                                "organizationName", "Beta",
                                                "description", "Description"))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.organizationId").value(id.toString()))
                                .andExpect(jsonPath("$.organizationCode").value("ORG-002"))
                                .andExpect(jsonPath("$.organizationName").value("Beta"))
                                .andExpect(jsonPath("$.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.version").value(0));
        }

        @Test
        void create_returns400WhenServiceRejectsDuplicateCode() throws Exception {
                when(service.create(eq("ORG-DUP"), eq("Duplicate"), nullable(String.class), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Organization.Code.Duplicate"));

                mockMvc.perform(post("/api/v1/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationCode", "ORG-DUP",
                                                "organizationName", "Duplicate"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("DOMAIN_RULE_VIOLATION"))
                                .andExpect(jsonPath("$.message").value("Pages.Organization.Code.Duplicate"));
        }

        @Test
        void create_returns400WhenServiceRejectsDuplicateName() throws Exception {
                when(service.create(eq("ORG-OK"), eq("Duplicate Name"), nullable(String.class), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Organization.Name.Duplicate"));

                mockMvc.perform(post("/api/v1/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationCode", "ORG-OK",
                                                "organizationName", "Duplicate Name"))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("DOMAIN_RULE_VIOLATION"))
                                .andExpect(jsonPath("$.message").value("Pages.Organization.Name.Duplicate"));
        }

        @Test
        void update_returnsUpdatedOrganizationBody() throws Exception {
                UUID id = UUID.randomUUID();
                when(service.update(eq(id), eq("ORG-003"), eq("Gamma"), eq("Updated"), eq("ACTIVE"), eq(1L),
                                eq(caller)))
                                .thenReturn(organization(id, "ORG-003", "Gamma", Organization.OrganizationStatus.ACTIVE,
                                                2L));

                mockMvc.perform(put("/api/v1/organizations/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationCode", "ORG-003",
                                                "organizationName", "Gamma",
                                                "description", "Updated",
                                                "status", "ACTIVE",
                                                "version", 1L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.organizationCode").value("ORG-003"))
                                .andExpect(jsonPath("$.organizationName").value("Gamma"))
                                .andExpect(jsonPath("$.version").value(2));
        }

        @Test
        void update_returns400WhenDuplicateName() throws Exception {
                UUID id = UUID.randomUUID();
                when(service.update(eq(id), eq("ORG-003"), eq("Duplicate Name"), nullable(String.class), eq("ACTIVE"),
                                eq(1L), eq(caller)))
                                .thenThrow(new BusinessRuleException("Pages.Organization.Name.Duplicate"));

                mockMvc.perform(put("/api/v1/organizations/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationCode", "ORG-003",
                                                "organizationName", "Duplicate Name",
                                                "status", "ACTIVE",
                                                "version", 1L))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Pages.Organization.Name.Duplicate"));
        }

        @Test
        void update_returns409WhenVersionIsStale() throws Exception {
                UUID id = UUID.randomUUID();
                when(service.update(eq(id), eq("ORG-003"), eq("Gamma"), nullable(String.class), eq("ACTIVE"), eq(1L),
                                eq(caller)))
                                .thenThrow(new OptimisticLockingException("Pages.Organization.Conflict.Version"));

                mockMvc.perform(put("/api/v1/organizations/{id}", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                                "organizationCode", "ORG-003",
                                                "organizationName", "Gamma",
                                                "status", "ACTIVE",
                                                "version", 1L))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.error").value("CONFLICT"))
                                .andExpect(jsonPath("$.message").value("Pages.Organization.Conflict.Version"));
        }

        @Test
        void softDelete_callsDeleteEndpointWithVersion() throws Exception {
                UUID id = UUID.randomUUID();
                when(service.softDelete(eq(id), eq(4L), eq(caller)))
                                .thenReturn(organization(id, "ORG-004", "Delta",
                                                Organization.OrganizationStatus.DELETED, 5L));

                mockMvc.perform(patch("/api/v1/organizations/{id}/delete", id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("version", 4L))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.organizationCode").value("ORG-004"))
                                .andExpect(jsonPath("$.status").value("DELETED"))
                                .andExpect(jsonPath("$.version").value(5));
        }

        @Test
        void directOrganizationApiReturns403WhenServiceRejectsNonAdmin() throws Exception {
                when(service.search(nullable(String.class), nullable(String.class), eq(0), eq(20), eq(caller)))
                                .thenThrow(new ForbiddenException("Component.Permission.Denied"));

                mockMvc.perform(get("/api/v1/organizations"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                                .andExpect(jsonPath("$.message").value("Component.Permission.Denied"));
        }

        private Organization organization(UUID id, String code, String name, Organization.OrganizationStatus status,
                        long version) {
                OffsetDateTime now = OffsetDateTime.parse("2026-06-10T00:00:00Z");
                return Organization.builder()
                                .organizationId(id)
                                .organizationCode(code)
                                .organizationName(name)
                                .description("Description")
                                .status(status)
                                .createdAt(now)
                                .createdBy("tester")
                                .updatedAt(now)
                                .updatedBy("tester")
                                .deletedAt(status == Organization.OrganizationStatus.DELETED ? now : null)
                                .deletedBy(status == Organization.OrganizationStatus.DELETED ? "tester" : null)
                                .version(version)
                                .build();
        }

        private static class Phase6CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
                private final AppUser caller;

                private Phase6CurrentUserArgumentResolver(AppUser caller) {
                        this.caller = caller;
                }

                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(AppUser.class)
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
