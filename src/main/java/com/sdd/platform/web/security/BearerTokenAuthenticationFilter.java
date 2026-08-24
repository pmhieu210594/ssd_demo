package com.sdd.platform.web.security;

import com.sdd.platform.application.port.out.persistence.AuthTokenSessionRepositoryPort;
import com.sdd.platform.application.usecase.governance.AuthTokenService;
import com.sdd.platform.domain.model.AuthUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService tokenService;
    private final AuthTokenSessionRepositoryPort tokenSessionRepository;

    public BearerTokenAuthenticationFilter(AuthTokenService tokenService,
                                           AuthTokenSessionRepositoryPort tokenSessionRepository) {
        this.tokenService = tokenService;
        this.tokenSessionRepository = tokenSessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            AuthUserContext context = tokenService.parseAndValidate(token);
            boolean activeSession = context != null
                    && tokenSessionRepository.findActiveByAccessTokenHash(
                    tokenService.hashToken(token),
                    OffsetDateTime.now()
            ).isPresent();
            if (activeSession) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        context,
                        null,
                        List.of(() -> "ROLE_" + context.getRole())
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
