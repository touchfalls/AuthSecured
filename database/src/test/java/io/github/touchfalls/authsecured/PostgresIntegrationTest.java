package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.security.AuditEntry;
import io.github.touchfalls.authsecured.domain.security.SecurityEvent;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.domain.session.Session;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseAuditRepository;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseSessionRepository;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseTotpRepository;
import io.github.touchfalls.authsecured.infrastructure.database.PostgresAccountRepository;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.TotpRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the Flyway migrations and the PostgreSQL branches of the repositories against a real
 * server. Skipped unless AUTHSECURED_TEST_PG_HOST is set; CI provides a database named, owned by
 * and with the password "authsecured" on port 5432.
 */
@EnabledIfEnvironmentVariable(named = "AUTHSECURED_TEST_PG_HOST", matches = ".+")
class PostgresIntegrationTest {

    private static final List<String> warnings = new ArrayList<>();
    private static DatabaseManager database;
    private static PostgresAccountRepository accounts;

    @BeforeAll
    static void migrate() {
        PlatformLogger logger = new PlatformLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) { warnings.add(message); }
            @Override public void severe(String message) { warnings.add(message); }
            @Override public void severe(String message, Throwable throwable) { warnings.add(message); }
        };
        database = new DatabaseManager(logger, "postgresql", System.getenv("AUTHSECURED_TEST_PG_HOST"), 5432,
                "authsecured", "authsecured", "authsecured", "", 4, "disable");
        accounts = new PostgresAccountRepository(database.getDataSource(), database.getDbExecutor());
    }

    @AfterAll
    static void close() {
        if (database != null) database.close();
    }

    // The database outlives one test run, so every test registers its own player.
    private static Account register() {
        String name = "pg" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return accounts.save(Account.createNew(UUID.randomUUID(), name, name, "hash"), new byte[]{1}).join();
    }

    @Test
    void accountsLockAfterTheConfiguredFailuresAndUnlock() {
        Account account = register();
        Instant now = Instant.now();

        assertTrue(accounts.incrementFailedAttemptsAtomic(account.getId(), 2, 900, now).join());
        assertFalse(accounts.findByUuid(account.getUuid()).join().orElseThrow().isLocked(now));
        assertTrue(accounts.incrementFailedAttemptsAtomic(account.getId(), 2, 900, now).join());
        assertTrue(accounts.findByUuid(account.getUuid()).join().orElseThrow().isLocked(now));

        assertTrue(accounts.unlock(account.getId(), now).join());
        Account unlocked = accounts.findByUsernameNormalized(account.getUsernameNormalized()).join().orElseThrow();
        assertFalse(unlocked.isLocked(now));
        assertEquals(0, unlocked.getFailedLoginAttempts());
    }

    @Test
    void concurrentRegistrationsCannotExceedThePerIpLimit() {
        // The database outlives the run, so the address must be new every time.
        byte[] ipHash = java.util.UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        List<java.util.concurrent.CompletableFuture<Account>> saves = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String name = "ip" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            saves.add(accounts.save(Account.createNew(UUID.randomUUID(), name, name, "hash"), ipHash, 3));
        }

        long saved = saves.stream().filter(save -> {
            try {
                save.join();
                return true;
            } catch (java.util.concurrent.CompletionException e) {
                assertInstanceOf(io.github.touchfalls.authsecured.ports.AccountLimitExceededException.class,
                        e.getCause(), e.toString());
                return false;
            }
        }).count();

        assertEquals(3, saved);
        assertEquals(3L, accounts.countAccountsByIpHash(ipHash).join());
    }

    @Test
    void concurrentRegistrationsOfOneNameCreateOneAccount() {
        String name = "dup" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<java.util.concurrent.CompletableFuture<Account>> saves = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            saves.add(accounts.save(Account.createNew(UUID.randomUUID(), name, name, "hash"), new byte[]{9}, 100));
        }

        long saved = saves.stream().filter(save -> {
            try {
                save.join();
                return true;
            } catch (java.util.concurrent.CompletionException e) {
                assertInstanceOf(io.github.touchfalls.authsecured.ports.DuplicateAccountException.class,
                        e.getCause(), e.toString());
                return false;
            }
        }).count();

        assertEquals(1, saved);
    }

    @Test
    void sessionsAreRestoredByAddressCountedExtendedAndRevoked() {
        Account account = register();
        DatabaseSessionRepository sessions = new DatabaseSessionRepository(
                database.getDataSource(), database.getDbExecutor(), true);
        byte[] home = {1, 1};
        Instant now = Instant.now();
        Session older = new Session(UUID.randomUUID(), account.getId(), account.getUuid(), home, "server",
                now.minusSeconds(60), now.plusSeconds(600), null);
        Session newer = new Session(UUID.randomUUID(), account.getId(), account.getUuid(), new byte[]{2, 2},
                "server", now, now.plusSeconds(600), null);
        sessions.save(older).join();
        sessions.save(newer).join();

        assertEquals(2, sessions.countActiveByPlayerUuid(account.getUuid()).join());
        assertEquals(older.getId(),
                sessions.findRestorableByPlayerUuid(account.getUuid(), home).join().orElseThrow().getId());
        assertEquals(newer.getId(), sessions.findActiveByPlayerUuid(account.getUuid()).join().orElseThrow().getId());

        sessions.extendActive(List.of(account.getUuid()), now.plusSeconds(3600)).join();
        assertTrue(sessions.findActiveByPlayerUuid(account.getUuid()).join().orElseThrow()
                .getExpiresAt().isAfter(now.plusSeconds(3000)));

        sessions.revokeAllForAccount(account.getId()).join();
        assertEquals(0, sessions.countActiveByPlayerUuid(account.getUuid()).join());
        sessions.deleteExpired().join();
    }

    @Test
    void securityEventsWithJsonMetadataAreStoredAndListed() {
        Account account = register();
        DatabaseAuditRepository audit = new DatabaseAuditRepository(
                database.getDataSource(), database.getDbExecutor(), true, new PlatformLogger() {
            @Override public void info(String message) {}
            @Override public void warning(String message) { warnings.add(message); }
            @Override public void severe(String message) { warnings.add(message); }
            @Override public void severe(String message, Throwable throwable) { warnings.add(message); }
        });

        audit.recordSecurityEvent(new SecurityEvent(null, account.getId(), SecurityEventType.REGISTER_SUCCESS,
                new byte[]{1}, "{\"actor\":\"console\"}", Instant.now())).join();

        // A failed insert is only logged, so the row itself is the proof.
        List<AuditEntry> entries = audit.findRecent(10, account.getUsernameNormalized()).join();
        assertTrue(entries.stream().anyMatch(entry -> "REGISTER_SUCCESS".equals(entry.event())),
                "stored events: " + entries + ", warnings: " + warnings);
        audit.purgeOldRecords(30, 30).join();
    }

    @Test
    void authenticatorStateSurvivesTheRoundTrip() {
        Account account = register();
        TotpRepository totp = new DatabaseTotpRepository(database.getDataSource(), database.getDbExecutor(), true);

        totp.savePending(account.getId(), new byte[]{7, 7, 7}).join();
        assertTrue(totp.enable(account.getId(), 100, List.of("a", "b")).join());
        assertFalse(totp.useStep(account.getId(), 100).join(), "a used step cannot be used again");
        assertTrue(totp.useStep(account.getId(), 101).join());
        assertTrue(totp.consumeBackupCode(account.getId(), "a").join());
        assertFalse(totp.consumeBackupCode(account.getId(), "a").join());

        TotpRepository.State state = totp.find(account.getId()).join().orElseThrow();
        assertTrue(state.enabled());
        assertArrayEquals(new byte[]{7, 7, 7}, state.encryptedSecret());
        assertEquals(List.of("b"), state.backupCodeHashes());

        totp.disable(account.getId()).join();
        assertFalse(totp.find(account.getId()).join().map(TotpRepository.State::enabled).orElse(false));
    }
}
