package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseTotpRepository;
import io.github.touchfalls.authsecured.infrastructure.database.SqliteAccountRepository;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTotpRepositoryTest {

    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) { }
        @Override public void warning(String message) { }
        @Override public void severe(String message) { }
        @Override public void severe(String message, Throwable throwable) { }
    };

    private DatabaseManager database;
    private DatabaseTotpRepository totp;
    private Long accountId;

    @BeforeEach
    void setUp(@TempDir Path directory) {
        database = new DatabaseManager(LOGGER, "sqlite", "", 0, "", "", "",
                directory.resolve("totp.db").toString(), 2);
        totp = new DatabaseTotpRepository(database.getDataSource(), database.getDbExecutor(), false);
        accountId = new SqliteAccountRepository(database.getDataSource(), database.getDbExecutor())
                .save(Account.createNew(UUID.randomUUID(), "TotpUser", "totpuser", "hash"), null).join().getId();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void pendingSecretIsEnabledOnceAndThenReadBack() {
        assertTrue(totp.find(accountId).join().isEmpty());
        assertFalse(totp.enable(accountId, 5, List.of("a")).join(), "nothing to enable yet");

        totp.savePending(accountId, new byte[]{1, 2, 3}).join();
        var pending = totp.find(accountId).join().orElseThrow();
        assertFalse(pending.enabled());
        assertArrayEquals(new byte[]{1, 2, 3}, pending.encryptedSecret());

        assertTrue(totp.enable(accountId, 5, List.of("h1", "h2")).join());
        assertFalse(totp.enable(accountId, 6, List.of("x")).join(), "already enabled");
        var enabled = totp.find(accountId).join().orElseThrow();
        assertTrue(enabled.enabled());
        assertEquals(5, enabled.lastStep());
        assertEquals(List.of("h1", "h2"), enabled.backupCodeHashes());
    }

    @Test
    void aStepAndABackupCodeWorkOnlyOnce() {
        totp.savePending(accountId, new byte[]{1}).join();
        totp.enable(accountId, 5, List.of("h1", "h2")).join();

        assertFalse(totp.useStep(accountId, 5).join());
        assertFalse(totp.useStep(accountId, 4).join());
        assertTrue(totp.useStep(accountId, 6).join());
        assertFalse(totp.useStep(accountId, 6).join());

        assertTrue(totp.consumeBackupCode(accountId, "h1").join());
        assertFalse(totp.consumeBackupCode(accountId, "h1").join());
        assertFalse(totp.consumeBackupCode(accountId, "nope").join());
        assertEquals(List.of("h2"), totp.find(accountId).join().orElseThrow().backupCodeHashes());
    }

    @Test
    void disableForgetsEverything() {
        totp.savePending(accountId, new byte[]{1}).join();
        totp.enable(accountId, 5, List.of("h1")).join();

        totp.disable(accountId).join();

        assertTrue(totp.find(accountId).join().isEmpty());
    }
}
