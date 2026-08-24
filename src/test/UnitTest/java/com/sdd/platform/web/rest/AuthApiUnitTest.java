package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.AuthLoginResult;
import com.sdd.platform.application.usecase.governance.AuthService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.Dtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthApiUnitTest {

    private AuthService authService;
    private AuthController authController;
    private MeController meController;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        authController = new AuthController(authService);
        meController = new MeController(authService);
    }

    @Test
    void login_delegates_to_service_and_maps_response() {
        AuthUserContext user = user("alice", "EDITOR");
        when(authService.login("alice", "secret")).thenReturn(new AuthLoginResult(
                "signed-token",
                "refresh-token",
                "Bearer",
                7200,
                user,
                "/"
        ));

        Dtos.AuthLoginResponse response = authController.login(new Dtos.AuthLoginRequest("alice", "secret"));

        verify(authService).login("alice", "secret");
        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(7200);
        assertThat(response.redirectTo()).isEqualTo("/");
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().email()).isEqualTo("alice@example.com");
    }

    @Test
    void logout_uses_authentication_principal_when_request_attribute_missing() {
        AuthUserContext user = user("alice", "EDITOR");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(() -> "ROLE_EDITOR")
        );

        authController.logout(null, authentication);

        verify(authService).logout(user);
    }

    @Test
    void me_uses_request_attribute_when_present() {
        AuthUserContext user = user("alice", "ADMIN");
        when(authService.currentUser(user)).thenReturn(user);

        Dtos.AuthCurrentUserDto response = meController.me(user, null);

        verify(authService).currentUser(user);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.role()).isEqualTo("ADMIN");
    }

    private AuthUserContext user(String username, String role) {
        return AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username(username)
                .displayName("Alice Example")
                .email("alice@example.com")
                .role(role)
                .accessScopes(List.of("role:1"))
                .build();
    }
}
