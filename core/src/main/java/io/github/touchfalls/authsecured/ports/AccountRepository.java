package io.github.touchfalls.authsecured.ports;

import io.github.touchfalls.authsecured.domain.account.Account;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public interface AccountRepository {
    CompletableFuture<Optional<Account>> findByUuid(UUID uuid);
    CompletableFuture<Optional<Account>> findByUsernameNormalized(String usernameNormalized);
    /**
     * @param registrationIpHash the hashed IP the account registered from, stored so
     *                           {@link #countAccountsByIpHash} works even with audit
     *                           logging disabled or its retention period elapsed; null
     *                           if the IP was unavailable.
     */
    /**
     * Inserts the account unless {@code maxAccountsPerIp} accounts already have this registration
     * IP hash. The count and the insert are one atomic step, so registrations running at the same
     * time cannot exceed the limit; a full IP fails with {@link AccountLimitExceededException}.
     */
    CompletableFuture<Account> save(Account account, byte[] registrationIpHash, int maxAccountsPerIp);

    default CompletableFuture<Account> save(Account account, byte[] registrationIpHash) {
        return save(account, registrationIpHash, Integer.MAX_VALUE);
    }
    CompletableFuture<Boolean> incrementFailedAttemptsAtomic(Long accountId, int maxAttempts,
                                                              long lockDurationSeconds, Instant now);
    CompletableFuture<Boolean> resetFailedAttemptsAtomic(Long accountId, Instant now);

    /**
     * Stores the IP hash of a successful login and returns the previous one, empty when the account
     * had none (first login, or an account from before this was recorded).
     */
    CompletableFuture<Optional<byte[]>> exchangeLastLoginIpHash(Long accountId, byte[] ipHash);
    /** Updates only the password, clearing any pending temporary-password state. Leaves lock state untouched. */
    CompletableFuture<Boolean> updatePasswordHash(Long accountId, String newPasswordHash, Instant now);
    /**
     * Swaps the stored hash of an unchanged password for one with current cost settings.
     * Writes only if the stored hash still equals {@code expectedHash}, so a password change
     * made in the meantime is never overwritten; every other column, including the
     * password-change time, stays as it is.
     */
    CompletableFuture<Boolean> replacePasswordHash(Long accountId, String expectedHash, String newPasswordHash,
                                                   Instant now);
    /** Clears lock and failed-attempt state. Leaves the password hash untouched. */
    CompletableFuture<Boolean> unlock(Long accountId, Instant now);
    /** Sets a temporary password and clears lock/failed-attempt state in one write. */
    CompletableFuture<Boolean> setTemporaryPassword(Long accountId, String passwordHash, Instant expiresAt, Instant now);
    CompletableFuture<Boolean> delete(UUID uuid);
    CompletableFuture<Long> countAccountsByIpHash(byte[] ipHash);
}
