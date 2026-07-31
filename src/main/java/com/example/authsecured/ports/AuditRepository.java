package com.example.authsecured.ports;

import com.example.authsecured.domain.auth.LoginAttempt;
import com.example.authsecured.domain.security.SecurityEvent;

import java.util.concurrent.CompletableFuture;

public interface AuditRepository {
    CompletableFuture<Void> recordLoginAttempt(LoginAttempt attempt);
    CompletableFuture<Void> recordSecurityEvent(SecurityEvent event);
    CompletableFuture<Void> purgeOldRecords(int loginAttemptsDays, int securityEventsDays);
}
