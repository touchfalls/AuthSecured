package com.example.authsecured.domain.security;

public enum SecurityEventType {
    REGISTER_SUCCESS,
    REGISTER_FAILURE,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    PASSWORD_CHANGE,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    SESSION_REVOKED,
    RATE_LIMIT_EXCEEDED
}
