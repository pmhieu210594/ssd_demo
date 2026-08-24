package com.sdd.platform.web.exception;

import com.sdd.platform.application.exception.AccountTemporarilyUnavailableException;
import com.sdd.platform.application.exception.ApplicationException;
import com.sdd.platform.application.exception.AuthenticationFailedException;
import com.sdd.platform.application.exception.ForbiddenException;
import com.sdd.platform.application.exception.OptimisticLockingException;
import com.sdd.platform.domain.exception.DomainException;
import com.sdd.platform.domain.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Arrays;

/**
 * The single place where exceptions become HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingException.class)
    public ResponseEntity<ErrorResponse> handleConflict(OptimisticLockingException ex) {
        return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex) {
        return error(HttpStatus.BAD_REQUEST, "DOMAIN_RULE_VIOLATION", ex.getMessage());
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ApplicationException ex) {
        return error(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(AccountTemporarilyUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAccountUnavailable(AccountTemporarilyUnavailableException ex) {
        return error(HttpStatus.UNAUTHORIZED, "ACCOUNT_TEMPORARILY_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        boolean unsafeInput = bindingResult != null && bindingResult.getAllErrors().stream()
                .flatMap(e -> Arrays.stream(e.getCodes() != null ? e.getCodes() : new String[0]))
                .anyMatch("NoXssFields"::equals);
        if (unsafeInput) {
            return error(HttpStatus.BAD_REQUEST, "UNSAFE_INPUT", "UNSAFE_INPUT");
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "VALIDATION_ERROR");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException ex) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessResourceFailure(DataAccessResourceFailureException ex) {
        log.error("Upstream resource unavailable", ex);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Component.Error.UpstreamUnavailable");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Component.Error.Unexpected");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                code,
                message,
                MDC.get("traceId") != null ? MDC.get("traceId") : ""
        );
        return ResponseEntity.status(status).body(body);
    }
}
