package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.application.exception.AccountTemporarilyUnavailableException;
import com.sdd.platform.application.exception.AuthenticationFailedException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.port.out.persistence.AuthTokenSessionRepositoryPort;
import com.sdd.platform.application.port.out.persistence.AuthUserAccountRepositoryPort;
import com.sdd.platform.config.AppProperties;
import com.sdd.platform.domain.model.AuthTokenSession;
import com.sdd.platform.domain.model.AuthUserAccount;
import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_MESSAGE_KEY = "auth.invalid_credentials";
    private static final String UNAVAILABLE_MESSAGE_KEY = "auth.account_temporarily_unavailable";
    private static final String NO_ROLE_ASSIGNED_MESSAGE_KEY = "auth.no_role_assigned";

    private final AuthUserAccountRepositoryPort accountRepository;
    private final AuthTokenSessionRepositoryPort tokenSessionRepository;
    private final AuthTokenService tokenService;
    private final AppProperties appProperties;
    private final AdminAuditLogService adminAuditLogService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            AuthUserAccountRepositoryPort accountRepository,
            AuthTokenSessionRepositoryPort tokenSessionRepository,
            AuthTokenService tokenService,
            AppProperties appProperties,
            AdminAuditLogService adminAuditLogService
    ) {
        this.accountRepository = accountRepository;
        this.tokenSessionRepository = tokenSessionRepository;
        this.tokenService = tokenService;
        this.appProperties = appProperties;
        this.adminAuditLogService = adminAuditLogService;
    }

    public AuthLoginResult login(String rawUsername, String rawPassword) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            adminAuditLogService.logLoginFailure(username, "Missing username or password");
            throw new AuthenticationFailedException(INVALID_MESSAGE_KEY);
        }

        AuthUserAccount account = accountRepository.findByUsername(username).orElse(null);
        if (account == null || !matches(account.getPasswordAlgo(), account.getPasswordHash(), rawPassword)) {
            if (account != null && !account.isActive()) {
                adminAuditLogService.logLoginFailure(username, "Account inactive");
                throw new AccountTemporarilyUnavailableException(UNAVAILABLE_MESSAGE_KEY);
            }
            adminAuditLogService.logLoginFailure(username, "Invalid credentials");
            throw new AuthenticationFailedException(INVALID_MESSAGE_KEY);
        }
        if (!account.isActive()) {
            adminAuditLogService.logLoginFailure(username, "Account inactive");
            throw new AccountTemporarilyUnavailableException(UNAVAILABLE_MESSAGE_KEY);
        }
        if (account.isRoleDeleted() || account.getRoleName() == null || account.getRoleName().isBlank()) {
            adminAuditLogService.logLoginFailure(username, "No role assigned");
            throw new ForbiddenException(NO_ROLE_ASSIGNED_MESSAGE_KEY);
        }

        OffsetDateTime now = OffsetDateTime.now();
        AuthUserContext context = toContext(account);
        int ttlSeconds = appProperties.jwt().expirationHours() * 60 * 60;
        String token = tokenService.issueToken(context, ttlSeconds);
        String refreshToken = tokenService.issueRefreshToken();
        tokenSessionRepository.revokeByUsername(context.getUsername(), now);
        tokenSessionRepository.save(AuthTokenSession.builder()
                .sessionId(UUID.randomUUID())
                .userAccountId(context.getUserAccountId())
                .username(context.getUsername())
                .accessTokenHash(tokenService.hashToken(token))
                .refreshTokenHash(tokenService.hashToken(refreshToken))
                .accessTokenExpiresAt(now.plusSeconds(ttlSeconds))
                .refreshTokenExpiresAt(now.plusDays(appProperties.auth().rememberMeTtlDays()))
                .createdAt(now)
                .updatedAt(now)
                .build());
        String roleName = account.getRoleName() == null ? "" : account.getRoleName().trim();
        String redirectTo =
                "ADMIN".equalsIgnoreCase(roleName) ? "/admin"
                        : "PM".equalsIgnoreCase(roleName) ? "/pm-dashboard"
                        : "/";
        adminAuditLogService.logLoginSuccess(context);
        return new AuthLoginResult(token, refreshToken, "Bearer", ttlSeconds, context, redirectTo);
    }

    public AuthUserContext currentUser(AuthUserContext context) {
        if (context == null) {
            throw new AuthenticationFailedException(INVALID_MESSAGE_KEY);
        }
        return context;
    }

    public void logout(AuthUserContext context) {
        if (context != null) {
            tokenSessionRepository.revokeByUsername(context.getUsername().trim(), OffsetDateTime.now());
            adminAuditLogService.logLogout(context);
        }
    }

    private boolean matches(String algo, String storedHash, String rawPassword) {
        if (storedHash == null || rawPassword == null) {
            return false;
        }
        if (algo == null || algo.isBlank() || "bcrypt".equalsIgnoreCase(algo)) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        return false;
    }

    private AuthUserContext toContext(AuthUserAccount account) {
        UUID userAccountId = account.getUserAccountId();
        if (userAccountId == null) {
            throw new IllegalStateException("Authenticated account is missing user_account_id");
        }
        return AuthUserContext.builder()
                .userAccountId(userAccountId)
                .username(account.getUsername())
                .displayName(account.getFullname())
                .email(account.getEmail())
                .role(account.getRoleName())
                .accessScopes(account.getAccessScopes() == null ? List.of() : account.getAccessScopes())
                .build();
    }
}
