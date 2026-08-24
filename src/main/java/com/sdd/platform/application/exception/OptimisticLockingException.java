package com.sdd.platform.application.exception;

/**
 * Optimistic-lock conflict for stale version tokens.
 */
public class OptimisticLockingException extends ApplicationException {

    public OptimisticLockingException(String message) {
        super(message);
    }
}
