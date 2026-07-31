package com.example.authsecured.application;

import com.example.authsecured.domain.auth.LoginAttempt;
import com.example.authsecured.domain.security.SecurityEvent;
import com.example.authsecured.domain.security.SecurityEventType;
import com.example.authsecured.ports.AuditRepository;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class SecurityService {

    private final AuditRepository auditRepository;
    private final boolean auditEnabled;

    public SecurityService(AuditRepository auditRepository, boolean auditEnabled) {
        this.auditRepository = auditRepository;
        this.auditEnabled = auditEnabled;
    }

    public CompletableFuture<Void> logLoginAttempt(Long accountId, byte[] ipHash, boolean success, String reason) {
        if (!auditEnabled) return CompletableFuture.completedFuture(null);
        LoginAttempt attempt = new LoginAttempt(null, accountId, ipHash, success, reason, Instant.now());
        return auditRepository.recordLoginAttempt(attempt);
    }

    public CompletableFuture<Void> logSecurityEvent(Long accountId, SecurityEventType eventType, byte[] ipHash, String metadataJson) {
        if (!auditEnabled) return CompletableFuture.completedFuture(null);
        SecurityEvent event = new SecurityEvent(null, accountId, eventType, ipHash, metadataJson, Instant.now());
        return auditRepository.recordSecurityEvent(event);
    }

    public CompletableFuture<Void> purgeAuditLogs(int loginAttemptsDays, int securityEventsDays) {
        return auditRepository.purgeOldRecords(loginAttemptsDays, securityEventsDays);
    }
}
