package com.example.authsecured.domain.auth;

import java.time.Instant;

public class LoginAttempt {
    private final Long id;
    private final Long accountId;
    private final byte[] ipHash;
    private final boolean success;
    private final String reason;
    private final Instant createdAt;

    public LoginAttempt(Long id, Long accountId, byte[] ipHash, boolean success, String reason, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.ipHash = ipHash;
        this.success = success;
        this.reason = reason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public byte[] getIpHash() { return ipHash; }
    public boolean isSuccess() { return success; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
