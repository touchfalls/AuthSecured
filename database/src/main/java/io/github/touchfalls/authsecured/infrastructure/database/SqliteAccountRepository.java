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

public class SqliteAccountRepository implements AccountRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;
    private final Object registrationLock = new Object();

    public SqliteAccountRepository(DataSource dataSource, ExecutorService executor) {
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
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                stmt.setInt(12, account.isPasswordChangeRequiredFlag() ? 1 : 0);
                stmt.setString(13, account.getTemporaryPasswordExpiresAt() != null
                        ? account.getTemporaryPasswordExpiresAt().toString() : null);
                stmt.setBytes(14, registrationIpHash);

                synchronized (registrationLock) {
                    // Only this process writes the SQLite file, so the lock makes count and insert atomic.
                    checkIpLimit(conn, registrationIpHash, maxAccountsPerIp);
                    stmt.executeUpdate();
                }

                try (ResultSet rs = stmt.getGeneratedKeys()) {
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
                    + "password_change_required = 0, temporary_password_expires_at = NULL WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newPasswordHash);
                stmt.setString(2, now.toString());
                stmt.setString(3, now.toString());
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
                stmt.setString(2, now.toString());
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
                stmt.setString(1, now.toString());
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
            String sql = "UPDATE accounts SET password_hash = ?, password_change_required = 1, "
                    + "temporary_password_expires_at = ?, password_changed_at = ?, updated_at = ?, "
                    + "status = 'ACTIVE', locked_until = NULL, failed_login_attempts = 0 WHERE id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, passwordHash);
                stmt.setString(2, expiresAt.toString());
                stmt.setString(3, now.toString());
                stmt.setString(4, now.toString());
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
                String nowStr = now.toString();
                stmt.setString(1, nowStr);
                stmt.setString(2, nowStr);
                stmt.setInt(3, maxAttempts);
                stmt.setString(4, nowStr);
                stmt.setInt(5, maxAttempts);
                stmt.setString(6, now.plusSeconds(lockDurationSeconds).toString());
                stmt.setString(7, nowStr);
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
                stmt.setString(1, now.toString());
                stmt.setString(2, now.toString());
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
        boolean passwordChangeRequired = rs.getInt("password_change_required") != 0;
        String temporaryPasswordExpiresAtStr = rs.getString("temporary_password_expires_at");
        Instant temporaryPasswordExpiresAt = temporaryPasswordExpiresAtStr != null
                ? Instant.parse(temporaryPasswordExpiresAtStr) : null;

        return new Account(id, uuid, username, usernameNormalized, passwordHash, status,
                failedAttempts, lockedUntil, createdAt, updatedAt, lastLoginAt, passwordChangedAt,
                passwordChangeRequired, temporaryPasswordExpiresAt);
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
            if (current.getErrorCode() == 19 || "23000".equals(current.getSQLState())) return true;
            current = current.getNextException();
        }
        return false;
    }
}
