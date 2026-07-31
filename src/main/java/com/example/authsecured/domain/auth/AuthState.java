package com.example.authsecured.domain.auth;

public enum AuthState {
    CONNECTED,
    AUTH_REQUIRED,
    AUTHENTICATED,
    KICKED,
    LOCKED
}
