package com.example.authsecured;

import com.example.authsecured.domain.account.Account;
import com.example.authsecured.infrastructure.database.DatabaseManager;
import com.example.authsecured.infrastructure.database.SqliteAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class SqliteAccountRepositoryTest {

    private DatabaseManager databaseManager;
    private SqliteAccountRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        File dbFile = tempDir.resolve("test_auth.db").toFile();
        Logger logger = Logger.getLogger("TestLogger");
        databaseManager = new DatabaseManager(logger, "sqlite", "", 0, "", "", "", dbFile.getAbsolutePath(), 2);
        repository = new SqliteAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor());
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

        Account saved = repository.save(account).join();
        assertNotNull(saved.getId());

        Optional<Account> found = repository.findByUuid(uuid).join();
        assertTrue(found.isPresent());
        assertEquals("TestUser", found.get().getUsername());
        assertEquals("testuser", found.get().getUsernameNormalized());
    }
}
