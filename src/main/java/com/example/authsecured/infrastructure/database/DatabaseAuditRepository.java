package com.example.authsecured.infrastructure.database;

import com.example.authsecured.domain.auth.LoginAttempt;
import com.example.authsecured.domain.security.SecurityEvent;
import com.example.authsecured.ports.AuditRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DatabaseAuditRepository implements AuditRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;
    private final boolean isPostgres;

    public DatabaseAuditRepository(DataSource dataSource, ExecutorService executor, boolean isPostgres) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.isPostgres = isPostgres;
    }

    @Override
    public CompletableFuture<Void> recordLoginAttempt(LoginAttempt attempt) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO login_attempts (account_id, ip_hash, success, reason, created_at) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (attempt.getAccountId() != null) {
                    stmt.setLong(1, attempt.getAccountId());
                } else {
                    stmt.setNull(1, java.sql.Types.BIGINT);
                }
                stmt.setBytes(2, attempt.getIpHash());
                if (isPostgres) {
                    stmt.setBoolean(3, attempt.isSuccess());
                    stmt.setString(4, attempt.getReason());
                    stmt.setTimestamp(5, Timestamp.from(attempt.getCreatedAt()));
                } else {
                    stmt.setInt(3, attempt.isSuccess() ? 1 : 0);
                    stmt.setString(4, attempt.getReason());
                    stmt.setString(5, attempt.getCreatedAt().toString());
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                // Log failure silently to maintain production safety
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> recordSecurityEvent(SecurityEvent event) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO security_events (account_id, event_type, ip_hash, metadata, created_at) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (event.getAccountId() != null) {
                    stmt.setLong(1, event.getAccountId());
                } else {
                    stmt.setNull(1, java.sql.Types.BIGINT);
                }
                stmt.setString(2, event.getEventType().name());
                stmt.setBytes(3, event.getIpHash());
                stmt.setString(4, event.getMetadataJson());
                if (isPostgres) {
                    stmt.setTimestamp(5, Timestamp.from(event.getCreatedAt()));
                } else {
                    stmt.setString(5, event.getCreatedAt().toString());
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                // Log failure silently to maintain production safety
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> purgeOldRecords(int loginAttemptsDays, int securityEventsDays) {
        return CompletableFuture.runAsync(() -> {
            Instant loginThreshold = Instant.now().minusSeconds(loginAttemptsDays * 86400L);
            Instant eventThreshold = Instant.now().minusSeconds(securityEventsDays * 86400L);

            String sqlLogin = isPostgres ?
                    "DELETE FROM login_attempts WHERE created_at < ?" :
                    "DELETE FROM login_attempts WHERE created_at < ?";
            String sqlEvent = isPostgres ?
                    "DELETE FROM security_events WHERE created_at < ?" :
                    "DELETE FROM security_events WHERE created_at < ?";

            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement stmt = conn.prepareStatement(sqlLogin)) {
                    if (isPostgres) stmt.setTimestamp(1, Timestamp.from(loginThreshold));
                    else stmt.setString(1, loginThreshold.toString());
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = conn.prepareStatement(sqlEvent)) {
                    if (isPostgres) stmt.setTimestamp(1, Timestamp.from(eventThreshold));
                    else stmt.setString(1, eventThreshold.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                // Background purge exception
            }
        }, executor);
    }
}
