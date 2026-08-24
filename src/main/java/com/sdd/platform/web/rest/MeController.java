package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.AuthService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.Dtos;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping({"/me", "/auth/me"})
    public Dtos.AuthCurrentUserDto me(
            @RequestAttribute(value = "testUserContext", required = false) AuthUserContext testPrincipal,
            Authentication authentication
    ) {
        AuthUserContext principal = testPrincipal != null ? testPrincipal : resolvePrincipal(authentication);
        return Dtos.AuthCurrentUserDto.from(authService.currentUser(principal));
    }

    private AuthUserContext resolvePrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserContext user) {
            return user;
        }
        authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUserContext user) {
            return user;
        }
        return null;
    }
}
