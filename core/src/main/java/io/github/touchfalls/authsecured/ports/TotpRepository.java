package io.github.touchfalls.authsecured.ports;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Authenticator (TOTP) state of an account. The secret is stored encrypted, backup codes only as hashes. */
public interface TotpRepository {

    /** {@code enabled} is false while a secret is set up but not yet confirmed with a code. */
    record State(byte[] encryptedSecret, boolean enabled, long lastStep, List<String> backupCodeHashes) { }

    /** Empty when the account never started setting up an authenticator. */
    CompletableFuture<Optional<State>> find(Long accountId);

    /** Stores a new, not yet enabled secret, replacing an earlier unconfirmed one. */
    CompletableFuture<Void> savePending(Long accountId, byte[] encryptedSecret);

    /** Turns a pending secret on. False when there is no pending secret (already on, or never set up). */
    CompletableFuture<Boolean> enable(Long accountId, long usedStep, List<String> backupCodeHashes);

    /** Remembers {@code step} as used. False when this step or a later one was already used. */
    CompletableFuture<Boolean> useStep(Long accountId, long step);

    /** Removes one backup code. False when the account does not have it, so a code works once. */
    CompletableFuture<Boolean> consumeBackupCode(Long accountId, String hash);

    CompletableFuture<Void> disable(Long accountId);
}
