package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogDetail;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogFilter;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogListItem;
import com.sdd.platform.application.usecase.governance.AdminAuditLogService;
import com.sdd.platform.domain.model.AppUser;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuditLogControllerTest {

    private MockMvc mockMvc;
    private AdminAuditLogService service;
    private AppUser admin;
    private AppUser viewer;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(AdminAuditLogService.class);
        admin = AppUser.builder()
                .email("admin@example.com")
                .displayName("Admin User")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
        viewer = AppUser.builder()
                .email("viewer@example.com")
                .displayName("Viewer User")
                .role(AppUser.Role.VIEWER)
                .active(true)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuditLogController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(admin))
                .build();
    }

    @Test
    void list_returnsPageAndNormalizesPagination() throws Exception {
        OffsetDateTime from = OffsetDateTime.parse("2026-07-09T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-09T23:59:59Z");
        AdminAuditLogFilter filter = new AdminAuditLogFilter("ROLE", "UPDATE", "admin", from, to, "khoa");
        PageResult<AdminAuditLogListItem> page = new PageResult<>(
                List.of(new AdminAuditLogListItem(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        OffsetDateTime.parse("2026-07-09T10:05:00Z"),
                        "admin@example.com",
                        "ROLE",
                        "ROLE",
                        "role-1",
                        "UPDATE"
                )),
                0,
                100,
                1L,
                1
        );
        when(service.list(eq(filter), eq(0), eq(100))).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("module", "ROLE")
                        .param("operationType", "UPDATE")
                        .param("actor", "admin")
                        .param("dateFrom", "2026-07-09T00:00:00Z")
                        .param("dateTo", "2026-07-09T23:59:59Z")
                        .param("search", "khoa")
                        .param("page", "-4")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actorUsername").value("admin@example.com"))
                .andExpect(jsonPath("$.items[0].module").value("ROLE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(service).list(eq(filter), eq(0), eq(100));
    }

    @Test
    void detail_returnsReadOnlyDto() throws Exception {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AdminAuditLogDetail detail = new AdminAuditLogDetail(
                id,
                OffsetDateTime.parse("2026-07-09T10:05:00Z"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "admin@example.com",
                "ADMIN",
                "ROLE",
                "ROLE",
                "role-1",
                "UPDATE",
                "name",
                "{\"name\":\"before\"}",
                "{\"name\":\"after\"}",
                "Mozilla/5.0",
                null,
                "trc_detail_01"
        );
        when(service.detail(id)).thenReturn(Optional.of(detail));

        mockMvc.perform(get("/api/v1/admin/audit-logs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.module").value("ROLE"))
                .andExpect(jsonPath("$.operationType").value("UPDATE"))
                .andExpect(jsonPath("$.beforeValue").value("{\"name\":\"before\"}"))
                .andExpect(jsonPath("$.afterValue").value("{\"name\":\"after\"}"))
                .andExpect(jsonPath("$.traceId").value("trc_detail_01"));

        verify(service).detail(id);
    }

    @Test
    void detail_returnsNotFoundForMissingAuditEntry() throws Exception {
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(service.detail(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/audit-logs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Pages.AdminAuditLog.NotFound"));
    }

    @Test
    void nonAdminCallerIsForbidden() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuditLogController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new FixedCurrentUserResolver(viewer))
                .build();

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Component.Permission.Denied"));
    }

    private static final class FixedCurrentUserResolver implements HandlerMethodArgumentResolver {
        private final AppUser caller;

        private FixedCurrentUserResolver(AppUser caller) {
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
