package com.sdd.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.usecase.governance.RoleService;
import com.sdd.platform.domain.exception.BusinessRuleException;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.Role;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleControllerIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private RoleService service;
    private AppUser caller;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(RoleService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        caller = AppUser.builder()
                .id(1L)
                .provider("google")
                .providerUid("admin")
                .email("admin@example.com")
                .displayName("Admin")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver(caller))
                .build();
    }

    @Test
    void list_returnsRawRoleArray() throws Exception {
        Role role = role(UUID.randomUUID(), "PM");
        when(service.list(eq("pm"), eq("ACTIVE"), eq(caller))).thenReturn(List.of(role));

        mockMvc.perform(get("/api/v1/roles")
                        .param("keyword", "pm")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleId").value(role.getRoleId().toString()))
                .andExpect(jsonPath("$[0].roleName").value("PM"))
                .andExpect(jsonPath("$[0].description").value("Description"));

        verify(service).list(eq("pm"), eq("ACTIVE"), eq(caller));
    }

    @Test
    void create_returns201AndRoleBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(eq("QA"), eq("Quality"), eq(caller))).thenReturn(role(id, "QA"));

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleName", "QA",
                                "description", "Quality"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleId").value(id.toString()))
                .andExpect(jsonPath("$.roleName").value("QA"));
    }

    @Test
    void create_returns400ForDuplicateRoleName() throws Exception {
        when(service.create(eq("PM"), nullable(String.class), eq(caller)))
                .thenThrow(new BusinessRuleException("Pages.RoleManagement.RoleName.Duplicate"));

        mockMvc.perform(post("/api/v1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roleName", "PM"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DOMAIN_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Pages.RoleManagement.RoleName.Duplicate"));
    }

    @Test
    void update_returnsUpdatedRoleBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(eq(id), eq("PM"), eq("Product"), eq(caller))).thenReturn(role(id, "PM"));

        mockMvc.perform(put("/api/v1/roles/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "roleName", "PM",
                                "description", "Product"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("PM"));
    }

    @Test
    void logicalDelete_usesPutEndpointAndReturnsDeletedRoleBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.logicalDelete(eq(id), eq(caller))).thenReturn(role(id, "PM"));

        mockMvc.perform(put("/api/v1/roles/{id}/delete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(id.toString()))
                .andExpect(jsonPath("$.roleName").value("PM"))
                .andExpect(jsonPath("$.description").value("Description"));
    }

    @Test
    void directApiReturns403WhenServiceRejectsRole() throws Exception {
        when(service.list(nullable(String.class), nullable(String.class), eq(caller)))
                .thenThrow(new ForbiddenException("Component.Permission.Denied"));

        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Component.Permission.Denied"));
    }

    private Role role(UUID id, String name) {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-10T00:00:00Z");
        return Role.builder()
                .roleId(id)
                .roleName(name)
                .description("Description")
                .createdAt(now)
                .createdBy("tester")
                .updatedAt(now)
                .updatedBy("tester")
                .deleteFlag(0)
                .build();
    }

    private static class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
        private final AppUser caller;

        private CurrentUserArgumentResolver(AppUser caller) {
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
