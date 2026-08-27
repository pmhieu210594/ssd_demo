package com.sdd.platform.web.rest;

import com.sdd.platform.application.port.out.persistence.UserAccountAdminRepositoryPort;
import com.sdd.platform.application.usecase.governance.UserAccountAdminService;
import com.sdd.platform.domain.model.AppUser;
import com.sdd.platform.domain.model.RoleOption;
import com.sdd.platform.domain.model.UserAccountAdminView;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = UserAccountAdminApiIntegrationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserAccountAdminApiIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @org.springframework.context.annotation.Import({
            UserAccountAdminController.class,
            UserAccountAdminService.class,
            GlobalExceptionHandler.class,
            UserAccountAdminApiIntegrationTest.Config.class
    })
    static class TestApp {
    }

    @TestConfiguration
    static class Config {
        @Bean
        WebMvcConfigurer testCurrentUserResolver() {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(MethodParameter parameter) {
                            return parameter.getParameterType().equals(AppUser.class)
                                    && parameter.hasParameterAnnotation(CurrentUser.class);
                        }

                        @Override
                        public Object resolveArgument(MethodParameter parameter,
                                                      ModelAndViewContainer mavContainer,
                                                      NativeWebRequest webRequest,
                                                      WebDataBinderFactory binderFactory) {
                            return webRequest.getAttribute("testAppUser", NativeWebRequest.SCOPE_REQUEST);
                        }
                    });
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAccountAdminRepositoryPort repository;

    private AppUser admin;
    private AppUser viewer;
    private UUID adminRoleId;
    private UUID userRoleId;
    private UUID accountId;
    private UUID memberKey;

    @BeforeEach
    void setUp() {
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
        adminRoleId = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        userRoleId = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        accountId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        memberKey = UUID.fromString("00000000-0000-0000-0000-000000000201");
        when(repository.findRoleById(adminRoleId)).thenReturn(Optional.of(role(adminRoleId, "ADMIN")));
        when(repository.findRoleById(userRoleId)).thenReturn(Optional.of(role(userRoleId, "USER")));
    }

    @Test
    void list_returnsAccountPageWithoutSecretFields() throws Exception {
        when(repository.findPage(isNull(), eq("ALL"), eq(List.of()), eq(0), eq(20)))
                .thenReturn(List.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", true)));
        when(repository.count(isNull(), eq("ALL"), eq(List.of()))).thenReturn(1L);

        mockMvc.perform(get("/api/v1/admin/user-accounts")
                        .requestAttr("testAppUser", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username", is("user01")))
                .andExpect(jsonPath("$.items[0].roleName", is("USER")))
                .andExpect(jsonPath("$.items[0].teamId", nullValue()))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].token").doesNotExist())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void list_forNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/user-accounts")
                        .requestAttr("testAppUser", viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("FORBIDDEN")))
                .andExpect(jsonPath("$.message", is("Component.Permission.Denied")));
    }

    @Test
    void list_acceptsRoleIdAndRoleIdsFilters() throws Exception {
        when(repository.findPage(isNull(), eq("ACTIVE"), eq(List.of(userRoleId)), eq(25), eq(25)))
                .thenReturn(List.of());
        when(repository.count(isNull(), eq("ACTIVE"), eq(List.of(userRoleId)))).thenReturn(0L);

        mockMvc.perform(get("/api/v1/admin/user-accounts")
                        .param("status", "ACTIVE")
                        .param("roleId", userRoleId.toString())
                        .param("page", "1")
                        .param("size", "25")
                        .requestAttr("testAppUser", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(1)))
                .andExpect(jsonPath("$.size", is(25)));

        verify(repository).findPage(isNull(), eq("ACTIVE"), eq(List.of(userRoleId)), eq(25), eq(25));
    }

    @Test
    void get_returnsAccountDetailWithoutSecretFields() throws Exception {
        when(repository.findById(accountId))
                .thenReturn(Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", true)));

        mockMvc.perform(get("/api/v1/admin/user-accounts/{accountId}", accountId)
                        .requestAttr("testAppUser", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(accountId.toString())))
                .andExpect(jsonPath("$.memberKey", is(memberKey.toString())))
                .andExpect(jsonPath("$.teamId", nullValue()))
                .andExpect(jsonPath("$.teamName", nullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.privateKey").doesNotExist());
    }

    @Test
    void roles_returnsRoleOptionsForAdminOnly() throws Exception {
        when(repository.findRoles()).thenReturn(List.of(role(adminRoleId, "ADMIN"), role(userRoleId, "USER")));

        mockMvc.perform(get("/api/v1/admin/roles")
                        .requestAttr("testAppUser", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName", is("ADMIN")))
                .andExpect(jsonPath("$[1].roleId", is(userRoleId.toString())));

        mockMvc.perform(get("/api/v1/admin/roles")
                        .requestAttr("testAppUser", viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("Component.Permission.Denied")));
    }

    @Test
    void create_createsAccountAndMemberMapping() throws Exception {
        when(repository.findById(any())).thenReturn(Optional.of(account(accountId, memberKey, "new.user", "New User", userRoleId, "USER", true)));

        mockMvc.perform(post("/api/v1/admin/user-accounts")
                        .requestAttr("testAppUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.user",
                                  "fullname": "New User",
                                  "email": "new.user@example.com",
                                  "password": "Password1!",
                                  "confirmPassword": "Password1!",
                                  "roleId": "%s",
                                  "teamId": "00000000-0000-0000-0000-000000000999",
                                  "isActive": true
                                }
                                """.formatted(userRoleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId", notNullValue()))
                .andExpect(jsonPath("$.username", is("new.user")))
                .andExpect(jsonPath("$.roleId", is(userRoleId.toString())))
                .andExpect(jsonPath("$.teamId", nullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(repository).insertMember(any(), eq(userRoleId), eq("new.user"), eq("admin@example.com"), any());
        verify(repository).insertAccount(any(), any(), eq("new.user"), eq("New User"), eq("new.user@example.com"), any(), eq("bcrypt"), eq(true), eq("admin@example.com"), any());
    }

    @Test
    void create_rejectsDuplicateUsername() throws Exception {
        when(repository.existsUsername("new.user", null)).thenReturn(true);

        mockMvc.perform(post("/api/v1/admin/user-accounts")
                        .requestAttr("testAppUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.user",
                                  "fullname": "New User",
                                  "email": null,
                                  "password": "Password1!",
                                  "confirmPassword": "Password1!",
                                  "roleId": "%s",
                                  "isActive": true
                                }
                                """.formatted(userRoleId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("DOMAIN_RULE_VIOLATION")))
                .andExpect(jsonPath("$.message", is("Pages.UserAccounts.Username.Duplicate")));
    }

    @Test
    void create_rejectsMissingRoleWithSafeMessage() throws Exception {
        mockMvc.perform(post("/api/v1/admin/user-accounts")
                        .requestAttr("testAppUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "missing.role",
                                  "fullname": "Missing Role",
                                  "password": "Password1!",
                                  "confirmPassword": "Password1!",
                                  "isActive": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("DOMAIN_RULE_VIOLATION")))
                .andExpect(jsonPath("$.message", is("Pages.UserAccounts.Role.Required")))
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }

    @Test
    void create_rejectsPasswordMismatchWithSafeMessage() throws Exception {
        mockMvc.perform(post("/api/v1/admin/user-accounts")
                        .requestAttr("testAppUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "mismatch.user",
                                  "fullname": "Mismatch User",
                                  "password": "Password1!",
                                  "confirmPassword": "Password2!",
                                  "roleId": "%s",
                                  "isActive": true
                                }
                                """.formatted(userRoleId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("DOMAIN_RULE_VIOLATION")))
                .andExpect(jsonPath("$.message", is("Pages.UserAccounts.Password.Mismatch")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void update_changesRoleAndLeavesTeamAssignmentOutOfScope() throws Exception {
        when(repository.findById(accountId)).thenReturn(
                Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", true)),
                Optional.of(account(accountId, memberKey, "user01", "User One Updated", adminRoleId, "ADMIN", true))
        );
        when(repository.updateAccount(eq(accountId), eq("User One Updated"), eq("user01@example.com"), eq(true), eq("admin@example.com"), any()))
                .thenReturn(1);
        when(repository.updateMemberRole(eq(memberKey), eq(adminRoleId), eq("admin@example.com"), any()))
                .thenReturn(1);

        mockMvc.perform(put("/api/v1/admin/user-accounts/{accountId}", accountId)
                        .requestAttr("testAppUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullname": "User One Updated",
                                  "email": "user01@example.com",
                                  "roleId": "%s",
                                  "isActive": true
                                }
                                """.formatted(adminRoleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("user01")))
                .andExpect(jsonPath("$.roleName", is("ADMIN")))
                .andExpect(jsonPath("$.teamId", nullValue()));

        verify(repository).updateMemberRole(eq(memberKey), eq(adminRoleId), eq("admin@example.com"), any());
    }

    @Test
    void update_rejectsInvalidRole() throws Exception {
        UUID missingRoleId = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        when(repository.findById(accountId))
                .thenReturn(Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", true)));
        when(repository.findRoleById(missingRoleId)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/admin/user-accounts/{accountId}", accountId)
                        .requestAttr("testAppUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullname": "User One",
                                  "email": "user01@example.com",
                                  "roleId": "%s",
                                  "isActive": true
                                }
                                """.formatted(missingRoleId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Pages.UserAccounts.Role.Invalid")));
    }

    @Test
    void activateAndDeactivateChangeStatusWithoutHardDeleteEndpoint() throws Exception {
        when(repository.findById(accountId)).thenReturn(
                Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", false)),
                Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", true)),
                Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", true)),
                Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", false))
        );
        when(repository.updateActive(eq(accountId), eq(true), eq("admin@example.com"), any())).thenReturn(1);
        when(repository.updateActive(eq(accountId), eq(false), eq("admin@example.com"), any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/admin/user-accounts/{accountId}/activate", accountId)
                        .requestAttr("testAppUser", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(true)));

        mockMvc.perform(post("/api/v1/admin/user-accounts/{accountId}/deactivate", accountId)
                        .requestAttr("testAppUser", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));
    }

    @Test
    void deactivateLastActiveAdmin_returnsBusinessRuleError() throws Exception {
        when(repository.findById(accountId)).thenReturn(Optional.of(account(accountId, memberKey, "admin01", "Admin", adminRoleId, "ADMIN", true)));
        when(repository.countActiveAdminsExcluding(accountId)).thenReturn(0L);

        mockMvc.perform(post("/api/v1/admin/user-accounts/{accountId}/deactivate", accountId)
                        .requestAttr("testAppUser", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Pages.UserAccounts.LastAdmin")));
    }

    @Test
    void resetPassword_doesNotReturnPasswordOrHash() throws Exception {
        when(repository.findById(accountId)).thenReturn(Optional.of(account(accountId, memberKey, "user01", "User One", userRoleId, "USER", true)));
        when(repository.updatePassword(eq(accountId), any(), eq("bcrypt"), eq("admin@example.com"), any()))
                .thenReturn(1);

        mockMvc.perform(post("/api/v1/admin/user-accounts/{accountId}/reset-password", accountId)
                        .requestAttr("testAppUser", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"Password2!","confirmPassword":"Password2!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("user01")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private RoleOption role(UUID roleId, String roleName) {
        return RoleOption.builder().roleId(roleId).roleName(roleName).build();
    }

    private UserAccountAdminView account(UUID accountId, UUID memberKey, String username, String fullname,
                                         UUID roleId, String roleName, boolean active) {
        return UserAccountAdminView.builder()
                .accountId(accountId)
                .memberKey(memberKey)
                .username(username)
                .fullname(fullname)
                .email(username + "@example.com")
                .roleId(roleId)
                .roleName(roleName)
                .teamId(null)
                .teamName(null)
                .active(active)
                .build();
    }
}
