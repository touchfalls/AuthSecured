package io.github.touchfalls.authsecured.domain.auth;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Secret-bearing result. Callers must erase the password once it has been delivered. */
public final class TemporaryPasswordReset {
    private final UUID playerUuid;
    private final char[] password;
    private final Instant expiresAt;

    public TemporaryPasswordReset(UUID playerUuid, char[] password, Instant expiresAt) {
        this.playerUuid = playerUuid;
        this.password = password == null ? new char[0] : password.clone();
        this.expiresAt = expiresAt;
    }

    /** UUID of the account the password was issued for. */
    public UUID getPlayerUuid() { return playerUuid; }
    public char[] copyPassword() { return password.clone(); }
    public Instant getExpiresAt() { return expiresAt; }

    public void destroy() { Arrays.fill(password, '\0'); }
}
