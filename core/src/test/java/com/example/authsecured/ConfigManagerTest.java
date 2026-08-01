package com.example.authsecured;

import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {

    @TempDir
    File tempFolder;

    private ConfigManager configManager;
    private static final PlatformLogger DUMMY_LOGGER = new PlatformLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
        @Override public void severe(String message, Throwable throwable) {}
    };

    @BeforeEach
    void setUp() {
        configManager = new ConfigManager(tempFolder, DUMMY_LOGGER);
        configManager.loadConfigurations();
    }

    @Test
    @DisplayName("Verify config.yml creation and default value reading")
    void testConfigDefaults() {
        assertTrue(new File(tempFolder, "config.yml").exists());

        assertEquals("sqlite", configManager.getString("database.type", "sqlite"));
        assertEquals(65536, configManager.getInt("security.password.argon2.memory-kib", 65536));
        assertTrue(configManager.getBoolean("session.enabled", true));

        List<String> allowedCommands = configManager.getStringList("security.allowed-commands", List.of("/login", "/register"));
        assertNotNull(allowedCommands);
        assertFalse(allowedCommands.isEmpty());
    }

    @Test
    @DisplayName("Verify fallback values for missing config paths")
    void testMissingPathFallback() {
        assertEquals("default_val", configManager.getString("nonexistent.path", "default_val"));
        assertEquals(999, configManager.getInt("nonexistent.int", 999));
        assertFalse(configManager.getBoolean("nonexistent.bool", false));
    }
}
