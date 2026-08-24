package com.sdd.platform.web.exception;

import java.time.OffsetDateTime;

/**
 * Standard error envelope returned by {@link GlobalExceptionHandler}.
 *
 * Never includes stack trace or internal class names — clients see only
 * {@code error} (a short category code) and {@code message}. The {@code traceId}
 * lets the operator correlate with backend logs.
 */
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String traceId
) {}
