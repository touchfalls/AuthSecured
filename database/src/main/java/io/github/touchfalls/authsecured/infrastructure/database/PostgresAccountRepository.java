package io.github.touchfalls.authsecured.infrastructure.database;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.account.AccountStatus;
import io.github.touchfalls.authsecured.ports.AccountLimitExceededException;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.DuplicateAccountException;

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
                    "password_changed_at, password_change_required, temporary_password_expires_at FROM accounts WHERE uuid = ?";
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
                    "password_changed_at, password_change_required, temporary_password_expires_at FROM accounts WHERE username_normalized = ?";
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
    public CompletableFuture<Account> save(Account account, byte[] registrationIpHash, int maxAccountsPerIp) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO accounts (uuid, username, username_normalized, password_hash, status, " +
                    "failed_login_attempts, locked_until, created_at, updated_at, last_login_at, password_changed_at, " +
                    "password_change_required, temporary_password_expires_at, registration_ip_hash) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
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
                stmt.setBoolean(12, account.isPasswordChangeRequiredFlag());
                stmt.setTimestamp(13, account.getTemporaryPasswordExpiresAt() != null
                        ? Timestamp.from(account.getTemporaryPasswordExpiresAt()) : null);
                stmt.setBytes(14, registrationIpHash);

                // Every server sharing this database takes the same per-IP lock, held until commit.
                // On an exception HikariCP rolls back and restores auto-commit when the connection returns.
                conn.setAutoCommit(false);
                lockIpHash(conn, registrationIpHash);
                checkIpLimit(conn, registrationIpHash, maxAccountsPerIp);
                try (ResultSet rs = stmt.executeQuery()) {
                    conn.commit();
                    if (rs.next()) {
                        long generatedId = rs.getLong(1);
                        return new Account(generatedId, account.getUuid(), account.getUsername(),
                                account.getUsernameNormalized(), account.getPasswordHash(),
                                account.getStatus(), account.getFailedLoginAttempts(),
                                account.getLockedUntil(), account.getCreatedAt(),
                                account.getUpdatedAt(), account.getLastLoginAt(),
                                account.getPasswordChangedAt(), account.isPasswordChangeRequiredFlag(),
                                account.getTemporaryPasswordExpiresAt());
                    }
                }
            } catch (SQLException e) {
                if (isUniqueConstraintViolation(e)) {
                    throw new DuplicateAccountException("Account already exists", e);
                }
                throw new RuntimeException("Error saving account", e);
            }
            return account;
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updatePasswordHash(Long accountId, String newPasswordHash, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE accounts SET password_hash = ?, password_changed_at = ?, updated_at = ?, "
                    + "password_change_required = FALSE, temporary_password_expires_at = NULL WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                Timestamp nowTs = Timestamp.from(now);
                stmt.setString(1, newPasswordHash);
                stmt.setTimestamp(2, nowTs);
                stmt.setTimestamp(3, nowTs);
                stmt.setLong(4, accountId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error updating password hash", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> replacePasswordHash(Long accountId, String expectedHash, String newPasswordHash,
                                                          Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE accounts SET password_hash = ?, updated_at = ? WHERE id = ? AND password_hash = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newPasswordHash);
                stmt.setTimestamp(2, Timestamp.from(now));
                stmt.setLong(3, accountId);
                stmt.setString(4, expectedHash);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error replacing password hash", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> unlock(Long accountId, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE accounts SET status = 'ACTIVE', locked_until = NULL, "
                    + "failed_login_attempts = 0, updated_at = ? WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, Timestamp.from(now));
                stmt.setLong(2, accountId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error unlocking account", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> setTemporaryPassword(Long accountId, String passwordHash, Instant expiresAt, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE accounts SET password_hash = ?, password_change_required = TRUE, "
                    + "temporary_password_expires_at = ?, password_changed_at = ?, updated_at = ?, "
                    + "status = 'ACTIVE', locked_until = NULL, failed_login_attempts = 0 WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                Timestamp nowTs = Timestamp.from(now);
                stmt.setString(1, passwordHash);
                stmt.setTimestamp(2, Timestamp.from(expiresAt));
                stmt.setTimestamp(3, nowTs);
                stmt.setTimestamp(4, nowTs);
                stmt.setLong(5, accountId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error setting temporary password", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> incrementFailedAttemptsAtomic(Long accountId, int maxAttempts,
                                                                     long lockDurationSeconds, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            // An expired lock resets the counter to 1 instead of adding to the stale count:
            // otherwise one more failure after the lock expires immediately re-locks the account.
            String resetIfLockExpired = "(CASE WHEN status = 'LOCKED' AND locked_until <= ? "
                    + "THEN 1 ELSE failed_login_attempts + 1 END)";
            String sql = "UPDATE accounts SET "
                    + "failed_login_attempts = " + resetIfLockExpired + ", "
                    + "status = CASE WHEN status = 'SUSPENDED' THEN status "
                    + "WHEN " + resetIfLockExpired + " >= ? THEN 'LOCKED' "
                    + "WHEN status = 'LOCKED' THEN 'ACTIVE' ELSE status END, "
                    + "locked_until = CASE WHEN status = 'SUSPENDED' THEN locked_until "
                    + "WHEN " + resetIfLockExpired + " >= ? THEN ? ELSE NULL END, "
                    + "updated_at = ? WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                Timestamp nowTs = Timestamp.from(now);
                stmt.setTimestamp(1, nowTs);
                stmt.setTimestamp(2, nowTs);
                stmt.setInt(3, maxAttempts);
                stmt.setTimestamp(4, nowTs);
                stmt.setInt(5, maxAttempts);
                stmt.setTimestamp(6, Timestamp.from(now.plusSeconds(lockDurationSeconds)));
                stmt.setTimestamp(7, nowTs);
                stmt.setLong(8, accountId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error atomically incrementing failed attempts", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> resetFailedAttemptsAtomic(Long accountId, Instant now) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE accounts SET failed_login_attempts = 0, locked_until = NULL, "
                    + "status = CASE WHEN status = 'LOCKED' THEN 'ACTIVE' ELSE status END, "
                    + "last_login_at = ?, updated_at = ? WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, Timestamp.from(now));
                stmt.setTimestamp(2, Timestamp.from(now));
                stmt.setLong(3, accountId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Error atomically resetting failed attempts", e);
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
            // Counts the accounts table directly so the cap survives disabled audit
            // logging and audit retention, instead of depending on security_events rows.
            try (Connection conn = dataSource.getConnection()) {
                return countByIpHash(conn, ipHash);
            } catch (SQLException e) {
                // Fails open: save() repeats this check atomically and is the real guard.
                return 0L;
            }
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
        boolean passwordChangeRequired = rs.getBoolean("password_change_required");
        Timestamp temporaryPasswordExpiresAtTs = rs.getTimestamp("temporary_password_expires_at");
        Instant temporaryPasswordExpiresAt = temporaryPasswordExpiresAtTs != null
                ? temporaryPasswordExpiresAtTs.toInstant() : null;

        return new Account(id, uuid, username, usernameNormalized, passwordHash, status,
                failedAttempts, lockedUntil, createdAt, updatedAt, lastLoginAt, passwordChangedAt,
                passwordChangeRequired, temporaryPasswordExpiresAt);
    }

    private static void lockIpHash(Connection conn, byte[] ipHash) throws SQLException {
        if (ipHash == null || ipHash.length == 0) return;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            stmt.setLong(1, java.nio.ByteBuffer.wrap(java.util.Arrays.copyOf(ipHash, Long.BYTES)).getLong());
            stmt.executeQuery().close();
        }
    }

    private static long countByIpHash(Connection conn, byte[] ipHash) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM accounts WHERE registration_ip_hash = ?")) {
            stmt.setBytes(1, ipHash);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static void checkIpLimit(Connection conn, byte[] ipHash, int maxAccountsPerIp) throws SQLException {
        if (ipHash != null && ipHash.length > 0 && countByIpHash(conn, ipHash) >= maxAccountsPerIp) {
            throw new AccountLimitExceededException("Registration IP already has the maximum number of accounts");
        }
    }

    private static boolean isUniqueConstraintViolation(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if ("23505".equals(current.getSQLState())) return true;
            current = current.getNextException();
        }
        return false;
    }
}
