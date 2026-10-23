package io.github.touchfalls.authsecured.domain.session;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class Session {
    private final UUID id;
    private final Long accountId;
    private final UUID playerUuid;
    private final byte[] tokenHash;
    private final String serverId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant revokedAt;

    public Session(UUID id, Long accountId, UUID playerUuid, byte[] tokenHash, String serverId,
                   Instant createdAt, Instant expiresAt, Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "Session ID cannot be null");
        this.accountId = accountId;
        this.playerUuid = Objects.requireNonNull(playerUuid, "Player UUID cannot be null");
        this.tokenHash = tokenHash != null ? Arrays.copyOf(tokenHash, tokenHash.length) : null;
        this.serverId = serverId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = Objects.requireNonNull(expiresAt, "ExpiresAt cannot be null");
        this.revokedAt = revokedAt;
    }

    public static Session create(Long accountId, UUID playerUuid, String serverId, long durationSeconds) {
        return create(accountId, playerUuid, null, serverId, durationSeconds);
    }

    public static Session create(Long accountId, UUID playerUuid, byte[] ipHash,
                                 String serverId, long durationSeconds) {
        Instant now = Instant.now();
        return new Session(
                UUID.randomUUID(),
                accountId,
                playerUuid,
                ipHash,
                serverId,
                now,
                now.plusSeconds(durationSeconds),
                null
        );
    }

    public boolean isValid(Instant currentTime) {
        if (revokedAt != null) return false;
        return currentTime.isBefore(expiresAt);
    }

    /** Copy of this session with a different expiry. */
    public Session withExpiresAt(Instant newExpiresAt) {
        return new Session(id, accountId, playerUuid, tokenHash, serverId, createdAt, newExpiresAt, revokedAt);
    }

    public void revoke(Instant currentTime) {
        this.revokedAt = currentTime;
    }

    public UUID getId() { return id; }
    public Long getAccountId() { return accountId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public byte[] getTokenHash() {
        return tokenHash != null ? Arrays.copyOf(tokenHash, tokenHash.length) : null;
    }
    public String getServerId() { return serverId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
