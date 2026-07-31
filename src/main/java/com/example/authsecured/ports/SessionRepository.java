package com.example.authsecured.ports;

import com.example.authsecured.domain.session.Session;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SessionRepository {
    CompletableFuture<Void> save(Session session);
    CompletableFuture<Optional<Session>> findById(UUID sessionId);
    CompletableFuture<Optional<Session>> findActiveByPlayerUuid(UUID playerUuid);
    CompletableFuture<Void> revoke(UUID sessionId);
    CompletableFuture<Void> revokeAllForAccount(Long accountId);
    CompletableFuture<Void> deleteExpired();
}
