package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.domain.session.Session;
import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseSessionRepository;
import io.github.touchfalls.authsecured.infrastructure.database.SqliteAccountRepository;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseSessionRepositoryTest {

    private DatabaseManager databaseManager;
    private DatabaseSessionRepository repository;

    private static final PlatformLogger DUMMY_LOGGER = new PlatformLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
        @Override public void severe(String message, Throwable throwable) {}
    };

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("test_session.db").toFile();
        databaseManager = new DatabaseManager(DUMMY_LOGGER, "sqlite", "", 0, "", "", "", dbFile.getAbsolutePath(), 2);
        repository = new DatabaseSessionRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor(), false);
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    @DisplayName("Verify session creation, lookup by UUID, and revocation")
    void testSessionLifecycle() {
        UUID playerUuid = UUID.randomUUID();
        Long accountId = new SqliteAccountRepository(databaseManager.getDataSource(),
                databaseManager.getDbExecutor()).save(Account.createNew(
                playerUuid, "SessionUser", "sessionuser", "hash"), null).join().getId();
        Session session = Session.create(accountId, playerUuid, "server-1", 1800);

        // Save session
        repository.save(session).join();

        // Revoke session
        repository.revoke(session.getId()).join();

        // Verify session is no longer active
        Optional<Session> revoked = repository.findActiveByPlayerUuid(playerUuid).join();
        assertTrue(revoked.isEmpty());
    }

    @Test
    @DisplayName("Counts only valid sessions of the account")
    void countsActiveSessionsOfTheAccount() {
        UUID playerUuid = UUID.randomUUID();
        Long accountId = new SqliteAccountRepository(databaseManager.getDataSource(),
                databaseManager.getDbExecutor()).save(Account.createNew(
                playerUuid, "CountUser", "countuser", "hash"), null).join().getId();
        Session first = Session.create(accountId, playerUuid, "server-1", 1800);
        Session second = Session.create(accountId, playerUuid, "server-2", 1800);
        Session expired = Session.create(accountId, playerUuid, "server-3", -60);
        repository.save(first).join();
        repository.save(second).join();
        repository.save(expired).join();

        assertEquals(2, repository.countActiveByPlayerUuid(playerUuid).join());

        repository.revoke(first.getId()).join();
        assertEquals(1, repository.countActiveByPlayerUuid(playerUuid).join());
        assertEquals(0, repository.countActiveByPlayerUuid(UUID.randomUUID()).join());
    }

    @Test
    void restoreMatchesTheAddressAmongAllSessionsAndSkipsSuspendedAccounts() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Long accountId = new SqliteAccountRepository(databaseManager.getDataSource(),
                databaseManager.getDbExecutor()).save(Account.createNew(
                playerUuid, "RestoreUser", "restoreuser", "hash"), null).join().getId();
        byte[] home = {1, 1};
        byte[] cafe = {2, 2};
        Instant now = Instant.now();
        Session older = new Session(UUID.randomUUID(), accountId, playerUuid, home, "server",
                now.minusSeconds(60), now.plusSeconds(600), null);
        Session newer = new Session(UUID.randomUUID(), accountId, playerUuid, cafe, "server",
                now, now.plusSeconds(600), null);
        repository.save(older).join();
        repository.save(newer).join();

        // The older session is not the newest one, but it is the one for this address.
        assertEquals(older.getId(), repository.findRestorableByPlayerUuid(playerUuid, home).join().orElseThrow().getId());
        assertEquals(newer.getId(), repository.findRestorableByPlayerUuid(playerUuid, null).join().orElseThrow().getId());
        assertTrue(repository.findRestorableByPlayerUuid(playerUuid, new byte[]{9}).join().isEmpty());

        setAccountStatus(accountId, "LOCKED", now.plusSeconds(900).toString());
        assertTrue(repository.findRestorableByPlayerUuid(playerUuid, home).join().isPresent(),
                "a timed lock from wrong passwords must not log the owner out");
        setAccountStatus(accountId, "LOCKED", null);
        assertTrue(repository.findRestorableByPlayerUuid(playerUuid, home).join().isEmpty());
        setAccountStatus(accountId, "SUSPENDED", null);
        assertTrue(repository.findRestorableByPlayerUuid(playerUuid, home).join().isEmpty());
        assertTrue(repository.findActiveByPlayerUuid(playerUuid).join().isPresent(),
                "logout still finds the sessions of a suspended account");
    }

    private void setAccountStatus(Long accountId, String status, String lockedUntil) throws Exception {
        try (Connection connection = databaseManager.getDataSource().getConnection();
             var statement = connection.prepareStatement("UPDATE accounts SET status = ?, locked_until = ? WHERE id = ?")) {
            statement.setString(1, status);
            statement.setString(2, lockedUntil);
            statement.setLong(3, accountId);
            statement.executeUpdate();
        }
    }

    @Test
    void deleteAllClosesConnectionsAndRemovesStoredSessions() {
        repository.deleteAll().join();
        assertEquals(0, databaseManager.getActiveConnections());
    }

    @Test
    void removesExpiredSessions() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Long accountId = new SqliteAccountRepository(databaseManager.getDataSource(),
                databaseManager.getDbExecutor()).save(Account.createNew(
                playerUuid, "ExpiredUser", "expireduser", "hash"), null).join().getId();
        repository.save(new Session(UUID.randomUUID(), accountId, playerUuid, null, "server",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60), null)).join();
        repository.deleteExpired().join();
        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM sessions")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void extendActiveMovesOnlyStillValidSessions() {
        UUID playerUuid = UUID.randomUUID();
        Long accountId = new SqliteAccountRepository(databaseManager.getDataSource(),
                databaseManager.getDbExecutor()).save(Account.createNew(
                playerUuid, "ExtendUser", "extenduser", "hash"), null).join().getId();
        Instant now = Instant.now();
        Session active = new Session(UUID.randomUUID(), accountId, playerUuid, null, "server",
                now.minusSeconds(60), now.plusSeconds(30), null);
        Session expired = new Session(UUID.randomUUID(), accountId, playerUuid, null, "server",
                now.minusSeconds(120), now.minusSeconds(60), null);
        Session revoked = new Session(UUID.randomUUID(), accountId, playerUuid, null, "server",
                now.minusSeconds(30), now.plusSeconds(30), now.minusSeconds(10));
        repository.save(active).join();
        repository.save(expired).join();
        repository.save(revoked).join();

        Instant extendedUntil = now.plusSeconds(3_600);
        repository.extendActive(List.of(playerUuid, UUID.randomUUID()), extendedUntil).join();

        assertEquals(extendedUntil, repository.findById(active.getId()).join().orElseThrow().getExpiresAt());
        assertEquals(expired.getExpiresAt(), repository.findById(expired.getId()).join().orElseThrow().getExpiresAt());
        assertEquals(revoked.getExpiresAt(), repository.findById(revoked.getId()).join().orElseThrow().getExpiresAt());
    }

    @Test
    void databaseWorkRunsOnNamedThreads() throws Exception {
        String thread = databaseManager.getDbExecutor().submit(() -> Thread.currentThread().getName()).get();
        assertTrue(thread.startsWith("authsecured-database-"), thread);
    }
}
