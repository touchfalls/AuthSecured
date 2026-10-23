package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.domain.auth.AuthState;
import io.github.touchfalls.authsecured.infrastructure.config.LocalizationManager;
import io.github.touchfalls.authsecured.infrastructure.security.IpHashService;
import io.github.touchfalls.authsecured.ports.AuthPlatform;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import io.github.touchfalls.authsecured.util.FailureDiagnostics;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified implementation of command processing logic across all platforms.
 */
public class UnifiedCommandServiceImpl implements UnifiedCommandExecutor {

    private final AuthService authService;
    private final AuthPlatform platform;
    private final LocalizationManager localizationManager;
    private final IpHashService ipHashService;
    private final AuthenticationTimeoutService authenticationTimeoutService;

    public UnifiedCommandServiceImpl(AuthService authService, AuthPlatform platform, LocalizationManager localizationManager, IpHashService ipHashService) {
        this(authService, platform, localizationManager, ipHashService, null);
    }

    public UnifiedCommandServiceImpl(AuthService authService, AuthPlatform platform,
                                     LocalizationManager localizationManager, IpHashService ipHashService,
                                     AuthenticationTimeoutService authenticationTimeoutService) {
        this.authService = authService;
        this.platform = platform;
        this.localizationManager = localizationManager;
        this.ipHashService = ipHashService;
        this.authenticationTimeoutService = authenticationTimeoutService;
    }

    @Override
    public CompletableFuture<Void> executeRegister(UUID playerUuid, String playerName, char[] password, char[] confirmPassword, String ipAddress) {
        if (platform.getRestrictionAdapter().isAuthenticated(playerUuid)) {
            if (password != null) Arrays.fill(password, ' ');
            if (confirmPassword != null) Arrays.fill(confirmPassword, ' ');
            platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid,
                    localizationManager.getMessage("messages.already-authenticated", "&cYou are already authenticated!")));
            return CompletableFuture.completedFuture(null);
        }
        if (!Arrays.equals(password, confirmPassword)) {
            platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid,
                    localizationManager.getMessage("messages.passwords-dont-match", "§cPasswords do not match!")));
            if (password != null) Arrays.fill(password, ' ');
            if (confirmPassword != null) Arrays.fill(confirmPassword, ' ');
            return CompletableFuture.completedFuture(null);
        }
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        Object connectionToken = platform.currentConnectionToken(playerUuid);
        return authService.register(playerUuid, playerName, password, ipHash)
                .thenAccept(result -> platform.runTaskForPlayer(playerUuid, () -> {
                    // A disconnect-and-reconnect under the same UUID while this was hashing must
                    // not authenticate the new connection with the old one's result (audit #6).
                    if (!Objects.equals(connectionToken, platform.currentConnectionToken(playerUuid))) return;
                    if (result == AuthResult.SUCCESS) {
                        platform.getRestrictionAdapter().setPlayerAuthenticated(playerUuid);
                        clearAuthenticationTimeout(playerUuid);
                        platform.sendMessage(playerUuid, localizationManager.getMessage("messages.register-success"));
                    } else {
                        String msgKey = switch (result) {
                            case ALREADY_REGISTERED -> "messages.already-registered";
                            case VALIDATION_FAILED -> "messages.password-policy-failed";
                            case RATE_LIMITED -> "messages.rate-limited";
                            default -> "messages.error";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    logFailure("Registration command", throwable);
                    platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid, localizationManager.getMessage("messages.error")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (password != null) Arrays.fill(password, ' ');
                    if (confirmPassword != null) Arrays.fill(confirmPassword, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeLogin(UUID playerUuid, String playerName, char[] password, String ipAddress) {
        if (platform.getRestrictionAdapter().isAuthenticated(playerUuid)) {
            if (password != null) Arrays.fill(password, ' ');
            platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid,
                    localizationManager.getMessage("messages.already-authenticated", "&cYou are already logged in!")));
            return CompletableFuture.completedFuture(null);
        }
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        Object connectionToken = platform.currentConnectionToken(playerUuid);
        return authService.login(playerUuid, playerName, password, ipHash)
                .thenAccept(result -> platform.runTaskForPlayer(playerUuid, () -> {
                    // A disconnect-and-reconnect under the same UUID while this was hashing must
                    // not authenticate the new connection with the old one's result (audit #6).
                    if (!Objects.equals(connectionToken, platform.currentConnectionToken(playerUuid))) return;
                    if (result == AuthResult.SUCCESS) {
                        platform.getRestrictionAdapter().setPlayerAuthenticated(playerUuid);
                        clearAuthenticationTimeout(playerUuid);
                        platform.sendMessage(playerUuid, localizationManager.getMessage("messages.login-success"));
                    } else {
                        if (result == AuthResult.PASSWORD_CHANGE_REQUIRED) {
                            platform.getRestrictionAdapter().setPasswordChangeRequired(playerUuid);
                        }
                        String msgKey = switch (result) {
                            case ACCOUNT_NOT_FOUND, NOT_REGISTERED, INVALID_PASSWORD -> "messages.login-failed";
                            case ACCOUNT_LOCKED -> "messages.account-locked";
                            case RATE_LIMITED -> "messages.rate-limited";
                            case PASSWORD_CHANGE_REQUIRED -> "messages.temporary-password-change-required";
                            case TEMPORARY_PASSWORD_EXPIRED -> "messages.temporary-password-expired";
                            default -> "messages.error";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    logFailure("Login command", throwable);
                    platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid, localizationManager.getMessage("messages.error")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (password != null) Arrays.fill(password, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeChangePassword(UUID playerUuid, String playerName, char[] oldPassword,
                                                        char[] newPassword, String ipAddress) {
        AuthState state = platform.getRestrictionAdapter().getAuthState(playerUuid);
        if (state != AuthState.AUTHENTICATED && state != AuthState.PASSWORD_CHANGE_REQUIRED) {
            // Before login the command only completes the temporary-password flow.
            if (oldPassword != null) Arrays.fill(oldPassword, ' ');
            if (newPassword != null) Arrays.fill(newPassword, ' ');
            platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid,
                    localizationManager.getMessage("messages.changepass-login-first",
                            "&cLog in with /login <password> before changing your password.")));
            return CompletableFuture.completedFuture(null);
        }
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        Object connectionToken = platform.currentConnectionToken(playerUuid);
        return authService.changePassword(playerUuid, playerName, oldPassword, newPassword, ipHash)
                .thenAccept(result -> platform.runTaskForPlayer(playerUuid, () -> {
                    // A disconnect-and-reconnect under the same UUID while this was hashing must
                    // not authenticate the new connection with the old one's result (audit #6).
                    if (!Objects.equals(connectionToken, platform.currentConnectionToken(playerUuid))) return;
                    if (result == AuthResult.SUCCESS) {
                        platform.getRestrictionAdapter().setPlayerAuthenticated(playerUuid);
                        clearAuthenticationTimeout(playerUuid);
                        platform.sendMessage(playerUuid, localizationManager.getMessage("messages.changepass-success"));
                    } else {
                        String msgKey = switch (result) {
                            case INVALID_PASSWORD, ACCOUNT_NOT_FOUND -> "messages.old-password-invalid";
                            case PASSWORD_UNCHANGED -> "messages.password-unchanged";
                            case VALIDATION_FAILED -> "messages.password-policy-failed";
                            case RATE_LIMITED -> "messages.rate-limited";
                            case ACCOUNT_LOCKED -> "messages.account-locked";
                            case TEMPORARY_PASSWORD_EXPIRED -> "messages.temporary-password-expired";
                            default -> "messages.error";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    logFailure("Password change command", throwable);
                    platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid, localizationManager.getMessage("messages.error")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (oldPassword != null) Arrays.fill(oldPassword, ' ');
                    if (newPassword != null) Arrays.fill(newPassword, ' ');
                });
    }

    private void logFailure(String operation, Throwable error) {
        platform.getLogger().severe(operation + " failed internally ("
                + FailureDiagnostics.causeTypes(error) + "). Sensitive details are hidden.");
    }

    @Override
    public CompletableFuture<Void> executeLogout(UUID playerUuid) {
        if (!platform.getRestrictionAdapter().isAuthenticated(playerUuid)) {
            platform.runTaskForPlayer(playerUuid, () -> platform.sendMessage(playerUuid,
                    localizationManager.getMessage("messages.must-auth", "&cYou are not authenticated!")));
            return CompletableFuture.completedFuture(null);
        }
        return authService.getSessionService().revokeActiveSessionForPlayer(playerUuid)
                .whenComplete((ignored, throwable) -> platform.runTaskForPlayer(playerUuid, () -> {
                    platform.getRestrictionAdapter().setPlayerUnauthenticated(playerUuid, null);
                    if (authenticationTimeoutService != null) {
                        authenticationTimeoutService.start(playerUuid);
                    }
                    String key = throwable == null ? "messages.logged-out" : "messages.error";
                    platform.sendMessage(playerUuid, localizationManager.getMessage(key));
                }));
    }

    @Override
    public void executeAuthStatus(UUID playerUuid) {
        AuthState state = platform.getRestrictionAdapter().getAuthState(playerUuid);
        String message = state == AuthState.AUTHENTICATED
                ? localizationManager.getMessage("messages.auth-status-authenticated",
                "&aAuthentication status: authenticated.")
                : localizationManager.getMessage("messages.auth-status-required",
                "&cAuthentication status: login required.");
        platform.sendMessage(playerUuid, message);
    }

    private void clearAuthenticationTimeout(UUID playerUuid) {
        if (authenticationTimeoutService != null) {
            authenticationTimeoutService.clear(playerUuid);
        }
    }
}
