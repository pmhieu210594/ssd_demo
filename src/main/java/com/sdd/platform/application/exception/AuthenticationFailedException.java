package com.sdd.platform.application.exception;

public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String messageKey) {
        super(messageKey);
    }
}
