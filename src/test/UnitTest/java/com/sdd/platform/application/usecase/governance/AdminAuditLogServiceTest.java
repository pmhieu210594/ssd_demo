package com.sdd.platform.application.usecase.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdd.platform.application.port.out.persistence.AdminAuditLogPersistencePort;
import com.sdd.platform.application.usecase.common.PageResult;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogDetail;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogEntry;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogFilter;
import com.sdd.platform.application.usecase.governance.AdminAuditLogModels.AdminAuditLogListItem;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.AuthUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAuditLogServiceTest {

    private AdminAuditLogWriter writer;
    private AdminAuditLogPersistencePort readPort;
    private AdminAuditLogService service;

    @BeforeEach
    void setUp() {
        writer = Mockito.mock(AdminAuditLogWriter.class);
        readPort = Mockito.mock(AdminAuditLogPersistencePort.class);
        service = new AdminAuditLogService(writer, readPort, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        org.slf4j.MDC.clear();
    }

    @Test
    void logCreate_masksSecretsAndCapturesRequestContext() {
        bindRequest("Mozilla/5.0 (Test Browser) " + "x".repeat(300));
        org.slf4j.MDC.put("traceId", "trc_create_01");

        AppUser caller = adminUser();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Role Editor");
        payload.put("passwordHash", "super-secret");
        payload.put("apiKey", "api-secret");
        payload.put("status", "ACTIVE");

        service.logCreate(caller, "ROLE", "ROLE", "role-1", payload);

        ArgumentCaptor<AdminAuditLogEntry> captor = ArgumentCaptor.forClass(AdminAuditLogEntry.class);
        verify(writer).insertWithinCallerTransaction(captor.capture());
        AdminAuditLogEntry entry = captor.getValue();

        assertThat(entry.actorUserId()).isEqualTo(UUID.fromString(caller.getProviderUid()));
        assertThat(entry.actorUsername()).isEqualTo("admin@example.com");
        assertThat(entry.actorRoleName()).isEqualTo("ADMIN");
        assertThat(entry.module()).isEqualTo("ROLE");
        assertThat(entry.entityType()).isEqualTo("ROLE");
        assertThat(entry.entityId()).isEqualTo("role-1");
        assertThat(entry.operationType()).isEqualTo("CREATE");
        assertThat(entry.beforeValueJson()).isNull();
        assertThat(entry.afterValueJson()).contains("\"name\":\"Role Editor\"");
        assertThat(entry.afterValueJson()).doesNotContain("passwordHash");
        assertThat(entry.afterValueJson()).doesNotContain("apiKey");
        assertThat(entry.userAgent()).hasSize(256);
        assertThat(entry.traceId()).isEqualTo("trc_create_01");
    }

    @Test
    void logUpdate_recordsChangedFieldsAndMasksSnapshots() {
        bindRequest("Mozilla/5.0");
        org.slf4j.MDC.put("traceId", "trc_update_01");

        AppUser caller = adminUser();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("name", "Role Admin");
        before.put("passwordHash", "old-secret");
        before.put("apiKey", "old-key");
        before.put("status", "ACTIVE");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", "Role Reviewer");
        after.put("passwordHash", "new-secret");
        after.put("apiKey", "new-key");
        after.put("status", "ACTIVE");

        service.logUpdate(caller, "ROLE", "ROLE", "role-1", before, after);

        ArgumentCaptor<AdminAuditLogEntry> captor = ArgumentCaptor.forClass(AdminAuditLogEntry.class);
        verify(writer).insertWithinCallerTransaction(captor.capture());
        AdminAuditLogEntry entry = captor.getValue();

        assertThat(entry.changedFields()).isEqualTo("apiKey,name,passwordHash");
        assertThat(entry.beforeValueJson()).contains("\"name\":\"Role Admin\"");
        assertThat(entry.afterValueJson()).contains("\"name\":\"Role Reviewer\"");
        assertThat(entry.beforeValueJson()).doesNotContain("passwordHash");
        assertThat(entry.afterValueJson()).doesNotContain("apiKey");
    }

    @Test
    void logCrudFailure_truncatesLongMessages() {
        bindRequest("Mozilla/5.0");
        org.slf4j.MDC.put("traceId", "trc_failed_01");

        String errorMessage = "validation failed ".repeat(40) + "secret-token-should-not-leak";

        service.logCrudFailure(adminUser(), "ROLE", "ROLE", "role-1", "UPDATE", errorMessage);

        ArgumentCaptor<AdminAuditLogEntry> captor = ArgumentCaptor.forClass(AdminAuditLogEntry.class);
        verify(writer).insertWithinCallerTransaction(captor.capture());
        AdminAuditLogEntry entry = captor.getValue();

        assertThat(entry.errorMessage()).hasSize(500);
        assertThat(entry.errorMessage()).doesNotContain("secret-token-should-not-leak");
        assertThat(entry.beforeValueJson()).isNull();
        assertThat(entry.afterValueJson()).isNull();
    }

    @Test
    void logLoginSuccess_andLogout_delegateToIndependentWriter() {
        bindRequest("Mozilla/5.0");
        org.slf4j.MDC.put("traceId", "trc_auth_01");
        AuthUserContext context = AuthUserContext.builder()
                .userAccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .username("admin.khoa")
                .displayName("Admin Khoa")
                .email("admin@example.com")
                .role("ADMIN")
                .accessScopes(List.of("role:1"))
                .build();

        service.logLoginSuccess(context);
        service.logLogout(context);

        ArgumentCaptor<AdminAuditLogEntry> captor = ArgumentCaptor.forClass(AdminAuditLogEntry.class);
        verify(writer, Mockito.times(2)).insertIndependently(captor.capture());
        List<AdminAuditLogEntry> entries = captor.getAllValues();

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).operationType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(entries.get(1).operationType()).isEqualTo("LOGOUT");
        assertThat(entries.get(0).userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(entries.get(1).traceId()).isEqualTo("trc_auth_01");
    }

    @Test
    void logLoginFailure_usesAttemptedUsername_andSanitizesMessage() {
        bindRequest("Mozilla/5.0");
        org.slf4j.MDC.put("traceId", "trc_auth_02");

        service.logLoginFailure("unknown.user", "invalid credentials with secret-123");

        ArgumentCaptor<AdminAuditLogEntry> captor = ArgumentCaptor.forClass(AdminAuditLogEntry.class);
        verify(writer).insertIndependently(captor.capture());
        AdminAuditLogEntry entry = captor.getValue();

        assertThat(entry.actorUserId()).isNull();
        assertThat(entry.actorUsername()).isEqualTo("unknown.user");
        assertThat(entry.module()).isEqualTo("LOGIN");
        assertThat(entry.entityType()).isEqualTo("LOGIN_SESSION");
        assertThat(entry.operationType()).isEqualTo("LOGIN_FAILED");
        assertThat(entry.errorMessage()).contains("invalid credentials");
        assertThat(entry.errorMessage()).doesNotContain("secret-123");
    }

    @Test
    void logLogout_ignoresNullContext() {
        service.logLogout(null);

        verifyNoInteractions(writer);
    }

    @Test
    void list_and_detail_delegateToReadPort() {
        AdminAuditLogFilter filter = new AdminAuditLogFilter(
                "ROLE",
                "UPDATE",
                "admin",
                OffsetDateTime.parse("2026-07-09T10:00:00Z"),
                OffsetDateTime.parse("2026-07-10T10:00:00Z"),
                "search"
        );
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
                20,
                1L,
                1
        );
        AdminAuditLogDetail detail = new AdminAuditLogDetail(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
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

        when(readPort.search(eq(filter), eq(1), eq(20))).thenReturn(page);
        when(readPort.findById(detail.id())).thenReturn(Optional.of(detail));

        assertThat(service.list(filter, 1, 20)).isEqualTo(page);
        assertThat(service.detail(detail.id())).contains(detail);
    }

    private void bindRequest(String userAgent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", userAgent);
        request.addHeader("X-Forwarded-For", "203.0.113.42, 10.0.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .id(1L)
                .provider("google")
                .providerUid("11111111-1111-1111-1111-111111111111")
                .email("admin@example.com")
                .displayName("Admin Khoa")
                .role(AppUser.Role.ADMIN)
                .active(true)
                .build();
    }
}
