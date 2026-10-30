package io.github.touchfalls.authsecured.ports;

import io.github.touchfalls.authsecured.domain.session.Session;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SessionRepository {
    CompletableFuture<Void> save(Session session);
    CompletableFuture<Optional<Session>> findById(UUID sessionId);
    CompletableFuture<Optional<Session>> findActiveByPlayerUuid(UUID playerUuid);
    /** Number of still-valid sessions of the account behind this player, across all servers. */
    CompletableFuture<Integer> countActiveByPlayerUuid(UUID playerUuid);
    CompletableFuture<Void> revoke(UUID sessionId);
    CompletableFuture<Void> revokeAllForAccount(Long accountId);

    /**
     * Moves the expiry of every still-valid session of these players' accounts to
     * {@code expiresAt}. Revoked and already expired sessions stay ended.
     */
    CompletableFuture<Void> extendActive(Collection<UUID> playerUuids, Instant expiresAt);
    CompletableFuture<Void> deleteExpired();
    CompletableFuture<Void> deleteAll();
}
