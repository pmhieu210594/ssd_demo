package com.sdd.platform.domain.exception;

/**
 * Root of all domain-level exceptions.
 *
 * Subclasses describe a business rule violation or a missing aggregate.
 * Infrastructure exceptions (e.g., {@code WebClientResponseException},
 * {@code SQLException}) must be translated into a {@link DomainException}
 * subclass before they reach the application or web layer.
 *
 * Mapping to HTTP status codes is the sole responsibility of
 * {@code web/exception/GlobalExceptionHandler}.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
