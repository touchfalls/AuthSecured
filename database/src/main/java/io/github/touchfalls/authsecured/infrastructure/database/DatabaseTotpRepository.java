package io.github.touchfalls.authsecured.infrastructure.database;

import io.github.touchfalls.authsecured.ports.TotpRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DatabaseTotpRepository implements TotpRepository {

    private final DataSource dataSource;
    private final ExecutorService executor;
    private final boolean isPostgres;

    public DatabaseTotpRepository(DataSource dataSource, ExecutorService executor, boolean isPostgres) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.isPostgres = isPostgres;
    }

    @Override
    public CompletableFuture<Optional<State>> find(Long accountId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT totp_secret, totp_enabled, totp_last_step, totp_backup_codes "
                                 + "FROM accounts WHERE id = ?")) {
                stmt.setLong(1, accountId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next() || rs.getBytes(1) == null) return Optional.<State>empty();
                    return Optional.of(new State(rs.getBytes(1), rs.getBoolean(2), rs.getLong(3),
                            split(rs.getString(4))));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error reading the authenticator state", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePending(Long accountId, byte[] encryptedSecret) {
        return CompletableFuture.runAsync(() -> update(
                "UPDATE accounts SET totp_secret = ?, totp_enabled = ?, totp_last_step = 0, totp_backup_codes = NULL "
                        + "WHERE id = ?", encryptedSecret, false, accountId), executor);
    }

    @Override
    public CompletableFuture<Boolean> enable(Long accountId, long usedStep, List<String> backupCodeHashes) {
        return CompletableFuture.supplyAsync(() -> update(
                "UPDATE accounts SET totp_enabled = ?, totp_last_step = ?, totp_backup_codes = ? "
                        + "WHERE id = ? AND totp_secret IS NOT NULL AND totp_enabled = ?",
                true, usedStep, String.join(",", backupCodeHashes), accountId, false) > 0, executor);
    }

    @Override
    public CompletableFuture<Boolean> useStep(Long accountId, long step) {
        return CompletableFuture.supplyAsync(() -> update(
                "UPDATE accounts SET totp_last_step = ? WHERE id = ? AND totp_last_step < ?",
                step, accountId, step) > 0, executor);
    }

    @Override
    public CompletableFuture<Boolean> consumeBackupCode(Long accountId, String hash) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                List<String> codes;
                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT totp_backup_codes FROM accounts WHERE id = ?" + (isPostgres ? " FOR UPDATE" : ""))) {
                    select.setLong(1, accountId);
                    try (ResultSet rs = select.executeQuery()) {
                        codes = rs.next() ? split(rs.getString(1)) : List.of();
                    }
                }
                if (!codes.contains(hash)) return false;
                List<String> remaining = new ArrayList<>(codes);
                remaining.remove(hash);
                try (PreparedStatement update = conn.prepareStatement(
                        "UPDATE accounts SET totp_backup_codes = ? WHERE id = ?")) {
                    update.setString(1, String.join(",", remaining));
                    update.setLong(2, accountId);
                    update.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                throw new RuntimeException("Error using a backup code", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> disable(Long accountId) {
        return CompletableFuture.runAsync(() -> update(
                "UPDATE accounts SET totp_secret = NULL, totp_enabled = ?, totp_last_step = 0, "
                        + "totp_backup_codes = NULL WHERE id = ?", false, accountId), executor);
    }

    private static List<String> split(String stored) {
        return stored == null || stored.isEmpty() ? List.of() : Arrays.asList(stored.split(","));
    }

    /** Binds values in order: byte[], boolean, long, int and String are all that is needed here. */
    private int update(String sql, Object... values) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                int index = i + 1;
                if (value == null) stmt.setObject(index, null);
                else if (value instanceof byte[] bytes) stmt.setBytes(index, bytes);
                else if (value instanceof Boolean flag) stmt.setBoolean(index, flag);
                else if (value instanceof Long number) stmt.setLong(index, number);
                else stmt.setString(index, value.toString());
            }
            return stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating the authenticator state", e);
        }
    }
}
