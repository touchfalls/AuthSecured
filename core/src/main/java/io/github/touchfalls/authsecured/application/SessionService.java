package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.domain.session.Session;
import io.github.touchfalls.authsecured.ports.SessionRepository;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class SessionService {

    private final SessionRepository sessionRepository;
    private final boolean enabled;
    private final boolean persistent;
    private final AtomicLong timeoutSeconds;
    private final boolean verifyIp;
    private volatile boolean extendWhileOnline;
    private final CompletableFuture<Void> initialization;
    private final Map<UUID, Session> volatileSessions = new ConcurrentHashMap<>();

    public SessionService(SessionRepository sessionRepository, boolean persistent, long timeoutMinutes) {
        this(sessionRepository, true, persistent, timeoutMinutes * 60L, false);
    }

    public SessionService(SessionRepository sessionRepository, boolean enabled, boolean persistent, long timeoutSeconds, boolean verifyIp) {
        this.sessionRepository = sessionRepository;
        this.enabled = enabled;
        this.persistent = persistent;
        this.timeoutSeconds = new AtomicLong(validateTimeout(timeoutSeconds));
        this.verifyIp = verifyIp;
        this.initialization = persistent
                ? CompletableFuture.completedFuture(null)
                : sessionRepository.deleteAll();
    }

    public CompletableFuture<Void> initialize() {
        return initialization;
    }

    /** @deprecated Supply an IP hash explicitly when IP verification is enabled. */
    @Deprecated
    public CompletableFuture<Session> createSession(Long accountId, UUID playerUuid, String serverId) {
        return createSession(accountId, playerUuid, null, serverId);
    }

    public CompletableFuture<Session> createSession(Long accountId, UUID playerUuid, byte[] ipHash,
                                                    String serverId) {
        Session session = Session.create(accountId, playerUuid, ipHash, serverId, timeoutSeconds.get());
        if (!enabled) {
            return CompletableFuture.completedFuture(session);
        }
        if (verifyIp && (ipHash == null || ipHash.length == 0)) {
            return CompletableFuture.completedFuture(session);
        }
        if (!persistent) {
            return initialization.thenApply(ignored -> {
                volatileSessions.put(playerUuid, session);
                return session;
            });
        }
        return sessionRepository.save(session).thenApply(v -> session);
    }

    public CompletableFuture<Boolean> isValidActiveSession(UUID playerUuid) {
        return isValidActiveSession(playerUuid, null);
    }

    public CompletableFuture<Boolean> isValidActiveSession(UUID playerUuid, byte[] playerIpHash) {
        if (!enabled) {
            return CompletableFuture.completedFuture(false);
        }
        if (!persistent) {
            return initialization.thenApply(ignored -> isValid(volatileSessions.get(playerUuid), playerIpHash));
        }
        return sessionRepository.findActiveByPlayerUuid(playerUuid)
                .thenApply(opt -> opt.map(session -> isValid(session, playerIpHash)).orElse(false));
    }

    public CompletableFuture<Void> revokeSession(UUID sessionId) {
        if (!persistent) {
            return initialization.thenRun(() ->
                    volatileSessions.entrySet().removeIf(entry -> entry.getValue().getId().equals(sessionId)));
        }
        return sessionRepository.revoke(sessionId);
    }

    public CompletableFuture<Void> revokeAllSessionsForAccount(Long accountId) {
        if (accountId == null) return CompletableFuture.completedFuture(null);
        if (!persistent) {
            return initialization.thenRun(() -> volatileSessions.entrySet()
                    .removeIf(entry -> accountId.equals(entry.getValue().getAccountId())));
        }
        return sessionRepository.revokeAllForAccount(accountId);
    }

    /**
     * Revokes every session for the account behind this player, not only the most recent
     * one: a player can have more than one active persistent session (for example from two
     * different IPs, or a reconnect that outlived an earlier one), and /logout must end all
     * of them, not leave an older session still valid.
     */
    public CompletableFuture<Void> revokeActiveSessionForPlayer(UUID playerUuid) {
        if (!persistent) {
            // The in-memory map holds at most one session per player, so there is nothing else to revoke.
            return initialization.thenRun(() -> volatileSessions.remove(playerUuid));
        }
        return sessionRepository.findActiveByPlayerUuid(playerUuid)
                .thenCompose(optSession -> optSession
                        .map(session -> revokeAllSessionsForAccount(session.getAccountId()))
                        .orElseGet(() -> CompletableFuture.completedFuture(null)));
    }

    /**
     * Keeps the sessions of players who are still online from expiring: their expiry moves to
     * now plus the session timeout, so the lifetime counts from when they leave instead of
     * from login. Only takes effect when {@code session.extend-while-online} is enabled.
     */
    public CompletableFuture<Void> extendActiveSessions(Collection<UUID> playerUuids) {
        if (!enabled || !extendWhileOnline || playerUuids == null || playerUuids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Instant expiresAt = Instant.now().plusSeconds(timeoutSeconds.get());
        if (!persistent) {
            return initialization.thenRun(() -> {
                Instant now = Instant.now();
                for (UUID playerUuid : playerUuids) {
                    volatileSessions.computeIfPresent(playerUuid, (uuid, session) ->
                            session.isValid(now) ? session.withExpiresAt(expiresAt) : session);
                }
            });
        }
        return sessionRepository.extendActive(List.copyOf(playerUuids), expiresAt);
    }

    public CompletableFuture<Void> cleanupExpiredSessions() {
        if (!persistent) {
            return initialization.thenRun(() -> {
                Instant now = Instant.now();
                volatileSessions.entrySet().removeIf(entry -> !entry.getValue().isValid(now));
            });
        }
        return sessionRepository.deleteExpired();
    }

    private boolean isValid(Session session, byte[] playerIpHash) {
        if (session == null || !session.isValid(Instant.now())) {
            return false;
        }
        if (!verifyIp) {
            return true;
        }
        byte[] storedIpHash = session.getTokenHash();
        return storedIpHash != null && playerIpHash != null
                && MessageDigest.isEqual(storedIpHash, playerIpHash);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isPersistent() { return persistent; }
    public long getTimeoutSeconds() { return timeoutSeconds.get(); }
    public void setTimeoutSeconds(long seconds) { timeoutSeconds.set(validateTimeout(seconds)); }
    public boolean isVerifyIp() { return verifyIp; }
    public boolean isExtendWhileOnline() { return extendWhileOnline; }
    public void setExtendWhileOnline(boolean extendWhileOnline) { this.extendWhileOnline = extendWhileOnline; }

    private static long validateTimeout(long seconds) {
        if (seconds < 1 || seconds > 2_592_000L) {
            throw new IllegalArgumentException("Session timeout must be between 1 and 2592000 seconds");
        }
        return seconds;
    }
}
