package com.example.authsecured.application;

import com.example.authsecured.domain.account.Account;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.domain.security.SecurityEventType;
import com.example.authsecured.ports.AccountRepository;
import com.example.authsecured.util.UsernameNormalizer;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AuthService {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final SecurityService securityService;
    private final AccountRepository accountRepository;

    public AuthService(RegistrationService registrationService, LoginService loginService,
                       PasswordService passwordService, SessionService sessionService,
                       SecurityService securityService, AccountRepository accountRepository) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.passwordService = passwordService;
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.accountRepository = accountRepository;
    }

    public CompletableFuture<AuthResult> register(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        return registrationService.register(playerUuid, username, password, ipHash);
    }

    public CompletableFuture<AuthResult> login(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        return loginService.login(playerUuid, username, password, ipHash);
    }

    public CompletableFuture<AuthResult> changePassword(UUID playerUuid, char[] oldPassword, char[] newPassword, byte[] ipHash) {
        if (!passwordService.validatePolicy(newPassword)) {
            return CompletableFuture.completedFuture(AuthResult.VALIDATION_FAILED);
        }

        return accountRepository.findByUuid(playerUuid)
                .thenCompose(optAccount -> {
                    if (optAccount.isEmpty()) {
                        return CompletableFuture.completedFuture(AuthResult.ACCOUNT_NOT_FOUND);
                    }
                    Account account = optAccount.get();

                    return passwordService.verifyPasswordAsync(oldPassword, account.getPasswordHash())
                            .thenCompose(valid -> {
                                if (!valid) {
                                    return CompletableFuture.completedFuture(AuthResult.INVALID_PASSWORD);
                                }
                                return passwordService.hashPasswordAsync(newPassword)
                                        .thenCompose(newHash -> {
                                            account.updatePassword(newHash, java.time.Instant.now());
                                            return accountRepository.update(account)
                                                    .thenCompose(v -> sessionService.revokeAllSessionsForAccount(account.getId()))
                                                    .thenCompose(v -> securityService.logSecurityEvent(account.getId(), SecurityEventType.PASSWORD_CHANGE, ipHash, "{}"))
                                                    .thenApply(v -> AuthResult.SUCCESS);
                                        });
                            });
                }).exceptionally(ex -> AuthResult.INTERNAL_ERROR);
    }

    public CompletableFuture<Boolean> isRegistered(UUID playerUuid) {
        return accountRepository.findByUuid(playerUuid).thenApply(Optional::isPresent);
    }

    public CompletableFuture<Boolean> unlockAccount(String username) {
        String normalized = UsernameNormalizer.normalize(username);
        return accountRepository.findByUsernameNormalized(normalized)
                .thenCompose(optAccount -> {
                    if (optAccount.isEmpty()) return CompletableFuture.completedFuture(false);
                    Account account = optAccount.get();
                    account.unlock();
                    return accountRepository.update(account)
                            .thenCompose(v -> securityService.logSecurityEvent(account.getId(), SecurityEventType.ACCOUNT_UNLOCKED, null, "{}"))
                            .thenApply(v -> true);
                });
    }

    public CompletableFuture<Boolean> resetPassword(String username, char[] newPassword) {
        if (!passwordService.validatePolicy(newPassword)) {
            return CompletableFuture.completedFuture(false);
        }
        String normalized = UsernameNormalizer.normalize(username);
        return accountRepository.findByUsernameNormalized(normalized)
                .thenCompose(optAccount -> {
                    if (optAccount.isEmpty()) return CompletableFuture.completedFuture(false);
                    Account account = optAccount.get();
                    return passwordService.hashPasswordAsync(newPassword)
                            .thenCompose(newHash -> {
                                account.updatePassword(newHash, java.time.Instant.now());
                                account.unlock();
                                return accountRepository.update(account)
                                        .thenCompose(v -> sessionService.revokeAllSessionsForAccount(account.getId()))
                                        .thenApply(v -> true);
                            });
                });
    }

    public CompletableFuture<Boolean> unregister(String username) {
        String normalized = UsernameNormalizer.normalize(username);
        return accountRepository.findByUsernameNormalized(normalized)
                .thenCompose(optAccount -> {
                    if (optAccount.isEmpty()) return CompletableFuture.completedFuture(false);
                    Account account = optAccount.get();
                    return accountRepository.delete(account.getUuid())
                            .thenCompose(deleted -> {
                                if (deleted) {
                                    return sessionService.revokeAllSessionsForAccount(account.getId()).thenApply(v -> true);
                                }
                                return CompletableFuture.completedFuture(false);
                            });
                });
    }

    public SessionService getSessionService() {
        return sessionService;
    }
}
