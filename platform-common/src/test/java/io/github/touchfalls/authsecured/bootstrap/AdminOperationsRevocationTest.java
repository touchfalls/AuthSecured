package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.application.AuthService;
import io.github.touchfalls.authsecured.domain.auth.TemporaryPasswordReset;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOperationsRevocationTest {
    @TempDir Path directory;

    private AuthService authService;
    private UnifiedCommandExecutor commandExecutor;
    private AdminOperations operations;

    @BeforeEach
    void setUp() {
        ConfigManager config = new ConfigManager(directory.toFile(), mock(PlatformLogger.class));
        assertTrue(config.loadConfigurations());
        DependencyContainer container = mock(DependencyContainer.class);
        authService = mock(AuthService.class);
        commandExecutor = mock(UnifiedCommandExecutor.class);
        when(container.getConfigManager()).thenReturn(config);
        when(container.getAuthService()).thenReturn(authService);
        when(container.getCommandExecutor()).thenReturn(commandExecutor);
        operations = new AdminOperations(container);
    }

    @Test
    void temporaryPasswordEndsTheAuthenticationOfTheOnlineAccountOwner() {
        UUID owner = UUID.randomUUID();
        TemporaryPasswordReset reset = new TemporaryPasswordReset(owner, "Temporary12345".toCharArray(),
                Instant.now().plusSeconds(900));
        when(authService.issueTemporaryPassword(eq("Player"), anyInt(), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(reset));

        assertSame(reset, operations.issueTemporaryPassword("Player", null).join());
        verify(commandExecutor).revokeAuthentication(eq(owner), eq("messages.admin-password-reset-notice"), anyString());
    }

    @Test
    void unknownAccountForTemporaryPasswordRevokesNothing() {
        when(authService.issueTemporaryPassword(eq("Nobody"), anyInt(), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertNull(operations.issueTemporaryPassword("Nobody", null).join());
        verify(commandExecutor, never()).revokeAuthentication(any(), any(), any());
    }

    @Test
    void unregisterEndsTheAuthenticationOfTheDeletedAccountOwner() {
        UUID owner = UUID.randomUUID();
        when(authService.unregisterAccount("Player", null))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(owner)));

        assertTrue(operations.unregister("Player", null).join());
        verify(commandExecutor).revokeAuthentication(eq(owner), eq("messages.admin-unregistered-notice"), anyString());
    }

    @Test
    void unregisterOfAnUnknownAccountRevokesNothing() {
        when(authService.unregisterAccount("Nobody", null))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        assertFalse(operations.unregister("Nobody", null).join());
        verify(commandExecutor, never()).revokeAuthentication(any(), any(), any());
    }
}
