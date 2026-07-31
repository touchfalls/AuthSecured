package com.example.authsecured.application;

import com.example.authsecured.domain.session.Session;
import com.example.authsecured.ports.SessionRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SessionService {

    private final SessionRepository sessionRepository;
    private final boolean persistent;
    private final long timeoutMinutes;

    public SessionService(SessionRepository sessionRepository, boolean persistent, long timeoutMinutes) {
        this.sessionRepository = sessionRepository;
        this.persistent = persistent;
        this.timeoutMinutes = timeoutMinutes;
    }

    public CompletableFuture<Session> createSession(Long accountId, UUID playerUuid, String serverId) {
        Session session = Session.create(accountId, playerUuid, serverId, timeoutMinutes * 60L);
        return sessionRepository.save(session).thenApply(v -> session);
    }

    public CompletableFuture<Boolean> isValidActiveSession(UUID playerUuid) {
        if (!persistent) {
            return CompletableFuture.completedFuture(false);
        }
        return sessionRepository.findActiveByPlayerUuid(playerUuid)
                .thenApply(opt -> opt.map(s -> s.isValid(Instant.now())).orElse(false));
    }

    public CompletableFuture<Void> revokeSession(UUID sessionId) {
        return sessionRepository.revoke(sessionId);
    }

    public CompletableFuture<Void> revokeAllSessionsForAccount(Long accountId) {
        if (accountId == null) return CompletableFuture.completedFuture(null);
        return sessionRepository.revokeAllForAccount(accountId);
    }

    public CompletableFuture<Void> revokeActiveSessionForPlayer(UUID playerUuid) {
        return sessionRepository.findActiveByPlayerUuid(playerUuid)
                .thenCompose(optSession -> {
                    if (optSession.isPresent()) {
                        return sessionRepository.revoke(optSession.get().getId());
                    }
                    return CompletableFuture.completedFuture(null);
                });
    }

    public CompletableFuture<Void> cleanupExpiredSessions() {
        return sessionRepository.deleteExpired();
    }
}
