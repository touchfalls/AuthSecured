package com.example.authsecured.ports;

import com.example.authsecured.domain.auth.AuthState;
import java.util.UUID;

/**
 * Port for managing pre-authentication player restrictions.
 * Implemented by PaperPlayerRestrictionAdapter and FabricPlayerRestrictionAdapter.
 */
public interface PlayerRestrictionAdapter {
    void setPlayerAuthenticated(UUID playerUuid);
    void setPlayerUnauthenticated(UUID playerUuid, Object location);
    void removePlayer(UUID playerUuid);
    boolean isAuthenticated(UUID playerUuid);
    AuthState getAuthState(UUID playerUuid);
}
