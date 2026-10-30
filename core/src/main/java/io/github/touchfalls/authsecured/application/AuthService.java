package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.domain.auth.TemporaryPasswordReset;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.infrastructure.security.SecureRandomProvider;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.util.FailureDiagnostics;
import io.github.touchfalls.authsecured.util.UsernameNormalizer;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class AuthService {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final PasswordService passwordService;
    private final SessionService sessionService;
    private final SecurityService securityService;
    private final AccountRepository accountRepository;
    private final PlatformLogger logger;

    public AuthService(RegistrationService registrationService, LoginService loginService,
                       PasswordService passwordService, SessionService sessionService,
                       SecurityService securityService, AccountRepository accountRepository) {
        this(registrationService, loginService, passwordService, sessionService,
                securityService, accountRepository, null);
    }

    public AuthService(RegistrationService registrationService, LoginService loginService,
                       PasswordService passwordService, SessionService sessionService,
                       SecurityService securityService, AccountRepository accountRepository,
                       PlatformLogger logger) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.passwordService = passwordService;
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.accountRepository = accountRepository;
        this.logger = logger;
    }

    public CompletableFuture<AuthResult> register(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        return registrationService.register(playerUuid, username, password, ipHash);
    }

    public CompletableFuture<AuthResult> login(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        return loginService.login(playerUuid, username, password, ipHash);
    }

    public int getPasswordMinLength() {
        return passwordService.getMinLength();
    }

    /**
     * Changes the password after the old one passes the same checks as {@code /login}:
     * rate limits, failed-attempt counter, account lock and temporary-password expiry.
     */
    public CompletableFuture<AuthResult> changePassword(UUID playerUuid, String username, char[] oldPassword,
                                                        char[] newPassword, byte[] ipHash) {
        if (!passwordService.validatePolicy(newPassword) || PasswordService.matchesUsername(newPassword, username)) {
            return CompletableFuture.completedFuture(AuthResult.VALIDATION_FAILED);
        }
        if (ipHash == null || ipHash.length == 0) {
            if (logger != null) {
                logger.severe("Password change rejected because the client IP is unavailable. Check proxy forwarding.");
            }
            return CompletableFuture.completedFuture(AuthResult.INTERNAL_ERROR);
        }

        return loginService.verifyCredentials(playerUuid, username, oldPassword, ipHash)
                .thenCompose(check -> {
                    if (!check.accepted()) {
                        return CompletableFuture.completedFuture(check.result());
                    }
                    Account account = check.account();
                    if (constantTimeEquals(oldPassword, newPassword)) {
                        return CompletableFuture.completedFuture(AuthResult.PASSWORD_UNCHANGED);
                    }
                    return passwordService.hashPasswordAsync(newPassword)
                            .thenCompose(newHash -> {
                                Instant now = Instant.now();
                                // A narrow write touches only the password columns, so it cannot
                                // undo a failed-attempt counter or lock written concurrently by a
                                // slow Argon2 hashing step elsewhere (see finding #4 of the audit).
                                return accountRepository.updatePasswordHash(account.getId(), newHash, now)
                                        .thenCompose(updated -> updated
                                                ? loginService.recordSuccessfulAuthentication(
                                                        account, check.usernameNormalized(), now)
                                                : CompletableFuture.failedFuture(
                                                        new IllegalStateException("Account update failed")))
                                        .thenCompose(v -> sessionService.revokeAllSessionsForAccount(account.getId()))
                                        .thenCompose(v -> securityService.logSecurityEvent(account.getId(), SecurityEventType.PASSWORD_CHANGE, ipHash, "{}"))
                                        .thenApply(v -> AuthResult.SUCCESS);
                            });
                }).exceptionally(ex -> {
                    if (logger != null) logger.severe("Password change failed internally ("
                            + FailureDiagnostics.causeTypes(ex) + "). Sensitive details are hidden.");
                    return AuthResult.INTERNAL_ERROR;
                });
    }

    public CompletableFuture<TemporaryPasswordReset> issueTemporaryPassword(String username,
                                                                              int passwordLength,
                                                                              long expirySeconds) {
        if (expirySeconds < 60 || expirySeconds > 86_400) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Temporary password expiry must be between 60 and 86400 seconds"));
        }
        String normalized = UsernameNormalizer.normalize(username);
        char[] temporaryPassword = SecureRandomProvider.generateTemporaryPassword(passwordLength);
        Instant expiresAt = Instant.now().plusSeconds(expirySeconds);
        return accountRepository.findByUsernameNormalized(normalized)
                .thenCompose(optAccount -> {
                    if (optAccount.isEmpty()) {
                        Arrays.fill(temporaryPassword, '\0');
                        return CompletableFuture.completedFuture(null);
                    }
                    Account account = optAccount.get();
                    return passwordService.hashPasswordAsync(temporaryPassword)
                            .thenCompose(hash -> {
                                return accountRepository.setTemporaryPassword(
                                                account.getId(), hash, expiresAt, Instant.now())
                                        .thenCompose(updated -> updated
                                                ? sessionService.revokeAllSessionsForAccount(account.getId())
                                                : CompletableFuture.failedFuture(new IllegalStateException("Account update failed")))
                                        .thenCompose(v -> loginService.clearRateLimit(normalized))
                                        .thenCompose(v -> securityService.logSecurityEvent(account.getId(),
                                                SecurityEventType.TEMPORARY_PASSWORD_ISSUED, null, "{}"))
                                        .thenApply(v -> {
                                            TemporaryPasswordReset reset = new TemporaryPasswordReset(
                                                    account.getUuid(), temporaryPassword, expiresAt);
                                            Arrays.fill(temporaryPassword, '\0');
                                            return reset;
                                        });
                            });
                })
                .whenComplete((result, error) -> {
                    if (error != null || result == null) {
                        Arrays.fill(temporaryPassword, '\0');
                    }
                });
    }

    public CompletableFuture<Boolean> isPasswordChangeRequired(UUID playerUuid) {
        return accountRepository.findByUuid(playerUuid)
                .thenApply(account -> account.map(Account::isPasswordChangeRequired).orElse(false));
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
                    return accountRepository.unlock(account.getId(), Instant.now())
                            .thenCompose(updated -> updated
                                    ? loginService.clearRateLimit(normalized)
                                            .thenCompose(v -> securityService.logSecurityEvent(account.getId(),
                                                    SecurityEventType.ACCOUNT_UNLOCKED, null, "{}"))
                                            .thenApply(v -> true)
                                    : CompletableFuture.completedFuture(false));
                });
    }

    /** Deletes the account and revokes its sessions; yields the owner's UUID if an account was deleted. */
    public CompletableFuture<Optional<UUID>> unregisterAccount(String username) {
        String normalized = UsernameNormalizer.normalize(username);
        return accountRepository.findByUsernameNormalized(normalized)
                .thenCompose(optAccount -> {
                    if (optAccount.isEmpty()) return CompletableFuture.completedFuture(Optional.<UUID>empty());
                    Account account = optAccount.get();
                    return accountRepository.delete(account.getUuid())
                            .thenCompose(deleted -> {
                                if (deleted) {
                                    return sessionService.revokeAllSessionsForAccount(account.getId())
                                            .thenApply(v -> Optional.of(account.getUuid()));
                                }
                                return CompletableFuture.completedFuture(Optional.<UUID>empty());
                            });
                });
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    private static boolean constantTimeEquals(char[] left, char[] right) {
        if (left == null || right == null) return false;
        int difference = left.length ^ right.length;
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            char leftChar = i < left.length ? left[i] : 0;
            char rightChar = i < right.length ? right[i] : 0;
            difference |= leftChar ^ rightChar;
        }
        return difference == 0;
    }
}
