package com.sdd.platform.application.exception;

/**
 * Application-boundary conflict exception (e.g. duplicate active resource).
 *
 * The global handler maps {@link ApplicationException} generically to HTTP 409,
 * so this subclass exists purely to name the intent at the throw site.
 */
public class ConflictException extends ApplicationException {

    public ConflictException(String message) {
        super(message);
    }
}
