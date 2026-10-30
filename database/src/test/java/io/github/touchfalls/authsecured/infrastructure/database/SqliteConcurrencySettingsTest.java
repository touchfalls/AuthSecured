package io.github.touchfalls.authsecured.infrastructure.database;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteConcurrencySettingsTest {

    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) { }
        @Override public void warning(String message) { }
        @Override public void severe(String message) { }
        @Override public void severe(String message, Throwable throwable) { }
    };

    @Test
    void sqliteConnectionsUseWalAndWaitForLocks(@TempDir Path directory) throws Exception {
        DatabaseManager manager = new DatabaseManager(LOGGER, "sqlite", "", 0, "", "", "",
                directory.resolve("auth.db").toString(), 2);
        try (Connection connection = manager.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {
                rs.next();
                assertEquals("wal", rs.getString(1));
            }
            try (ResultSet rs = statement.executeQuery("PRAGMA busy_timeout")) {
                rs.next();
                assertEquals(5000, rs.getInt(1));
            }
        } finally {
            manager.close();
        }
    }
}
