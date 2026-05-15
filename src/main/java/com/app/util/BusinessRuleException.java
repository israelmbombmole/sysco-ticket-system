package com.app.util;

/** Expected business rule violation (user-facing message, no stack trace in logs). */
public final class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
