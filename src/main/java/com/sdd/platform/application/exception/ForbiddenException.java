package com.sdd.platform.application.exception;

/**
 * Application-boundary access denied exception.
 *
 * Use this when a use case rejects an authenticated caller based on role or
 * permission checks. The global handler maps this to HTTP 403.
 */
public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String message) {
        super(message);
    }
}
