package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.AccountTemporarilyUnavailableException;
import com.sdd.platform.application.exception.AuthenticationFailedException;
import com.sdd.platform.application.port.out.persistence.AuthTokenSessionRepositoryPort;
import com.sdd.platform.application.port.out.persistence.AuthUserAccountRepositoryPort;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.model.AuthUserAccount;
import com.sdd.platform.domain.model.AuthUserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthUserAccountRepositoryPort accountRepository;
    private AuthTokenSessionRepositoryPort tokenSessionRepository;
    private AuthTokenService tokenService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        accountRepository = Mockito.mock(AuthUserAccountRepositoryPort.class);
        tokenSessionRepository = Mockito.mock(AuthTokenSessionRepositoryPort.class);
        tokenService = Mockito.mock(AuthTokenService.class);

        AppProperties props = new AppProperties(
                null,
                null,
                new AppProperties.Jwt("test-secret", 2),
                new AppProperties.Auth(false, false, 0),
                null
        );

        service = new AuthService(accountRepository, tokenSessionRepository, tokenService, props);
    }

    @Test
    void login_issues_token_and_persists_session() {
        AuthUserAccount account = userAccount("alice", true, "ADMIN", "alice@example.com");
        AuthUserContext expectedContext = AuthUserContext.builder()
                .userAccountId(account.getUserAccountId())
                .username("alice")
                .displayName("Alice Example")
                .email("alice@example.com")
                .role("ADMIN")
                .accessScopes(List.of("role:1"))
                .build();

        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(tokenService.issueToken(any(AuthUserContext.class), Mockito.eq(7200))).thenReturn("signed-token");
        when(tokenService.issueRefreshToken()).thenReturn("refresh-token");
        when(tokenService.hashToken("signed-token")).thenReturn("access-token-hash");
        when(tokenService.hashToken("refresh-token")).thenReturn("refresh-token-hash");

        var result = service.login(" alice ", "secret");

        assertEquals("signed-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(7200, result.expiresInSeconds());
        assertEquals("/admin", result.redirectTo());
        assertEquals(expectedContext.getUsername(), result.user().getUsername());
        assertEquals(expectedContext.getEmail(), result.user().getEmail());
        assertEquals(expectedContext.getRole(), result.user().getRole());
        assertNotNull(result.user().getUserAccountId());

        verify(tokenService).issueToken(any(AuthUserContext.class), Mockito.eq(7200));
        verify(tokenSessionRepository).revokeByUsername(Mockito.eq("alice"), any());
        verify(tokenSessionRepository).save(argThat(session ->
                "alice".equals(session.getUsername())
                        && "access-token-hash".equals(session.getAccessTokenHash())
                        && "refresh-token-hash".equals(session.getRefreshTokenHash())
        ));
    }

    @Test
    void login_rejects_invalid_credentials() {
        AuthUserAccount account = userAccount("alice", true, "EDITOR", "alice@example.com");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(account));

        AuthenticationFailedException ex = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login("alice", "wrong-password")
        );

        assertEquals("auth.invalid_credentials", ex.getMessage());
        verifyNoInteractions(tokenService);
    }

    @Test
    void login_rejects_inactive_accounts() {
        AuthUserAccount account = userAccount("alice", false, "EDITOR", "alice@example.com");
        when(accountRepository.findByUsername("alice")).thenReturn(Optional.of(account));

        AccountTemporarilyUnavailableException ex = assertThrows(
                AccountTemporarilyUnavailableException.class,
                () -> service.login("alice", "secret")
        );

        assertEquals("auth.account_temporarily_unavailable", ex.getMessage());
        verifyNoInteractions(tokenService);
    }

    @Test
    void logout_revokes_token_session_when_principal_exists() {
        AuthUserContext user = AuthUserContext.builder()
                .userAccountId(UUID.randomUUID())
                .username("alice")
                .displayName("Alice Example")
                .email("alice@example.com")
                .role("ADMIN")
                .accessScopes(List.of("role:1"))
                .build();

        service.logout(user);

        verify(tokenSessionRepository).revokeByUsername(Mockito.eq("alice"), any());
        verifyNoInteractions(tokenService);
    }

    private AuthUserAccount userAccount(String username, boolean active, String role, String email) {
        return AuthUserAccount.builder()
                .userAccountId(UUID.randomUUID())
                .memberKey(UUID.randomUUID())
                .username(username)
                .fullname("Alice Example")
                .email(email)
                .passwordAlgo("bcrypt")
                .passwordHash(passwordEncoder.encode("secret"))
                .active(active)
                .roleName(role)
                .accessScopes(List.of("role:1"))
                .build();
    }
}
