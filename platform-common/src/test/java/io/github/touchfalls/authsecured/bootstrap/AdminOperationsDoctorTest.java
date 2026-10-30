package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.application.SecurityService;
import io.github.touchfalls.authsecured.application.SessionService;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseManager;
import io.github.touchfalls.authsecured.ports.AuthPlatform;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AdminOperationsDoctorTest {
    @TempDir Path directory;

    @Test
    void fileBasedSecretWithSharedPostgresIsAnExplicitDoctorFailure() {
        var lines = doctor("postgresql", false, true, true);
        assertTrue(lines.stream().anyMatch(line -> line.contains("[FAIL]")
                && line.contains("AUTHSECURED_IP_SECRET")));
    }

    @Test
    void fileBasedSecretWithSqliteAndNoRedisIsFine() {
        var lines = doctor("sqlite", false, true, true);
        assertTrue(lines.stream().noneMatch(line -> line.contains("[FAIL]")
                && line.contains("AUTHSECURED_IP_SECRET")));
    }

    @Test
    void sessionsWithoutIpBindingFailOnlyOnOfflineModeServers() {
        assertTrue(failed(doctor("sqlite", true, false, false), "session.verify-ip"));
        assertFalse(failed(doctor("sqlite", true, false, true), "session.verify-ip"));
        assertFalse(failed(doctor("sqlite", true, true, false), "session.verify-ip"));
    }

    @Test
    void weakArgon2SettingsFail() throws IOException {
        assertFalse(failed(doctor("sqlite", true, true, false), "OWASP"));

        writeConfig("security:\n  password:\n    argon2:\n      memory-kib: 16384\n      iterations: 2\n");
        assertTrue(failed(doctor("sqlite", true, true, false), "OWASP"));
    }

    @Test
    void owaspArgon2MinimumTradesMemoryForIterations() {
        assertTrue(AdminOperations.meetsOwaspArgon2Minimum(65_536, 3));
        assertTrue(AdminOperations.meetsOwaspArgon2Minimum(47_104, 1));
        assertTrue(AdminOperations.meetsOwaspArgon2Minimum(19_456, 2));
        assertTrue(AdminOperations.meetsOwaspArgon2Minimum(7_168, 5));
        assertFalse(AdminOperations.meetsOwaspArgon2Minimum(47_103, 1));
        assertFalse(AdminOperations.meetsOwaspArgon2Minimum(19_455, 2));
        assertFalse(AdminOperations.meetsOwaspArgon2Minimum(9_215, 4));
    }

    @Test
    void unencryptedPostgresFailsOnlyForRemoteHosts() throws IOException {
        writeConfig("database:\n  postgresql:\n    host: db.example.com\n    ssl-mode: disable\n");
        assertTrue(failed(doctor("postgresql", true, true, false), "verify-full"));

        writeConfig("database:\n  postgresql:\n    host: 127.0.0.1\n    ssl-mode: disable\n");
        assertFalse(failed(doctor("postgresql", true, true, false), "verify-full"));

        writeConfig("database:\n  postgresql:\n    host: db.example.com\n    ssl-mode: verify-full\n");
        assertFalse(failed(doctor("postgresql", true, true, false), "verify-full"));
    }

    private List<String> doctor(String databaseType, boolean secretFromEnvironment,
                                boolean verifyIp, boolean onlineMode) {
        ConfigManager config = new ConfigManager(directory.toFile(), mock(PlatformLogger.class));
        assertTrue(config.loadConfigurations());
        DependencyContainer container = mock(DependencyContainer.class);
        DatabaseManager database = mock(DatabaseManager.class);
        SecurityService security = mock(SecurityService.class);
        SessionService sessions = mock(SessionService.class);
        AuthPlatform platform = mock(AuthPlatform.class);
        when(container.getConfigManager()).thenReturn(config);
        when(container.getDatabaseManager()).thenReturn(database);
        when(container.getSecurityService()).thenReturn(security);
        when(container.getSessionService()).thenReturn(sessions);
        when(container.getPlatform()).thenReturn(platform);
        when(container.getRateLimitBackend()).thenReturn("memory");
        when(database.ping()).thenReturn(CompletableFuture.completedFuture(true));
        when(database.getDatabaseType()).thenReturn(databaseType);
        when(security.isAuditEnabled()).thenReturn(true);
        when(sessions.isEnabled()).thenReturn(true);
        when(sessions.isVerifyIp()).thenReturn(verifyIp);
        when(platform.isOnlineMode()).thenReturn(onlineMode);
        when(container.isIpHashSecretFromEnvironment()).thenReturn(secretFromEnvironment);
        return new AdminOperations(container).doctor().join();
    }

    private void writeConfig(String yaml) throws IOException {
        Files.writeString(directory.resolve("config.yml"), yaml);
    }

    @Test
    void onlyNumericLoopbackAddressesCountAsLocal() {
        assertTrue(AdminOperations.isLoopbackHost("127.0.0.1"));
        assertTrue(AdminOperations.isLoopbackHost("localhost"));
        // A remote host name that merely starts with "127." must still get the TLS warning.
        assertFalse(AdminOperations.isLoopbackHost("127.db.example.com"));
    }

    private static boolean failed(List<String> lines, String text) {
        return lines.stream().anyMatch(line -> line.contains("[FAIL]") && line.contains(text));
    }

    @Test
    void sharedLocalAddressMeansAProxyWithoutIpForwarding() {
        assertTrue(AdminOperations.playersShareOneLocalAddress(java.util.List.of("127.0.0.1", "127.0.0.1")));
        assertTrue(AdminOperations.playersShareOneLocalAddress(java.util.List.of("10.0.0.5", "10.0.0.5", "10.0.0.5")));
        assertTrue(AdminOperations.playersShareOneLocalAddress(java.util.List.of("fd12::1", "fd12::1")));
        assertFalse(AdminOperations.playersShareOneLocalAddress(java.util.List.of("10.0.0.5")));
        assertFalse(AdminOperations.playersShareOneLocalAddress(java.util.List.of("10.0.0.5", "10.0.0.6")));
        assertFalse(AdminOperations.playersShareOneLocalAddress(java.util.List.of("203.0.113.9", "203.0.113.9")));
        assertFalse(AdminOperations.playersShareOneLocalAddress(java.util.List.of("example.com", "example.com")));
        assertFalse(AdminOperations.playersShareOneLocalAddress(java.util.List.of()));
    }
}
