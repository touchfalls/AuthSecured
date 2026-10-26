package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseAuditRepository;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseSessionRepository;
import io.github.touchfalls.authsecured.infrastructure.database.SqliteAccountRepository;
import io.github.touchfalls.authsecured.domain.session.Session;
import io.github.touchfalls.authsecured.domain.auth.LoginAttempt;
import io.github.touchfalls.authsecured.domain.security.SecurityEvent;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.AccountLimitExceededException;
import io.github.touchfalls.authsecured.ports.DuplicateAccountException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteAccountRepositoryTest {

    private DatabaseManager databaseManager;
    private SqliteAccountRepository repository;
    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) { }
        @Override public void warning(String message) { }
        @Override public void severe(String message) { }
        @Override public void severe(String message, Throwable throwable) { }
    };

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("test_auth.db").toFile();
        databaseManager = new DatabaseManager(LOGGER, "sqlite", "", 0, "", "", "",
                dbFile.getAbsolutePath(), 2);
        repository = new SqliteAccountRepository(databaseManager.getDataSource(),
                databaseManager.getDbExecutor());
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Test
    void testSaveAndFindByUuid() {
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "TestUser", "testuser", "$argon2id$dummyhash");

        Account saved = repository.save(account, null).join();
        assertNotNull(saved.getId());

        Optional<Account> found = repository.findByUuid(uuid).join();
        assertTrue(found.isPresent());
        assertEquals("TestUser", found.get().getUsername());
        assertEquals("testuser", found.get().getUsernameNormalized());
    }

    @Test
    void persistsTemporaryPasswordState() {
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "ResetUser", "resetuser", "$argon2id$temporary");
        Instant expiry = Instant.now().plusSeconds(900);
        account.setTemporaryPassword("$argon2id$temporary", expiry);

        repository.save(account, null).join();
        Account restored = repository.findByUuid(uuid).join().orElseThrow();

        assertTrue(restored.isPasswordChangeRequired());
        assertEquals(expiry, restored.getTemporaryPasswordExpiresAt());
    }

    @Test
    void rejectsDuplicateUsernameWithSpecificException() {
        repository.save(Account.createNew(UUID.randomUUID(), "Player", "player", "hash"), null).join();

        java.util.concurrent.CompletionException error = assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> repository.save(Account.createNew(
                        UUID.randomUUID(), "PLAYER", "player", "hash"), null).join());

        assertTrue(error.getCause() instanceof DuplicateAccountException);
    }

    @Test
    void replacePasswordHashOnlyOverwritesTheExpectedHash() {
        Account account = repository.save(Account.createNew(UUID.randomUUID(), "Rehash", "rehash", "old-hash"), null)
                .join();
        Instant changedAt = repository.findByUuid(account.getUuid()).join().orElseThrow().getPasswordChangedAt();

        assertFalse(repository.replacePasswordHash(account.getId(), "stale-hash", "new-hash", Instant.now()).join());
        assertTrue(repository.replacePasswordHash(account.getId(), "old-hash", "new-hash", Instant.now()).join());

        Account stored = repository.findByUuid(account.getUuid()).join().orElseThrow();
        assertEquals("new-hash", stored.getPasswordHash());
        // Not a password change: the change time stays as it was.
        assertEquals(changedAt, stored.getPasswordChangedAt());
    }

    @Test
    void failedAttemptsAreUpdatedAtomically() {
        Account saved = repository.save(Account.createNew(
                UUID.randomUUID(), "AtomicUser", "atomicuser", "hash"), null).join();
        Instant now = Instant.now();
        java.util.List<java.util.concurrent.CompletableFuture<Boolean>> updates =
                java.util.stream.IntStream.range(0, 20)
                        .mapToObj(ignored -> repository.incrementFailedAttemptsAtomic(
                                saved.getId(), 100, 900, now))
                        .toList();
        updates.forEach(java.util.concurrent.CompletableFuture::join);

        Account updated = repository.findByUuid(saved.getUuid()).join().orElseThrow();
        assertEquals(20, updated.getFailedLoginAttempts());
        assertTrue(repository.resetFailedAttemptsAtomic(saved.getId(), now.plusSeconds(1)).join());
        assertEquals(0, repository.findByUuid(saved.getUuid()).join().orElseThrow().getFailedLoginAttempts());
    }

    @Test
    void failureAfterLockExpiryResetsTheCounterInsteadOfRelocking() {
        Account saved = repository.save(Account.createNew(
                UUID.randomUUID(), "LockedUser", "lockeduser", "hash"), null).join();
        Instant now = Instant.now();
        // 5 failures reach maxAttempts=5 and lock the account until now+900s.
        for (int i = 0; i < 5; i++) {
            repository.incrementFailedAttemptsAtomic(saved.getId(), 5, 900, now).join();
        }
        Account locked = repository.findByUuid(saved.getUuid()).join().orElseThrow();
        assertEquals(5, locked.getFailedLoginAttempts());
        assertTrue(locked.isLocked(now));

        // After the lock has expired, one more wrong guess must not re-lock the account
        // by counting from the stale total of 5; it should start a fresh count of 1.
        Instant afterExpiry = now.plusSeconds(1000);
        repository.incrementFailedAttemptsAtomic(saved.getId(), 5, 900, afterExpiry).join();

        Account afterOneMoreFailure = repository.findByUuid(saved.getUuid()).join().orElseThrow();
        assertEquals(1, afterOneMoreFailure.getFailedLoginAttempts());
        assertTrue(!afterOneMoreFailure.isLocked(afterExpiry.plusSeconds(1)),
                "a single failure right after lock expiry must not immediately re-lock the account");
    }

    @Test
    void countAccountsByIpHashWorksWithoutAnyAuditEvents() {
        byte[] ipHash = {1, 2, 3};
        repository.save(Account.createNew(UUID.randomUUID(), "First", "first", "hash"), ipHash).join();
        repository.save(Account.createNew(UUID.randomUUID(), "Second", "second", "hash"), ipHash).join();
        repository.save(Account.createNew(UUID.randomUUID(), "Other", "other", "hash"), new byte[]{9}).join();

        // No security_events rows were written at all: the cap must still count from accounts.
        assertEquals(2L, repository.countAccountsByIpHash(ipHash).join());
    }

    @Test
    void closeLetsQueuedWritesFinish() throws Exception {
        // Both worker threads are busy, so the save is still queued when close() starts.
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        for (int i = 0; i < 2; i++) {
            databaseManager.getDbExecutor().submit(() -> {
                release.await();
                return null;
            });
        }
        java.util.concurrent.CompletableFuture<Account> queued = repository.save(
                Account.createNew(UUID.randomUUID(), "Late", "late", "hash"), null);
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
            release.countDown();
        }).start();

        databaseManager.close();

        assertNotNull(queued.join().getId());
        databaseManager = null;
    }

    @Test
    void concurrentRegistrationsCannotExceedThePerIpLimit() {
        byte[] ipHash = {4, 5, 6};
        java.util.List<java.util.concurrent.CompletableFuture<Account>> saves = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            saves.add(repository.save(Account.createNew(UUID.randomUUID(), "Alt" + i, "alt" + i, "hash"), ipHash, 3));
        }

        long saved = saves.stream().filter(save -> {
            try {
                save.join();
                return true;
            } catch (java.util.concurrent.CompletionException e) {
                assertTrue(e.getCause() instanceof AccountLimitExceededException, e.toString());
                return false;
            }
        }).count();

        assertEquals(3, saved);
        assertEquals(3L, repository.countAccountsByIpHash(ipHash).join());
    }

    @Test
    void upgradesLegacySqliteAccountsTable(@TempDir Path legacyDir) throws Exception {
        File legacyFile = legacyDir.resolve("legacy.db").toFile();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + legacyFile);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "uuid TEXT NOT NULL UNIQUE, username TEXT NOT NULL, username_normalized TEXT NOT NULL UNIQUE, "
                    + "password_hash TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'ACTIVE', "
                    + "failed_login_attempts INTEGER NOT NULL DEFAULT 0, locked_until TEXT NULL, "
                    + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, last_login_at TEXT NULL, "
                    + "password_changed_at TEXT NOT NULL)");
        }

        DatabaseManager legacyManager = new DatabaseManager(LOGGER, "sqlite", "", 0, "", "", "",
                legacyFile.getAbsolutePath(), 2);
        try (Connection connection = legacyManager.getDataSource().getConnection();
             ResultSet columns = connection.getMetaData().getColumns(
                     null, null, "accounts", "password_change_required")) {
            assertTrue(columns.next());
            try (ResultSet ipColumn = connection.getMetaData().getColumns(
                    null, null, "accounts", "registration_ip_hash")) {
                assertTrue(ipColumn.next());
            }
            try (Statement statement = connection.createStatement();
                 ResultSet version = statement.executeQuery("SELECT version FROM authsecured_schema_version")) {
                assertTrue(version.next());
                assertEquals(3, version.getInt(1));
            }
            try (Statement statement = connection.createStatement();
                 ResultSet table = statement.executeQuery(
                         "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sessions'")) {
                assertTrue(table.next(), "V1 must recreate missing tables before marking a legacy schema migrated");
            }
        } finally {
            legacyManager.close();
        }
    }

    @Test
    void returnsRecentPrivacySafeAuditEntries() {
        Account saved = repository.save(Account.createNew(
                UUID.randomUUID(), "AuditUser", "audituser", "$argon2id$dummyhash"), null).join();
        DatabaseAuditRepository audit = new DatabaseAuditRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), false, LOGGER);
        audit.recordLoginAttempt(new LoginAttempt(
                null, saved.getId(), new byte[]{1, 2}, false, "INVALID_PASSWORD", Instant.now())).join();
        audit.recordSecurityEvent(new SecurityEvent(
                null, saved.getId(), SecurityEventType.ACCOUNT_UNLOCKED,
                new byte[]{1, 2}, "{}", Instant.now().plusMillis(1))).join();

        var entries = audit.findRecent(10, "audituser").join();

        assertEquals(2, entries.size());
        assertEquals("AuditUser", entries.get(0).username());
        assertEquals("ACCOUNT_UNLOCKED", entries.get(0).event());
        assertEquals("INVALID_PASSWORD", entries.get(1).reason());
        assertEquals(0, databaseManager.getActiveConnections());
    }

    @Test
    void deletingAccountCascadesToSessionOnEveryPooledConnection() throws Exception {
        UUID uuid = UUID.randomUUID();
        Account account = repository.save(Account.createNew(uuid, "CascadeUser", "cascadeuser", "hash"), null).join();
        DatabaseSessionRepository sessions = new DatabaseSessionRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), false);
        sessions.save(Session.create(account.getId(), uuid, "server", 1800)).join();

        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet pragma = statement.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(pragma.next());
            assertEquals(1, pragma.getInt(1));
        }

        assertTrue(repository.delete(uuid).join());
        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM sessions")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    void purgesOldAuditRows() throws Exception {
        DatabaseAuditRepository audit = new DatabaseAuditRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), false, LOGGER);
        Instant old = Instant.now().minusSeconds(40L * 86_400);
        audit.recordLoginAttempt(new LoginAttempt(null, null, new byte[]{1}, false,
                "INVALID_PASSWORD", old)).join();
        audit.recordSecurityEvent(new SecurityEvent(null, null,
                SecurityEventType.RATE_LIMIT_EXCEEDED, new byte[]{1}, "{}", old)).join();
        audit.purgeOldRecords(30, 30).join();
        try (Connection connection = databaseManager.getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT (SELECT COUNT(*) FROM login_attempts) + "
                             + "(SELECT COUNT(*) FROM security_events)")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }
}
