package com.sdd.platform.application.usecase.governance;

import com.sdd.platform.domain.model.AuthUserContext;

public record AuthLoginResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresInSeconds,
        AuthUserContext user,
        String redirectTo
) {}
