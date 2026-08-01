package com.example.authsecured.infrastructure.database;

import com.example.authsecured.domain.account.Account;
import com.example.authsecured.domain.account.AccountStatus;
import com.example.authsecured.ports.AccountRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class PostgresAccountRepository implements AccountRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;

    public PostgresAccountRepository(DataSource dataSource, ExecutorService executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<Account>> findByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, uuid, username, username_normalized, password_hash, status, " +
                    "failed_login_attempts, locked_until, created_at, updated_at, last_login_at, " +
                    "password_changed_at FROM accounts WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, uuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToAccount(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error finding account by UUID", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<Account>> findByUsernameNormalized(String usernameNormalized) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, uuid, username, username_normalized, password_hash, status, " +
                    "failed_login_attempts, locked_until, created_at, updated_at, last_login_at, " +
                    "password_changed_at FROM accounts WHERE username_normalized = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, usernameNormalized);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToAccount(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error finding account by normalized username", e);
            }
            return Optional.empty();
        }, executor);
    }

    @Override
    public CompletableFuture<Account> save(Account account) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO accounts (uuid, username, username_normalized, password_hash, status, " +
                    "failed_login_attempts, locked_until, created_at, updated_at, last_login_at, password_changed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, account.getUuid());
                stmt.setString(2, account.getUsername());
                stmt.setString(3, account.getUsernameNormalized());
                stmt.setString(4, account.getPasswordHash());
                stmt.setString(5, account.getStatus().name());
                stmt.setInt(6, account.getFailedLoginAttempts());
                stmt.setTimestamp(7, account.getLockedUntil() != null ? Timestamp.from(account.getLockedUntil()) : null);
                stmt.setTimestamp(8, Timestamp.from(account.getCreatedAt()));
                stmt.setTimestamp(9, Timestamp.from(account.getUpdatedAt()));
                stmt.setTimestamp(10, account.getLastLoginAt() != null ? Timestamp.from(account.getLastLoginAt()) : null);
                stmt.setTimestamp(11, Timestamp.from(account.getPasswordChangedAt()));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long generatedId = rs.getLong(1);
                        return new Account(generatedId, account.getUuid(), account.getUsername(),
                                account.getUsernameNormalized(), account.getPasswordHash(),
                                account.getStatus(), account.getFailedLoginAttempts(),
                                account.getLockedUntil(), account.getCreatedAt(),
                                account.getUpdatedAt(), account.getLastLoginAt(),
                                account.getPasswordChangedAt());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error saving account", e);
            }
            return account;
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> update(Account account) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE accounts SET username = ?, password_hash = ?, status = ?, " +
                    "failed_login_attempts = ?, locked_until = ?, updated_at = ?, last_login_at = ?, " +
                    "password_changed_at = ? WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, account.getUsername());
                stmt.setString(2, account.getPasswordHash());
                stmt.setString(3, account.getStatus().name());
                stmt.setInt(4, account.getFailedLoginAttempts());
                stmt.setTimestamp(5, account.getLockedUntil() != null ? Timestamp.from(account.getLockedUntil()) : null);
                stmt.setTimestamp(6, Timestamp.from(account.getUpdatedAt()));
                stmt.setTimestamp(7, account.getLastLoginAt() != null ? Timestamp.from(account.getLastLoginAt()) : null);
                stmt.setTimestamp(8, Timestamp.from(account.getPasswordChangedAt()));
                stmt.setObject(9, account.getUuid());

                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error updating account", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> delete(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM accounts WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, uuid);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error deleting account", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> countAccountsByIpHash(byte[] ipHash) {
        if (ipHash == null || ipHash.length == 0) {
            return CompletableFuture.completedFuture(0L);
        }
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(DISTINCT account_id) FROM security_events WHERE ip_hash = ? AND event_type = 'REGISTER_SUCCESS'";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBytes(1, ipHash);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            } catch (SQLException e) {
                return 0L;
            }
            return 0L;
        }, executor);
    }

    private Account mapResultSetToAccount(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        UUID uuid = rs.getObject("uuid", UUID.class);
        String username = rs.getString("username");
        String usernameNormalized = rs.getString("username_normalized");
        String passwordHash = rs.getString("password_hash");
        AccountStatus status = AccountStatus.valueOf(rs.getString("status"));
        int failedAttempts = rs.getInt("failed_login_attempts");
        Timestamp lockedUntilTs = rs.getTimestamp("locked_until");
        Instant lockedUntil = lockedUntilTs != null ? lockedUntilTs.toInstant() : null;
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        Timestamp lastLoginTs = rs.getTimestamp("last_login_at");
        Instant lastLoginAt = lastLoginTs != null ? lastLoginTs.toInstant() : null;
        Instant passwordChangedAt = rs.getTimestamp("password_changed_at").toInstant();

        return new Account(id, uuid, username, usernameNormalized, passwordHash, status,
                failedAttempts, lockedUntil, createdAt, updatedAt, lastLoginAt, passwordChangedAt);
    }
}
