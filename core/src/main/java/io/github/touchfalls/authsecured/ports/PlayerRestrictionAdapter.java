package io.github.touchfalls.authsecured.ports;

import io.github.touchfalls.authsecured.domain.auth.AuthState;
import java.util.Set;
import java.util.UUID;

/**
 * Port for managing pre-authentication player restrictions.
 * Implemented by each platform adapter.
 */
public interface PlayerRestrictionAdapter {
    void setPlayerAuthenticated(UUID playerUuid);
    void setPlayerUnauthenticated(UUID playerUuid, Object location);

    /**
     * Keeps the player restricted but allows the temporary-password change flow.
     * Adapters that do not track this state reject pre-login password changes.
     */
    default void setPasswordChangeRequired(UUID playerUuid) {
    }
    void removePlayer(UUID playerUuid);
    boolean isAuthenticated(UUID playerUuid);
    AuthState getAuthState(UUID playerUuid);

    /** Snapshot of the online players that are currently authenticated. */
    Set<UUID> getAuthenticatedPlayers();
}
