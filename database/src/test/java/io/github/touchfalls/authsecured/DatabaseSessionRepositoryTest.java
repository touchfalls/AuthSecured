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
