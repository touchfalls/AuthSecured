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

public class SqliteAccountRepository implements AccountRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;

    public SqliteAccountRepository(DataSource dataSource, ExecutorService executor) {
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
                stmt.setString(1, uuid.toString());
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
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, account.getUuid().toString());
                stmt.setString(2, account.getUsername());
                stmt.setString(3, account.getUsernameNormalized());
                stmt.setString(4, account.getPasswordHash());
                stmt.setString(5, account.getStatus().name());
                stmt.setInt(6, account.getFailedLoginAttempts());
                stmt.setString(7, account.getLockedUntil() != null ? account.getLockedUntil().toString() : null);
                stmt.setString(8, account.getCreatedAt().toString());
                stmt.setString(9, account.getUpdatedAt().toString());
                stmt.setString(10, account.getLastLoginAt() != null ? account.getLastLoginAt().toString() : null);
                stmt.setString(11, account.getPasswordChangedAt().toString());

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
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
                stmt.setString(5, account.getLockedUntil() != null ? account.getLockedUntil().toString() : null);
                stmt.setString(6, account.getUpdatedAt().toString());
                stmt.setString(7, account.getLastLoginAt() != null ? account.getLastLoginAt().toString() : null);
                stmt.setString(8, account.getPasswordChangedAt().toString());
                stmt.setString(9, account.getUuid().toString());

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
                stmt.setString(1, uuid.toString());
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
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String username = rs.getString("username");
        String usernameNormalized = rs.getString("username_normalized");
        String passwordHash = rs.getString("password_hash");
        AccountStatus status = AccountStatus.valueOf(rs.getString("status"));
        int failedAttempts = rs.getInt("failed_login_attempts");
        String lockedUntilStr = rs.getString("locked_until");
        Instant lockedUntil = lockedUntilStr != null ? Instant.parse(lockedUntilStr) : null;
        Instant createdAt = Instant.parse(rs.getString("created_at"));
        Instant updatedAt = Instant.parse(rs.getString("updated_at"));
        String lastLoginStr = rs.getString("last_login_at");
        Instant lastLoginAt = lastLoginStr != null ? Instant.parse(lastLoginStr) : null;
        Instant passwordChangedAt = Instant.parse(rs.getString("password_changed_at"));

        return new Account(id, uuid, username, usernameNormalized, passwordHash, status,
                failedAttempts, lockedUntil, createdAt, updatedAt, lastLoginAt, passwordChangedAt);
    }
}
