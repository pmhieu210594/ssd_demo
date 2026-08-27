package com.sdd.platform.web.exception;

import com.sdd.platform.application.exception.AccountTemporarilyUnavailableException;
import com.sdd.platform.application.exception.AuthenticationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void authentication_failures_return_machine_readable_code() {
        var response = handler.handleAuthenticationFailed(
                new AuthenticationFailedException("auth.invalid_credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_CREDENTIALS", response.getBody().error());
        assertEquals("auth.invalid_credentials", response.getBody().message());
    }

    @Test
    void temporarily_unavailable_accounts_return_machine_readable_code() {
        var response = handler.handleAccountUnavailable(
                new AccountTemporarilyUnavailableException("auth.account_temporarily_unavailable"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ACCOUNT_TEMPORARILY_UNAVAILABLE", response.getBody().error());
        assertEquals("auth.account_temporarily_unavailable", response.getBody().message());
    }

    @Test
    void validation_errors_return_validation_code() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);

        var response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().error());
        assertEquals("VALIDATION_ERROR", response.getBody().message());
    }
}
