package com.example.authsecured;

import com.example.authsecured.domain.session.Session;
import com.example.authsecured.infrastructure.database.DatabaseManager;
import com.example.authsecured.infrastructure.database.DatabaseSessionRepository;
import com.example.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
        Session session = Session.create(100L, playerUuid, "server-1", 1800);

        // Save session
        repository.save(session).join();

        // Revoke session
        repository.revoke(session.getId()).join();

        // Verify session is no longer active
        Optional<Session> revoked = repository.findActiveByPlayerUuid(playerUuid).join();
        assertTrue(revoked.isEmpty());
    }
}
