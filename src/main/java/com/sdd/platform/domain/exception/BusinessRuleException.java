package com.sdd.platform.domain.exception;

/**
 * Generic domain rule violation.
 *
 * Use this for validation, duplicate, and read-only-state rules that should map
 * to HTTP 400 via the global exception handler.
 */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
