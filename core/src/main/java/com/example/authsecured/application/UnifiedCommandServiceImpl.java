package com.example.authsecured.application;

import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.domain.auth.AuthState;
import com.example.authsecured.infrastructure.config.LocalizationManager;
import com.example.authsecured.infrastructure.security.IpHashService;
import com.example.authsecured.ports.AuthPlatform;
import com.example.authsecured.ports.UnifiedCommandExecutor;

import java.util.Arrays;
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

    public UnifiedCommandServiceImpl(AuthService authService, AuthPlatform platform, LocalizationManager localizationManager, IpHashService ipHashService) {
        this.authService = authService;
        this.platform = platform;
        this.localizationManager = localizationManager;
        this.ipHashService = ipHashService;
    }

    @Override
    public CompletableFuture<Void> executeRegister(UUID playerUuid, String playerName, char[] password, char[] confirmPassword, String ipAddress) {
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        return authService.register(playerUuid, playerName, password, ipHash)
                .thenAccept(result -> platform.runTaskOnMainThread(() -> {
                    if (result == AuthResult.SUCCESS) {
                        platform.getRestrictionAdapter().setPlayerAuthenticated(playerUuid);
                        platform.sendMessage(playerUuid, localizationManager.getMessage("register.success"));
                    } else {
                        String msgKey = switch (result) {
                            case ALREADY_REGISTERED -> "register.already-registered";
                            case VALIDATION_FAILED -> "register.policy-violation";
                            case RATE_LIMITED -> "register.rate-limited";
                            default -> "register.failed";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    platform.runTaskOnMainThread(() -> platform.sendMessage(playerUuid, localizationManager.getMessage("register.failed")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (password != null) Arrays.fill(password, ' ');
                    if (confirmPassword != null) Arrays.fill(confirmPassword, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeLogin(UUID playerUuid, String playerName, char[] password, String ipAddress) {
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        return authService.login(playerUuid, playerName, password, ipHash)
                .thenAccept(result -> platform.runTaskOnMainThread(() -> {
                    if (result == AuthResult.SUCCESS) {
                        platform.getRestrictionAdapter().setPlayerAuthenticated(playerUuid);
                        platform.sendMessage(playerUuid, localizationManager.getMessage("login.success"));
                    } else {
                        String msgKey = switch (result) {
                            case ACCOUNT_NOT_FOUND, NOT_REGISTERED -> "login.not-registered";
                            case INVALID_PASSWORD -> "login.invalid-credentials";
                            case ACCOUNT_LOCKED -> "login.account-locked";
                            case RATE_LIMITED -> "login.rate-limited";
                            default -> "login.failed";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    platform.runTaskOnMainThread(() -> platform.sendMessage(playerUuid, localizationManager.getMessage("login.failed")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (password != null) Arrays.fill(password, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeChangePassword(UUID playerUuid, char[] oldPassword, char[] newPassword, String ipAddress) {
        byte[] ipHash = ipHashService.hashIp(ipAddress);
        return authService.changePassword(playerUuid, oldPassword, newPassword, ipHash)
                .thenAccept(result -> platform.runTaskOnMainThread(() -> {
                    if (result == AuthResult.SUCCESS) {
                        platform.sendMessage(playerUuid, localizationManager.getMessage("changepassword.success"));
                    } else {
                        String msgKey = switch (result) {
                            case INVALID_PASSWORD -> "changepassword.invalid-old";
                            case VALIDATION_FAILED -> "changepassword.policy-violation";
                            default -> "changepassword.failed";
                        };
                        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
                    }
                }))
                .exceptionally(throwable -> {
                    platform.runTaskOnMainThread(() -> platform.sendMessage(playerUuid, localizationManager.getMessage("changepassword.failed")));
                    return null;
                })
                .whenComplete((v, ex) -> {
                    if (oldPassword != null) Arrays.fill(oldPassword, ' ');
                    if (newPassword != null) Arrays.fill(newPassword, ' ');
                });
    }

    @Override
    public CompletableFuture<Void> executeLogout(UUID playerUuid) {
        platform.getRestrictionAdapter().setPlayerUnauthenticated(playerUuid, null);
        platform.sendMessage(playerUuid, localizationManager.getMessage("logout.success"));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void executeAuthStatus(UUID playerUuid) {
        AuthState state = platform.getRestrictionAdapter().getAuthState(playerUuid);
        String msgKey = (state == AuthState.AUTHENTICATED) ? "authstatus.authenticated" : "authstatus.unauthenticated";
        platform.sendMessage(playerUuid, localizationManager.getMessage(msgKey));
    }
}
