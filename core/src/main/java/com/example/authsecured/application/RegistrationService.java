package com.example.authsecured.application;

import com.example.authsecured.domain.account.Account;
import com.example.authsecured.domain.auth.AuthResult;
import com.example.authsecured.domain.security.SecurityEventType;
import com.example.authsecured.ports.AccountRepository;
import com.example.authsecured.util.UsernameNormalizer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RegistrationService {

    private final AccountRepository accountRepository;
    private final PasswordService passwordService;
    private final RateLimitService rateLimitService;
    private final SecurityService securityService;

    public RegistrationService(AccountRepository accountRepository, PasswordService passwordService,
                                RateLimitService rateLimitService, SecurityService securityService) {
        this.accountRepository = accountRepository;
        this.passwordService = passwordService;
        this.rateLimitService = rateLimitService;
        this.securityService = securityService;
    }

    public CompletableFuture<AuthResult> register(UUID playerUuid, String username, char[] password, byte[] ipHash) {
        if (!UsernameNormalizer.isValidUsername(username)) {
            return CompletableFuture.completedFuture(AuthResult.VALIDATION_FAILED);
        }

        if (!passwordService.validatePolicy(password)) {
            return CompletableFuture.completedFuture(AuthResult.VALIDATION_FAILED);
        }

        String normalizedUsername = UsernameNormalizer.normalize(username);

        return accountRepository.countAccountsByIpHash(ipHash)
                .thenCompose(count -> rateLimitService.isRegistrationAllowed(ipHash, count))
                .thenCompose(allowed -> {
                    if (!allowed) {
                        securityService.logSecurityEvent(null, SecurityEventType.RATE_LIMIT_EXCEEDED, ipHash, "{\"reason\":\"registration_limit\"}");
                        return CompletableFuture.completedFuture(AuthResult.RATE_LIMITED);
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
                                                        return accountRepository.save(account)
                                                                .thenCompose(savedAccount -> {
                                                                    securityService.logSecurityEvent(savedAccount.getId(), SecurityEventType.REGISTER_SUCCESS, ipHash, "{}");
                                                                    return CompletableFuture.completedFuture(AuthResult.SUCCESS);
                                                                });
                                                    });
                                        });
                            });
                }).exceptionally(ex -> AuthResult.INTERNAL_ERROR);
    }
}
