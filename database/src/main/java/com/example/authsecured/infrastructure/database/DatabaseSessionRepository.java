package com.example.authsecured.infrastructure.database;

import com.example.authsecured.domain.session.Session;
import com.example.authsecured.ports.SessionRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DatabaseSessionRepository implements SessionRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;
    private final boolean isPostgres;

    public DatabaseSessionRepository(DataSource dataSource, ExecutorService executor, boolean isPostgres) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.isPostgres = isPostgres;
    }

    @Override
    public CompletableFuture<Void> save(Session session) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO sessions (id, account_id, token_hash, server_id, created_at, expires_at, revoked_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isPostgres) {
                    stmt.setObject(1, session.getId());
                    stmt.setLong(2, session.getAccountId());
                    stmt.setBytes(3, session.getTokenHash());
                    stmt.setString(4, session.getServerId());
                    stmt.setTimestamp(5, Timestamp.from(session.getCreatedAt()));
                    stmt.setTimestamp(6, Timestamp.from(session.getExpiresAt()));
                    stmt.setTimestamp(7, session.getRevokedAt() != null ? Timestamp.from(session.getRevokedAt()) : null);
                } else {
                    stmt.setString(1, session.getId().toString());
                    stmt.setLong(2, session.getAccountId());
                    stmt.setBytes(3, session.getTokenHash());
                    stmt.setString(4, session.getServerId());
                    stmt.setString(5, session.getCreatedAt().toString());
                    stmt.setString(6, session.getExpiresAt().toString());
                    stmt.setString(7, session.getRevokedAt() != null ? session.getRevokedAt().toString() : null);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error saving session", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Session>> findById(UUID sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, account_id, token_hash, server_id, created_at, expires_at, revoked_at " +
                    "FROM sessions WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isPostgres) {
                    stmt.setObject(1, sessionId);
                } else {
                    stmt.setString(1, sessionId.toString());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToSession(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error finding session", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Session>> findActiveByPlayerUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = isPostgres ?
                    "SELECT s.id, s.account_id, a.uuid AS player_uuid, s.token_hash, s.server_id, s.created_at, s.expires_at, s.revoked_at " +
                            "FROM sessions s JOIN accounts a ON s.account_id = a.id " +
                            "WHERE a.uuid = ? AND s.revoked_at IS NULL AND s.expires_at > NOW() " +
                            "ORDER BY s.created_at DESC LIMIT 1" :
                    "SELECT s.id, s.account_id, a.uuid AS player_uuid, s.token_hash, s.server_id, s.created_at, s.expires_at, s.revoked_at " +
                            "FROM sessions s JOIN accounts a ON s.account_id = a.id " +
                            "WHERE a.uuid = ? AND s.revoked_at IS NULL AND s.expires_at > ? " +
                            "ORDER BY s.created_at DESC LIMIT 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isPostgres) {
                    stmt.setObject(1, playerUuid);
                } else {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, Instant.now().toString());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Session session = mapResultSetToSession(rs);
                        return Optional.of(session);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error finding active session", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Void> revoke(UUID sessionId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE sessions SET revoked_at = ? WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isPostgres) {
                    stmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    stmt.setObject(2, sessionId);
                } else {
                    stmt.setString(1, Instant.now().toString());
                    stmt.setString(2, sessionId.toString());
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error revoking session", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> revokeAllForAccount(Long accountId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE sessions SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (isPostgres) {
                    stmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    stmt.setLong(2, accountId);
                } else {
                    stmt.setString(1, Instant.now().toString());
                    stmt.setLong(2, accountId);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error revoking all sessions for account", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteExpired() {
        return CompletableFuture.runAsync(() -> {
            String sql = isPostgres ?
                    "DELETE FROM sessions WHERE expires_at < NOW() OR revoked_at IS NOT NULL" :
                    "DELETE FROM sessions WHERE expires_at < ? OR revoked_at IS NOT NULL";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (!isPostgres) {
                    stmt.setString(1, Instant.now().toString());
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error deleting expired sessions", e);
            }
        }, executor);
    }

    private Session mapResultSetToSession(ResultSet rs) throws SQLException {
        UUID id = isPostgres ? rs.getObject("id", UUID.class) : UUID.fromString(rs.getString("id"));
        Long accountId = rs.getLong("account_id");
        byte[] tokenHash = rs.getBytes("token_hash");
        String serverId = rs.getString("server_id");
        Instant createdAt = isPostgres ? rs.getTimestamp("created_at").toInstant() : Instant.parse(rs.getString("created_at"));
        Instant expiresAt = isPostgres ? rs.getTimestamp("expires_at").toInstant() : Instant.parse(rs.getString("expires_at"));
        Timestamp revokedTs = isPostgres ? rs.getTimestamp("revoked_at") : null;
        String revokedStr = !isPostgres ? rs.getString("revoked_at") : null;
        Instant revokedAt = isPostgres ? (revokedTs != null ? revokedTs.toInstant() : null) : (revokedStr != null ? Instant.parse(revokedStr) : null);

        UUID playerUuid;
        try {
            playerUuid = isPostgres ? rs.getObject("player_uuid", UUID.class) : UUID.fromString(rs.getString("player_uuid"));
        } catch (SQLException e) {
            playerUuid = UUID.randomUUID();
        }
        return new Session(id, accountId, playerUuid, tokenHash, serverId, createdAt, expiresAt, revokedAt);
    }
}
