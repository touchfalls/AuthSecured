package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.infrastructure.security.TotpService;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.TotpRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Authenticator (TOTP) setup and the second step of login. Every wrong code counts against the same
 * failure counters, locks and rate limits as a wrong password, because a 6-digit code is easy to guess.
 */
public class TwoFactorService {

    public enum Status {
        OK, ALREADY_ENABLED, NOT_ENABLED, NO_PENDING, INVALID_CODE, RATE_LIMITED, ACCOUNT_LOCKED, ERROR
    }

    /** {@code uri} and {@code secret} are set after a setup starts, {@code backupCodes} once it is confirmed. */
    public record Outcome(Status status, boolean newAddress, String uri, String secret, List<String> backupCodes) {
        static Outcome of(Status status) {
            return new Outcome(status, false, null, null, List.of());
        }
    }

    private static final String ISSUER = "AuthSecured";

    private final AccountRepository accountRepository;
    private final TotpRepository totpRepository;
    private final TotpService totpService;
    private final RateLimitService rateLimitService;
    private final SecurityService securityService;
    private final LoginService loginService;
    private final int maxFailedAttempts;
    private final long lockDurationSeconds;

    public TwoFactorService(AccountRepository accountRepository, TotpRepository totpRepository,
                            TotpService totpService, RateLimitService rateLimitService,
                            SecurityService securityService, LoginService loginService,
                            int maxFailedAttempts, long lockDurationSeconds) {
        this.accountRepository = accountRepository;
        this.totpRepository = totpRepository;
        this.totpService = totpService;
        this.rateLimitService = rateLimitService;
        this.securityService = securityService;
        this.loginService = loginService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDurationSeconds = lockDurationSeconds;
    }

    public CompletableFuture<Boolean> isEnabled(UUID playerUuid) {
        return accountRepository.findByUuid(playerUuid).thenCompose(account -> account.isEmpty()
                ? CompletableFuture.completedFuture(false)
                : totpRepository.find(account.get().getId())
                        .thenApply(state -> state.map(TotpRepository.State::enabled).orElse(false)));
    }

    /** Starts a setup: a new secret is stored but stays off until {@link #confirmSetup} sees a valid code. */
    public CompletableFuture<Outcome> beginSetup(UUID playerUuid) {
        return accountRepository.findByUuid(playerUuid).thenCompose(optional -> {
            if (optional.isEmpty()) return done(Status.ERROR);
            Account account = optional.get();
            return totpRepository.find(account.getId()).thenCompose(state -> {
                if (state.isPresent() && state.get().enabled()) return done(Status.ALREADY_ENABLED);
                byte[] secret = totpService.newSecret();
                String uri = totpService.otpauthUri(ISSUER, account.getUsername(), secret);
                String base32 = TotpService.toBase32(secret);
                byte[] encrypted = totpService.encrypt(secret);
                Arrays.fill(secret, (byte) 0);
                return totpRepository.savePending(account.getId(), encrypted)
                        .thenApply(v -> new Outcome(Status.OK, false, uri, base32, List.of()));
            });
        });
    }

    public CompletableFuture<Outcome> confirmSetup(UUID playerUuid, String code, byte[] ipHash) {
        return withAccount(playerUuid, ipHash, (account, state) -> {
            if (state == null) return done(Status.NO_PENDING);
            if (state.enabled()) return done(Status.ALREADY_ENABLED);
            byte[] secret = totpService.decrypt(state.encryptedSecret());
            long step;
            try {
                step = totpService.matchingStep(secret, code, System.currentTimeMillis(), 0);
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
            if (step < 0) return recordFailure(account, ipHash);
            List<String> codes = totpService.newBackupCodes();
            List<String> hashes = codes.stream().map(totpService::hashBackupCode).toList();
            return totpRepository.enable(account.getId(), step, hashes).thenCompose(enabled -> {
                if (!enabled) return done(Status.NO_PENDING);
                return securityService.logSecurityEvent(account.getId(), SecurityEventType.TWO_FACTOR_ENABLED, ipHash, "{}")
                        .thenApply(v -> new Outcome(Status.OK, false, null, null, codes));
            });
        });
    }

    /** Turns the authenticator off; a current code or a backup code is required. */
    public CompletableFuture<Outcome> disable(UUID playerUuid, String code, byte[] ipHash) {
        return withAccount(playerUuid, ipHash, (account, state) -> {
            if (state == null || !state.enabled()) return done(Status.NOT_ENABLED);
            return checkCode(account, state, code).thenCompose(valid -> {
                if (!valid) return recordFailure(account, ipHash);
                return totpRepository.disable(account.getId())
                        .thenCompose(v -> securityService.logSecurityEvent(
                                account.getId(), SecurityEventType.TWO_FACTOR_DISABLED, ipHash, "{}"))
                        .thenApply(v -> Outcome.of(Status.OK));
            });
        });
    }

    /** The second step of login: on a valid code the login completes exactly as after a password. */
    public CompletableFuture<Outcome> verifyLogin(UUID playerUuid, String code, byte[] ipHash) {
        return withAccount(playerUuid, ipHash, (account, state) -> {
            if (state == null || !state.enabled()) return done(Status.NOT_ENABLED);
            return checkCode(account, state, code).thenCompose(valid -> {
                if (!valid) return recordFailure(account, ipHash);
                return loginService.completeLogin(account, playerUuid, account.getUsernameNormalized(), ipHash)
                        .thenApply(newAddress -> new Outcome(Status.OK, newAddress, null, null, List.of()));
            });
        });
    }

    /** Runs {@code action} after the same IP, account and lock checks a password attempt goes through. */
    private CompletableFuture<Outcome> withAccount(UUID playerUuid, byte[] ipHash, Action action) {
        if (ipHash == null || ipHash.length == 0) return done(Status.ERROR);
        return rateLimitService.isIpRateLimited(ipHash).thenCompose(ipLimited -> {
            if (ipLimited) return done(Status.RATE_LIMITED);
            return accountRepository.findByUuid(playerUuid).thenCompose(optional -> {
                if (optional.isEmpty()) return done(Status.ERROR);
                Account account = optional.get();
                return rateLimitService.isAccountRateLimited(account.getUsernameNormalized()).thenCompose(limited -> {
                    if (limited) return done(Status.RATE_LIMITED);
                    if (account.isLocked(Instant.now())) return done(Status.ACCOUNT_LOCKED);
                    return totpRepository.find(account.getId())
                            .thenCompose(state -> action.run(account, state.orElse(null)));
                });
            });
        });
    }

    /** A current code, or an unused backup code. Both work once. */
    private CompletableFuture<Boolean> checkCode(Account account, TotpRepository.State state, String code) {
        byte[] secret = totpService.decrypt(state.encryptedSecret());
        long step;
        try {
            step = totpService.matchingStep(secret, code, System.currentTimeMillis(), state.lastStep());
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
        if (step >= 0) return totpRepository.useStep(account.getId(), step);
        if (code == null || code.isBlank()) return CompletableFuture.completedFuture(false);
        return totpRepository.consumeBackupCode(account.getId(), totpService.hashBackupCode(code));
    }

    private CompletableFuture<Outcome> recordFailure(Account account, byte[] ipHash) {
        return accountRepository.incrementFailedAttemptsAtomic(
                        account.getId(), maxFailedAttempts, lockDurationSeconds, Instant.now())
                .thenCompose(v -> rateLimitService.recordFailedLoginAttempt(account.getUsernameNormalized(), ipHash))
                .thenCompose(v -> securityService.logLoginAttempt(account.getId(), ipHash, false, "INVALID_TWO_FACTOR_CODE"))
                .thenApply(v -> Outcome.of(Status.INVALID_CODE));
    }

    private static CompletableFuture<Outcome> done(Status status) {
        return CompletableFuture.completedFuture(Outcome.of(status));
    }

    private interface Action {
        CompletableFuture<Outcome> run(Account account, TotpRepository.State state);
    }
}
