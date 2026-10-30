package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.AuthService;
import io.github.touchfalls.authsecured.application.AuthenticationTimeoutService;
import io.github.touchfalls.authsecured.application.SessionService;
import io.github.touchfalls.authsecured.application.UnifiedCommandServiceImpl;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.domain.auth.AuthState;
import io.github.touchfalls.authsecured.infrastructure.config.LocalizationManager;
import io.github.touchfalls.authsecured.infrastructure.security.IpHashService;
import io.github.touchfalls.authsecured.ports.AuthPlatform;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.PlayerRestrictionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnifiedCommandServiceTest {

    @Test
    void unexpectedCommandFailureIsLoggedWithoutSensitiveMessage() {
        PlatformLogger logger = mock(PlatformLogger.class);
        when(platform.getLogger()).thenReturn(logger);
        UUID playerUuid = UUID.randomUUID();
        when(authService.login(eq(playerUuid), eq("TestPlayer"), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("password=secret")));
        commandExecutor.executeLogin(playerUuid, "TestPlayer", "secret".toCharArray(), "127.0.0.1").join();
        org.mockito.ArgumentCaptor<String> message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger).severe(message.capture());
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().contains("password=secret"));
    }

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
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(platform).runTaskForPlayer(any(), any());

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
        when(restrictionAdapter.isAuthenticated(playerUuid)).thenReturn(true);
        SessionService sessionService = mock(SessionService.class);
        when(authService.getSessionService()).thenReturn(sessionService);
        when(sessionService.revokeActiveSessionForPlayer(playerUuid))
                .thenReturn(CompletableFuture.completedFuture(null));

        commandExecutor.executeLogout(playerUuid).join();

        verify(sessionService).revokeActiveSessionForPlayer(playerUuid);
        verify(restrictionAdapter).setPlayerUnauthenticated(playerUuid, null);
        verify(platform).sendMessage(eq(playerUuid), anyString());
    }

    @Test
    @DisplayName("Execute authstatus queries restriction adapter")
    void testExecuteAuthStatus() {
        UUID playerUuid = UUID.randomUUID();
        localizationManager.init("ru");
        when(restrictionAdapter.getAuthState(playerUuid)).thenReturn(AuthState.AUTHENTICATED);

        commandExecutor.executeAuthStatus(playerUuid);

        verify(restrictionAdapter).getAuthState(playerUuid);
        verify(platform).sendMessage(eq(playerUuid), argThat(message -> message.contains("вы авторизованы")));
    }

    @Test
    @DisplayName("Password change before login is refused without checking the password")
    void changePasswordBeforeLoginIsRejected() {
        UUID playerUuid = UUID.randomUUID();
        when(restrictionAdapter.getAuthState(playerUuid)).thenReturn(AuthState.AUTH_REQUIRED);

        commandExecutor.executeChangePassword(playerUuid, "TestPlayer",
                "guess12345".toCharArray(), "newPassword12".toCharArray(), "127.0.0.1").join();

        verify(authService, never()).changePassword(any(), any(), any(), any(), any());
        verify(restrictionAdapter, never()).setPlayerAuthenticated(any());
        verify(platform).sendMessage(eq(playerUuid), contains("/login"));
    }

    @Test
    @DisplayName("Temporary-password state may finish the login with a password change")
    void changePasswordAfterTemporaryPasswordIsAllowed() {
        UUID playerUuid = UUID.randomUUID();
        when(restrictionAdapter.getAuthState(playerUuid)).thenReturn(AuthState.PASSWORD_CHANGE_REQUIRED);
        when(authService.changePassword(eq(playerUuid), eq("TestPlayer"), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(AuthResult.SUCCESS));

        commandExecutor.executeChangePassword(playerUuid, "TestPlayer",
                "Temporary123".toCharArray(), "newPassword12".toCharArray(), "127.0.0.1").join();

        verify(restrictionAdapter).setPlayerAuthenticated(playerUuid);
    }

    @Test
    @DisplayName("Accepted temporary password marks the player for the password-change flow")
    void temporaryPasswordLoginMarksPasswordChangeState() {
        UUID playerUuid = UUID.randomUUID();
        when(authService.login(eq(playerUuid), eq("TestPlayer"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(AuthResult.PASSWORD_CHANGE_REQUIRED));

        commandExecutor.executeLogin(playerUuid, "TestPlayer", "Temporary123".toCharArray(), "127.0.0.1").join();

        verify(restrictionAdapter).setPasswordChangeRequired(playerUuid);
        verify(restrictionAdapter, never()).setPlayerAuthenticated(any());
    }

    @Test
    @DisplayName("A login result is dropped if the player reconnected under a new connection meanwhile")
    void loginResultForAnOldConnectionDoesNotAuthenticateTheNewOne() {
        UUID playerUuid = UUID.randomUUID();
        Object oldConnection = new Object();
        Object newConnection = new Object();
        when(platform.currentConnectionToken(playerUuid)).thenReturn(oldConnection, newConnection);
        when(authService.login(eq(playerUuid), eq("TestPlayer"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(AuthResult.SUCCESS));

        commandExecutor.executeLogin(playerUuid, "TestPlayer", "secret".toCharArray(), "127.0.0.1").join();

        verify(restrictionAdapter, never()).setPlayerAuthenticated(any());
        verify(platform, never()).sendMessage(eq(playerUuid), any());
    }

    @Test
    @DisplayName("Register is refused when the player is already authenticated")
    void registerIsRefusedWhenAlreadyAuthenticated() {
        UUID playerUuid = UUID.randomUUID();
        when(restrictionAdapter.isAuthenticated(playerUuid)).thenReturn(true);

        commandExecutor.executeRegister(playerUuid, "TestPlayer",
                "password12".toCharArray(), "password12".toCharArray(), "127.0.0.1").join();

        verify(authService, never()).register(any(), any(), any(), any());
        verify(platform).sendMessage(eq(playerUuid), anyString());
    }

    @Test
    @DisplayName("Logout is a no-op when the player is not authenticated")
    void logoutIsANoOpWhenNotAuthenticated() {
        UUID playerUuid = UUID.randomUUID();

        commandExecutor.executeLogout(playerUuid).join();

        verify(authService, never()).getSessionService();
        verify(restrictionAdapter, never()).setPlayerUnauthenticated(any(), any());
        verify(platform).sendMessage(eq(playerUuid), anyString());
    }

    @Test
    @DisplayName("Sessions command shows the count, revoke ends sessions but keeps the player logged in")
    void sessionsShowsCountAndRevokeKeepsTheConnectionAuthenticated() {
        UUID playerUuid = UUID.randomUUID();
        SessionService sessionService = mock(SessionService.class);
        when(authService.getSessionService()).thenReturn(sessionService);
        when(restrictionAdapter.isAuthenticated(playerUuid)).thenReturn(true);
        when(sessionService.countActiveSessions(playerUuid)).thenReturn(CompletableFuture.completedFuture(3));
        when(sessionService.revokeActiveSessionForPlayer(playerUuid)).thenReturn(CompletableFuture.completedFuture(null));

        commandExecutor.executeSessions(playerUuid, false).join();
        verify(platform).sendMessage(eq(playerUuid), contains("3"));

        commandExecutor.executeSessions(playerUuid, true).join();
        verify(sessionService).revokeActiveSessionForPlayer(playerUuid);
        verify(restrictionAdapter, never()).setPlayerUnauthenticated(any(), any());
    }

    @Test
    @DisplayName("Sessions command requires authentication")
    void sessionsRequiresAuthentication() {
        UUID playerUuid = UUID.randomUUID();

        commandExecutor.executeSessions(playerUuid, true).join();

        verify(authService, never()).getSessionService();
    }

    @Test
    @DisplayName("Player state changes run on the thread that owns that player")
    void playerStateChangesAreScheduledForThatPlayer() {
        UUID playerUuid = UUID.randomUUID();
        when(authService.login(eq(playerUuid), eq("TestPlayer"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(AuthResult.SUCCESS));

        commandExecutor.executeLogin(playerUuid, "TestPlayer", "Pass123456!".toCharArray(), "127.0.0.1").join();

        // Folia has no main thread: the platform must be told whose state changes.
        verify(platform).runTaskForPlayer(eq(playerUuid), any());
        verify(platform, never()).runTaskOnMainThread(any());
    }

    @Test
    @DisplayName("Revoking authentication returns an online player to the pre-login state")
    void revokeAuthenticationRestrictsAnOnlinePlayerAgain() {
        UUID playerUuid = UUID.randomUUID();
        when(platform.isPlayerOnline(playerUuid)).thenReturn(true);
        AuthenticationTimeoutService timeouts = new AuthenticationTimeoutService(true, 5);
        UnifiedCommandServiceImpl executor = new UnifiedCommandServiceImpl(
                authService, platform, localizationManager, ipHashService, timeouts);

        executor.revokeAuthentication(playerUuid, "messages.admin-password-reset-notice", "fallback");

        verify(platform).runTaskForPlayer(eq(playerUuid), any());
        verify(restrictionAdapter).setPlayerUnauthenticated(playerUuid, null);
        verify(platform).sendMessage(eq(playerUuid), contains("administrator"));
        assertEquals(List.of(playerUuid), timeouts.drainExpired(Instant.now().plusSeconds(6)));
    }

    @Test
    @DisplayName("Revoking authentication ignores a player who is not online")
    void revokeAuthenticationIgnoresAnOfflinePlayer() {
        UUID playerUuid = UUID.randomUUID();
        when(platform.isPlayerOnline(playerUuid)).thenReturn(false);

        commandExecutor.revokeAuthentication(playerUuid, "messages.admin-unregistered-notice", "fallback");

        verify(restrictionAdapter, never()).setPlayerUnauthenticated(any(), any());
        verify(platform, never()).sendMessage(any(), any());
    }
}
