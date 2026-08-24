package com.sdd.platform.application.exception;

/**
 * Root of application-level (use case orchestration) exceptions.
 *
 * Use for failures that are NOT business-rule violations of the domain,
 * e.g. access denied at the use-case boundary, optimistic concurrency,
 * downstream-systems-degraded. Pure domain rule violations belong in
 * {@code com.sdd.platform.domain.exception}.
 */
public abstract class ApplicationException extends RuntimeException {

    protected ApplicationException(String message) {
        super(message);
    }

    protected ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
