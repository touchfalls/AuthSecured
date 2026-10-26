package io.github.touchfalls.authsecured.ports;

/** Raised when an IP address already has the maximum number of registered accounts. */
public final class AccountLimitExceededException extends RuntimeException {
    public AccountLimitExceededException(String message) {
        super(message);
    }
}
