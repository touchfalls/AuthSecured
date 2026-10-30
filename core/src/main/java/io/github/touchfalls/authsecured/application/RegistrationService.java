package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.AccountLimitExceededException;
import io.github.touchfalls.authsecured.ports.DuplicateAccountException;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.util.FailureDiagnostics;
import io.github.touchfalls.authsecured.util.UsernameNormalizer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RegistrationService {

    private final AccountRepository accountRepository;
    private final PasswordService passwordService;
    private final RateLimitService rateLimitService;
    private final SecurityService securityService;
    private final PlatformLogger logger;

    public RegistrationService(AccountRepository accountRepository, PasswordService passwordService,
                                RateLimitService rateLimitService, SecurityService securityService) {
        this(accountRepository, passwordService, rateLimitService, securityService, null);
    }

    public RegistrationService(AccountRepository accountRepository, PasswordService passwordService,
                               RateLimitService rateLimitService, SecurityService securityService,
                               PlatformLogger logger) {
        this.accountRepository = accountRepository;
        this.passwordService = passwordService;
        this.rateLimitService = rateLimitService;
        this.securityService = securityService;
        this.logger = logger;
    }

    public CompletableFuture<AuthResult> register(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        if (ipHash == null || ipHash.length == 0) {
            return CompletableFuture.completedFuture(AuthResult.INTERNAL_ERROR);
        }
        if (!UsernameNormalizer.isValidUsername(username)) {
            return CompletableFuture.completedFuture(AuthResult.VALIDATION_FAILED);
        }

        if (!passwordService.validatePolicy(password) || PasswordService.matchesUsername(password, username)) {
            return CompletableFuture.completedFuture(AuthResult.VALIDATION_FAILED);
        }

        String normalizedUsername = UsernameNormalizer.normalize(username);

        return accountRepository.countAccountsByIpHash(ipHash)
                .thenCompose(count -> rateLimitService.isRegistrationAllowed(ipHash, count))
                .thenCompose(allowed -> {
                    if (!allowed) {
                        return securityService.logSecurityEvent(null, SecurityEventType.RATE_LIMIT_EXCEEDED,
                                        ipHash, "{\"reason\":\"registration_limit\"}")
                                .thenApply(ignored -> AuthResult.RATE_LIMITED);
                    }

                    return accountRepository.findByUsernameNormalized(normalizedUsername)
                            .thenCompose(existingByUsername -> {
                                if (existingByUsername.isPresent()) {
                                    return CompletableFuture.completedFuture(AuthResult.ALREADY_REGISTERED);
                                }
                                return accountRepository.findByUuid(playerUuid)
                                        .thenCompose(existingByUuid -> {
                                            if (existingByUuid.isPresent()) {
                                                return CompletableFuture.completedFuture(AuthResult.ALREADY_REGISTERED);
                                            }

                                            return passwordService.hashPasswordAsync(password)
                                                    .thenCompose(hash -> {
                                                        Account account = Account.createNew(playerUuid, username, normalizedUsername, hash);
                                                        return accountRepository.save(account, ipHash, rateLimitService.getMaxRegistrationsPerIp())
                                                                .thenCompose(savedAccount -> {
                                                                    return securityService.logSecurityEvent(savedAccount.getId(),
                                                                                    SecurityEventType.REGISTER_SUCCESS, ipHash, "{}")
                                                                            .thenApply(ignored -> AuthResult.SUCCESS);
                                                                });
                                                    });
                                        });
                            });
                }).exceptionally(ex -> {
                    if (isDuplicateAccount(ex)) return AuthResult.ALREADY_REGISTERED;
                    if (hasCause(ex, AccountLimitExceededException.class)) return AuthResult.RATE_LIMITED;
                    if (logger != null) logger.severe("Registration failed internally ("
                            + FailureDiagnostics.causeTypes(ex) + "). Sensitive details are hidden.");
                    return AuthResult.INTERNAL_ERROR;
                });
    }

    private static boolean isDuplicateAccount(Throwable error) {
        return hasCause(error, DuplicateAccountException.class);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }
}
