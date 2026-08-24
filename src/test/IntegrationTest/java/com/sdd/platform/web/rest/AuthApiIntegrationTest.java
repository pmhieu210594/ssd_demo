package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.AuthLoginResult;
import com.sdd.platform.application.port.out.persistence.AuthTokenSessionRepositoryPort;
import com.sdd.platform.application.usecase.governance.AuthService;
import com.sdd.platform.application.usecase.governance.AuthTokenService;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.config.SecurityConfig;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.exception.GlobalExceptionHandler;
import com.sdd.platform.web.security.BearerTokenAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuthApiIntegrationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class
    })
    @Import({
            AuthController.class,
            MeController.class,
            SecurityConfig.class,
            BearerTokenAuthenticationFilter.class,
            GlobalExceptionHandler.class,
            AuthApiIntegrationTest.Config.class
    })
    static class TestApp {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthTokenService authTokenService;

    @MockBean
    private AuthTokenSessionRepositoryPort authTokenSessionRepository;

    @TestConfiguration
    static class Config {
        @Bean
        AppProperties appProperties() {
            return new AppProperties(
                    new AppProperties.Cors(List.of("http://127.0.0.1:5173")),
                    new AppProperties.Frontend("http://127.0.0.1:5173"),
                    new AppProperties.Jwt("test-secret", 2),
                    new AppProperties.Auth(false, false, 0),
                    null
            );
        }

    }

    @Test
    void login_returns_access_token_and_user_payload() throws Exception {
        AuthUserContext user = authenticatedUser("alice", "EDITOR");
        when(authService.login("alice", "secret")).thenReturn(new AuthLoginResult(
                "signed-token",
                "refresh-token",
                "Bearer",
                7200,
                user,
                "/"
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("signed-token")))
                .andExpect(jsonPath("$.refreshToken", is("refresh-token")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresInSeconds", is(7200)))
                .andExpect(jsonPath("$.user.username", is("alice")))
                .andExpect(jsonPath("$.user.email", is("alice@example.com")))
                .andExpect(jsonPath("$.redirectTo", is("/")));
    }

    @Test
    void login_rejects_username_longer_than_schema_limit() throws Exception {
        String longUsername = "a".repeat(101);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret"}
                                """.formatted(longUsername)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("VALIDATION_ERROR")));

        verifyNoInteractions(authService);
    }

    @Test
    void auth_me_requires_bearer_token() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void canonical_auth_me_returns_current_user_for_valid_bearer_token() throws Exception {
        AuthUserContext user = authenticatedUser("alice", "ADMIN");
        when(authService.currentUser(user)).thenReturn(user);
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(authentication(authenticated(user)))
                        .requestAttr("testUserContext", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("alice")))
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    void compatibility_me_path_returns_current_user_for_valid_bearer_token() throws Exception {
        AuthUserContext user = authenticatedUser("alice", "ADMIN");
        when(authService.currentUser(user)).thenReturn(user);
        mockMvc.perform(get("/api/v1/me")
                        .with(authentication(authenticated(user)))
                        .requestAttr("testUserContext", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("alice")))
                .andExpect(jsonPath("$.email", is("alice@example.com")))
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    void logout_returns_no_content_and_clears_server_state() throws Exception {
        AuthUserContext user = authenticatedUser("alice", "EDITOR");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(authentication(authenticated(user)))
                        .requestAttr("testUserContext", user))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Content-Type"));

        verify(authService).logout(argThat(actual ->
                actual != null
                        && "alice".equals(actual.getUsername())
                        && "EDITOR".equals(actual.getRole())
        ));
    }

    private AuthUserContext authenticatedUser(String username, String role) {
        return AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username(username)
                .displayName("Alice Example")
                .email("alice@example.com")
                .role(role)
                .accessScopes(List.of("role:1"))
                .build();
    }

    private UsernamePasswordAuthenticationToken authenticated(AuthUserContext user) {
        return new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(() -> "ROLE_" + user.getRole())
        );
    }
}
