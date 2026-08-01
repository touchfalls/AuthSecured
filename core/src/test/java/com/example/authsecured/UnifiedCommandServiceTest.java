package com.example.authsecured;

import com.example.authsecured.application.AuthService;
import com.example.authsecured.application.UnifiedCommandServiceImpl;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.domain.auth.AuthState;
import com.example.authsecured.infrastructure.config.LocalizationManager;
import com.example.authsecured.infrastructure.security.IpHashService;
import com.example.authsecured.ports.AuthPlatform;
import com.example.authsecured.ports.PlatformLogger;
import com.example.authsecured.ports.PlayerRestrictionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnifiedCommandServiceTest {

    @TempDir
    File tempDir;

    private AuthService authService;
    private AuthPlatform platform;
    private PlayerRestrictionAdapter restrictionAdapter;
    private LocalizationManager localizationManager;
    private IpHashService ipHashService;
    private UnifiedCommandServiceImpl commandExecutor;

    private static final PlatformLogger DUMMY_LOGGER = new PlatformLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
        @Override public void severe(String message, Throwable throwable) {}
    };

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        platform = mock(AuthPlatform.class);
        restrictionAdapter = mock(PlayerRestrictionAdapter.class);

        when(platform.getRestrictionAdapter()).thenReturn(restrictionAdapter);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(platform).runTaskOnMainThread(any());

        localizationManager = new LocalizationManager(tempDir, "en", DUMMY_LOGGER);
        ipHashService = new IpHashService("test-secret");

        commandExecutor = new UnifiedCommandServiceImpl(authService, platform, localizationManager, ipHashService);
    }

    @Test
    @DisplayName("Execute register command success path triggers player authentication and message")
    void testExecuteRegisterSuccess() {
        UUID playerUuid = UUID.randomUUID();
        when(authService.register(eq(playerUuid), eq("TestPlayer"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(AuthResult.SUCCESS));

        commandExecutor.executeRegister(playerUuid, "TestPlayer", "Pass123456!".toCharArray(), "Pass123456!".toCharArray(), "127.0.0.1").join();

        verify(restrictionAdapter).setPlayerAuthenticated(playerUuid);
        verify(platform).sendMessage(eq(playerUuid), anyString());
    }

    @Test
    @DisplayName("Execute login command success path triggers player authentication and message")
    void testExecuteLoginSuccess() {
        UUID playerUuid = UUID.randomUUID();
        when(authService.login(eq(playerUuid), eq("TestPlayer"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(AuthResult.SUCCESS));

        commandExecutor.executeLogin(playerUuid, "TestPlayer", "Pass123456!".toCharArray(), "127.0.0.1").join();

        verify(restrictionAdapter).setPlayerAuthenticated(playerUuid);
        verify(platform).sendMessage(eq(playerUuid), anyString());
    }

    @Test
    @DisplayName("Execute logout sets player unauthenticated")
    void testExecuteLogout() {
        UUID playerUuid = UUID.randomUUID();

        commandExecutor.executeLogout(playerUuid).join();

        verify(restrictionAdapter).setPlayerUnauthenticated(playerUuid, null);
        verify(platform).sendMessage(eq(playerUuid), anyString());
    }

    @Test
    @DisplayName("Execute authstatus queries restriction adapter")
    void testExecuteAuthStatus() {
        UUID playerUuid = UUID.randomUUID();
        when(restrictionAdapter.getAuthState(playerUuid)).thenReturn(AuthState.AUTHENTICATED);

        commandExecutor.executeAuthStatus(playerUuid);

        verify(restrictionAdapter).getAuthState(playerUuid);
        verify(platform).sendMessage(eq(playerUuid), anyString());
    }
}
