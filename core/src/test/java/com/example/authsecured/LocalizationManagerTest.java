package com.example.authsecured;

import com.example.authsecured.infrastructure.config.LocalizationManager;
import com.example.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationManagerTest {

    @TempDir
    File tempFolder;

    private LocalizationManager localizationManager;
    private static final PlatformLogger LOGGER = new PlatformLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
        @Override public void severe(String message, Throwable throwable) {}
    };

    @BeforeEach
    void setUp() {
        localizationManager = new LocalizationManager(tempFolder, "en", LOGGER);
    }

    @Test
    void testDefaultEnglishLocalization() {
        localizationManager.init("en");
        assertEquals("en", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default Prompt");
        assertTrue(loginPrompt.contains("§"), "Color code should be translated to section symbol");
        assertTrue(loginPrompt.contains("/login"));
    }

    @Test
    void testRussianLocalization() {
        localizationManager.init("ru");
        assertEquals("ru", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("войдите"), "Russian translation should contain Russian text: " + loginPrompt);
    }

    @Test
    void testSpanishLocalization() {
        localizationManager.init("es");
        assertEquals("es", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("inicia"), "Spanish translation should contain Spanish text: " + loginPrompt);
    }

    @Test
    void testItalianLocalization() {
        localizationManager.init("it");
        assertEquals("it", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("login"), "Italian translation should contain Italian text: " + loginPrompt);
    }

    @Test
    void testFrenchLocalization() {
        localizationManager.init("fr");
        assertEquals("fr", localizationManager.getCurrentLanguage());

        String loginPrompt = localizationManager.getMessage("messages.login-prompt", "Default");
        assertTrue(loginPrompt.contains("connecter"), "French translation should contain French text: " + loginPrompt);
    }

    @Test
    void testFallbackToEnglish() {
        localizationManager.init("ru");
        String missingKey = localizationManager.getMessage("messages.nonexistent-key", "Fallback Default");
        assertEquals("Fallback Default", missingKey);
    }

    @Test
    void testPlaceholderSubstitution() {
        localizationManager.init("en");
        String msg = localizationManager.getMessage("messages.login-usage", "&cUsage: /login <password>", Map.of("user", "Player1"));
        assertNotNull(msg);
    }
}
