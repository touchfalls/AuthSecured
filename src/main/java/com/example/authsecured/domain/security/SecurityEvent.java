package com.example.authsecured.domain.security;

import java.time.Instant;

public class SecurityEvent {
    private final Long id;
    private final Long accountId;
    private final SecurityEventType eventType;
    private final byte[] ipHash;
    private final String metadataJson;
    private final Instant createdAt;

    public SecurityEvent(Long id, Long accountId, SecurityEventType eventType, byte[] ipHash, String metadataJson, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.eventType = eventType;
        this.ipHash = ipHash;
        this.metadataJson = metadataJson;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Long getId() { return id; }
    public Long getAccountId() { return accountId; }
    public SecurityEventType getEventType() { return eventType; }
    public byte[] getIpHash() { return ipHash; }
    public String getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
