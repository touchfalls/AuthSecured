package io.github.touchfalls.authsecured.domain.auth;

public enum AuthResult {
    SUCCESS,
    /** Logged in, but from a different address than the previous login. */
    SUCCESS_NEW_ADDRESS,
    /** The password was right and the account has an authenticator; a code from it is still needed. */
    TWO_FACTOR_REQUIRED,
    INVALID_TWO_FACTOR_CODE,
    ACCOUNT_NOT_FOUND,
    INVALID_PASSWORD,
    RATE_LIMITED,
    ACCOUNT_LOCKED,
    ALREADY_AUTHENTICATED,
    NOT_REGISTERED,
    ALREADY_REGISTERED,
    PASSWORD_CHANGE_REQUIRED,
    TEMPORARY_PASSWORD_EXPIRED,
    PASSWORD_UNCHANGED,
    VALIDATION_FAILED,
    INTERNAL_ERROR
}
