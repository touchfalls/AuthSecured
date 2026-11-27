package io.github.touchfalls.authsecured.domain.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Account {
    private final Long id;
    private final UUID uuid;
    private final String username;
    private final String usernameNormalized;
    private String passwordHash;
    private AccountStatus status;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private Instant passwordChangedAt;
    private boolean passwordChangeRequired;
    private Instant temporaryPasswordExpiresAt;

    public Account(Long id, UUID uuid, String username, String usernameNormalized,
                   String passwordHash, AccountStatus status, int failedLoginAttempts,
                   Instant lockedUntil, Instant createdAt, Instant updatedAt,
                   Instant lastLoginAt, Instant passwordChangedAt) {
        this(id, uuid, username, usernameNormalized, passwordHash, status, failedLoginAttempts,
                lockedUntil, createdAt, updatedAt, lastLoginAt, passwordChangedAt, false, null);
    }

    public Account(Long id, UUID uuid, String username, String usernameNormalized,
                   String passwordHash, AccountStatus status, int failedLoginAttempts,
                   Instant lockedUntil, Instant createdAt, Instant updatedAt,
                   Instant lastLoginAt, Instant passwordChangedAt,
                   boolean passwordChangeRequired, Instant temporaryPasswordExpiresAt) {
        this.id = id;
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.usernameNormalized = Objects.requireNonNull(usernameNormalized, "UsernameNormalized cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "PasswordHash cannot be null");
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.lastLoginAt = lastLoginAt;
        this.passwordChangedAt = passwordChangedAt != null ? passwordChangedAt : Instant.now();
        this.passwordChangeRequired = passwordChangeRequired;
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
    }

    public static Account createNew(UUID uuid, String username, String usernameNormalized, String passwordHash) {
        Instant now = Instant.now();
        return new Account(null, uuid, username, usernameNormalized, passwordHash,
                AccountStatus.ACTIVE, 0, null, now, now, null, now);
    }

    public boolean isLocked(Instant currentTime) {
        if (status == AccountStatus.SUSPENDED) return true;
        if (status == AccountStatus.LOCKED && lockedUntil == null) return true;
        if (lockedUntil != null && currentTime.isBefore(lockedUntil)) return true;
        return false;
    }

    /**
     * Whether one more failure locks the account, by the same rule as
     * {@code AccountRepository.incrementFailedAttemptsAtomic}: an expired lock restarts the count at 1.
     */
    public boolean nextFailureLocks(int maxAttempts, Instant currentTime) {
        if (status == AccountStatus.SUSPENDED) return false;
        boolean lockExpired = status == AccountStatus.LOCKED && lockedUntil != null
                && !currentTime.isBefore(lockedUntil);
        return (lockExpired ? 1 : failedLoginAttempts + 1) >= maxAttempts;
    }

    public void recordFailedAttempt(int maxAttempts, long lockDurationSeconds, Instant currentTime) {
        this.failedLoginAttempts++;
        this.updatedAt = currentTime;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.status = AccountStatus.LOCKED;
            this.lockedUntil = currentTime.plusSeconds(lockDurationSeconds);
        }
    }

    public void recordSuccessfulLogin(Instant currentTime) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.status = AccountStatus.ACTIVE;
        this.lastLoginAt = currentTime;
        this.updatedAt = currentTime;
    }

    public void updatePassword(String newPasswordHash, Instant currentTime) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "New password hash cannot be null");
        this.passwordChangedAt = currentTime;
        this.updatedAt = currentTime;
        this.passwordChangeRequired = false;
        this.temporaryPasswordExpiresAt = null;
    }

    public void setTemporaryPassword(String passwordHash, Instant expiresAt) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash cannot be null");
        this.passwordChangeRequired = true;
        this.temporaryPasswordExpiresAt = Objects.requireNonNull(expiresAt, "Expiry cannot be null");
        this.passwordChangedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public boolean isTemporaryPasswordExpired(Instant currentTime) {
        return passwordChangeRequired && temporaryPasswordExpiresAt != null
                && !currentTime.isBefore(temporaryPasswordExpiresAt);
    }

    public void unlock() {
        this.status = AccountStatus.ACTIVE;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
        this.updatedAt = Instant.now();
    }

    public void lockUntil(Instant until) {
        this.status = AccountStatus.LOCKED;
        this.lockedUntil = until;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getUsername() { return username; }
    public String getUsernameNormalized() { return usernameNormalized; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getStatus() { return status; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public boolean isPasswordChangeRequiredFlag() { return passwordChangeRequired; }
    public Instant getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(uuid, account.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
