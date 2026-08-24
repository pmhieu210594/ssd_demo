package com.sdd.platform.web.rest;

import com.sdd.platform.application.usecase.governance.AuthService;
import com.sdd.platform.domain.model.AuthUserContext;
import com.sdd.platform.web.dto.Dtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Dtos.AuthLoginResponse login(@Valid @RequestBody Dtos.AuthLoginRequest request) {
        return Dtos.AuthLoginResponse.from(authService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestAttribute(value = "testUserContext", required = false) AuthUserContext testPrincipal,
            Authentication authentication
    ) {
        AuthUserContext principal = testPrincipal != null
                ? testPrincipal
                : (authentication != null && authentication.getPrincipal() instanceof AuthUserContext user ? user : null);
        authService.logout(principal);
    }
}
