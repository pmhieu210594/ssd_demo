package com.sdd.platform.application.exception;

public class AccountTemporarilyUnavailableException extends RuntimeException {

    public AccountTemporarilyUnavailableException(String messageKey) {
        super(messageKey);
    }
}
